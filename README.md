# ODM Platform — service repository template

This repository is a **starting point** for new [Open Data Mesh Platform](https://github.com/opendatamesh-initiative) microservices (registry, blueprint, or domain-specific APIs). Use it as a [GitHub template](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template) or clone it, then rename packages, configuration keys, and infrastructure identifiers to match your service.

The layout and configuration style are aligned with product-plane services such as the **[ODM Platform Registry Server](https://github.com/opendatamesh-initiative/odm-platform-pp-registry-server)** (see that project’s README for production-oriented examples: `policy-service`, observer subscriptions, event matrices, Git provider auth). **This template only ships a minimal notification client setup**; it does not implement policy integration, incoming observer endpoints, or automatic event subscription.

<!-- TOC -->

* [Overview](#overview)
* [What you get](#what-you-get)
* [Prerequisites](#prerequisites)
* [Setup instructions](#setup-instructions)
  * [1. Database configuration](#1-database-configuration)
  * [2. Building the project](#2-building-the-project)
  * [3. Running the application](#3-running-the-application)
* [Configuration options](#configuration-options)
  * [Server configuration](#server-configuration)
  * [Spring configuration](#spring-configuration)
  * [Database and Flyway](#database-and-flyway)
  * [ODM product-plane configuration](#odm-product-plane-configuration)
  * [Observer identifiers (`service-template`)](#observer-identifiers-service-template)
  * [Notification client (`NotificationClientConfig`)](#notification-client-notificationclientconfig)
  * [Logging configuration](#logging-configuration)
  * [Docker and environment variables](#docker-and-environment-variables)
* [API documentation](#api-documentation)
* [Testing](#testing)
* [Customization checklist](#customization-checklist)
* [Summary of string locations](#summary-of-string-locations)

<!-- TOC -->

## Overview

The template is a Spring Boot application with JPA, Flyway (PostgreSQL dialect), Actuator, SpringDoc OpenAPI, shared REST error handling, CRUD helpers, and a **notification client** bean you can use to register with the product-plane notification service when you build a real service.

## What you get

- **Spring Boot 3.5**, **Java 21**, JPA, Flyway, Testcontainers in tests, DevTools (optional).
- **Profiles:** `application-dev.yml` (H2), `application-docker.yml`, `application-localpostgres.yml`, plus test resources for integration tests.
- **CI/CD:** GitHub Actions (`verify` on `main`; release workflow and Docker image publishing — configurable).
- **Notification:** `NotificationClientConfig` + `NotificationClient` / `NotificationClientImpl` (HTTP when `odm.product-plane.notification-service.active` is `true`, in-process no-op when `false`). There is **no** `policy-service` wiring, **no** startup subscription to event types, and **no** sample `NotificationEventHandler` / observer controller — add those when you follow patterns from the Registry (or your own design).

## Prerequisites

- **Java 21** (see `pom.xml` `java.version`).
- **Maven 3.6+**.
- **Docker** (for Testcontainers during `mvn verify` / integration tests).
- **PostgreSQL** for profiles that use JDBC PostgreSQL (e.g. default `application.yml` + local Postgres); **H2** for the `dev` profile.

## Setup instructions

### 1. Database configuration

**PostgreSQL (typical local / production-style)** — example fragment; adjust host, database, user, and match `spring.jpa.properties.hibernate.default_schema` with your Flyway/JPA schema:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres
    username: postgres
    password: your_password
    driver-class-name: org.postgresql.Driver
  jpa:
    properties:
      hibernate:
        default_schema: odm_service_template
```

See `application-localpostgres.yml` for a concrete profile.

**H2 (development)** — `application-dev.yml` uses an in-memory H2 database with PostgreSQL compatibility mode for quick runs without Postgres.

### 2. Building the project

```bash
git clone <your-repo-url>
cd odm-platform-service-template
mvn clean install
```

### 3. Running the application

```bash
# Default profile from application.yml is "test"; override as needed
mvn spring-boot:run

# Examples
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn spring-boot:run -Dspring-boot.run.profiles=localpostgres
```

**Docker** (after `mvn package`):

```bash
docker build -t odm-platform-service-template .
docker run -p 8080:8080 \
  -e PROFILES_ACTIVE=docker \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/postgres \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=your_password \
  odm-platform-service-template
```

Adapt datasource environment variable names to match Spring Boot relaxed binding for your `application-docker.yml`.

## Configuration options

### Server configuration

```yaml
server:
  port: 8080
  baseUrl: http://localhost:8080   # Used by the notification client as the observer callback base URL when integrated
```

### Spring configuration

```yaml
spring:
  application:
    name: odm-platform-service-template
  banner:
    charset: UTF-8
    mode: console
```

Maven resource filtering exposes `@project.version@` and `@project.name@` under `info.*` in `application.yml`.

### Database and Flyway

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration/postgresql
  jpa:
    properties:
      hibernate:
        default_schema: odm_service_template
        ddl-auto: validate
```

Migration scripts live under `src/main/resources/db/migration/postgresql/`. Keep `default_schema` consistent with your SQL.

### ODM product-plane configuration

Properties read by **`NotificationClientConfig`** (no Java defaults — must be present in YAML, a profile, or the environment):

```yaml
odm:
  product-plane:
    notification-service:
      address: http://localhost:8001   # Notification service base URL
      active: false                     # true: real HTTP client + connection check at startup; false: dummy client
```

| Property | Description |
|----------|-------------|
| `odm.product-plane.notification-service.address` | Base URL of the product-plane notification service. Still required when `active` is `false` (placeholder resolution). |
| `odm.product-plane.notification-service.active` | Enables the real `NotificationClientImpl` and startup connectivity check; `false` uses an in-process implementation that logs and skips HTTP. |

**Not in this template:** the Registry documents `odm.product-plane.policy-service.*` and startup event subscriptions. Those are **Registry-specific**; this repository does not read `policy-service` properties. Add them in your service when you implement the same behaviour.

Environment examples (relaxed binding): `ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ADDRESS`, `ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ACTIVE`.

### Observer identifiers (`service-template`)

Analogous to the Registry’s `registry.observer.*` keys, this template uses a **renameable YAML prefix** `service-template` for observer identity fields consumed by `NotificationClientConfig`:

```yaml
service-template:
  observer:
    name: service-template
    displayName: Service template
```

After renaming the prefix (e.g. to `blueprint`), update `@Value("${service-template.observer...}")` in `NotificationClientConfig.java` to match.

### Notification client (`NotificationClientConfig`)

Spring `@Configuration` class that:

1. Loads **`server.baseUrl`**, **`service-template.observer.name`** / **`displayName`** (with defaults in `@Value`), and **`odm.product-plane.notification-service.*`**.
2. Exposes a **`NotificationClient`** bean: if `active` is `true`, builds `NotificationClientImpl`, asserts connection to the notification service, and returns it; if `false`, returns a dummy client that warns on `notifyEvent`, `subscribeToEvents`, and processing callbacks.

Unlike the Registry, this template **does not** call `subscribeToEvents` at startup or expose an observer REST endpoint — you add that when you integrate.

### Logging configuration

```yaml
logging:
  pattern:
    console: "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(%5p) %clr(${PID:- }){magenta} %clr(---){faint} %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m%n%wEx"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  level:
    org.springframework.web.filter.CommonsRequestLoggingFilter: DEBUG
```

### Docker and environment variables

The `Dockerfile` sets `PROFILES_ACTIVE` (default `docker`) and passes `JAVA_OPTS` and `SPRING_PROPS` into the JVM command line before `-jar`. For JSON-style overrides similar to the Registry, use Spring Boot’s supported mechanisms (e.g. `SPRING_APPLICATION_JSON` or profile-specific YAML) rather than assuming a single variable format unless your entrypoint maps it.

Common variables:

- `SPRING_PROFILES_ACTIVE` — active profiles
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `SERVER_PORT`
- `ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ADDRESS`, `ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ACTIVE`

## API documentation

With SpringDoc on the classpath, when you add controllers the UI is typically:

- Swagger UI: `http://localhost:<port>/swagger-ui/index.html` (or `/swagger-ui.html` depending on version)
- OpenAPI JSON: `http://localhost:<port>/v3/api-docs`

The bare template may not expose application REST endpoints until you implement them.

## Testing

```bash
mvn -B verify -Dspring.profiles.active=test
```

Ensure Docker is available for Testcontainers when integration tests run.

---

## Customization checklist

Work through these in order. Prefer IDE **refactor → rename package** where possible; then fix YAML, SQL, Dockerfile, workflows, and hard-coded classpath patterns.

### 1. Maven coordinates (`pom.xml`)

| Location | Action |
|----------|--------|
| `groupId` | Your organisation (e.g. `org.opendatamesh`). |
| `artifactId` | Must match the JAR name glob in the `Dockerfile`. |
| `version` | Initial version; release tags should match for CD. |
| `name` / `description` | Service branding; `name` feeds `@project.name@` in YAML. |

### 2. Java base package

Rename `org.opendatamesh.platform.service.template` under `src/main/java` and `src/test/java`, including `OdmPlatformServiceTemplateApplication` and `SpringBootTest` references.

### 3. Configuration and database

- Adjust `application.yml` and profiles (ports, datasource, `odm.product-plane.notification-service`, `service-template` prefix).
- Align Flyway scripts and `default_schema` with your domain (replace sample `service_template` table if needed).

### 4. Optional: API exception type

Rename `ServiceTemplateApiException` and subclasses, and update `ResponseExceptionHandler` and any `catch` sites in **your** code.

### 5. Integration tests: client mocks

Update the Ant pattern in `TestConfig` from `classpath*:org/opendatamesh/platform/service/template/client/**/*.class` to your new package path.

### 6. Docker and GitHub Actions

- `Dockerfile` `COPY` glob vs `artifactId`.
- `.github/workflows/ci.yml` and `cicd.yml`: branch names, `IMAGE_NAME`, Docker Hub org, Maven `settings.xml` server id if used.

### 7. Replace this README

After forking from the template, replace this document with a service-specific README (like the Registry’s) describing your APIs, events, and operational config.

---

## Summary of string locations

Search and replace template-specific tokens:

- `odm-platform-service-template` — POM `artifactId`, Spring app name, workflows, Docker.
- `org.opendatamesh.platform.service.template` — packages, `TestConfig` classpath pattern.
- `OdmPlatformServiceTemplateApplication` — main class and tests.
- `service-template` / `service_template` / `odm_service_template` — YAML and SQL.
- `ServiceTemplateApiException` — if you rebrand exceptions.

After customization, run `mvn verify` and a full local or container run with real datasource and `odm.product-plane.notification-service.*` before your first release.
