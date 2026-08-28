from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    path.write_text(text.replace(old, new, 1))
    print(f"updated {label}")


def replace_span(path: Path, start: str, end: str, replacement: str, label: str) -> None:
    text = path.read_text()
    start_index = text.find(start)
    if start_index < 0:
        raise SystemExit(f"{label}: start marker not found")
    end_index = text.find(end, start_index)
    if end_index < 0:
        raise SystemExit(f"{label}: end marker not found")
    path.write_text(text[:start_index] + replacement + text[end_index:])
    print(f"updated {label}")


# Android: prevent completed CPF duplicates and keep cadastro pending while attachment is queued.
repo = Path("android-app/app/src/main/java/br/com/vendamais/mobile/data/remote/CadastroWorkflowRepository.kt")
replace_once(
    repo,
    '''    private fun normalizeCpfDigits(value: String?): String? {
        val digits = value?.filter(Char::isDigit).orEmpty()
        return digits.takeIf { it.length == 11 }
    }
''',
    '''    private fun normalizeCpfDigits(value: String?): String? {
        val digits = value?.filter(Char::isDigit).orEmpty()
        return digits.takeIf { it.length == 11 }
    }

    private fun isAttachmentDeliveryPending(response: JsonElement): Boolean {
        val root = response as? JsonObject ?: return false
        val attachmentQueue = root["attachmentQueue"] as? JsonObject ?: return false
        val required = attachmentQueue["required"]?.jsonPrimitive?.booleanOrNull == true
        val delivered = attachmentQueue["delivered"]?.jsonPrimitive?.booleanOrNull == true
        return required && !delivered
    }
''',
    "android attachment pending helper",
)

replace_once(
    repo,
    '''            if (!statusPermiteContinuar) {
                Log.i(
                    logTag,
                    "createDraftFromCpf ignorando cadastro historico id=${existing.cadastroId ?: "-"} status=${existing.status ?: "-"} cpf=$cpf",
                )
            } else {
''',
    '''            if (!statusPermiteContinuar) {
                if (statusNormalizado == "enviado") {
                    throw CadastroExistenteException(
                        cadastroId = null,
                        empresaNome = existing.empresaNome,
                        canContinue = false,
                        "Este CPF ja possui uma adesao concluida e enviada. Abra o cadastro existente; uma nova adesao para o mesmo CPF nao e permitida.",
                    )
                }
                Log.i(
                    logTag,
                    "createDraftFromCpf ignorando cadastro historico id=${existing.cadastroId ?: "-"} status=${existing.status ?: "-"} cpf=$cpf",
                )
            } else {
''',
    "android completed CPF guard",
)

replace_once(
    repo,
    '''        val statusPersistido = if (success) {
            "enviado"
        } else {
''',
    '''        val attachmentPending = success && isAttachmentDeliveryPending(response)
        val statusPersistido = if (success) {
            if (attachmentPending) "incompleto" else "enviado"
        } else {
''',
    "android queued attachment status",
)

replace_once(
    repo,
    '''            put("erp_response", response)
            put("tipo_cadastro", "cadastro")
            cpfForSync?.let { put("cpf", it) }
            put("created_by", session.userId)
''',
    '''            put("erp_response", response)
            put("tipo_cadastro", "cadastro")
            if (attachmentPending) {
                put("motivo_bloqueio", "Aguardando envio do anexo ao ERP.")
            } else if (success) {
                put("motivo_bloqueio", JsonNull)
            }
            cpfForSync?.let { put("cpf", it) }
            put("created_by", session.userId)
''',
    "android attachment blocking reason",
)

replace_once(
    repo,
    '''        if (success) {
            val fallback = runCatching {
                markCadastroAsEnviado(
                    session = session,
                    cadastroId = cadastroId,
                    payload = payload,
                    response = response,
                )
            }
            if (fallback.isSuccess) {
                Log.w(
                    logTag,
                    "syncCadastroAfterSend usou fallback minimo para marcar cadastro como enviado id=$cadastroId",
                    throwable,
                )
                return
            }
        }
''',
    '''        if (success) {
            val attachmentPending = isAttachmentDeliveryPending(response)
            val fallback = runCatching {
                if (attachmentPending) {
                    markCadastroWaitingForAttachment(
                        session = session,
                        cadastroId = cadastroId,
                        payload = payload,
                        response = response,
                    )
                } else {
                    markCadastroAsEnviado(
                        session = session,
                        cadastroId = cadastroId,
                        payload = payload,
                        response = response,
                    )
                }
            }
            if (fallback.isSuccess) {
                Log.w(
                    logTag,
                    if (attachmentPending) {
                        "syncCadastroAfterSend usou fallback para manter cadastro pendente ate o anexo id=$cadastroId"
                    } else {
                        "syncCadastroAfterSend usou fallback minimo para marcar cadastro como enviado id=$cadastroId"
                    },
                    throwable,
                )
                return
            }
        }
''',
    "android sync fallback",
)

