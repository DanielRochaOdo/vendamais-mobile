package br.com.vendamais.mobile

object AppConfig {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trim()
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()
    val publicAppUrl: String = BuildConfig.PUBLIC_APP_URL.trim()

    fun isConfigured(): Boolean = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}
