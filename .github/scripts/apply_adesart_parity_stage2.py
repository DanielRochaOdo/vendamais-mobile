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


# Models: expose API logs in the same configuration area as Adesart.
models_path = "android-app/app/src/main/java/br/com/vendamais/mobile/data/models/AdminFeatureModels.kt"
text = read(models_path)
api_log_model = '''
@Serializable
data class ApiLogItem(
    val id: String,
    @SerialName("user_email")
    val userEmail: String? = null,
    val endpoint: String = "",
    val method: String = "",
    @SerialName("status_code")
    val statusCode: Int? = null,
    val success: Boolean = false,
    @SerialName("error_message")
    val errorMessage: String? = null,
    @SerialName("duration_ms")
    val durationMs: Long? = null,
    val cost: Double? = null,
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("request_body")
    val requestBody: JsonElement? = null,
    @SerialName("response_body")
    val responseBody: JsonElement? = null,
)

'''
text = replace_once(text, "@Serializable\ndata class AuditLemmitResponse(", api_log_model + "@Serializable\ndata class AuditLemmitResponse(", "ApiLogItem model")
write(models_path, text)

# REST CRUD used by the settings tabs and queue item reprocessing.
repo_path = "android-app/app/src/main/java/br/com/vendamais/mobile/data/remote/SupabaseRepository.kt"
text = read(repo_path)
text = replace_once(text, "import io.ktor.client.request.get\n", "import io.ktor.client.request.get\nimport io.ktor.client.request.delete\n", "delete import")
text = replace_once(text, "import kotlinx.serialization.json.JsonArray\n", "import kotlinx.serialization.json.JsonArray\n", "noop JsonArray") if False else text
anchor = '''    private suspend inline fun <reified T> getList(
'''
methods = '''    suspend fun createPlanoMap(session: SavedSession, payload: JsonObject): br.com.vendamais.mobile.data.models.PlanoMap {
        return client.safePost<List<br.com.vendamais.mobile.data.models.PlanoMap>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/cadastro_planos_map",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
        }.firstOrNull() ?: throw IllegalStateException("Falha ao criar plano.")
    }

    suspend fun updatePlanoMap(session: SavedSession, id: String, payload: JsonObject): br.com.vendamais.mobile.data.models.PlanoMap {
        return client.safePatch<List<br.com.vendamais.mobile.data.models.PlanoMap>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/cadastro_planos_map?id=eq.$id",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
        }.firstOrNull() ?: throw IllegalStateException("Falha ao atualizar plano.")
    }

    suspend fun deletePlanoMap(session: SavedSession, id: String) {
        client.delete("${AppConfig.supabaseUrl}/rest/v1/cadastro_planos_map?id=eq.$id") {
            applyAuthHeaders(session)
        }
    }

    suspend fun createParentescoMap(session: SavedSession, payload: JsonObject): br.com.vendamais.mobile.data.models.ParentescoMap {
        return client.safePost<List<br.com.vendamais.mobile.data.models.ParentescoMap>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/cadastro_parentesco_map",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
        }.firstOrNull() ?: throw IllegalStateException("Falha ao criar parentesco.")
    }

    suspend fun updateParentescoMap(session: SavedSession, id: String, payload: JsonObject): br.com.vendamais.mobile.data.models.ParentescoMap {
        return client.safePatch<List<br.com.vendamais.mobile.data.models.ParentescoMap>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/cadastro_parentesco_map?id=eq.$id",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
        }.firstOrNull() ?: throw IllegalStateException("Falha ao atualizar parentesco.")
    }

    suspend fun deleteParentescoMap(session: SavedSession, id: String) {
        client.delete("${AppConfig.supabaseUrl}/rest/v1/cadastro_parentesco_map?id=eq.$id") {
            applyAuthHeaders(session)
        }
    }

    suspend fun createStatusAdesao(session: SavedSession, payload: JsonObject): br.com.vendamais.mobile.data.models.StatusAdesao {
        return client.safePost<List<br.com.vendamais.mobile.data.models.StatusAdesao>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/status_adesoes",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
        }.firstOrNull() ?: throw IllegalStateException("Falha ao criar status.")
    }

    suspend fun updateStatusAdesao(session: SavedSession, id: String, payload: JsonObject): br.com.vendamais.mobile.data.models.StatusAdesao {
        return client.safePatch<List<br.com.vendamais.mobile.data.models.StatusAdesao>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/status_adesoes?id=eq.$id",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
        }.firstOrNull() ?: throw IllegalStateException("Falha ao atualizar status.")
    }

    suspend fun deleteStatusAdesao(session: SavedSession, id: String) {
        client.delete("${AppConfig.supabaseUrl}/rest/v1/status_adesoes?id=eq.$id") {
            applyAuthHeaders(session)
        }
    }

    suspend fun fetchApiLogs(
        session: SavedSession,
        success: Boolean? = null,
        startIso: String? = null,
        endIso: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): List<br.com.vendamais.mobile.data.models.ApiLogItem> {
        return getList(
            path = "api_logs",
            session = session,
            query = {
                parameter("select", "id,user_email,endpoint,method,status_code,success,error_message,duration_ms,cost,created_at,request_body,response_body")
                success?.let { parameter("success", "eq.$it") }
                startIso?.takeIf { it.isNotBlank() }?.let { parameter("created_at", "gte.$it") }
                endIso?.takeIf { it.isNotBlank() }?.let { parameter("created_at", "lte.$it") }
                parameter("order", "created_at.desc")
                parameter("limit", limit)
                parameter("offset", offset)
            },
        )
    }

    suspend fun reprocessUploadQueueItem(session: SavedSession, id: String): ErpUploadQueueItem {
        val payload = buildJsonObject {
            put("status", "queued")
            put("attempts", 0)
            put("next_attempt_at", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString())
            put("last_error", JsonNull)
        }
        return client.safePatch<List<ErpUploadQueueItem>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/erp_upload_queue?id=eq.$id",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
        }.firstOrNull() ?: throw IllegalStateException("Falha ao reprocessar item da fila.")
    }

'''
text = replace_once(text, anchor, methods + anchor, "settings CRUD repository methods")
text = text.replace("limit: Int = 100,\n    ): List<ErpUploadQueueItem>", "limit: Int = 500,\n    ): List<ErpUploadQueueItem>", 1)
write(repo_path, text)

