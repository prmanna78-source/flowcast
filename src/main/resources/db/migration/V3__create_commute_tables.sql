CREATE TABLE IF NOT EXISTS saved_commutes (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL DEFAULT 'local_user',
    name VARCHAR(120) NOT NULL,
    origin_name VARCHAR(255) NOT NULL,
    origin_latitude DOUBLE PRECISION NOT NULL CHECK (origin_latitude BETWEEN -90 AND 90),
    origin_longitude DOUBLE PRECISION NOT NULL CHECK (origin_longitude BETWEEN -180 AND 180),
    destination_name VARCHAR(255) NOT NULL,
    destination_latitude DOUBLE PRECISION NOT NULL CHECK (destination_latitude BETWEEN -90 AND 90),
    destination_longitude DOUBLE PRECISION NOT NULL CHECK (destination_longitude BETWEEN -180 AND 180),
    target_arrival_time VARCHAR(8) NOT NULL,
    weekdays VARCHAR(64) NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI',
    safety_buffer_minutes INTEGER NOT NULL DEFAULT 15,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_calculated_at TIMESTAMPTZ,
    last_departure_recommendation VARCHAR(16),
    last_estimated_duration_minutes DOUBLE PRECISION,
    last_traffic_delay_minutes INTEGER,
    last_exposure_category VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_saved_commutes_user ON saved_commutes (user_id);
CREATE INDEX IF NOT EXISTS idx_saved_commutes_enabled ON saved_commutes (is_enabled);

CREATE TABLE IF NOT EXISTS trip_feedback (
    id BIGSERIAL PRIMARY KEY,
    commute_id BIGINT REFERENCES saved_commutes(id) ON DELETE SET NULL,
    target_arrival_time VARCHAR(8),
    actual_departure_time VARCHAR(8),
    actual_arrival_time VARCHAR(8),
    actual_duration_minutes INTEGER,
    was_on_time BOOLEAN,
    was_departure_useful BOOLEAN,
    disruption_experienced VARCHAR(120),
    feedback_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trip_feedback_commute ON trip_feedback (commute_id);
