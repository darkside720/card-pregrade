# 0010 — JDK 17 daemon pin and a conservative dependency set

## Context
The development machine has several JDKs (18 is the default). The Android Gradle Plugin needs JDK 17 or newer. Stability matters more than being on the newest versions in a skeleton phase.

## Decision
- `android/gradle/gradle-daemon-jvm.properties` sets `toolchainVersion=17`. Gradle runs this project on an installed JDK 17 regardless of the default `java`, and other projects are unaffected. (Daemon JVM criteria is incubating in Gradle 8.11.)
- Bytecode targets JVM 17 in all modules.
- Versions: AGP 8.7.3, Gradle 8.11.1 (wrapper with SHA-256 verification), Kotlin 2.1.0, KSP 2.1.0-1.0.29, Compose BOM 2024.12.01, Room 2.6.1, compile/target SDK 35 with Build-Tools 35.0.0 pinned.

## Consequences
- Lint reports newer versions (AGP 9.x and others). Upgrading is a separate, tested change, and AGP 9 in particular changes Kotlin integration.
- Android Studio should use a JDK 17+ Gradle JDK. The daemon criteria resolves JDK 17 automatically if it is installed.