# ViewModel state + operations.
vm_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/AppViewModel.kt"
text = read(vm_path)
text = replace_once(text, "import br.com.vendamais.mobile.data.models.AdminUser\n", "import br.com.vendamais.mobile.data.models.AdminUser\nimport br.com.vendamais.mobile.data.models.ApiLogItem\n", "ApiLogItem import")
text = replace_once(text, "    val auditLemmit: AuditLemmitResponse = AuditLemmitResponse(),\n", "    val auditLemmit: AuditLemmitResponse = AuditLemmitResponse(),\n    val apiLogs: List<ApiLogItem> = emptyList(),\n", "apiLogs state")
anchor = '''    suspend fun updateCadastroConfig(payload: JsonObject): CadastroConfig {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        val updated = repository.updateCadastroConfig(activeSession, payload)
        _uiState.update {
            it.copy(cadastroWorkspace = it.cadastroWorkspace.copy(config = updated))
        }
        return updated
    }
'''
extra = anchor + '''
    suspend fun createPlanoMap(payload: JsonObject): PlanoMap {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        val created = repository.createPlanoMap(activeSession, payload)
        val items = workflowRepository.fetchPlanosMap(activeSession)
        _uiState.update { it.copy(planosMap = items) }
        return created
    }

    suspend fun updatePlanoMap(id: String, payload: JsonObject): PlanoMap {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        val updated = repository.updatePlanoMap(activeSession, id, payload)
        val items = workflowRepository.fetchPlanosMap(activeSession)
        _uiState.update { it.copy(planosMap = items) }
        return updated
    }

    suspend fun deletePlanoMap(id: String) {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        repository.deletePlanoMap(activeSession, id)
        val items = workflowRepository.fetchPlanosMap(activeSession)
        _uiState.update { it.copy(planosMap = items) }
    }

    suspend fun createParentescoMap(payload: JsonObject): ParentescoMap {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        val created = repository.createParentescoMap(activeSession, payload)
        val items = workflowRepository.fetchParentescosMap(activeSession)
        _uiState.update { it.copy(parentescosMap = items) }
        return created
    }

    suspend fun updateParentescoMap(id: String, payload: JsonObject): ParentescoMap {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        val updated = repository.updateParentescoMap(activeSession, id, payload)
        val items = workflowRepository.fetchParentescosMap(activeSession)
        _uiState.update { it.copy(parentescosMap = items) }
        return updated
    }

    suspend fun deleteParentescoMap(id: String) {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        repository.deleteParentescoMap(activeSession, id)
        val items = workflowRepository.fetchParentescosMap(activeSession)
        _uiState.update { it.copy(parentescosMap = items) }
    }

    suspend fun createStatusAdesao(payload: JsonObject): StatusAdesao {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        val created = repository.createStatusAdesao(activeSession, payload)
        val items = workflowRepository.fetchStatusAdesoes(activeSession)
        _uiState.update { it.copy(statusAdesoes = items) }
        return created
    }

    suspend fun updateStatusAdesao(id: String, payload: JsonObject): StatusAdesao {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        val updated = repository.updateStatusAdesao(activeSession, id, payload)
        val items = workflowRepository.fetchStatusAdesoes(activeSession)
        _uiState.update { it.copy(statusAdesoes = items) }
        return updated
    }

    suspend fun deleteStatusAdesao(id: String) {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        repository.deleteStatusAdesao(activeSession, id)
        val items = workflowRepository.fetchStatusAdesoes(activeSession)
        _uiState.update { it.copy(statusAdesoes = items) }
    }

    fun loadApiLogs(
        success: Boolean? = null,
        startIso: String? = null,
        endIso: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ) {
        val session = currentSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(adminFeatureLoading = true, errorMessage = null) }
            runCatching {
                val activeSession = ensureFreshSession(session)
                repository.fetchApiLogs(activeSession, success, startIso, endIso, limit, offset)
            }.onSuccess { logs ->
                _uiState.update { it.copy(apiLogs = logs, adminFeatureLoading = false) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(adminFeatureLoading = false, errorMessage = throwable.message) }
            }
        }
    }

    fun reprocessUploadQueueItem(id: String) {
        val session = currentSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(adminFeatureLoading = true, errorMessage = null) }
            runCatching {
                val activeSession = ensureFreshSession(session)
                repository.reprocessUploadQueueItem(activeSession, id)
                repository.fetchErpUploadQueue(activeSession)
            }.onSuccess { items ->
                _uiState.update {
                    it.copy(
                        uploadQueue = items,
                        adminFeatureLoading = false,
                        noticeMessage = "Item marcado para reprocessamento.",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(adminFeatureLoading = false, errorMessage = throwable.message) }
            }
        }
    }
'''
text = replace_once(text, anchor, extra, "ViewModel settings methods")
text = text.replace("fun loadCadastrosExcluidos(limit: Int = 100)", "fun loadCadastrosExcluidos(limit: Int = 1000)")
write(vm_path, text)

