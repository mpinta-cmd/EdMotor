package com.example.edmotor.data.remote

import com.example.edmotor.model.UserRole
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

internal class PrivateCredentials(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
) {
    fun expiresSoon(now: Long = System.currentTimeMillis()): Boolean = expiresAtMillis <= now + 60_000L
}

internal data class VerifiedPrivateProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val role: UserRole
)

internal class PrivateAuthHttpException(val status: Int) : Exception()
internal class PrivateSignupConfigurationException : Exception()

class SupabaseConnection private constructor(
    val baseUrl: String,
    val publishableKey: String,
    val projectKey: String,
    private val auth: PrivateAuthTransport
) {
    private val profilePrefix = "supabase:$projectKey:"

    fun profileKey(profileId: String): String {
        require(isCanonicalPrivateUuid(profileId))
        return "$profilePrefix$profileId"
    }

    fun profileId(profileKey: String?): String? {
        if (profileKey == null || !profileKey.startsWith(profilePrefix)) return null
        return profileKey.removePrefix(profilePrefix).takeIf(::isCanonicalPrivateUuid)
    }

    internal suspend fun signIn(username: String, password: String): PrivateCredentials =
        auth.signIn(CredentialRequest(username, password)).requirePayload().toCredentials()

    internal suspend fun registerClient(username: String, displayName: String, email: String, password: String) {
        val settings = auth.settings().requirePayload()
        if (settings.mailerAutoconfirm != false) throw PrivateSignupConfigurationException()

        val response = auth.signup(SignupRequest(email, password, SignupMetadata(username, displayName))).requirePayload()
        if (response.has("access_token") || response.has("refresh_token") ||
            response.get("session")?.isJsonObject == true
        ) throw PrivateSignupConfigurationException()
    }

    internal suspend fun verifyEmail(email: String, code: String) {
        auth.verify(EmailVerificationRequest(email, code)).requireSuccess()
    }

    internal suspend fun refresh(credentials: PrivateCredentials): PrivateCredentials {
        val updated = auth.refresh("refresh_token", RefreshRequest(credentials.refreshToken))
            .requirePayload().toCredentials()
        if (updated.userId != credentials.userId) throw PrivateAuthHttpException(401)
        return updated
    }

    internal suspend fun fetchProfile(credentials: PrivateCredentials): VerifiedPrivateProfile {
        val response = auth.getProfile(
            bearer = "Bearer ${credentials.accessToken}",
            filter = "eq.${credentials.userId}",
            columns = "id,username,display_name,role"
        ).requirePayload()
        val found = response.singleOrNull() ?: throw PrivateAuthHttpException(403)
        val id = found.id ?: throw PrivateAuthHttpException(403)
        val username = found.username?.takeIf { it.isNotBlank() } ?: throw PrivateAuthHttpException(403)
        val name = found.displayName?.trim()?.takeIf { it.length in 2..50 && it.none(Char::isISOControl) }
            ?: throw PrivateAuthHttpException(403)
        val role = UserRole.entries.firstOrNull { it.name == found.role } ?: throw PrivateAuthHttpException(403)
        if (id != credentials.userId) throw PrivateAuthHttpException(403)
        return VerifiedPrivateProfile(id, username, name, role)
    }

    internal suspend fun revoke(credentials: PrivateCredentials) {
        val response = auth.revoke("Bearer ${credentials.accessToken}", "local")
        response.requireSuccess()
        response.body()?.close()
    }

    companion object {
        private val hostPattern = Regex("([a-z0-9][a-z0-9-]{0,62})\\.supabase\\.co")
        private val keyPattern = Regex("sb_publishable_[A-Za-z0-9_-]{8,256}")

        fun fromConfig(configuredUrl: String, configuredPublishableKey: String): SupabaseConnection? {
            val uri = runCatching { URI(configuredUrl.trim()) }.getOrNull() ?: return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            val projectKey = hostPattern.matchEntire(host)?.groupValues?.get(1) ?: return null
            val key = configuredPublishableKey.trim()
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null || uri.query != null ||
                uri.fragment != null || (uri.rawPath != "" && uri.rawPath != "/") ||
                (uri.port != -1 && uri.port != 443) || !keyPattern.matches(key)
            ) return null

            val baseUrl = "https://$host/"
            val transport = createSupabaseRetrofit(baseUrl, key).create(PrivateAuthTransport::class.java)
            return SupabaseConnection(baseUrl, key, projectKey, transport)
        }
    }
}

