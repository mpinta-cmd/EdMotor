from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from psycopg import OperationalError

from .auth import router as auth_router
from .auth import session_hours
from .commerce import router as commerce_router
from .db import Database, get_db
from .workshop import router as workshop_router

@asynccontextmanager
async def lifespan(_: FastAPI):
    session_hours()
    yield


app = FastAPI(title="EdMotor", docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)
app.include_router(auth_router, prefix="/api/v1")
app.include_router(commerce_router, prefix="/api/v1")
app.include_router(workshop_router, prefix="/api/v1")


@app.exception_handler(RequestValidationError)
async def invalid_request(_: Request, error: RequestValidationError) -> JSONResponse:
    return JSONResponse(status_code=422, content={"detail": "Revisa los datos de la solicitud."})


@app.exception_handler(OperationalError)
async def database_unavailable(_: Request, error: OperationalError) -> JSONResponse:
    return JSONResponse(status_code=503, content={"detail": "La base de EdMotor no está disponible. Intenta nuevamente."})


@app.middleware("http")
async def prevent_session_caching(request: Request, call_next):
    response = await call_next(request)
    if request.url.path.startswith("/api/v1/auth/"):
        response.headers["Cache-Control"] = "no-store"
    return response


@app.get("/health")
@app.get("/api/v1/health")
def health(db: Annotated[Database, Depends(get_db)]) -> dict[str, str]:
    db.execute("SELECT 1")
    return {"status": "ok"}
