# Git providers

How the Blueprint Server talks to Git hosts when registering blueprints, instantiating, updating data-product repositories, and validating protected content at publication.

Related: [Blueprint process](blueprint-process.md) · [Protected resources](protected-resources.md) · [Configuration](../setup/configuration.md)

## Supported providers

Via the shared **`git-utils`** library and a blueprint-local **`GitProviderFactory`**:

- GitHub
- GitLab
- Bitbucket
- Azure DevOps

Callers identify the provider (type and optional base URL) and pass **authentication** through HTTP headers (PAT / provider-specific params), consistent with other product-plane services.

## What the service uses Git for

| Area | Operations |
|:-----|:-----------|
| **Git provider utils** | List organizations and repositories, create repositories, list branches, provider-specific custom resources |
| **Blueprint / version workflows** | Clone at branch or tag, orphan branches, commit, checkpoint tags, merge, selective push, optional create Pull Request |
| **Protected-resources check** | Clone only protected product targets at their own Registry-recorded publication tags, clone Blueprint/Module sources, and re-instantiate locally with **no push** |
| **Repository context** | Commits, branches, tags (where exposed by utils APIs) |

Low-level Git and provider HTTP live behind outbound ports / `git-utils`. Use cases orchestrate workflows (instantiate / update) without embedding raw JGit or provider clients.

## Auth model

- Headers such as `x-odm-gpauth-type`, `x-odm-gpauth-param-token`, and `x-odm-gpauth-param-username` select and authenticate the provider for the request.
- Factory → git outbound port receives those headers; domain commands do not carry secrets.
- The background protected-resources policy does not receive caller Git headers. It resolves service credentials per provider from `blueprint.validator.git.credentials`; see [Configuration](../setup/configuration.md).

Exact paths and payloads: **Swagger UI** under `/api/v2/pp/blueprint/`.

---

↑ Back to [docs index](../README.md)
