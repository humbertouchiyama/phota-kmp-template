# Phota KMP template

[![CI](https://github.com/humbertouchiyama/phota-kmp-template/actions/workflows/ci.yml/badge.svg)](https://github.com/humbertouchiyama/phota-kmp-template/actions/workflows/ci.yml)

Kotlin Multiplatform photo app: Android + iOS, Compose Multiplatform UI on both.
A gallery grid opens a photo detail with a fake upload-then-generate flow whose result joins the gallery;
data comes from a fake API that one Koin line swaps for a Ktor client on picsum.photos.

## How to run

- **Android:** `./gradlew :androidApp:installDebug`, or run `androidApp` from Android Studio.
- **iOS:** open `iosApp/iosApp.xcodeproj` in Xcode and run on a simulator.
- **Tests:** `./gradlew allTests` (Android host + iOS simulator).

## Architecture

```
androidApp/   Android entry point (Application, Activity). Thin.
iosApp/       iOS entry point (SwiftUI shell hosting the Compose view). Thin.
shared/       All app code: UI, ViewModels, data, DI.
  feature            PhotoImage, the one Coil image call site
  feature/gallery    image grid + detail
  feature/generate   fake generate/upload
  core/network       PhotoApi, FakePhotoApi, Ktor client + KtorPhotoApi
  core/data          PhotoRepository with an in-memory cache
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
- **Sealed `UiState` per screen, one `onEvent`, read-only `StateFlow`** — every state has a UI and every transition is testable; alternative: several flows per screen.
- **`Result`-based repository, ViewModels never see Ktor** — failures are values and `CancellationException` is rethrown, never wrapped; alternative: exceptions up to the ViewModel.
- **`FakePhotoApi` in commonMain, bound by Koin** — the app runs offline on both platforms and `KtorPhotoApi` is a one-line swap; alternative: the fake only in tests.
- **In-memory cache, no database** — a generate's `Finished` photo is prepended to the cache, so it joins the gallery; alternative: SQLDelight.
- **State-based navigation, no library** — a saveable `Screen` with per-photo ViewModel keys (`detail:<id>`, `generate:<id>`); alternative: a navigation library.
- **Coil 3 with the Ktor fetcher** — `SetUpImageLoader()` adds `KtorNetworkFetcherFactory`, without which iOS renders blank images; alternative: Kamel.
- **ViewModel tests with Turbine and hand-written fakes** — the same `commonTest` runs on the JVM host and the iOS simulator; alternative: JVM-only tests.
- **Gallery reloads on entry, no observable cache** — a generate that finishes after leaving Detail appears on the next Refresh or re-entry; alternative: a StateFlow-backed cache in the repository.
- **Upload simulated, no picker** — upload is a `Flow<Float>`, Retry restarts from upload, Cancel stops the run but leaving Detail does not; alternative: a real file picker.
- **`KoinGraphTest` resolves beans by hand through `initKoin()`** — `module.verify()` is JVM-only and the test must also run on Native; alternative: `verify()` on the JVM only.

### Switching to the real API

The default `PhotoApi` is `FakePhotoApi`. In `shared/src/commonMain/kotlin/com/humbertouchiyama/phota/core/network/NetworkModule.kt`, replace

`single<PhotoApi> { FakePhotoApi() }` with `single<PhotoApi> { KtorPhotoApi(get()) }`.

`KtorPhotoApi` lists from `picsum.photos/v2/list` and keeps upload/generate simulated; `KoinGraphTest.defaultPhotoApiIsFake` then fails by design, so update it too.

## What I would do with more time

- A real API with auth, and real upload/generate endpoints.
- Persistence for photos and generated results.
- A navigation library with per-destination ViewModel scopes.
- UI tests with compose-ui-test.
- A real file picker for upload.
- Pagination of the photo list.
- detekt and ktlint in CI.
- A SwiftUI UI over the shared ViewModels as an alternative to Compose on iOS.
- Tests for `KtorPhotoApi` and `createHttpClient`, which are untested (no `ktor-client-mock`, which needs a catalog change), and an `HttpTimeout` on the client.