# Settings UI: mirror Adesart tabs and CRUD permissions.
settings_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/SettingsScreen.kt"
settings_content = r'''package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.data.models.ApiLogItem
import br.com.vendamais.mobile.data.models.ParentescoMap
import br.com.vendamais.mobile.data.models.PlanoMap
import br.com.vendamais.mobile.data.models.StatusAdesao
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard
import br.com.vendamais.mobile.ui.components.bringIntoViewOnFocus
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

private enum class SettingsSection { GERAL, PLANOS, PARENTESCO, STATUS, LOGS }

@Composable
fun SettingsScreen(state: AppUiState, viewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(SettingsSection.GERAL) }
    val role = state.profile?.role
    val canModifyMappings = role in setOf("ADMINISTRADOR", "GERENTE", "CADASTRO", "ADESIONISTA")
    val canDeleteMappings = role == "ADMINISTRADOR"

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenHeading(title = "Configuracoes", subtitle = "Gerencie as regras e tabelas do cadastro") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionButton("Geral", section == SettingsSection.GERAL, Modifier.weight(1f)) { section = SettingsSection.GERAL }
                    SectionButton("Planos", section == SettingsSection.PLANOS, Modifier.weight(1f)) { section = SettingsSection.PLANOS }
                    SectionButton("Parentesco", section == SettingsSection.PARENTESCO, Modifier.weight(1f)) { section = SettingsSection.PARENTESCO }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionButton("Status", section == SettingsSection.STATUS, Modifier.weight(1f)) { section = SettingsSection.STATUS }
                    SectionButton("Logs API", section == SettingsSection.LOGS, Modifier.weight(1f)) { section = SettingsSection.LOGS }
                }
            }
        }

        when (section) {
            SettingsSection.GERAL -> {
                val config = state.cadastroWorkspace.config
                if (config == null) item { AdminLoadingCard() } else {
                    item { ConfigSwitchCard("Consulta Lemmit", "Consulta a Lemmit no fluxo de cadastro.", config.ativarLemmit) { v -> scope.launch { viewModel.updateCadastroConfig(buildJsonObject { put("ativar_lemmit", v) }) } } }
                    item { ConfigSwitchCard("Exigir Envio de Arquivo", "Quando ativo, o envio exige documento anexado.", config.exigirArquivo) { v -> scope.launch { viewModel.updateCadastroConfig(buildJsonObject { put("exigir_arquivo", v) }) } } }
                    item { ConfigSwitchCard("Lemmit no Dependente", "Preenchimento automatico no dependente do novo cadastro.", config.lemmitDependente) { v -> scope.launch { viewModel.updateCadastroConfig(buildJsonObject { put("lemmit_dependente", v) }) } } }
                    item { ConfigSwitchCard("Lemmit Incluir Dep.", "Preenchimento automatico na inclusao de dependente.", config.lemmitInclusaoDependente) { v -> scope.launch { viewModel.updateCadastroConfig(buildJsonObject { put("lemmit_inclusao_dependente", v) }) } } }
                    item { ConfigListEditorCard("Situacoes que Barram Cadastro", "Codigos que impedem recadastro.", config.situacoesQueBarram.joinToString(", ")) { value -> scope.launch { viewModel.updateCadastroConfig(buildJsonObject { put("situacoes_que_barram", intJsonArray(value.split(',').mapNotNull { it.trim().toIntOrNull() })) }) } } }
                    item { ConfigListEditorCard("Planos Validos", "Planos permitidos para recadastro.", config.planosValidos.joinToString(", ")) { value -> scope.launch { viewModel.updateCadastroConfig(buildJsonObject { put("planos_validos", intJsonArray(value.split(',').mapNotNull { it.trim().toIntOrNull() })) }) } } }
                    item { ConfigListEditorCard("Planos Ocultos", "Codigos que nao aparecem na selecao.", config.planosOcultos.joinToString(", ")) { value -> scope.launch { viewModel.updateCadastroConfig(buildJsonObject { put("planos_ocultos", stringJsonArray(value.split(',').map { it.trim() }.filter { it.isNotBlank() })) }) } } }
                    item { ConfigListEditorCard("Codigos de Empresa Invalidos", "Empresas invalidas para novos cadastros.", config.codigosEmpresaInvalidos.joinToString(", ")) { value -> scope.launch { viewModel.updateCadastroConfig(buildJsonObject { put("codigos_empresa_invalidos", stringJsonArray(value.split(',').map { it.trim() }.filter { it.isNotBlank() })) }) } } }
                }
            }
            SettingsSection.PLANOS -> item { PlanosEditor(state, viewModel, canModifyMappings, canDeleteMappings) }
            SettingsSection.PARENTESCO -> item { ParentescosEditor(state, viewModel, canModifyMappings, canDeleteMappings) }
            SettingsSection.STATUS -> item { StatusEditor(state, viewModel) }
            SettingsSection.LOGS -> item { ApiLogsEditor(state, viewModel) }
        }
    }
}

@Composable
private fun SectionButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(label) }
    else TextButton(onClick = onClick, modifier = modifier) { Text(label) }
}

@Composable
private fun PlanosEditor(state: AppUiState, viewModel: AppViewModel, canModify: Boolean, canDelete: Boolean) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<PlanoMap?>(null) }
    var creating by remember { mutableStateOf(false) }
    WebCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Planos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (canModify) Button(onClick = { creating = true }) { Text("Adicionar Plano") }
            state.planosMap.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.planoId} - ${item.nomeExibicao}", fontWeight = FontWeight.Medium)
                        Text("${item.regraValor} | ${if (item.ativo) "Ativo" else "Inativo"}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (canModify) TextButton(onClick = { editing = item }) { Text("Editar") }
                    if (canDelete) TextButton(onClick = { scope.launch { viewModel.deletePlanoMap(item.id) } }) { Text("Excluir") }
                }
            }
            if (state.planosMap.isEmpty()) Text("Nenhum plano cadastrado.")
        }
    }
    if (creating || editing != null) {
        PlanoDialog(editing, onDismiss = { creating = false; editing = null }) { id, nome, registro, regra, ativo ->
            scope.launch {
                val payload = buildJsonObject {
                    put("plano_id", id); put("nome_exibicao", nome); put("registro_produto", registro); put("regra_valor", regra); put("ativo", ativo)
                }
                if (editing == null) viewModel.createPlanoMap(payload) else viewModel.updatePlanoMap(editing!!.id, payload)
                creating = false; editing = null
            }
        }
    }
}

@Composable
private fun PlanoDialog(item: PlanoMap?, onDismiss: () -> Unit, onSave: (Int, String, String, String, Boolean) -> Unit) {
    var id by remember(item?.id) { mutableStateOf(item?.planoId?.toString().orEmpty()) }
    var nome by remember(item?.id) { mutableStateOf(item?.nomeExibicao.orEmpty()) }
    var registro by remember(item?.id) { mutableStateOf(item?.registroProduto.orEmpty()) }
    var regra by remember(item?.id) { mutableStateOf(item?.regraValor ?: "titular") }
    var ativo by remember(item?.id) { mutableStateOf(item?.ativo ?: true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (item == null) "Novo Plano" else "Editar Plano") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(id, { id = it.filter(Char::isDigit) }, label = { Text("ID do Plano no ERP") }, enabled = item == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(nome, { nome = it }, label = { Text("Nome para Exibicao") })
            OutlinedTextField(registro, { registro = it }, label = { Text("Registro do Produto") })
            SelectionField("Regra de Valor", regra, listOf("titular" to "Titular", "dependente" to "Dependente", "agregado" to "Agregado", "fixo" to "Fixo", "manual" to "Manual"), onSelected = { regra = it })
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(ativo, { ativo = it }); Text("Ativo") }
        }
    }, confirmButton = { TextButton(onClick = { val parsed = id.toIntOrNull() ?: 0; if (parsed > 0 && nome.isNotBlank()) onSave(parsed, nome.trim(), registro.trim(), regra, ativo) }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun ParentescosEditor(state: AppUiState, viewModel: AppViewModel, canModify: Boolean, canDelete: Boolean) {
    val scope = rememberCoroutineScope(); var editing by remember { mutableStateOf<ParentescoMap?>(null) }; var creating by remember { mutableStateOf(false) }
    WebCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Parentesco", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (canModify) Button(onClick = { creating = true }) { Text("Adicionar Parentesco") }
        state.parentescosMap.forEach { item -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) { Text("${item.parentescoId} - ${item.label}"); Text(if (item.ativo) "Ativo" else "Inativo", style = MaterialTheme.typography.bodySmall) }
            if (canModify) TextButton(onClick = { editing = item }) { Text("Editar") }
            if (canDelete) TextButton(onClick = { scope.launch { viewModel.deleteParentescoMap(item.id) } }) { Text("Excluir") }
        } }
        if (state.parentescosMap.isEmpty()) Text("Nenhum parentesco cadastrado.")
    } }
    if (creating || editing != null) ParentescoDialog(editing, { creating = false; editing = null }) { id, label, ativo -> scope.launch {
        val payload = buildJsonObject { put("parentesco_id", id); put("label", label); put("ativo", ativo) }
        if (editing == null) viewModel.createParentescoMap(payload) else viewModel.updateParentescoMap(editing!!.id, payload)
        creating = false; editing = null
    } }
}

@Composable
private fun ParentescoDialog(item: ParentescoMap?, onDismiss: () -> Unit, onSave: (Int, String, Boolean) -> Unit) {
    var id by remember(item?.id) { mutableStateOf(item?.parentescoId?.toString().orEmpty()) }; var label by remember(item?.id) { mutableStateOf(item?.label.orEmpty()) }; var ativo by remember(item?.id) { mutableStateOf(item?.ativo ?: true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (item == null) "Novo Parentesco" else "Editar Parentesco") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(id, { id = it.filter(Char::isDigit) }, label = { Text("ID do Parentesco no ERP") }, enabled = item == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(label, { label = it }, label = { Text("Label para Exibicao") })
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(ativo, { ativo = it }); Text("Ativo") }
    } }, confirmButton = { TextButton(onClick = { val parsed = id.toIntOrNull() ?: 0; if (parsed > 0 && label.isNotBlank()) onSave(parsed, label.trim(), ativo) }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun StatusEditor(state: AppUiState, viewModel: AppViewModel) {
    val scope = rememberCoroutineScope(); var editing by remember { mutableStateOf<StatusAdesao?>(null) }; var creating by remember { mutableStateOf(false) }
    WebCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Status de Adesoes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Button(onClick = { creating = true }) { Text("Adicionar Status") }
        state.statusAdesoes.forEach { status -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) { Text(status.nome, color = parseColor(status.cor)); Text("${status.cor} | ordem ${status.ordem}", style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { editing = status }) { Text("Editar") }
            TextButton(onClick = { scope.launch { viewModel.deleteStatusAdesao(status.id) } }) { Text("Excluir") }
        } }
        if (state.statusAdesoes.isEmpty()) Text("Nenhum status cadastrado.")
    } }
    if (creating || editing != null) StatusDialog(editing, { creating = false; editing = null }, state.statusAdesoes.maxOfOrNull { it.ordem } ?: 0) { nome, cor, ordem -> scope.launch {
        val payload = buildJsonObject { put("nome", nome); put("cor", cor); put("ordem", ordem) }
        if (editing == null) viewModel.createStatusAdesao(payload) else viewModel.updateStatusAdesao(editing!!.id, payload)
        creating = false; editing = null
    } }
}

@Composable
private fun StatusDialog(item: StatusAdesao?, onDismiss: () -> Unit, maxOrder: Int, onSave: (String, String, Int) -> Unit) {
    var nome by remember(item?.id) { mutableStateOf(item?.nome.orEmpty()) }; var cor by remember(item?.id) { mutableStateOf(item?.cor ?: "#6B7280") }; var ordem by remember(item?.id) { mutableStateOf((item?.ordem ?: maxOrder + 1).toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (item == null) "Novo Status" else "Editar Status") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(nome, { nome = it }, label = { Text("Nome do Status") }); OutlinedTextField(cor, { cor = it }, label = { Text("Cor (#RRGGBB)") }); OutlinedTextField(ordem, { ordem = it.filter(Char::isDigit) }, label = { Text("Ordem") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    } }, confirmButton = { TextButton(onClick = { if (nome.isNotBlank()) onSave(nome.trim(), cor.trim().ifBlank { "#6B7280" }, ordem.toIntOrNull() ?: maxOrder + 1) }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun ApiLogsEditor(state: AppUiState, viewModel: AppViewModel) {
    var filter by rememberSaveable { mutableStateOf("all") }; var start by rememberSaveable { mutableStateOf("") }; var end by rememberSaveable { mutableStateOf("") }; var page by rememberSaveable { mutableStateOf(1) }; var selected by remember { mutableStateOf<ApiLogItem?>(null) }
    val size = 100
    fun load() {
        val success = when (filter) { "success" -> true; "error" -> false; else -> null }
        viewModel.loadApiLogs(success, start.takeIf { it.isNotBlank() }?.let { "${it}T00:00:00Z" }, end.takeIf { it.isNotBlank() }?.let { "${it}T23:59:59Z" }, size, (page - 1) * size)
    }
    LaunchedEffect(filter, start, end, page) { load() }
    WebCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Logs de API", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SelectionField("Status", filter, listOf("all" to "Todos", "success" to "Sucesso", "error" to "Erros"), onSelected = { filter = it; page = 1 })
        OutlinedTextField(start, { start = it; page = 1 }, label = { Text("Data Inicio (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(end, { end = it; page = 1 }, label = { Text("Data Fim (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        state.apiLogs.forEach { log -> TextButton(onClick = { selected = log }, modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.fillMaxWidth()) { Text("${if (log.success) "OK" else "ERRO"} ${log.endpoint} (${log.statusCode ?: "-"})", fontWeight = FontWeight.Medium); Text("${log.userEmail ?: "Anonimo"} | ${formatLogDate(log.createdAt)} | ${log.durationMs ?: 0}ms", style = MaterialTheme.typography.bodySmall); log.errorMessage?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } } } }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(onClick = { if (page > 1) page-- }, enabled = page > 1) { Text("Anterior") }; Text("Pagina $page"); TextButton(onClick = { page++ }, enabled = state.apiLogs.size == size) { Text("Proxima") } }
    } }
    selected?.let { log -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(log.endpoint) }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Metodo: ${log.method}"); Text("Status: ${log.statusCode ?: "-"}"); Text("Usuario: ${log.userEmail ?: "Anonimo"}"); Text("Duracao: ${log.durationMs ?: 0}ms"); Text("Custo: ${log.cost ?: 0.0}"); log.errorMessage?.let { Text("Erro: $it") }; Text("Request: ${log.requestBody ?: "-"}", style = MaterialTheme.typography.bodySmall); Text("Response: ${log.responseBody ?: "-"}", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Fechar") } }) }
}

@Composable
private fun ConfigSwitchCard(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) { WebCard { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(description, style = MaterialTheme.typography.bodySmall) }; Switch(checked, onCheckedChange) } } }

@Composable
private fun ConfigListEditorCard(title: String, description: String, value: String, onSave: (String) -> Unit) { var editing by remember { mutableStateOf(false) }; var text by remember(value) { mutableStateOf(value) }; WebCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(description, style = MaterialTheme.typography.bodySmall); if (editing) { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()); Row { Button(onClick = { onSave(text); editing = false }) { Text("Salvar") }; TextButton(onClick = { editing = false }) { Text("Cancelar") } } } else { Text(value.ifBlank { "Nao configurado" }); TextButton(onClick = { editing = true }) { Text("Editar") } } } } }

private fun formatLogDate(value: String): String = runCatching { java.time.OffsetDateTime.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) }.getOrDefault(value)
'''
write(settings_path, settings_content)

