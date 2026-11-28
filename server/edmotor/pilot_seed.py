import json
import os
from hashlib import sha256
from pathlib import Path
from uuid import UUID
from zipfile import BadZipFile, ZipFile

from psycopg import Error, sql

from .auth import _matches_password, bootstrap_owner, reset_password
from .commerce import IMAGE_PREFIX, MAX_IMAGE_BYTES
from .db import Database, open_database


CATALOG = Path(__file__).with_name("pilot_catalog.zip")
PRODUCT_COUNT = 40
PILOT_SEED_LOCK = 1_156_670_439
PRODUCT_FIELDS = (
    "id", "version", "name", "description", "category", "priceCop", "promotionPriceCop",
    "stock", "imagePath",
)
DATA_TABLES = (
    "users", "products", "product_images", "auth_sessions", "auth_rate_limits", "orders",
    "order_items", "vehicles", "appointments", "appointment_requests",
    "maintenance_reports", "product_ratings",
)


def _read_catalog() -> tuple[list[tuple], list[tuple[UUID, bytes, bytes]]]:
    with ZipFile(CATALOG) as archive:
        manifest_file = archive.getinfo("catalog.json")
        if manifest_file.file_size > 1_000_000:
            raise ValueError("El catálogo piloto supera el tamaño permitido.")
        manifest = json.loads(archive.read(manifest_file))
        rows = manifest["products"]
        hashes = manifest["photos"]
        if manifest["version"] != 1 or len(rows) != PRODUCT_COUNT or len(hashes) != PRODUCT_COUNT:
            raise ValueError("El catálogo piloto no contiene los 40 repuestos y sus fotos.")
        if len({row["id"] for row in rows}) != PRODUCT_COUNT:
            raise ValueError("El catálogo piloto contiene identificadores repetidos.")
        files = archive.namelist()
        if set(files) != {"catalog.json", *hashes} or len(files) != PRODUCT_COUNT + 1:
            raise ValueError("El catálogo piloto contiene archivos inesperados.")

        products = []
        images = []
        photo_files = set()
        for row in rows:
            if set(row) != set(PRODUCT_FIELDS):
                raise ValueError("El catálogo piloto contiene campos inesperados.")
            image_path = row["imagePath"]
            image_id = UUID(image_path.removeprefix(IMAGE_PREFIX))
            if image_path != f"{IMAGE_PREFIX}{image_id}":
                raise ValueError("Un repuesto piloto tiene una ruta de imagen inválida.")
            photo_file = f"photos/{image_id}.webp"
            if photo_file in photo_files:
                raise ValueError("El catálogo piloto contiene fotos repetidas.")
            photo_files.add(photo_file)
            info = archive.getinfo(photo_file)
            if info.file_size > MAX_IMAGE_BYTES:
                raise ValueError("Una foto piloto supera el tamaño permitido.")
            data = archive.read(info)
            digest = sha256(data).digest()
            if data[:4] != b"RIFF" or data[8:12] != b"WEBP" or digest.hex() != hashes[photo_file]:
                raise ValueError("Una foto del catálogo piloto no coincide con su huella.")
            products.append((*(row[key] for key in PRODUCT_FIELDS[:-1]), image_id))
            images.append((image_id, digest, data))
        return products, images


def seed(database: Database, owner_password: str | None) -> bool:
    with database.transaction():
        database.execute("SELECT pg_advisory_xact_lock(%s)", (PILOT_SEED_LOCK,))
        database.execute(
            """CREATE TABLE IF NOT EXISTS pilot_catalog_seed (
                   singleton boolean PRIMARY KEY CHECK (singleton),
                   seeded_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
               )"""
        )
        if database.execute("SELECT 1 FROM pilot_catalog_seed").fetchone():
            if owner_password:
                owner = database.execute(
                    "SELECT username, password_hash FROM users WHERE role = 'WORKSHOP' FOR UPDATE"
                ).fetchone()
                if owner is None:
                    raise ValueError("La base del piloto no tiene una cuenta de dueño que se pueda actualizar.")
                if not _matches_password(owner_password, owner["password_hash"]):
                    reset_password(database, owner["username"], owner_password)
            return False

        tables = sql.SQL(", ").join(map(sql.Identifier, DATA_TABLES))
        database.execute(sql.SQL("LOCK TABLE {} IN SHARE ROW EXCLUSIVE MODE NOWAIT").format(tables))
        queries = (sql.SQL("SELECT 1 FROM {}").format(sql.Identifier(table)) for table in DATA_TABLES)
        if database.execute(sql.SQL(" UNION ALL ").join(queries) + sql.SQL(" LIMIT 1")).fetchone():
            raise ValueError("La base del piloto ya tiene datos y no se modificó.")
        if not owner_password:
            raise ValueError("Define EDMOTOR_PILOT_OWNER_PASSWORD para inicializar el piloto.")

        products, images = _read_catalog()
        bootstrap_owner(database, "EdMotor", "EdMotor", owner_password)
        owner = database.execute("SELECT id FROM users WHERE role = 'WORKSHOP'").fetchone()
        for image_id, digest, data in images:
            database.execute(
                """INSERT INTO product_images (id, uploaded_by, content_digest, data)
                   VALUES (%s, %s, %s, %s)""",
                (image_id, owner["id"], digest, data),
            )
        for record in products:
            database.execute(
                """INSERT INTO products
                       (id, version, name, description, category, price_cop, promotion_price_cop, stock, image_id)
                   OVERRIDING SYSTEM VALUE VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)""",
                record,
            )
        database.execute(
            "SELECT setval(pg_get_serial_sequence('products', 'id'), (SELECT max(id) FROM products))"
        )
        database.execute("INSERT INTO pilot_catalog_seed (singleton) VALUES (TRUE)")
    return True


def main() -> None:
    try:
        with open_database() as database:
            initialized = seed(database, os.environ.get("EDMOTOR_PILOT_OWNER_PASSWORD"))
    except ValueError as error:
        raise SystemExit(str(error)) from None
    except (BadZipFile, KeyError, OSError, TypeError):
        raise SystemExit("No se pudo leer el archivo del catálogo piloto.") from None
    except Error as error:
        raise SystemExit(f"No se pudo inicializar la base del piloto ({error.sqlstate or 'conexión'}).") from None
    if initialized:
        print(f"Catálogo piloto inicializado con {PRODUCT_COUNT} repuestos y {PRODUCT_COUNT} fotos.")
    else:
        print("Catálogo piloto ya inicializado. Se importaron 0 repuestos y 0 fotos.")


if __name__ == "__main__":
    main()
