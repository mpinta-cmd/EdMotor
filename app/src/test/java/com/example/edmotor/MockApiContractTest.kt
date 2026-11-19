package com.example.edmotor

import com.example.edmotor.data.remote.MockApiConnection
import com.example.edmotor.data.remote.MockApiService
import com.example.edmotor.data.remote.MockAppointmentCreateDto
import com.example.edmotor.data.remote.MockAppointmentStatusDto
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MockApiContractTest {
    @Test
    fun profileKeysAreScopedByProject() {
        val connection = requireNotNull(MockApiConnection.fromBaseUrl("https://demo.mockapi.io/api/v1"))
        val sameProject = requireNotNull(MockApiConnection.fromBaseUrl("https://demo.mockapi.io/api/v1/"))
        val otherProject = requireNotNull(MockApiConnection.fromBaseUrl("https://other.mockapi.io/api/v1/"))
        val key = connection.profileKey("1")

        assertEquals(key, sameProject.profileKey("1"))
        assertEquals("1", connection.profileId(key))
        assertNotEquals(key, otherProject.profileKey("1"))
        assertNull(otherProject.profileId(key))
        assertNull(connection.profileId("local-demo:client"))
        assertNull(MockApiConnection.fromBaseUrl(""))
        assertNull(MockApiConnection.fromBaseUrl("https://mockapi.io/projects"))
        assertNull(MockApiConnection.fromBaseUrl("https://demo.mockapi.io.example.com/"))
    }

    @Test
    fun retrofitListsProfilesCreatesAndUpdatesAnAppointment() {
        data class Request(val route: String, val body: String)
        val requests = ConcurrentLinkedQueue<Request>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val appointment = """{"id":"7","profileId":"1","serviceType":"Revisión general","vehicleBrand":"Demo","vehicleModel":"Modelo","vehicleYear":2023,"scheduledAtMillis":1800000000000,"status":"PENDING","createdAtMillis":1790000000000,"updatedAtMillis":1790000000000}"""
        server.createContext("/api/v1/") { exchange ->
            val route = "${exchange.requestMethod} ${exchange.requestURI}"
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            requests.add(Request(route, body))
            val response = when (route) {
                "GET /api/v1/profiles" -> """[{"id":"1","displayName":"Cliente","role":"CLIENT"}]"""
                "GET /api/v1/appointments?profileId=1" -> "[$appointment]"
                "GET /api/v1/appointments/7", "POST /api/v1/appointments" -> appointment
                "PUT /api/v1/appointments/7" -> appointment.replace("PENDING", "CONFIRMED")
                else -> null
            }
            val payload = response.orEmpty().toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (response != null) 200 else 404, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()

        try {
            val api = Retrofit.Builder()
                .baseUrl("http://127.0.0.1:${server.address.port}/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(MockApiService::class.java)

            runBlocking {
                assertEquals("CLIENT", api.getProfiles().single().role)
                assertEquals("7", api.getAppointments("1").single().id)
                val created = api.createAppointment(MockAppointmentCreateDto(
                    profileId = "1",
                    serviceType = "Revisión general",
                    vehicleBrand = "Demo",
                    vehicleModel = "Modelo",
                    vehicleYear = 2023,
                    scheduledAtMillis = 1_800_000_000_000,
                    status = "PENDING",
                    createdAtMillis = 1_790_000_000_000,
                    updatedAtMillis = 1_790_000_000_000
                ))
                assertEquals("PENDING", api.getAppointment(created.id).status)
                assertEquals("CONFIRMED", api.updateAppointment(
                    created.id, MockAppointmentStatusDto("CONFIRMED", 1_790_000_001_000)
                ).status)
            }

            val postBody = JsonParser.parseString(requests.single { it.route.startsWith("POST") }.body).asJsonObject
            assertEquals("1", postBody.get("profileId").asString)
            assertEquals(1_800_000_000_000L, postBody.get("scheduledAtMillis").asLong)
            assertEquals(setOf("profileId", "serviceType", "vehicleBrand", "vehicleModel", "vehicleYear",
                "scheduledAtMillis", "status", "createdAtMillis", "updatedAtMillis"), postBody.keySet())
            val put = requests.find { it.route == "PUT /api/v1/appointments/7" }
            assertNotNull(put)
            assertEquals(setOf("status", "updatedAtMillis"), JsonParser.parseString(put!!.body).asJsonObject.keySet())
        } finally {
            server.stop(0)
        }
    }
}
