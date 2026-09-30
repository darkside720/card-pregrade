# 0009 — Optional Python/OpenCV reference implementation

## Context
CV algorithms are easier to prototype and visualise in Python, and a future backend is planned in Python. The Android build must not depend on Python.

## Decision
`cv-reference/` holds Python/OpenCV reference algorithms and a synthetic fixture generator that produces the same specs as the Kotlin `SyntheticCardSpec`. It uses a project-local `.venv` and pinned requirements, and nothing is installed globally. It is optional for Android development.

## Consequences
- Parity tests can compare Kotlin and Python outputs on identical fixtures.
- Two implementations must be kept consistent, which the shared fixtures and tolerances enforce.
