CREATE TABLE IF NOT EXISTS vehicles (
    id BIGSERIAL PRIMARY KEY,
    registration_number VARCHAR(30) NOT NULL UNIQUE,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS vehicle_id BIGINT REFERENCES vehicles(id);

CREATE INDEX IF NOT EXISTS idx_trips_vehicle_id ON trips(vehicle_id);