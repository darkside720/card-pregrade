# 0002 — Modular Gradle structure

## Context
The CV and grading logic must be testable quickly, replaceable (deterministic → learned, local → remote), and potentially reusable by a backend or iOS.

## Decision
Five modules:
- `:core:model`, `:core:cv`, `:core:grading` are **pure Kotlin/JVM** (no Android types).
- `:core:data` is an Android library holding Room. It exposes only repository interfaces through `LocalDataSources`; Room is not visible to `:app`.
- `:app` holds UI, navigation, ViewModels and orchestration.

The OpenCV implementation will be a separate `:core:cv-opencv` module (Phase 4), so the contracts stay dependency-free.

## Consequences
- JVM unit tests run in seconds without an emulator.
- There are a few more build files to maintain.
- Image types in `:core:cv` are abstracted (`CardImage`), and adapters wrap `Mat`/`Bitmap` later.
