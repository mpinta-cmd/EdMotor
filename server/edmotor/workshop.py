"""Vehicles, versioned workshop appointments, and their maintenance reports."""

import hashlib
import hmac
from datetime import UTC, datetime, timedelta
from typing import Annotated, Literal
from uuid import UUID
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Path, Query, Response
from psycopg import Connection
from pydantic import BaseModel, ConfigDict, Field, StringConstraints, field_validator

from .auth import require_owner, require_user
from .db import get_db

router = APIRouter(tags=["workshop"])

BOGOTA = ZoneInfo("America/Bogota")
EPOCH = datetime(1970, 1, 1, tzinfo=UTC)
MINIMUM_NOTICE = timedelta(minutes=15)
ACTIVE = ("PENDING", "CONFIRMED")
SERVICES = frozenset({
    "Revisión general",
    "Mantenimiento preventivo",
    "Diagnóstico",
    "Cambio de aceite",
    "Frenos",
    "Neumáticos",
})
MAX_ID = 2**63 - 1
MAX_TIMESTAMP_MS = 253402300799999

Database = Annotated[Connection, Depends(get_db)]
Actor = Annotated[dict, Depends(require_user)]
Owner = Annotated[dict, Depends(require_owner)]
ResourceId = Annotated[int, Path(ge=1, le=MAX_ID)]
TimestampMillis = Annotated[int, Field(strict=True, ge=1, le=MAX_TIMESTAMP_MS)]
NO_NULL_CHARACTERS = r"^[^\x00]*$"
LabelText = Annotated[
    str, StringConstraints(strip_whitespace=True, min_length=1, max_length=80, pattern=NO_NULL_CHARACTERS)
]
ReportText = Annotated[
    str, StringConstraints(strip_whitespace=True, min_length=1, max_length=4000, pattern=NO_NULL_CHARACTERS)
]
AppointmentStatus = Literal["PENDING", "CONFIRMED", "REJECTED", "COMPLETED"]


class WorkshopRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")


class VehicleRequest(WorkshopRequest):
    brand: LabelText
    model: LabelText
    year: Annotated[int, Field(strict=True, ge=1886, le=9999)]
    plate: Annotated[str, StringConstraints(max_length=20, pattern=NO_NULL_CHARACTERS)] = ""

    @field_validator("plate", mode="before")
    @classmethod
    def normalize_plate(cls, value: object) -> object:
        if value is None:
            return ""
        return value.strip().upper() if isinstance(value, str) else value


class VehicleDto(BaseModel):
    id: int
    clientId: int
    clientName: str
    brand: str
    model: str
    year: int
    plate: str


class AppointmentRequest(WorkshopRequest):
    requestId: UUID
    vehicleId: Annotated[int, Field(strict=True, ge=1, le=MAX_ID)]
    scheduledAtMillis: TimestampMillis
    serviceType: LabelText


class AppointmentChange(WorkshopRequest):
    version: Annotated[int, Field(strict=True, ge=1, le=MAX_ID)]
    action: Literal["CONFIRM", "REJECT", "RESCHEDULE", "FINISH"]
    scheduledAtMillis: TimestampMillis | None = None


class AppointmentDto(BaseModel):
    id: int
    version: int
    vehicleId: int
    clientId: int
    clientName: str
    vehicleBrand: str
    vehicleModel: str
    vehicleYear: int
    vehiclePlate: str
    serviceType: str
    scheduledAtMillis: int
    status: AppointmentStatus
    createdAtMillis: int
    updatedAtMillis: int
    finishedAtMillis: int | None


class MaintenanceRequest(WorkshopRequest):
    summary: ReportText
    mileageKm: Annotated[int, Field(strict=True, ge=0, le=2**31 - 1)] | None = None


class MaintenanceDto(BaseModel):
    id: int
    appointmentId: int
    vehicleId: int
    summary: str
    mileageKm: int | None
    createdAtMillis: int


APPOINTMENT_FIELDS = """
    a.id, a.version, a.client_id, a.vehicle_id, a.service_type, a.scheduled_at, a.status,
    a.created_at, a.updated_at, a.finished_at, u.display_name AS client_name,
    v.brand, v.model, v.year, v.plate
"""
APPOINTMENT_SELECT = f"""
    SELECT {APPOINTMENT_FIELDS}
    FROM appointments a
    JOIN vehicles v ON v.id = a.vehicle_id
    JOIN users u ON u.id = a.client_id
"""
MAINTENANCE_SELECT = """
    SELECT m.id, m.appointment_id, a.vehicle_id, m.summary, m.mileage_km, m.created_at
    FROM maintenance_reports m
    JOIN appointments a ON a.id = m.appointment_id
"""


