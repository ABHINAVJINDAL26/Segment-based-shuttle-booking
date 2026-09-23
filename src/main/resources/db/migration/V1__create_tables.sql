-- ============================================================
-- Flyway Migration V1: Initial Schema
-- Office Shuttle Segment-Based Seat Booking System
-- ============================================================

-- ========================
-- users
-- ========================
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100)        NOT NULL,
    email       VARCHAR(255)        NOT NULL UNIQUE,
    password_hash VARCHAR(255)      NOT NULL,
    role        VARCHAR(20)         NOT NULL DEFAULT 'EMPLOYEE',
    created_at  TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP           NOT NULL DEFAULT NOW()
);

-- ========================
-- routes
-- ========================
CREATE TABLE IF NOT EXISTS routes (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100)        NOT NULL,
    description TEXT,
    created_at  TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP           NOT NULL DEFAULT NOW()
);

-- ========================
-- stops
-- ========================
CREATE TABLE IF NOT EXISTS stops (
    id           BIGSERIAL PRIMARY KEY,
    route_id     BIGINT              NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    name         VARCHAR(100)        NOT NULL,
    sequence_num INTEGER             NOT NULL,
    arrival_time VARCHAR(10),
    UNIQUE (route_id, sequence_num),
    UNIQUE (route_id, name)
);

CREATE INDEX idx_stops_route_id ON stops(route_id);

-- ========================
-- trips
-- ========================
CREATE TABLE IF NOT EXISTS trips (
    id          BIGSERIAL PRIMARY KEY,
    route_id    BIGINT              NOT NULL REFERENCES routes(id),
    trip_date   DATE                NOT NULL,
    capacity    INTEGER             NOT NULL CHECK (capacity > 0),
    status      VARCHAR(20)         NOT NULL DEFAULT 'SCHEDULED',
    created_at  TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP           NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trips_route_id ON trips(route_id);
CREATE INDEX idx_trips_trip_date ON trips(trip_date);

-- ========================
-- seats
-- ========================
CREATE TABLE IF NOT EXISTS seats (
    id          BIGSERIAL PRIMARY KEY,
    trip_id     BIGINT              NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    seat_number INTEGER             NOT NULL,
    UNIQUE (trip_id, seat_number)
);

CREATE INDEX idx_seats_trip_id ON seats(trip_id);

-- ========================
-- bookings
-- ========================
CREATE TABLE IF NOT EXISTS bookings (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT          NOT NULL REFERENCES trips(id),
    seat_id         BIGINT          REFERENCES seats(id),
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    from_stop_id    BIGINT          NOT NULL REFERENCES stops(id),
    to_stop_id      BIGINT          NOT NULL REFERENCES stops(id),
    status          VARCHAR(20)     NOT NULL DEFAULT 'CONFIRMED',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bookings_trip_id ON bookings(trip_id);
CREATE INDEX idx_bookings_seat_id ON bookings(seat_id);
CREATE INDEX idx_bookings_user_id ON bookings(user_id);
CREATE INDEX idx_bookings_status  ON bookings(status);

-- ========================
-- waitlist
-- ========================
CREATE TABLE IF NOT EXISTS waitlist (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT          NOT NULL REFERENCES trips(id),
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    from_stop_id    BIGINT          NOT NULL REFERENCES stops(id),
    to_stop_id      BIGINT          NOT NULL REFERENCES stops(id),
    position        INTEGER         NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'WAITING',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_waitlist_trip_id  ON waitlist(trip_id);
CREATE INDEX idx_waitlist_user_id  ON waitlist(user_id);
CREATE INDEX idx_waitlist_position ON waitlist(trip_id, position);
