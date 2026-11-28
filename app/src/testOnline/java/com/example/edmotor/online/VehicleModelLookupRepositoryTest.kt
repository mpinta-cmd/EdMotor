package com.example.edmotor.online

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class VehicleModelLookupRepositoryTest {
    @Test
    fun encodedLookupSelectsTheExactMakeWithoutSendingAuthorizationOrCookies() {
        val requests = ConcurrentLinkedQueue<ObservedRequest>()
        withServer(handler = { exchange ->
            requests.add(ObservedRequest(
                exchange.requestMethod,
                exchange.requestURI.toString(),
                exchange.requestHeaders.getFirst("Authorization"),
                exchange.requestHeaders.getFirst("Cookie")
            ))
            exchange.responseHeaders.add("Set-Cookie", "provider=fixture; Path=/")
            exchange.reply(200, """
                {
                    "Count": 8,
                    "Results": [
                        {"Make_Name":" LAND   ROVER ", "Model_Name":" Range Rover "},
                        {"Make_Name":"LAND ROVER", "Model_Name":"Defender"},
                        {"Make_Name":"land rover", "Model_Name":"defender"},
                        {"Make_Name":"LAND ROVER SPECIAL", "Model_Name":"Excluded"},
                        {"Make_Name":"LAND ROVER", "Model_Name":""},
                        {"Make_Name":"LAND ROVER", "Model_Name":null},
                        {"Model_Name":"Unknown make"},
                        null
                    ]
                }
            """.trimIndent())
        }) { repository ->
            repeat(2) {
                assertEquals(
                    listOf(
                        VehicleModelSuggestion("LAND ROVER", "Defender"),
                        VehicleModelSuggestion("LAND ROVER", "Range Rover")
                    ),
                    runBlocking { repository.findModels("  land   rover ", 2021) }
                )
            }
            val request = ObservedRequest(
                "GET", "/api/vehicles/GetModelsForMakeYear/make/land%20rover/modelyear/2021?format=json", null, null
            )
            assertEquals(listOf(request, request), requests.toList())
        }
    }

    @Test
    fun emptyCatalogAndIncompletePayloadAreDifferentOutcomes() {
        withServer(handler = { exchange ->
            val body = when {
                "/renault/" in exchange.requestURI.path -> """{"Count":0,"Results":[]}"""
                "/missing/" in exchange.requestURI.path -> """{"Count":0}"""
                else -> """{"Count":3,"Results":[]}"""
            }
            exchange.reply(200, body)
        }) { repository ->
            assertTrue(runBlocking { repository.findModels("renault", 2021) }.isEmpty())
            assertThrows(IOException::class.java) { runBlocking { repository.findModels("missing", 2021) } }
            assertThrows(IOException::class.java) { runBlocking { repository.findModels("mismatch", 2021) } }
        }
    }

    @Test
    fun providerErrorsAreReportedWithoutFollowingRedirects() {
        val paths = ConcurrentLinkedQueue<String>()
        withServer(handler = { exchange ->
            val path = exchange.requestURI.path
            paths.add(path)
            if ("/redirect/" in path) {
                exchange.responseHeaders.add("Location", "/destination")
                exchange.reply(302, "")
            } else {
                exchange.reply(503, "{}")
            }
        }) { repository ->
            val redirect = assertThrows(HttpException::class.java) {
                runBlocking { repository.findModels("redirect", 2021) }
            }
            val unavailable = assertThrows(HttpException::class.java) {
                runBlocking { repository.findModels("unavailable", 2021) }
            }
            assertEquals(302, redirect.code())
            assertEquals(503, unavailable.code())
            assertEquals(listOf(
                "/api/vehicles/GetModelsForMakeYear/make/redirect/modelyear/2021",
                "/api/vehicles/GetModelsForMakeYear/make/unavailable/modelyear/2021"
            ), paths.toList())
        }
    }

    @Test
    fun invalidLookupDoesNotContactTheProvider() {
        val requests = ConcurrentLinkedQueue<String>()
        withServer(handler = { exchange ->
            requests.add(exchange.requestURI.path)
            exchange.reply(200, """{"Count":0,"Results":[]}""")
        }) { repository ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.findModels("   ", 2021) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.findModels("x".repeat(81), 2021) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.findModels("Toyota", 1995) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.findModels("Toyota", 10000) }
            }
            assertTrue(requests.isEmpty())
        }
    }

    private fun withServer(handler: (HttpExchange) -> Unit, test: (VehicleModelLookupRepository) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            test(VehicleModelLookupRepository.fromUrl("http://127.0.0.1:${server.address.port}/api/"))
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.reply(code: Int, json: String) {
        val payload = json.toByteArray(Charsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(code, payload.size.toLong())
        responseBody.use { it.write(payload) }
    }

    private data class ObservedRequest(
        val method: String,
        val target: String,
        val authorization: String?,
        val cookie: String?
    )
}
