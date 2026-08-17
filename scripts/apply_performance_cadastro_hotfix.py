from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: esperado 1 match, encontrado {count}")
    return text.replace(old, new, 1)


editor_path = Path("android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/CadastroEditorDialog.kt")
editor = editor_path.read_text()
editor = replace_once(
    editor,
    "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.withTimeoutOrNull",
    "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext\nimport kotlinx.coroutines.withTimeoutOrNull",
    "imports editor",
)
editor = replace_once(
    editor,
    "private const val LEMMIT_DEPENDENTE_MAX_ATTEMPTS = 3\nprivate const val LEMMIT_DEPENDENTE_TIMEOUT_MS = 12000L\nprivate const val LEMMIT_DEPENDENTE_RETRY_DELAY_MS = 900L",
    "private const val LEMMIT_DEPENDENTE_MAX_ATTEMPTS = 1\nprivate const val LEMMIT_DEPENDENTE_TIMEOUT_MS = 9000L\nprivate const val LEMMIT_DEPENDENTE_RETRY_DELAY_MS = 0L",
    "timeouts lemmit dependente",
)
editor = replace_once(
    editor,
    '''        if (arquivoPath.isNotBlank()) {
            runCatching { viewModel.deleteTempFile(arquivoPath) }
        }
        validateUpload(
            fileName = fileName,
            mimeType = mimeType,
            size = bytes.size.toLong(),
        )
        val uploaded = viewModel.uploadTempFile(
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            prefix = "cadastros/${cadastro.id}",
        )
        val draftAttachment = DraftAttachmentStorage.copyBytesToDraftStorage(
            context = context,
            draftId = cadastro.id,
            originalName = uploaded.nome,
            mimeType = uploaded.mime,
            bytes = bytes,
        )''',
    '''        if (arquivoPath.isNotBlank()) {
            val existingFile = File(arquivoPath)
            if (existingFile.exists()) {
                withContext(Dispatchers.IO) { runCatching { existingFile.delete() } }
            } else {
                runCatching { viewModel.deleteTempFile(arquivoPath) }
            }
        }
        validateUpload(
            fileName = fileName,
            mimeType = mimeType,
            size = bytes.size.toLong(),
        )
        val draftAttachment = withContext(Dispatchers.IO) {
            DraftAttachmentStorage.copyBytesToDraftStorage(
                context = context,
                draftId = cadastro.id,
                originalName = fileName,
                mimeType = mimeType,
                bytes = bytes,
            )
        }''',
    "anexo salvo localmente sem upload duplicado",
)
editor = replace_once(
    editor,
    '''                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Nao foi possivel ler o arquivo.")''',
    '''                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Nao foi possivel ler o arquivo.")
                }''',
    "leitura documento IO",
)
editor = replace_once(
    editor,
    "                val bytes = cameraFile.readBytes()",
    "                val bytes = withContext(Dispatchers.IO) { cameraFile.readBytes() }",
    "leitura camera IO",
)
editor = replace_once(
    editor,
    '''                                    val payload = buildPayload(requireStatus = false) ?: return@launch
                                    saving = true
                                    runCatching { viewModel.updateCadastroRecord(cadastro.id, payload) }
                                        .onSuccess { currentStep = 2 }
                                        .onFailure { throwable ->
                                            localMessage = CadastroApiErrorMapper.mapUserMessage(
                                                throwable.message,
                                                "Falha ao salvar antes de avancar.",
                                            )
                                        }
                                    saving = false''',
    '''                                    val payload = buildPayload(requireStatus = false) ?: return@launch
                                    localMessage = null
                                    currentStep = 2
                                    viewModel.persistCadastroDraftSilently(cadastro.id, payload)''',
    "avanco etapa 2 sem rede bloqueante",
)
editor_path.write_text(editor)


