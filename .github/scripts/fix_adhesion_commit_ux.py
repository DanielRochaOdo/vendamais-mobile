from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    path.write_text(text.replace(old, new, 1))
    print(f"updated {label}")


def replace_count(path: Path, old: str, new: str, expected: int, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{label}: expected {expected} matches, got {count}")
    path.write_text(text.replace(old, new))
    print(f"updated {label} ({count} matches)")


# -----------------------------------------------------------------------------
# Edge: ERP creation is the transaction boundary. Attachment delivery is async
# recovery and must never turn a committed ERP adhesion into a user-visible
# failure/retry of the whole adhesion.
# -----------------------------------------------------------------------------
erp = Path("supabase/functions/erp-novo-usuario2/index.ts")

replace_once(
    erp,
    '''const extractDependenteCodigo = (payload: any): number | null => {
  const candidateArrays = [
    payload?.dados?.dependentes,
    payload?.data?.dados?.dependentes,
    payload?.data?.data?.dados?.dependentes,
  ];

  for (const candidate of candidateArrays) {
    if (!Array.isArray(candidate)) continue;
    for (const item of candidate) {
      const codigo = toPositiveInt(item?.codigo);
      if (codigo) return codigo;
    }
  }

  return null;
};
''',
    '''const extractDependenteCodigo = (payload: any): number | null => {
  // NovoUsuario2 is not perfectly shape-stable. In production we have seen the
  // created dependent code returned directly as dados.codigo as well as inside
  // dependentes arrays. Accept every known successful shape before deciding
  // that the code is absent.
  const scalarCandidates = [
    payload?.dados?.codigo,
    payload?.data?.dados?.codigo,
    payload?.data?.data?.dados?.codigo,
  ];
  for (const candidate of scalarCandidates) {
    const codigo = toPositiveInt(candidate);
    if (codigo) return codigo;
  }

  const candidateCollections = [
    payload?.dados?.dependentes,
    payload?.dados?.dependente,
    payload?.data?.dados?.dependentes,
    payload?.data?.dados?.dependente,
    payload?.data?.data?.dados?.dependentes,
    payload?.data?.data?.dados?.dependente,
  ];

  for (const candidate of candidateCollections) {
    const items = Array.isArray(candidate) ? candidate : candidate ? [candidate] : [];
    for (const item of items) {
      const codigo = toPositiveInt(item?.codigo ?? item?.codigoDependente ?? item?.idDependente);
      if (codigo) return codigo;
    }
  }

  return null;
};
''',
    "ERP dependent-code extraction",
)

replace_once(
    erp,
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
''',
    '''async function keepCadastroPendingForAttachment(_supabase: any, cadastroId: string) {
  if (!cadastroId || !isUuid(cadastroId)) return;
  // IMPORTANT: once NovoUsuario2 confirms creation, the adhesion is committed.
  // Attachment state lives in erp_upload_queue and must never reopen the
  // adhesion, otherwise the UI invites a duplicate ERP submission.
  console.info(`Cadastro ${cadastroId} permanece enviado enquanto o anexo e processado em segundo plano.`);
}

async function markCadastroErpCreated(supabase: any, cadastroId: string, erpPayload: any) {
  if (!cadastroId || !isUuid(cadastroId)) return;

  const sentAt = new Date().toISOString();
  let { error } = await supabase
    .from("cadastros")
    .update({
      status: "enviado",
      data_envio: sentAt,
      motivo_bloqueio: null,
      erp_response: { success: true, data: erpPayload },
    })
    .eq("id", cadastroId);

  // Keep compatibility with older schemas where data_envio may not be writable.
  if (error?.message?.includes("data_envio")) {
    const retry = await supabase
      .from("cadastros")
      .update({
        status: "enviado",
        motivo_bloqueio: null,
        erp_response: { success: true, data: erpPayload },
      })
      .eq("id", cadastroId);
    error = retry.error;
  }

  if (error) {
    console.warn(`ERP confirmou cadastro ${cadastroId}, mas a reconciliacao local falhou:`, error.message);
  }
}
''',
    "ERP committed-cadastro reconciliation",
)

replace_once(
    erp,
    '''const buildResponseWithAttachment = (
  erpPayload: any,
  attachment: AttachmentQueueResult,
  extra: Record<string, unknown> = {},
) => {
  if (attachment.required && !attachment.queued) {
    return {
      success: false,
      erpCreated: true,
      data: erpPayload,
      attachmentQueue: attachment,
      error: attachment.error || "Cadastro criado no ERP, mas o anexo ainda nao foi enfileirado.",
      ...extra,
    };
  }

  return {
    success: true,
    data: erpPayload,
    attachmentQueue: attachment,
    ...extra,
  };
};
''',
    '''const buildResponseWithAttachment = (
  erpPayload: any,
  attachment: AttachmentQueueResult,
  extra: Record<string, unknown> = {},
) => {
  const attachmentPending = attachment.required && !attachment.queued;
  return {
    success: true,
    erpCreated: true,
    data: erpPayload,
    attachmentQueue: attachment,
    ...(attachmentPending
      ? {
          attachmentPending: true,
          warning:
            attachment.error ||
            "Cadastro criado no ERP. O documento seguira pela fila de sincronizacao sem exigir novo cadastro.",
        }
      : {}),
    ...extra,
  };
};
''',
    "ERP post-commit response semantics",
)

replace_once(
    erp,
    '''        const erpPayload = unwrapStoredErpPayload(existingCadastro.erp_response);
        const attachment = await ensureAttachmentQueued(
''',
    '''        const erpPayload = unwrapStoredErpPayload(existingCadastro.erp_response);
        await markCadastroErpCreated(supabase, cadastroIdHeader, erpPayload);
        const attachment = await ensureAttachmentQueued(
''',
    "ERP cadastro reuse reconciliation",
)

replace_once(
    erp,
    '''          const erpPayload = unwrapStoredErpPayload(idempotencyRow.response_body);
          const attachment = await ensureAttachmentQueued(
''',
    '''          const erpPayload = unwrapStoredErpPayload(idempotencyRow.response_body);
          await markCadastroErpCreated(supabase, cadastroIdHeader, erpPayload);
          const attachment = await ensureAttachmentQueued(
''',
    "ERP idempotency reuse reconciliation",
)

replace_once(
    erp,
    '''    const hasDadosCodigo = responseData?.dados?.codigo || responseData?.data?.dados?.codigo;
''',
    '''    const hasDadosCodigo = extractDependenteCodigo(responseData);
''',
    "ERP success-code validation",
)

replace_once(
    erp,
    '''    const attachment = await ensureAttachmentQueued(
      supabase,
      supabaseUrl,
      supabaseServiceKey,
      cadastroIdHeader,
      userId,
      requestBody,
      responseData,
    );
''',
    '''    await markCadastroErpCreated(supabase, cadastroIdHeader, responseData);

    const attachment = await ensureAttachmentQueued(
      supabase,
      supabaseUrl,
      supabaseServiceKey,
      cadastroIdHeader,
      userId,
      requestBody,
      responseData,
    );
''',
    "ERP fresh success reconciliation",
)

replace_count(
    erp,
    '''        statusCode = attachment.required && !attachment.queued ? 503 : 200;
        errorMessage = statusCode === 200 ? undefined : responseBody.error;
''',
    '''        statusCode = 200;
        errorMessage = undefined;
''',
    2,
    "ERP reused post-commit HTTP status",
)
replace_once(
    erp,
    '''    statusCode = attachment.required && !attachment.queued ? 503 : 200;
    errorMessage = statusCode === 200 ? undefined : responseBody.error;
''',
    '''    statusCode = 200;
    errorMessage = undefined;
''',
    "ERP fresh post-commit HTTP status",
)


# -----------------------------------------------------------------------------
# Edge queue worker: document retries are not adhesion retries.
# -----------------------------------------------------------------------------
queue = Path("supabase/functions/erp-process-upload-queue/index.ts")
replace_once(
    queue,
    '''async function markCadastroWaiting(supabase: any, cadastroId: string | null, message: string) {
  if (!cadastroId) return;
  const { error } = await supabase
    .from("cadastros")
    .update({ status: "incompleto", motivo_bloqueio: message })
    .eq("id", cadastroId);
  if (error) console.warn(`Falha ao manter cadastro ${cadastroId} pendente:`, error.message);
}

async function markCadastroFailed(supabase: any, cadastroId: string | null, message: string) {
  if (!cadastroId) return;
  const { error } = await supabase
    .from("cadastros")
    .update({ status: "erro_envio", motivo_bloqueio: message })
    .eq("id", cadastroId);
  if (error) console.warn(`Falha ao marcar cadastro ${cadastroId} com erro:`, error.message);
}
''',
    '''async function markCadastroWaiting(_supabase: any, cadastroId: string | null, message: string) {
  if (!cadastroId) return;
  // The ERP adhesion was already committed. Keep retry state exclusively in
  // erp_upload_queue so the user is never invited to submit the adhesion again.
  console.warn(`Anexo do cadastro ${cadastroId} aguardando retry sem reabrir adesao: ${message}`);
}

async function markCadastroFailed(_supabase: any, cadastroId: string | null, message: string) {
  if (!cadastroId) return;
  // A terminal attachment failure is an operational queue issue, not a failed
  // adhesion. Admins can retry it from the ERP upload queue.
  console.error(`Anexo do cadastro ${cadastroId} falhou sem reabrir adesao: ${message}`);
}
''',
    "queue post-commit cadastro status isolation",
)


# -----------------------------------------------------------------------------
# Android repository: never throw a whole-adhesion failure after ERP commit.
# -----------------------------------------------------------------------------
repo = Path("android-app/app/src/main/java/br/com/vendamais/mobile/data/remote/CadastroWorkflowRepository.kt")
replace_once(
    repo,
    '''            val response = enviarParaErp(session, targetCadastroId, payload)
            val firstDependenteId = CadastroPayloadBuilder.firstDependenteCodigo(response)
            if (cadastro.arquivoPath != null && firstDependenteId != null && funcionarioCadastroId != null) {
                processDocumentoUpload(
                    session = session,
                    cadastro = cadastro,
                    funcionarioCadastroId = funcionarioCadastroId,
                    dependenteId = firstDependenteId,
                )
            }

            fetchCadastroDetalhe(session, targetCadastroId)
''',
    '''            val response = enviarParaErp(session, targetCadastroId, payload)
            val attachmentQueuedByEdge = runCatching {
                response.jsonObject["attachmentQueue"]
                    ?.jsonObject
                    ?.get("queued")
                    ?.jsonPrimitive
                    ?.booleanOrNull == true
            }.getOrDefault(false)
            val firstDependenteId = CadastroPayloadBuilder.firstDependenteCodigo(response)

            if (
                !cadastro.arquivoPath.isNullOrBlank() &&
                !attachmentQueuedByEdge &&
                firstDependenteId != null &&
                funcionarioCadastroId != null
            ) {
                runCatching {
                    processDocumentoUpload(
                        session = session,
                        cadastro = cadastro,
                        funcionarioCadastroId = funcionarioCadastroId,
                        dependenteId = firstDependenteId,
                    )
                }.onFailure { throwable ->
                    Log.e(
                        logTag,
                        "ERP confirmou cadastro $targetCadastroId; fallback de enqueue do anexo falhou sem reabrir a adesao.",
                        throwable,
                    )
                }
            } else if (!cadastro.arquivoPath.isNullOrBlank() && !attachmentQueuedByEdge) {
                Log.w(
                    logTag,
                    "ERP confirmou cadastro $targetCadastroId, mas nao foi possivel montar fallback local do anexo. A adesao permanece enviada.",
                )
            }

            runCatching { fetchCadastroDetalhe(session, targetCadastroId) }
                .onFailure { throwable ->
                    Log.w(
                        logTag,
                        "ERP confirmou cadastro $targetCadastroId; refresh local falhou e foi ignorado para impedir reenvio duplicado.",
                        throwable,
                    )
                }
                .getOrDefault(cadastro)
''',
    "Android post-ERP attachment fallback",
)

replace_once(
    repo,
    '''        val attachmentPending = success && isAttachmentDeliveryPending(response)
        val statusPersistido = if (success) {
            if (attachmentPending) "incompleto" else "enviado"
        } else {
''',
    '''        val statusPersistido = if (success) {
            // NovoUsuario2 confirmed the remote transaction. Attachment delivery
            // is independent and must not reopen this adhesion.
            "enviado"
        } else {
''',
    "Android committed status semantics",
)

replace_once(
    repo,
    '''            if (attachmentPending) {
                put("motivo_bloqueio", "Aguardando envio do anexo ao ERP.")
            } else if (success) {
                put("motivo_bloqueio", JsonNull)
            }
''',
    '''            if (success) {
                put("motivo_bloqueio", JsonNull)
            }
''',
    "Android committed status reason",
)

replace_once(
    repo,
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
                    "syncCadastroAfterSend usou fallback minimo para manter ERP confirmado como enviado id=$cadastroId",
                    throwable,
                )
            } else {
                Log.e(
                    logTag,
                    "ERP confirmou cadastro $cadastroId, mas a reconciliacao local falhou. O erro nao sera propagado para evitar reenvio duplicado.",
                    fallback.exceptionOrNull(),
                )
            }
            return
        }
''',
    "Android non-fatal post-commit reconciliation",
)


# -----------------------------------------------------------------------------
# Android response parsing: same response-shape tolerance as the Edge function.
# -----------------------------------------------------------------------------
payload_builder = Path("android-app/app/src/main/java/br/com/vendamais/mobile/data/remote/CadastroPayloadBuilder.kt")
replace_once(
    payload_builder,
    '''    fun firstDependenteCodigo(response: JsonElement?): Int? {
        val dados = response?.jsonObject
            ?.get("data")?.jsonObject
            ?.get("dados")?.jsonObject
            ?.get("dependentes")?.jsonArray
            ?: return null
        return dados.firstOrNull()
            ?.jsonObject
            ?.get("codigo")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toIntOrNull()
    }
''',
    '''    fun firstDependenteCodigo(response: JsonElement?): Int? {
        val root = runCatching { response?.jsonObject }.getOrNull() ?: return null
        val payloads = listOfNotNull(
            root,
            runCatching { root["data"]?.jsonObject }.getOrNull(),
            runCatching { root["data"]?.jsonObject?.get("data")?.jsonObject }.getOrNull(),
        )

        payloads.forEach { payload ->
            val dados = runCatching { payload["dados"]?.jsonObject }.getOrNull() ?: return@forEach
            dados["codigo"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let { return it }

            listOf("dependentes", "dependente").forEach { key ->
                val value = dados[key] ?: return@forEach
                val items = runCatching { value.jsonArray.toList() }
                    .getOrElse { listOf(value) }
                items.forEach { item ->
                    val obj = runCatching { item.jsonObject }.getOrNull() ?: return@forEach
                    listOf("codigo", "codigoDependente", "idDependente").forEach { codeKey ->
                        obj[codeKey]
                            ?.jsonPrimitive
                            ?.contentOrNull
                            ?.toIntOrNull()
                            ?.takeIf { it > 0 }
                            ?.let { return it }
                    }
                }
            }
        }

        return null
    }
''',
    "Android ERP dependent-code parsing",
)


# -----------------------------------------------------------------------------
# Android attachment UX: validate the real ERP limit at selection time and
# preserve the previous valid attachment if the replacement is invalid.
# -----------------------------------------------------------------------------
editor = Path("android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/CadastroEditorDialog.kt")
replace_once(
    editor,
    'private const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024\n',
    'private const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024\n',
    "Android attachment size limit",
)

replace_once(
    editor,
    '''    suspend fun uploadSelectedArquivo(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        if (arquivoPath.isNotBlank()) {
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
        }
        arquivoPath = draftAttachment.path
''',
    '''    suspend fun uploadSelectedArquivo(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        validateUpload(
            fileName = fileName,
            mimeType = mimeType,
            size = bytes.size.toLong(),
        )
        val previousPath = arquivoPath
        val draftAttachment = withContext(Dispatchers.IO) {
            DraftAttachmentStorage.copyBytesToDraftStorage(
                context = context,
                draftId = cadastro.id,
                originalName = fileName,
                mimeType = mimeType,
                bytes = bytes,
            )
        }
        if (previousPath.isNotBlank()) {
            val existingFile = File(previousPath)
            if (existingFile.exists()) {
                withContext(Dispatchers.IO) { runCatching { existingFile.delete() } }
            } else {
                runCatching { viewModel.deleteTempFile(previousPath) }
            }
        }
        arquivoPath = draftAttachment.path
''',
    "Android validate-before-replace attachment flow",
)

replace_once(
    editor,
    '''                                Text("Documento", fontWeight = FontWeight.SemiBold)
                                if (arquivoNome.isNotBlank()) {
''',
    '''                                Text("Documento", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "PDF, JPG ou PNG · maximo 5 MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (arquivoNome.isNotBlank()) {
''',
    "Android attachment helper text",
)

replace_once(
    editor,
    '    if (size > MAX_UPLOAD_BYTES) throw IllegalStateException("Arquivo excede 10MB.")\n',
    '    if (size > MAX_UPLOAD_BYTES) throw IllegalStateException("O anexo excede o limite de 5 MB aceito pelo ERP. Escolha um arquivo menor.")\n',
    "Android selection-time oversize message",
)

storage = Path("android-app/app/src/main/java/br/com/vendamais/mobile/data/remote/DraftAttachmentStorage.kt")
replace_once(
    storage,
    '''object DraftAttachmentStorage {
''',
    '''object DraftAttachmentStorage {
    private const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024
''',
    "draft storage attachment limit constant",
)
replace_once(
    storage,
    '''    ): DraftAttachmentCopyResult {
        val safeName = resolveAttachmentDisplayName(originalName)
''',
    '''    ): DraftAttachmentCopyResult {
        require(bytes.size <= MAX_ATTACHMENT_BYTES) {
            "O anexo excede o limite de 5 MB aceito pelo ERP. Escolha um arquivo menor."
        }
        val safeName = resolveAttachmentDisplayName(originalName)
''',
    "draft storage attachment limit guard",
)


tests = Path("android-app/app/src/test/java/br/com/vendamais/mobile/data/remote/CadastroPayloadBuilderTest.kt")
replace_once(
    tests,
    '''    private fun cadastroDetalheBase(
''',
    '''    @Test
    fun `firstDependenteCodigo should read scalar codigo returned by NovoUsuario2`() {
        val response = JSON.parseToJsonElement(
            """{"success":true,"data":{"dados":{"codigo":12345}}}""",
        )

        assertThat(CadastroPayloadBuilder.firstDependenteCodigo(response)).isEqualTo(12345)
    }

    @Test
    fun `firstDependenteCodigo should keep legacy dependentes array compatibility`() {
        val response = JSON.parseToJsonElement(
            """{"success":true,"data":{"dados":{"dependentes":[{"codigo":54321}]}}}""",
        )

        assertThat(CadastroPayloadBuilder.firstDependenteCodigo(response)).isEqualTo(54321)
    }

    private fun cadastroDetalheBase(
''',
    "ERP response parsing unit tests",
)

print("adhesion commit UX hardening patch applied")
