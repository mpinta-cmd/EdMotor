package com.example.edmotor.online

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException

class OnlineAppointmentRepositoryTest {
    @Test
    fun crudUsesTheAuthenticatedApiAndReusesTheBookingIdAfterAnUncertainResponse() {
        val requests = ConcurrentLinkedQueue<ObservedRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(APPOINTMENTS) { exchange ->
            try {
                val route = "${exchange.requestMethod} ${exchange.requestURI}"
                val auth = exchange.requestHeaders.getFirst("Authorization")
                val body = exchange.requestBody.bufferedReader().use { it.readText() }
                requests.add(ObservedRequest(route, auth, body))
                when (route) {
                    "POST $APPOINTMENTS" -> if (requests.size == 1) {
                        exchange.reply(503, "{}")
                    } else {
                        exchange.reply(201, appointment(version = 1))
                    }
                    "GET $APPOINTMENTS" -> exchange.reply(200, "[${appointment(version = 1)}]")
                    "PATCH $APPOINTMENTS/42" -> if (auth == WORKSHOP) {
                        exchange.reply(200, appointment(version = 3, scheduledAt = RESCHEDULED, status = "CONFIRMED"))
                    } else {
                        exchange.reply(200, appointment(version = 2, scheduledAt = RESCHEDULED))
                    }
                    "DELETE $APPOINTMENTS/42?version=3" -> exchange.reply(204)
                    else -> exchange.reply(404, "{}")
                }
            } finally {
                exchange.close()
            }
        }
        server.start()

        try {
            val api = requireNotNull(OnlineApi.fromUrl("http://127.0.0.1:${server.address.port}", debug = true))
            val client = OnlineAppointmentRepository(api.service, CLIENT)
            val workshop = OnlineAppointmentRepository(api.service, WORKSHOP)

            runBlocking {
                val failure = runCatching { client.create(7, SCHEDULED, "Revisión") }.exceptionOrNull() as? HttpException
                failure?.response()?.errorBody()?.close()
                assertEquals(503, failure?.code())
                assertEquals(1, requests.size)

                val created = client.create(7, SCHEDULED, "Revisión")
                assertEquals(42L, created.id)
                assertEquals(1L, created.version)
                assertEquals(listOf(created), client.list())

                val rescheduled = client.update(created, "RESCHEDULE", RESCHEDULED)
                assertEquals(RESCHEDULED, rescheduled.scheduledAtMillis)
                assertEquals(2L, rescheduled.version)
                val confirmed = workshop.update(rescheduled, "CONFIRM", null)
                assertEquals("CONFIRMED", confirmed.status)
                assertEquals(3L, confirmed.version)
                client.delete(confirmed)
            }

            val observed = requests.toList()
            assertEquals(
                listOf(
                    "POST $APPOINTMENTS", "POST $APPOINTMENTS", "GET $APPOINTMENTS",
                    "PATCH $APPOINTMENTS/42", "PATCH $APPOINTMENTS/42", "DELETE $APPOINTMENTS/42?version=3"
                ),
                observed.map { it.route }
            )
            assertEquals(listOf(CLIENT, CLIENT, CLIENT, CLIENT, WORKSHOP, CLIENT), observed.map { it.auth })
            val booking = JsonParser.parseString(observed[0].body).asJsonObject
            val requestId = UUID.fromString(booking.get("requestId").asString)
            assertEquals(
                JsonParser.parseString(
                    """{"requestId":"$requestId","vehicleId":7,"scheduledAtMillis":$SCHEDULED,"serviceType":"Revisión"}"""
                ),
                booking
            )
            assertEquals(booking, JsonParser.parseString(observed[1].body))
            assertEquals("", observed[2].body)
            assertEquals(
                JsonParser.parseString("""{"version":1,"action":"RESCHEDULE","scheduledAtMillis":$RESCHEDULED}"""),
                JsonParser.parseString(observed[3].body)
            )
            assertEquals(
                JsonParser.parseString("""{"version":2,"action":"CONFIRM"}"""),
                JsonParser.parseString(observed[4].body)
            )
            assertEquals("", observed[5].body)
        } finally {
            server.stop(0)
        }
    }

    private fun appointment(version: Long, scheduledAt: Long = SCHEDULED, status: String = "PENDING") = """
        {
            "id": 42, "version": $version, "vehicleId": 7, "clientId": 9, "clientName": "Cliente de prueba",
            "vehicleBrand": "Renault", "vehicleModel": "Logan", "vehicleYear": 2021, "vehiclePlate": "EDM123",
            "serviceType": "Revisión", "scheduledAtMillis": $scheduledAt, "status": "$status",
            "createdAtMillis": 1795708800000, "updatedAtMillis": 1795708800000, "finishedAtMillis": null
        }
    """.trimIndent()

    private fun HttpExchange.reply(code: Int, json: String? = null) {
        if (json == null) {
            sendResponseHeaders(code, -1)
            return
        }
        val payload = json.toByteArray(Charsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(code, payload.size.toLong())
        responseBody.use { it.write(payload) }
    }

    private data class ObservedRequest(val route: String, val auth: String?, val body: String)

    private companion object {
        const val APPOINTMENTS = "/api/v1/appointments"
        const val CLIENT = "Bearer cliente-de-prueba"
        const val WORKSHOP = "Bearer taller-de-prueba"
        const val SCHEDULED = 1795795200000L
        const val RESCHEDULED = 1795881600000L
    }
}
