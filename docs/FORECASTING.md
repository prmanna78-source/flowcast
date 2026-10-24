# FlowCast Forecasting Pipeline & Readiness Protocol

This document outlines the machine-learning pipeline, baseline methods, chronological evaluation protocol, and strict readiness requirements for FlowCast's traffic forecasting capabilities.

---

## 1. Principles of Truthful Forecasting

Under FlowCast's non-negotiable data integrity principles:
1. **No Fabricated Predictions**: FlowCast will never generate simulated traffic numbers, fake model accuracies, or synthetic confidence scores.
2. **Explicit State Representation**: When a model has not been trained or evaluated on sufficient genuine data, the forecast endpoint explicitly returns `INSUFFICIENT_DATA` or `PERMISSION_OR_COLLECTION_DISABLED`.
3. **Provider Routing vs. ML Forecast**: A routing provider's current duration estimate is never misrepresented as an in-house machine-learning forecast.

---

## 2. 5-Stage Readiness Protocol

FlowCast mandates five explicit criteria before predictive serving is unlocked:

| Step | Criterion | Requirement | Status Verification |
|------|-----------|-------------|---------------------|
| 1 | **Collection Enabled** | `FLOWCAST_TRAFFIC_HISTORY_ENABLED=true` | Checked via Spring environment |
| 2 | **Training Permission** | `FLOWCAST_TRAFFIC_TRAINING_PERMISSION=true` | Explicit legal/privacy consent |
| 3 | **Sample Volume** | Minimum 1,000 observations per sampling corridor | Verified against `traffic_observations` count |
| 4 | **Chronological Split** | Walk-forward split: 80% past train, 20% future test | No future leakage or random k-fold cross-validation |
| 5 | **Baseline Outperformance** | Model MAE must beat segment-hour historical median | Evaluated against baseline benchmark |

---

## 3. Data Schema

Historical observations collected by `DataCollectorService` are persisted in PostgreSQL:

```sql
CREATE TABLE traffic_observations (
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
```

### Monitored Visakhapatnam Corridors:
1. **Gajuwaka Junction (NH16)**: `17.6868° N, 83.2185° E`
2. **NAD Junction**: `17.7475° N, 83.2185° E`
3. **Maddilapalem Junction**: `17.7315° N, 83.3185° E`
4. **Dwaraka Nagar / RTC Complex**: `17.7245° N, 83.3075° E`
5. **Beach Road / RK Beach**: `17.7125° N, 83.3215° E`
6. **Pendurthi Junction**: `17.8285° N, 83.2015° E`

---

## 4. Model Architecture & Tooling

- **Library**: Oracle Tribuo 4.3.2 (`tribuo-regression-sgd`)
- **Baseline Predictor**: Segment-hour historical rolling median.
- **Evaluation Metric**: Mean Absolute Error (MAE) measured in km/h speed delta.
- **Readiness API**: `GET /api/v1/forecasts/readiness`
- **Forecast API**: `GET /api/v1/forecasts/traffic`
