# ODM Platform Blueprint Server

> The product-plane blueprint service for the [Open Data Mesh Platform](https://dpds.opendatamesh.org/) —  
> standardize data-product code and artifacts as reusable blueprints, and apply them to real data-product repositories across the organization.

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=flat-square" alt="License: Apache 2.0"></a>
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Java-21-ED8B00.svg?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F.svg?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 3.5"></a>
</p>

<p align="center">
  <a href="#quick-start-local">Quick start</a> ·
  <a href="#run-with-docker">Docker</a> ·
  <a href="#documentation">Documentation</a> ·
  <a href="#contributing">Contributing</a>
</p>

---

## Why this service

In a data mesh, every product team should not reinvent pipelines, descriptors, infra stubs, or repo layout from scratch.  
**Blueprints** capture that shared standard — versioned, parameterized Git repositories of templates and artifacts — so the organization can **reuse one definition across many real data products**.

The Blueprint Server is the product-plane service that makes that real: it registers blueprints and their versions, connects to Git providers, and **instantiates** (and later evolves) those standards onto actual data-product repositories.

```mermaid
flowchart LR
  A[Organization blueprints<br/>versioned standards] --> B[Instantiate]
  B --> C[Data product repos<br/>shared scaffolding]
  C --> D[Teams customize<br/>their products]
```

How instantiate / update work under the hood: [Blueprint process](docs/service/blueprint-process.md).

---

## Highlights

| Capability | Details |
|:-----------|:--------|
| **Standardize & reuse** | Turn org-wide patterns into versioned blueprints applied to many data-product repos |
| **Blueprints & versions** | Persist blueprint identity, Git repo metadata, and versioned manifests |
| **Instantiate onto real products** | Render parameterized templates into a target data-product repository |
| **Evolve with new versions** | Roll a new blueprint version onto products that already exist (details in the process guide) |
| **Git providers** | GitHub, GitLab, Bitbucket, Azure DevOps via shared `git-utils` |
| **Manifest & Velocity** | Parameterized templates driven by the blueprint manifest |
| **Notifications** | Optional client to the ODM Notification Server |

---

## Quick start (local)

**Requirements:** Java **21** · Maven **3.6+** · Docker (for Testcontainers / `mvn verify`)

```bash
git clone https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server.git
cd odm-platform-pp-blueprint-server

mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile uses an in-memory **H2** database (default port **8087** in that profile). When the server is up:

| | Endpoint |
|:--|:---------|
| **Swagger UI** | [http://localhost:8087/swagger-ui.html](http://localhost:8087/swagger-ui.html) |
| **OpenAPI** | [http://localhost:8087/v3/api-docs](http://localhost:8087/v3/api-docs) |

API prefix: **`/api/v2/pp/blueprint/`**.

More detail (profiles, Postgres, tests): [Development](docs/setup/development.md)

---

## Run with Docker

```bash
mvn clean package
docker build -t odm-platform-pp-blueprint-server .

docker run -p 8080:8080 \
  -e DB_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/odm_blueprint \
  -e DB_USERNAME=your_username \
  -e DB_PASSWORD=your_password \
  -e PROFILES_ACTIVE=docker \
  odm-platform-pp-blueprint-server
```

| Guide | Link |
|:------|:-----|
| Step-by-step deploy | [Deployment](docs/setup/deployment.md) |
| Properties to manage | [Configuration](docs/setup/configuration.md) |

---

## Documentation

All guides live under [`docs/`](docs/README.md).

<details open>
<summary><strong>Service</strong></summary>

<br>

| Guide | Description |
|:------|:------------|
| [Blueprint process](docs/service/blueprint-process.md) | How blueprints are applied and evolved on data-product repositories |
| [Git providers](docs/service/git-providers.md) | Multi-provider Git operations and client-supplied auth |
| [Blueprint manifest](src/main/java/org/opendatamesh/platform/pp/blueprint/manifest/README.md) | Manifest schema, parameters, composition, Instantiation strategy |

</details>

<details open>
<summary><strong>Setup</strong></summary>

<br>

| Guide | Description |
|:------|:------------|
| [Development](docs/setup/development.md) | Build, run, profiles, testing |
| [Deployment](docs/setup/deployment.md) | Containers and external dependencies |
| [Configuration](docs/setup/configuration.md) | DB, notification, observer identity |

</details>

---

## Contributing

Contributions are welcome.

1. Fork the repository and create a feature branch  
2. Make your changes with clear commits  
3. Open a pull request against the main branch  

Bugs, questions, or proposals → [open an issue](https://github.com/opendatamesh-initiative/odm-platform-pp-blueprint-server/issues).

---

## License

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

Part of the [Open Data Mesh Initiative](https://github.com/opendatamesh-initiative).
