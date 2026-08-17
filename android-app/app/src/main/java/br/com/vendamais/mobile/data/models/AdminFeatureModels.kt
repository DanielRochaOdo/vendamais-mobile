package br.com.vendamais.mobile.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


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

@Serializable
data class AuditLemmitResponse(
    val cards: AuditLemmitCards = AuditLemmitCards(),
    @SerialName("usuario_consulta")
    val usuarioConsulta: List<AuditLemmitUsuarioConsulta> = emptyList(),
    @SerialName("usuario_custo")
    val usuarioCusto: List<AuditLemmitUsuarioCusto> = emptyList(),
    @SerialName("ultimas_consultas")
    val ultimasConsultas: List<AuditLemmitUltimaConsulta> = emptyList(),
)

@Serializable
data class AuditLemmitCards(
    @SerialName("total_limite_ajustado")
    val totalLimiteAjustado: Double = 0.0,
    @SerialName("total_consultas")
    val totalConsultas: Int = 0,
    @SerialName("bem_sucedidas")
    val bemSucedidas: Int = 0,
    @SerialName("com_erro")
    val comErro: Int = 0,
    @SerialName("custo_total")
    val custoTotal: Double = 0.0,
)

@Serializable
data class AuditLemmitUsuarioConsulta(
    @SerialName("user_id")
    val userId: String? = null,
    val nome: String = "",
    val consultas: Int = 0,
)

@Serializable
data class AuditLemmitUsuarioCusto(
    @SerialName("user_id")
    val userId: String? = null,
    val nome: String = "",
    @SerialName("custo_total")
    val custoTotal: Double = 0.0,
)

@Serializable
data class AuditLemmitUltimaConsulta(
    val nome: String = "",
    val cpf: String = "",
    val hora: String = "",
)

@Serializable
data class ErpUploadQueueItem(
    val id: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    val status: String,
    val attempts: Int = 0,
    @SerialName("next_attempt_at")
    val nextAttemptAt: String? = null,
    @SerialName("last_attempt_at")
    val lastAttemptAt: String? = null,
    @SerialName("last_error")
    val lastError: String? = null,
    @SerialName("last_status_code")
    val lastStatusCode: Int? = null,
    @SerialName("erp_response")
    val erpResponse: JsonElement? = null,
    @SerialName("cadastro_id")
    val cadastroId: String? = null,
    @SerialName("created_by")
    val createdBy: String? = null,
    @SerialName("id_funcionario")
    val idFuncionario: Int = 0,
    @SerialName("id_dependente")
    val idDependente: Int = 0,
    @SerialName("arquivo_path")
    val arquivoPath: String = "",
    @SerialName("arquivo_nome")
    val arquivoNome: String = "",
    val bucket: String = "cadastros-temp-files",
    val tipo: String = "dependente",
)

@Serializable
data class ProcessUploadQueueResponse(
    val message: String? = null,
    @SerialName("queued_count")
    val queuedCount: Int? = null,
    @SerialName("estimated_time_seconds")
    val estimatedTimeSeconds: Int? = null,
    val note: String? = null,
)

@Serializable
data class ResetStuckQueueResult(
    @SerialName("reset_count")
    val resetCount: Int = 0,
    @SerialName("reset_ids")
    val resetIds: List<String> = emptyList(),
)

@Serializable
data class CadastroExcluidoItem(
    val id: String,
    @SerialName("cadastro_id")
    val cadastroId: String,
    @SerialName("dados_cadastro")
    val dadosCadastro: JsonElement,
    @SerialName("motivo_exclusao")
    val motivoExclusao: String,
    @SerialName("excluido_por")
    val excluidoPor: String,
    @SerialName("excluido_por_nome")
    val excluidoPorNome: String,
    @SerialName("excluido_por_role")
    val excluidoPorRole: String,
    @SerialName("excluido_em")
    val excluidoEm: String,
    @SerialName("team_id")
    val teamId: String? = null,
)

@Serializable
data class PublicCadastroLinkResolveResponse(
    val ok: Boolean = false,
    val link: PublicCadastroLinkInfo? = null,
    val error: String? = null,
)

@Serializable
data class PublicCadastroLinkInfo(
    val id: String,
    @SerialName("empresaCodigo")
    val empresaCodigo: Int,
    @SerialName("empresaNome")
    val empresaNome: String,
    @SerialName("empresaCnpj")
    val empresaCnpj: String? = null,
    @SerialName("empresaRaw")
    val empresaRaw: JsonElement? = null,
    @SerialName("empresaExigeMatricula")
    val empresaExigeMatricula: Int? = null,
    @SerialName("planosRaw")
    val planosRaw: JsonElement? = null,
    @SerialName("planosOcultos")
    val planosOcultos: List<String> = emptyList(),
    val parentescos: List<PublicParentescoInfo> = emptyList(),
    @SerialName("vendedorCodigo")
    val vendedorCodigo: String? = null,
    @SerialName("vendedorNome")
    val vendedorNome: String? = null,
    @SerialName("vendedorTelefone")
    val vendedorTelefone: String? = null,
)

@Serializable
data class PublicParentescoInfo(
    @SerialName("parentescoId")
    val parentescoId: Int,
    val label: String,
    val ativo: Boolean = true,
)

@Serializable
data class PublicCadastroCheckCpfResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val code: String? = null,
)

@Serializable
data class PublicCadastroSubmitResponse(
    val ok: Boolean = false,
    val error: String? = null,
    @SerialName("cadastroId")
    val cadastroId: String? = null,
    val warning: String? = null,
    val message: String? = null,
    val details: JsonElement? = null,
)

@Serializable
data class PublicCadastroPayload(
    val cpf: String,
    val nome: String,
    @SerialName("dataNascimento")
    val dataNascimento: String,
    @SerialName("sexoCodigo")
    val sexoCodigo: Int,
    val contatos: List<PublicCadastroContato>,
    val endereco: PublicCadastroEndereco,
    @SerialName("nomeMae")
    val nomeMae: String,
    @SerialName("numeroMatricula")
    val numeroMatricula: String? = null,
    val dependentes: List<PublicCadastroDependente>,
)

@Serializable
data class PublicCadastroContato(
    val tipo: String,
    val valor: String,
    val principal: Boolean = false,
)

@Serializable
data class PublicCadastroEndereco(
    val cep: String,
    @SerialName("tipoLogradouro")
    val tipoLogradouro: String? = null,
    val logradouro: String,
    val numero: String,
    val complemento: String? = null,
    val bairro: String,
    val cidade: String,
    val uf: String,
    @SerialName("idTipoLogradouro")
    val idTipoLogradouro: Int? = null,
    @SerialName("idBairro")
    val idBairro: Int? = null,
    @SerialName("idMunicipio")
    val idMunicipio: Int? = null,
    @SerialName("idUf")
    val idUf: Int? = null,
    @SerialName("ufSigla")
    val ufSigla: String? = null,
)

@Serializable
data class PublicCadastroDependente(
    val tipo: Int,
    val nome: String,
    @SerialName("dataNascimento")
    val dataNascimento: String,
    val cpf: String,
    val sexo: Int,
    @SerialName("sexoDescricao")
    val sexoDescricao: String,
    val plano: Int,
    @SerialName("planoValor")
    val planoValor: String = "0,00",
    @SerialName("nomeMae")
    val nomeMae: String,
    @SerialName("carenciaAtendimento")
    val carenciaAtendimento: Int = 0,
    @SerialName("funcionarioCadastro")
    val funcionarioCadastro: Int = 0,
)
