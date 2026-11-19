package com.example.edmotor

import com.example.edmotor.data.remote.MockApiConnection
import com.example.edmotor.data.remote.MockApiService
import com.example.edmotor.data.remote.MockAppointmentCreateDto
import com.example.edmotor.data.remote.MockAppointmentStatusDto
import com.example.edmotor.data.remote.MockProfileCreateDto
import com.example.edmotor.data.remote.createMockApiService
import com.example.edmotor.data.remote.listAppointments
import com.example.edmotor.data.remote.mockAppointmentFailureMessage
import com.example.edmotor.data.remote.submitAppointment
import com.example.edmotor.data.remote.submitAppointmentStatus
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
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
    fun retrofitCreatesAClientProfileAndUpdatesAnAppointment() {
        data class Request(val route: String, val body: String)
        val requests = ConcurrentLinkedQueue<Request>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/") { exchange ->
            val route = "${exchange.requestMethod} ${exchange.requestURI}"
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            requests.add(Request(route, body))
            val response = when (route) {
                "GET /api/v1/profiles" -> """[{"id":"1","displayName":"Cliente","role":"CLIENT"}]"""
                "POST /api/v1/profiles" -> """{"id":"3","displayName":"Cliente nuevo","role":"CLIENT"}"""
                "GET /api/v1/appointments?profileId=1" -> "[$EXISTING_APPOINTMENT]"
                "GET /api/v1/appointments/7", "POST /api/v1/appointments" -> EXISTING_APPOINTMENT
                "PUT /api/v1/appointments/7" -> EXISTING_APPOINTMENT.replace("PENDING", "CONFIRMED")
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
                val profile = api.createProfile(MockProfileCreateDto("Cliente nuevo"))
                assertEquals("3", profile.id)
                assertEquals("Cliente nuevo", profile.displayName)
                assertEquals("CLIENT", profile.role)
                assertEquals("7", api.listAppointments("1").single().id)
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

            val profileBody = JsonParser.parseString(
                requests.single { it.route == "POST /api/v1/profiles" }.body
            ).asJsonObject
            assertEquals(setOf("displayName", "role"), profileBody.keySet())
            assertEquals("Cliente nuevo", profileBody.get("displayName").asString)
            assertEquals("CLIENT", profileBody.get("role").asString)

            val postBody = JsonParser.parseString(
                requests.single { it.route == "POST /api/v1/appointments" }.body
            ).asJsonObject
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

    @Test
    fun firstAppointmentCanBeCreatedAfterFilteredNotFoundAndReloadedWithoutAnotherPost() {
        val routes = ConcurrentLinkedQueue<String>()
        val savedAppointment = AtomicReference<String>()
        withAppointmentEndpoint(
            respond = { route, body ->
                routes.add(route)
                when (route) {
                    "GET /api/v1/appointments?profileId=3" -> savedAppointment.get()?.let { 200 to "[$it]" }
                        ?: (404 to "\"Not found\"")
                    "GET /api/v1/appointments?limit=1&page=1" -> 200 to "[$EXISTING_APPOINTMENT]"
                    "POST /api/v1/appointments" -> {
                        val appointment = JsonParser.parseString(body).asJsonObject
                        appointment.addProperty("id", "8")
                        val record = appointment.toString()
                        savedAppointment.set(record)
                        201 to record
                    }
                    else -> 500 to "\"Unexpected route\""
                }
            }
        ) { newApi ->
            val api = newApi()
            assertTrue(api.listAppointments("3").isEmpty())
            val created = api.submitAppointment(MockAppointmentCreateDto(
                profileId = "3",
                serviceType = "Revisión general",
                vehicleBrand = "Vehículo",
                vehicleModel = "registrado",
                vehicleYear = 0,
                scheduledAtMillis = 1_800_000_000_000,
                status = "PENDING",
                createdAtMillis = 1_790_000_000_000,
                updatedAtMillis = 1_790_000_000_000
            ))
            assertEquals("8", created.id)
            assertEquals("3", created.profileId)
            assertEquals("PENDING", created.status)

            val reopened = newApi().listAppointments("3").single()
            assertEquals(created, reopened)
        }
        assertEquals(listOf(
            "GET /api/v1/appointments?profileId=3",
            "GET /api/v1/appointments?limit=1&page=1",
            "POST /api/v1/appointments",
            "GET /api/v1/appointments?profileId=3"
        ), routes.toList())
    }

    @Test
    fun ambiguousPostReconcilesExactAttemptWithoutRetryingTheWrite() {
        val routes = ConcurrentLinkedQueue<String>()
        val otherAttempt = EXISTING_APPOINTMENT.replace("1790000000000", "1790000000001")
        val requestedAppointment = EXISTING_APPOINTMENT.replace("\"id\":\"7\"", "\"id\":\"8\"")
        withAppointmentEndpoint(
            headers = mapOf("Retry-After" to "0"),
            respond = { route, _ ->
                routes.add(route)
                when (route) {
                    "POST /api/v1/appointments" -> 503 to "\"Response lost\""
                    "GET /api/v1/appointments?profileId=1" -> 200 to "[$otherAttempt,$requestedAppointment]"
                    else -> 500 to "\"Unexpected route\""
                }
            }
        ) { newApi -> assertEquals("8", newApi().submitAppointment(REQUEST).id) }
        assertEquals(listOf("POST /api/v1/appointments", "GET /api/v1/appointments?profileId=1"), routes.toList())
    }

    @Test
    fun appointmentWritesDoNotFollowRedirects() {
        val routes = ConcurrentLinkedQueue<String>()
        withAppointmentEndpoint(
            headers = mapOf("Location" to "/api/v1/appointments/redirected"),
            respond = { route, _ ->
                routes.add(route)
                302 to "\"Redirected\""
            }
        ) { newApi ->
            val failure = runCatching { newApi().createAppointment(REQUEST) }.exceptionOrNull()
            assertEquals(302, (failure as? HttpException)?.code())
        }
        assertEquals(listOf("POST /api/v1/appointments"), routes.toList())
    }

    @Test
    fun ambiguousPutRecoversCurrentStatusWithoutReplaying() {
        val routes = ConcurrentLinkedQueue<String>()
        val confirmed = EXISTING_APPOINTMENT.replace("PENDING", "CONFIRMED")
        withAppointmentEndpoint(
            headers = mapOf("Retry-After" to "0"),
            respond = { route, _ ->
                when (route) {
                    "PUT /api/v1/appointments/7" -> {
                        routes.add(route)
                        503 to "\"Response lost\""
                    }
                    "GET /api/v1/appointments/7" -> {
                        val wasUpdated = routes.peek() != null
                        routes.add(route)
                        200 to if (wasUpdated) confirmed else EXISTING_APPOINTMENT
                    }
                    else -> 500 to "\"Unexpected route\""
                }
            }
        ) { newApi ->
            val api = newApi()
            val target = api.getAppointment("7")
            api.submitAppointmentStatus(target, MockAppointmentStatusDto("CONFIRMED", 1_790_000_001_000))
        }
        assertEquals(listOf("GET /api/v1/appointments/7", "PUT /api/v1/appointments/7", "GET /api/v1/appointments/7"), routes.toList())
    }

    @Test
    fun reschedulingRecoversAnAppliedPutWithoutCreatingAnotherAppointment() {
        val routes = ConcurrentLinkedQueue<String>()
        val saved = AtomicReference(EXISTING_APPOINTMENT)
        val updateBody = AtomicReference<String>()
        val update = MockAppointmentStatusDto(null, 1_790_000_001_000, 1_800_003_600_000)
        withAppointmentEndpoint(
            headers = mapOf("Retry-After" to "0"),
            respond = { route, body ->
                routes.add(route)
                when (route) {
                    "GET /api/v1/appointments/7" -> 200 to saved.get()
                    "PUT /api/v1/appointments/7" -> {
                        updateBody.set(body)
                        saved.set(applyUpdate(saved.get(), body))
                        503 to "\"Response lost\""
                    }
                    "GET /api/v1/appointments?profileId=1" -> 200 to "[${saved.get()}]"
                    else -> 500 to "\"Unexpected route\""
                }
            }
        ) { newApi ->
            val api = newApi()
            val target = api.getAppointment("7")
            api.submitAppointmentStatus(target, update)

            assertEquals(
                target.copy(
                    scheduledAtMillis = requireNotNull(update.scheduledAtMillis),
                    updatedAtMillis = update.updatedAtMillis
                ),
                newApi().listAppointments(target.profileId).single()
            )
        }
        assertEquals(
            setOf("scheduledAtMillis", "updatedAtMillis"),
            JsonParser.parseString(updateBody.get()).asJsonObject.keySet()
        )
        assertEquals(
            listOf(
                "GET /api/v1/appointments/7",
                "PUT /api/v1/appointments/7",
                "GET /api/v1/appointments/7",
                "GET /api/v1/appointments?profileId=1"
            ),
            routes.toList()
        )
    }

    @Test
    fun reschedulingRejectsAnUnrelatedOrStaleReadBackAfterAFailedPut() {
        val update = MockAppointmentStatusDto("CONFIRMED", 1_790_000_001_000, 1_800_003_600_000)
        val updated = applyUpdate(
            EXISTING_APPOINTMENT,
            """{"status":"CONFIRMED","updatedAtMillis":1790000001000,"scheduledAtMillis":1800003600000}"""
        )
        val incorrectRecords = listOf(
            updated.replace("\"id\":\"7\"", "\"id\":\"8\""),
            updated.replace("\"profileId\":\"1\"", "\"profileId\":\"2\""),
            updated.replace("\"createdAtMillis\":1790000000000", "\"createdAtMillis\":1790000000001"),
            updated.replace("CONFIRMED", "PENDING"),
            updated.replace("1800003600000", "1800000000000")
        )
        for (record in incorrectRecords) {
            val routes = ConcurrentLinkedQueue<String>()
            withAppointmentEndpoint(
                headers = mapOf("Retry-After" to "0"),
                respond = { route, _ ->
                    routes.add(route)
                    when (route) {
                        "GET /api/v1/appointments/7" -> {
                            val afterWrite = routes.contains("PUT /api/v1/appointments/7")
                            200 to if (afterWrite) record else EXISTING_APPOINTMENT
                        }
                        "PUT /api/v1/appointments/7" -> 503 to "\"Response lost\""
                        else -> 500 to "\"Unexpected route\""
                    }
                }
            ) { newApi ->
                val api = newApi()
                val target = api.getAppointment("7")
                val failure = runCatching { api.submitAppointmentStatus(target, update) }.exceptionOrNull()
                assertEquals(record, 503, (failure as? HttpException)?.code())
            }
            assertEquals(
                listOf("GET /api/v1/appointments/7", "PUT /api/v1/appointments/7", "GET /api/v1/appointments/7"),
                routes.toList()
            )
        }
    }

    @Test
    fun cancelledAppointmentsStayVisibleInTheClientsListing() {
        val saved = AtomicReference(EXISTING_APPOINTMENT)
        withAppointmentEndpoint(respond = { route, body ->
            when (route) {
                "GET /api/v1/appointments/7" -> 200 to saved.get()
                "PUT /api/v1/appointments/7" -> {
                    val cancelled = applyUpdate(saved.get(), body)
                    saved.set(cancelled)
                    200 to cancelled
                }
                "GET /api/v1/appointments?profileId=1" -> 200 to "[${saved.get()}]"
                else -> 500 to "\"Unexpected route\""
            }
        }) { newApi ->
            val api = newApi()
            val target = api.getAppointment("7")
            val update = MockAppointmentStatusDto("CANCELLED", 1_790_000_001_000)
            api.submitAppointmentStatus(target, update)

            assertEquals(
                target.copy(status = "CANCELLED", updatedAtMillis = update.updatedAtMillis),
                newApi().listAppointments(target.profileId).single()
            )
        }
    }

    @Test
    fun missingAppointmentCollectionRemainsAnError() {
        val routes = ConcurrentLinkedQueue<String>()
        withAppointmentEndpoint(
            respond = { route, _ ->
                routes.add(route)
                404 to "\"Not found\""
            }
        ) { newApi ->
            val error = runCatching { newApi().listAppointments("3") }.exceptionOrNull()
            assertEquals(404, (error as? HttpException)?.code())
        }
        assertEquals(listOf(
            "GET /api/v1/appointments?profileId=3",
            "GET /api/v1/appointments?limit=1&page=1"
        ), routes.toList())
    }

    @Test
    fun aSuccessfulProbeWithNonArrayJsonDoesNotHideMisconfiguration() {
        withAppointmentEndpoint(
            respond = { route, _ ->
                if (route == "GET /api/v1/appointments?limit=1&page=1") {
                    200 to """{"message":"Not an appointment collection"}"""
                } else {
                    404 to "\"Not found\""
                }
            }
        ) { newApi ->
            assertTrue(runCatching { newApi().listAppointments("3") }.isFailure)
        }
    }

    @Test
    fun malformedAndConflictingAppointmentsDoNotHideValidRows() {
        val routes = ConcurrentLinkedQueue<String>()
        val conflicting = EXISTING_APPOINTMENT.replace("\"id\":\"7\"", "\"id\":\"6\"")
        val otherVersion = conflicting.replace("PENDING", "CONFIRMED")
        val optionalFieldsMissing = """{"id":"8","profileId":"1","scheduledAtMillis":1800000000000,"status":"PENDING"}"""
        withAppointmentEndpoint(
            respond = { route, _ ->
                routes.add(route)
                200 to """[
                    null,
                    {"id":"2","profileId":"1","scheduledAtMillis":"incorrecta"},
                    $conflicting,
                    $otherVersion,
                    $EXISTING_APPOINTMENT,
                    $EXISTING_APPOINTMENT,
                    $optionalFieldsMissing
                ]""".trimIndent()
            }
        ) { newApi ->
            assertEquals(listOf("7", "8"), newApi().listAppointments("1").map { it.id })
        }
        assertEquals(listOf("GET /api/v1/appointments?profileId=1"), routes.toList())
    }

    @Test
    fun onlyKnownHttpRejectionsReplaceTheAmbiguousCreationMessage() {
        val fallback = "No se pudo confirmar el envío."
        for (status in listOf(400, 404, 422, 429)) {
            assertNotEquals(fallback, mockAppointmentFailureMessage(status, fallback))
        }
        for (status in listOf(408, 500, 502, 503)) {
            assertEquals(fallback, mockAppointmentFailureMessage(status, fallback))
        }
    }

    private fun applyUpdate(existing: String, body: String): String {
        val appointment = JsonParser.parseString(existing).asJsonObject
        JsonParser.parseString(body).asJsonObject.entrySet().forEach { (name, value) ->
            appointment.add(name, value)
        }
        return appointment.toString()
    }

    private fun withAppointmentEndpoint(
        headers: Map<String, String> = emptyMap(),
        respond: (route: String, body: String) -> Pair<Int, String>,
        assertions: suspend (() -> MockApiService) -> Unit
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/appointments") { exchange ->
            val route = "${exchange.requestMethod} ${exchange.requestURI}"
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            val (status, response) = respond(route, body)
            val payload = response.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            headers.forEach { (name, value) -> exchange.responseHeaders.set(name, value) }
            exchange.sendResponseHeaders(status, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        try {
            val newApi = { createMockApiService("http://127.0.0.1:${server.address.port}/api/v1/") }
            runBlocking { assertions(newApi) }
        } finally {
            server.stop(0)
        }
    }

    private companion object {
        const val EXISTING_APPOINTMENT = """{"id":"7","profileId":"1","serviceType":"Revisión general","vehicleBrand":"Demo","vehicleModel":"Modelo","vehicleYear":2023,"scheduledAtMillis":1800000000000,"status":"PENDING","createdAtMillis":1790000000000,"updatedAtMillis":1790000000000}"""
        val REQUEST = MockAppointmentCreateDto(
            profileId = "1", serviceType = "Revisión general", vehicleBrand = "Demo", vehicleModel = "Modelo",
            vehicleYear = 2023, scheduledAtMillis = 1_800_000_000_000, status = "PENDING",
            createdAtMillis = 1_790_000_000_000, updatedAtMillis = 1_790_000_000_000
        )
    }
}
