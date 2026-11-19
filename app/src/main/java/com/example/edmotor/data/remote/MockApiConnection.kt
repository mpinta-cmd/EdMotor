package com.example.edmotor.data.remote

import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MockApiConnection private constructor(
    val service: MockApiService,
    val projectKey: String
) {
    private val profilePrefix = "mockapi:$projectKey:"

    fun profileKey(profileId: String): String {
        require(profileIdPattern.matches(profileId)) { "El ID del perfil MockAPI no es válido." }
        return "$profilePrefix$profileId"
    }

    fun profileId(localKey: String?): String? {
        if (localKey == null || !localKey.startsWith(profilePrefix)) return null
        return localKey.removePrefix(profilePrefix).takeIf(profileIdPattern::matches)
    }

    companion object {
        private val profileIdPattern = Regex("[A-Za-z0-9_-]{1,64}")

        fun fromBaseUrl(configuredUrl: String): MockApiConnection? {
            val uri = runCatching { URI(configuredUrl.trim()) }.getOrNull() ?: return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            if (!host.endsWith(".mockapi.io") || !uri.scheme.equals("https", ignoreCase = true) ||
                uri.userInfo != null || uri.query != null || uri.fragment != null || uri.port !in listOf(-1, 443)
            ) return null

            val endpoint = "https://$host${uri.normalize().rawPath.trimEnd('/')}/"
            val service = runCatching {
                Retrofit.Builder()
                    .baseUrl(endpoint)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(MockApiService::class.java)
            }.getOrNull() ?: return null
            val key = MessageDigest.getInstance("SHA-256").digest(endpoint.toByteArray(Charsets.UTF_8))
                .take(12).joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
            return MockApiConnection(service, key)
        }
    }
}
