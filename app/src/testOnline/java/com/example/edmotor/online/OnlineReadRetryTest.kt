package com.example.edmotor.online

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class OnlineReadRetryTest {
    @Test
    fun catalogRecoversAfterConnectionClosesBeforeResponseHeaders() = withApi(
        handler = { exchange, attempt ->
            if (attempt > 1) exchange.respond(200, "[]")
        }
    ) { api, requests ->
        assertTrue(api.products().isEmpty())
        assertEquals(List(2) { "GET /api/v1/products" }, requests.map { it.route })
    }

    @Test
    fun reconciliationRetriesTransientServiceUnavailableWithSameAuthentication() = withApi(
        handler = { exchange, attempt ->
            if (attempt == 1) {
                exchange.respond(503)
            } else {
                exchange.respond(
                    200,
                    """{"id":42,"status":"PENDING","clientName":"Cliente","totalCop":1000,"createdAtMillis":1,"items":[]}"""
                )
            }
        }
    ) { api, requests ->
        assertEquals(42L, api.orderByRequest(AUTH, REQUEST_ID).id)
        assertEquals(List(2) { "GET /api/v1/orders/requests/$REQUEST_ID" }, requests.map { it.route })
        assertEquals(List(2) { AUTH }, requests.map { it.authorization })
    }

    @Test
    fun persistentUnavailableResponseGetsOnlyOneRetry() = withApi(
        handler = { exchange, _ -> exchange.respond(503) }
    ) { api, requests ->
        val error = runCatching { api.products() }.exceptionOrNull() as HttpException
        assertEquals(503, error.code())
        assertEquals(2, requests.size)
    }

    @Test
    fun retryAfterInstructionsAreRespected() = withApi(
        handler = { exchange, _ ->
            exchange.responseHeaders.set("Retry-After", "30")
            exchange.respond(503)
        }
    ) { api, requests ->
        val error = runCatching { api.products() }.exceptionOrNull() as HttpException
        assertEquals(503, error.code())
        assertEquals("30", error.response()?.headers()?.get("Retry-After"))
        assertEquals(1, requests.size)
    }

    @Test
    fun absentOrderIsNotRetried() = withApi(
        handler = { exchange, _ -> exchange.respond(404) }
    ) { api, requests ->
        val error = runCatching { api.orderByRequest(AUTH, REQUEST_ID) }.exceptionOrNull() as HttpException
        assertEquals(404, error.code())
        assertEquals(1, requests.size)
    }

    @Test
    fun losingTheOrderResponseDoesNotAutomaticallyRepeatTheWrite() = withApi(
        handler = { _, _ -> }
    ) { api, requests ->
        val error = runCatching { api.createOrder(AUTH, order()) }.exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals(listOf("POST /api/v1/orders"), requests.map { it.route })
    }

    @Test
    fun losingCheckoutResponseReplaysTheSamePurchaseWithItsOriginalTotal() = withApi(
        handler = { exchange, attempt ->
            if (attempt == 2) {
                exchange.respond(
                    201,
                    """{"id":47,"status":"PENDING","clientName":"Cliente","totalCop":18500,"createdAtMillis":1,"items":[],"paymentMethod":"PAY_ON_PICKUP"}"""
                )
            }
        }
    ) { api, requests ->
        val confirmed = api.checkout(AUTH, OnlineCheckoutWrite(REQUEST_ID, listOf(OnlineOrderLine(1, 1)), 18_500L))

        assertEquals(47L, confirmed.id)
        assertEquals("PAY_ON_PICKUP", confirmed.paymentMethod)
        assertEquals(List(2) { "POST /api/v1/orders/checkout" }, requests.map { it.route })
        assertEquals(List(2) { AUTH }, requests.map { it.authorization })
        val payload = JsonParser.parseString(
            """{"requestId":"$REQUEST_ID","items":[{"productId":1,"quantity":1}],"expectedTotalCop":18500}"""
        )
        assertEquals(List(2) { payload }, requests.map { JsonParser.parseString(it.body) })
    }

    @Test
    fun writeStaysOneShotEvenWhenTheServerSuggestsAnImmediateRetry() = withApi(
        handler = { exchange, _ ->
            exchange.responseHeaders.set("Retry-After", "0")
            exchange.respond(503)
        }
    ) { api, requests ->
        val error = runCatching { api.createOrder(AUTH, order()) }.exceptionOrNull() as HttpException
        assertEquals(503, error.code())
        assertEquals(listOf("POST /api/v1/orders"), requests.map { it.route })
    }

    private fun order() = OnlineOrderWrite(REQUEST_ID, listOf(OnlineOrderLine(1, 1)))

    private fun withApi(
        handler: (HttpExchange, Int) -> Unit,
        verify: suspend (OnlineService, Collection<ObservedRequest>) -> Unit
    ) {
        val requests = ConcurrentLinkedQueue<ObservedRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/") { exchange ->
            try {
                val body = exchange.requestBody.bufferedReader().use { it.readText() }
                requests.add(
                    ObservedRequest(
                        "${exchange.requestMethod} ${exchange.requestURI}",
                        exchange.requestHeaders.getFirst("Authorization"),
                        body
                    )
                )
                handler(exchange, requests.size)
            } finally {
                exchange.close()
            }
        }
        server.start()

        try {
            val api = requireNotNull(OnlineApi.fromUrl("http://127.0.0.1:${server.address.port}", debug = true))
            runBlocking { verify(api.service, requests) }
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(status: Int, json: String = "{}") {
        val payload = json.toByteArray(Charsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(status, payload.size.toLong())
        responseBody.use { it.write(payload) }
    }

    private data class ObservedRequest(val route: String, val authorization: String?, val body: String)

    private companion object {
        const val AUTH = "Bearer prueba"
        const val REQUEST_ID = "10e66a0c-0c71-4a0a-98aa-49c86b3fa58c"
    }
}
