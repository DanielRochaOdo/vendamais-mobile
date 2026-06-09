package br.com.vendamais.mobile.data.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.head
import kotlinx.serialization.json.Json
import java.net.URI
import java.io.File

class AppUpdateRepository(
    private val client: HttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    suspend fun fetchUpdateInfo(metadataUrl: String): AppUpdateInfo? {
        if (metadataUrl.isBlank()) return null
        return runCatching {
            val raw = client.get(metadataUrl).body<String>()
            val payload = json.decodeFromString(AppUpdateInfo.serializer(), raw)
            payload.copy(
                apkUrl = normalizeDownloadUrl(payload.apkUrl.ifBlank { br.com.vendamais.mobile.AppConfig.updateApkUrl }),
            )
        }.getOrNull()
    }

    suspend fun verifyApkUrl(apkUrl: String): Boolean {
        return runCatching {
            client.head(apkUrl)
            true
        }.getOrDefault(false)
    }

    suspend fun downloadApk(apkUrl: String, target: File): File? {
        return runCatching {
            val resolvedUrl = normalizeDownloadUrl(apkUrl.ifBlank { br.com.vendamais.mobile.AppConfig.updateApkUrl })
            if (resolvedUrl.isBlank()) return null
            val bytes = client.get(resolvedUrl).body<ByteArray>()
            target.outputStream().use { it.write(bytes) }
            target
        }.getOrNull()
    }

    private fun normalizeDownloadUrl(rawUrl: String): String {
        val url = rawUrl.trim()
        if (!url.contains("drive.google.com", ignoreCase = true)) return url

        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val path = uri.path.orEmpty()
        val query = uri.query.orEmpty()

        val fileIdFromPath = Regex("""/file/d/([^/]+)""")
            .find(path)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }

        val fileIdFromQuery = Regex("""(?:^|&)id=([^&]+)""")
            .find(query)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }

        val fileId = fileIdFromPath ?: fileIdFromQuery ?: return url
        return "https://drive.google.com/uc?export=download&id=$fileId"
    }
}
