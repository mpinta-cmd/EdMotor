from collections import defaultdict
from hashlib import sha256
from io import BytesIO
from typing import Annotated, Literal
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Path, Query, Request, Response, status
from PIL import Image, ImageOps, UnidentifiedImageError
from psycopg import Connection
from psycopg.errors import UniqueViolation
from pydantic import BaseModel, ConfigDict, Field, StrictInt
from pydantic.alias_generators import to_camel
from python_multipart.exceptions import MultipartParseError
from starlette.concurrency import run_in_threadpool
from starlette.datastructures import UploadFile
from starlette.formparsers import MultiPartException, MultiPartParser

from .auth import Bearer, CLIENT, WORKSHOP, require_owner, require_user
from .db import get_db

router = APIRouter(tags=["inventario y pedidos"])

MAX_BIGINT = 2**63 - 1
MAX_STOCK = 2**31 - 1
MAX_IMAGE_BYTES = 5 * 1024 * 1024
MAX_IMAGE_PIXELS = 20_000_000
MAX_IMAGE_SIDE = 1800
MAX_UNATTACHED_IMAGES = 20
IMAGE_PREFIX = "/api/v1/products/images/"
SUPPORTED_IMAGE_FORMATS = {"JPEG", "PNG", "WEBP"}

Db = Annotated[Connection, Depends(get_db)]
User = Annotated[dict, Depends(require_user)]
Owner = Annotated[dict, Depends(require_owner)]
PositiveId = Annotated[int, Path(gt=0, le=MAX_BIGINT)]
PositiveLong = Annotated[StrictInt, Field(gt=0, le=MAX_BIGINT)]

_PRODUCT_FIELDS = "id, version, name, description, category, price_cop, promotion_price_cop, stock, image_id"
_ORDER_FIELDS = "id, status, client_name, total_cop, created_at, payment_method"
_ORDER_ITEM_FIELDS = "order_id, product_id, product_name, quantity, unit_price_cop"
_ORDER_REJECTED = {"EdMotor-Order-Result": "rejected"}
PAY_ON_PICKUP = "PAY_ON_PICKUP"


def require_rating_client(user: User) -> dict:
    if user["role"] != CLIENT:
        raise HTTPException(403, "Solo la cuenta de cliente puede calificar repuestos.")
    return user


RatingClient = Annotated[dict, Depends(require_rating_client)]


class CommerceBody(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, extra="forbid", str_strip_whitespace=True)


class ProductDraft(CommerceBody):
    name: str = Field(min_length=3, max_length=180)
    description: str = Field(min_length=1, max_length=4000)
    category: str = Field(min_length=2, max_length=100)
    price_cop: PositiveLong
    promotion_price_cop: PositiveLong | None = None
    stock: StrictInt = Field(ge=0, le=MAX_STOCK)
    image_path: str | None = Field(default=None, max_length=len(IMAGE_PREFIX) + 36)


class ProductEdit(ProductDraft):
    version: PositiveLong


class ProductRatingWrite(CommerceBody):
    stars: StrictInt = Field(ge=1, le=5)


class OrderLineRequest(CommerceBody):
    product_id: PositiveLong
    quantity: StrictInt = Field(gt=0, le=MAX_STOCK)


class OrderRequest(CommerceBody):
    request_id: UUID
    items: list[OrderLineRequest] = Field(min_length=1, max_length=100)


class CheckoutRequest(OrderRequest):
    expected_total_cop: PositiveLong


class OrderDecision(CommerceBody):
    status: Literal["CONFIRMED", "REJECTED", "COMPLETED"]


def product_dto(row: dict, rating: dict | None = None) -> dict:
    return {
        "id": row["id"], "version": row["version"], "name": row["name"],
        "description": row["description"], "category": row["category"],
        "priceCop": row["price_cop"], "promotionPriceCop": row["promotion_price_cop"],
        "stock": row["stock"],
        "imagePath": f"{IMAGE_PREFIX}{row['image_id']}" if row["image_id"] else None,
        "averageRating": rating["average_rating"] if rating else None,
        "ratingCount": rating["rating_count"] if rating else 0,
    }


def product_rating_totals(db: Connection, product_ids: list[int]) -> dict[int, dict]:
    rows = db.execute(
        """SELECT product_id, avg(stars)::double precision AS average_rating, count(*) AS rating_count
           FROM product_ratings WHERE product_id = ANY(%s::bigint[]) GROUP BY product_id""",
        (product_ids,),
    ).fetchall()
    return {row["product_id"]: row for row in rows}


