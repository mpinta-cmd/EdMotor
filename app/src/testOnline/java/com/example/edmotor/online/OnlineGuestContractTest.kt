package com.example.edmotor.online

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineGuestContractTest {
    @Test
    fun guestGetsCatalogAndProductImageWithoutAnAuthorizationHeader() {
        val imageId = "ded49e79-4dee-448d-a01e-b2651244f460"
        val imagePath = "/api/v1/products/images/$imageId"
        val productsPath = "GET /api/v1/products"
        val imageRoute = "GET $imagePath"
        val products = """
            [{
                "id": 1, "version": 1, "name": "Filtro de aceite", "description": "Filtro de prueba",
                "category": "Filtros", "priceCop": 25000, "stock": 5, "imagePath": "$imagePath"
            }]
        """.trimIndent()

        withApi(
            mapOf(
                productsPath to Reply(products),
                imageRoute to Reply("imagen-de-prueba", contentType = "image/webp")
            )
        ) { api, requests ->
            val product = api.products().single()
            assertEquals("Filtro de aceite", product.name)
            assertEquals(imagePath, product.imagePath)
            api.productImage(null, imageId).use { image ->
                assertEquals("image/webp", image.contentType().toString())
                assertEquals("imagen-de-prueba", image.string())
            }

            assertEquals(listOf(productsPath, imageRoute), requests.map { it.route })
            requests.forEach { assertNull(it.authorization) }
        }
    }

    @Test
    fun ownerActivationUsesCamelCaseAndReturnsTheWorkshopRole() {
        val registrationRoute = "POST /api/v1/auth/register-owner"
        val registrationResponse = """
            {
                "token": "token-de-prueba",
                "user": {"id": 1, "username": "edmotor", "displayName": "Taller EdMotor", "role": "WORKSHOP"}
            }
        """.trimIndent()

        withApi(mapOf(registrationRoute to Reply(registrationResponse, status = 201))) { api, requests ->
            val response = api.registerOwner(
                OnlineOwnerRegistration("edmotor", "Taller EdMotor", "clave-de-prueba", "codigo-de-prueba")
            )

            assertEquals("WORKSHOP", response.user.role)
            assertEquals("token-de-prueba", response.token)
            val request = requests.single()
            assertEquals(registrationRoute, request.route)
            assertNull(request.authorization)
            val expected = JsonParser.parseString(
                """
                    {
                        "username": "edmotor", "displayName": "Taller EdMotor",
                        "password": "clave-de-prueba", "activationCode": "codigo-de-prueba"
                    }
                """.trimIndent()
            )
            assertEquals(expected, JsonParser.parseString(request.body))
        }
    }

    private fun withApi(
        replies: Map<String, Reply>,
        test: suspend (OnlineService, Collection<ObservedRequest>) -> Unit
    ) {
        val requests = ConcurrentLinkedQueue<ObservedRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/") { exchange ->
            val route = "${exchange.requestMethod} ${exchange.requestURI}"
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            requests.add(ObservedRequest(route, exchange.requestHeaders.getFirst("Authorization"), body))
            val reply = replies[route] ?: Reply("{}", status = 404)
            val payload = reply.body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", reply.contentType)
            exchange.sendResponseHeaders(reply.status, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()

        try {
            val api = requireNotNull(OnlineApi.fromUrl("http://127.0.0.1:${server.address.port}", debug = true))
            runBlocking { test(api.service, requests) }
        } finally {
            server.stop(0)
        }
    }

    private data class ObservedRequest(val route: String, val authorization: String?, val body: String)

    private data class Reply(val body: String, val status: Int = 200, val contentType: String = "application/json")
}
