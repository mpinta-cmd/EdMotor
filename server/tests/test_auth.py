import hashlib
import os
import secrets
from pathlib import Path

import psycopg
import pytest
from fastapi.testclient import TestClient
from psycopg import sql
from psycopg.conninfo import conninfo_to_dict, make_conninfo

from edmotor.auth import _matches_owner_activation_code, bootstrap_owner, reset_password
from edmotor.db import SQL_DIRECTORY, migrate, open_database

PREFIX = "/api/v1/auth"
OWNER_PASSWORD = secrets.token_urlsafe(24)
CLIENT_PASSWORD = secrets.token_urlsafe(24)


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch, tmp_path: Path):
    test_dsn = os.environ.get("EDMOTOR_TEST_DATABASE_URL")
    if not test_dsn:
        pytest.skip("Define EDMOTOR_TEST_DATABASE_URL para un PostgreSQL de prueba.")
    if conninfo_to_dict(test_dsn).get("host") not in {"127.0.0.1", "localhost", "::1"}:
        raise ValueError("Las pruebas de acceso requieren un PostgreSQL desechable local.")
    from edmotor.main import app

    schema_name = f"edmotor_test_{secrets.token_hex(8)}"
    schema = sql.Identifier(schema_name)
    with psycopg.connect(test_dsn, autocommit=True) as administrator:
        administrator.execute(sql.SQL("CREATE SCHEMA {}").format(schema))
        try:
            dsn = make_conninfo(test_dsn, options=f"-csearch_path={schema_name}")
            monkeypatch.setenv("EDMOTOR_DATABASE_URL", dsn)
            identity = SQL_DIRECTORY / "001_identity.sql"
            (tmp_path / identity.name).write_bytes(identity.read_bytes())
            with open_database() as db:
                assert migrate(db, tmp_path) == [identity.name]
                assert migrate(db, tmp_path) == []
            with TestClient(app) as http:
                yield http
        finally:
            administrator.execute(sql.SQL("DROP SCHEMA {} CASCADE").format(schema))


def create_owner() -> None:
    with open_database() as db:
        bootstrap_owner(db, " EdMotor ", "EdMotor", OWNER_PASSWORD)


def register_client(client: TestClient) -> dict:
    response = client.post(
        f"{PREFIX}/register",
        json={"username": " Prueba_Cliente ", "displayName": "Cliente de prueba", "password": CLIENT_PASSWORD},
    )
    assert response.status_code == 201
    assert response.headers["cache-control"] == "no-store"
    return response.json()


