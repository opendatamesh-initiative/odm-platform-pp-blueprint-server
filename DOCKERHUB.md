# ODM Platform Blueprint Server

The Blueprint Server is the product-plane blueprint service of [Open Data Mesh Platform](https://github.com/opendatamesh-initiative) V2. A blueprint is a versioned, parameterized Git repository of templates and artifacts that captures an organization standard. The service registers blueprints and their versions, connects to Git providers (GitHub, GitLab, Bitbucket, Azure DevOps), and instantiates blueprints onto real data-product repositories, then rolls new blueprint versions onto them. Optionally, it checks on publication that protected files in data-product repositories weren't changed.

- **Source code:** [opendatamesh-initiative/odm-platform-pp-blueprint-server](https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server)
- **Documentation:** [docs](https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server/blob/main/docs/README.md)
- **Issues:** [GitHub issues](https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server/issues)

## Tags

Each [GitHub release](https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server/releases) publishes one image tagged with its version, without the `v` prefix:

```bash
docker pull opendatamesh/odm-platform-blueprint:<version>
```

There is no `latest` tag: always pin a version. Tags before `2.0.0` are ODM Platform V1 images, built from [odm-platform](https://github.com/opendatamesh-initiative/odm-platform), and are configured differently.

## Requirements

| Dependency | Required | Notes |
| --- | --- | --- |
| PostgreSQL | Yes | Flyway creates and migrates the schema on startup. |
| Git provider access | Yes | API clients send their Git credentials with each request, as headers. See [Git providers](https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server/blob/main/docs/service/git-providers.md). |
| Policy service and [Registry](https://hub.docker.com/r/opendatamesh/odm-platform-registry) | Only for the protected-resources validator | Off by default. See [Protected resources validator](#protected-resources-validator). |

## Quick start

Starts the Blueprint Server against an existing PostgreSQL database:

```bash
docker run --name odm-blueprint -p 8080:8080 \
  -e DB_JDBC_URL=jdbc:postgresql://db.example.com:5432/blueprint \
  -e DB_USERNAME=blueprint \
  -e DB_PASSWORD=change-me \
  opendatamesh/odm-platform-blueprint:<version>
```

When it's up, the API (prefix `/api/v2/pp/blueprint/`) is documented at `http://localhost:8080/swagger-ui.html` (OpenAPI at `/v3/api-docs`).

## Protected resources validator

When enabled, the Blueprint Server registers itself as an engine in the Policy service and checks, on data-product version publication, that the paths a blueprint declares as protected weren't changed. It reads the publication from the Registry and clones repositories with its own Git credentials, never with credentials taken from the event.

Spring Boot reads nested configuration from the `SPRING_APPLICATION_JSON` environment variable:

```bash
docker run --name odm-blueprint -p 8080:8080 \
  -e DB_JDBC_URL=jdbc:postgresql://db.example.com:5432/blueprint \
  -e DB_USERNAME=blueprint \
  -e DB_PASSWORD=change-me \
  -e SPRING_APPLICATION_JSON='{
    "server": { "baseUrl": "https://blueprint.example.com" },
    "blueprint": { "validator": {
      "active": true,
      "git": { "credentials": [
        { "provider-type": "GITHUB", "auth-type": "PAT", "token": "change-me" }
      ] }
    } },
    "odm": { "product-plane": {
      "policy-service": { "active": true, "address": "http://policy.example.com:8005" },
      "registry-service": { "active": true, "address": "http://registry.example.com:8080" }
    } }
  }' \
  opendatamesh/odm-platform-blueprint:<version>
```

`server.baseUrl` must be a URL the Policy service can reach: it's where the Policy service sends evaluation requests. Details: [Protected resources](https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server/blob/main/docs/service/protected-resources.md).

## Environment variables

### Image

| Variable | Description | Default |
| --- | --- | --- |
| `DB_JDBC_URL` | JDBC URL of the PostgreSQL database (`spring.datasource.url`). | none, required |
| `DB_USERNAME` | Database user (`spring.datasource.username`). | none, required |
| `DB_PASSWORD` | Database password (`spring.datasource.password`). | none, required |
| `PROFILES_ACTIVE` | Spring profiles to activate. The `docker` profile maps the three `DB_*` variables above. | `docker` |
| `JAVA_OPTS` | Extra JVM options, for example `-Xmx512m`. | empty |
| `SPRING_PROPS` | Extra arguments added to the `java` command line before `-jar`, such as `-Dserver.port=9000`. It does not accept JSON: use `SPRING_APPLICATION_JSON` for that. | empty |

### Application

Every property can be set with `SPRING_APPLICATION_JSON` or as an environment variable, using Spring Boot relaxed binding (for example, `blueprint.validator.active` becomes `BLUEPRINT_VALIDATOR_ACTIVE`).

| Property | Description | Default |
| --- | --- | --- |
| `server.port` | HTTP port. | `8080` |
| `server.baseUrl` | Externally reachable URL of the Blueprint Server, registered in the Policy service as the validator's address. | `http://localhost:8080` |
| `spring.jpa.properties.hibernate.default_schema` | Database schema used by Hibernate and Flyway. | `odm_blueprint` |
| `blueprint.validator.active` | Enable the protected-resources validator. | `false` |
| `blueprint.validator.evaluation-timeout-seconds` | Maximum time for one evaluation, clones included. When it's exceeded, the check fails. | `120` |
| `blueprint.validator.policy.blocking` | Whether a failed check blocks publication. Applied only when the policy is first created in the Policy service. | `true` |
| `blueprint.validator.git.credentials` | List of Git credentials used by the validator to clone repositories: `provider-type`, `provider-base-url`, `auth-type`, `token`, `username`. | empty |
| `odm.product-plane.policy-service.active` | Use the Policy service. Required by the validator. | `false` |
| `odm.product-plane.policy-service.address` | Base URL of the Policy service. | empty |
| `odm.product-plane.registry-service.active` | Use the Registry to read publications. Required by the validator. | `false` |
| `odm.product-plane.registry-service.address` | Base URL of the Registry. | empty |

The full list is in [Configuration](https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server/blob/main/docs/setup/configuration.md).

## Ports and volumes

- **Port:** `8080` (HTTP), or the value of `server.port`.
- **Volumes:** none. All state is in PostgreSQL; Git repositories are cloned to temporary directories.

## Learn more

- [Blueprint process](https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server/blob/main/docs/service/blueprint-process.md)
- [Multi-repository and composition](https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server/blob/main/docs/service/repositories-and-composition.md)
- [Blueprint manifest](https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server/blob/main/src/main/java/org/opendatamesh/platform/pp/blueprint/manifest/README.md)
- [Deployment](https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server/blob/main/docs/setup/deployment.md)

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
