FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && groupadd --system --gid 10001 edmotor \
    && useradd --system --uid 10001 --gid edmotor --no-create-home edmotor

COPY edmotor ./edmotor
COPY sql ./sql
RUN chmod -R a+rX /app/edmotor /app/sql

USER edmotor

EXPOSE 8000

CMD ["sh", "-c", "python -m edmotor.db migrate && exec uvicorn edmotor.main:app --host 0.0.0.0 --port 8000"]
