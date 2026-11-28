package com.example.edmotor.online

import com.google.gson.annotations.SerializedName
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

internal data class VehicleModelSuggestion(val make: String, val model: String)

internal class VehicleModelLookupRepository private constructor(private val service: VpicService) {
    suspend fun findModels(make: String, year: Int): List<VehicleModelSuggestion> {
        val query = normalizeMakeName(make)
        require(make.length <= MAX_LABEL_LENGTH && query.isNotBlank() && query.none(Char::isISOControl))
        require(year in 1996..9999)

        val response = try {
            service.models(query, year)
        } catch (error: HttpException) {
            error.response()?.errorBody()?.close()
            throw error
        }
        val rows = response.results?.takeIf { it.size == response.count }
            ?: throw IOException("Incomplete vPIC models response.")

        return rows.mapNotNull { row ->
            val resultMake = row?.make?.let(::normalizeMakeName)
            val model = row?.model?.trim()
            if (resultMake == null || !resultMake.equals(query, ignoreCase = true) ||
                model.isNullOrBlank() || model.length > MAX_LABEL_LENGTH || model.any(Char::isISOControl)
            ) {
                null
            } else {
                VehicleModelSuggestion(resultMake, model)
            }
        }.distinctBy { it.model.lowercase(Locale.ROOT) }
            .sortedBy { it.model.lowercase(Locale.ROOT) }
    }

    companion object {
        private const val VPIC_BASE_URL = "https://vpic.nhtsa.dot.gov/api/"
        private const val MAX_LABEL_LENGTH = 80
        private val whitespace = Regex("\\s+")

        fun create(): VehicleModelLookupRepository = fromUrl(VPIC_BASE_URL)

        internal fun fromUrl(baseUrl: String): VehicleModelLookupRepository {
            val client = OkHttpClient.Builder()
                .cookieJar(CookieJar.NO_COOKIES)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(5, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)
                .build()
            val service = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(VpicService::class.java)
            return VehicleModelLookupRepository(service)
        }

        private fun normalizeMakeName(make: String): String = make.trim().replace(whitespace, " ")
    }
}

private interface VpicService {
    @GET("vehicles/GetModelsForMakeYear/make/{make}/modelyear/{year}?format=json")
    suspend fun models(
        @Path("make") make: String,
        @Path("year") year: Int
    ): VpicModelsResponse
}

private data class VpicModelsResponse(
    @SerializedName("Count") val count: Int?,
    @SerializedName("Results") val results: List<VpicModel?>?
)

private data class VpicModel(
    @SerializedName("Make_Name") val make: String?,
    @SerializedName("Model_Name") val model: String?
)
