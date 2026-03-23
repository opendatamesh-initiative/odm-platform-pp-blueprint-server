# ODM Platform Blueprint Server

## Overview

The ODM Platform Blueprint Server is a microservice in the [Open Data Mesh Platform](https://github.com/opendatamesh-initiative) product plane. It exposes Git-provider integration for blueprints: listing organizations and repositories, creating repositories, browsing branches, and working with provider-specific resources (for example projects or workspaces). It can integrate with the product-plane **notification service** as an observer to subscribe to events and emit or acknowledge notifications when that integration is enabled.

<!-- TOC -->

* [ODM Platform Blueprint Server](#odm-platform-blueprint-server)
    * [Overview](#overview)
        * [Key Features](#key-features)
        * [How It Works](#how-it-works)
    * [Setup and Start](#setup-and-start)
        * [Prerequisites](#prerequisites)
        * [Configuration](#configuration)
            * [Environment Variables (Docker Profile)](#environment-variables-docker-profile)
            * [Notification Service and Observer Identity](#notification-service-and-observer-identity)
        * [Running Locally](#running-locally)
            * [Option 1: Using Maven](#option-1-using-maven)
            * [Option 2: Using Docker](#option-2-using-docker)
            * [Option 3: Using the JAR](#option-3-using-the-jar)
        * [Default Port](#default-port)
        * [API Documentation](#api-documentation)
    * [Main Capabilities](#main-capabilities)
        * [1. Git Provider Operations](#1-git-provider-operations)
        * [2. Blueprint Repository Context](#2-blueprint-repository-context)
        * [3. Notification Integration](#3-notification-integration)
    * [Additional Endpoints](#additional-endpoints)
    * [Architecture](#architecture)
    * [Testing](#testing)
    * [License](#license)

<!-- TOC -->

### Key Features

- **Multi-provider Git integration**: GitHub, GitLab, Bitbucket, and Azure DevOps via the shared `git-utils` abstraction and a blueprint-local `GitProviderFactory`
- **Organizations and repositories**: Paginated listing, filters, and repository creation with standardized request/response models
- **Provider extensions**: Custom resource definitions and custom resources for provider-specific concepts (for example Bitbucket projects or Azure DevOps projects)
- **Optional notification client**: HTTP client to the ODM notification service (`NotificationClient` / `NotificationClientConfig`) when `odm.product-plane.notification-service.active` is `true`
- **Persistence and migrations**: PostgreSQL with Flyway; Hibernate schema `odm_blueprint` (see `application.yml`)

### How It Works

1. Callers identify the **Git provider** (type and optional base URL) and pass **authentication** through HTTP headers, consistent with other product-plane services.
2. The service builds a provider-specific `GitProvider` (or `GitProviderExtension` for unauthenticated metadata) through `GitProviderFactory`.
3. **GitProvidersUtilsService** orchestrates operations (organizations, repositories, branches, custom resources) and maps results to versioned REST resources under `rest.v2.resources`.
4. If the notification integration is active, the service can interact with the notification API (subscribe, emit, processing callbacks) using `server.baseUrl` and `blueprint.observer.*` identity fields.

## Setup and Start

### Prerequisites

- Java 21 or higher
- Maven 3.6+
- PostgreSQL 12+ (for production or `localpostgres` / `docker` profiles) or H2 (for the `dev` profile)
- Docker (optional, for containerized deployment; required for Testcontainers during `mvn verify`)

### Configuration

Spring Boot profiles used in this repository:

- **`dev`**: Development profile with H2 in-memory database (`application-dev.yml`; default server port **8087** in that file)
- **`docker`**: Docker profile with PostgreSQL via environment variables (`application-docker.yml`)
- **`localpostgres`**: Local PostgreSQL example (`application-localpostgres.yml`; port **8087**)
- **`test`**: Used by integration tests (`src/test/resources/application-test.yml`)

The root `application.yml` sets `spring.profiles.active` to **`test`**. For a normal local run, override the profile (for example `dev` or `localpostgres`) as shown below.

#### Environment Variables (Docker Profile)

When running with the `docker` profile, configure:

```bash
DB_JDBC_URL=jdbc:postgresql://localhost:5432/odm_blueprint
DB_USERNAME=your_username
DB_PASSWORD=your_password
```

The Docker image sets `PROFILES_ACTIVE=docker` by default (see `Dockerfile`).

#### Notification Service and Observer Identity

Properties consumed by `NotificationClientConfig`:

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
| `odm.product-plane.notification-service.address` | Base URL of the [ODM Platform Notification Server](https://github.com/opendatamesh-initiative/odm-platform-pp-notification-server) |
| `odm.product-plane.notification-service.active` | If `true`, uses the real HTTP client and checks connectivity at startup; if `false`, uses an in-process no-op client |
| `blueprint.observer.name` / `displayName` | Observer identity when subscribing to the notification service |
| `server.baseUrl` | Public base URL of this service (used as the observer callback base URL when integrated) |

Environment examples (relaxed binding): `ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ADDRESS`, `ODM_PRODUCT_PLANE_NOTIFICATION_SERVICE_ACTIVE`.

### Running Locally

#### Option 1: Using Maven

```bash
# Build the project
mvn clean install

# Run with dev profile (H2)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run against local PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=localpostgres
```

#### Option 2: Using Docker

```bash
# Build the JAR then the image
mvn clean package
docker build -t odm-platform-pp-blueprint-server .

# Run the container
docker run -p 8080:8080 \
  -e DB_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/odm_blueprint \
  -e DB_USERNAME=your_username \
  -e DB_PASSWORD=your_password \
  -e PROFILES_ACTIVE=docker \
  odm-platform-pp-blueprint-server
```

#### Option 3: Using the JAR

```bash
# Build the JAR
mvn clean package

# Run the JAR (adjust version if needed)
java -jar target/odm-platform-pp-blueprint-server-1.0.0.jar

# Or with a specific profile
java -Dspring.profiles.active=dev -jar target/odm-platform-pp-blueprint-server-1.0.0.jar
```

### Default Port

`application.yml` sets **8080**. The `dev` and `localpostgres` profiles override this to **8087** for local convenience. Override with `server.port` or `SERVER_PORT` if needed.

### API Documentation

When the application exposes REST controllers, use SpringDoc OpenAPI:

- **Swagger UI**: http://localhost:8080/swagger-ui.html (or `/swagger-ui/index.html` depending on SpringDoc version)
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

Integration tests use the product-plane prefix **`/api/v2/pp/blueprint/`** (for example `git-providers` under that path). See `RoutesV2` in tests and the live OpenAPI document for the current surface.

## Main Capabilities

### 1. Git Provider Operations

Through **GitProvidersUtilsService** (implementation: `GitProvidersUtilsServiceImpl`), the service supports:

- Listing **organizations** and **repositories** (with pagination and filters)
- **Creating** repositories
- Listing **branches**
- **Custom resource definitions** and **custom resources** for provider-specific types

Authentication and provider instance selection follow the `ProviderIdentifierRes` model and `HttpHeaders` passed into the service layer. REST mapping for these operations is aligned with **`/api/v2/pp/blueprint/`**; consult Swagger for exact paths and payloads once controllers are registered.

### 2. Blueprint Repository Context

- List **commits** (with `CommitSearchOptions`), **branches**, and **tags**
- **Add tags** to a repository

### 3. Notification Integration

This service includes a **notification client** compatible with the ODM notification API (subscribe, emit, notification status updates). For observer contract, payload shapes, and HTTP endpoints on the notification side, see the **ODM Platform Notification Server** README and Swagger.

When you implement an incoming **observer** endpoint on this service, follow the same path conventions as documented there (for example `POST {observerBaseUrl}/api/v2/up/observer/notifications` for API version `v2`).

## Additional Endpoints

Actuator and infrastructure endpoints follow Spring Boot defaults when starters are enabled. Application REST resources will appear under `/api/v2/pp/blueprint/` as controllers are added. Use **Swagger UI** for the authoritative list.

## Architecture

- **Spring Boot 3.5.7**: Core framework
- **PostgreSQL**: Primary database with Flyway migrations under `src/main/resources/db/migration/postgresql/`
- **H2**: In-memory database for the `dev` profile
- **Spring Data JPA**: Persistence (schema `odm_blueprint` in default configuration)
- **SpringDoc OpenAPI**: API documentation
- **Apache HttpClient 5**: HTTP client stack (via Spring and REST utilities)
- **ODM git-utils**: Provider implementations behind `GitProviderFactory`

## Testing

```bash
mvn -B verify -Dspring.profiles.active=test
```

Docker must be available for Testcontainers when integration tests run.

## License

Licensed under the Apache License, Version 2.0.
