# Guidelines: Implementing a Use Case

This document describes how use cases are structured: REST surface, application orchestration, factories, domain commands, presenters, and outbound ports.

## Architecture at a glance

The flow is:

**HTTP** → **Controller** (`*UseCaseController`) → **Use cases service** (`*UseCasesService`) → **Factory** (`*Factory`) → **Use case** (implements `UseCase`) → **Outbound ports** (interfaces) → **Adapters** (`*OutboundPortImpl`) → **Core / infrastructure** (CRUD services, clients, etc.).

- The **use case** stays free of Spring and HTTP: it receives a **command** (domain input) and talks to the outside world only through **ports**.
- The **presenter** is the use case’s output boundary: it receives domain results (e.g. `presentX(...)`).
- The **factory** is the composition root for a single use case: it wires concrete port implementations and returns a `UseCase` instance.
- The **use cases service** adapts REST DTOs to commands, runs the use case, and maps domain results back to API resources.

```mermaid
flowchart LR
  subgraph rest [REST]
    C[UseCaseController]
    DTO[CommandRes / ResultRes]
  end
  subgraph app [Application]
    UCS[*UseCasesService]
    F[*Factory]
    UC[Use case class]
  end
  subgraph boundaries [Boundaries]
    Cmd[Command record]
    Pres[Presenter interface]
    P[Outbound ports]
  end
  C --> UCS
  DTO --> UCS
  UCS --> Cmd
  UCS --> F
  F --> UC
  UC --> Cmd
  UC --> Pres
  UC --> P
```



---

## 1. Shared building blocks (`utils.usecases`)


| Artifact                    | Role                                                                                                                                                                                                                  |
| --------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `UseCase`                   | Single method: `void execute()`. Every use case class implements this interface.                                                                                                                                      |
| `TransactionalOutboundPort` | Wraps work in a transaction (`doInTransaction`, `doInTransactionWithResults`). Injected into factories; implemented by `DefaultTransactionalOutboundPortImpl` (Spring `TransactionTemplate`, serializable isolation). |


Use the transactional port inside the use case when the operation must be atomic (typical for persistence + side effects that belong in the same transaction).

---

## 2. REST API: controller and resources

### Controller

- **Package:** `...controllers`
- **Class name:** `*UseCaseController` (e.g. `DataProductUseCaseController`).
- **Annotations:** `@RestController`, `@RequestMapping`, `produces = MediaType.APPLICATION_JSON_VALUE`.
- **Responsibility:** Map HTTP to the corresponding `*UseCasesService` method only. No business rules.
- **OpenAPI:** `@Tag`, `@Operation`, `@ApiResponses`, `@Parameter`; use `@Schema(implementation = ...)` on responses where applicable. Use `@Hidden` for endpoints that should not appear in public API docs.

### Resources (DTOs)

- **Package:** `rest.v2.resources.<aggregate>.usecases.<verb>` (e.g. `...dataproduct.usecases.init`).
- **Naming:**
  - Request body: `*CommandRes` (e.g. `DataProductInitCommandRes`).
  - Response body: `*ResultRes` when returning payload (e.g. `DataProductInitResultRes`).
- **Content:** JavaBeans with getters/setters (and empty constructor for Jackson), fields documented with `@Schema` where useful.
- **Mapping:** Use existing mappers under `rest.v2.resources` (e.g. `DataProductMapper`, `DataProductRepoMapper`) to convert between `*Res` and **domain entities** inside the use cases service—not inside the controller.

---

## 3. Use cases service (`*UseCasesService`)

- **Package:** `...<aggregate>.services` (e.g. `dataproduct.services`).
- **Annotation:** `@Service`.
- **Responsibility:**
  1. Convert `*CommandRes` → domain **command** (record) using mappers (`toEntity`, etc.).
  2. Build a **presenter** implementation:
    - Often a **private static inner class** `*ResultHolder` that implements the presenter interface and stores the last `present*` argument for later `getResult()`.
    - For void outcomes, a **lambda** implementing the presenter is acceptable when there is nothing to return (e.g. delete).
  3. Call `factory.build...(command, presenter).execute()`.
  4. Map domain result to `*ResultRes` when needed.

Keep this class as the single place that knows both REST mappers and use-case boundaries for that aggregate’s use cases.

---

## 4. Use case package layout

For one behavioral slice (e.g. “initialize data product”), colocate under `...services.usecases.<name>`:


