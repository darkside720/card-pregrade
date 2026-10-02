# Card Pre-Grade

**TCG Card Pre-Grading / Visual Inspection tool** — an Android-first app that helps
collectors *estimate* how a Pokémon or One Piece TCG card might grade, using guided,
high-resolution photographs.

> **Not an official grade.** In the current Phase 3 build, Results are DEMO / MOCK sample data
> and are not derived from captured photographs. Future analysis results will be estimates
> rather than official grades. Professional in-person inspection may detect defects that
> photographs cannot.

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Discovery | Done |
| 1 | Architecture, docs, ADRs, data models, CV interfaces | Done |
| 2 | Buildable Android skeleton: navigation, 7 screens, mock result, test scaffolding | Done |
| 3 | CameraX capture, guide overlay, retake, capture-quality checks | Done (validated on a Google Pixel 8 Pro, Android 17 / API 37) |
| 4 | CardDetector, PerspectiveCorrector, ImageQualityAnalyzer, CenteringAnalyzer | Not started |
| 5 | Corner / Edge / Whitening analysis | Not started |
| 6 | Rule-based pre-grade (PSA / BGS ranges) | Not started |

**What works today:** guided four-photo capture with CameraX (front straight, back straight,
front angled left, front angled right), stored in app-private storage and persisted with Room,
plus technical *capture-quality* checks (resolution, sharpness, exposure, highlight clipping)
that decide whether a photo is usable.

**What does not exist yet:** any analysis of the card itself — no card-condition computer
vision, no centering measurement, no corner/edge/surface defect detection, no ML, and no grade
calculation. The Results screen shows clearly labelled **DEMO / MOCK** sample data; none of its
values are derived from your photographs. See [docs/limitations.md](docs/limitations.md).

## Repository layout

```
card-pregrade/
  README.md
  docs/                 Architecture, methodology, limitations, privacy, testing, dataset/ML strategy, roadmap
    adr/                Architecture Decision Records
  android/              Native Android app (Kotlin, Compose, Material 3) — Gradle multi-module
    app/                UI, navigation, ViewModels, orchestration
    core/model/         Pure-Kotlin domain models (no Android deps)
    core/cv/            CV pipeline contracts + deterministic geometry (pure Kotlin)
    core/grading/       Grade-estimation contracts, confidence policy (pure Kotlin)
    core/data/          Room persistence + repository interfaces
  cv-reference/         Python/OpenCV reference algorithms & fixture generation (skeleton; optional)
  backend/              Future backend DESIGN ONLY (nothing deployed)
  infra/                Future infrastructure DESIGN ONLY (nothing provisioned)
  test-data/            Fixture policy + synthetic / known-good / known-defects folders
```

See [docs/architecture.md](docs/architecture.md) for the full design.

## Local developer setup

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | **17** (Temurin recommended) | The project pins the Gradle daemon to JDK 17 via `android/gradle/gradle-daemon-jvm.properties`; other JDKs on the machine are unaffected. |
| Android SDK | Platform **35**, Build-Tools **35.0.0**, current platform-tools | Point `android/local.properties` at it (see below). |
| Android Studio | Current stable | Primary IDE. Open the `android/` folder. |
| Emulator (optional) | API 35 ARM64 (Google APIs) system image | For instrumented tests / manual runs. |
| Python (optional) | 3.11+ | Only for `cv-reference/`. **Not** needed to build the app. |

Gradle itself is provided by the wrapper (`android/gradlew`, Gradle 8.11.1, checksum-verified).

### First-time configuration

Create `android/local.properties` (git-ignored, machine-specific):

```properties
sdk.dir=/Users/<you>/Library/Android/sdk-current
```

Android Studio writes this file automatically when it opens the project with a configured SDK.

### Build, test, lint

```bash
cd android
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew test                   # all JVM unit tests (all modules, all variants)
./gradlew :app:lintDebug :core:data:lintDebug
```

Instrumented UI tests (need a running emulator or device):

```bash
./gradlew :app:connectedDebugAndroidTest
```

Install and launch manually:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.cardpregrade.app/.MainActivity
```

### Optional: Python reference environment

```bash
cd cv-reference
python3 -m venv ../.venv
../.venv/bin/pip install -r requirements.txt
```

Use the project-local `.venv` only; do not install OpenCV/numpy globally.

## Principles

- **Estimates, not grades.** Grading-company estimates are ranges (e.g. "PSA 9–10"), never false
  precision (no "PSA 9.73"). A future continuous condition score (e.g. "9.7 / 10") is a separate
  concept and is not a probability of any grade; see [docs/grading-methodology.md](docs/grading-methodology.md).
- **Don't fabricate.** Unmeasurable values are reported as *Unknown*; bad photos lower confidence or trigger a retake.
- **Deterministic first.** Geometry and image processing where objectively measurable; ML only when real graded-card data exists.
- **Local first.** Photos stay in app-private storage on the device. The current build does not request the `INTERNET` permission and has no upload path. Any future upload requires explicit per-scan consent.
- **Reproducible.** Every result records the algorithm/pipeline versions that produced it.

## Security

No API keys, credentials, or signing material live in this repository. See `.gitignore` and
[docs/privacy.md](docs/privacy.md).
