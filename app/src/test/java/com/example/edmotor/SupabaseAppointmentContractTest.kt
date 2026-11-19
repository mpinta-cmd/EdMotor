package com.example.edmotor

import com.example.edmotor.data.remote.SupabaseAppointmentAccess
import com.example.edmotor.data.remote.SupabaseAppointmentRequest
import com.example.edmotor.data.remote.SupabaseConnection
import com.example.edmotor.data.remote.createSupabaseAppointmentService
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAppointmentContractTest {
    @Test
    fun authenticatedCallsUseThePrivateTableAndNamespacedRpcs() {
        data class Request(
            val method: String,
            val path: String,
            val query: String?,
            val apiKey: String?,
            val authorization: String?,
            val body: String
        )

        val id = 2_147_483_648L
        val clientId = "f84a9dbb-35a9-4534-9748-327e8d35f077"
        val apiKey = "sb_publishable_fixture1"
        val expiredToken = "expired-fixture-token"
        val token = "fixture-access-token"
        val row = """{"id":$id,"client_auth_id":"$clientId","client_name":"Cliente","vehicle_brand":"Marca","vehicle_model":"Modelo","vehicle_year":2023,"scheduled_at_ms":1800000000000,"service_type":"Revisión general","status":"PENDING","created_at_ms":1790000000000,"updated_at_ms":1790000000000}"""
        val calls = ConcurrentLinkedQueue<Request>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/v1/") { exchange ->
            val request = Request(
                exchange.requestMethod,
                exchange.requestURI.path,
                exchange.requestURI.query,
                exchange.requestHeaders.getFirst("apikey"),
                exchange.requestHeaders.getFirst("Authorization"),
                exchange.requestBody.bufferedReader().use { it.readText() }
            )
            calls.add(request)
            val (status, response) = when {
                request.authorization == "Bearer $expiredToken" -> 401 to "{}"
                request.path == "/rest/v1/edmotor_appointments" ->
                    200 to if (request.query.orEmpty().contains("id=gt.$id")) "[]" else "[$row]"
                request.path.endsWith("/edmotor_create_appointment") -> 200 to "[$row]"
                request.path.endsWith("/edmotor_accept_appointment") ||
                    request.path.endsWith("/edmotor_reject_appointment") -> 200 to "true"
                else -> 404 to "{}"
            }
            val payload = response.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()

        try {
            val service = createSupabaseAppointmentService("http://127.0.0.1:${server.address.port}/", apiKey)
            val connection = requireNotNull(SupabaseConnection.fromConfig("https://fixture.supabase.co/", apiKey))
            val profileKey = connection.profileKey(clientId)
            var currentToken = expiredToken
            var refreshes = 0
            val access = SupabaseAppointmentAccess(connection, { expectedProfileKey, rejectedToken ->
                assertEquals(profileKey, expectedProfileKey)
                if (rejectedToken != null) {
                    assertEquals(expiredToken, rejectedToken)
                    currentToken = token
                    refreshes++
                }
                currentToken
            }, service)
            runBlocking {
                val appointment = access.getAppointments(profileKey, clientId).single()
                assertEquals(id, appointment.id)
                assertEquals(clientId, appointment.clientAuthId)
                assertEquals("Revisión general", appointment.serviceType)
                val created = access.createAppointment(
                    profileKey,
                    SupabaseAppointmentRequest("Marca", "Modelo", 2023, 1_800_000_000_000L, "Revisión general")
                )
                assertEquals(id, created.id)
                access.reviewAppointment(profileKey, id, accept = true)
                access.reviewAppointment(profileKey, id, accept = false)
            }

            assertEquals(6, calls.size)
            assertEquals(1, refreshes)
            assertTrue(calls.all { it.apiKey == apiKey })
            assertEquals("Bearer $expiredToken", calls.first().authorization)
            assertTrue(calls.drop(1).all { it.authorization == "Bearer $token" })
            val listCalls = calls.filter { it.method == "GET" }
            assertTrue(listCalls.all { it.query.orEmpty().contains("client_auth_id=eq.$clientId") })
            assertTrue(listCalls.all { it.query.orEmpty().contains("order=id.asc") })
            assertTrue(listCalls.last().query.orEmpty().contains("id=gt.$id"))

            val create = calls.single { it.path.endsWith("/edmotor_create_appointment") }
            val creation = JsonParser.parseString(create.body).asJsonObject
            assertEquals(
                setOf("p_vehicle_brand", "p_vehicle_model", "p_vehicle_year", "p_scheduled_at_ms", "p_service_type"),
                creation.keySet()
            )
            assertEquals(1_800_000_000_000L, creation.get("p_scheduled_at_ms").asLong)

            for (review in calls.filter { it.path.endsWith("/edmotor_accept_appointment") || it.path.endsWith("/edmotor_reject_appointment") }) {
                val body = JsonParser.parseString(review.body).asJsonObject
                assertEquals(setOf("p_appointment_id"), body.keySet())
                assertEquals(id, body.get("p_appointment_id").asLong)
            }
        } finally {
            server.stop(0)
        }
    }
}