# Lemmit audit pagination + Adesart default month window.
audit_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/AuditoriaLemmitScreen.kt"
audit = read(audit_path)
audit = replace_once(audit, '    var endDate by rememberSaveable { mutableStateOf(java.time.LocalDate.now().toString()) }\n', '    var endDate by rememberSaveable { mutableStateOf(java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).toString()) }\n    var currentPage by rememberSaveable { mutableStateOf(1) }\n    val itemsPerPage = 20\n', "audit pagination state")
audit = replace_once(audit, '''    LaunchedEffect(Unit) {
        val startIso = "${startDate}T00:00:00Z"
        val endIso = "${endDate}T23:59:59Z"
        viewModel.loadAuditLemmit(startIso = startIso, endIso = endIso)
    }
''', '''    LaunchedEffect(currentPage) {
        val startIso = "${startDate}T00:00:00Z"
        val endIso = "${endDate}T00:00:00Z"
        viewModel.loadAuditLemmit(startIso = startIso, endIso = endIso, limit = itemsPerPage, offset = (currentPage - 1) * itemsPerPage)
    }
''', "audit initial load")
audit = replace_once(audit, '''                            val startIso = "${startDate}T00:00:00Z"
                            val endIso = "${endDate}T23:59:59Z"
                            viewModel.loadAuditLemmit(startIso = startIso, endIso = endIso)
''', '''                            currentPage = 1
                            val startIso = "${startDate}T00:00:00Z"
                            val endIso = "${endDate}T00:00:00Z"
                            viewModel.loadAuditLemmit(startIso = startIso, endIso = endIso, limit = itemsPerPage, offset = 0)
''', "audit filter load")
audit = replace_once(audit, '''        if (audit.ultimasConsultas.isNotEmpty()) {
            items(audit.ultimasConsultas) { consulta ->
''', '''        if (audit.ultimasConsultas.isNotEmpty()) {
            items(audit.ultimasConsultas) { consulta ->
''', "audit items noop")
audit = replace_once(audit, '''            }
        }
    }
}

private fun formatCurrency''', '''            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { if (currentPage > 1) currentPage-- }, enabled = currentPage > 1) { Text("Anterior") }
                    Text("Pagina $currentPage")
                    Button(onClick = { currentPage++ }, enabled = audit.ultimasConsultas.size == itemsPerPage) { Text("Proxima") }
                }
            }
        }
    }
}

private fun formatCurrency''', "audit pager footer")
write(audit_path, audit)

