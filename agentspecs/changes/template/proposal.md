# <Short title for the change>

**Purpose:** This document is the **development guide for code logic**. It explains why and how; the companion `specs.md` defines testable requirements (Gherkin). Implementers should follow this proposal when writing code and use `specs.md` when writing tests.

**Note:** Every bullet list below is **variable length** (0 to N items). Add as many bullets as the change needs; do not pad or trim to a fixed number. Empty lists are allowed (e.g. no out-of-scope items).

---

## Context

<Describe the current state: what exists today, which components or code paths are involved, and why a change is needed. Include concrete details (e.g. APIs, libraries, file names, or current behavior) that explain the problem. Use bullet points if helpful.>

## Goal

<One or two sentences stating what should be true after the change. Prefer observable, user-visible or system behavior; avoid vague goals.>

## Scope

- **In scope**
  - <Area, component, or artifact to change; add more bullets as needed.>
- **Out of scope**
  - <What we are explicitly not changing (helps prevent scope creep); add more bullets as needed.>

## Proposed direction

<Concrete technical approach: what to build or change, and how. This is what implementers follow when writing code. Name files, layers, or patterns when known.>

- **<Aspect name>:** <Description; add as many aspect bullets as needed.>

<Optional: one short summary sentence of the outcome.>

## Success criteria

<High-level, testable conditions that must hold when the change is done. Each should be verifiable without reading code (e.g. observable behavior or a check). These should be reflected in `specs.md` as concrete scenarios.>

- <Criterion; add as many as needed.>