replace_once(
    repo,
    '''    private suspend fun markCadastroAsEnviado(
''',
    '''    private suspend fun markCadastroWaitingForAttachment(
        session: SavedSession,
        cadastroId: String,
        payload: JsonElement,
        response: JsonElement,
    ) {
        patchCadastroById(
            session = session,
            id = cadastroId,
            payload = buildJsonObject {
                put("status", "incompleto")
                put("payload_erp", payload)
                put("erp_response", response)
                put("motivo_bloqueio", "Aguardando envio do anexo ao ERP.")
            },
        )
    }

    private suspend fun markCadastroAsEnviado(
''',
    "android waiting attachment fallback method",
)

# Android list: suppress historical pending duplicate when the same CPF already has a sent cadastro.
cadastros_screen = Path("android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/CadastrosScreen.kt")
replace_once(
    cadastros_screen,
    '''    val baseCadastros = state.cadastros.filter {
        when (state.cadastroFiltro) {
            CadastroFiltro.PENDENTES -> isPendingCadastroStatus(it.status)
            CadastroFiltro.ENVIADOS -> it.status == "enviado"
        }
    }
''',
    '''    val cpfsComCadastroConcluido = state.cadastros
        .asSequence()
        .filter { it.status == "enviado" && it.tipoCadastro == "cadastro" }
        .map { it.cpf.filter(Char::isDigit) }
        .filter { it.length == 11 }
        .toSet()
    val baseCadastros = state.cadastros.filter { cadastro ->
        when (state.cadastroFiltro) {
            CadastroFiltro.PENDENTES -> {
                val cpf = cadastro.cpf.filter(Char::isDigit)
                isPendingCadastroStatus(cadastro.status) &&
                    !(cadastro.tipoCadastro == "cadastro" && cpf.length == 11 && cpf in cpfsComCadastroConcluido)
            }
            CadastroFiltro.ENVIADOS -> cadastro.status == "enviado"
        }
    }
''',
    "android hide historical sent+pending duplicate",
)

replace_once(
    cadastros_screen,
    '''            onCompleted = {
                viewModel.selectTab(MainTab.CADASTROS)
                viewModel.selectCadastroAreaTab(CadastroAreaTab.COMPLETOS)
            },
''',
    '''            onCompleted = {
                viewModel.selectTab(MainTab.CADASTROS)
                viewModel.selectCadastroAreaTab(CadastroAreaTab.COMPLETOS)
            },
            onQueued = {
                viewModel.selectTab(MainTab.CADASTROS)
                viewModel.selectCadastroAreaTab(CadastroAreaTab.INCOMPLETOS)
            },
''',
    "android dependent queued navigation",
)

# Inclusion of dependents: queue every attachment and only mark sent after worker confirmation.
inclusion = Path("android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/InclusaoDependenteDialog.kt")
replace_once(
    inclusion,
    '''    onCompleted: () -> Unit = {},
    cadastro: CadastroDetalhe? = null,
''',
    '''    onCompleted: () -> Unit = {},
    onQueued: () -> Unit = {},
    cadastro: CadastroDetalhe? = null,
''',
    "dependent dialog queued callback",
)
replace_once(
    inclusion,
    '''    var successDialogMessage by rememberSaveable { mutableStateOf<String?>(null) }
''',
    '''    var successDialogMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var completionHasPendingAttachments by rememberSaveable { mutableStateOf(false) }
''',
    "dependent completion queue state",
)

