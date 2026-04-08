# Source Repository Fixture

This directory is a test fixture used by `BlueprintInstantiationControllerIT` to emulate a realistic blueprint source repository.

It contains:

- `templates/`: templated files rendered with runtime parameters (Velocity).
- `infrastructure/core/`: static files copied as-is to the target repository.
- `docs/` and `scripts/`: additional non-templated project assets.
- `README.md`: intentionally present so tests can verify README exclusion during population.

The fixture is copied into a temporary local clone during integration tests and should stay deterministic and lightweight.
