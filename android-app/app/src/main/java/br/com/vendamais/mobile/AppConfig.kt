package br.com.vendamais.mobile

import java.net.URI

object AppConfig {
    private const val defaultPublicAppUrl = "https://vendamais.odontoart.com"
    private const val defaultUpdateMetadataUrl = "https://odontoart.com/vendaMais/updates/android-update.json"

    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trim()
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()
    val publicAppUrl: String = normalizePublicAppUrl(BuildConfig.PUBLIC_APP_URL)
    val updateMetadataUrl: String = normalizeUpdateMetadataUrl(BuildConfig.UPDATE_METADATA_URL)
    val updateApkUrl: String = BuildConfig.UPDATE_APK_URL.trim()

    fun isConfigured(): Boolean = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()

    private fun normalizePublicAppUrl(raw: String): String {
        val candidate = raw.trim().removeSuffix("/")
        if (candidate.isBlank()) return defaultPublicAppUrl
        val host = runCatching { URI(candidate).host?.trim()?.lowercase() }.getOrNull()
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") {
            return defaultPublicAppUrl
        }
        return candidate
    }

    private fun normalizeUpdateMetadataUrl(raw: String): String {
        val candidate = raw.trim()
        if (candidate.isBlank()) return defaultUpdateMetadataUrl

        val uri = runCatching { URI(candidate) }.getOrNull() ?: return defaultUpdateMetadataUrl
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.trim()?.lowercase()
        if (scheme != "https" || host.isNullOrBlank() || host in setOf("localhost", "127.0.0.1", "::1")) {
            return defaultUpdateMetadataUrl
        }
        return candidate
    }
}
