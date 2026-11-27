import argparse
import getpass
import hashlib
import os
import re
import secrets
import warnings
from functools import lru_cache
from threading import BoundedSemaphore
from typing import Annotated, Any, NoReturn

from argon2 import PasswordHasher, Type
from argon2.exceptions import InvalidHashError, VerificationError
from fastapi import APIRouter, Depends, HTTPException, Request, Response
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field

from .db import Database, get_db, open_database

CLIENT = "CLIENT"
WORKSHOP = "WORKSHOP"
DEFAULT_OWNER_USERNAME = "edmotor"
USERNAME = re.compile(r"[a-z0-9_]{3,20}")
BEARER_TOKEN = re.compile(r"[A-Za-z0-9_-]{43}")
PASSWORD_MAX_BYTES = 128
HASHER = PasswordHasher(time_cost=3, memory_cost=65_536, parallelism=1, hash_len=32, salt_len=16, type=Type.ID)
HASH_SLOTS = BoundedSemaphore(value=4)
router = APIRouter(prefix="/auth", tags=["Acceso"])

Db = Annotated[Database, Depends(get_db)]
User = dict[str, Any]
Bearer = Annotated[HTTPAuthorizationCredentials | None, Depends(HTTPBearer(auto_error=False))]


class LoginRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    username: str = Field(max_length=80)
    password: str = Field(repr=False, max_length=PASSWORD_MAX_BYTES)


class RegisterRequest(LoginRequest):
    display_name: str = Field(alias="displayName", max_length=80)


class UserResponse(BaseModel):
    id: int
    username: str
    displayName: str
    role: str


class SessionResponse(BaseModel):
    token: str = Field(repr=False)
    user: UserResponse


def _normalized_username(username: str) -> str:
    return username.strip().lower()


def _registration_details(username: str, display_name: str, password: str) -> tuple[str, str]:
    username = _normalized_username(username)
    display_name = display_name.strip()
    if USERNAME.fullmatch(username) is None:
        raise ValueError("Usa un usuario de 3 a 20 letras, números o guiones bajos.")
    if not 2 <= len(display_name) <= 50 or not display_name.isprintable():
        raise ValueError("Escribe un nombre de entre 2 y 50 caracteres.")
    _validate_new_password(password)
    return username, display_name


def _validate_new_password(password: str) -> None:
    if len(password) < 8 or not password.strip():
        raise ValueError("La contraseña debe tener al menos 8 caracteres.")
    if not _valid_password_size(password):
        raise ValueError("La contraseña contiene caracteres inválidos o supera el límite permitido.")


def _valid_password_size(password: str) -> bool:
    try:
        return len(password.encode("utf-8")) <= PASSWORD_MAX_BYTES
    except UnicodeEncodeError:
        return False


def _hash_password(password: str) -> str:
    with HASH_SLOTS:
        return HASHER.hash(password)


def _unauthorized(message: str = "Inicia sesión para continuar.") -> NoReturn:
    raise HTTPException(status_code=401, detail=message, headers={"WWW-Authenticate": "Bearer"})


def _public_user(user: User) -> UserResponse:
    return UserResponse(id=user["id"], username=user["username"], displayName=user["display_name"], role=user["role"])


def _token_hash(token: str) -> bytes:
    return hashlib.sha256(token.encode("utf-8")).digest()


def _bearer_hash(credentials: HTTPAuthorizationCredentials | None) -> bytes:
    if credentials is None or BEARER_TOKEN.fullmatch(credentials.credentials) is None:
        _unauthorized()
    return _token_hash(credentials.credentials)


def session_hours() -> int:
    hours = int(os.environ.get("EDMOTOR_SESSION_HOURS", "24"))
    if not 1 <= hours <= 720:
        raise RuntimeError("Configura EDMOTOR_SESSION_HOURS entre 1 y 720.")
    return hours


def _new_session(database: Database, user: User) -> SessionResponse:
    token = secrets.token_urlsafe(32)
    database.execute(
        """
        INSERT INTO auth_sessions (token_hash, user_id, expires_at)
        VALUES (%s, %s, CURRENT_TIMESTAMP + make_interval(hours => %s))
        """,
        (_token_hash(token), user["id"], session_hours()),
    )
    return SessionResponse(token=token, user=_public_user(user))


