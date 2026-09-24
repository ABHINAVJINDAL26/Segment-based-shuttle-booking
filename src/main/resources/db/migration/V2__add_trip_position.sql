ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS current_stop_sequence INTEGER NOT NULL DEFAULT 1;

ALTER TABLE trips
    ADD CONSTRAINT trips_current_stop_sequence_positive
    CHECK (current_stop_sequence > 0);
