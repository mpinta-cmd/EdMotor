"""Ejecutar contra una instancia de prueba local con una cuenta de dueño creada."""

import base64
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
import json
import os
import unittest
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen
from uuid import uuid4
from zoneinfo import ZoneInfo


class SharedWorkflows(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        endpoint = os.environ.get("EDMOTOR_TEST_URL", "")
        owner_password = os.environ.get("EDMOTOR_TEST_OWNER_PASSWORD", "")
        if not endpoint or not owner_password:
            raise unittest.SkipTest("Define EDMOTOR_TEST_URL y EDMOTOR_TEST_OWNER_PASSWORD.")
        if urlsplit(endpoint).hostname not in {"localhost", "127.0.0.1"}:
            raise ValueError("Estas pruebas requieren una instancia desechable local.")
        cls.base_url = endpoint.rstrip("/")
        owner_login = cls.call("POST", "/auth/login", expected=200, body={
            "username": os.environ.get("EDMOTOR_TEST_OWNER_USERNAME", "edmotor"),
            "password": owner_password,
        })
        assert owner_login["user"]["role"] == "WORKSHOP"
        cls.owner = owner_login["token"]

    @classmethod
    def call(cls, method, path, token=None, *, body=None, expected=200, raw=None, content_type=None,
             expected_header=None):
        headers = {"Accept": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        if body is not None:
            raw = json.dumps(body).encode()
            content_type = "application/json"
        if content_type:
            headers["Content-Type"] = content_type
        request = Request(cls.base_url + path, data=raw, method=method, headers=headers)
        try:
            response = urlopen(request, timeout=15)
        except HTTPError as error:
            response = error
        with response:
            status = response.code
            data = response.read()
            mime = response.headers.get_content_type()
            if expected_header:
                name, value = expected_header
                if response.headers.get(name) != value:
                    raise AssertionError(f"{method} {path}: se esperaba {name}={value}")
        allowed = (expected,) if isinstance(expected, int) else expected
        if status not in allowed:
            raise AssertionError(f"{method} {path}: HTTP {status} en vez de {allowed} ({data[:250]!r})")
        result = json.loads(data) if data and mime == "application/json" else data
        return (status, result) if len(allowed) > 1 else result

    def client(self):
        password = uuid4().hex
        credentials = {"username": "prueba_" + uuid4().hex[:10], "password": password}
        registration = self.call("POST", "/auth/register", body={
            **credentials, "displayName": "Cliente de prueba",
        }, expected=201)
        self.assertEqual(registration["user"]["role"], "CLIENT")
        return registration["token"], credentials

    def checkout_product(self, stock):
        return self.call("POST", "/products", self.owner, body={
            "name": "Repuesto de prueba " + uuid4().hex[:8], "description": "Prueba de compra en taller",
            "category": "Repuestos", "priceCop": 20_000, "promotionPriceCop": 15_000, "stock": stock,
        }, expected=201)

    def catalog_stock(self, *product_ids):
        return {row["id"]: row["stock"] for row in self.call("GET", "/products") if row["id"] in product_ids}

    def pickup_order(self, client, product_id):
        return self.call("POST", "/orders/checkout", client, body={
            "requestId": str(uuid4()), "items": [{"productId": product_id, "quantity": 1}],
            "expectedTotalCop": 15_000,
        }, expected=201)

    def deliver_pickup(self, order):
        path = f"/orders/{order['id']}/decision"
        self.call("POST", path, self.owner, body={"status": "CONFIRMED"})
        self.call("POST", path, self.owner, body={"status": "COMPLETED"})

    def test_appointments_are_shared_and_changes_are_versioned(self):
        client, credentials = self.client()
        second_device = self.call("POST", "/auth/login", body=credentials)["token"]
        self.assertNotEqual(second_device, client)
        other, _ = self.client()
        vehicle_data = {"brand": "Marca", "model": "Modelo", "year": 2020, "plate": "PRU123"}
        vehicle = self.call("POST", "/vehicles", client, body=vehicle_data, expected=201)
        other_vehicle = self.call("POST", "/vehicles", other, body=vehicle_data, expected=201)
        self.assertEqual(self.call("GET", "/vehicles", second_device), [vehicle])
        self.assertIn(vehicle, self.call("GET", "/vehicles", self.owner))

        tomorrow = (datetime.now(ZoneInfo("America/Bogota")) + timedelta(days=1)).replace(
            hour=10, minute=0, second=0, microsecond=0
        )
        scheduled = int(tomorrow.timestamp() * 1000)
        payload = {
            "requestId": str(uuid4()), "vehicleId": vehicle["id"],
            "scheduledAtMillis": scheduled, "serviceType": "Revisión general",
        }
        self.call("GET", "/appointments", expected=401)
        self.call("POST", "/appointments", client, body={
            **payload, "scheduledAtMillis": scheduled + 60_000,
        }, expected=422)
        self.call("POST", "/appointments", other, body=payload, expected=404)
        sibling = {**payload, "requestId": str(uuid4())}
        with ThreadPoolExecutor(max_workers=2) as pool:
            first = pool.submit(self.call, "POST", "/appointments", client, body=payload, expected=(201, 409))
            second = pool.submit(self.call, "POST", "/appointments", second_device, body=sibling, expected=(201, 409))
            results = [first.result(), second.result()]
        self.assertCountEqual([status for status, _ in results], [201, 409])
        index = next(index for index, (status, _) in enumerate(results) if status == 201)
        appointment = results[index][1]
        booked_payload = (payload, sibling)[index]
        self.assertEqual(self.call("POST", "/appointments", second_device, body=booked_payload, expected=201), appointment)
        other_booking = self.call("POST", "/appointments", other, body={
            **payload, "requestId": str(uuid4()), "vehicleId": other_vehicle["id"],
        }, expected=201)
        self.assertEqual(appointment["scheduledAtMillis"], scheduled)
        self.assertEqual(appointment["status"], "PENDING")
        self.assertEqual(self.call("GET", "/appointments", client), [appointment])
        self.assertEqual(self.call("GET", "/appointments", other), [other_booking])

        path = f"/appointments/{appointment['id']}"
        version = appointment["version"]
        reschedule = {"version": version, "action": "RESCHEDULE", "scheduledAtMillis": scheduled + 1_800_000}
        self.call("DELETE", f"{path}?version={version}", other, expected=404)
        self.call("PATCH", path, other, body=reschedule, expected=404)
        moved = self.call("PATCH", path, second_device, body=reschedule)
        self.assertEqual(moved["id"], appointment["id"])
        self.assertEqual(moved["scheduledAtMillis"], reschedule["scheduledAtMillis"])
        self.assertEqual(moved["status"], "PENDING")
        self.assertEqual(moved["version"], version + 1)
        self.assertEqual(self.call("GET", "/appointments", client), [moved])
        owner_bookings = self.call("GET", "/appointments", self.owner)
        ids = {appointment["id"], other_booking["id"]}
        self.assertEqual([booking for booking in owner_bookings if booking["id"] in ids], [other_booking, moved])
        self.call("PATCH", path, self.owner, body={"version": version, "action": "CONFIRM"}, expected=409)
        self.call("DELETE", f"{path}?version={version}", client, expected=409)
        confirmed = self.call("PATCH", path, self.owner, body={"version": moved["version"], "action": "CONFIRM"})
        self.assertEqual(self.call("GET", "/appointments", client), [confirmed])
        self.call("PATCH", path, self.owner, body={"version": confirmed["version"], "action": "FINISH"}, expected=409)
        delete_path = f"{path}?version={confirmed['version']}"
        self.assertEqual(self.call("DELETE", delete_path, client, expected=204), b"")
        self.assertEqual(self.call("GET", "/appointments", client), [])
        new_session = self.call("POST", "/auth/login", body=credentials)["token"]
        self.assertEqual(self.call("GET", "/appointments", new_session), [])
        self.call("DELETE", delete_path, self.owner, expected=404)
        self.assertEqual(self.call("GET", "/appointments", other), [other_booking])
        self.call("POST", "/appointments", client, body=booked_payload, expected=410)

        self.call("POST", "/auth/logout", second_device, expected=204)
        self.call("GET", "/auth/me", second_device, expected=401)
        self.assertEqual(self.call("GET", "/auth/me", client)["role"], "CLIENT")

    def test_catalog_orders_photos_and_stock_are_shared(self):
        first, _ = self.client()
        second, _ = self.client()
        png = base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII="
        )
        boundary = "EdMotorFixture" + uuid4().hex
        multipart = (
            f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"prueba.png\"\r\n"
            "Content-Type: image/png\r\n\r\n"
        ).encode() + png + f"\r\n--{boundary}--\r\n".encode()
        image = self.call("POST", "/products/images", self.owner, raw=multipart,
                          content_type=f"multipart/form-data; boundary={boundary}", expected=201)
        image_path = image["imagePath"].removeprefix("/api/v1")
        self.assertTrue(self.call("GET", image_path, self.owner).startswith(b"RIFF"))
        self.call("GET", image_path, expected=404)
        product_data = {
            "name": "Filtro de prueba " + uuid4().hex[:8], "description": "Repuesto de prueba",
            "category": "Filtros", "priceCop": 65_000, "promotionPriceCop": 55_000,
            "stock": 1, "imagePath": image["imagePath"],
        }
        self.call("POST", "/products", first, body=product_data, expected=403)
        self.call("POST", "/products", body=product_data, expected=401)
        product = self.call("POST", "/products", self.owner, body=product_data, expected=201)
        self.assertIn(product, self.call("GET", "/products", first))
        self.assertIn(product, self.call("GET", "/products"))
        self.assertTrue(self.call("GET", image_path, first).startswith(b"RIFF"))
        self.assertTrue(self.call("GET", image_path).startswith(b"RIFF"))

        order_data = {"requestId": str(uuid4()), "items": [{"productId": product["id"], "quantity": 1}]}
        request_path = f"/orders/requests/{order_data['requestId']}"
        self.call("POST", "/orders", body=order_data, expected=401)
        self.call("GET", "/orders", expected=401)
        self.call("GET", request_path, expected=401)
        self.call("GET", request_path, first, expected=404)
        first_order = self.call("POST", "/orders", first, body=order_data, expected=201)
        self.assertEqual(self.call("GET", request_path, first), first_order)
        self.call("GET", request_path, second, expected=404)
        self.call("GET", request_path, self.owner, expected=404)
        self.assertEqual(first_order, self.call("POST", "/orders", first, body=order_data, expected=201))
        second_order = self.call("POST", "/orders", second, body={**order_data, "requestId": str(uuid4())}, expected=201)
        self.assertEqual(self.call("GET", "/orders", first), [first_order])
        self.assertEqual(self.call("GET", "/orders", second), [second_order])
        self.assertEqual(first_order["totalCop"], 55_000)
        self.assertIn(first_order, self.call("GET", "/orders", self.owner))
        decision = {"status": "CONFIRMED"}
        with ThreadPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(self.call, "POST", f"/orders/{order['id']}/decision", self.owner,
                                   body=decision, expected=(200, 409)) for order in (first_order, second_order)]
            results = [future.result() for future in futures]
        self.assertCountEqual([status for status, _ in results], [200, 409])
        confirmed_order = next(order for status, order in results if status == 200)
        self.assertEqual(self.call("POST", f"/orders/{confirmed_order['id']}/decision", self.owner,
                                   body=decision), confirmed_order)
        latest = next(item for item in self.call("GET", "/products", second) if item["id"] == product["id"])
        self.assertEqual(latest["stock"], 0)
        self.assertEqual(confirmed_order.pop("inventoryUpdates"), [latest])
        self.assertIsNone(confirmed_order["paymentMethod"])
        self.call("POST", f"/orders/{confirmed_order['id']}/decision", self.owner,
                  body={"status": "COMPLETED"}, expected=409)
        with self.assertRaises(HTTPError) as rejection:
            urlopen(Request(
                self.base_url + "/orders", method="POST",
                headers={"Content-Type": "application/json", "Authorization": "Bearer " + first},
                data=json.dumps({**order_data, "requestId": str(uuid4())}).encode(),
            ), timeout=15)
        with rejection.exception as response:
            self.assertEqual(response.code, 409)
            self.assertEqual(response.headers.get("EdMotor-Order-Result"), "rejected")
        replay = self.call("GET", request_path, first)
        self.assertEqual(replay["id"], first_order["id"])
        path = f"/products/{product['id']}"
        self.call("PUT", path, self.owner, body={**product_data, "version": product["version"]}, expected=409)
        self.call("DELETE", f"{path}?version={latest['version']}", self.owner, expected=204)
        self.assertNotIn(product["id"], [item["id"] for item in self.call("GET", "/products", first)])
        self.assertNotIn(product["id"], [item["id"] for item in self.call("GET", "/products")])
        self.call("GET", image_path, expected=404)
        self.assertIn(confirmed_order, self.call("GET", "/orders", self.owner))

    def test_checkout_uses_confirmed_price_and_reserves_stock_once(self):
        client, _ = self.client()
        product = self.checkout_product(stock=3)
        product_id = product["id"]
        body = {
            "requestId": str(uuid4()), "items": [{"productId": product_id, "quantity": 2}],
            "expectedTotalCop": 30_000,
        }
        request_path = f"/orders/requests/{body['requestId']}"
        self.call("POST", "/orders/checkout", body=body, expected=401)
        self.call("POST", "/orders/checkout", self.owner, body=body, expected=403)
        self.call("POST", "/orders/checkout", client, body={**body, "expectedTotalCop": 29_999},
                  expected=409, expected_header=("EdMotor-Order-Result", "rejected"))
        self.call("GET", request_path, client, expected=404)
        self.assertEqual(self.catalog_stock(product_id), {product_id: 3})

        with ThreadPoolExecutor(max_workers=2) as pool:
            purchases = [pool.submit(self.call, "POST", "/orders/checkout", client, body=body, expected=201)
                         for _ in range(2)]
        order, retry = [purchase.result() for purchase in purchases]
        self.assertEqual(order, retry)
        self.assertEqual(order["paymentMethod"], "PAY_ON_PICKUP")
        self.assertEqual(order["status"], "PENDING")
        self.assertEqual(order["totalCop"], 30_000)
        self.assertEqual(order["items"][0]["unitPriceCop"], 15_000)
        self.assertEqual(order["inventoryUpdates"], [
            {**product, "stock": 1, "version": product["version"] + 1},
        ])
        self.assertEqual(self.call("GET", request_path, client), order)
        self.assertEqual(self.catalog_stock(product_id), {product_id: 1})
        legacy_retry = self.call("POST", "/orders", client, body={
            "requestId": body["requestId"], "items": body["items"],
        }, expected=201)
        self.assertEqual(legacy_retry["id"], order["id"])
        self.call("POST", "/orders/checkout", client, body={**body, "expectedTotalCop": 29_999}, expected=409)

        decision_path = f"/orders/{order['id']}/decision"
        self.call("POST", decision_path, self.owner, body={"status": "COMPLETED"}, expected=409)
        ready = self.call("POST", decision_path, self.owner, body={"status": "CONFIRMED"})
        self.assertEqual(ready["status"], "CONFIRMED")
        self.call("POST", decision_path, client, body={"status": "COMPLETED"}, expected=403)
        completed = self.call("POST", decision_path, self.owner, body={"status": "COMPLETED"})
        self.assertEqual(completed["status"], "COMPLETED")
        self.assertEqual(self.call("POST", decision_path, self.owner, body={"status": "COMPLETED"}), completed)
        self.call("POST", decision_path, self.owner, body={"status": "REJECTED"}, expected=409)
        self.assertEqual(self.call("GET", "/orders", client), [completed])
        self.assertEqual(self.catalog_stock(product_id), {product_id: 1})

    def test_checkout_stock_conflict_is_atomic_and_rejection_restores_units_once(self):
        first, _ = self.client()
        second, _ = self.client()
        scarce_id = self.checkout_product(stock=1)["id"]
        ample_id = self.checkout_product(stock=3)["id"]
        bodies = [{
            "requestId": str(uuid4()), "expectedTotalCop": 45_000,
            "items": [{"productId": ample_id, "quantity": 2}, {"productId": scarce_id, "quantity": 1}],
        } for _ in range(2)]

        with ThreadPoolExecutor(max_workers=2) as pool:
            purchases = [pool.submit(self.call, "POST", "/orders/checkout", token, body=body, expected=(201, 409))
                         for token, body in zip((first, second), bodies)]
        results = [purchase.result() for purchase in purchases]
        self.assertCountEqual([status for status, _ in results], [201, 409])
        order = next(value for status, value in results if status == 201)
        self.assertEqual(self.catalog_stock(scarce_id, ample_id), {scarce_id: 0, ample_id: 1})
        for (status, _), client, body in zip(results, (first, second), bodies):
            if status == 409:
                self.call("GET", f"/orders/requests/{body['requestId']}", client, expected=404)

        # Inactive items must return to inventory too, while staying hidden from the public catalog.
        scarce = next(product for product in order["inventoryUpdates"] if product["id"] == scarce_id)
        self.call("DELETE", f"/products/{scarce_id}?version={scarce['version']}", self.owner, expected=204)
        decision_path = f"/orders/{order['id']}/decision"
        decision = {"status": "REJECTED"}
        with ThreadPoolExecutor(max_workers=2) as pool:
            decisions = [pool.submit(self.call, "POST", decision_path, self.owner, body=decision)
                         for _ in range(2)]
        rejected, retry = [result.result() for result in decisions]
        self.assertEqual(rejected, retry)
        self.assertEqual(rejected["status"], "REJECTED")
        self.assertEqual({row["id"]: row["stock"] for row in rejected["inventoryUpdates"]}, {ample_id: 3})
        self.assertEqual(self.catalog_stock(scarce_id, ample_id), {ample_id: 3})

    def test_rating_requires_an_owned_delivery_and_stays_available_for_inactive_products(self):
        client, _ = self.client()
        product = self.checkout_product(stock=3)
        product_id = product["id"]
        rating_path = f"/products/{product_id}/rating"
        eligible_path = "/ratings/eligible"
        self.call("GET", eligible_path, expected=401)
        self.call("GET", eligible_path, self.owner, expected=403)
        self.call("PUT", rating_path, body={"stars": 5}, expected=401)
        self.call("PUT", rating_path, self.owner, body={"stars": 5}, expected=403)

        order = self.pickup_order(client, product_id)
        self.assertEqual(self.call("GET", eligible_path, client), [])
        self.call("PUT", rating_path, client, body={"stars": 5}, expected=403)
        decision_path = f"/orders/{order['id']}/decision"
        self.call("POST", decision_path, self.owner, body={"status": "CONFIRMED"})
        self.assertEqual(self.call("GET", eligible_path, client), [])
        self.call("PUT", rating_path, client, body={"stars": 5}, expected=403)
        self.call("POST", decision_path, self.owner, body={"status": "COMPLETED"})

        eligible, = self.call("GET", eligible_path, client, expected_header=("Cache-Control", "private, no-store"))
        self.assertEqual(eligible, {
            "productId": product_id, "productName": product["name"], "imagePath": None,
            "deliveredAtMillis": eligible["deliveredAtMillis"], "myRating": None,
        })
        self.assertGreater(eligible["deliveredAtMillis"], 0)
        for invalid in (0, 6, "4", True, 4.5):
            with self.subTest(invalid=invalid):
                self.call("PUT", rating_path, client, body={"stars": invalid}, expected=422)
        rating = self.call("PUT", rating_path, client, body={"stars": 5})
        self.assertEqual(rating, {"productId": product_id, "myRating": 5, "averageRating": 5.0, "ratingCount": 1})

        self.deliver_pickup(self.pickup_order(client, product_id))
        second_delivery, = self.call("GET", eligible_path, client)
        self.assertEqual(second_delivery["myRating"], 5)
        self.assertGreaterEqual(second_delivery["deliveredAtMillis"], eligible["deliveredAtMillis"])

        current = next(row for row in self.call("GET", "/products") if row["id"] == product_id)
        self.call("DELETE", f"/products/{product_id}?version={current['version']}", self.owner, expected=204)
        self.assertNotIn(product_id, self.catalog_stock(product_id))
        archived, = self.call("GET", eligible_path, client)
        self.assertEqual(archived["productName"], product["name"])
        self.assertIsNone(archived["imagePath"])
        changed = self.call("PUT", rating_path, client, body={"stars": 4})
        self.assertEqual(changed, {"productId": product_id, "myRating": 4, "averageRating": 4.0, "ratingCount": 1})

    def test_catalog_and_inventory_follow_the_average_of_distinct_buyers(self):
        first, _ = self.client()
        second, _ = self.client()
        product = self.checkout_product(stock=4)
        product_id = product["id"]
        rating_path = f"/products/{product_id}/rating"
        self.assertIsNone(product["averageRating"])
        self.assertEqual(product["ratingCount"], 0)
        self.deliver_pickup(self.pickup_order(first, product_id))
        self.assertEqual(self.call("GET", "/ratings/eligible", second), [])
        self.call("PUT", rating_path, second, body={"stars": 5}, expected=403)
        self.deliver_pickup(self.pickup_order(second, product_id))
        with ThreadPoolExecutor(max_workers=2) as pool:
            first_vote = pool.submit(self.call, "PUT", rating_path, first, body={"stars": 5})
            second_vote = pool.submit(self.call, "PUT", rating_path, second, body={"stars": 3})
        votes = [future.result() for future in (first_vote, second_vote)]
        self.assertCountEqual([vote["ratingCount"] for vote in votes], [1, 2])
        self.assertEqual(next(vote for vote in votes if vote["ratingCount"] == 2)["averageRating"], 4.0)
        self.assertEqual(self.call("GET", "/ratings/eligible", first)[0]["myRating"], 5)
        self.assertEqual(self.call("GET", "/ratings/eligible", second)[0]["myRating"], 3)

        modified = self.call("PUT", rating_path, first, body={"stars": 4})
        self.assertEqual(modified, {"productId": product_id, "myRating": 4, "averageRating": 3.5, "ratingCount": 2})
        self.assertEqual(self.call("PUT", rating_path, first, body={"stars": 4}), modified)
        inventory, = self.pickup_order(first, product_id)["inventoryUpdates"]
        self.assertEqual((inventory["averageRating"], inventory["ratingCount"]), (3.5, 2))
        product_keys = ("name", "description", "category", "priceCop", "promotionPriceCop", "stock", "imagePath", "version")
        updated = self.call("PUT", f"/products/{product_id}", self.owner, body={
            **{key: inventory[key] for key in product_keys}, "stock": 2,
        })
        self.assertEqual((updated["averageRating"], updated["ratingCount"]), (3.5, 2))
        self.assertIn(updated, self.call("GET", "/products"))
