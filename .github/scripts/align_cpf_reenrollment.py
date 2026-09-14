from pathlib import Path

path = Path('android-app/app/src/main/java/br/com/vendamais/mobile/data/remote/CadastroWorkflowRepository.kt')
text = path.read_text(encoding='utf-8')

old = '''        val existingDeferred = async {
            val stepStart = System.currentTimeMillis()
            val result = checkCpfExistente(session, profile.id, cpf)
            Log.i(logTag, "createDraftFromCpf check_cpf_existente took ${System.currentTimeMillis() - stepStart}ms")
            result
        }
        val erpCheckDeferred = async {
            val stepStart = System.currentTimeMillis()
            val result = withTimeoutOrNull(20000) { checkErpAssociado(session, cpf) }
                ?: throw IllegalStateException("A consulta de CPF no ERP excedeu o tempo limite. Tente novamente.")
            Log.i(logTag, "createDraftFromCpf erp-check-associado took ${System.currentTimeMillis() - stepStart}ms")
            result
        }
        val clienteAnteriorDeferred = async {
            val stepStart = System.currentTimeMillis()
            val result = withTimeoutOrNull(12000) { findClienteByCpf(session, cpf) }
            Log.i(logTag, "createDraftFromCpf findClienteByCpf took ${System.currentTimeMillis() - stepStart}ms")
            result
        }

        val existing = existingDeferred.await()
        val erpCheck = erpCheckDeferred.await()
        if (erpCheck.exists && erpCheck.shouldBlock) {
            throw IllegalStateException(erpCheck.blockReason ?: "Cliente ja cadastrado no ERP.")
        }
        if (existing.exists) {
            val statusNormalizado = existing.status
                ?.trim()
                ?.lowercase(Locale.ROOT)
                .orEmpty()
            val statusPermiteContinuar = isPendingCadastroStatus(statusNormalizado)
            if (!statusPermiteContinuar) {
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
                val canContinue = existing.canContinue && statusPermiteContinuar && !existing.cadastroId.isNullOrBlank()
                throw CadastroExistenteException(
                    cadastroId = existing.cadastroId.takeIf { canContinue },
                    empresaNome = existing.empresaNome,
                    canContinue = canContinue,
                    buildString {
                        append("Ja existe um cadastro para este CPF")
                        existing.empresaNome?.let { append(" em $it") }
                        when {
                            canContinue -> {
                                append(". Abra o rascunho existente para continuar.")
                            }
                            statusNormalizado == "enviado" -> {
                                append(". Este cadastro ja foi concluido e enviado.")
                            }
                            existing.status != null -> {
                                append(". Status atual: ${existing.status}.")
                            }
                            else -> {
                                append(".")
                            }
                        }
                    },
                )
            }
        }
'''

new = '''        val existingDeferred = async {
            val stepStart = System.currentTimeMillis()
            val result = checkCpfExistente(session, profile.id, cpf)
            Log.i(logTag, "createDraftFromCpf check_cpf_existente took ${System.currentTimeMillis() - stepStart}ms")
            result
        }
        val clienteAnteriorDeferred = async {
            val stepStart = System.currentTimeMillis()
            val result = withTimeoutOrNull(12000) { findClienteByCpf(session, cpf) }
            Log.i(logTag, "createDraftFromCpf findClienteByCpf took ${System.currentTimeMillis() - stepStart}ms")
            result
        }

        // A base local protege apenas contra dois processos pendentes para o mesmo CPF.
        // Historico concluido nunca decide elegibilidade de recadastro: essa decisao e do ERP.
        val existing = existingDeferred.await()
        if (existing.exists) {
            val statusNormalizado = existing.status
                ?.trim()
                ?.lowercase(Locale.ROOT)
                .orEmpty()
            val statusPermiteContinuar = isPendingCadastroStatus(statusNormalizado)

            if (statusPermiteContinuar) {
                val canContinue = existing.canContinue && !existing.cadastroId.isNullOrBlank()
                throw CadastroExistenteException(
                    cadastroId = existing.cadastroId.takeIf { canContinue },
                    empresaNome = existing.empresaNome,
                    canContinue = canContinue,
                    buildString {
                        append("Ja existe uma adesao em andamento para este CPF")
                        existing.empresaNome?.let { append(" em $it") }
                        if (canContinue) {
                            append(". Abra o rascunho existente para continuar.")
                        } else {
                            append(". Finalize o processo pendente antes de iniciar outro.")
                        }
                    },
                )
            }

            Log.i(
                logTag,
                "createDraftFromCpf ignorando historico local id=${existing.cadastroId ?: "-"} status=${existing.status ?: "-"} cpf=$cpf; ERP decidira elegibilidade",
            )
        }

        val erpStepStart = System.currentTimeMillis()
        val erpCheck = withTimeoutOrNull(20000) { checkErpAssociado(session, cpf) }
            ?: throw IllegalStateException("A consulta de CPF no ERP excedeu o tempo limite. Tente novamente.")
        Log.i(logTag, "createDraftFromCpf erp-check-associado took ${System.currentTimeMillis() - erpStepStart}ms")

        if (erpCheck.exists && erpCheck.shouldBlock) {
            throw IllegalStateException(erpCheck.blockReason ?: "Cliente ja cadastrado no ERP.")
        }
'''

if old not in text:
    raise SystemExit('Expected createDraftFromCpf block was not found; refusing to patch')

text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
print('Updated createDraftFromCpf to use ERP as recadastro authority')