def _require_client(user: dict) -> None:
    if user["role"] != "CLIENT":
        raise HTTPException(403, "Solo un cliente puede realizar esta operación.")


def _client_scope(user: dict) -> int | None:
    return None if user["role"] == "WORKSHOP" else user["id"]


def _millis(instant: datetime) -> int:
    elapsed = instant - EPOCH
    return (elapsed.days * 86_400 + elapsed.seconds) * 1_000 + elapsed.microseconds // 1_000


def _vehicle_dto(row: dict) -> VehicleDto:
    return VehicleDto(
        id=row["id"], clientId=row["client_id"], clientName=row["client_name"],
        brand=row["brand"], model=row["model"], year=row["year"], plate=row["plate"],
    )


def _appointment_dto(row: dict) -> AppointmentDto:
    finished_at = row["finished_at"]
    return AppointmentDto(
        id=row["id"], version=row["version"], vehicleId=row["vehicle_id"],
        clientId=row["client_id"], clientName=row["client_name"],
        vehicleBrand=row["brand"], vehicleModel=row["model"], vehicleYear=row["year"],
        vehiclePlate=row["plate"], serviceType=row["service_type"], status=row["status"],
        scheduledAtMillis=_millis(row["scheduled_at"]), createdAtMillis=_millis(row["created_at"]),
        updatedAtMillis=_millis(row["updated_at"]),
        finishedAtMillis=_millis(finished_at) if finished_at is not None else None,
    )


def _maintenance_dto(row: dict) -> MaintenanceDto:
    return MaintenanceDto(
        id=row["id"], appointmentId=row["appointment_id"], vehicleId=row["vehicle_id"],
        summary=row["summary"], mileageKm=row["mileage_km"], createdAtMillis=_millis(row["created_at"]),
    )


def _booking_time(value: int, now: datetime, *, client: bool) -> datetime:
    instant = EPOCH + timedelta(milliseconds=value)
    local_time = instant.astimezone(BOGOTA)
    if (
        not 8 <= local_time.hour <= 17
        or local_time.minute not in (0, 30)
        or (local_time.hour == 17 and local_time.minute != 0)
        or local_time.second != 0
        or local_time.microsecond != 0
    ):
        raise HTTPException(422, "Elige una franja de 30 minutos entre las 08:00 y las 17:00, hora de Bogotá.")
    if client and instant < now + MINIMUM_NOTICE:
        raise HTTPException(422, "Selecciona un horario con al menos 15 minutos de anticipación.")
    if instant <= now:
        raise HTTPException(422, "Selecciona una fecha y una hora futuras.")
    return instant


def _appointment(db: Connection, appointment_id: int, user: dict, *, lock: bool = False) -> dict:
    client_id = _client_scope(user)
    condition = " WHERE a.id = %s"
    params = (appointment_id,)
    if client_id is not None:
        condition += " AND a.client_id = %s"
        params += (client_id,)
    if lock:
        condition += " FOR UPDATE OF a"
    row = db.execute(APPOINTMENT_SELECT + condition, params).fetchone()
    if row is None:
        raise HTTPException(404, "No se encontró la cita para este perfil.")
    return row


def _check_version(appointment: dict, version: int) -> None:
    if appointment["version"] != version:
        raise HTTPException(409, "La cita cambió. Actualiza la agenda y vuelve a intentarlo.")


def _request_digests(client_id: int, body: AppointmentRequest) -> tuple[bytes, bytes]:
    key = hashlib.sha256(f"edmotor:appointment:v1:{client_id}:".encode() + body.requestId.bytes).digest()
    fields = f"{body.vehicleId}:{body.scheduledAtMillis}:{body.serviceType}".encode()
    return key, hashlib.sha256(body.requestId.bytes + fields).digest()


