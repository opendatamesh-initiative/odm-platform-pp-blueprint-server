# Blueprint Git repository content — read (GET) — testable requirements

**Feature:** Expose `GET /api/v2/pp/blueprint/blueprints/{uuid}/repository-content` so clients can read UTF-8 text from files in the blueprint’s linked Git repository at a **repository pointer** (branch, tag, or commit hash), using the same Git provider authentication as `POST .../repository-content`. When no file paths are requested, the server reads the default triple from `BlueprintRepo`: `readmePath`, `manifestRootPath`, `descriptorTemplatePath`.

**Implementation note:** Controller: `BlueprintRepositoryUtilsController`; service: `BlueprintRepositoryUtilsService`; map query parameters to `RepositoryPointerBranch` | `RepositoryPointerTag` | `RepositoryPointerCommit`; integration tests in `BlueprintRepositoryUtilsControllerIT` (extend or mirror existing POST tests).

---

Feature: Read blueprint repository file content

  As an API client  
  I want to GET file contents from a blueprint’s Git repository at a specific ref  
  So that I can inspect readme, manifest, and descriptor template (or arbitrary paths)

---

## Scenario: Successful read with explicit paths and branch pointer

**Requirement:** Given a valid blueprint and reachable repository, when the client specifies **branch** as the repository pointer and one or more valid repo-relative paths, the API returns **200** and a JSON array of `{ filePath, fileContent }` matching the files on disk at that branch.

```gherkin
Given a blueprint exists with uuid "bp-uuid" and a linked repository
And the Git provider can materialize the repository at branch "main"
And files exist at the requested paths relative to the repository root
When the client sends GET to "/api/v2/pp/blueprint/blueprints/bp-uuid/repository-content"
  with query parameter branch=main
  and path parameters listing each relative file path (e.g. path=dir/file.yaml)
And the same authentication headers convention as POST repository-content is present
Then the response status is 200
And the response body is a JSON array
And each element has non-null filePath and fileContent (UTF-8 text)
And fileContent matches the file bytes interpreted as UTF-8 for each requested path
And the remote repository has no new commits compared to before the request (read-only)
```

**Requirement ID:** `REPO-READ-001`

---

## Scenario: Successful read with tag pointer

**Requirement:** When the client specifies **tag** as the repository pointer, the clone/read uses that tag; response is **200** with correct contents for explicit paths.

```gherkin
Given a blueprint exists with uuid "bp-uuid" and a linked repository
And the repository has an annotated or lightweight tag "v1.0.0" pointing to a revision that contains the requested files
When the client sends GET to "/api/v2/pp/blueprint/blueprints/bp-uuid/repository-content"
  with query parameter tag=v1.0.0
  and path parameters for existing repo-relative files
And valid Git provider authentication headers are present
Then the response status is 200
And fileContent for each path matches the content at tag v1.0.0
```

**Requirement ID:** `REPO-READ-002`

---

## Scenario: Successful read with commit hash pointer

**Requirement:** When the client specifies **commit** (full or acceptable short hash per Git provider) as the repository pointer, the API reads files at that commit and returns **200**.

```gherkin
Given a blueprint exists with uuid "bp-uuid" and a linked repository
And commit "abc1234..." exists and contains the requested paths
When the client sends GET to "/api/v2/pp/blueprint/blueprints/bp-uuid/repository-content"
  with query parameter commit=abc1234...
  and path parameters for existing repo-relative files
And valid Git provider authentication headers are present
Then the response status is 200
And fileContent matches the files at that commit
```

**Requirement ID:** `REPO-READ-003`

---

## Scenario: Default paths when no path query parameters

**Requirement:** When the client **does not** supply path parameters (or supplies an empty list—same behavior as documented in OpenAPI), the server resolves paths from `BlueprintRepo`: `readmePath`, `manifestRootPath`, `descriptorTemplatePath` (after normalization). The response lists one entry per **non-blank** configured path; duplicate paths after normalization appear **once**.

```gherkin
Given a blueprint exists with uuid "bp-uuid"
And BlueprintRepo has readmePath "README.md", manifestRootPath "manifest/blueprint.yaml", descriptorTemplatePath "templates/descriptor.json"
And those files exist in the repository at the chosen pointer
When the client sends GET to "/api/v2/pp/blueprint/blueprints/bp-uuid/repository-content"
  with a valid repository pointer (e.g. branch=main)
  and no path query parameters
And valid Git provider authentication headers are present
Then the response status is 200
And the response array has one entry per distinct non-blank default path
And each filePath matches the configured path (normalized consistently with the API)
And fileContent matches the file at that path
```

**Requirement ID:** `REPO-READ-004`

---

## Scenario: Blueprint not found

**Requirement:** Unknown blueprint uuid returns **404** with the same error style as other blueprint endpoints.

```gherkin
Given no blueprint exists with uuid "00000000-0000-0000-0000-000000000001"
When the client sends GET to "/api/v2/pp/blueprint/blueprints/00000000-0000-0000-0000-000000000001/repository-content"
  with valid pointer and optional paths
Then the response status is 404
```

**Requirement ID:** `REPO-READ-006`

---

## Scenario: Conflicting or ambiguous repository pointer

**Requirement:** At most **one** of `branch`, `tag`, and `commit` may be set. If two or more are set, the API returns **400**.

```gherkin
Given a blueprint exists with uuid "bp-uuid"
When the client sends GET to "/api/v2/pp/blueprint/blueprints/bp-uuid/repository-content"
  with branch=main and tag=v1.0.0
Then the response status is 400
```

**Requirement ID:** `REPO-READ-007`

---

## Scenario: Path traversal in requested path

**Requirement:** A requested path that escapes the repository root after normalization (e.g. contains `..` segments) returns **400**.

```gherkin
Given a blueprint exists with uuid "bp-uuid"
When the client sends GET with path="../../etc/passwd" (or equivalent traversal)
  and a valid repository pointer
Then the response status is 400
```

**Requirement ID:** `REPO-READ-008`

---

## Scenario: Repository pointer not found or Git read failure

**Requirement:** If the branch, tag, or commit does not exist, or the Git provider cannot read the repository at that pointer, the API returns **400** with a message consistent with `POST .../repository-content` failure style.

```gherkin
Given a blueprint exists with uuid "bp-uuid"
When the client sends GET with branch=nonexistent-branch
  and path parameters for files that would exist on a valid branch
Then the response status is 400
```

**Requirement ID:** `REPO-READ-009`

---

## Scenario: Requested file does not exist at pointer

**Requirement:** If a resolved path (explicit or default) does not exist as a file in the clone at the given pointer, the API returns **404**.

```gherkin
Given a blueprint exists with uuid "bp-uuid"
And the repository at the given pointer has no file at "missing.txt"
When the client sends GET with path=missing.txt
Then the response status is 404
```

**Requirement ID:** `REPO-READ-010`

---

## Notes

- **Encoding:** Text is interpreted and returned as **UTF-8**; binary content is out of scope for this feature.
- **Default pointer behavior:** If none of `branch`, `tag`, `commit` is provided, the API uses `defaultBranch` from `BlueprintRepo`; if no default branch is configured, it returns **400**.
- **OpenAPI:** Document query parameter names for `branch`, `tag`, `commit`, repeated `path`, the default pointer behavior, and response schema.
- **Tests:** Each integration test method should cite the **Requirement ID** or scenario title in a comment at the top of the method.