def catalog_product_dtos(db: Connection, rows: list[dict]) -> list[dict]:
    if not rows:
        return []
    totals = product_rating_totals(db, [row["id"] for row in rows])
    return [product_dto(row, totals.get(row["id"])) for row in rows]


def product_values(db: Connection, body: ProductDraft) -> tuple:
    if body.promotion_price_cop is not None and body.promotion_price_cop >= body.price_cop:
        raise HTTPException(422, "El precio en promoción debe ser menor que el precio normal.")
    if any("\x00" in value for value in (body.name, body.description, body.category)):
        raise HTTPException(422, "Revisa los caracteres del repuesto.")
    image_id = None
    if body.image_path is not None:
        try:
            image_id = UUID(body.image_path.removeprefix(IMAGE_PREFIX))
            if body.image_path != f"{IMAGE_PREFIX}{image_id}":
                raise ValueError("La ruta de imagen debe ser del catálogo.")
        except ValueError as error:
            raise HTTPException(422, "Elige una imagen del catálogo o carga una nueva.") from error
        image = db.execute(
            "SELECT id FROM product_images WHERE id = %s FOR KEY SHARE", (image_id,)
        ).fetchone()
        if image is None:
            raise HTTPException(422, "La imagen ya no está disponible. Cárgala nuevamente.")
    return (
        body.name, body.description, body.category, body.price_cop,
        body.promotion_price_cop, body.stock, image_id,
    )


@router.get("/products")
def list_products(db: Db) -> list[dict]:
    rows = db.execute(
        f"""SELECT {_PRODUCT_FIELDS} FROM products WHERE is_active
            ORDER BY (promotion_price_cop IS NOT NULL) DESC, lower(name), id"""
    ).fetchall()
    return catalog_product_dtos(db, rows)


@router.post("/products", status_code=status.HTTP_201_CREATED)
def create_product(body: ProductDraft, db: Db, _owner: Owner) -> dict:
    try:
        with db.transaction():
            values = product_values(db, body)
            row = db.execute(
                f"""INSERT INTO products
                    (name, description, category, price_cop, promotion_price_cop, stock, image_id)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    RETURNING {_PRODUCT_FIELDS}""",
                values,
            ).fetchone()
    except UniqueViolation as error:
        raise HTTPException(409, "Ya hay un repuesto con ese nombre en la categoría.") from error
    return product_dto(row)


@router.put("/products/{product_id}")
def update_product(product_id: PositiveId, body: ProductEdit, db: Db, _owner: Owner) -> dict:
    try:
        with db.transaction():
            values = product_values(db, body)
            row = db.execute(
                f"""UPDATE products SET name = %s, description = %s, category = %s,
                    price_cop = %s, promotion_price_cop = %s, stock = %s, image_id = %s,
                    version = version + 1, updated_at = now()
                    WHERE id = %s AND is_active AND version = %s
                    RETURNING {_PRODUCT_FIELDS}""",
                (*values, product_id, body.version),
            ).fetchone()
            if row is None:
                product_write_conflict(db, product_id)
            rating = product_rating_totals(db, [product_id]).get(product_id)
    except UniqueViolation as error:
        raise HTTPException(409, "Ya hay un repuesto con ese nombre en la categoría.") from error
    return product_dto(row, rating)


@router.get("/ratings/eligible")
def list_eligible_ratings(db: Db, client: RatingClient, response: Response) -> list[dict]:
    rows = db.execute(
        """SELECT delivered.product_id, delivered.product_name, delivered.delivered_at,
                  product.image_id, rating.stars
           FROM (
               SELECT DISTINCT ON (item.product_id)
                      item.product_id, item.product_name, placed.reviewed_at AS delivered_at
               FROM orders placed
               JOIN order_items item ON item.order_id = placed.id
               WHERE placed.client_user_id = %s
                 AND placed.status = 'COMPLETED' AND placed.payment_method = 'PAY_ON_PICKUP'
               ORDER BY item.product_id, placed.reviewed_at DESC, placed.id DESC
           ) delivered
           LEFT JOIN products product ON product.id = delivered.product_id AND product.is_active
           LEFT JOIN product_ratings rating
             ON rating.product_id = delivered.product_id AND rating.client_user_id = %s
           ORDER BY delivered.delivered_at DESC, delivered.product_id""",
        (client["id"], client["id"]),
    ).fetchall()
    response.headers["Cache-Control"] = "private, no-store"
    return [
        {
            "productId": row["product_id"],
            "productName": row["product_name"],
            "imagePath": f"{IMAGE_PREFIX}{row['image_id']}" if row["image_id"] else None,
            "deliveredAtMillis": int(row["delivered_at"].timestamp() * 1000),
            "myRating": row["stars"],
        }
        for row in rows
    ]


