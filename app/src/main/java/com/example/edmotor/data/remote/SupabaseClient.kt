package com.example.edmotor.data.remote

import com.example.edmotor.session.ActiveRemoteSession
import com.example.edmotor.session.RemoteSession
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class SupabaseProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val phone: String?,
    val role: String
)

class SupabaseException(
    message: String,
    val statusCode: Int? = null,
    val errorCode: String? = null
) : Exception(message)

/**
 * Cliente REST/Auth/Realtime de Supabase.
 *
 * Se utiliza la publishable key pública y el JWT del usuario autenticado.
 * No se utiliza ni se incluye una service_role key.
 */
class SupabaseClient(
    private val sessionManager: SessionManager
) {
    private val baseUrl = SupabaseConfig.PROJECT_URL.trimEnd('/')
    private val apiKey = SupabaseConfig.PUBLISHABLE_KEY
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val _realtimeChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 32)
    val realtimeChanges = _realtimeChanges.asSharedFlow()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var realtimeToken: String? = null
    private var reconnectJob: Job? = null

    fun authEmail(username: String): String =
        "${username.trim().lowercase()}@${SupabaseConfig.AUTH_EMAIL_DOMAIN}"

    suspend fun signUpClient(
        username: String,
        password: String,
        displayName: String,
        phone: String?
    ): RemoteSession {
        val metadata = JSONObject()
            .put("username", username)
            .put("display_name", displayName)
            .put("phone", phone ?: JSONObject.NULL)

        val body = JSONObject()
            .put("email", authEmail(username))
            .put("password", password)
            .put("data", metadata)

        return parseAuthSession(authRequest("/auth/v1/signup", body))
    }

    suspend fun signIn(username: String, password: String): RemoteSession {
        val body = JSONObject()
            .put("email", authEmail(username))
            .put("password", password)

        val response = authRequest(
            "/auth/v1/token?grant_type=password",
            body
        )
        return parseAuthSession(response)
    }

    suspend fun prepareRestoredSession(
        remote: RemoteSession,
        forceRefresh: Boolean = false
    ): RemoteSession {
        val expiresSoon =
            remote.expiresAtMillis <= System.currentTimeMillis() + 60_000L
        if (!expiresSoon && !forceRefresh) return remote
        if (remote.refreshToken.isBlank()) {
            throw SupabaseException("La sesión remota expiró.", statusCode = 401)
        }
        return refreshSession(remote)
    }

    suspend fun fetchMyProfile(session: RemoteSession): SupabaseProfile {
        val encodedId = encode(session.userId)
        val result = execute(
            path = "/rest/v1/profiles?select=id,username,display_name,phone,role&id=eq.$encodedId",
            method = "GET",
            body = null,
            bearer = session.accessToken,
            prefer = null
        )
        if (result.code !in 200..299) {
            throw responseError(result)
        }

        val rows = JSONArray(result.body)
        if (rows.length() == 0) {
            throw SupabaseException(
                "No encontramos tu perfil en Supabase. Ejecuta el SQL de configuración de EdMotor.",
                statusCode = 404
            )
        }

        val row = rows.getJSONObject(0)
        return SupabaseProfile(
            id = row.getString("id"),
            username = row.getString("username"),
            displayName = row.optString("display_name", row.getString("username")),
            phone = row.optString("phone").takeIf { it.isNotBlank() && it != "null" },
            role = row.getString("role")
        )
    }

    suspend fun get(path: String, expectedUserId: String? = null): String =
        authenticatedRequest(path, "GET", null, expectedUserId = expectedUserId)

    suspend fun post(
        path: String,
        body: JSONObject,
        prefer: String? = null,
        expectedUserId: String? = null
    ): String = authenticatedRequest(path, "POST", body, prefer, expectedUserId)

    suspend fun patch(path: String, body: JSONObject, prefer: String? = null): String =
        authenticatedRequest(path, "PATCH", body, prefer)

    suspend fun rpc(
        functionName: String,
        body: JSONObject,
        expectedUserId: String? = null
    ): String =
        authenticatedRequest(
            "/rest/v1/rpc/${encode(functionName)}",
            "POST",
            body,
            expectedUserId = expectedUserId
        )

    @Synchronized
    fun stopRealtime() {
        realtimeToken = null
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, "Sesión cerrada")
        socket = null
    }

    suspend fun startRealtimeFor(remote: RemoteSession) {
        sessionManager.withCurrentCredentials(remote) { startRealtime(remote.accessToken) }
    }

    @Synchronized
    private fun startRealtime(accessToken: String) {
        if (accessToken.isBlank()) return
        if (realtimeToken == accessToken && socket != null) return

        stopRealtime()
        realtimeToken = accessToken
        openRealtimeSocket(accessToken)
    }

    private fun openRealtimeSocket(accessToken: String) {
        val wsBase = baseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")

        val wsUrl = "$wsBase/realtime/v1/websocket?apikey=${encode(apiKey)}&vsn=1.0.0"
        val request = Request.Builder().url(wsUrl).build()

        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val changes = JSONArray()
                    .put(
                        JSONObject()
                            .put("event", "*")
                            .put("schema", "public")
                            .put("table", "orders")
                    )
                    .put(
                        JSONObject()
                            .put("event", "*")
                            .put("schema", "public")
                            .put("table", "appointments")
                    )

                val config = JSONObject()
                    .put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("key", ""))
                    .put("postgres_changes", changes)

                val payload = JSONObject()
                    .put("config", config)
                    .put("access_token", accessToken)

                val join = JSONObject()
                    .put("topic", "realtime:public:edmotor")
                    .put("event", "phx_join")
                    .put("payload", payload)
                    .put("ref", "1")

                webSocket.send(join.toString())

                reconnectJob?.cancel()
                reconnectJob = realtimeScope.launch {
                    var ref = 2
                    while (isActive && realtimeToken == accessToken) {
                        delay(25_000)
                        webSocket.send(
                            JSONObject()
                                .put("topic", "phoenix")
                                .put("event", "heartbeat")
                                .put("payload", JSONObject())
                                .put("ref", (ref++).toString())
                                .toString()
                        )
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (
                    text.contains("postgres_changes") ||
                    text.contains("\"eventType\"") ||
                    text.contains("\"INSERT\"") ||
                    text.contains("\"UPDATE\"") ||
                    text.contains("\"DELETE\"")
                ) {
                    _realtimeChanges.tryEmit(Unit)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scheduleReconnect(accessToken)
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                scheduleReconnect(accessToken)
            }
        })
    }

    @Synchronized
    private fun scheduleReconnect(token: String) {
        if (realtimeToken != token) return
        reconnectJob?.cancel()
        reconnectJob = realtimeScope.launch {
            delay(4_000)
            synchronized(this@SupabaseClient) {
                if (realtimeToken == token) openRealtimeSocket(token)
            }
        }
    }

    private suspend fun authRequest(path: String, body: JSONObject): JSONObject {
        val result = execute(
            path = path,
            method = "POST",
            body = body,
            bearer = null,
            prefer = null
        )
        if (result.code !in 200..299) {
            throw responseError(result)
        }
        return JSONObject(result.body)
    }

    private suspend fun authenticatedRequest(
        path: String,
        method: String,
        body: JSONObject?,
        prefer: String? = null,
        expectedUserId: String? = null
    ): String {
        val captured = sessionManager.activeRemoteSession ?: throw inactiveSession()
        val initial = captured.credentials
        if (expectedUserId != null && initial.userId != expectedUserId) throw inactiveSession()

        var result = execute(path, method, body, initial.accessToken, prefer)
        if (result.code == 401 && initial.refreshToken.isNotBlank()) {
            val refreshed = renewCurrentSession(captured)
            requireCurrentSession(captured)
            result = execute(path, method, body, refreshed.accessToken, prefer)
        }

        requireCurrentSession(captured)
        if (result.code !in 200..299) {
            throw responseError(result)
        }

        return result.body
    }

    private suspend fun renewCurrentSession(captured: ActiveRemoteSession): RemoteSession =
        refreshMutex.withLock {
            val current = requireCurrentSession(captured)
            if (current !== captured) return@withLock current.credentials

            // Supabase puede rotar el refresh token. Guarda la renovación
            // aunque se cancele la consulta que la inició.
            withContext(NonCancellable) {
                val refreshed = refreshSession(current.credentials)
                if (!sessionManager.updateRemoteSession(current, refreshed)) throw inactiveSession()
                startRealtimeFor(refreshed)
                refreshed
            }
        }

    private fun requireCurrentSession(captured: ActiveRemoteSession): ActiveRemoteSession {
        val current = sessionManager.activeRemoteSession
        if (current == null || current.generation !== captured.generation) throw inactiveSession()
        return current
    }

    private fun inactiveSession() = SupabaseException(
        "La sesión remota cambió o expiró. Inicia sesión nuevamente.",
        statusCode = 401
    )

    private suspend fun refreshSession(current: RemoteSession): RemoteSession {
        val body = JSONObject().put("refresh_token", current.refreshToken)
        val response = authRequest(
            "/auth/v1/token?grant_type=refresh_token",
            body
        )
        val refreshed = parseAuthSession(response)
        if (refreshed.userId != current.userId) throw inactiveSession()
        return refreshed
    }

    private suspend fun execute(
        path: String,
        method: String,
        body: JSONObject?,
        bearer: String?,
        prefer: String?
    ): HttpResult = withContext(Dispatchers.IO) {
        val requestBody = body?.toString()?.toRequestBody(jsonMediaType)

        val builder = Request.Builder()
            .url("$baseUrl$path")
            .header("apikey", apiKey)
            .header("Accept", "application/json")

        if (bearer != null) {
            builder.header("Authorization", "Bearer $bearer")
        }
        if (prefer != null) {
            builder.header("Prefer", prefer)
        }

        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody(jsonMediaType))
            "PATCH" -> builder.patch(requestBody ?: "{}".toRequestBody(jsonMediaType))
            "DELETE" -> builder.delete(requestBody)
            else -> throw IllegalArgumentException("Método HTTP no soportado: $method")
        }

        http.newCall(builder.build()).execute().use { response ->
            HttpResult(
                code = response.code,
                body = response.body?.string().orEmpty()
            )
        }
    }

    private fun parseAuthSession(json: JSONObject): RemoteSession {
        val accessToken = json.optString("access_token")
        val refreshToken = json.optString("refresh_token")
        val user = json.optJSONObject("user")
            ?: throw SupabaseException("Supabase no devolvió el usuario autenticado.")
        val userId = user.optString("id")

        if (accessToken.isBlank() || refreshToken.isBlank() || userId.isBlank()) {
            throw SupabaseException(
                "Supabase no entregó una sesión activa. Desactiva la confirmación de correo en Authentication."
            )
        }

        val expiresIn = json.optLong("expires_in", 3600L)
        val expiresAt = json.optLong("expires_at", 0L).let {
            if (it > 0L) it * 1000L else System.currentTimeMillis() + expiresIn * 1000L
        }

        return RemoteSession(
            userId = userId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = expiresAt
        )
    }

    private fun responseError(result: HttpResult): SupabaseException {
        val message = parseError(result.body)
        val code = runCatching { JSONObject(result.body).optString("error_code") }
            .getOrNull()
            ?.ifBlank { null }
            ?: "invalid_credentials".takeIf {
                result.code == 400 && message.equals("Invalid login credentials", ignoreCase = true)
            }
        return SupabaseException(message, result.code, code)
    }

    private fun parseError(body: String): String {
        return try {
            val json = JSONObject(body)
            json.optString("message")
                .ifBlank { json.optString("msg") }
                .ifBlank { json.optString("error_description") }
                .ifBlank { json.optString("error") }
                .ifBlank { body.take(300) }
        } catch (_: Exception) {
            body.take(300).ifBlank { "Error de conexión con Supabase." }
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private data class HttpResult(
        val code: Int,
        val body: String
    )
}