start = '''        val finalPayloadBase = buildCadastroPayload(
'''
end = '''        cadastroId?.let { id ->
'''
new_block = '''        val hasAttachments = base.any { it.arquivo != null }
        val finalPayloadBase = buildCadastroPayload(
            profileId = profile?.id.orEmpty(),
            teamId = profile?.teamId,
            responsavel = responsavel,
            empresaCodigo = resolveEmpresaCodigo(responsavel),
            empresaNome = resolveEmpresaNome(responsavel),
            empresaRaw = empresaRaw,
            planosRaw = empresaPlanosRaw ?: extractPlanosRawFromEmpresa(empresaRaw),
            status = if (hasAttachments) "incompleto" else "enviado",
            statusAdesaoId = selectedStatusId.takeIf { it.isNotBlank() },
            vendedor = vendedor ?: TeamMemberOption("", "", "", null),
            adesionista = adesionista,
            dependentes = base,
        )
        val finalPayload = buildJsonObject {
            finalPayloadBase.forEach { (key, value) -> put(key, value) }
            put("erp_response", response)
            if (hasAttachments) {
                put("motivo_bloqueio", "Aguardando envio do(s) anexo(s) ao ERP.")
            } else {
                put("motivo_bloqueio", JsonNull)
            }
        }

        cadastroId = if (cadastroId == null) {
            viewModel.createCadastroRecord(finalPayload).id
        } else {
            viewModel.updateCadastroRecord(cadastroId, finalPayload)
            cadastroId
        }

        val codes = extractDependenteCodes(response)
        val funcionario = profile?.externalId?.toIntOrNull() ?: 0
        if (hasAttachments) {
            if (funcionario <= 0 || cadastroId == null) {
                throw IllegalStateException(
                    "Dependentes criados no ERP, mas o anexo nao pode ser enfileirado. O cadastro foi mantido pendente para nova tentativa.",
                )
            }

            base.forEachIndexed { idx, dep ->
                val file = dep.arquivo ?: return@forEachIndexed
                val code = codes.getOrNull(idx)
                    ?: throw IllegalStateException(
                        "Dependente ${idx + 1} criado no ERP, mas sem codigo para envio do anexo. O cadastro foi mantido pendente.",
                    )
                runCatching {
                    viewModel.enqueueDependenteUpload(cadastroId, funcionario, code, file.path, file.nome)
                }.getOrElse { throwable ->
                    throw IllegalStateException(
                        "Dependente ${idx + 1} criado no ERP, mas falhou ao enfileirar o anexo: ${throwable.message ?: "erro desconhecido"}. O arquivo foi preservado.",
                        throwable,
                    )
                }
            }
        }

        completionHasPendingAttachments = hasAttachments
'''
replace_span(inclusion, start, end, new_block, "dependent attachment queue authority")

replace_once(
    inclusion,
    '''        successDialogMessage = "Dependentes incluidos com sucesso."
''',
    '''        successDialogMessage = if (completionHasPendingAttachments) {
            "Dependentes incluidos. Os anexos estao sendo enviados ao ERP e o cadastro permanecera pendente ate a confirmacao."
        } else {
            "Dependentes incluidos com sucesso."
        }
''',
    "dependent success message",
)
replace_once(
    inclusion,
    '''            successDialogMessage = null
            onDismiss()
            onCompleted()
''',
    '''            successDialogMessage = null
            onDismiss()
            if (completionHasPendingAttachments) onQueued() else onCompleted()
''',
    "dependent completion destination",
)

# Web: never mark sent until attachmentQueue confirms delivery.
use_cadastros = Path("src/hooks/useCadastros.ts")
replace_once(
    use_cadastros,
    '''const normalizeCpf = (value?: string | null) => (value || '').replace(/\\D/g, '');
''',
    '''const normalizeCpf = (value?: string | null) => (value || '').replace(/\\D/g, '');
const isAttachmentDeliveryPending = (result: any) =>
  Boolean(result?.attachmentQueue?.required && result?.attachmentQueue?.delivered !== true);
''',
    "web attachment pending helper",
)
replace_once(
    use_cadastros,
    '''    const syncPayloadBase = {
      status: 'enviado',
      payload_erp: payload,
      erp_response: result,
      dependentes: dependentesFormatados,
    };

    let { data: syncedCadastro, error: syncError } = await supabase
      .from('cadastros')
      .update({
        ...syncPayloadBase,
        data_envio: new Date().toISOString(),
      })
      .eq('id', id);
''',
    '''    const attachmentPending = isAttachmentDeliveryPending(result);
    const syncPayloadBase = {
      status: attachmentPending ? 'incompleto' : 'enviado',
      payload_erp: payload,
      erp_response: result,
      dependentes: dependentesFormatados,
      motivo_bloqueio: attachmentPending ? 'Aguardando envio do anexo ao ERP.' : null,
    };

    let syncQuery = supabase
      .from('cadastros')
      .update(
        attachmentPending
          ? syncPayloadBase
          : { ...syncPayloadBase, data_envio: new Date().toISOString() }
      )
      .eq('id', id);
    let { data: syncedCadastro, error: syncError } = await syncQuery;
''',
    "web queued attachment status",
)
replace_once(
    use_cadastros,
    '''      if (!cpfTitular || cpfTitular.length !== 11) {
        return null;
      }
''',
    '''      if (!cpfTitular || cpfTitular.length !== 11) {
        return null;
      }

      const documento = (payload as any)?.dados?.documento;
      if (documento?.caminho) {
        // Nao conclui localmente apenas porque o CPF apareceu no ERP. O anexo precisa ser confirmado pela fila.
        return null;
      }
''',
    "web abort reconciliation attachment guard",
)

