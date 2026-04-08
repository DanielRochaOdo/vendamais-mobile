package br.com.vendamais.mobile.data.remote

import android.util.Log
import br.com.vendamais.mobile.AppConfig
import br.com.vendamais.mobile.data.auth.SavedSession
import br.com.vendamais.mobile.data.models.CadastroBaseData
import br.com.vendamais.mobile.data.models.CadastroConfig
import br.com.vendamais.mobile.data.models.CadastroContato
import br.com.vendamais.mobile.data.models.CadastroDetalhe
import br.com.vendamais.mobile.data.models.CadastroLinkItem
import br.com.vendamais.mobile.data.models.PublicCadastroCheckCpfResponse
import br.com.vendamais.mobile.data.models.PublicCadastroLinkResolveResponse
import br.com.vendamais.mobile.data.models.PublicCadastroPayload
import br.com.vendamais.mobile.data.models.PublicCadastroSubmitResponse
import br.com.vendamais.mobile.data.models.ParentescoMap
import br.com.vendamais.mobile.data.models.PlanoMap
import br.com.vendamais.mobile.data.models.CheckCpfExistenteResponse
import br.com.vendamais.mobile.data.models.CpfConsultInput
import br.com.vendamais.mobile.data.models.CpfConsultResult
import br.com.vendamais.mobile.data.models.EmpresaResumo
import br.com.vendamais.mobile.data.models.EmpresaSearchResponse
import br.com.vendamais.mobile.data.models.EmpresaSearchType
import br.com.vendamais.mobile.data.models.ErpAssociadoItem
import br.com.vendamais.mobile.data.models.ErpAssociadoResponse
import br.com.vendamais.mobile.data.models.LemmitLimitInfo
import br.com.vendamais.mobile.data.models.LemmitResponse
import br.com.vendamais.mobile.data.models.MobileProfile
import br.com.vendamais.mobile.data.models.StatusAdesao
import br.com.vendamais.mobile.data.models.TeamMemberOption
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import java.util.Locale