def require_user(credentials: Bearer, db: Db) -> User:
    token_hash = _bearer_hash(credentials)
    user = db.execute(
        """
        SELECT users.id, users.username, users.display_name, users.role
        FROM auth_sessions JOIN users ON users.id = auth_sessions.user_id
        WHERE auth_sessions.token_hash = %s AND auth_sessions.expires_at > CURRENT_TIMESTAMP
        """,
        (token_hash,),
    ).fetchone()
    if user is None:
        _unauthorized("Tu sesión venció o se cerró. Vuelve a entrar.")
    return user


def require_owner(user: Annotated[User, Depends(require_user)]) -> User:
    if user["role"] != WORKSHOP:
        raise HTTPException(status_code=403, detail="Solo el dueño puede realizar esta operación.")
    return user


def _rate_limit(db: Database, bucket: str, identity: str, maximum: int, seconds: int) -> None:
    row = db.execute(
        """
        INSERT INTO auth_rate_limits (bucket, identity_hash, attempts, expires_at)
        VALUES (%s, %s, 1, CURRENT_TIMESTAMP + make_interval(secs => %s))
        ON CONFLICT (bucket, identity_hash) DO UPDATE SET
            attempts = CASE WHEN auth_rate_limits.expires_at <= CURRENT_TIMESTAMP THEN 1
                            ELSE LEAST(auth_rate_limits.attempts + 1, %s) END,
            expires_at = CASE WHEN auth_rate_limits.expires_at <= CURRENT_TIMESTAMP THEN EXCLUDED.expires_at
                              ELSE auth_rate_limits.expires_at END
        RETURNING attempts,
          GREATEST(1, ceil(EXTRACT(EPOCH FROM (expires_at - CURRENT_TIMESTAMP)))::integer) AS retry_seconds
        """,
        (bucket, _token_hash(identity), seconds, maximum + 1),
    ).fetchone()
    if row["attempts"] > maximum:
        raise HTTPException(
            status_code=429,
            detail="Hay demasiados intentos. Espera un momento antes de volver a intentar.",
            headers={"Retry-After": str(row["retry_seconds"])},
        )


def _client_host(request: Request) -> str:
    return request.client.host if request.client is not None else "sin-direccion"


@lru_cache(maxsize=1)
def _dummy_password_hash() -> str:
    return _hash_password(secrets.token_urlsafe(32))


def _matches_password(entered: str, stored_hash: str | None) -> bool:
    verifier = stored_hash or _dummy_password_hash()
    try:
        with HASH_SLOTS:
            matched = HASHER.verify(verifier, entered)
    except (VerificationError, InvalidHashError):
        return False
    return stored_hash is not None and matched


@router.post("/register", status_code=201, response_model=SessionResponse)
def register(body: RegisterRequest, request: Request, db: Db) -> SessionResponse:
    _rate_limit(db, "register_ip", _client_host(request), maximum=10, seconds=3600)
    try:
        username, display_name = _registration_details(body.username, body.display_name, body.password)
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
    if username == DEFAULT_OWNER_USERNAME:
        raise HTTPException(status_code=409, detail="Ese nombre está reservado para EdMotor.")
    if db.execute("SELECT 1 FROM users WHERE role = 'WORKSHOP'").fetchone() is None:
        raise HTTPException(status_code=503, detail="El taller aún no ha habilitado el registro.")

    password_hash = _hash_password(body.password)
    with db.transaction():
        user = db.execute(
            """
            INSERT INTO users (username, display_name, password_hash, role)
            VALUES (%s, %s, %s, 'CLIENT')
            ON CONFLICT (username) DO NOTHING
            RETURNING id, username, display_name, role
            """,
            (username, display_name, password_hash),
        ).fetchone()
        if user is None:
            raise HTTPException(status_code=409, detail="Ya existe una cuenta con ese usuario.")
        return _new_session(db, user)


