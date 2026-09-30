# 0001 — Native Android with Kotlin, Jetpack Compose, Material 3

## Context
Camera quality and control (focus, exposure, resolution, Camera2 metadata) are central to the product. The app also needs heavy on-device image processing (OpenCV).

## Decision
Build a native Android app in Kotlin with Jetpack Compose, Material 3, single activity, Navigation Compose, MVVM with Coroutines/Flow, Room for persistence, and CameraX (Phase 3) with Camera2 interop for metadata.

## Consequences
- Full access to CameraX/Camera2 and the OpenCV Android SDK without bridge layers.
- iOS will need its own UI. Domain and grading logic are kept in pure Kotlin to limit what must be rewritten (see 0006).
- React Native / Flutter were rejected because camera control and native CV integration would pass through plugins that add risk exactly where the product needs precision.
