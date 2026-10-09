# Phota KMP template

[![CI](https://github.com/humbertouchiyama/phota-kmp-template/actions/workflows/ci.yml/badge.svg)](https://github.com/humbertouchiyama/phota-kmp-template/actions/workflows/ci.yml)

Kotlin Multiplatform skeleton for a small photo app: Android + iOS, Compose Multiplatform UI on both.
No features yet.

## How to run

- **Android:** `./gradlew :androidApp:installDebug`, or run `androidApp` from Android Studio.
- **iOS:** open `iosApp/iosApp.xcodeproj` in Xcode and run on a simulator.
- **Tests:** `./gradlew allTests` (Android host + iOS simulator).

## Architecture

```
androidApp/   Android entry point (Application, Activity). Thin.
iosApp/       iOS entry point (SwiftUI shell hosting the Compose view). Thin.
shared/       All app code: UI, ViewModels, data, DI.
  feature/gallery    image grid + detail
  feature/generate   fake generate/upload
  core/network       Ktor client, API
  core/data          repositories
  di                 Koin setup
```

The feature packages live in `shared`, because `shared` is the only KMP module: it holds the
Compose UI and the logic, and both apps only start it.

## Decisions and trade-offs

- **One `shared` module, packages per feature** — small app, fast builds; alternative: a Gradle module per feature.
- **`androidApp` + `shared` + `iosApp`** — the current KMP wizard layout (AGP 9 needs the Android app in its own module); alternative: the older single `composeApp`.
- **Compose Multiplatform on both** — one UI, fastest to ship; alternative: SwiftUI on iOS over a shared ViewModel.
- **Koin** — simple, multiplatform, no codegen; alternative: kotlin-inject.
- **Hand-written fakes, no MockK** — MockK does not run on Kotlin/Native; alternative: Mokkery.

## What I would do with more time

-
