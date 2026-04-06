package br.com.vendamais.mobile.data.auth

import br.com.vendamais.mobile.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SupabaseAuthService(
    private val client: HttpClient,
    private val json: Json,
) {
    suspend fun login(email: String, password: String): SavedSession {
        check(AppConfig.isConfigured()) {
            "Supabase nao configurado. Atualize android-app/local.properties."
        }

        val response: TokenResponse = try {
            client.post("${AppConfig.supabaseUrl}/auth/v1/token") {
                parameter("grant_type", "password")
                header("apikey", AppConfig.supabaseAnonKey)
                header(HttpHeaders.Authorization, "Bearer ${AppConfig.supabaseAnonKey}")
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email = email, password = password))
            }.body<TokenResponse>()
        } catch (exception: ClientRequestException) {
            throw exception.toSupabaseException()
        }

        return response.toSavedSession(email)
    }

    suspend fun refreshIfNeeded(session: SavedSession): SavedSession {
        val expiresAt = session.expiresAt ?: return session
        val now = System.currentTimeMillis() / 1000
        return if (expiresAt <= now + 60) refreshSession(session) else session
    }

    private suspend fun refreshSession(session: SavedSession): SavedSession {
        val response: TokenResponse = try {
            client.post("${AppConfig.supabaseUrl}/auth/v1/token") {
                parameter("grant_type", "refresh_token")
                header("apikey", AppConfig.supabaseAnonKey)
                header(HttpHeaders.Authorization, "Bearer ${AppConfig.supabaseAnonKey}")
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("refresh_token", session.refreshToken)
                        },
                    ),
                )
            }.body<TokenResponse>()
        } catch (exception: ClientRequestException) {
            throw exception.toSupabaseException()
        }

        return response.toSavedSession(session.email)
    }

    private suspend fun ClientRequestException.toSupabaseException(): IllegalStateException {
        val errorBody = response.body<String>()
        val parsed = runCatching {
            json.decodeFromString(SupabaseError.serializer(), errorBody)
        }.getOrNull()

        return IllegalStateException(parsed?.message ?: parsed?.error ?: "Falha ao autenticar no Supabase")
    }

    private fun TokenResponse.toSavedSession(fallbackEmail: String): SavedSession {
        return SavedSession(
            userId = user.id,
            accessToken = accessToken,
            refreshToken = refreshToken,
            email = user.email ?: fallbackEmail,
            expiresAt = expiresAt,
        )
    }
}

@Serializable
private data class SupabaseError(
    val message: String? = null,
    val error: String? = null,
)