# Queue: local 20-item pagination, automatic refresh, per-item reprocess.
queue_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/FilaUploadErpScreen.kt"
queue = read(queue_path)
queue = replace_once(queue, "import androidx.compose.runtime.setValue\n", "import androidx.compose.runtime.setValue\nimport kotlinx.coroutines.delay\n", "queue delay import")
queue = replace_once(queue, '    var selectedFilter by rememberSaveable { mutableStateOf("todos") }\n', '    var selectedFilter by rememberSaveable { mutableStateOf("todos") }\n    var currentPage by rememberSaveable { mutableStateOf(1) }\n    val itemsPerPage = 20\n', "queue page state")
queue = replace_once(queue, '''    LaunchedEffect(Unit) {
        viewModel.loadUploadQueue()
    }
''', '''    LaunchedEffect(Unit) {
        while (true) {
            viewModel.loadUploadQueue()
            delay(5000)
        }
    }
''', "queue auto refresh")
queue = replace_once(queue, '''    val filteredItems = state.uploadQueue.filter { item ->
        selectedFilter == "todos" || item.status == selectedFilter
    }
''', '''    val filteredItems = state.uploadQueue.filter { item ->
        selectedFilter == "todos" || item.status == selectedFilter
    }
    val totalPages = ((filteredItems.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)
    if (currentPage > totalPages) currentPage = totalPages
    val pagedItems = filteredItems.drop((currentPage - 1) * itemsPerPage).take(itemsPerPage)
''', "queue paged list")
queue = queue.replace('onSelected = { selectedFilter = it },', 'onSelected = { selectedFilter = it; currentPage = 1 },', 1)
queue = queue.replace('if (filteredItems.isEmpty()) {', 'if (filteredItems.isEmpty()) {', 1)
queue = queue.replace('items(filteredItems) { item ->', 'items(pagedItems) { item ->', 1)
queue = replace_once(queue, '''                        if (!item.lastError.isNullOrBlank()) {
                            Text("Erro: ${item.lastError}", color = MaterialTheme.colorScheme.error)
                        }
''', '''                        if (!item.lastError.isNullOrBlank()) {
                            Text("Erro: ${item.lastError}", color = MaterialTheme.colorScheme.error)
                        }
                        if (item.status == "failed" || item.status == "retry_wait") {
                            Button(onClick = { viewModel.reprocessUploadQueueItem(item.id) }, enabled = !state.adminFeatureLoading) {
                                Text("Reprocessar item")
                            }
                        }
''', "queue per item retry")
queue = replace_once(queue, '''            }
        }
    }
}

private fun formatDateTime''', '''            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { if (currentPage > 1) currentPage-- }, enabled = currentPage > 1) { Text("Anterior") }
                    Text("Pagina $currentPage de $totalPages")
                    Button(onClick = { if (currentPage < totalPages) currentPage++ }, enabled = currentPage < totalPages) { Text("Proxima") }
                }
            }
        }
    }
}

private fun formatDateTime''', "queue pager")
write(queue_path, queue)