@router.post("/login", response_model=SessionResponse)
def login(body: LoginRequest, request: Request, db: Db) -> SessionResponse:
    host = _client_host(request)
    _rate_limit(db, "login_ip", host, maximum=30, seconds=300)
    username = _normalized_username(body.username)
    invalid = "El usuario o la contraseña no son válidos."
    if USERNAME.fullmatch(username) is None or not body.password.strip() or not _valid_password_size(body.password):
        _unauthorized(invalid)
    _rate_limit(db, "login_username_ip", f"{host}|{username}", maximum=8, seconds=300)

    stored = db.execute("SELECT id, password_hash FROM users WHERE username = %s", (username,)).fetchone()
    if not _matches_password(body.password, stored["password_hash"] if stored else None):
        _unauthorized(invalid)
    with db.transaction():
        user = db.execute(
            "SELECT id, username, display_name, password_hash, role FROM users WHERE id = %s FOR UPDATE",
            (stored["id"],),
        ).fetchone()
        if user is None or user["password_hash"] != stored["password_hash"] or user["username"] != username:
            _unauthorized(invalid)
        return _new_session(db, user)


@router.get("/me", response_model=UserResponse)
def me(user: Annotated[User, Depends(require_user)]) -> UserResponse:
    return _public_user(user)


@router.post("/logout", status_code=204)
def logout(credentials: Bearer, db: Db) -> Response:
    db.execute("DELETE FROM auth_sessions WHERE token_hash = %s", (_bearer_hash(credentials),))
    return Response(status_code=204)


def _prompt_new_password() -> str:
    with warnings.catch_warnings():
        warnings.simplefilter("error", getpass.GetPassWarning)
        password = getpass.getpass("Contraseña nueva: ")
        confirmation = getpass.getpass("Confirma la contraseña: ")
    _validate_new_password(password)
    if password != confirmation:
        raise ValueError("Las contraseñas no coinciden.")
    return password


def bootstrap_owner(db: Database, username: str, display_name: str, password: str) -> None:
    username, display_name = _registration_details(username, display_name, password)
    owner = db.execute(
        """
        INSERT INTO users (username, display_name, password_hash, role)
        VALUES (%s, %s, %s, 'WORKSHOP') ON CONFLICT DO NOTHING RETURNING id
        """,
        (username, display_name, _hash_password(password)),
    ).fetchone()
    if owner is None:
        raise ValueError("El taller ya tiene dueño o el usuario está ocupado.")


def reset_password(db: Database, username: str, password: str) -> None:
    username = _normalized_username(username)
    if USERNAME.fullmatch(username) is None:
        raise ValueError("El nombre de usuario no es válido.")
    _validate_new_password(password)
    password_hash = _hash_password(password)
    with db.transaction():
        user = db.execute(
            "UPDATE users SET password_hash = %s WHERE username = %s RETURNING id", (password_hash, username)
        ).fetchone()
        if user is None:
            raise ValueError("No existe esa cuenta.")
        db.execute("DELETE FROM auth_sessions WHERE user_id = %s", (user["id"],))


def main() -> None:
    parser = argparse.ArgumentParser(description="Administración local de cuentas EdMotor.")
    commands = parser.add_subparsers(dest="command", required=True)
    owner = commands.add_parser("bootstrap-owner", help="Crea el único dueño del taller.")
    owner.add_argument("--username", default=DEFAULT_OWNER_USERNAME)
    owner.add_argument("--display-name", default="EdMotor")
    reset = commands.add_parser("reset-password", help="Cambia la contraseña y revoca todas sus sesiones.")
    reset.add_argument("--username", required=True)
    commands.add_parser("prune-auth", help="Elimina sesiones y límites vencidos.")
    args = parser.parse_args()

    try:
        if args.command == "prune-auth":
            with open_database() as db, db.transaction():
                db.execute("DELETE FROM auth_sessions WHERE expires_at <= CURRENT_TIMESTAMP")
                db.execute("DELETE FROM auth_rate_limits WHERE expires_at <= CURRENT_TIMESTAMP")
            print("Se retiraron los registros de acceso vencidos.")
            return
        password = _prompt_new_password()
        with open_database() as db:
            if args.command == "bootstrap-owner":
                bootstrap_owner(db, args.username, args.display_name, password)
                print("Cuenta de dueño creada.")
            else:
                reset_password(db, args.username, password)
                print("Contraseña actualizada y sesiones anteriores revocadas.")
    except (ValueError, EOFError, getpass.GetPassWarning) as error:
        parser.exit(1, f"{error or 'Usa una terminal interactiva para introducir la contraseña.'}\n")


if __name__ == "__main__":
    main()
