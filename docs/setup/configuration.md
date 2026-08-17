# Configuration

Properties commonly managed for the Blueprint Server.

Related: [Development](development.md) · [Deployment](deployment.md)

## Database (Docker / Postgres profiles)

```bash
DB_JDBC_URL=jdbc:postgresql://localhost:5432/odm_blueprint
DB_USERNAME=your_username
DB_PASSWORD=your_password
```

The Docker image sets `PROFILES_ACTIVE=docker` by default (see `Dockerfile`).

## Notification service and observer identity

```yaml
odm:
  product-plane:
    notification-service:
      address: http://localhost:8001
      active: false

blueprint:
  observer:
    name: blueprint
    displayName: Blueprint
```

| Property | Description |
|----------|-------------|
| `odm.product-plane.notification-service.address` | Base URL of the ODM Platform Notification Server |
| `odm.product-plane.notification-service.active` | If `true`, real HTTP client + startup connectivity check; if `false`, in-process no-op client |
| `blueprint.observer.name` / `displayName` | Observer identity when subscribing |
| `server.baseUrl` | Public base URL of this service (observer callback base) |

Environment examples (relaxed binding):  
`ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ADDRESS`, `ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ACTIVE`.

## Server port

- Default in root `application.yml`: **8080**
- `dev` / `localpostgres`: **8087**
- Override with `server.port` or `SERVER_PORT`

---

↑ Back to [docs index](../README.md)