def _previous_appointment(db: Connection, request_key: bytes, payload_hash: bytes) -> AppointmentDto:
    previous = db.execute(
        f"""
        SELECT r.payload_hash, {APPOINTMENT_FIELDS}
        FROM appointment_requests r
        LEFT JOIN appointments a ON a.id = r.appointment_id
        LEFT JOIN vehicles v ON v.id = a.vehicle_id
        LEFT JOIN users u ON u.id = a.client_id
        WHERE r.request_key = %s
        """,
        (request_key,),
    ).fetchone()
    if not hmac.compare_digest(bytes(previous["payload_hash"]), payload_hash):
        raise HTTPException(409, "El identificador de solicitud ya corresponde a otros datos.")
    if previous["id"] is None:
        raise HTTPException(410, "Esta solicitud ya fue cancelada. Crea una nueva para reservar otra cita.")
    return _appointment_dto(previous)


@router.get("/vehicles")
def list_vehicles(db: Database, user: Actor) -> list[VehicleDto]:
    client_id = _client_scope(user)
    rows = db.execute(
        """
        SELECT v.id, v.client_id, u.display_name AS client_name, v.brand, v.model, v.year, v.plate
        FROM vehicles v JOIN users u ON u.id = v.client_id
        WHERE (%s::bigint IS NULL OR v.client_id = %s)
        ORDER BY v.id DESC
        """,
        (client_id, client_id),
    ).fetchall()
    return [_vehicle_dto(row) for row in rows]


@router.post("/vehicles", status_code=201)
def create_vehicle(body: VehicleRequest, db: Database, user: Actor) -> VehicleDto:
    _require_client(user)
    if body.year > datetime.now(BOGOTA).year + 1:
        raise HTTPException(422, "El año del vehículo no es válido.")
    with db.transaction():
        row = db.execute(
            """
            INSERT INTO vehicles(client_id, brand, model, year, plate)
            VALUES (%s, %s, %s, %s, %s)
            RETURNING id, client_id, brand, model, year, plate
            """,
            (user["id"], body.brand, body.model, body.year, body.plate),
        ).fetchone()
        return _vehicle_dto({**row, "client_name": user["display_name"]})


@router.get("/appointments")
def list_appointments(db: Database, user: Actor) -> list[AppointmentDto]:
    client_id = _client_scope(user)
    rows = db.execute(
        APPOINTMENT_SELECT + """
        WHERE (%s::bigint IS NULL OR a.client_id = %s)
        ORDER BY a.scheduled_at, a.id
        """,
        (client_id, client_id),
    ).fetchall()
    return [_appointment_dto(row) for row in rows]


@router.post("/appointments", status_code=201)
def create_appointment(body: AppointmentRequest, db: Database, user: Actor) -> AppointmentDto:
    _require_client(user)
    request_key, payload_hash = _request_digests(user["id"], body)
    with db.transaction():
        claimed = db.execute(
            """
            INSERT INTO appointment_requests(request_key, payload_hash)
            VALUES (%s, %s) ON CONFLICT (request_key) DO NOTHING
            RETURNING request_key
            """,
            (request_key, payload_hash),
        ).fetchone() is not None
        if not claimed:
            return _previous_appointment(db, request_key, payload_hash)

        if body.serviceType not in SERVICES:
            raise HTTPException(422, "Selecciona un tipo de servicio válido.")
        now = datetime.now(UTC)
        scheduled_at = _booking_time(body.scheduledAtMillis, now, client=True)
        vehicle = db.execute(
            "SELECT id FROM vehicles WHERE id = %s AND client_id = %s",
            (body.vehicleId, user["id"]),
        ).fetchone()
        if vehicle is None:
            raise HTTPException(404, "Selecciona un vehículo registrado en tu garaje.")
        appointment = db.execute(
            """
            INSERT INTO appointments(vehicle_id, client_id, service_type, scheduled_at, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s, %s)
            ON CONFLICT (client_id) WHERE status IN ('PENDING', 'CONFIRMED') DO NOTHING
            RETURNING id
            """,
            (body.vehicleId, user["id"], body.serviceType, scheduled_at, now, now),
        ).fetchone()
        if appointment is None:
            raise HTTPException(409, "Ya tienes una cita activa. Gestiona esa cita antes de solicitar otra.")
        db.execute(
            "UPDATE appointment_requests SET appointment_id = %s WHERE request_key = %s",
            (appointment["id"], request_key),
        )
        return _appointment_dto(_appointment(db, appointment["id"], user))


