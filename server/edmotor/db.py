import argparse
import hashlib
import os
import re
from collections.abc import Iterator
from pathlib import Path
from typing import Any

from psycopg import Connection, connect
from psycopg.conninfo import make_conninfo
from psycopg.rows import dict_row

Database = Connection[dict[str, Any]]
SQL_DIRECTORY = Path(__file__).resolve().parent.parent / "sql"
MIGRATION_NAME = re.compile(r"[0-9]{3}_[a-z0-9_]+\.sql")
MIGRATION_LOCK = 542_914_377


def open_database() -> Database:
    dsn = os.environ.get("EDMOTOR_DATABASE_URL", "").strip()
    if not dsn:
        password = os.environ.get("EDMOTOR_DB_PASSWORD")
        if not password:
            raise RuntimeError("Define EDMOTOR_DATABASE_URL o EDMOTOR_DB_PASSWORD.")
        dsn = make_conninfo(
            host=os.environ.get("EDMOTOR_DB_HOST", "127.0.0.1"),
            port=os.environ.get("EDMOTOR_DB_PORT", "5432"),
            dbname=os.environ.get("EDMOTOR_DB_NAME", "edmotor"),
            user=os.environ.get("EDMOTOR_DB_USER", "edmotor"),
            password=password,
        )
    return connect(dsn, autocommit=True, row_factory=dict_row, connect_timeout=5)


def get_db() -> Iterator[Database]:
    with open_database() as database:
        yield database


def migrate(database: Database, sql_directory: Path = SQL_DIRECTORY) -> list[str]:
    paths = sorted(path for path in sql_directory.iterdir() if MIGRATION_NAME.fullmatch(path.name))
    if not paths or len({path.name[:3] for path in paths}) != len(paths):
        raise RuntimeError("Faltan migraciones SQL o hay versiones repetidas.")
    applied = []
    with database.transaction():
        database.execute("SELECT pg_advisory_xact_lock(%s)", (MIGRATION_LOCK,))
        database.execute(
            """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                filename text PRIMARY KEY,
                checksum text NOT NULL,
                applied_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """
        )
        current = {
            row["filename"]: row["checksum"]
            for row in database.execute("SELECT filename, checksum FROM schema_migrations")
        }
        if current.keys() - {path.name for path in paths}:
            raise RuntimeError("La base registra una migración ausente en esta versión del servidor.")
        for path in paths:
            source = path.read_bytes()
            checksum = hashlib.sha256(source).hexdigest()
            if path.name in current:
                if current[path.name] != checksum:
                    raise RuntimeError(f"La migración aplicada {path.name} cambió.")
                continue
            database.execute(source.decode("utf-8"), prepare=False)
            database.execute(
                "INSERT INTO schema_migrations (filename, checksum) VALUES (%s, %s)",
                (path.name, checksum),
            )
            applied.append(path.name)
    return applied


def main() -> None:
    parser = argparse.ArgumentParser(description="Administración de la base de datos de EdMotor.")
    parser.add_argument("command", choices=["migrate"])
    parser.parse_args()
    with open_database() as database:
        applied = migrate(database)
    print("Migraciones aplicadas: " + ", ".join(applied) if applied else "La base de EdMotor está actualizada.")


if __name__ == "__main__":
    main()
