package com.example.edmotor.online

import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSink
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

private const val CHECKOUT_PATH = "orders/checkout"

internal interface OnlineService {
    @POST("auth/login")
    suspend fun login(@Body body: OnlineCredentials): OnlineAuthResponse

    @POST("auth/register")
    suspend fun register(@Body body: OnlineRegistration): OnlineAuthResponse

    @GET("auth/me")
    suspend fun me(@Header("Authorization") auth: String): OnlineUser

    @POST("auth/logout")
    suspend fun logout(@Header("Authorization") auth: String)

    @GET("products")
    suspend fun products(@Header("Authorization") auth: String? = null): List<OnlineProduct>

    @POST("products")
    suspend fun createProduct(@Header("Authorization") auth: String, @Body body: OnlineProductWrite): OnlineProduct

    @PUT("products/{id}")
    suspend fun updateProduct(
        @Header("Authorization") auth: String, @Path("id") id: Long, @Body body: OnlineProductWrite
    ): OnlineProduct

    @DELETE("products/{id}")
    suspend fun deleteProduct(@Header("Authorization") auth: String, @Path("id") id: Long, @Query("version") version: Long)

    @Multipart
    @POST("products/images")
    suspend fun uploadImage(@Header("Authorization") auth: String, @Part file: MultipartBody.Part): OnlineImageUpload

    @Streaming
    @GET("products/images/{id}")
    suspend fun productImage(@Header("Authorization") auth: String?, @Path("id") id: String): ResponseBody

    @GET("orders")
    suspend fun orders(@Header("Authorization") auth: String): List<OnlineOrder>

    @GET("orders/requests/{requestId}")
    suspend fun orderByRequest(
        @Header("Authorization") auth: String, @Path("requestId") requestId: String
    ): OnlineOrder

    @POST("orders")
    suspend fun createOrder(@Header("Authorization") auth: String, @Body body: OnlineOrderWrite): OnlineOrder

    @POST(CHECKOUT_PATH)
    suspend fun checkout(@Header("Authorization") auth: String, @Body body: OnlineCheckoutWrite): OnlineOrder

    @POST("orders/{id}/decision")
    suspend fun decideOrder(
        @Header("Authorization") auth: String, @Path("id") id: Long, @Body body: OnlineOrderDecision
    ): OnlineOrder

    @GET("ratings/eligible")
    suspend fun eligibleRatings(@Header("Authorization") auth: String): List<OnlineRatingItem>

    @PUT("products/{id}/rating")
    suspend fun rateProduct(
        @Header("Authorization") auth: String,
        @Path("id") id: Long,
        @Body body: OnlineRatingWrite
    ): OnlineRatingResult

    @GET("vehicles")
    suspend fun vehicles(@Header("Authorization") auth: String): List<OnlineVehicle>

    @POST("vehicles")
    suspend fun createVehicle(@Header("Authorization") auth: String, @Body body: OnlineVehicleWrite): OnlineVehicle

    @GET("appointments")
    suspend fun appointments(@Header("Authorization") auth: String): List<OnlineAppointment>

    @POST("appointments")
    suspend fun createAppointment(@Header("Authorization") auth: String, @Body body: OnlineAppointmentWrite): OnlineAppointment

    @PATCH("appointments/{id}")
    suspend fun changeAppointment(
        @Header("Authorization") auth: String, @Path("id") id: Long, @Body body: OnlineAppointmentChange
    ): OnlineAppointment

    @DELETE("appointments/{id}")
    suspend fun cancelAppointment(
        @Header("Authorization") auth: String, @Path("id") id: Long, @Query("version") version: Long
    )

    @GET("maintenance")
    suspend fun maintenance(@Header("Authorization") auth: String): List<OnlineMaintenance>

    @POST("appointments/{id}/maintenance")
    suspend fun recordMaintenance(
        @Header("Authorization") auth: String, @Path("id") appointmentId: Long, @Body body: OnlineMaintenanceWrite
    ): OnlineMaintenance
}

