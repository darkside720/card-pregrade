# 0006 — Defer Kotlin Multiplatform

## Context
iOS is a future goal. KMP could share models and grading logic, but it adds build complexity now for hypothetical reuse.

## Decision
Do not use KMP in the MVP. Keep `:core:model`, `:core:grading` and the geometry in `:core:cv` free of Android and JVM-specific APIs where practical, so conversion is mechanical later.

## Consequences
- The Android build stays simpler and more reliable today.
- Some JVM APIs are used (`java.time`, `String.format`). These will need replacements such as `kotlinx-datetime` if KMP is adopted. Re-evaluate at the start of the iOS phase.