@router.put("/products/{product_id}/rating")
def save_product_rating(
    product_id: PositiveId, body: ProductRatingWrite, db: Db, client: RatingClient, response: Response
) -> dict:
    with db.transaction():
        delivered = db.execute(
            """SELECT 1 FROM orders placed
               JOIN order_items item ON item.order_id = placed.id
               WHERE placed.client_user_id = %s AND item.product_id = %s
                 AND placed.status = 'COMPLETED' AND placed.payment_method = 'PAY_ON_PICKUP'
               LIMIT 1""",
            (client["id"], product_id),
        ).fetchone()
        if delivered is None:
            raise HTTPException(403, "Puedes calificar este repuesto cuando el pedido quede entregado a tu cuenta.")
        # Alternate ratings for a product finish in order so the reply includes prior committed votes.
        db.execute("SELECT id FROM products WHERE id = %s FOR NO KEY UPDATE", (product_id,)).fetchone()
        db.execute(
            """INSERT INTO product_ratings (product_id, client_user_id, stars)
               VALUES (%s, %s, %s)
               ON CONFLICT (product_id, client_user_id) DO UPDATE SET stars = EXCLUDED.stars""",
            (product_id, client["id"], body.stars),
        )
        rating = product_rating_totals(db, [product_id])[product_id]
    response.headers["Cache-Control"] = "private, no-store"
    return {
        "productId": product_id,
        "myRating": body.stars,
        "averageRating": rating["average_rating"],
        "ratingCount": rating["rating_count"],
    }


def product_write_conflict(db: Connection, product_id: int) -> None:
    row = db.execute("SELECT is_active FROM products WHERE id = %s", (product_id,)).fetchone()
    if row is None or not row["is_active"]:
        raise HTTPException(404, "El repuesto ya no está activo.")
    raise HTTPException(409, "El repuesto cambió. Actualiza el inventario y vuelve a intentarlo.")


