#!/bin/sh
set -eu

python -m edmotor.db migrate
python -m edmotor.pilot_seed
exec uvicorn edmotor.main:app --host 0.0.0.0 --port "${PORT:-8000}"
