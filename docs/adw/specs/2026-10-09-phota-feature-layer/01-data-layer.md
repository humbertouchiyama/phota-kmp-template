unit: u1
intent: phota-feature-layer
depth: D3
packaging: chain A / sub-PR 1
after: []
verify: bash -c 'for f in shared/src/commonMain/kotlin/com/humbertouchiyama/phota/core/network/PhotoApi.kt shared/src/commonMain/kotlin/com/humbertouchiyama/phota/core/network/FakePhotoApi.kt shared/src/commonMain/kotlin/com/humbertouchiyama/phota/core/data/PhotoRepository.kt shared/src/commonTest/kotlin/com/humbertouchiyama/phota/core/data/PhotoRepositoryTest.kt; do [ -f "$f" ] || { echo "VERIFY missing $f"; exit 1; }; done && ./gradlew :shared:testAndroidHostTest --tests "com.humbertouchiyama.phota.core.data.PhotoRepositoryTest" --rerun --console=plain && ./gradlew :shared:compileKotlinIosSimulatorArm64 --console=plain'

# u1 — Data layer: photo API, fake, repository with in-memory cache

`verify` is unchanged from the approved header; the guarded file names match this design.

## Problem

`core/network` and `core/data` are empty Koin placeholders. u2 (gallery) and u3 (detail and
generate) are authored in parallel against the pinned contract below and need it to exist, be bound
in Koin, and behave: images that load in fake mode, failures the UI can render, cancellation that
stays cancellation.

## Change

`core/network` (commonMain): `Photo` + `GenerateEvent` (`Photo.kt`), `PhotoApi`, `FakePhotoApi`,
`KtorPhotoApi` (+ `createHttpClient()`), `networkModule`. `core/data`: `PhotoRepository.kt`
(interface + `PhotoRepositoryImpl` + `resultOf`), `dataModule`. Test: `PhotoRepositoryTest`
(commonTest). No edit to `shared/build.gradle.kts`, the catalog, `iosMain`, `androidMain`,
`di/Koin.kt` or `README.md` (u4 owns the README).

### Pinned contract (owned here)

```kotlin
package com.humbertouchiyama.phota.core.network
@Serializable data class Photo(val id: String, val author: String, val imageUrl: String)
sealed interface GenerateEvent { data class Progress(val fraction: Float) : GenerateEvent; data class Finished(val photo: Photo) : GenerateEvent }
interface PhotoApi { suspend fun listPhotos(): List<Photo>; suspend fun getPhoto(id: String): Photo; fun upload(): Flow<Float>; fun generate(sourceId: String): Flow<GenerateEvent> }
package com.humbertouchiyama.phota.core.data
interface PhotoRepository { suspend fun photos(forceRefresh: Boolean = false): Result<List<Photo>>; suspend fun photo(id: String): Result<Photo>; fun upload(): Flow<Float>; fun generate(sourceId: String): Flow<GenerateEvent> }
```

- `PhotoApi`: suspend funs **throw** on failure; Flows emit progress in (0f, 1f], complete on
  success, throw on failure.
- `PhotoRepository`: suspend funs **return `Result`**, never wrapping `CancellationException`
  (rethrown); its Flows **throw** like the API's. A `Finished` photo is prepended to the cache
  before the collector receives it.
- **Additive** means: a new member must have a default (u2/u3/u4 fakes implement these interfaces),
  and `GenerateEvent` stays closed at exactly `Progress` + `Finished` (exhaustive `when` downstream).

### Added members

- `class FakePhotoApi(var delayMillis: Long = 500, var progressSteps: Int = 10) : PhotoApi` —
  `var`s, u2/u3 set them. Steps used are `progressSteps.coerceAtLeast(1)`.
  - `fun failNext(times: Int = 1, error: Throwable = FakeApiException())` **replaces** any pending
    arming (not additive). Each call to a suspend fun, and each *collection* of a flow, consumes one.
    A flow takes its failure when collection starts, so a second collection never re-throws a stale
    one. A failing flow throws after its first progress value; a failing suspend fun after its delay.
  - `var listCalls: Int` / `var getCalls: Int` with `private set` — counters for cache assertions.
  - Unknown id in `getPhoto` throws `FakeApiException`.
  - `class FakeApiException(message: String = "Simulated failure") : Exception(message)`.
- `class KtorPhotoApi(client: HttpClient, simulator: PhotoApi = FakePhotoApi()) : PhotoApi`;
  `fun createHttpClient(): HttpClient`.
- `class PhotoRepositoryImpl(api: PhotoApi) : PhotoRepository`.

## Decisions (approved answers)

- **Q1** — real API is `KtorPhotoApi` on `https://picsum.photos/v2/list`; fake bound by default.
  Swap = replace the one line `single<PhotoApi> { FakePhotoApi() }` in `NetworkModule.kt` with
  `single<PhotoApi> { KtorPhotoApi(get()) }`; `HttpClient` is its own lazy `single`. No other line of
  `NetworkModule.kt` contains `PhotoApi` (u4 greps the first match); the swap hint goes in the README (u4).
- **Q2** — `FakePhotoApi` lives in commonMain.
- **Q3** — a generate that reaches `Finished` adds the photo to the gallery via the repository cache.
- **Q4** — upload is simulated (progress flow), no file picker, no new dependency.
- **Q5** — `verify` is Android host + iOS compile; the implementer also runs §5 gate 3 for this test
  class once (Kotlin/Native check of `Mutex`/`delay`/Flow/cancellation).