# Deleted adherences: filters, pagination and structured detail instead of raw JSON.
deleted_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/AdesoesExcluidasScreen.kt"
deleted_content = r'''package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.data.models.CadastroExcluidoItem
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.OffsetDateTime

@Composable
fun AdesoesExcluidasScreen(state: AppUiState, viewModel: AppViewModel) {
    var selected by remember { mutableStateOf<CadastroExcluidoItem?>(null) }
    var nome by rememberSaveable { mutableStateOf("") }; var cpf by rememberSaveable { mutableStateOf("") }; var start by rememberSaveable { mutableStateOf("") }; var end by rememberSaveable { mutableStateOf("") }; var exclusor by rememberSaveable { mutableStateOf("") }; var page by rememberSaveable { mutableStateOf(1) }
    val perPage = 15
    LaunchedEffect(Unit) { viewModel.loadCadastrosExcluidos(1000) }
    val filtered = state.cadastrosExcluidos.filter { item ->
        val data = runCatching { item.dadosCadastro.jsonObject }.getOrNull()
        val title = data?.get("nome")?.jsonPrimitive?.contentOrNull.orEmpty()
        val dependentes = runCatching { data?.get("dependentes")?.jsonArray }.getOrNull().orEmpty()
        val matchNome = nome.isBlank() || title.contains(nome, true) || dependentes.any { runCatching { it.jsonObject["nome"]?.jsonPrimitive?.contentOrNull.orEmpty().contains(nome, true) }.getOrDefault(false) }
        val cpfData = data?.get("cpf")?.jsonPrimitive?.contentOrNull.orEmpty().filter(Char::isDigit)
        val matchCpf = cpf.filter(Char::isDigit).let { it.isBlank() || cpfData.contains(it) }
        val date = runCatching { OffsetDateTime.parse(item.excluidoEm).toLocalDate() }.getOrNull()
        val matchStart = start.takeIf { it.isNotBlank() }?.let { s -> runCatching { !date!!.isBefore(LocalDate.parse(s)) }.getOrDefault(false) } ?: true
        val matchEnd = end.takeIf { it.isNotBlank() }?.let { e -> runCatching { !date!!.isAfter(LocalDate.parse(e)) }.getOrDefault(false) } ?: true
        val matchExclusor = exclusor.isBlank() || item.excluidoPor == exclusor
        matchNome && matchCpf && matchStart && matchEnd && matchExclusor
    }
    val totalPages = ((filtered.size + perPage - 1) / perPage).coerceAtLeast(1); if (page > totalPages) page = totalPages
    val paged = filtered.drop((page - 1) * perPage).take(perPage)
    val exclusores = state.cadastrosExcluidos.distinctBy { it.excluidoPor }.sortedBy { it.excluidoPorNome }

    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeading("Adesoes Excluidas", "Historico de exclusoes logicas com motivo e auditoria.") }
        item { WebCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Filtros", fontWeight = FontWeight.SemiBold); OutlinedTextField(nome, { nome = it; page = 1 }, modifier = Modifier.fillMaxWidth(), label = { Text("Nome Titular/Dependente") }); OutlinedTextField(cpf, { cpf = it.filter(Char::isDigit).take(11); page = 1 }, modifier = Modifier.fillMaxWidth(), label = { Text("CPF Titular") }); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(start, { start = it; page = 1 }, modifier = Modifier.weight(1f), label = { Text("Data Inicio") }); OutlinedTextField(end, { end = it; page = 1 }, modifier = Modifier.weight(1f), label = { Text("Data Fim") }) }; SelectionField("Excluido por", exclusores.firstOrNull { it.excluidoPor == exclusor }?.excluidoPorNome ?: "Todos", listOf("" to "Todos") + exclusores.map { it.excluidoPor to it.excluidoPorNome }, onSelected = { exclusor = it; page = 1 }); TextButton(onClick = { nome = ""; cpf = ""; start = ""; end = ""; exclusor = ""; page = 1 }) { Text("Limpar filtros") }; Text("Mostrando ${paged.size} de ${filtered.size}")
        } } }
        if (paged.isEmpty()) item { WebCard { Text("Nenhuma exclusao encontrada.") } } else items(paged) { item ->
            val obj = runCatching { item.dadosCadastro.jsonObject }.getOrNull(); WebCard(modifier = Modifier.clickable { selected = item }) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(obj?.get("nome")?.jsonPrimitive?.contentOrNull ?: "Nome nao informado", fontWeight = FontWeight.SemiBold); Text("Empresa: ${obj?.get("empresa_nome")?.jsonPrimitive?.contentOrNull ?: "-"}"); Text("Vendedor: ${obj?.get("vendedor_nome")?.jsonPrimitive?.contentOrNull ?: "-"}"); Text("Motivo: ${item.motivoExclusao}", color = MaterialTheme.colorScheme.error); Text("Excluido por ${item.excluidoPorNome} (${item.excluidoPorRole})"); Text(formatDateTime(item.excluidoEm)) } }
        }
        item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Button(onClick = { if (page > 1) page-- }, enabled = page > 1) { Text("Anterior") }; Text("Pagina $page de $totalPages"); Button(onClick = { if (page < totalPages) page++ }, enabled = page < totalPages) { Text("Proxima") } } }
    }
    selected?.let { current ->
        val obj = runCatching { current.dadosCadastro.jsonObject }.getOrNull(); val deps = runCatching { obj?.get("dependentes")?.jsonArray }.getOrNull().orEmpty()
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(obj?.get("nome")?.jsonPrimitive?.contentOrNull ?: "Detalhes da exclusao") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("CPF: ${obj?.get("cpf")?.jsonPrimitive?.contentOrNull ?: "-"}"); Text("Empresa: ${obj?.get("empresa_nome")?.jsonPrimitive?.contentOrNull ?: "-"}"); Text("Vendedor: ${obj?.get("vendedor_nome")?.jsonPrimitive?.contentOrNull ?: "-"}"); Text("Tipo: ${obj?.get("tipo_cadastro")?.jsonPrimitive?.contentOrNull ?: "-"}"); Text("Motivo: ${current.motivoExclusao}", color = MaterialTheme.colorScheme.error); Text("Excluido por: ${current.excluidoPorNome} (${current.excluidoPorRole})"); Text("Data: ${formatDateTime(current.excluidoEm)}"); if (deps.isNotEmpty()) { Text("Dependentes", fontWeight = FontWeight.SemiBold); deps.forEach { dep -> val d = runCatching { it.jsonObject }.getOrNull(); Text("• ${d?.get("nome")?.jsonPrimitive?.contentOrNull ?: "Dependente"} - ${d?.get("cpf")?.jsonPrimitive?.contentOrNull ?: ""}") } } } }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Fechar") } })
    }
}

private fun formatDateTime(value: String): String = runCatching { OffsetDateTime.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) }.getOrDefault(value)
'''
write(deleted_path, deleted_content)

print("Stage 2 parity patch applied")