# Admin queue page: expose health and monitor automatic draining rather than assuming one batch is enough.
fila = Path("src/pages/FilaUploadERP.tsx")
replace_once(
    fila,
    '''interface QueueItem {
''',
    '''interface QueueHealth {
  total: number;
  queued: number;
  processing: number;
  retry_wait: number;
  success: number;
  failed: number;
  due_now: number;
  stuck_processing: number;
  oldest_pending_at: string | null;
  checked_at: string;
}

interface QueueItem {
''',
    "queue health type",
)
replace_once(
    fila,
    '''  const [processingCount, setProcessingCount] = useState(0);
''',
    '''  const [processingCount, setProcessingCount] = useState(0);
  const [processingProgress, setProcessingProgress] = useState<string | null>(null);
  const [queueHealth, setQueueHealth] = useState<QueueHealth | null>(null);
''',
    "queue health state",
)
replace_once(
    fila,
    '''      setItems(mappedData);
      setTotalCount(count || 0);

      const processingItems = mappedData.filter(item => item.status === 'processing').length;
''',
    '''      setItems(mappedData);
      setTotalCount(count || 0);

      const { data: healthData, error: healthError } = await supabase.rpc('get_erp_upload_queue_health');
      if (!healthError && healthData) {
        setQueueHealth(healthData as QueueHealth);
      }

      const processingItems = mappedData.filter(item => item.status === 'processing').length;
''',
    "queue health fetch",
)

replace_span(
    fila,
    '''  const handleProcessQueue = async () => {
''',
    '''  const handleReprocessItem = async (itemId: string) => {
''',
    '''  const handleProcessQueue = async () => {
    setProcessingQueue(true);
    setProcessingProgress('Iniciando worker da fila...');
    try {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) throw new Error('Sessao nao encontrada');

      const response = await fetch(
        `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/erp-process-upload-queue`,
        {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${session.access_token}`,
            'Content-Type': 'application/json',
          },
          body: '{}',
        }
      );
      const result = await response.json();
      if (!response.ok) {
        throw new Error(result.error || result.details || 'Erro desconhecido ao processar a fila');
      }

      const firstSummary = result.results || {};
      setProcessingProgress(
        `Primeiro lote: ${firstSummary.success || 0} sucesso(s), ${firstSummary.retry || 0} retry(s), ${result.remaining_due || 0} ainda devido(s).`
      );
      await fetchQueueItems();

      let finalHealth: QueueHealth | null = null;
      for (let attempt = 0; attempt < 90; attempt += 1) {
        const { data: healthData, error: healthError } = await supabase.rpc('get_erp_upload_queue_health');
        if (!healthError && healthData) {
          finalHealth = healthData as QueueHealth;
          setQueueHealth(finalHealth);
          setProcessingProgress(
            `Fila ativa: ${finalHealth.processing} processando, ${finalHealth.due_now} devido(s), ${finalHealth.retry_wait} aguardando retry, ${finalHealth.failed} falha(s) definitiva(s).`
          );
          if (finalHealth.due_now === 0 && finalHealth.processing === 0) break;
        } else {
          break;
        }

        if (attempt % 4 === 0) await fetchQueueItems();
        await new Promise(resolve => window.setTimeout(resolve, 1000));
      }

      await fetchQueueItems();
      if (finalHealth) {
        alert(
          `Processamento imediato concluido.\n\n` +
          `Aguardando retry: ${finalHealth.retry_wait}\n` +
          `Falhas definitivas: ${finalHealth.failed}\n\n` +
          `Itens em retry serao retomados automaticamente pelo cron a cada minuto.`
        );
      } else {
        alert(`Lote processado. Sucessos: ${firstSummary.success || 0}; retries: ${firstSummary.retry || 0}.`);
      }
    } catch (error) {
      console.error('Erro ao processar fila:', error);
      alert(error instanceof Error ? error.message : 'Erro ao conectar com o servidor.');
    } finally {
      setProcessingQueue(false);
      setProcessingProgress(null);
    }
  };

''',
    "queue processing monitor",
)

