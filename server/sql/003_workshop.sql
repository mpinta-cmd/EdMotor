CREATE TABLE IF NOT EXISTS vehicles (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    brand text NOT NULL CHECK (length(brand) BETWEEN 1 AND 80),
    model text NOT NULL CHECK (length(model) BETWEEN 1 AND 80),
    year integer NOT NULL CHECK (year BETWEEN 1886 AND 9999),
    plate text NOT NULL DEFAULT '' CHECK (length(plate) <= 20),
    UNIQUE (id, client_id)
);

CREATE INDEX IF NOT EXISTS vehicles_by_client ON vehicles(client_id, id);

CREATE TABLE IF NOT EXISTS appointments (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vehicle_id bigint NOT NULL,
    client_id bigint NOT NULL,
    service_type text NOT NULL CHECK (length(service_type) BETWEEN 1 AND 80),
    scheduled_at timestamptz NOT NULL,
    status text NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'COMPLETED')),
    version bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    finished_at timestamptz,
    FOREIGN KEY (vehicle_id, client_id) REFERENCES vehicles(id, client_id) ON DELETE RESTRICT,
    CHECK ((status = 'COMPLETED') = (finished_at IS NOT NULL)),
    CHECK (finished_at IS NULL OR finished_at >= scheduled_at),
    CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX IF NOT EXISTS appointments_one_active_per_client
    ON appointments(client_id) WHERE status IN ('PENDING', 'CONFIRMED');
CREATE INDEX IF NOT EXISTS appointments_by_schedule ON appointments(scheduled_at, id);
CREATE INDEX IF NOT EXISTS appointments_by_client ON appointments(client_id, scheduled_at, id);
CREATE INDEX IF NOT EXISTS appointments_by_vehicle ON appointments(vehicle_id);

-- A cancelled request retains only its salted digests. Deleting its appointment clears the pointer.
CREATE TABLE IF NOT EXISTS appointment_requests (
    request_key bytea PRIMARY KEY CHECK (octet_length(request_key) = 32),
    payload_hash bytea NOT NULL CHECK (octet_length(payload_hash) = 32),
    appointment_id bigint UNIQUE REFERENCES appointments(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS maintenance_reports (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    appointment_id bigint NOT NULL UNIQUE REFERENCES appointments(id) ON DELETE RESTRICT,
    summary text NOT NULL CHECK (length(summary) BETWEEN 1 AND 4000),
    mileage_km integer CHECK (mileage_km >= 0),
    created_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS maintenance_reports_by_date ON maintenance_reports(created_at DESC, id DESC);
