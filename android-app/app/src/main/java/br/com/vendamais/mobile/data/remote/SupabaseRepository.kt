package br.com.vendamais.mobile.data.remote

import br.com.vendamais.mobile.AppConfig
import br.com.vendamais.mobile.data.auth.SavedSession
import br.com.vendamais.mobile.data.models.AdminTeam
import br.com.vendamais.mobile.data.models.AdminUser
import br.com.vendamais.mobile.data.models.AuditLemmitResponse
import br.com.vendamais.mobile.data.models.CadastroExcluidoItem
import br.com.vendamais.mobile.data.models.CadastroConfig
import br.com.vendamais.mobile.data.models.CadastroDetalhe
import br.com.vendamais.mobile.data.models.CadastroResumo
import br.com.vendamais.mobile.data.models.CadastroStats
import br.com.vendamais.mobile.data.models.ErpUploadQueueItem
import br.com.vendamais.mobile.data.models.MobileProfile
import br.com.vendamais.mobile.data.models.MobileTeam
import br.com.vendamais.mobile.data.models.ProcessUploadQueueResponse
import br.com.vendamais.mobile.data.models.ResetStuckQueueResult
import br.com.vendamais.mobile.data.models.SystemOverview
import br.com.vendamais.mobile.data.models.VendedorStats
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseRepository(
    private val client: HttpClient,
    private val json: Json,
) {
    suspend fun fetchProfile(session: SavedSession): MobileProfile {
        return getList<MobileProfile>(
            path = "profiles",
            session = session,
            query = {
                parameter("id", "eq.${session.userId}")
                parameter("select", "id,name,email,telefone,role,external_id,team_id,is_active,created_at,lemmit_limite_consultas")
            },
        ).firstOrNull() ?: throw IllegalStateException("Perfil não encontrado para o usuário autenticado.")
    }

    suspend fun fetchTeam(session: SavedSession, teamId: String): MobileTeam? {
        return getList<MobileTeam>(
            path = "teams",
            session = session,
            query = {
                parameter("id", "eq.$teamId")
                parameter("select", "id,name,is_active")
            },
        ).firstOrNull()
    }

    suspend fun fetchSystemOverview(session: SavedSession): SystemOverview {
        val totalUsers = fetchCount(
            path = "profiles",
            session = session,
            filters = mapOf("select" to "id"),
        )
        val activeUsers = fetchCount(
            path = "profiles",
            session = session,
            filters = mapOf(
                "select" to "id",
                "is_active" to "eq.true",
            ),
        )
        val totalTeams = fetchCount(
            path = "teams",
            session = session,
            filters = mapOf("select" to "id"),
        )

        return SystemOverview(
            totalUsers = totalUsers,
            totalTeams = totalTeams,
            activeUsers = activeUsers,
        )
    }

    suspend fun fetchCadastroStats(session: SavedSession): CadastroStats {
        return client.post("${AppConfig.supabaseUrl}/rest/v1/rpc/get_cadastros_stats") {
            applyAuthHeaders(session)
            contentType(ContentType.Application.Json)
            setBody(mapOf("p_user_id" to session.userId))
        }.body()
    }

    suspend fun fetchCadastroStatsFromCache(session: SavedSession): CadastroStats {
        return client.post("${AppConfig.supabaseUrl}/rest/v1/rpc/get_stats_from_cache") {
            applyAuthHeaders(session)
            contentType(ContentType.Application.Json)
            setBody(mapOf("p_user_id" to session.userId))
        }.body()
    }

    suspend fun fetchCadastros(session: SavedSession): List<CadastroResumo> {
        val allCadastros = mutableListOf<CadastroResumo>()
        var offset = 0
        val pageSize = 1000

        while (true) {
            val chunk = getList<CadastroResumo>(
                path = "cadastros",
                session = session,
                query = {
                    parameter(
                        "select",
                        "id,status,tipo_cadastro,nome,cpf,empresa_nome,vendedor_nome,adesionista_nome,created_at,updated_at"
                    )
                    parameter("order", "updated_at.desc")
                    parameter("limit", pageSize)
                    parameter("offset", offset)
                },
            )

            allCadastros += chunk
            if (chunk.size < pageSize) break
            offset += pageSize
        }

        return allCadastros
    }

    suspend fun fetchCadastroDetalhe(session: SavedSession, id: String): CadastroDetalhe {
        return getList<CadastroDetalhe>(
            path = "cadastros",
            session = session,
            query = {
                parameter("id", "eq.$id")
                parameter(
                    "select",
                    "id,status,tipo_cadastro,nome,cpf,data_nascimento,nome_mae,empresa_nome,empresa_cnpj,numero_matricula,vendedor_nome,adesionista_nome,motivo_bloqueio,dependentes,erp_response,created_at,updated_at"
                )
                parameter("limit", 1)
            },
        ).firstOrNull() ?: throw IllegalStateException("Cadastro não encontrado.")
    }

    suspend fun fetchStatsByVendedor(
        session: SavedSession,
        tipoCadastro: String,
    ): List<VendedorStats> {
        try {
            return client.post("${AppConfig.supabaseUrl}/rest/v1/rpc/get_stats_by_vendedor") {
                applyAuthHeaders(session)
                contentType(ContentType.Application.Json)
                setBody(
                    mapOf(
                        "p_user_id" to session.userId,
                        "p_tipo_cadastro" to tipoCadastro,
                    ),
                )
            }.body()
        } catch (exception: ClientRequestException) {
            throw exception.toSupabaseException(json)
        }
    }

    suspend fun fetchUsers(session: SavedSession): List<AdminUser> {
        return getList(
            path = "profiles",
            session = session,
            query = {
                parameter("select", "id,name,email,telefone,role,external_id,team_id,is_active,lemmit_limite_consultas,created_at")
                parameter("order", "created_at.desc")
            },
        )
    }

    suspend fun fetchTeamsAdmin(session: SavedSession): List<AdminTeam> {
        return getList(
            path = "teams",
            session = session,
            query = {
                parameter("select", "id,name,is_active,created_at")
                parameter("order", "name.asc")
            },
        )
    }

    suspend fun fetchAuditLemmit(
        session: SavedSession,
        startIso: String,
        endIso: String,
        limit: Int = 100,
        offset: Int = 0,
    ): AuditLemmitResponse {
        return client.post("${AppConfig.supabaseUrl}/rest/v1/rpc/audit_lemmit") {
            applyAuthHeaders(session)
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "p_start" to startIso,
                    "p_end" to endIso,
                    "p_limit" to limit,
                    "p_offset" to offset,
                ),
            )
        }.body()
    }

    suspend fun fetchErpUploadQueue(
        session: SavedSession,
        statuses: List<String> = emptyList(),
        limit: Int = 100,
    ): List<ErpUploadQueueItem> {
        return getList(
            path = "erp_upload_queue",
            session = session,
            query = {
                parameter(
                    "select",
                    "id,created_at,updated_at,status,attempts,next_attempt_at,last_attempt_at,last_error,last_status_code,erp_response,cadastro_id,created_by,id_funcionario,id_dependente,arquivo_path,arquivo_nome,bucket,tipo",
                )
                if (statuses.isNotEmpty()) {
                    val payload = statuses.joinToString(",") { it.trim() }
                    parameter("status", "in.($payload)")
                }
                parameter("order", "created_at.desc")
                parameter("limit", limit)
            },
        )
    }

    suspend fun processUploadQueue(session: SavedSession): ProcessUploadQueueResponse {
        return client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/erp-process-upload-queue",
            json = json,
            body = buildJsonObject { },
        ) {
            applyAuthHeaders(session)
        }
    }

    suspend fun resetStuckQueue(session: SavedSession, minutes: Int = 15): ResetStuckQueueResult {
        val response: List<ResetStuckQueueResult> = client.safePost(
            url = "${AppConfig.supabaseUrl}/rest/v1/rpc/reset_stuck_queue_items",
            json = json,
            body = buildJsonObject {
                put("stuck_threshold_minutes", minutes)
            },
        ) {
            applyAuthHeaders(session)
        }
        return response.firstOrNull() ?: ResetStuckQueueResult()
    }

    suspend fun fetchCadastrosExcluidos(session: SavedSession, limit: Int = 100): List<CadastroExcluidoItem> {
        return getList(
            path = "cadastros_excluidos",
            session = session,
            query = {
                parameter(
                    "select",
                    "id,cadastro_id,dados_cadastro,motivo_exclusao,excluido_por,excluido_por_nome,excluido_por_role,excluido_em,team_id",
                )
                parameter("order", "excluido_em.desc")
                parameter("limit", limit)
            },
        )
    }

    suspend fun createUser(session: SavedSession, payload: JsonObject) {
        val response: JsonObject = client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/create-user",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
        }

        val success = response["success"]
            ?.let { it as? JsonPrimitive }
            ?.content
            ?.toBooleanStrictOrNull()
            ?: false

        if (!success) {
            val message = response["error"]
                ?.let { it as? JsonPrimitive }
                ?.content
                ?.takeIf { it.isNotBlank() }
                ?: "Falha ao criar usuÃ¡rio."
            throw IllegalStateException(message)
        }
    }

    suspend fun updateUser(session: SavedSession, id: String, payload: JsonObject): AdminUser {
        return client.safePost<List<AdminUser>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/profiles?id=eq.$id",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
            method = HttpMethod.Patch
            contentType(ContentType.Application.Json)
        }.firstOrNull() ?: throw IllegalStateException("Falha ao atualizar usuÃ¡rio.")
    }

    suspend fun createTeam(session: SavedSession, name: String): AdminTeam {
        val payload = buildJsonObject { put("name", name) }
        return client.safePost<List<AdminTeam>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/teams",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
        }.firstOrNull() ?: throw IllegalStateException("Falha ao criar equipe.")
    }

    suspend fun updateTeam(session: SavedSession, id: String, payload: JsonObject): AdminTeam {
        return client.safePost<List<AdminTeam>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/teams?id=eq.$id",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
            method = HttpMethod.Patch
            contentType(ContentType.Application.Json)
        }.firstOrNull() ?: throw IllegalStateException("Falha ao atualizar equipe.")
    }

    suspend fun updateCadastroConfig(session: SavedSession, payload: JsonObject): CadastroConfig {
        return client.safePost<List<CadastroConfig>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/cadastro_config?id=eq.1",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
            method = HttpMethod.Patch
            contentType(ContentType.Application.Json)
        }.firstOrNull() ?: throw IllegalStateException("Falha ao atualizar configuraÃ§Ãµes.")
    }

    private suspend inline fun <reified T> getList(
        path: String,
        session: SavedSession,
        noinline query: HttpRequestBuilder.() -> Unit,
    ): List<T> {
        return client.safeGet(
            url = "${AppConfig.supabaseUrl}/rest/v1/$path",
            json = json,
        ) {
            applyAuthHeaders(session)
            query()
        }
    }

    private suspend fun fetchCount(
        path: String,
        session: SavedSession,
        filters: Map<String, String>,
    ): Int {
        try {
            val response = client.get("${AppConfig.supabaseUrl}/rest/v1/$path") {
                applyAuthHeaders(session)
                header("Prefer", "count=exact")
                method = HttpMethod.Head
                filters.forEach { (key, value) ->
                    parameter(key, value)
                }
            }

            return response.headers["Content-Range"]
                ?.substringAfter("/")
                ?.toIntOrNull()
                ?: 0
        } catch (_: Exception) {
            return 0
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuthHeaders(session: SavedSession) {
        header("apikey", AppConfig.supabaseAnonKey)
        header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
        header(HttpHeaders.Accept, "application/json")
    }
}

private suspend fun ClientRequestException.toSupabaseException(json: Json): IllegalStateException {
    val body = response.body<String>()
    val parsed = runCatching {
        json.decodeFromString(SupabaseRepositoryError.serializer(), body)
    }.getOrNull()

    return IllegalStateException(
        parsed?.message ?: parsed?.error ?: "Falha ao consultar o backend.",
    )
}

@kotlinx.serialization.Serializable
private data class SupabaseRepositoryError(
    val message: String? = null,
    val error: String? = null,
)