@router.delete("/products/{product_id}", status_code=status.HTTP_204_NO_CONTENT)
def deactivate_product(
    product_id: PositiveId,
    version: Annotated[int, Query(gt=0, le=MAX_BIGINT)],
    db: Db,
    _owner: Owner,
) -> Response:
    with db.transaction():
        row = db.execute(
            """UPDATE products SET is_active = false, version = version + 1, updated_at = now()
               WHERE id = %s AND is_active AND version = %s RETURNING id""",
            (product_id, version),
        ).fetchone()
        if row is None:
            previous = db.execute(
                "SELECT is_active, version FROM products WHERE id = %s", (product_id,)
            ).fetchone()
            if previous is None or previous["is_active"] or previous["version"] != version + 1:
                product_write_conflict(db, product_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


class ImageUploadTooLarge(MultiPartException):
    pass


async def limited_image_body(request: Request):
    total_bytes = 0
    async for chunk in request.stream():
        total_bytes += len(chunk)
        if total_bytes > MAX_IMAGE_BYTES + 64 * 1024:
            raise ImageUploadTooLarge("La foto debe pesar como máximo 5 MB.")
        yield chunk


@router.post(
    "/products/images",
    status_code=status.HTTP_201_CREATED,
    openapi_extra={
        "requestBody": {
            "required": True,
            "content": {"multipart/form-data": {"schema": {
                "type": "object", "required": ["file"],
                "properties": {"file": {"type": "string", "format": "binary"}},
            }}},
        },
    },
)
async def upload_product_image(request: Request, db: Db, owner: Owner) -> dict:
    if request.headers.get("content-type", "").split(";", 1)[0].strip().lower() != "multipart/form-data":
        raise HTTPException(415, "Adjunta una foto en el campo file.")
    try:
        form = await MultiPartParser(
            request.headers, limited_image_body(request), max_files=1, max_fields=0
        ).parse()
    except ImageUploadTooLarge as error:
        raise HTTPException(413, error.message) from error
    except (MultiPartException, MultipartParseError) as error:
        raise HTTPException(400, "Adjunta una sola foto en el campo file.") from error
    try:
        file = form.get("file")
        if not isinstance(file, UploadFile):
            raise HTTPException(422, "Adjunta una foto en el campo file.")
        data = await file.read(MAX_IMAGE_BYTES + 1)
    finally:
        await form.close()
    return await run_in_threadpool(save_image, db, owner["id"], data)


def normalized_image(data: bytes) -> bytes:
    if len(data) > MAX_IMAGE_BYTES:
        raise HTTPException(413, "La foto debe pesar como máximo 5 MB.")
    try:
        with Image.open(BytesIO(data), formats=list(SUPPORTED_IMAGE_FORMATS)) as image:
            if image.width * image.height > MAX_IMAGE_PIXELS:
                raise HTTPException(413, "La foto supera los 20 megapíxeles.")
            if getattr(image, "is_animated", False):
                raise HTTPException(415, "Elige una foto sin animación.")
            normalized = ImageOps.exif_transpose(image)
            try:
                normalized.thumbnail((MAX_IMAGE_SIDE, MAX_IMAGE_SIDE), Image.Resampling.LANCZOS)
                with BytesIO() as output:
                    normalized.save(output, format="WEBP", quality=90)
                    return output.getvalue()
            finally:
                normalized.close()
    except Image.DecompressionBombError as error:
        raise HTTPException(413, "La foto supera los 20 megapíxeles.") from error
    except (UnidentifiedImageError, OSError, ValueError, SyntaxError) as error:
        raise HTTPException(415, "Elige una foto válida en formato JPEG, PNG o WebP.") from error


def save_image(db: Connection, owner_id: int, data: bytes) -> dict:
    data = normalized_image(data)
    if len(data) > MAX_IMAGE_BYTES:
        raise HTTPException(413, "La foto debe pesar como máximo 5 MB.")
    digest = sha256(data).digest()
    with db.transaction():
        db.execute("SELECT id FROM users WHERE id = %s FOR NO KEY UPDATE", (owner_id,)).fetchone()
        db.execute(
            """DELETE FROM product_images WHERE id IN (
                   SELECT im.id FROM product_images im
                   WHERE im.uploaded_by = %s AND im.created_at < now() - INTERVAL '24 hours'
                   AND NOT EXISTS (SELECT 1 FROM products p WHERE p.image_id = im.id)
                   FOR UPDATE OF im SKIP LOCKED
               )""",
            (owner_id,),
        )
        previous = db.execute(
            """UPDATE product_images SET created_at = now()
               WHERE uploaded_by = %s AND content_digest = %s RETURNING id""",
            (owner_id, digest),
        ).fetchone()
        if previous is not None:
            return {"imagePath": f"{IMAGE_PREFIX}{previous['id']}"}

        pending = db.execute(
            """SELECT count(*) AS count FROM product_images im
               WHERE im.uploaded_by = %s
               AND NOT EXISTS (SELECT 1 FROM products p WHERE p.image_id = im.id)""",
            (owner_id,),
        ).fetchone()
        if pending["count"] >= MAX_UNATTACHED_IMAGES:
            raise HTTPException(409, "Hay demasiadas fotos pendientes. Guarda los repuestos con las fotos cargadas.")
        image_id = uuid4()
        db.execute(
            "INSERT INTO product_images (id, uploaded_by, content_digest, data) VALUES (%s, %s, %s, %s)",
            (image_id, owner_id, digest, data),
        )
    return {"imagePath": f"{IMAGE_PREFIX}{image_id}"}


@router.get("/products/images/{image_id}", response_class=Response)
def product_image(image_id: UUID, db: Db, credentials: Bearer) -> Response:
    user = require_user(credentials, db) if credentials is not None else None
    can_preview = user is not None and user["role"] == WORKSHOP
    image = db.execute(
        """SELECT data FROM product_images im WHERE im.id = %s AND (
              %s OR EXISTS (SELECT 1 FROM products p WHERE p.image_id = im.id AND p.is_active)
           )""",
        (image_id, can_preview),
    ).fetchone()
    if image is None:
        raise HTTPException(404, "La imagen no está disponible.")
    return Response(
        content=bytes(image["data"]),
        media_type="image/webp",
        headers={
            "Cache-Control": "no-store" if can_preview else "private, max-age=300",
            "Vary": "Authorization",
            "X-Content-Type-Options": "nosniff",
        },
    )


def order_dto(row: dict, items: list[dict]) -> dict:
    return {
        "id": row["id"], "status": row["status"], "clientName": row["client_name"],
        "totalCop": row["total_cop"], "createdAtMillis": int(row["created_at"].timestamp() * 1000),
        "paymentMethod": row["payment_method"],
        "items": [
            {
                "productId": item["product_id"], "productName": item["product_name"],
                "quantity": item["quantity"], "unitPriceCop": item["unit_price_cop"],
            }
            for item in items
        ],
    }


def read_order_items(db: Connection, order_ids: list[int]) -> list[dict]:
    return db.execute(
        f"""SELECT {_ORDER_ITEM_FIELDS} FROM order_items
            WHERE order_id = ANY(%s::bigint[]) ORDER BY product_id""",
        (order_ids,),
    ).fetchall()


def order_with_inventory(db: Connection, order: dict, items: list[dict]) -> dict:
    products = db.execute(
        f"SELECT {_PRODUCT_FIELDS} FROM products WHERE is_active AND id = ANY(%s::bigint[]) ORDER BY id",
        ([item["product_id"] for item in items],),
    ).fetchall()
    return {**order_dto(order, items), "inventoryUpdates": catalog_product_dtos(db, products)}


@router.get("/orders")
def list_orders(db: Db, user: User) -> list[dict]:
    rows = db.execute(
        f"""SELECT {_ORDER_FIELDS} FROM orders
            WHERE (%s OR client_user_id = %s) ORDER BY created_at DESC, id DESC""",
        (user["role"] == "WORKSHOP", user["id"]),
    ).fetchall()
    items = defaultdict(list)
    if rows:
        for item in read_order_items(db, [row["id"] for row in rows]):
            items[item["order_id"]].append(item)
    return [order_dto(row, items[row["id"]]) for row in rows]


def find_order_by_request(db: Connection, client_id: int, request_id: UUID) -> dict | None:
    return db.execute(
        f"SELECT {_ORDER_FIELDS} FROM orders WHERE client_user_id = %s AND request_id = %s FOR SHARE",
        (client_id, request_id),
    ).fetchone()


@router.get("/orders/requests/{request_id}")
def get_order_by_request(request_id: UUID, db: Db, user: User) -> dict:
    with db.transaction():
        order = find_order_by_request(db, user["id"], request_id)
        if order is None:
            raise HTTPException(404, "No encontramos un pedido para esta solicitud.")
        items = read_order_items(db, [order["id"]])
        if order["payment_method"] == PAY_ON_PICKUP:
            return order_with_inventory(db, order, items)
        return order_dto(order, items)


@router.post("/orders", status_code=status.HTTP_201_CREATED)
def create_order(body: OrderRequest, db: Db, user: User) -> dict:
    return save_order(body, db, user)


@router.post("/orders/checkout", status_code=status.HTTP_201_CREATED)
def checkout_order(body: CheckoutRequest, db: Db, user: User) -> dict:
    return save_order(body, db, user, expected_total_cop=body.expected_total_cop)


def lock_order_products(
    db: Connection, product_ids: list[int], *, for_update: bool = True, include_inactive: bool = False
) -> list[dict]:
    lock = "FOR NO KEY UPDATE" if for_update else "FOR SHARE"
    return db.execute(
        f"""SELECT id, name, stock, COALESCE(promotion_price_cop, price_cop) AS unit_price
            FROM products WHERE (%s OR is_active) AND id = ANY(%s::bigint[]) ORDER BY id {lock}""",
        (include_inactive, product_ids),
    ).fetchall()


def change_order_stock(db: Connection, order_id: int, *, direction: Literal[-1, 1]) -> None:
    db.execute(
        """UPDATE products p SET stock = p.stock + (%s * i.quantity),
           version = p.version + 1, updated_at = now()
           FROM order_items i WHERE i.order_id = %s AND p.id = i.product_id""",
        (direction, order_id),
    )


def save_order(
    body: OrderRequest, db: Connection, user: dict, *, expected_total_cop: int | None = None
) -> dict:
    if user["role"] != "CLIENT":
        raise HTTPException(403, "Solo la cuenta de cliente puede solicitar compras.")
    quantities = {item.product_id: item.quantity for item in body.items}
    if len(quantities) != len(body.items):
        raise HTTPException(422, "Cada repuesto debe aparecer una sola vez en el pedido.")
    checkout = expected_total_cop is not None

    with db.transaction():
        # Serialize submissions from this client to make retries safe across workers.
        db.execute("SELECT id FROM users WHERE id = %s FOR NO KEY UPDATE", (user["id"],)).fetchone()
        previous = find_order_by_request(db, user["id"], body.request_id)
        if previous is not None:
            items = read_order_items(db, [previous["id"]])
            if quantities != {item["product_id"]: item["quantity"] for item in items}:
                raise HTTPException(409, "Ese identificador de solicitud ya se usó con otro pedido.")
            if checkout and previous["total_cop"] != expected_total_cop:
                raise HTTPException(409, "Ese identificador de solicitud ya se usó con otro total.")
            if checkout:
                return order_with_inventory(db, previous, items)
            return order_dto(previous, items)
        products = lock_order_products(db, list(quantities), for_update=checkout)
        if len(products) != len(quantities) or any(row["stock"] < quantities[row["id"]] for row in products):
            raise HTTPException(409, "Cambió la disponibilidad. Revisa los repuestos del carrito.", headers=_ORDER_REJECTED)
        total_cop = sum(row["unit_price"] * quantities[row["id"]] for row in products)
        if total_cop > MAX_BIGINT:
            raise HTTPException(422, "El total del pedido se pasa del límite. Reduce las cantidades.", headers=_ORDER_REJECTED)
        if checkout and total_cop != expected_total_cop:
            raise HTTPException(409, "Cambió el precio. Revisa el nuevo total antes de confirmar.", headers=_ORDER_REJECTED)
        order = db.execute(
            f"""INSERT INTO orders (client_user_id, request_id, client_name, total_cop, payment_method)
                VALUES (%s, %s, %s, %s, %s) RETURNING {_ORDER_FIELDS}""",
            (user["id"], body.request_id, user["display_name"], total_cop, PAY_ON_PICKUP if checkout else None),
        ).fetchone()
        with db.cursor() as cursor:
            cursor.executemany(
                """INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price_cop)
                   VALUES (%s, %s, %s, %s, %s)""",
                [(order["id"], row["id"], row["name"], quantities[row["id"]], row["unit_price"]) for row in products],
            )
        items = read_order_items(db, [order["id"]])
        if checkout:
            change_order_stock(db, order["id"], direction=-1)
            return order_with_inventory(db, order, items)
        return order_dto(order, items)


@router.post("/orders/{order_id}/decision")
def decide_order(order_id: PositiveId, body: OrderDecision, db: Db, owner: Owner) -> dict:
    with db.transaction():
        order = db.execute(
            f"SELECT {_ORDER_FIELDS} FROM orders WHERE id = %s FOR UPDATE",
            (order_id,),
        ).fetchone()
        if order is None:
            raise HTTPException(404, "El pedido no existe.")
        items = read_order_items(db, [order_id])
        pickup = order["payment_method"] == PAY_ON_PICKUP
        confirms_legacy_order = body.status == "CONFIRMED" and not pickup
        restores_pickup_order = body.status == "REJECTED" and pickup
        if order["status"] == body.status:
            if confirms_legacy_order or restores_pickup_order:
                return order_with_inventory(db, order, items)
            return order_dto(order, items)

        if body.status == "COMPLETED":
            if not pickup or order["status"] != "CONFIRMED":
                raise HTTPException(409, "El pedido debe estar listo para registrar el cobro y la entrega.")
        elif order["status"] != "PENDING":
            raise HTTPException(409, "El pedido ya fue revisado.")
        elif confirms_legacy_order:
            products = lock_order_products(db, [item["product_id"] for item in items])
            available_stock = {row["id"]: row["stock"] for row in products}
            if not items or any(available_stock.get(i["product_id"], 0) < i["quantity"] for i in items):
                raise HTTPException(409, "No hay existencias para completar el pedido.")
            change_order_stock(db, order_id, direction=-1)
        elif restores_pickup_order:
            products = lock_order_products(db, [item["product_id"] for item in items], include_inactive=True)
            quantities = {item["product_id"]: item["quantity"] for item in items}
            if any(row["stock"] + quantities[row["id"]] > MAX_STOCK for row in products):
                raise HTTPException(409, "Ajusta las existencias antes de devolver las unidades del pedido.")
            change_order_stock(db, order_id, direction=1)
        order = db.execute(
            f"""UPDATE orders SET status = %s, reviewed_by = %s, reviewed_at = now()
                WHERE id = %s RETURNING {_ORDER_FIELDS}""",
            (body.status, owner["id"], order_id),
        ).fetchone()
        if confirms_legacy_order or restores_pickup_order:
            return order_with_inventory(db, order, items)
        return order_dto(order, items)
