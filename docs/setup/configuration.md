# Configuration

Properties commonly managed for the Blueprint Server.

Related: [Development](development.md) · [Deployment](deployment.md) · [Protected resources](../service/protected-resources.md)

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

## Protected-resources validator

Off by default. When `blueprint.validator.active` is `true`, the service registers a Policy engine/policy and evaluates protected paths on data-product version publication. See [Protected resources](../service/protected-resources.md).

```yaml
blueprint:
  validator:
    active: false
    evaluation-timeout-seconds: 120
    policy-engine:
      name: blueprint-service-validator
      display-name: Blueprint Service Validator
    policy:
      name: Protected Resources Integrity
      blocking: true
    git:
      credentials:
        - provider-type: GITHUB
          auth-type: PAT
          token: your-git-token

odm:
  product-plane:
    policy-service:
      active: false
      address: http://localhost:8005
    registry-service:
      active: false
      address: http://localhost:8086
```

| Property | Description |
|----------|-------------|
| `blueprint.validator.active` | If `true`, register with Policy and evaluate protected resources |
| `blueprint.validator.evaluation-timeout-seconds` | Fail closed if evaluation (including clones) exceeds this |
| `blueprint.validator.policy.blocking` | Whether Policy treats a failed check as blocking publication |
| `blueprint.validator.git.credentials` | Service-level Git auth for policy-path clones (never taken from the event) |
| `odm.product-plane.policy-service.active` / `address` | Policy Server used to register the engine and receive evaluate calls |
| `odm.product-plane.registry-service.active` / `address` | Registry V2 used only by the Policy V1 adapter to reconstruct publication metadata |

Do not commit real Git tokens. Use environment overrides (relaxed binding), for example `BLUEPRINT_VALIDATOR_ACTIVE`.

## Server port

- Default in root `application.yml`: **8080**
- `dev` / `localpostgres`: **8087**
- Override with `server.port` or `SERVER_PORT`

---

↑ Back to [docs index](../README.md)
