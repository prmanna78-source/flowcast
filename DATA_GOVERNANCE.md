# Data governance

| Source | Fields | Purpose | Retention/training |
|---|---|---|---|
| TomTom Traffic Incidents | Provider incident fields and returned geometry | Current incident display | Do not persist until applicable TomTom terms are reviewed |
| TomTom Flow Segment Data | Returned speed and segment metadata | Selected-point display | History and ML disabled by default |
| Open-Meteo | Current forecast variables and valid time | Weather display | Review applicable terms before retention |

FlowCast never copies retrieval time into an upstream observation time. It distinguishes a successful empty response from a failed request. No traffic samples, incidents, weather records, routes, or predictions are seeded by the migration. The structural migration creates only application configuration tables.

Set `FLOWCAST_TRAFFIC_HISTORY_ENABLED=true` and `FLOWCAST_TRAFFIC_TRAINING_PERMISSION=true` only after legal and provider-term review has confirmed storage and derived-model permissions for the intended deployment.