internal fun isCanonicalPrivateUuid(value: String): Boolean =
    runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

internal fun createSupabaseRetrofit(baseUrl: String, publishableKey: String): Retrofit {
    val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("apikey", publishableKey).build())
        }
        .build()
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}

private interface PrivateAuthTransport {
    @POST("functions/v1/edmotor-sign-in")
    suspend fun signIn(@Body body: CredentialRequest): Response<AuthResponse>

    @POST("auth/v1/signup")
    suspend fun signup(@Body body: SignupRequest): Response<JsonObject>

    @GET("auth/v1/settings")
    suspend fun settings(): Response<PublicAuthSettings>

    @POST("auth/v1/verify")
    suspend fun verify(@Body body: EmailVerificationRequest): Response<JsonObject>

    @POST("auth/v1/token")
    suspend fun refresh(@Query("grant_type") type: String, @Body body: RefreshRequest): Response<AuthResponse>

    @GET("rest/v1/edmotor_profiles")
    suspend fun getProfile(
        @Header("Authorization") bearer: String,
        @Query("id") filter: String,
        @Query("select") columns: String
    ): Response<List<PrivateProfileDto>>

    @POST("auth/v1/logout")
    suspend fun revoke(@Header("Authorization") bearer: String, @Query("scope") scope: String): Response<ResponseBody>
}

private class CredentialRequest(val username: String, val password: String)
private class SignupRequest(val email: String, val password: String, val data: SignupMetadata)
private class SignupMetadata(
    val username: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("edmotor_private_signup") val privateSignup: Boolean = true
)
private class EmailVerificationRequest(val email: String, val token: String, val type: String = "email")
private class RefreshRequest(@SerializedName("refresh_token") val refreshToken: String)
private class PublicAuthSettings(@SerializedName("mailer_autoconfirm") val mailerAutoconfirm: Boolean?)
private class AuthUser(val id: String?)
private class AuthResponse(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("expires_at") val expiresAt: Long?,
    @SerializedName("expires_in") val expiresIn: Long?,
    val user: AuthUser?
)
private class PrivateProfileDto(
    val id: String?,
    val username: String?,
    @SerializedName("display_name") val displayName: String?,
    val role: String?
)

private fun Response<*>.requireSuccess() {
    if (isSuccessful) return
    errorBody()?.close()
    throw PrivateAuthHttpException(code())
}

private fun <T : Any> Response<T>.requirePayload(): T {
    requireSuccess()
    return body() ?: throw PrivateAuthHttpException(502)
}

private fun AuthResponse.toCredentials(): PrivateCredentials {
    val id = user?.id?.takeIf(::isCanonicalPrivateUuid) ?: throw PrivateAuthHttpException(502)
    val access = accessToken?.takeIf { it.isNotBlank() && it.length <= 16_384 && it.none(Char::isWhitespace) }
        ?: throw PrivateAuthHttpException(502)
    val refresh = refreshToken?.takeIf { it.isNotBlank() && it.length <= 2048 && it.none(Char::isWhitespace) }
        ?: throw PrivateAuthHttpException(502)
    val expiryMillis = runCatching {
        expiresAt?.takeIf { it > 0 }?.let { Math.multiplyExact(it, 1000L) }
            ?: expiresIn?.takeIf { it > 0 }?.let {
                Math.addExact(System.currentTimeMillis(), Math.multiplyExact(it, 1000L))
            }
    }.getOrNull() ?: throw PrivateAuthHttpException(502)
    if (expiryMillis <= System.currentTimeMillis()) throw PrivateAuthHttpException(502)
    return PrivateCredentials(id, access, refresh, expiryMillis)
}
