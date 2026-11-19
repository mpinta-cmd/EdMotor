package com.example.edmotor

import com.example.edmotor.data.remote.MockApiConnection
import com.example.edmotor.data.remote.MockApiService
import com.example.edmotor.data.remote.MockAppointmentAvailability
import com.example.edmotor.data.remote.MockAppointmentCancellation
import com.example.edmotor.data.remote.MockAppointmentCreateDto
import com.example.edmotor.data.remote.MockAppointmentStatusDto
import com.example.edmotor.data.remote.MockProfileCreateDto
import com.example.edmotor.data.remote.cancelAppointmentIfMatches
import com.example.edmotor.data.remote.checkAppointmentAvailability
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
            assertEquals(MockAppointmentAvailability.Available, api.checkAppointmentAvailability("3"))
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
    fun pendingAndConfirmedAppointmentsBlockAnotherBookingForThatProfile() {
        for (status in listOf("PENDING", "CONFIRMED")) {
            val routes = ConcurrentLinkedQueue<String>()
            val appointment = EXISTING_APPOINTMENT.replace("PENDING", status)
            withAppointmentEndpoint(respond = { route, _ ->
                routes.add(route)
                when (route) {
                    "GET /api/v1/appointments?profileId=1" -> 200 to "[$appointment]"
                    else -> 500 to "\"Unexpected route\""
                }
            }) { newApi ->
                assertEquals(
                    status,
                    MockAppointmentAvailability.HasActive,
                    newApi().checkAppointmentAvailability("1")
                )
            }
            assertEquals(listOf("GET /api/v1/appointments?profileId=1"), routes.toList())
        }
    }

    @Test
    fun closedAppointmentsAndActiveAppointmentsOfAnotherProfileAllowANewBooking() {
        val routes = ConcurrentLinkedQueue<String>()
        val closed = listOf("COMPLETED", "CANCELLED", "REJECTED").mapIndexed { index, status ->
            applyUpdate(EXISTING_APPOINTMENT, """{"id":"${7 + index}","status":"$status"}""")
        }
        val otherClient = applyUpdate(EXISTING_APPOINTMENT, """{"id":"10","profileId":"10","status":"CONFIRMED"}""")
        val rows = (closed + otherClient).joinToString(prefix = "[", postfix = "]")
        withAppointmentEndpoint(respond = { route, body ->
            routes.add(route)
            when (route) {
                "GET /api/v1/appointments?profileId=1" -> 200 to rows
                "POST /api/v1/appointments" -> 201 to applyUpdate(body, """{"id":"11"}""")
                else -> 500 to "\"Unexpected route\""
            }
        }) { newApi ->
            val api = newApi()
            assertEquals(MockAppointmentAvailability.Available, api.checkAppointmentAvailability("1"))
            val now = 1_790_000_001_000L
            val created = api.submitAppointment(REQUEST.copy(createdAtMillis = now, updatedAtMillis = now))
            assertEquals("11", created.id)
            assertEquals("1", created.profileId)
            assertEquals("PENDING", created.status)
        }
        assertEquals(listOf("GET /api/v1/appointments?profileId=1", "POST /api/v1/appointments"), routes.toList())
    }

    @Test
    fun failedAvailabilityReadCannotClearAProfileForBooking() {
        val routes = ConcurrentLinkedQueue<String>()
        withAppointmentEndpoint(respond = { route, _ ->
            routes.add(route)
            503 to "\"Temporarily unavailable\""
        }) { newApi ->
            val failure = runCatching { newApi().checkAppointmentAvailability("1") }.exceptionOrNull()
            assertEquals(503, (failure as? HttpException)?.code())
        }
        assertEquals(listOf("GET /api/v1/appointments?profileId=1"), routes.toList())
    }

    @Test
    fun malformedActiveAppointmentCannotClearItsProfileForBooking() {
        val routes = ConcurrentLinkedQueue<String>()
        withAppointmentEndpoint(respond = { route, _ ->
            routes.add(route)
            200 to """[{"id":"7","profileId":"1","status":"PENDING"}]"""
        }) { newApi ->
            assertTrue(runCatching { newApi().checkAppointmentAvailability("1") }.isFailure)
        }
        assertEquals(listOf("GET /api/v1/appointments?profileId=1"), routes.toList())
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
        ) { newApi ->
            val submitted = newApi().submitAppointment(REQUEST)
            assertEquals("8", submitted.id)
            assertEquals(
                MockAppointmentAvailability.PreviouslySubmitted(submitted),
                newApi().checkAppointmentAvailability("1", previousRequest = REQUEST)
            )
        }
        assertEquals(
            listOf("POST /api/v1/appointments", "GET /api/v1/appointments?profileId=1", "GET /api/v1/appointments?profileId=1"),
            routes.toList()
        )
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
    fun legacyCancelledAppointmentsAreHiddenButCompletedAndRejectedRemainListed() {
        val cancelled = EXISTING_APPOINTMENT.replace("PENDING", "CANCELLED")
        val completed = applyUpdate(EXISTING_APPOINTMENT, """{"id":"8","status":"COMPLETED"}""")
        val rejected = applyUpdate(EXISTING_APPOINTMENT, """{"id":"9","status":"REJECTED"}""")
        val listing = "[$cancelled,$completed,$rejected]"
        val routes = ConcurrentLinkedQueue<String>()
        withAppointmentEndpoint(respond = { route, _ ->
            routes.add(route)
            when (route) {
                "GET /api/v1/appointments?profileId=1", "GET /api/v1/appointments" -> 200 to listing
                else -> 500 to "\"Unexpected route\""
            }
        }) { newApi ->
            val api = newApi()
            val expected = mapOf("8" to "COMPLETED", "9" to "REJECTED")
            assertEquals(expected, api.listAppointments("1").associate { it.id to it.status })
            assertEquals(expected, api.listAppointments().associate { it.id to it.status })
        }
        assertEquals(listOf("GET /api/v1/appointments?profileId=1", "GET /api/v1/appointments"), routes.toList())
    }

    @Test
    fun cancellingDeletesOnlyTheSelectedAppointment() {
        val completed = applyUpdate(EXISTING_APPOINTMENT, """{"id":"8","status":"COMPLETED"}""")
        val rejected = applyUpdate(EXISTING_APPOINTMENT, """{"id":"9","status":"REJECTED"}""")
        val saved = AtomicReference(listOf(EXISTING_APPOINTMENT, completed, rejected))
        val routes = ConcurrentLinkedQueue<String>()
        withAppointmentEndpoint(respond = { route, _ ->
            routes.add(route)
            when (route) {
                "GET /api/v1/appointments/7" -> 200 to EXISTING_APPOINTMENT
                "DELETE /api/v1/appointments/7" -> {
                    saved.set(saved.get() - EXISTING_APPOINTMENT)
                    200 to EXISTING_APPOINTMENT
                }
                "GET /api/v1/appointments?profileId=1" -> 200 to saved.get().joinToString(prefix = "[", postfix = "]")
                else -> 500 to "\"Unexpected route\""
            }
        }) { newApi ->
            val result = newApi().cancelAppointmentIfMatches("7", "1", REQUEST.createdAtMillis)
            assertEquals(MockAppointmentCancellation.REMOVED, result)
            val remaining = newApi().listAppointments("1").associate { it.id to it.status }
            assertEquals(mapOf("8" to "COMPLETED", "9" to "REJECTED"), remaining)
        }
        assertEquals(
            listOf("GET /api/v1/appointments/7", "DELETE /api/v1/appointments/7", "GET /api/v1/appointments?profileId=1"),
            routes.toList()
        )
    }

    @Test
    fun ambiguousDeleteChecksAbsenceAndRequiresAnAccessibleCollectionWithoutReplayingTheWrite() {
        val confirmed = EXISTING_APPOINTMENT.replace("PENDING", "CONFIRMED")
        for (collectionStatus in listOf(200, 404)) {
            val saved = AtomicReference<String?>(confirmed)
            val routes = ConcurrentLinkedQueue<String>()
            withAppointmentEndpoint(
                headers = mapOf("Retry-After" to "0"),
                respond = { route, _ ->
                    routes.add(route)
                    when (route) {
                        "GET /api/v1/appointments/7" -> saved.get()?.let { 200 to it } ?: (404 to "\"Not found\"")
                        "DELETE /api/v1/appointments/7" -> {
                            saved.set(null)
                            503 to "\"Response lost\""
                        }
                        "GET /api/v1/appointments?limit=1&page=1" -> {
                            collectionStatus to if (collectionStatus == 200) "[]" else "\"Not found\""
                        }
                        else -> 500 to "\"Unexpected route\""
                    }
                }
            ) { newApi ->
                val result = runCatching {
                    newApi().cancelAppointmentIfMatches("7", "1", REQUEST.createdAtMillis)
                }
                if (collectionStatus == 200) {
                    assertEquals(MockAppointmentCancellation.REMOVED, result.getOrThrow())
                } else {
                    assertTrue(result.isFailure)
                }
            }
            assertEquals(
                listOf(
                    "GET /api/v1/appointments/7",
                    "DELETE /api/v1/appointments/7",
                    "GET /api/v1/appointments/7",
                    "GET /api/v1/appointments?limit=1&page=1"
                ),
                routes.toList()
            )
        }
    }

    @Test
    fun cancellingAnAlreadyMissingAppointmentDoesNotWriteWhenItsCollectionExists() {
        val routes = ConcurrentLinkedQueue<String>()
        withAppointmentEndpoint(respond = { route, _ ->
            routes.add(route)
            when (route) {
                "GET /api/v1/appointments/7" -> 404 to "\"Not found\""
                "GET /api/v1/appointments?limit=1&page=1" -> 200 to "[]"
                else -> 500 to "\"Unexpected route\""
            }
        }) { newApi ->
            val result = newApi().cancelAppointmentIfMatches("7", "1", REQUEST.createdAtMillis)
            assertEquals(MockAppointmentCancellation.REMOVED, result)
        }
        assertEquals(listOf("GET /api/v1/appointments/7", "GET /api/v1/appointments?limit=1&page=1"), routes.toList())
    }

    @Test
    fun cancellationChecksIdentityAndStatusBeforeDeleting() {
        val cases = listOf(
            applyUpdate(EXISTING_APPOINTMENT, """{"id":"8"}""") to MockAppointmentCancellation.CHANGED,
            applyUpdate(EXISTING_APPOINTMENT, """{"profileId":"10"}""") to MockAppointmentCancellation.CHANGED,
            applyUpdate(EXISTING_APPOINTMENT, """{"createdAtMillis":1790000000001}""") to MockAppointmentCancellation.CHANGED,
            EXISTING_APPOINTMENT.replace("PENDING", "COMPLETED") to MockAppointmentCancellation.NOT_ACTIVE,
            EXISTING_APPOINTMENT.replace("PENDING", "REJECTED") to MockAppointmentCancellation.NOT_ACTIVE
        )
        for ((row, expected) in cases) {
            val routes = ConcurrentLinkedQueue<String>()
            withAppointmentEndpoint(respond = { route, _ ->
                routes.add(route)
                if (route == "GET /api/v1/appointments/7") 200 to row else 500 to "\"Unexpected route\""
            }) { newApi ->
                val result = newApi().cancelAppointmentIfMatches("7", "1", REQUEST.createdAtMillis)
                assertEquals(row, expected, result)
            }
            assertEquals(listOf("GET /api/v1/appointments/7"), routes.toList())
        }
    }

    @Test
    fun cancellationRechecksTheCallerBeforeTheDelete() {
        val routes = ConcurrentLinkedQueue<String>()
        withAppointmentEndpoint(respond = { route, _ ->
            routes.add(route)
            if (route == "GET /api/v1/appointments/7") 200 to EXISTING_APPOINTMENT else 500 to "\"Unexpected route\""
        }) { newApi ->
            val denied = IllegalStateException("Session changed")
            val failure = runCatching {
                newApi().cancelAppointmentIfMatches("7", "1", REQUEST.createdAtMillis, beforeDelete = { throw denied })
            }.exceptionOrNull()
            assertEquals(denied, failure)
        }
        assertEquals(listOf("GET /api/v1/appointments/7"), routes.toList())
    }

    @Test
    fun completingAConfirmedAppointmentRecoversTheSameAppointmentAfterAnAmbiguousPut() {
        val confirmed = EXISTING_APPOINTMENT.replace("PENDING", "CONFIRMED")
        val saved = AtomicReference(confirmed)
        val updateBody = AtomicReference<String>()
        val routes = ConcurrentLinkedQueue<String>()
        val completedAt = 1_800_003_600_000L
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
            val expected = target.copy(status = "COMPLETED", updatedAtMillis = completedAt)
            val result = api.submitAppointmentStatus(target, MockAppointmentStatusDto("COMPLETED", completedAt))
            assertEquals(expected, result)
            assertEquals(expected, newApi().listAppointments(target.profileId).single())
        }
        assertEquals(
            setOf("status", "updatedAtMillis"),
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
    fun completingAnAppointmentRequiresTheExpectedTimestampToReconcileAnAmbiguousPut() {
        val confirmed = EXISTING_APPOINTMENT.replace("PENDING", "CONFIRMED")
        val saved = AtomicReference(confirmed)
        withAppointmentEndpoint(
            headers = mapOf("Retry-After" to "0"),
            respond = { route, _ ->
                when (route) {
                    "GET /api/v1/appointments/7" -> 200 to saved.get()
                    "PUT /api/v1/appointments/7" -> {
                        saved.set(applyUpdate(confirmed, """{"status":"COMPLETED","updatedAtMillis":1800001800000}"""))
                        503 to "\"Response lost\""
                    }
                    else -> 500 to "\"Unexpected route\""
                }
            }
        ) { newApi ->
            val api = newApi()
            val target = api.getAppointment("7")
            val failure = runCatching {
                api.submitAppointmentStatus(target, MockAppointmentStatusDto("COMPLETED", 1_800_003_600_000))
            }.exceptionOrNull()
            assertEquals(503, (failure as? HttpException)?.code())
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
