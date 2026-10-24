CREATE TABLE IF NOT EXISTS data_source_health (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_success_at TIMESTAMPTZ,
    last_error_at TIMESTAMPTZ,
    last_error_category VARCHAR(64),
    request_count BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS sampling_point (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sampling_point_coordinates ON sampling_point (latitude, longitude);
