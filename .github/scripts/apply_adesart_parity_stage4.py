from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8-sig")


def write(path: str, content: str) -> None:
    target = ROOT / path
    current = target.read_text(encoding="utf-8-sig")
    if current != content:
        target.write_text(content, encoding="utf-8")
        print(f"updated {path}")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


repo_path = "android-app/app/src/main/java/br/com/vendamais/mobile/data/remote/SupabaseRepository.kt"
repo = read(repo_path)
anchor = '''    suspend fun reprocessUploadQueueItem(session: SavedSession, id: String): ErpUploadQueueItem {
'''
method = '''    suspend fun createStorageSignedUrl(
        session: SavedSession,
        bucket: String,
        objectPath: String,
        expiresIn: Int = 60,
    ): String {
        val safeBucket = java.net.URLEncoder.encode(bucket, Charsets.UTF_8.name()).replace("+", "%20")
        val safePath = objectPath
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { segment ->
                java.net.URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
            }
        if (safePath.isBlank()) throw IllegalStateException("Caminho do arquivo nao informado.")
        val response: JsonObject = client.safePost(
            url = "${AppConfig.supabaseUrl}/storage/v1/object/sign/$safeBucket/$safePath",
            json = json,
            body = buildJsonObject { put("expiresIn", expiresIn.coerceIn(30, 3600)) },
        ) {
            applyAuthHeaders(session)
        }
        val raw = listOf("signedURL", "signedUrl", "signed_url")
            .firstNotNullOfOrNull { key ->
                (response[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            }
            ?: throw IllegalStateException("Nao foi possivel gerar link temporario do arquivo.")
        val base = AppConfig.supabaseUrl.trimEnd('/')
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("/storage/v1/") -> "$base$raw"
            raw.startsWith("/") -> "$base/storage/v1$raw"
            else -> "$base/storage/v1/$raw"
        }
    }

'''
repo = replace_once(repo, anchor, method + anchor, "storage signed URL method")
write(repo_path, repo)

vm_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/AppViewModel.kt"
vm = read(vm_path)
anchor_vm = '''    fun reprocessUploadQueueItem(id: String) {
'''
method_vm = '''    suspend fun createQueueFileSignedUrl(item: ErpUploadQueueItem): String {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        return repository.createStorageSignedUrl(
            session = activeSession,
            bucket = item.bucket,
            objectPath = item.arquivoPath,
        )
    }

'''
vm = replace_once(vm, anchor_vm, method_vm + anchor_vm, "queue file ViewModel method")
write(vm_path, vm)

ui_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/FilaUploadErpScreen.kt"
ui = read(ui_path)
ui = replace_once(ui, "package br.com.vendamais.mobile.ui.screens\n\n", "package br.com.vendamais.mobile.ui.screens\n\nimport android.content.Intent\nimport android.net.Uri\n", "queue Android imports")
ui = replace_once(ui, "import androidx.compose.runtime.setValue\n", "import androidx.compose.runtime.setValue\nimport androidx.compose.runtime.rememberCoroutineScope\n", "queue coroutine scope import")
ui = replace_once(ui, "import androidx.compose.ui.Modifier\n", "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalContext\n", "queue context import")
ui = replace_once(ui, "import kotlinx.coroutines.delay\n", "import kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\n", "queue launch import")
ui = replace_once(
    ui,
    '''    var selectedFilter by rememberSaveable { mutableStateOf("todos") }
    var currentPage by rememberSaveable { mutableStateOf(1) }
    val itemsPerPage = 20
''',
    '''    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedFilter by rememberSaveable { mutableStateOf("todos") }
    var currentPage by rememberSaveable { mutableStateOf(1) }
    var fileError by rememberSaveable { mutableStateOf<String?>(null) }
    val itemsPerPage = 20
''',
    "queue context state",
)
ui = replace_once(
    ui,
    '''                        if (!item.lastError.isNullOrBlank()) {
                            Text("Erro: ${item.lastError}", color = MaterialTheme.colorScheme.error)
                        }
                        if (item.status == "failed" || item.status == "retry_wait") {
''',
    '''                        if (!item.lastError.isNullOrBlank()) {
                            Text("Erro: ${item.lastError}", color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                fileError = null
                                scope.launch {
                                    runCatching { viewModel.createQueueFileSignedUrl(item) }
                                        .onSuccess { signedUrl ->
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(signedUrl)).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    },
                                                )
                                            }.onFailure { fileError = "Nao foi possivel abrir o arquivo neste dispositivo." }
                                        }
                                        .onFailure { throwable ->
                                            fileError = throwable.message ?: "Falha ao gerar link do arquivo."
                                        }
                                }
                            },
                            enabled = !state.adminFeatureLoading && item.arquivoPath.isNotBlank(),
                        ) {
                            Text("Abrir arquivo")
                        }
                        if (item.status == "failed" || item.status == "retry_wait") {
''',
    "queue file button",
)
ui = replace_once(
    ui,
    '''        if (filteredItems.isEmpty()) {
''',
    '''        fileError?.let { message ->
            item {
                WebCard { Text(message, color = MaterialTheme.colorScheme.error) }
            }
        }

        if (filteredItems.isEmpty()) {
''',
    "queue file error",
)
write(ui_path, ui)

print("Stage 4 ERP queue file parity applied")