def authorize(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def test_roles_hashes_and_session_revocation(client: TestClient) -> None:
    assert client.get(f"{PREFIX}/me").status_code == 401
    assert client.post(
        f"{PREFIX}/register",
        json={"username": "prueba_cliente", "displayName": "Cliente de prueba", "password": CLIENT_PASSWORD},
    ).status_code == 503
    create_owner()
    with open_database() as db, pytest.raises(ValueError):
        bootstrap_owner(db, "otro_dueno", "Segundo Dueño", OWNER_PASSWORD)

    owner = client.post(f"{PREFIX}/login", json={"username": "EDMOTOR", "password": OWNER_PASSWORD})
    assert owner.status_code == 200
    assert owner.json()["user"]["role"] == "WORKSHOP"
    registered = register_client(client)
    assert registered["user"] == {
        "id": registered["user"]["id"], "username": "prueba_cliente", "displayName": "Cliente de prueba", "role": "CLIENT"
    }

    client_headers = authorize(registered["token"])
    assert client.get(f"{PREFIX}/me", headers=client_headers).json() == registered["user"]
    with open_database() as db:
        user = db.execute("SELECT password_hash FROM users WHERE id = %s", (registered["user"]["id"],)).fetchone()
        assert user["password_hash"].startswith("$argon2id$")
        assert CLIENT_PASSWORD not in user["password_hash"]
        saved = db.execute("SELECT token_hash FROM auth_sessions WHERE user_id = %s", (registered["user"]["id"],)).fetchone()
        assert saved["token_hash"] == hashlib.sha256(registered["token"].encode()).digest()

    assert client.post(f"{PREFIX}/logout", headers=client_headers).status_code == 204
    assert client.get(f"{PREFIX}/me", headers=client_headers).status_code == 401
    with open_database() as db:
        reset_password(db, "edmotor", secrets.token_urlsafe(24))
    assert client.get(f"{PREFIX}/me", headers=authorize(owner.json()["token"])).status_code == 401
    assert client.post(f"{PREFIX}/login", json={"username": "edmotor", "password": OWNER_PASSWORD}).status_code == 401


def test_expired_sessions_and_login_rate_limit(client: TestClient) -> None:
    create_owner()
    registered = register_client(client)
    with open_database() as db:
        db.execute(
            "UPDATE auth_sessions SET created_at = now() - interval '2 days', expires_at = now() - interval '1 day'"
        )
    assert client.get(f"{PREFIX}/me", headers=authorize(registered["token"])).status_code == 401
    payload = {"username": "edmotor", "password": "clave inválida"}
    for _ in range(8):
        assert client.post(f"{PREFIX}/login", json=payload).status_code == 401
    limited = client.post(f"{PREFIX}/login", json=payload)
    assert limited.status_code == 429
    assert int(limited.headers["retry-after"]) > 0


def test_validation_response_does_not_echo_input_or_let_clients_select_a_role(client: TestClient) -> None:
    invalid_length = secrets.token_urlsafe(100)
    wrong = client.post(
        f"{PREFIX}/register",
        json={"username": "cliente", "displayName": "Cliente", "password": invalid_length, "role": "WORKSHOP"},
    )
    assert wrong.status_code == 422
    assert wrong.headers["cache-control"] == "no-store"
    assert invalid_length not in wrong.text
    assert isinstance(wrong.json()["detail"], str)


def test_owner_activation_requires_a_configured_urlsafe_code(monkeypatch: pytest.MonkeyPatch) -> None:
    code = secrets.token_urlsafe(32)
    monkeypatch.delenv("EDMOTOR_OWNER_ACTIVATION_CODE", raising=False)
    assert not _matches_owner_activation_code(code)
    monkeypatch.setenv("EDMOTOR_OWNER_ACTIVATION_CODE", "codigo-corto")
    assert not _matches_owner_activation_code("codigo-corto")
    monkeypatch.setenv("EDMOTOR_OWNER_ACTIVATION_CODE", code)
    assert not _matches_owner_activation_code("á" * len(code))
    assert _matches_owner_activation_code(f" {code} ")


def test_owner_activation_creates_a_single_workshop_and_limits_attempts(
    client: TestClient, monkeypatch: pytest.MonkeyPatch,
) -> None:
    code = secrets.token_urlsafe(32)
    monkeypatch.setenv("EDMOTOR_OWNER_ACTIVATION_CODE", code)
    endpoint = f"{PREFIX}/register-owner"
    owner_data = {
        "username": " edmotor ", "displayName": "EdMotor", "password": OWNER_PASSWORD,
        "activationCode": code,
    }

    wrong = client.post(endpoint, json={**owner_data, "activationCode": secrets.token_urlsafe(32)})
    assert wrong.status_code == 403
    registered = client.post(endpoint, json=owner_data)
    assert registered.status_code == 201
    assert registered.headers["cache-control"] == "no-store"
    session = registered.json()
    assert session["user"]["role"] == "WORKSHOP"
    assert client.get(f"{PREFIX}/me", headers=authorize(session["token"])).json() == session["user"]
    assert register_client(client)["user"]["role"] == "CLIENT"

    for _ in range(3):
        assert client.post(endpoint, json={**owner_data, "username": "otro_dueno"}).status_code == 409
    limited = client.post(endpoint, json=owner_data)
    assert limited.status_code == 429
    assert int(limited.headers["retry-after"]) > 0
