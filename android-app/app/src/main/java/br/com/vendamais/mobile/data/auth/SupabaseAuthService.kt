package br.com.vendamais.mobile.data.auth

import android.util.Log
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
    private val logTag = "CadastroTrace"

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
            throw exception.toSupabaseException(isRefreshFlow = false)
        } catch (exception: Throwable) {
            Log.w(
                logTag,
                "operationId=- stage=auth_login_unexpected_failure type=${exception::class.simpleName} message=${exception.message}",
                exception,
            )
            throw IllegalStateException("Falha ao interpretar resposta de autenticacao do Supabase.")
        }

        return response.toSavedSession(
            fallbackEmail = email,
            fallbackUserId = null,
            isRefreshFlow = false,
        )
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
            val readable = exception.toSupabaseException(isRefreshFlow = true)
            Log.w(
                logTag,
                "operationId=- stage=auth_refresh_http_failure status=${exception.response.status.value} message=${readable.message}",
                exception,
            )
            throw readable
        } catch (exception: Throwable) {
            Log.w(
                logTag,
                "operationId=- stage=auth_refresh_unexpected_failure type=${exception::class.simpleName} message=${exception.message}",
                exception,
            )
            throw IllegalStateException("Falha temporaria ao atualizar sessao. Tente novamente.")
        }

        return response.toSavedSession(
            fallbackEmail = session.email,
            fallbackUserId = session.userId,
            isRefreshFlow = true,
        )
    }

    private suspend fun ClientRequestException.toSupabaseException(isRefreshFlow: Boolean): IllegalStateException {
        val statusCode = response.status.value
        val errorBody = response.body<String>()
        val parsed = runCatching {
            json.decodeFromString(SupabaseError.serializer(), errorBody)
        }.getOrNull()

        val message = parsed?.message ?: parsed?.error ?: "Falha ao autenticar no Supabase"
        val normalized = message.lowercase()
        val shouldInvalidateSession =
            statusCode == 401 ||
                statusCode == 403 ||
                (isRefreshFlow && normalized.contains("invalid_grant")) ||
                (isRefreshFlow && normalized.contains("refresh token")) ||
                (isRefreshFlow && normalized.contains("revoked"))

        return if (shouldInvalidateSession) {
            IllegalStateException(message)
        } else {
            IllegalStateException("Falha temporaria ao atualizar sessao: $message")
        }
    }

    private fun TokenResponse.toSavedSession(
        fallbackEmail: String,
        fallbackUserId: String?,
        isRefreshFlow: Boolean,
    ): SavedSession {
        val source = session
        val resolvedAccessToken = accessToken ?: source?.accessToken
        val resolvedRefreshToken = refreshToken ?: source?.refreshToken
        val resolvedUser = user ?: source?.user
        val resolvedUserId = resolvedUser?.id?.trim().takeUnless { it.isNullOrBlank() } ?: fallbackUserId
        val resolvedEmail = resolvedUser?.email?.takeIf { it.isNotBlank() } ?: fallbackEmail
        val resolvedExpiresAt = resolveExpiresAt(
            expiresAt = expiresAt ?: source?.expiresAt,
            expiresIn = expiresIn ?: source?.expiresIn,
        )

        if (resolvedAccessToken.isNullOrBlank() || resolvedRefreshToken.isNullOrBlank() || resolvedUserId.isNullOrBlank()) {
            val backendMessage = message?.takeIf { it.isNotBlank() } ?: error?.takeIf { it.isNotBlank() }
            val fallbackMessage = if (isRefreshFlow) {
                "Refresh token invalido ou sessao expirada. Faca login novamente."
            } else {
                "Falha ao autenticar no Supabase."
            }
            val finalMessage = backendMessage ?: fallbackMessage
            Log.w(
                logTag,
                "operationId=- stage=auth_token_payload_invalid refreshFlow=$isRefreshFlow message=$finalMessage",
            )
            throw IllegalStateException(finalMessage)
        }

        return SavedSession(
            userId = resolvedUserId,
            accessToken = resolvedAccessToken,
            refreshToken = resolvedRefreshToken,
            email = resolvedEmail,
            expiresAt = resolvedExpiresAt,
        )
    }

    private fun resolveExpiresAt(expiresAt: Long?, expiresIn: Long?): Long? {
        if (expiresAt != null) return expiresAt
        val ttlSeconds = expiresIn ?: return null
        val nowSeconds = System.currentTimeMillis() / 1000
        return nowSeconds + ttlSeconds
    }
}

@Serializable
private data class SupabaseError(
    val message: String? = null,
    val error: String? = null,
)
