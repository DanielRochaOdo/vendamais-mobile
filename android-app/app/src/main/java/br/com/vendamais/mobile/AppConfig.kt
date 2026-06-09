package br.com.vendamais.mobile

import java.net.URI

object AppConfig {
    private const val defaultPublicAppUrl = "https://vendamais.odontoart.com"
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trim()
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()
    val publicAppUrl: String = normalizePublicAppUrl(BuildConfig.PUBLIC_APP_URL)
    val updateMetadataUrl: String = BuildConfig.UPDATE_METADATA_URL.trim()
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
}