class CadastroWorkflowRepository(
    private val client: HttpClient,
    private val json: Json,
) {
    private val logTag = "CadastroWorkflowRepository"

    private fun isDuplicateDraftConstraintError(throwable: Throwable): Boolean {
        val message = throwable.message?.lowercase(Locale.ROOT).orEmpty()
        return message.contains("duplicate key") ||
            message.contains("unique constraint") ||
            message.contains("23505") ||
            message.contains("cadastros_cadastro_incompleto_cpf_unique_idx")
    }
    suspend fun fetchCadastroConfig(session: SavedSession): CadastroConfig? {
        return getList<CadastroConfig>(
            path = "cadastro_config",
            session = session,
            query = {
                parameter("id", "eq.1")
                parameter("select", "*")
                parameter("limit", 1)
            },
        ).firstOrNull()
    }

    suspend fun fetchPlanosMap(session: SavedSession): List<PlanoMap> {
        return getList(
            path = "cadastro_planos_map",
            session = session,
            query = {
                parameter("select", "*")
                parameter("order", "plano_id.asc")
            },
        )
    }

    suspend fun fetchParentescosMap(session: SavedSession): List<ParentescoMap> {
        return getList(
            path = "cadastro_parentesco_map",
            session = session,
            query = {
                parameter("select", "*")
                parameter("order", "parentesco_id.asc")
            },
        )
    }

    suspend fun fetchStatusAdesoes(session: SavedSession): List<StatusAdesao> {
        return getList(
            path = "status_adesoes",
            session = session,
            query = {
                parameter("select", "*")
                parameter("order", "ordem.asc")
            },
        )
    }

    suspend fun fetchProfilesByRole(session: SavedSession, role: String): List<TeamMemberOption> {
        return getList(
            path = "profiles",
            session = session,
            query = {
                parameter("role", "eq.$role")
                parameter("is_active", "eq.true")
                parameter("external_id", "not.is.null")
                parameter("select", "id,name,email,external_id")
                parameter("order", "name.asc")
            },
        )
    }

    suspend fun fetchActiveLinks(session: SavedSession): List<CadastroLinkItem> {
        return getList(
            path = "cadastro_links",
            session = session,
            query = {
                parameter(
                    "select",
                    "id,empresa_codigo,empresa_nome,empresa_cnpj,vendedor_nome,vendedor_codigo,link_url,is_active,click_count,used_at,used_cpf,created_at"
                )
                parameter("is_active", "eq.true")
                parameter("order", "created_at.desc")
            },
        )
    }

    suspend fun createCadastroLink(
        session: SavedSession,
        profile: MobileProfile,
        empresa: EmpresaResumo,
    ): CadastroLinkItem {
        val rawToken = CadastroLinkCrypto.generateCadastroLinkToken()
        val tokenHash = CadastroLinkCrypto.hashCadastroLinkToken(rawToken)
        val linkUrl = buildPublicAdesaoUrl(rawToken)
        val vendedorCodigo = profile.externalId?.trim().orEmpty().ifBlank { "0" }

        val payload = buildJsonObject {
            put("created_by", profile.id)
            profile.teamId?.let { put("team_id", it) }
            put("token_hash", tokenHash)
            put("link_url", linkUrl)
            put("empresa_codigo", empresa.id)
            put("empresa_nome", empresa.nomeFantasia.ifBlank { empresa.razaoSocial })
            put("empresa_cnpj", empresa.cnpj)
            put("empresa_raw", empresa.raw ?: JsonObject(emptyMap()))
            put("empresa_exige_matricula", empresa.exigeMatricula ?: 0)
            put("planos_raw", empresa.precoPlano ?: buildJsonArray {})
            put("vendedor_id", profile.id)
            put("vendedor_codigo", vendedorCodigo)
            put("vendedor_nome", profile.name.ifBlank { profile.email })
        }

        return client.safePost<List<CadastroLinkItem>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/cadastro_links",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
        }.firstOrNull() ?: throw IllegalStateException("Falha ao gerar link.")
    }

    suspend fun regenerateCadastroLink(
        session: SavedSession,
        linkId: String,
    ): CadastroLinkItem {
        val rawToken = CadastroLinkCrypto.generateCadastroLinkToken()
        val tokenHash = CadastroLinkCrypto.hashCadastroLinkToken(rawToken)
        val linkUrl = buildPublicAdesaoUrl(rawToken)

        val payload = buildJsonObject {
            put("token_hash", tokenHash)
            put("link_url", linkUrl)
            put("is_active", true)
        }

        return client.safePost<List<CadastroLinkItem>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/cadastro_links?id=eq.$linkId",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
            method = io.ktor.http.HttpMethod.Patch
            contentType(ContentType.Application.Json)
        }.firstOrNull() ?: throw IllegalStateException("Falha ao regerar link.")
    }

    suspend fun deleteCadastroLink(session: SavedSession, linkId: String) {
        client.safeDelete<JsonElement>(
            url = "${AppConfig.supabaseUrl}/rest/v1/cadastro_links?id=eq.$linkId",
            json = json,
        ) {
            applyAuthHeaders(session)
        }
    }

    suspend fun searchEmpresa(
        session: SavedSession,
        value: String,
        type: EmpresaSearchType,
    ): List<EmpresaResumo> {
        val payload = buildJsonObject {
            when (type) {
                EmpresaSearchType.CODIGO -> put("empresaId", value)
                EmpresaSearchType.CNPJ -> put("cnpj", CadastroPayloadBuilder.normalizeDigits(value))
                EmpresaSearchType.NOME -> put("nome", value.trim())
            }
        }

        val response: EmpresaSearchResponse = client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/erp-search-empresa",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
        }

        if (!response.ok) {
            throw IllegalStateException(response.error ?: "Erro ao buscar empresa.")
        }

        return response.empresas
    }

    suspend fun createDraftFromCpf(
        session: SavedSession,
        profile: MobileProfile,
        config: CadastroConfig?,
        input: CpfConsultInput,
    ): CpfConsultResult = coroutineScope {
        val cpf = CadastroPayloadBuilder.normalizeDigits(input.cpf)
        val startedAt = System.currentTimeMillis()

        val existingDeferred = async {
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
            val statusPermiteContinuar = statusNormalizado in setOf("incompleto", "adesoes_pendentes", "pendente")
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

        var warningMessage: String? = null
        var cadastroBase = CadastroBaseData(cpf = cpf)
        var lemmitRaw: JsonElement? = null

        if (config?.ativarLemmit != false) {
            val canUse = runCatching {
                withTimeoutOrNull(8000) { canUseLemmit(session, profile.id) }
            }.onFailure { throwable ->
                Log.w(logTag, "createDraftFromCpf canUseLemmit falhou, seguindo sem lemmit", throwable)
            }.getOrNull()

            if (canUse == true) {
                val lemmitResponse = runCatching {
                    withTimeoutOrNull(12000) { consultarCpfLemmit(session, cpf) }
                }.onFailure { throwable ->
                    Log.w(logTag, "createDraftFromCpf consultarCpfLemmit falhou, seguindo sem lemmit", throwable)
                }.getOrNull()

                if (lemmitResponse != null) {
                    lemmitRaw = json.encodeToJsonElement(LemmitResponse.serializer(), lemmitResponse)
                    cadastroBase = CadastroPayloadBuilder.mapLemmitToCadastro(lemmitResponse, cpf)
                } else {
                    warningMessage = "Consulta Lemmit indisponivel no momento. O rascunho foi criado sem preenchimento automatico."
                }
            } else {
                val limitInfo = runCatching {
                    withTimeoutOrNull(3000) { fetchLemmitLimitInfo(session, profile.id).firstOrNull() }
                }.onFailure { throwable ->
                    Log.w(logTag, "createDraftFromCpf getLemmitLimitInfo falhou", throwable)
                }.getOrNull()

                warningMessage = if (limitInfo?.limiteMensal != null) {
                    "Limite mensal da Lemmit atingido. O rascunho foi criado sem preenchimento automatico."
                } else {
                    "Consulta Lemmit indisponivel para este usuario. O rascunho foi criado sem preenchimento automatico."
                }
            }
        }

        clienteAnteriorDeferred.await()?.let { anterior ->
            cadastroBase = cadastroBase.copy(
                nome = cadastroBase.nome.ifBlank { anterior.nome },
                dataNascimento = cadastroBase.dataNascimento.ifBlank { anterior.dataNascimento },
                sexo = cadastroBase.sexo.ifBlank { anterior.sexo },
                sexoCodigo = if (cadastroBase.nome.isBlank()) anterior.sexoCodigo else cadastroBase.sexoCodigo,
                contatos = if (cadastroBase.contatos.isEmpty()) anterior.contatos else cadastroBase.contatos,
                endereco = cadastroBase.endereco ?: anterior.endereco,
                nomeMae = cadastroBase.nomeMae ?: anterior.nomeMae,
            )
        }

        val enderecoEnriquecido = cadastroBase.endereco
            ?.takeIf { it.cep.isNotBlank() }
            ?.let { endereco ->
                withTimeoutOrNull(7000) { CadastroPayloadBuilder.enrichEndereco(endereco, consultarEnderecoCep(session, endereco.cep)) }
                    ?: endereco
            }

        cadastroBase = cadastroBase.copy(
            dataNascimento = CadastroPayloadBuilder.normalizeIsoDateOrNull(cadastroBase.dataNascimento).orEmpty(),
            endereco = enderecoEnriquecido ?: cadastroBase.endereco,
        )

        val vendedorData = resolveVendedorData(profile, input)
        val adesionistaData = resolveAdesionistaData(profile, input)
        val funcionarioCadastroId = profile.externalId?.toIntOrNull() ?: 0
        val titular = CadastroPayloadBuilder.buildDependenteTitular(cadastroBase, funcionarioCadastroId)

        val draft = withTimeoutOrNull(20000) {
            createOrUpdateRascunho(
                session = session,
                profile = profile,
                payload = buildJsonObject {
                    put("cpf", cpf)
                    put("nome", cadastroBase.nome)
                    cadastroBase.nomeMae?.let { put("nome_mae", it) }
                    CadastroPayloadBuilder.normalizeIsoDateOrNull(cadastroBase.dataNascimento)?.let {
                        put("data_nascimento", it)
                    }
                    put("sexo", cadastroBase.sexo)
                    put("sexo_codigo", cadastroBase.sexoCodigo)
                    put("contatos", json.encodeToJsonElement(ListSerializerCache.contatos, cadastroBase.contatos))
                    cadastroBase.endereco?.let {
                        put("endereco", json.encodeToJsonElement(CadastroEnderecoSerializerCache.serializer, it))
                    }
                    put("dependentes", json.encodeToJsonElement(ListSerializerCache.dependentes, listOf(titular)))
                    put("cliente_sera_usuario", true)
                    put("empresa_id", input.empresa.id)
                    put("empresa_codigo", input.empresa.id)
                    put("empresa_nome", input.empresa.nomeFantasia)
                    put("empresa_cnpj", input.empresa.cnpj)
                    put("empresa_raw", input.empresa.raw ?: JsonObject(emptyMap()))
                    put("empresa_exige_matricula", input.empresa.exigeMatricula ?: 0)
                    put("planos_raw", input.empresa.precoPlano ?: buildJsonArray {})
                    if (lemmitRaw != null) put("lemit_raw", lemmitRaw)
                    vendedorData.forEach { (key, value) -> put(key, value) }
                    adesionistaData.forEach { (key, value) -> put(key, value) }
                },
            )
        } ?: throw IllegalStateException("A criação do rascunho excedeu o tempo limite. Tente novamente.")

        Log.i(logTag, "createDraftFromCpf total took ${System.currentTimeMillis() - startedAt}ms")
        CpfConsultResult(draft = draft, warningMessage = warningMessage)
    }

    suspend fun sendCadastroToErp(
        session: SavedSession,
        profile: MobileProfile,
        config: CadastroConfig?,
        cadastroId: String,
        cadastroPrefetched: CadastroDetalhe? = null,
        arquivoPathHint: String? = null,
        dependentesHint: JsonElement? = null,
        nomeHint: String? = null,
        dataNascimentoHint: String? = null,
        nomeMaeHint: String? = null,
    ): CadastroDetalhe {
        return runCatching {
            val cadastroOriginal = cadastroPrefetched ?: fetchCadastroDetalhe(session, cadastroId)
            val cadastroCore = ensureCadastroCoreFields(session, cadastroOriginal)
            val cadastroComCoreHints = withCoreFieldHints(
                cadastro = cadastroCore,
                nomeHint = nomeHint,
                dataNascimentoHint = dataNascimentoHint,
                nomeMaeHint = nomeMaeHint,
            )
            val cadastroComDependentes = withDependentesHint(cadastroComCoreHints, dependentesHint)
            val cadastro = withArquivoPathHint(cadastroComDependentes, arquivoPathHint)
            validateCadastroReady(cadastro, config)

            val funcionarioCadastroId = profile.externalId?.toIntOrNull()
            val baseData = CadastroPayloadBuilder.detailToBaseData(json, cadastro)
            val dependentes = CadastroPayloadBuilder.detailDependentes(json, cadastro, funcionarioCadastroId ?: 0)
            val payload = CadastroPayloadBuilder.buildErpPayload(
                cadastro = baseData,
                dependentes = dependentes,
                empresaId = cadastro.empresaId ?: cadastro.empresaCodigo
                ?: throw IllegalStateException("Empresa nao vinculada ao cadastro."),
                vendedorCodigo = cadastro.vendedorCodigo,
                funcionarioCadastroId = funcionarioCadastroId,
                userRole = profile.role,
                userExternalId = profile.externalId,
                adesionistaCodigo = cadastro.adesionistaCodigo,
            )

            val response = enviarParaErp(session, cadastroId, payload)
            val firstDependenteId = CadastroPayloadBuilder.firstDependenteCodigo(response)
            if (cadastro.arquivoPath != null && firstDependenteId != null && funcionarioCadastroId != null) {
                processDocumentoUpload(
                    session = session,
                    cadastro = cadastro,
                    funcionarioCadastroId = funcionarioCadastroId,
                    dependenteId = firstDependenteId,
                )
            }

            fetchCadastroDetalhe(session, cadastroId)
        }.getOrThrow()
    }

    private fun withArquivoPathHint(
        cadastro: CadastroDetalhe,
        arquivoPathHint: String?,
    ): CadastroDetalhe {
        val arquivoAtual = cadastro.arquivoPath?.trim().orEmpty()
        val hintNormalizado = arquivoPathHint?.trim().orEmpty()
        if (arquivoAtual.isNotBlank() || hintNormalizado.isBlank()) return cadastro
        return cadastro.copy(arquivoPath = hintNormalizado)
    }

    private fun withDependentesHint(
        cadastro: CadastroDetalhe,
        dependentesHint: JsonElement?,
    ): CadastroDetalhe {
        val hintArray = runCatching { dependentesHint?.jsonArray }.getOrNull()
        if (hintArray.isNullOrEmpty()) return cadastro
        return cadastro.copy(dependentes = dependentesHint)
    }

    private fun withCoreFieldHints(
        cadastro: CadastroDetalhe,
        nomeHint: String?,
        dataNascimentoHint: String?,
        nomeMaeHint: String?,
    ): CadastroDetalhe {
        val nome = nomeHint
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: cadastro.nome
        val dataNascimento = dataNascimentoHint
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: cadastro.dataNascimento
        val nomeMae = nomeMaeHint
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: cadastro.nomeMae

        if (nome == cadastro.nome && dataNascimento == cadastro.dataNascimento && nomeMae == cadastro.nomeMae) {
            return cadastro
        }
        return cadastro.copy(
            nome = nome,
            dataNascimento = dataNascimento,
            nomeMae = nomeMae,
        )
    }

    private suspend fun ensureCadastroCoreFields(
        session: SavedSession,
        cadastro: CadastroDetalhe,
    ): CadastroDetalhe {
        val dependenteTitular = runCatching {
            cadastro.dependentes
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
        }.getOrNull()

        val nomeAtual = cadastro.nome?.trim().orEmpty()
        val dataAtual = cadastro.dataNascimento?.trim().orEmpty()
        val nomeMaeAtual = cadastro.nomeMae?.trim().orEmpty()

        val nomeRecuperado = if (nomeAtual.isBlank()) {
            dependenteTitular?.get("nome")?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: cadastro.responsavelFinanceiroNome?.trim()?.takeIf { it.isNotBlank() }
        } else null

        val dataRecuperada = if (dataAtual.isBlank()) {
            dependenteTitular?.get("dataNascimento")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: dependenteTitular?.get("data_nascimento")?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
        } else null

        val nomeMaeRecuperado = if (nomeMaeAtual.isBlank()) {
            dependenteTitular?.get("nomeMae")?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: dependenteTitular?.get("nome_mae")?.jsonPrimitive?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
        } else null

        if (nomeRecuperado == null && dataRecuperada == null && nomeMaeRecuperado == null) {
            return cadastro
        }

        return updateCadastro(
            session = session,
            id = cadastro.id,
            payload = buildJsonObject {
                nomeRecuperado?.let { put("nome", it) }
                dataRecuperada?.let { put("data_nascimento", it) }
                nomeMaeRecuperado?.let { put("nome_mae", it) }
            },
        )
    }

    suspend fun fetchCadastroDetalhe(session: SavedSession, id: String): CadastroDetalhe {
        return getList<CadastroDetalhe>(
            path = "cadastros",
            session = session,
            query = {
                parameter("id", "eq.$id")
                parameter(
                    "select",
                    "id,status,tipo_cadastro,nome,cpf,data_nascimento,sexo_codigo,nome_mae,contatos,endereco,dependentes,empresa_id,empresa_codigo,empresa_nome,empresa_cnpj,empresa_exige_matricula,empresa_raw,planos_raw,numero_matricula,status_adesao_id,vendedor_nome,vendedor_codigo,adesionista_nome,adesionista_codigo,responsavel_financeiro_codigo,responsavel_financeiro_nome,responsavel_financeiro_cpf,contatos_responsavel_financeiro,motivo_bloqueio,erp_response,arquivo_path,plano_codigo,created_at,updated_at"
                )
                parameter("limit", 1)
            },
        ).firstOrNull() ?: throw IllegalStateException("Cadastro nao encontrado.")
    }

    suspend fun updateCadastro(session: SavedSession, id: String, payload: JsonObject): CadastroDetalhe {
        val payloadComCreatedBy = buildJsonObject {
            payload.forEach { (key, value) -> put(key, value) }
            val createdByPayload = payload["created_by"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
            if (createdByPayload.isNullOrBlank()) {
                put("created_by", session.userId)
            }
        }
        client.safePost<JsonElement>(
            url = "${AppConfig.supabaseUrl}/rest/v1/cadastros?id=eq.$id",
            json = json,
            body = payloadComCreatedBy,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
            header("Content-Profile", "public")
            header("Accept-Profile", "public")
            method = io.ktor.http.HttpMethod.Patch
            contentType(ContentType.Application.Json)
        }

        return fetchCadastroDetalhe(session, id)
    }

    suspend fun createCadastroDraft(
        session: SavedSession,
        profile: MobileProfile,
        payload: JsonObject,
    ): CadastroDetalhe {
        val profileId = ensureProfileReadyForCadastroInsert(session, profile)
        val tipoCadastro = payload["tipo_cadastro"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val cpf = payload["cpf"]?.jsonPrimitive?.contentOrNull?.filter(Char::isDigit).orEmpty()
        val responsavelCpf = payload["responsavel_financeiro_cpf"]?.jsonPrimitive?.contentOrNull?.filter(Char::isDigit).orEmpty()
        val existing = when {
            tipoCadastro == "inclusao_dependente" && responsavelCpf.isNotBlank() -> {
                getList<CadastroIdRow>(
                    path = "cadastros",
                    session = session,
                    query = {
                        parameter("tipo_cadastro", "eq.inclusao_dependente")
                        parameter("status", "eq.incompleto")
                        parameter("responsavel_financeiro_cpf", "eq.$responsavelCpf")
                        parameter("created_by", "eq.$profileId")
                        parameter("select", "id")
                        parameter("order", "updated_at.desc")
                        parameter("limit", 1)
                    },
                ).firstOrNull()
            }
            cpf.isNotBlank() -> {
                getList<CadastroIdRow>(
                    path = "cadastros",
                    session = session,
                    query = {
                        parameter("tipo_cadastro", "eq.cadastro")
                        parameter("status", "eq.incompleto")
                        parameter("cpf", "eq.$cpf")
                        parameter("created_by", "eq.$profileId")
                        parameter("select", "id")
                        parameter("order", "updated_at.desc")
                        parameter("limit", 1)
                    },
                ).firstOrNull()
            }
            else -> null
        }

        return if (existing != null) {
            val updatePayload = buildJsonObject {
                payload.forEach { (key, value) -> put(key, value) }
                put("created_by", profileId)
                profile.teamId?.let { put("team_id", it) }
            }
            client.safePost<List<CadastroDetalhe>>(
                url = "${AppConfig.supabaseUrl}/rest/v1/cadastros?id=eq.${existing.id}",
                json = json,
                body = updatePayload,
            ) {
                applyAuthHeaders(session)
                header("Prefer", "return=representation")
                method = io.ktor.http.HttpMethod.Patch
                contentType(ContentType.Application.Json)
            }.firstOrNull() ?: fetchCadastroDetalhe(session, existing.id)
        } else {
            val insertPayload = buildJsonObject {
                payload.forEach { (key, value) -> put(key, value) }
                put("created_by", profileId)
                profile.teamId?.let { put("team_id", it) }
                put("status", "incompleto")
            }

            val insertedRows = try {
                client.safePost<List<CadastroDetalhe>>(
                    url = "${AppConfig.supabaseUrl}/rest/v1/cadastros",
                    json = json,
                    body = insertPayload,
                ) {
                    applyAuthHeaders(session)
                    header("Prefer", "return=representation")
                }
            } catch (throwable: Throwable) {
                if (!isDuplicateDraftConstraintError(throwable)) throw throwable

                val existingAfterConflict = when {
                    tipoCadastro == "inclusao_dependente" && responsavelCpf.isNotBlank() -> {
                        getList<CadastroIdRow>(
                            path = "cadastros",
                            session = session,
                            query = {
                                parameter("tipo_cadastro", "eq.inclusao_dependente")
                                parameter("status", "eq.incompleto")
                                parameter("responsavel_financeiro_cpf", "eq.$responsavelCpf")
                                parameter("created_by", "eq.$profileId")
                                parameter("select", "id")
                                parameter("order", "updated_at.desc")
                                parameter("limit", 1)
                            },
                        ).firstOrNull()
                    }
                    cpf.isNotBlank() -> {
                        getList<CadastroIdRow>(
                            path = "cadastros",
                            session = session,
                            query = {
                                parameter("tipo_cadastro", "eq.cadastro")
                                parameter("status", "eq.incompleto")
                                parameter("cpf", "eq.$cpf")
                                parameter("created_by", "eq.$profileId")
                                parameter("select", "id")
                                parameter("order", "updated_at.desc")
                                parameter("limit", 1)
                            },
                        ).firstOrNull()
                    }
                    else -> null
                }

                if (existingAfterConflict != null) {
                    return fetchCadastroDetalhe(session, existingAfterConflict.id)
                }
                throw throwable
            }

            insertedRows.firstOrNull() ?: throw IllegalStateException("Falha ao criar rascunho.")
        }
    }

    suspend fun deleteCadastro(session: SavedSession, id: String) {
        client.safeDelete<JsonElement>(
            url = "${AppConfig.supabaseUrl}/rest/v1/cadastros?id=eq.$id",
            json = json,
        ) {
            applyAuthHeaders(session)
        }
    }

    suspend fun deleteCadastroLogico(
        session: SavedSession,
        cadastroId: String,
        motivoExclusao: String,
    ): JsonElement {
        return client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/excluir-cadastro",
            json = json,
            body = buildJsonObject {
                put("cadastroId", cadastroId)
                put("motivoExclusao", motivoExclusao)
            },
        ) {
            applyAuthHeaders(session)
        }
    }

    suspend fun uploadTempFile(
        session: SavedSession,
        userId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        prefix: String = "",
    ): UploadedTempFile {
        val sanitizedName = fileName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .lowercase()
        val finalName = "${System.currentTimeMillis()}_$sanitizedName"
        val path = if (prefix.isBlank()) "$userId/$finalName" else "$userId/$prefix/$finalName"

        client.safePutBytes(
            url = "${AppConfig.supabaseUrl}/storage/v1/object/cadastros-temp-files/$path",
            json = json,
            bytes = bytes,
            contentTypeValue = mimeType,
        ) {
            applyAuthHeaders(session)
            header("x-upsert", "false")
        }

        return UploadedTempFile(
            nome = fileName,
            path = path,
            mime = mimeType,
            size = bytes.size.toLong(),
        )
    }

    suspend fun deleteTempFile(session: SavedSession, path: String) {
        client.safeDelete<JsonElement>(
            url = "${AppConfig.supabaseUrl}/storage/v1/object/cadastros-temp-files/$path",
            json = json,
        ) {
            applyAuthHeaders(session)
        }
    }

    suspend fun downloadTempFile(session: SavedSession, path: String): ByteArray {
        val normalizedPath = path.trim().removePrefix("/")
        if (normalizedPath.isBlank()) throw IllegalStateException("Arquivo nao informado para visualizacao.")
        return client.safeGet(
            url = "${AppConfig.supabaseUrl}/storage/v1/object/cadastros-temp-files/$normalizedPath",
            json = json,
        ) {
            applyAuthHeaders(session)
            header(HttpHeaders.Accept, "*/*")
        }
    }

    private suspend fun validateCadastroReady(cadastro: CadastroDetalhe, config: CadastroConfig?) {
        if (cadastro.nome.isNullOrBlank()) throw IllegalStateException("Cadastro sem nome.")
        if (cadastro.dataNascimento.isNullOrBlank()) throw IllegalStateException("Cadastro sem data de nascimento.")
        if ((cadastro.empresaId ?: cadastro.empresaCodigo) == null) throw IllegalStateException("Selecione uma empresa antes de enviar.")
        val dependentes = CadastroPayloadBuilder.detailDependentes(json, cadastro, 0)
        if (dependentes.isEmpty()) {
            throw IllegalStateException("Cadastro sem dependentes validos para envio.")
        }
        dependentes.forEachIndexed { index, dependente ->
            if (dependente.plano <= 0) {
                val alvo = if (index == 0) "titular" else "dependente ${index + 1}"
                throw IllegalStateException("Selecione um plano valido para $alvo antes de enviar.")
            }
        }
        if (config?.exigirArquivo == true && cadastro.arquivoPath.isNullOrBlank()) {
            Log.w(logTag, "validateCadastroReady arquivo ausente id=${cadastro.id} exigirArquivo=true")
            throw IllegalStateException("Esta configuracao exige arquivo anexado antes do envio.")
        }
    }

    private suspend fun processDocumentoUpload(
        session: SavedSession,
        cadastro: CadastroDetalhe,
        funcionarioCadastroId: Int,
        dependenteId: Int,
    ) {
        val payload = buildJsonObject {
            put("idFuncionario", funcionarioCadastroId)
            put("idDependente", dependenteId)
            put("arquivoPath", cadastro.arquivoPath)
            put("arquivoNome", cadastro.arquivoPath?.substringAfterLast('/'))
            put("bucket", "cadastros-temp-files")
        }

        val uploadResponse = runCatching {
            client.safePost<JsonElement>(
                url = "${AppConfig.supabaseUrl}/functions/v1/erp-upload-documento",
                json = json,
                body = payload,
            ) {
                applyAuthHeaders(session)
            }
        }.getOrNull()

        val success = uploadResponse
            ?.jsonObject
            ?.get("success")
            ?.jsonPrimitive
            ?.content == "true"

        if (!success) {
            client.safePost<JsonElement>(
                url = "${AppConfig.supabaseUrl}/functions/v1/erp-enqueue-upload",
                json = json,
                body = buildJsonObject {
                    put("cadastroId", cadastro.id)
                    put("idFuncionario", funcionarioCadastroId)
                    put("idDependente", dependenteId)
                    put("arquivoPath", cadastro.arquivoPath)
                    put("arquivoNome", cadastro.arquivoPath?.substringAfterLast('/'))
                    put("tipo", "titular")
                },
            ) {
                applyAuthHeaders(session)
            }
        }
    }

    private suspend fun enviarParaErp(
        session: SavedSession,
        cadastroId: String,
        payload: JsonObject,
    ): JsonElement {
        val response: JsonElement = client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/erp-novo-usuario2",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("X-Idempotency-Key", "cadastro:$cadastroId")
            header("X-Cadastro-Id", cadastroId)
        }

        if (response is JsonObject && response["error"] != null) {
            syncCadastroAfterSend(session, cadastroId, payload, response, false)
            val mensagem = response["error"]?.jsonPrimitive?.content
                ?: "Erro ao enviar cadastro para o ERP."
            throw IllegalStateException(mensagem)
        }

        val dados = response.jsonObject["data"]?.jsonObject?.get("dados")
        if (dados == null || dados is JsonPrimitive && dados.content == "null") {
            syncCadastroAfterSend(session, cadastroId, payload, response, false)
            throw IllegalStateException("ERP nao retornou dados validos para o cadastro.")
        }

        syncCadastroAfterSend(session, cadastroId, payload, response, true)
        return response
    }

    private suspend fun syncCadastroAfterSend(
        session: SavedSession,
        cadastroId: String,
        payload: JsonElement,
        response: JsonElement,
        success: Boolean,
    ) {
        val body = buildJsonObject {
            put("status", if (success) "enviado" else "incompleto")
            put("payload_erp", payload)
            put("erp_response", response)
            put("created_by", session.userId)
        }

        client.safePost<JsonElement>(
            url = "${AppConfig.supabaseUrl}/rest/v1/cadastros?id=eq.$cadastroId",
            json = json,
            body = body,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
            header("Content-Profile", "public")
            header("Accept-Profile", "public")
            method = io.ktor.http.HttpMethod.Patch
            contentType(ContentType.Application.Json)
        }
    }

    private suspend fun createOrUpdateRascunho(
        session: SavedSession,
        profile: MobileProfile,
        payload: JsonObject,
    ): CadastroDetalhe {
        val profileId = ensureProfileReadyForCadastroInsert(session, profile)
        val existing = getList<CadastroIdRow>(
            path = "cadastros",
            session = session,
            query = {
                parameter("cpf", "eq.${payload["cpf"]?.jsonPrimitive?.content}")
                parameter("status", "eq.incompleto")
                parameter("created_by", "eq.$profileId")
                parameter("select", "id")
                parameter("order", "updated_at.desc")
                parameter("limit", 1)
            },
        ).firstOrNull()

        return if (existing != null) {
            val updatePayload = buildJsonObject {
                payload.forEach { (key, value) -> put(key, value) }
                put("created_by", profileId)
                profile.teamId?.let { put("team_id", it) }
            }
            client.safePost<List<CadastroDetalhe>>(
                url = "${AppConfig.supabaseUrl}/rest/v1/cadastros?id=eq.${existing.id}",
                json = json,
                body = updatePayload,
            ) {
                applyAuthHeaders(session)
                header("Prefer", "return=representation")
                method = io.ktor.http.HttpMethod.Patch
                contentType(ContentType.Application.Json)
            }.firstOrNull() ?: fetchCadastroDetalhe(session, existing.id)
        } else {
            val insertPayload = buildJsonObject {
                payload.forEach { (key, value) -> put(key, value) }
                put("created_by", profileId)
                profile.teamId?.let { put("team_id", it) }
                put("status", "incompleto")
            }

            val insertedRows = try {
                client.safePost<List<CadastroDetalhe>>(
                    url = "${AppConfig.supabaseUrl}/rest/v1/cadastros",
                    json = json,
                    body = insertPayload,
                ) {
                    applyAuthHeaders(session)
                    header("Prefer", "return=representation")
                }
            } catch (throwable: Throwable) {
                if (!isDuplicateDraftConstraintError(throwable)) throw throwable

                val existingAfterConflict = getList<CadastroIdRow>(
                    path = "cadastros",
                    session = session,
                    query = {
                        parameter("cpf", "eq.${payload["cpf"]?.jsonPrimitive?.content}")
                        parameter("status", "eq.incompleto")
                        parameter("created_by", "eq.$profileId")
                        parameter("select", "id")
                        parameter("order", "updated_at.desc")
                        parameter("limit", 1)
                    },
                ).firstOrNull()

                if (existingAfterConflict != null) {
                    return fetchCadastroDetalhe(session, existingAfterConflict.id)
                }
                throw throwable
            }

            insertedRows.firstOrNull() ?: throw IllegalStateException("Falha ao criar rascunho.")
        }
    }

    private suspend fun ensureProfileReadyForCadastroInsert(
        session: SavedSession,
        profile: MobileProfile,
    ): String {
        val profileId = profile.id.trim()
        if (profileId.isBlank()) {
            throw IllegalStateException("Sua sessão expirou. Faça login novamente para continuar.")
        }

        val exists = getList<CadastroIdRow>(
            path = "profiles",
            session = session,
            query = {
                parameter("id", "eq.$profileId")
                parameter("select", "id")
                parameter("limit", 1)
            },
        ).firstOrNull() != null

        if (!exists) {
            throw IllegalStateException("Sua sessão expirou. Faça login novamente para continuar.")
        }
        return profileId
    }

    private suspend fun checkCpfExistente(
        session: SavedSession,
        userId: String,
        cpf: String,
    ): CheckCpfExistenteResponse {
        return client.safePost(
            url = "${AppConfig.supabaseUrl}/rest/v1/rpc/check_cpf_existente",
            json = json,
            body = buildJsonObject {
                put("p_cpf", cpf)
                put("p_user_id", userId)
            },
        ) {
            applyAuthHeaders(session)
        }
    }

    private suspend fun canUseLemmit(session: SavedSession, userId: String): Boolean {
        return client.safePost(
            url = "${AppConfig.supabaseUrl}/rest/v1/rpc/can_use_lemmit",
            json = json,
            body = buildJsonObject { put("p_user_id", userId) },
        ) {
            applyAuthHeaders(session)
        }
    }

    private suspend fun fetchLemmitLimitInfo(session: SavedSession, userId: String): List<LemmitLimitInfo> {
        return client.safePost(
            url = "${AppConfig.supabaseUrl}/rest/v1/rpc/get_lemmit_limit_info",
            json = json,
            body = buildJsonObject { put("p_user_id", userId) },
        ) {
            applyAuthHeaders(session)
        }
    }

    private suspend fun consultarCpfLemmit(session: SavedSession, cpf: String): LemmitResponse {
        return client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/lemit-consulta-pessoa",
            json = json,
            body = buildJsonObject { put("cpf", cpf) },
        ) {
            applyAuthHeaders(session)
        }
    }

    private suspend fun consultarEnderecoCep(session: SavedSession, cep: String): JsonElement {
        return client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/erp-endereco-cep",
            json = json,
            body = buildJsonObject { put("cep", CadastroPayloadBuilder.normalizeDigits(cep)) },
        ) {
            applyAuthHeaders(session)
        }
    }

    suspend fun buscarResponsaveisFinanceiros(
        session: SavedSession,
        tipoBusca: InclusaoBuscaTipo,
        valor: String,
    ): List<ResponsavelFinanceiroResumo> {
        val payload = buildJsonObject {
            when (tipoBusca) {
                InclusaoBuscaTipo.CODIGO -> put("codigoAssociado", valor.trim())
                InclusaoBuscaTipo.CPF -> put("cpf", CadastroPayloadBuilder.normalizeDigits(valor))
            }
        }

        val result: JsonElement = client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/erp-check-associado",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
        }

        val root = result.jsonObject
        val explicitError = root["error"]?.jsonPrimitive?.contentOrNull
            ?: root["message"]?.jsonPrimitive?.contentOrNull
        if (!explicitError.isNullOrBlank()) {
            throw IllegalStateException(explicitError)
        }

        val dados = root["dados"]?.jsonArray ?: emptyList<JsonElement>()
        return dados.map { item ->
            val associado = item.jsonObject
            val dependentesRaw = associado["dependentes"]?.jsonArray ?: emptyList<JsonElement>()
            ResponsavelFinanceiroResumo(
                codigo = associado["codigo"]?.jsonPrimitive?.intOrNull ?: 0,
                codigoEmpresa = associado["codigoDaEmpresa"]?.jsonPrimitive?.intOrNull ?: 0,
                nome = associado["nome"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                cpf = associado["cpf"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                empresa = associado["nomeFantasiaDaEmpresa"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                dependentes = dependentesRaw.map { dependenteElement ->
                    val dependente = dependenteElement.jsonObject
                    ResponsavelDependenteResumo(
                        codigoDependente = dependente["codigoDependente"]?.jsonPrimitive?.intOrNull ?: 0,
                        numeroCpfDependente = dependente["numeroCpfDependente"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        codigoPlano = dependente["codigoPlano"]?.jsonPrimitive?.intOrNull ?: 0,
                        nomeSituacao = dependente["nomeSituacao"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        codigoSituacao = dependente["codigoSituacao"]?.jsonPrimitive?.intOrNull ?: 0,
                    )
                },
            )
        }
    }

    suspend fun uploadDependenteDocumento(
        session: SavedSession,
        idFuncionario: Int,
        idDependente: Int,
        arquivoPath: String,
        arquivoNome: String,
        bucket: String = "cadastros-temp-files",
    ): Boolean {
        val response: JsonElement = client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/erp-upload-documento",
            json = json,
            body = buildJsonObject {
                put("idFuncionario", idFuncionario)
                put("idDependente", idDependente)
                put("arquivoPath", arquivoPath)
                put("arquivoNome", arquivoNome)
                put("bucket", bucket)
            },
        ) {
            applyAuthHeaders(session)
        }

        return response
            .jsonObject["success"]
            ?.jsonPrimitive
            ?.booleanOrNull == true
    }

    suspend fun enqueueDependenteUpload(
        session: SavedSession,
        cadastroId: String?,
        idFuncionario: Int,
        idDependente: Int,
        arquivoPath: String,
        arquivoNome: String,
        tipo: String = "dependente",
        bucket: String = "cadastros-temp-files",
    ): Boolean {
        val response: JsonElement = client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/erp-enqueue-upload",
            json = json,
            body = buildJsonObject {
                if (!cadastroId.isNullOrBlank()) {
                    put("cadastroId", cadastroId)
                } else {
                    put("cadastroId", JsonNull)
                }
                put("idFuncionario", idFuncionario)
                put("idDependente", idDependente)
                put("arquivoPath", arquivoPath)
                put("arquivoNome", arquivoNome)
                put("tipo", tipo)
                put("bucket", bucket)
            },
        ) {
            applyAuthHeaders(session)
        }

        return response
            .jsonObject["queued"]
            ?.jsonPrimitive
            ?.booleanOrNull == true
    }

    suspend fun enviarInclusaoDependente(session: SavedSession, payload: JsonObject): JsonElement {
        return client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/erp-novo-dependente",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
        }
    }

    suspend fun resolvePublicCadastroLink(token: String): PublicCadastroLinkResolveResponse {
        return client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/cadastro-link-resolve",
            json = json,
            body = buildJsonObject {
                put("token", token.trim())
            },
        )
    }

    suspend fun checkPublicCadastroCpf(token: String, cpf: String): PublicCadastroCheckCpfResponse {
        return client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/cadastro-link-check-cpf",
            json = json,
            body = buildJsonObject {
                put("token", token.trim())
                put("cpf", CadastroPayloadBuilder.normalizeDigits(cpf))
            },
        )
    }

    suspend fun submitPublicCadastro(
        token: String,
        cadastro: PublicCadastroPayload,
    ): PublicCadastroSubmitResponse {
        return client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/cadastro-public-submit",
            json = json,
            body = buildJsonObject {
                put("token", token.trim())
                put("cadastro", json.encodeToJsonElement(PublicCadastroPayload.serializer(), cadastro))
            },
        )
    }

    private suspend fun checkErpAssociado(session: SavedSession, cpf: String): ErpAssociadoResponse {
        return client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/erp-check-associado",
            json = json,
            body = buildJsonObject { put("cpf", cpf) },
        ) {
            applyAuthHeaders(session)
        }
    }

    private suspend fun findClienteByCpf(session: SavedSession, cpf: String): ClienteAnterior? {
        val cadastro = getList<ClienteLookupRow>(
            path = "cadastros",
            session = session,
            query = {
                parameter("cpf", "eq.$cpf")
                parameter("erp_dados_associado", "not.is.null")
                parameter("order", "created_at.desc")
                parameter(
                    "select",
                    "id,nome,nome_mae,data_nascimento,sexo_codigo,contatos,endereco,erp_dados_associado,created_at,updated_at"
                )
                parameter("limit", 1)
            },
        ).firstOrNull() ?: return null

        val contatosDecodificados = runCatching {
            json.decodeFromJsonElement(ListSerializerCache.contatos, cadastro.contatos ?: buildJsonArray {})
        }.getOrDefault(emptyList())
        val contatos = contatosDecodificados
            .ifEmpty { parseCadastroContatosFlex(cadastro.contatos) }
            .ifEmpty { parseContatoFromErpAssociado(cadastro.erpDadosAssociado) }
            .let { contatosList ->
                if (contatosList.none { it.principal } && contatosList.isNotEmpty()) {
                    contatosList.mapIndexed { index, contato -> contato.copy(principal = index == 0) }
                } else {
                    contatosList
                }
            }
        val endereco = runCatching {
            cadastro.endereco?.let { json.decodeFromJsonElement(CadastroEnderecoSerializerCache.serializer, it) }
        }.getOrNull()

        val erpDados = cadastro.erpDadosAssociado?.jsonObject?.get("dados")?.jsonArray ?: return null
        val associados = runCatching {
            json.decodeFromJsonElement(ListSerializerCache.erpAssociados, erpDados)
        }.getOrDefault(emptyList())

        val maisRecente = associados
            .flatMap { associado ->
                associado.dependentes.map { dependente -> associado to dependente }
            }
            .sortedByDescending { (_, dependente) -> dependente.dataSituacao.orEmpty() }
            .firstOrNull()

        val associado = maisRecente?.first ?: associados.firstOrNull() ?: return null
        val dependente = maisRecente?.second ?: associado.dependentes.firstOrNull()

        return ClienteAnterior(
            nome = associado.nome.orEmpty().ifBlank { cadastro.nome.orEmpty() },
            nomeMae = cadastro.nomeMae,
            dataNascimento = dependente?.dataNascimento ?: cadastro.dataNascimento.orEmpty(),
            sexo = when (cadastro.sexoCodigo) {
                1 -> "M"
                0 -> "F"
                else -> ""
            },
            sexoCodigo = cadastro.sexoCodigo ?: 0,
            contatos = contatos,
            endereco = endereco,
        )
    }

    private fun resolveVendedorData(
        profile: MobileProfile,
        input: CpfConsultInput,
    ): Map<String, String> {
        return when {
            profile.role == "VENDEDOR" && !profile.externalId.isNullOrBlank() -> mapOf(
                "vendedor_id" to profile.id,
                "vendedor_codigo" to profile.externalId,
                "vendedor_nome" to profile.name,
            )
            input.vendedorSelecionado != null -> mapOf(
                "vendedor_id" to input.vendedorSelecionado.id,
                "vendedor_codigo" to input.vendedorSelecionado.externalId.orEmpty(),
                "vendedor_nome" to input.vendedorSelecionado.name,
            )
            else -> emptyMap()
        }
    }

    private fun resolveAdesionistaData(
        profile: MobileProfile,
        input: CpfConsultInput,
    ): Map<String, String> {
        val adesionista = input.adesionistaSelecionado ?: when {
            profile.role == "ADESIONISTA" -> TeamMemberOption(
                id = profile.id,
                name = profile.name,
                email = profile.email,
                externalId = profile.externalId,
            )
            profile.role == "ADMINISTRADOR" && !profile.externalId.isNullOrBlank() -> TeamMemberOption(
                id = profile.id,
                name = profile.name,
                email = profile.email,
                externalId = profile.externalId,
            )
            else -> null
        }

        return adesionista?.externalId?.takeIf { it.isNotBlank() }?.let {
            mapOf(
                "adesionista_id" to adesionista.id,
                "adesionista_codigo" to it,
                "adesionista_nome" to adesionista.name,
            )
        } ?: emptyMap()
    }

    private fun buildPublicAdesaoUrl(token: String): String {
        val base = AppConfig.publicAppUrl.trim().removeSuffix("/")
        if (base.isBlank()) {
            throw IllegalStateException("PUBLIC_APP_URL nao configurada para gerar links.")
        }
        return "$base/adesao/$token"
    }

    private suspend inline fun <reified T> getList(
        path: String,
        session: SavedSession,
        noinline query: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): List<T> {
        return client.safeGet(
            url = "${AppConfig.supabaseUrl}/rest/v1/$path",
            json = json,
        ) {
            applyAuthHeaders(session)
            query()
        }
    }
}