@router.patch("/appointments/{appointment_id}")
def change_appointment(
    appointment_id: ResourceId, body: AppointmentChange, db: Database, user: Actor,
) -> AppointmentDto:
    workshop = user["role"] == "WORKSHOP"
    if body.action != "RESCHEDULE" and not workshop:
        raise HTTPException(403, "Solo el taller puede revisar o terminar citas.")
    if (body.scheduledAtMillis is not None) != (body.action == "RESCHEDULE"):
        raise HTTPException(422, "Indica el horario únicamente al reagendar la cita.")

    with db.transaction():
        target = _appointment(db, appointment_id, user, lock=True)
        _check_version(target, body.version)
        now = datetime.now(UTC)
        status = target["status"]
        scheduled_at = target["scheduled_at"]
        finished_at = None

        if body.action == "RESCHEDULE":
            if status not in ACTIVE:
                raise HTTPException(409, "Esta cita ya no se puede reagendar.")
            next_time = _booking_time(body.scheduledAtMillis, now, client=not workshop)
            if next_time == scheduled_at:
                return _appointment_dto(target)
            scheduled_at = next_time
            if not workshop:
                status = "PENDING"
        elif body.action == "FINISH":
            if status != "CONFIRMED" or scheduled_at > now:
                raise HTTPException(409, "Solo puedes terminar una cita confirmada desde la hora pactada.")
            status = "COMPLETED"
            finished_at = now
        else:
            if status != "PENDING":
                raise HTTPException(409, "La solicitud ya no está pendiente.")
            if body.action == "CONFIRM" and scheduled_at <= now:
                raise HTTPException(409, "Reagenda la cita a una hora futura antes de confirmarla.")
            status = "CONFIRMED" if body.action == "CONFIRM" else "REJECTED"

        db.execute(
            """
            UPDATE appointments
            SET status = %s, scheduled_at = %s, finished_at = %s,
                updated_at = GREATEST(updated_at, %s), version = version + 1
            WHERE id = %s
            """,
            (status, scheduled_at, finished_at, now, appointment_id),
        )
        return _appointment_dto(_appointment(db, appointment_id, user))


@router.delete("/appointments/{appointment_id}", status_code=204)
def cancel_appointment(
    appointment_id: ResourceId, version: Annotated[int, Query(ge=1, le=MAX_ID)], db: Database, user: Actor,
) -> Response:
    with db.transaction():
        target = _appointment(db, appointment_id, user, lock=True)
        _check_version(target, version)
        if target["status"] not in ACTIVE:
            raise HTTPException(409, "Esta cita ya no se puede cancelar.")
        db.execute("DELETE FROM appointments WHERE id = %s", (appointment_id,))
    return Response(status_code=204)


@router.get("/maintenance")
def list_maintenance(db: Database, user: Actor) -> list[MaintenanceDto]:
    client_id = _client_scope(user)
    rows = db.execute(
        MAINTENANCE_SELECT + """
        WHERE (%s::bigint IS NULL OR a.client_id = %s)
        ORDER BY m.created_at DESC, m.id DESC
        """,
        (client_id, client_id),
    ).fetchall()
    return [_maintenance_dto(row) for row in rows]


@router.post("/appointments/{appointment_id}/maintenance", status_code=201)
def create_maintenance(
    appointment_id: ResourceId, body: MaintenanceRequest, db: Database, owner: Owner,
) -> MaintenanceDto:
    with db.transaction():
        appointment = _appointment(db, appointment_id, owner, lock=True)
        if appointment["status"] != "COMPLETED":
            raise HTTPException(409, "Termina la cita antes de registrar su mantenimiento.")
        previous = db.execute(
            MAINTENANCE_SELECT + " WHERE m.appointment_id = %s",
            (appointment_id,),
        ).fetchone()
        if previous is not None:
            if previous["summary"] != body.summary or previous["mileage_km"] != body.mileageKm:
                raise HTTPException(409, "Esta cita ya tiene un mantenimiento registrado.")
            return _maintenance_dto(previous)

        row = db.execute(
            """
            INSERT INTO maintenance_reports(appointment_id, summary, mileage_km, created_at)
            VALUES (%s, %s, %s, %s)
            RETURNING id, appointment_id, summary, mileage_km, created_at
            """,
            (appointment_id, body.summary, body.mileageKm, datetime.now(UTC)),
        ).fetchone()
        return _maintenance_dto({**row, "vehicle_id": appointment["vehicle_id"]})
