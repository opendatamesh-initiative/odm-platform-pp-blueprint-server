# Blueprint Server documentation

Index of guides for the **ODM Platform Blueprint Server**.

<p>
  <a href="http://localhost:8087/swagger-ui.html">Swagger UI</a> ·
  <a href="http://localhost:8087/v3/api-docs">OpenAPI</a>
  <em>(when the service is running locally with the <code>dev</code> profile)</em>
</p>

---

## Service

| Guide                                                                                            | Description                                                                                         |
| :----------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------- |
| [Blueprint process](service/blueprint-process.md)                                                | Instantiate, update, tag-based 3-way merge, conflict cases, hunk overlap & authoring best practices |
| [Multi-repository & composition](service/repositories-and-composition.md)                        | Multiple remotes, reusable modules, layouts, and what is supported today                            |
| [Git providers](service/git-providers.md)                                                        | How the service uses Git hosts and client-supplied auth                                             |
| [Blueprint manifest](../src/main/java/org/opendatamesh/platform/pp/blueprint/manifest/README.md) | Manifest schema (repositories, root, composition) |

## Setup

| Guide                                   | Description                             |
| :-------------------------------------- | :-------------------------------------- |
| [Development](setup/development.md)     | Local build, run, profiles, and testing |
| [Deployment](setup/deployment.md)       | Docker / container deployment           |
| [Configuration](setup/configuration.md) | Properties to manage (DB, Notification) |

---

↑ Back to the [project README](../README.md)