private data class ClienteAnterior(
    val nome: String,
    val nomeMae: String?,
    val dataNascimento: String,
    val sexo: String,
    val sexoCodigo: Int,
    val contatos: List<CadastroContato>,
    val endereco: br.com.vendamais.mobile.data.models.CadastroEndereco?,
)

data class UploadedTempFile(
    val nome: String,
    val path: String,
    val mime: String,
    val size: Long,
)

class CadastroExistenteException(
    val cadastroId: String?,
    val empresaNome: String?,
    val canContinue: Boolean,
    message: String,
) : IllegalStateException(message)

enum class InclusaoBuscaTipo {
    CODIGO,
    CPF,
}

data class ResponsavelDependenteResumo(
    val codigoDependente: Int,
    val numeroCpfDependente: String,
    val codigoPlano: Int,
    val nomeSituacao: String,
    val codigoSituacao: Int,
)

data class ResponsavelFinanceiroResumo(
    val codigo: Int,
    val codigoEmpresa: Int,
    val nome: String,
    val cpf: String,
    val empresa: String,
    val dependentes: List<ResponsavelDependenteResumo> = emptyList(),
)

@kotlinx.serialization.Serializable
private data class CadastroIdRow(
    val id: String,
)

@kotlinx.serialization.Serializable
private data class ClienteLookupRow(
    val id: String,
    val nome: String? = null,
    @kotlinx.serialization.SerialName("nome_mae")
    val nomeMae: String? = null,
    @kotlinx.serialization.SerialName("data_nascimento")
    val dataNascimento: String? = null,
    @kotlinx.serialization.SerialName("sexo_codigo")
    val sexoCodigo: Int? = null,
    val contatos: JsonElement? = null,
    val endereco: JsonElement? = null,
    @kotlinx.serialization.SerialName("erp_dados_associado")
    val erpDadosAssociado: JsonElement? = null,
)