vm_path = Path("android-app/app/src/main/java/br/com/vendamais/mobile/ui/AppViewModel.kt")
vm = vm_path.read_text()
start = vm.index("                }.onSuccess { result ->\n                    val activeSession = ensureFreshSession(session)\n", vm.index("workflowRepository.createDraftFromCpf"))
end = vm.index('                    if (warning.contains("Limite mensal da Lemmit atingido", ignoreCase = true)) {', start)
new = '''                }.onSuccess { result ->
                    val warning = result.warningMessage.orEmpty()
                    val postSuccessNotice = warning
                        .takeIf {
                            it.isNotBlank() &&
                                !it.contains("Lemit", ignoreCase = true) &&
                                !it.contains("Lemmit", ignoreCase = true)
                        }
                    _uiState.update {
                        it.copy(
                            selectedCadastro = result.draft,
                            cadastroWorkspace = it.cadastroWorkspace.copy(
                                operationLoading = false,
                                cpfValue = "",
                                empresaSearchResults = emptyList(),
                            ),
                            errorMessage = null,
                            noticeMessage = postSuccessNotice,
                            pendingCadastroPrompt = null,
                            pendingCadastroActionLoading = false,
                        )
                    }
                    viewModelScope.launch refreshDraftList@{
                        val refreshSession = runCatching { ensureFreshSession(session) }.getOrNull()
                            ?: return@refreshDraftList
                        runCatching {
                            withContext(Dispatchers.IO) { repository.fetchCadastros(refreshSession) }
                        }.onSuccess { refreshed ->
                            _uiState.update { current -> current.copy(cadastros = refreshed) }
                        }.onFailure {
                            Log.w(logTag, "Rascunho criado; refresh da lista ficou para a proxima sincronizacao", it)
                        }
                    }
'''
vm = vm[:start] + new + vm[end:]
vm = replace_once(
    vm,
    "                        val bytes = File(arquivoPathForSend).readBytes()",
    "                        val bytes = withContext(Dispatchers.IO) { File(arquivoPathForSend).readBytes() }",
    "leitura final arquivo IO",
)
old_success = '''                val cadastrosResult = runCatching { repository.fetchCadastros(activeSession) }
                val statsResult = runCatching { repository.fetchCadastroStats(activeSession) }
                val notice = buildString {
                    append("Cadastro enviado com sucesso ao ERP.")
                    if (cadastrosResult.isFailure || statsResult.isFailure) {
                        append(" Houve falha ao atualizar a listagem local, mas o envio foi concluido.")
                    }
                }
                val cadastrosAtualizados = cadastrosResult.getOrElse {
                    Log.w(logTag, "Envio concluido, mas falhou ao atualizar lista de cadastros", it)
                    _uiState.value.cadastros
                }
                val statsAtualizadas = statsResult.getOrElse {
                    Log.w(logTag, "Envio concluido, mas falhou ao atualizar estatisticas", it)
                    _uiState.value.cadastroStats
                }
                Triple(detalheAtualizado, cadastrosAtualizados, statsAtualizadas) to notice
            }.onSuccess { (payload, noticeMessage) ->
                Log.i(logTag, "[$sendTraceId] flowSuccess")
                val (_, cadastrosAtualizados, statsAtualizadas) = payload
                draftUxStateCache.clear(originalCadastroId)
                _uiState.update {
                    it.copy(
                        sendingCadastro = false,
                        selectedCadastro = null,
                        cadastros = cadastrosAtualizados,
                        cadastroStats = statsAtualizadas,
                        errorMessage = null,
                        noticeMessage = noticeMessage,
                        cadastroOverlay = null,
                        activeTab = MainTab.CADASTROS,
                        cadastroTab = CadastroAreaTab.COMPLETOS,
                        cadastroFiltro = CadastroFiltro.ENVIADOS,
                    )
                }
'''
new_success = '''                detalheAtualizado to "Cadastro enviado com sucesso ao ERP."
            }.onSuccess { (_, noticeMessage) ->
                Log.i(logTag, "[$sendTraceId] flowSuccess")
                draftUxStateCache.clear(originalCadastroId)
                _uiState.update {
                    it.copy(
                        sendingCadastro = false,
                        selectedCadastro = null,
                        errorMessage = null,
                        noticeMessage = noticeMessage,
                        cadastroOverlay = null,
                        activeTab = MainTab.CADASTROS,
                        cadastroTab = CadastroAreaTab.COMPLETOS,
                        cadastroFiltro = CadastroFiltro.ENVIADOS,
                    )
                }
                viewModelScope.launch refreshAfterSend@{
                    val refreshSession = runCatching { ensureFreshSession(session) }.getOrNull()
                        ?: return@refreshAfterSend
                    val (cadastrosResult, statsResult) = coroutineScope {
                        val cadastrosDeferred = async { runCatching { repository.fetchCadastros(refreshSession) } }
                        val statsDeferred = async { runCatching { repository.fetchCadastroStats(refreshSession) } }
                        cadastrosDeferred.await() to statsDeferred.await()
                    }
                    _uiState.update { current ->
                        current.copy(
                            cadastros = cadastrosResult.getOrElse {
                                Log.w(logTag, "Envio concluido; falhou refresh da lista em background", it)
                                current.cadastros
                            },
                            cadastroStats = statsResult.getOrElse {
                                Log.w(logTag, "Envio concluido; falhou refresh das estatisticas em background", it)
                                current.cadastroStats
                            },
                        )
                    }
                }
'''
vm = replace_once(vm, old_success, new_success, "sucesso imediato apos ERP")
vm_path.write_text(vm)