## Design

**Chosen direction.**
- *Engine:* `HttpClient { … }` with no engine argument — `shared/build.gradle.kts` puts
  `ktor-client-okhttp` in androidMain and `ktor-client-darwin` in iosMain, one engine per target.
  Installs `ContentNegotiation { json(Json { ignoreUnknownKeys = true }) }` and `expectSuccess = true`
  (non-2xx throws → `Result.failure`). No `HttpTimeout`: a hung request waits for the caller's
  cancellation, and a timeout-cancellation would be rethrown, not mapped (u4 notes it in README
  "more time").
- *Ktor is compile-only:* `createHttpClient()`/`KtorPhotoApi` have no test — `ktor-client-mock` is
  not in the catalog and a catalog change is an owner decision. u4 records it in README "more time".
- *Picsum mapping:* `/v2/list` and `/id/{id}/info` return `{id, author, width, height, url,
  download_url}`; a private `@Serializable PicsumPhoto(id, author)` maps to
  `Photo(id, author, "https://picsum.photos/id/$id/400/400")` (`download_url` is the full original).
  `upload`/`generate` delegate to `simulator` (picsum has neither, Q4).
- *Fake data:* 30 photos, ids `"0"`..`"29"`, URL `https://picsum.photos/id/<n>/400/400`. Generate
  emits `Progress` steps ending at `1f`, then `Finished(Photo("gen-<k>", "Phota AI",
  "https://picsum.photos/seed/phota-gen-<k>/400/400"))` (seed URLs always resolve). Waits are `delay`,
  virtual under `runTest`.
- *Cache:* `remote: List<Photo>?` (last successful list) and `generated: List<Photo>` (newest first).
  `photos()` returns `(generated + remote).distinctBy { it.id }`, calling the API only when
  `remote == null` or `forceRefresh`; a failed refresh returns the failure and keeps the cache.
  `photo(id)` searches both lists, else `getPhoto`. `generate` = `api.generate(id).onEach { if
  Finished, prepend }`, so a collector cancelled right after `Finished` still leaves it cached.
- *Mutex scope:* only around reads/writes of the two fields; the API call is outside the lock, so a
  cancelled fetch never holds it. Two concurrent first `photos()` may both fetch — accepted.
- *Result helper:* `internal suspend inline fun <T> resultOf(block: () -> T): Result<T>` — catches
  `CancellationException` (`kotlinx.coroutines`) first and rethrows, then `Exception` → failure.

**Rejected.**
- *Prepend generated into `remote` only* — a generate before the first load would leave a one-photo
  cache and `photos()` would never fetch the rest.
- *expect/actual engine factory* — new androidMain/iosMain files and gate 6, while each target has
  one engine.
- *The stdlib catch-all Result builder* — it wraps `CancellationException` (repo-profile §15 blocking).
- *Fake in commonTest only* — contradicts Q2.
- *Fake records generated photos* — two sources of truth; the repository owns the gallery.
- *Serve cache on refresh failure* — hides the error from the refresh UI.
- *`HttpTimeout`, a Koin graph test here* — out of scope (u4 owns the graph test).

## Acceptance criteria

`$T` = `shared/src/commonTest/kotlin/com/humbertouchiyama/phota/core/data/PhotoRepositoryTest.kt`.

| # | Criterion | Check |
|---|---|---|
| AC1 | Repository tests pass on the JVM host | the `verify` command |
| AC2 | commonMain compiles for iOS; tests pass on Kotlin/Native | `verify`; `./gradlew :shared:iosSimulatorArm64Test --tests "com.humbertouchiyama.phota.core.data.PhotoRepositoryTest" --console=plain` |
| AC3 | 19 tests: `photosSuccess`, `photosFailure`, `failNextTwice`, `photosCacheHit`, `forceRefreshRefetches`, `failedRefreshKeepsCache`, `generatePrependsToCache`, `generateBeforeFirstLoadStillFetches`, `generateFailureDoesNotTouchCache`, `generateCancelledMidwayWritesNothing`, `finishedThenCancelKeepsPhoto`, `photoServesFromCache`, `photoServesGeneratedFromCache`, `photoFallsBackToApi`, `photoUnknownIdFails`, `uploadPassesThrough`, `uploadCancelledCompletes`, `apiCancellationIsRethrown`, `cancelledCallerGetsNoResult` | `grep -c '@Test' "$T"` → `19`; `grep -o 'tests="[0-9]*"' shared/build/test-results/testAndroidHostTest/TEST-com.humbertouchiyama.phota.core.data.PhotoRepositoryTest.xml` → `tests="19"` |
| AC4 | Fake bound by default; swap is one line | `grep -n 'PhotoApi' shared/src/commonMain/kotlin/com/humbertouchiyama/phota/core/network/NetworkModule.kt` → exactly one line: `single<PhotoApi> { FakePhotoApi() }` |
| AC5 | No JVM/Android imports, no stdlib catch-all in common code (comments included) | `grep -rnE '^import (java|android)\.|runCatching' shared/src/commonMain shared/src/commonTest` → no output |
| AC6 | No out-of-scope edits | `git diff --name-only origin/main...HEAD -- . ':!docs/adw/**'` → exactly: `core/network/{Photo,PhotoApi,FakePhotoApi,KtorPhotoApi,NetworkModule}.kt`, `core/data/{PhotoRepository,DataModule}.kt`, `$T` |