private object CadastroEnderecoSerializerCache {
    val serializer = br.com.vendamais.mobile.data.models.CadastroEndereco.serializer()
}

private object ListSerializerCache {
    val contatos = kotlinx.serialization.builtins.ListSerializer(CadastroContato.serializer())
    val dependentes = kotlinx.serialization.builtins.ListSerializer(br.com.vendamais.mobile.data.models.DependenteCadastro.serializer())
    val erpAssociados = kotlinx.serialization.builtins.ListSerializer(ErpAssociadoItem.serializer())
}

private fun parseCadastroContatosFlex(raw: JsonElement?): List<CadastroContato> {
    val contatosArray = when (raw) {
        is kotlinx.serialization.json.JsonArray -> raw
        is JsonObject -> {
            raw["contatos"]?.let { runCatching { it.jsonArray }.getOrNull() }
                ?: raw["telefones"]?.let { runCatching { it.jsonArray }.getOrNull() }
                ?: raw["items"]?.let { runCatching { it.jsonArray }.getOrNull() }
        }
        else -> raw?.let { runCatching { it.jsonArray }.getOrNull() }
    } ?: return emptyList()

    return contatosArray.mapNotNull { item ->
        val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
        val tipoRaw = obj.readString("tipo", "tipoContato", "tipo_contato", "kind")
            ?.lowercase(Locale.ROOT)
            ?.trim()
            .orEmpty()
        val valorRaw = obj.readString("valor", "telefone", "numero", "contato", "email", "value")
            ?.trim()
            .orEmpty()
        if (valorRaw.isBlank()) return@mapNotNull null

        val tipo = when (tipoRaw) {
            "cel", "cell", "celular" -> "celular"
            "fixo", "telefone", "residencial" -> "fixo"
            "whatsapp", "zap" -> "whatsapp"
            "email", "e-mail" -> "email"
            else -> if (valorRaw.contains('@')) "email" else "celular"
        }
        val valor = if (tipo == "email") valorRaw else valorRaw.filter(Char::isDigit)
        if (valor.isBlank()) return@mapNotNull null

        val principal = obj["principal"]?.jsonPrimitive?.booleanOrNull
            ?: obj["isPrincipal"]?.jsonPrimitive?.booleanOrNull
            ?: obj["prioritario"]?.jsonPrimitive?.booleanOrNull
            ?: obj["principal"]?.jsonPrimitive?.contentOrNull?.equals("true", ignoreCase = true)
            ?: false

        CadastroContato(tipo = tipo, valor = valor, principal = principal)
    }.distinctBy { "${it.tipo}:${it.valor}" }
}