repo_path = Path("android-app/app/src/main/java/br/com/vendamais/mobile/data/remote/CadastroWorkflowRepository.kt")
repo = repo_path.read_text()
repo = replace_once(
    repo,
    "withTimeoutOrNull(8000) { canUseLemmit(session, profile.id) }",
    "withTimeoutOrNull(2500) { canUseLemmit(session, profile.id) }",
    "timeout permissao lemmit",
)
repo = replace_once(
    repo,
    "withTimeoutOrNull(12000) { consultarCpfLemmit(session, cpf) }",
    "withTimeoutOrNull(8500) { consultarCpfLemmit(session, cpf) }",
    "timeout consulta lemmit",
)
repo = replace_once(
    repo,
    "withTimeoutOrNull(7000) { CadastroPayloadBuilder.enrichEndereco(endereco, consultarEnderecoCep(session, endereco.cep)) }",
    "withTimeoutOrNull(2000) { CadastroPayloadBuilder.enrichEndereco(endereco, consultarEnderecoCep(session, endereco.cep)) }",
    "timeout enriquecimento CEP",
)
process_pattern = re.compile(
    r"    private suspend fun processDocumentoUpload\(\n        session: SavedSession,\n        cadastro: CadastroDetalhe,\n        funcionarioCadastroId: Int,\n        dependenteId: Int,\n    \) \{.*?\n    \}\n\n    private suspend fun enviarParaErp",
    re.S,
)
process_replacement = '''    private suspend fun processDocumentoUpload(
        session: SavedSession,
        cadastro: CadastroDetalhe,
        funcionarioCadastroId: Int,
        dependenteId: Int,
    ) {
        client.safePost<JsonElement>(
            url = "${AppConfig.supabaseUrl}/functions/v1/erp-enqueue-upload",
            json = json,
            body = buildJsonObject {
                put("cadastroId", cadastro.id)
                put("idFuncionario", funcionarioCadastroId)
                put("idDependente", dependenteId)
                put("arquivoPath", cadastro.arquivoPath)
                put("arquivoNome", cadastro.arquivoNome ?: cadastro.arquivoPath?.substringAfterLast('/'))
                put("tipo", "titular")
            },
        ) {
            applyAuthHeaders(session)
        }
    }

    private suspend fun enviarParaErp'''
repo, count = process_pattern.subn(process_replacement, repo, count=1)
if count != 1:
    raise RuntimeError(f"fila documento: esperado 1 match, encontrado {count}")
repo_path.write_text(repo)


edge_path = Path("supabase/functions/lemit-consulta-pessoa/index.ts")
edge = edge_path.read_text()
edge = replace_once(
    edge,
    "const LEMMIT_COST = 0.12;",
    "const LEMMIT_COST = 0.12;\nconst LEMMIT_REQUEST_TIMEOUT_MS = 7500;",
    "constante timeout edge",
)
old_fetch = '''    const lemmitResponse = await fetch(
      "http://189.84.127.130:8080/webhook/5e534e38-6f87-400b-a441-821559c6c2e9",
      {
        method: "POST",
        headers: {
          "ApiKey": LEMMIT_API_KEY,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          documento: cpfLimpo,
        }),
      }
    );

    const responseData = await lemmitResponse.json();
    statusCode = lemmitResponse.status;'''
new_fetch = '''    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), LEMMIT_REQUEST_TIMEOUT_MS);
    let lemmitResponse: Response;
    let responseData: any;
    try {
      lemmitResponse = await fetch(
        "http://189.84.127.130:8080/webhook/5e534e38-6f87-400b-a441-821559c6c2e9",
        {
          method: "POST",
          headers: {
            "ApiKey": LEMMIT_API_KEY,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            documento: cpfLimpo,
          }),
          signal: controller.signal,
        }
      );
      responseData = await lemmitResponse.json();
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        statusCode = 504;
        errorMessage = "Consulta Lemmit excedeu o tempo limite";
        responseBody = { error: errorMessage, timeout: true, canContinue: true };
        await saveLog(supabase, {
          user_id: userId,
          user_email: userEmail,
          endpoint: "lemit-consulta-pessoa",
          method: "POST",
          request_body: requestBody,
          response_body: responseBody,
          status_code: statusCode,
          success: false,
          error_message: errorMessage,
          duration_ms: Date.now() - startTime,
          cost: 0,
        });
        return new Response(JSON.stringify(responseBody), {
          status: statusCode,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }
      throw error;
    } finally {
      clearTimeout(timeoutId);
    }

    statusCode = lemmitResponse.status;'''
edge = replace_once(edge, old_fetch, new_fetch, "timeout fetch lemmit")
edge_path.write_text(edge)


api_utils_path = Path("supabase/functions/_shared/api-utils.ts")
api_utils = api_utils_path.read_text()
api_utils = replace_once(
    api_utils,
    "    insert: (payload: unknown) => Promise<unknown>;",
    "    insert: (payload: any) => PromiseLike<any>;",
    "tipagem SupabaseLike insert",
)
api_utils_path.write_text(api_utils)

print("Performance hotfix aplicado aos arquivos de produto.")