internal class OnlineApi private constructor(val project: String, val service: OnlineService) {
    companion object {
        fun fromUrl(value: String, debug: Boolean): OnlineApi? {
            val url = value.trim().toHttpUrlOrNull() ?: return null
            if (url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty() ||
                url.query != null || url.fragment != null
            ) return null
            if (url.scheme != "https" && (!debug || url.host !in setOf("localhost", "127.0.0.1", "::1", "10.0.2.2"))) {
                return null
            }
            if (url.encodedPath.trimEnd('/') !in setOf("", "/api/v1")) return null

            val baseUrl = url.newBuilder().encodedPath("/api/v1/").build()
            val project = MessageDigest.getInstance("SHA-256").digest(baseUrl.toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .callTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    if (request.method in setOf("POST", "PUT", "PATCH", "DELETE")) {
                        val body = request.body ?: ByteArray(0).toRequestBody()
                        val oneShot = object : RequestBody() {
                            override fun contentType() = body.contentType()
                            override fun contentLength() = body.contentLength()
                            override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
                            override fun isOneShot() = true
                        }
                        val write = request.newBuilder().method(request.method, oneShot).build()
                        if (request.method == "POST" && request.url.encodedPath == "/api/v1/$CHECKOUT_PATH" &&
                            request.body != null && !body.isOneShot()
                        ) {
                            // Checkout persists this request ID before sending and the server deduplicates it.
                            chain.proceedWithRetry(write, retryUnavailable = false)
                        } else {
                            chain.proceed(write)
                        }
                    } else if (request.method == "GET") {
                        chain.proceedWithRetry(request, retryUnavailable = true)
                    } else {
                        chain.proceed(request)
                    }
                }
                .build()
            val service = Retrofit.Builder().baseUrl(baseUrl).client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(OnlineService::class.java)
            return OnlineApi(project, service)
        }
    }
}

private val transientReadStatusCodes = setOf(502, 503, 504)

private fun Interceptor.Chain.proceedWithRetry(request: Request, retryUnavailable: Boolean): Response {
    val response = try {
        proceed(request)
    } catch (error: IOException) {
        if (call().isCanceled() || !error.isTransientConnectionFailure()) throw error
        return proceed(request)
    }
    if (!retryUnavailable || response.code !in transientReadStatusCodes || response.header("Retry-After") != null ||
        response.priorResponse != null || call().isCanceled()
    ) return response

    response.close()
    return proceed(request)
}

private fun IOException.isTransientConnectionFailure(): Boolean =
    this is EOFException || this is SocketException || this is SocketTimeoutException ||
        cause is EOFException

private val productImagePath = Regex("/api/v1/products/images/([0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12})")

internal fun imageId(path: String): String? = productImagePath.matchEntire(path)?.groupValues?.get(1)

internal class OnlineInputError(message: String) : IllegalArgumentException(message)

internal fun InputStream.readWithin(limit: Int): ByteArray {
    val buffer = ByteArray(8192)
    val output = ByteArrayOutputStream()
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > limit) throw IOException("El contenido supera el tamaño permitido.")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun onlineError(error: Exception): String {
    if (error is OnlineInputError) return requireNotNull(error.message)
    if (error is HttpException) {
        val detail = runCatching {
            error.response()?.errorBody()?.use { response ->
                val json = JsonParser.parseString(response.byteStream().readWithin(4096).toString(Charsets.UTF_8))
                json.asJsonObject.get("detail")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            }
        }.getOrNull()?.filter { !it.isISOControl() }?.trim()?.take(240)?.takeIf { it.isNotEmpty() }
        return when (error.code()) {
            401 -> detail ?: "Tu sesión venció. Vuelve a iniciar sesión."
            403 -> detail ?: "Tu perfil no tiene permiso para realizar esta acción."
            404 -> detail ?: "El dato solicitado ya no está disponible."
            409 -> detail ?: "La información cambió. Actualiza los datos e inténtalo de nuevo."
            410 -> detail ?: "Esta solicitud ya no está disponible."
            400, 422 -> detail ?: "Revisa los datos ingresados."
            429 -> "Hay demasiadas solicitudes. Espera un momento y vuelve a intentar."
            in 300..399 -> "La dirección del servidor redirige la conexión. Revisa su configuración."
            else -> "El servidor no pudo completar la acción. Intenta de nuevo."
        }
    }
    if (error is IOException) return "No fue posible comunicarse con el servidor. Revisa tu conexión."
    return "No fue posible completar la acción. Intenta de nuevo."
}