private fun parseContatoFromErpAssociado(raw: JsonElement?): List<CadastroContato> {
    val dados = raw?.jsonObject?.get("dados")?.jsonArray ?: return emptyList()
    val contatos = mutableListOf<CadastroContato>()

    dados.forEach { item ->
        val obj = runCatching { item.jsonObject }.getOrNull() ?: return@forEach

        contatos += parseCadastroContatosFlex(obj["contatos"])

        addContatoIfPresent(contatos, "celular", obj.readString("celular", "numeroCelular", "telefoneCelular", "celular1"))
        addContatoIfPresent(contatos, "celular", obj.readString("celular2"))
        addContatoIfPresent(contatos, "whatsapp", obj.readString("whatsapp"))
        addContatoIfPresent(contatos, "fixo", obj.readString("telefone", "telefone1", "numeroTelefone", "fone"))
        addContatoIfPresent(contatos, "fixo", obj.readString("telefone2"))
    }

    return contatos
        .filter { it.valor.isNotBlank() }
        .distinctBy { "${it.tipo}:${it.valor}" }
}

private fun addContatoIfPresent(
    contatos: MutableList<CadastroContato>,
    tipo: String,
    rawValue: String?,
) {
    val normalized = rawValue?.filter(Char::isDigit).orEmpty()
    if (normalized.length < 10) return
    contatos += CadastroContato(tipo = tipo, valor = normalized, principal = false)
}

private fun JsonObject.readString(vararg keys: String): String? {
    keys.forEach { key ->
        val value = this[key]?.jsonPrimitive?.contentOrNull?.trim()
        if (!value.isNullOrBlank()) return value
    }
    return null
}
