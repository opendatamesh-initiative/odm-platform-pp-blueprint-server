# Deployment

Container deployment checklist for the Blueprint Server.

Related: [Configuration](configuration.md) · [Development](development.md)

## Build image

```bash
mvn clean package
docker build -t odm-platform-pp-blueprint-server .
```

## Run container

```bash
docker run -p 8080:8080 \
  -e DB_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/odm_blueprint \
  -e DB_USERNAME=your_username \
  -e DB_PASSWORD=your_password \
  -e PROFILES_ACTIVE=docker \
  odm-platform-pp-blueprint-server
```

## Checklist

1. Provision **PostgreSQL** (schema migrations run via Flyway on startup).
2. Inject `DB_*` (and optionally notification / `server.baseUrl`) — see [Configuration](configuration.md).
3. Ensure Git provider credentials are supplied **per request** by API clients (headers), not only via static env.
4. If notification integration is enabled, point `odm.product-plane.notification-service.address` at a reachable Notification Server and set `active: true`.
5. Expose Swagger / health as required by your platform.

---

↑ Back to [docs index](../README.md)
