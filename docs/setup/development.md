# Development

Local build, run, profiles, and testing for the Blueprint Server.

Related: [Configuration](configuration.md) · [Deployment](deployment.md)

## Requirements

- Java **21** or higher
- Maven **3.6+**
- PostgreSQL 12+ (for `localpostgres` / `docker`) or H2 (for `dev`)
- Docker (optional for run; **required** for Testcontainers during `mvn verify`)

## Profiles

| Profile | Role |
|:--------|:-----|
| **`dev`** | H2 in-memory DB; server port **8087** (`application-dev.yml`) |
| **`docker`** | PostgreSQL via env vars (`application-docker.yml`) |
| **`localpostgres`** | Local PostgreSQL example; port **8087** |
| **`test`** | Integration tests (`src/test/resources/application-test.yml`) |

Root `application.yml` may default `spring.profiles.active` to **`test`**. For a normal local run, override with `dev` or `localpostgres`.

## Run with Maven

```bash
mvn clean install

# H2
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Local PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=localpostgres
```

## Run with the JAR

```bash
mvn clean package
java -Dspring.profiles.active=dev -jar target/odm-platform-pp-blueprint-server-*.jar
```

## API docs (local)

| | Endpoint |
|:--|:---------|
| Swagger UI | http://localhost:8087/swagger-ui.html |
| OpenAPI | http://localhost:8087/v3/api-docs |

Product-plane prefix: **`/api/v2/pp/blueprint/`**.

## Testing

```bash
mvn -B verify -Dspring.profiles.active=test
```

Docker must be available for Testcontainers when integration tests run.

### IntelliJ: unit tests with coverage and Apache Velocity

This project uses **Apache Velocity** for blueprint templating. If you run tests **with IntelliJ’s code coverage** enabled, you may see:

`VelocityException: Could not initialize property keys deprecation map because DeprecatedRuntimeConstants.__$hits$__ field isn't properly named`

**Cause:** IntelliJ’s coverage instrumentation adds synthetic fields to bytecode. Velocity’s `DeprecationAwareExtProperties` reflects on `DeprecatedRuntimeConstants` and expects specific field names; injected coverage fields break that check (see JetBrains **IDEA-350212**).

**Fix in IntelliJ:** exclude the class from coverage for your test run configuration:

1. **Run → Edit Configurations…**
2. Select your JUnit configuration.
3. Open the **Coverage** tab (use **Modify options** if hidden).
4. Exclude: `org.apache.velocity.runtime.DeprecatedRuntimeConstants`  
   If needed, widen to `org.apache.velocity.**`.

**CI / Maven:** if JaCoCo hits the same error, exclude that class or `org.apache.velocity.runtime.*` in the JaCoCo configuration.

## Architecture (stack)

- **Spring Boot 3.5.x**
- **PostgreSQL** + Flyway (`src/main/resources/db/migration/postgresql/`), schema `odm_blueprint`
- **H2** for `dev`
- **Spring Data JPA**, **SpringDoc OpenAPI**
- **ODM git-utils** behind `GitProviderFactory`
- **Apache Velocity** for template render

---

↑ Back to [docs index](../README.md)