| File                     | Purpose                                                                              |
| ------------------------ | ------------------------------------------------------------------------------------ |
| `<Name>.java`            | Use case class: `class <Name> implements UseCase`, **package-private**.              |
| `<Name>Command.java`     | Input: prefer a **Java `record`** holding domain entities/value objects.             |
| `<Name>Presenter.java`   | Output boundary: interface with methods like `present<DataProduct>Initialized(...)`. |
| `<Name>Factory.java`     | `@Component`; method `UseCase build...(Command, Presenter)`.                         |
| `*OutboundPort.java`     | Port interfaces (persistence, validation, notification, external descriptor, …).     |
| `*OutboundPortImpl.java` | Adapters: implement ports by delegating to **core** services or clients.             |


Optional: split ports by concern (`...PersistenceOutboundPort`, `...NotificationOutboundPort`, `...ValidationOutboundPort`, etc.).

---

## 5. Use case class

- Implements `UseCase` and implements `execute()`.
- **Constructor** receives: command, presenter, outbound ports, and `TransactionalOutboundPort` when transactions are required.
- **Visibility:** package-private (`class Foo implements UseCase`) so only the factory in the same package constructs it.
- **Logic:** Validate command; orchestrate ports; call presenter methods when the business outcome is ready. Throw domain/API exceptions (`BadRequestException`, etc.) for invalid state. Must contain ONLY business logic.
- **No** `@Autowired` on the use case class itself.
- **No** spring annotations on the use case class itself.

---

## 6. Command and presenter

- **Command:** Immutable **record** with domain types (entities, UUIDs, enums), not REST DTOs.
- **Presenter:** Interface named `<Action>Presenter` with one or more `present...` methods. The use case calls these to push results outward without knowing HTTP.

---

## 7. Factories (`*Factory`)

- `**@Component`** in the same package as the use case.
- **Inject** Spring beans needed to build adapters: core services (`*Service`, `*CrudService`), `TransactionalOutboundPort`, clients, mappers, etc.
- **Method signature:** `public UseCase build<Name>(<Name>Command command, <Name>Presenter presenter)`.
- **Inside:** Instantiate `*OutboundPortImpl` classes (often package-private) with constructor injection of the needed services; return `new <Name>(command, presenter, ...ports, transactionalOutboundPort)`.

The factory is the only place that chooses concrete port implementations for that use case.

---

## 8. Outbound ports and adapters

- **Interface:** `<UseCase><Concern>OutboundPort` (e.g. `DataProductInitializerPersistenceOutboundPort`). Methods express what the use case needs in domain terms (`save`, `findByFqn`, `emit...`), not SQL or REST.
- **Implementation:** `<UseCase><Concern>OutboundPortImpl` implements the port by calling:
  - **Core layer** services (e.g. `DataProductsService`, `DescriptorVariableCrudService`), or
  - **Clients** (e.g. `NotificationClient`), with mapping if needed.
- **Visibility:** Port interfaces and their impls are often **package-private** so the boundary stays inside the use case package.
- **Transactional port:** Shared across use cases; do not reimplement transaction handling inside each use case—delegate to `TransactionalOutboundPort`.

---

## 9. Core services vs use case layer

- `**...services.core.*`** (and similar): CRUD, queries, reusable domain operations. Use cases **do not** call these directly from the use case class; they go through outbound ports whose impls delegate to core services.
- `**utils.usecases`:** Cross-cutting use-case infrastructure (`UseCase`, `TransactionalOutboundPort`).

---

## 10. Testing

- Add or extend **integration tests** for use case controllers (e.g. `*UseCaseControllerIT` under `src/test/java/...rest.v2.controllers`) to verify the full stack for the new endpoint.
- Unit-test complex port adapters or use case logic where it pays off (existing project uses tests such as `*OutboundPortTest` in some areas).

---

## 11. Checklist for a new use case

1. Define **command** record and **presenter** interface in `...services.usecases.<name>`.
2. Implement the **use case** class (`implements UseCase`) with outbound port interfaces and **impl** classes.
3. Add `**@Component` factory** wiring ports and `TransactionalOutboundPort`.
4. Add `***UseCasesService`** methods (or extend an existing one): map `*CommandRes` → command, run factory + `execute()`, map to `*ResultRes`.
5. Add `**CommandRes` / `ResultRes**` under `rest.v2.resources...usecases...` with OpenAPI `@Schema` as needed.
6. Add `**@RestController**` endpoint calling the use cases service; document with OpenAPI.
7. Add **integration test** for the new route.

Following these steps keeps new behavior consistent with the existing hexagonal-style layout: **ports/adapters** for infrastructure, **use case** for orchestration, **REST** as a thin adapter on top of the use cases service.