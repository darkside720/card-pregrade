# Architecture Decision Records

Format: Context → Decision → Consequences. Status is Accepted unless noted. Supersede by adding a new ADR, never by rewriting history.

| # | Decision |
|---|---|
| [0001](0001-native-android-kotlin-compose.md) | Native Android with Kotlin, Jetpack Compose, Material 3 |
| [0002](0002-modular-gradle-structure.md) | Gradle modules: `:app`, `:core:model`, `:core:cv`, `:core:grading`, `:core:data` |
| [0003](0003-local-first-privacy.md) | Local-first processing; no network permission in the MVP |
| [0004](0004-deterministic-cv-before-ml.md) | Deterministic CV (OpenCV) before ML |
| [0005](0005-grade-ranges-and-explainability.md) | Grade ranges on a half-point scale, with explainable limiting factors |
| [0006](0006-defer-kotlin-multiplatform.md) | Defer Kotlin Multiplatform until the iOS phase |
| [0007](0007-normalized-defect-coordinates.md) | Defect coordinates normalized to the perspective-corrected side image |
| [0008](0008-versioned-provenance.md) | Algorithm/pipeline versions on every result |
| [0009](0009-python-cv-reference.md) | Optional Python/OpenCV reference implementation with shared synthetic fixtures |
| [0010](0010-toolchain-pinning.md) | JDK 17 daemon pin and a conservative dependency set |