replace_once(
    fila,
    '''        {processingCount > 0 && (
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
            <div className="flex items-center gap-3">
              <Loader2 className="w-5 h-5 text-blue-600 animate-spin flex-shrink-0" />
              <div className="flex-1">
                <p className="text-blue-900 font-medium">
                  Processamento em andamento
                </p>
                <p className="text-blue-700 text-sm mt-1">
                  {processingCount} item(ns) sendo enviado(s) para o ERP. A tela será atualizada automaticamente.
                </p>
              </div>
            </div>
          </div>
        )}
''',
    '''        {queueHealth && (
          <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
            <div className="bg-white border border-slate-200 rounded-lg p-3"><div className="text-xs text-slate-500">Devidos agora</div><div className="text-xl font-semibold">{queueHealth.due_now}</div></div>
            <div className="bg-white border border-slate-200 rounded-lg p-3"><div className="text-xs text-slate-500">Processando</div><div className="text-xl font-semibold">{queueHealth.processing}</div></div>
            <div className="bg-white border border-slate-200 rounded-lg p-3"><div className="text-xs text-slate-500">Aguardando retry</div><div className="text-xl font-semibold">{queueHealth.retry_wait}</div></div>
            <div className="bg-white border border-slate-200 rounded-lg p-3"><div className="text-xs text-slate-500">Falhas definitivas</div><div className="text-xl font-semibold">{queueHealth.failed}</div></div>
            <div className="bg-white border border-slate-200 rounded-lg p-3"><div className="text-xs text-slate-500">Travados &gt;10 min</div><div className="text-xl font-semibold">{queueHealth.stuck_processing}</div></div>
          </div>
        )}

        {(processingCount > 0 || processingProgress) && (
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
            <div className="flex items-center gap-3">
              <Loader2 className="w-5 h-5 text-blue-600 animate-spin flex-shrink-0" />
              <div className="flex-1">
                <p className="text-blue-900 font-medium">Processamento em andamento</p>
                <p className="text-blue-700 text-sm mt-1">
                  {processingProgress || `${processingCount} item(ns) sendo enviado(s) para o ERP.`}
                </p>
              </div>
            </div>
          </div>
        )}
''',
    "queue health UI",
)

# Server-side ERP creation: linked cadastro is pending as soon as an attachment is queued/processing.
erp_novo = Path("supabase/functions/erp-novo-usuario2/index.ts")
replace_once(
    erp_novo,
    '''async function ensureAttachmentQueued(
''',
    '''async function keepCadastroPendingForAttachment(supabase: any, cadastroId: string) {
  if (!cadastroId || !isUuid(cadastroId)) return;
  const { error } = await supabase
    .from("cadastros")
    .update({
      status: "incompleto",
      motivo_bloqueio: "Aguardando envio do anexo ao ERP.",
    })
    .eq("id", cadastroId);
  if (error) console.warn(`Falha ao manter cadastro ${cadastroId} pendente pelo anexo:`, error.message);
}

async function ensureAttachmentQueued(
''',
    "erp novo pending helper",
)
replace_once(
    erp_novo,
    '''      return {
        required: true,
        queued: true,
        reused: true,
        processing: true,
''',
    '''      await keepCadastroPendingForAttachment(supabase, cadastroId);
      return {
        required: true,
        queued: true,
        reused: true,
        processing: true,
''',
    "erp novo processing pending",
)
replace_once(
    erp_novo,
    '''    triggerUploadQueueProcessor(supabaseUrl, serviceRoleKey);
    return {
      required: true,
      queued: true,
      reused: true,
''',
    '''    await keepCadastroPendingForAttachment(supabase, cadastroId);
    triggerUploadQueueProcessor(supabaseUrl, serviceRoleKey);
    return {
      required: true,
      queued: true,
      reused: true,
''',
    "erp novo reused pending",
)
replace_once(
    erp_novo,
    '''  triggerUploadQueueProcessor(supabaseUrl, serviceRoleKey);
  return {
    required: true,
    queued: true,
    reused: false,
''',
    '''  await keepCadastroPendingForAttachment(supabase, cadastroId);
  triggerUploadQueueProcessor(supabaseUrl, serviceRoleKey);
  return {
    required: true,
    queued: true,
    reused: false,
''',
    "erp novo new pending",
)
