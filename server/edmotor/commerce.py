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

from .auth import Bearer, WORKSHOP, require_owner, require_user
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
_ORDER_FIELDS = "id, status, client_name, total_cop, created_at"
_ORDER_ITEM_FIELDS = "order_id, product_id, product_name, quantity, unit_price_cop"
_ORDER_REJECTED = {"EdMotor-Order-Result": "rejected"}


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


class OrderLineRequest(CommerceBody):
    product_id: PositiveLong
    quantity: StrictInt = Field(gt=0, le=MAX_STOCK)


class OrderRequest(CommerceBody):
    request_id: UUID
    items: list[OrderLineRequest] = Field(min_length=1, max_length=100)


class OrderDecision(CommerceBody):
    status: Literal["CONFIRMED", "REJECTED"]


def product_dto(row: dict) -> dict:
    return {
        "id": row["id"], "version": row["version"], "name": row["name"],
        "description": row["description"], "category": row["category"],
        "priceCop": row["price_cop"], "promotionPriceCop": row["promotion_price_cop"],
        "stock": row["stock"],
        "imagePath": f"{IMAGE_PREFIX}{row['image_id']}" if row["image_id"] else None,
    }


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
    return [product_dto(row) for row in rows]


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
    except UniqueViolation as error:
        raise HTTPException(409, "Ya hay un repuesto con ese nombre en la categoría.") from error
    return product_dto(row)


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
        f"SELECT {_ORDER_FIELDS} FROM orders WHERE client_user_id = %s AND request_id = %s",
        (client_id, request_id),
    ).fetchone()


@router.get("/orders/requests/{request_id}")
def get_order_by_request(request_id: UUID, db: Db, user: User) -> dict:
    order = find_order_by_request(db, user["id"], request_id)
    if order is None:
        raise HTTPException(404, "No encontramos un pedido para esta solicitud.")
    return order_dto(order, read_order_items(db, [order["id"]]))


@router.post("/orders", status_code=status.HTTP_201_CREATED)
def create_order(body: OrderRequest, db: Db, user: User) -> dict:
    if user["role"] != "CLIENT":
        raise HTTPException(403, "Solo la cuenta de cliente puede solicitar compras.")
    quantities = {item.product_id: item.quantity for item in body.items}
    if len(quantities) != len(body.items):
        raise HTTPException(422, "Cada repuesto debe aparecer una sola vez en el pedido.")

    with db.transaction():
        # Serialize submissions from this client to make retries safe across workers.
        db.execute("SELECT id FROM users WHERE id = %s FOR NO KEY UPDATE", (user["id"],)).fetchone()
        previous = find_order_by_request(db, user["id"], body.request_id)
        if previous is not None:
            items = read_order_items(db, [previous["id"]])
            if quantities != {item["product_id"]: item["quantity"] for item in items}:
                raise HTTPException(409, "Ese identificador de solicitud ya se usó con otro pedido.")
            return order_dto(previous, items)
        products = db.execute(
            """SELECT id, name, stock, COALESCE(promotion_price_cop, price_cop) AS unit_price
               FROM products WHERE is_active AND id = ANY(%s::bigint[]) ORDER BY id FOR SHARE""",
            (list(quantities),),
        ).fetchall()
        if len(products) != len(quantities) or any(row["stock"] < quantities[row["id"]] for row in products):
            raise HTTPException(409, "Cambió la disponibilidad. Revisa los repuestos del carrito.", headers=_ORDER_REJECTED)
        total_cop = sum(row["unit_price"] * quantities[row["id"]] for row in products)
        if total_cop > MAX_BIGINT:
            raise HTTPException(422, "El total del pedido se pasa del límite. Reduce las cantidades.", headers=_ORDER_REJECTED)
        order = db.execute(
            f"""INSERT INTO orders (client_user_id, request_id, client_name, total_cop)
                VALUES (%s, %s, %s, %s) RETURNING {_ORDER_FIELDS}""",
            (user["id"], body.request_id, user["display_name"], total_cop),
        ).fetchone()
        with db.cursor() as cursor:
            cursor.executemany(
                """INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price_cop)
                   VALUES (%s, %s, %s, %s, %s)""",
                [(order["id"], row["id"], row["name"], quantities[row["id"]], row["unit_price"]) for row in products],
            )
        return order_dto(order, read_order_items(db, [order["id"]]))


@router.post("/orders/{order_id}/decision")
def decide_order(order_id: PositiveId, body: OrderDecision, db: Db, owner: Owner) -> dict:
    with db.transaction():
        order = db.execute(
            f"SELECT {_ORDER_FIELDS} FROM orders WHERE id = %s FOR UPDATE",
            (order_id,),
        ).fetchone()
        if order is None:
            raise HTTPException(404, "El pedido no existe.")
        if order["status"] not in ("PENDING", body.status):
            raise HTTPException(409, "El pedido ya fue revisado.")
        items = read_order_items(db, [order_id])
        if order["status"] == body.status:
            return order_dto(order, items)
        if body.status == "CONFIRMED":
            products = db.execute(
                """SELECT id, stock FROM products WHERE is_active AND id = ANY(%s::bigint[])
                   ORDER BY id FOR NO KEY UPDATE""",
                ([item["product_id"] for item in items],),
            ).fetchall()
            available_stock = {row["id"]: row["stock"] for row in products}
            if not items or any(available_stock.get(i["product_id"], 0) < i["quantity"] for i in items):
                raise HTTPException(409, "No hay existencias para completar el pedido.")
            db.execute(
                """UPDATE products p SET stock = p.stock - i.quantity,
                   version = p.version + 1, updated_at = now()
                   FROM order_items i WHERE i.order_id = %s AND p.id = i.product_id""",
                (order_id,),
            )
        order = db.execute(
            f"""UPDATE orders SET status = %s, reviewed_by = %s, reviewed_at = now()
                WHERE id = %s RETURNING {_ORDER_FIELDS}""",
            (body.status, owner["id"], order_id),
        ).fetchone()
        return order_dto(order, items)
