# Security

Keep server API keys in environment variables or a secret manager. Never commit `.env` or put the server key in browser JavaScript. Use a distinct restricted browser key for map tiles, limited to the authorized origins and APIs.

Provider errors returned by the application do not include credentials or stack traces. Do not log credential-bearing URLs. Deploy behind HTTPS, configure database credentials outside source control, and restrict actuator endpoints in production.
