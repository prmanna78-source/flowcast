CREATE TABLE IF NOT EXISTS traffic_observations (
    id BIGSERIAL PRIMARY KEY,
    sampling_name VARCHAR(120),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    current_speed DOUBLE PRECISION,
    free_flow_speed DOUBLE PRECISION,
    travel_time_seconds INTEGER,
    free_flow_travel_time_seconds INTEGER,
    confidence_score INTEGER,
    road_class VARCHAR(32),
    observed_at TIMESTAMPTZ NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_traffic_obs_time ON traffic_observations (observed_at);
CREATE INDEX IF NOT EXISTS idx_traffic_obs_loc ON traffic_observations (latitude, longitude);

CREATE TABLE IF NOT EXISTS weather_observations (
    id BIGSERIAL PRIMARY KEY,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    temperature_celsius DOUBLE PRECISION,
    relative_humidity_percent DOUBLE PRECISION,
    wind_speed_kmh DOUBLE PRECISION,
    precipitation_mm DOUBLE PRECISION,
    weather_code INTEGER,
    observed_at TIMESTAMPTZ NOT NULL,
    source VARCHAR(64) NOT NULL DEFAULT 'Open-Meteo',
    collected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_weather_obs_time ON weather_observations (observed_at);

CREATE TABLE IF NOT EXISTS community_reports (
    id BIGSERIAL PRIMARY KEY,
    report_type VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    status VARCHAR(32) NOT NULL DEFAULT 'REPORTED',
    reporter_hash VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ,
    review_notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_community_reports_status ON community_reports (status);
CREATE INDEX IF NOT EXISTS idx_community_reports_created ON community_reports (created_at DESC);

CREATE TABLE IF NOT EXISTS collector_runs (
    id BIGSERIAL PRIMARY KEY,
    collector_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    records_collected INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_message TEXT
);

CREATE TABLE IF NOT EXISTS model_evaluations (
    id BIGSERIAL PRIMARY KEY,
    model_name VARCHAR(120) NOT NULL,
    version VARCHAR(32) NOT NULL,
    sample_count BIGINT NOT NULL,
    mae DOUBLE PRECISION NOT NULL,
    baseline_mae DOUBLE PRECISION,
    horizon_minutes INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details TEXT
);
