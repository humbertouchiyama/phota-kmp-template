# u1 Data Layer Implementation Plan

> **Executed by:** /adw-build §3.1 implementer.

**Goal:** Implement spec u1: the pinned photo contract, its fake, Ktor client, cached repository, Koin bindings.

**Architecture:** see spec "Design" — this plan does not restate it.

**Tech Stack:** Kotlin 2.4 KMP, coroutines 1.11, Ktor 3.6, kotlinx.serialization, Koin 4.2; kotlin.test, coroutines-test, Turbine.

**Spec:** `docs/adw/specs/2026-10-09-phota-feature-layer/01-data-layer.md`

Paths: `$M` = `shared/src/commonMain/kotlin/com/humbertouchiyama/phota/`, `$T` =
`shared/src/commonTest/kotlin/com/humbertouchiyama/phota/`. Constraints and Review Focus are the
spec's (Change, Added members, Design, AC5/AC6). No snippet or code comment may contain the word
`runCatching`; comments are one terse line with a spec ref.

## Review Focus

1. Generate before the first load still fetches the list (`generateBeforeFirstLoadStillFetches`).
2. A generated photo opens in detail from cache (`photoServesGeneratedFromCache`).
3. Cancellation thrown *by the API* is rethrown (`apiCancellationIsRethrown`).
4. A collector cancelled right after `Finished` keeps the photo (`finishedThenCancelKeepsPhoto`).
5. `KtorPhotoApi` / `createHttpClient()` are compile-only — no test exercises them, on either
   platform (spec: Design / Ktor is compile-only). Review them by reading.

---

### Task 1: Network contract + FakePhotoApi

**Files:** Create `$M/core/network/Photo.kt`, `$M/core/network/PhotoApi.kt`, `$M/core/network/FakePhotoApi.kt`.

**Interfaces — Produces:** spec "Pinned contract" verbatim; spec "Added members" for `FakePhotoApi`.

- [ ] **Step 1:** `Photo.kt` (`Photo`, `GenerateEvent`) and `PhotoApi.kt` — the pinned declarations.

- [ ] **Step 2:** `FakePhotoApi.kt`. Signatures and the invariants that matter:

```kotlin
class FakePhotoApi(var delayMillis: Long = 500, var progressSteps: Int = 10) : PhotoApi {
    var listCalls: Int = 0
        private set
    var getCalls: Int = 0
        private set
    fun failNext(times: Int = 1, error: Throwable = FakeApiException())  // replaces pending arming
    private fun takeFailure(): Throwable?                                 // consumes one arming, or null
    private fun progress(): Flow<Float> = flow {
        val pending = takeFailure()  // taken per collection (spec: failNext)
        val steps = progressSteps.coerceAtLeast(1)
        // for i in 1..steps: delay(delayMillis / steps); emit(i / steps); if (pending != null) throw pending
    }
}
```

  - `listPhotos`/`getPhoto`: increment counter, `delay(delayMillis)`, `takeFailure()?.let { throw it }`,
    then return. `getPhoto` unknown id → `FakeApiException("Photo $id not found")`.
  - `upload() = progress()`. `generate(sourceId) = flow { progress().collect { emit(Progress(it)) }; emit(Finished(...)) }`
    with `gen-<++counter>` and the seed URL from the spec.
  - Data: `(0..29).map { Photo("$it", "Author $it", "https://picsum.photos/id/$it/400/400") }`.

- [ ] **Step 3:** `./gradlew :shared:compileKotlinIosSimulatorArm64 --console=plain` → BUILD SUCCESSFUL.
- [ ] **Step 4:** commit the three files: `feat(network): photo contract and FakePhotoApi`.

### Task 2: PhotoRepository (TDD)

**Files:** Create `$M/core/data/PhotoRepository.kt`, `$T/core/data/PhotoRepositoryTest.kt`.

**Interfaces — Consumes:** Task 1. **Produces:** `PhotoRepository` (pinned), `PhotoRepositoryImpl(api: PhotoApi)`.

- [ ] **Step 1:** interface (pinned) + `PhotoRepositoryImpl` whose members are `TODO()`.

- [ ] **Step 2: Failing tests.** Each test: `val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)`
  inside `runTest {}`. Imports: `app.cash.turbine.test`, `kotlinx.coroutines.test.*`,
  `kotlinx.coroutines.CancellationException`, `kotlin.test.*`. One `@Test` per row (19):

| Test | Act | Assert |
|---|---|---|
| `photosSuccess` | `photos()` | success, 30 items, first id `"0"` |
| `photosFailure` | `failNext()`; `photos()` | `exceptionOrNull()` is `FakeApiException` |
| `failNextTwice` | `failNext(times = 2)`; `photos()` ×3 | failure, failure, success; `listCalls == 3` |
| `photosCacheHit` | `photos()` ×2 | `listCalls == 1`, equal lists |
| `forceRefreshRefetches` | `photos()`; `photos(forceRefresh = true)` | `listCalls == 2` |
| `failedRefreshKeepsCache` | `photos()`; `failNext()`; `photos(true)`; `photos()` | 2nd failure; 3rd success, 30 items; `listCalls == 2` |
| `generatePrependsToCache` | `photos()`; `generate("3").test { skip progress, Finished, awaitComplete() }`; `photos()` | first `"gen-1"`, 31 items, `listCalls == 1` |
| `generateBeforeFirstLoadStillFetches` | `generate("3").collect {}`; `photos()` | 31 items, first `"gen-1"`, `listCalls == 1` |
| `generateFailureDoesNotTouchCache` | `photos()`; `failNext()`; `generate("3").test { awaitItem(); awaitError() }`; `photos()` | error is `FakeApiException`; 30 items |
| `generateCancelledMidwayWritesNothing` | `photos()`; `val j = launch { repo.generate("3").collect {} }`; `advanceTimeBy(api.delayMillis / 2)`; `j.cancel()`; `advanceUntilIdle()` | `j.isCancelled`; `photos()` 30 items |
| `finishedThenCancelKeepsPhoto` | `repo.generate("3").first { it is GenerateEvent.Finished }` | `photos()` first is `"gen-1"` |
| `photoServesFromCache` | `photos()`; `photo("5")` | success, `getCalls == 0` |
| `photoServesGeneratedFromCache` | `generate("3").collect {}`; `photo("gen-1")` | success, `getCalls == 0` |
| `photoFallsBackToApi` | `photo("7")` on empty cache | success, `getCalls == 1` |
| `photoUnknownIdFails` | `photo("999")` | `exceptionOrNull()` is `FakeApiException` |
| `uploadPassesThrough` | `upload().toList()` | 10 values, last `1f` |
| `uploadCancelledCompletes` | `val j = launch { repo.upload().collect {} }`; `advanceTimeBy(api.delayMillis / 2)`; `j.cancel()`; `advanceUntilIdle()` | `j.isCancelled`, `j.isCompleted`; `photos()` 30 items |
| `apiCancellationIsRethrown` | `failNext(error = CancellationException("x"))`; `assertFailsWith<CancellationException> { repo.photos() }`; re-arm `failNext(error = CancellationException("y"))`; `assertFailsWith<CancellationException> { repo.photo("1") }` | both throw; no `Result` returned |
| `cancelledCallerGetsNoResult` | `var r: Result<*>? = null; val j = launch { r = repo.photos() }; runCurrent(); j.cancel(); advanceUntilIdle()` | `j.isCancelled`, `r == null` |

- [ ] **Step 3: Red.** `./gradlew :shared:testAndroidHostTest --tests "com.humbertouchiyama.phota.core.data.PhotoRepositoryTest" --rerun --console=plain`
  → FAIL (`NotImplementedError`).

- [ ] **Step 4: Implement.** Signatures and invariants:

```kotlin
// Rethrows cancellation, wraps other failures (spec: Design / Result helper).
internal suspend inline fun <T> resultOf(block: () -> T): Result<T>   // catch CancellationException → throw; catch Exception → failure

class PhotoRepositoryImpl(private val api: PhotoApi) : PhotoRepository {
    private val mutex = Mutex()
    private var remote: List<Photo>? = null
    private var generated: List<Photo> = emptyList()
}
```

  - `photos(forceRefresh)` (no default on the override): read `remote` under the lock; if null or
    `forceRefresh`, call `api.listPhotos()` **outside** the lock, then store under the lock; return
    `(generated + list).distinctBy { it.id }` — all inside `resultOf`.
  - `photo(id)`: `resultOf { lookup in generated + remote under the lock ?: api.getPhoto(id) }`.
  - `upload() = api.upload()`; `generate(id) = api.generate(id).onEach { if Finished, prepend to
    generated under the lock }` (`onEach` runs before the downstream sees the event).

- [ ] **Step 5: Green.** Step 3's command → BUILD SUCCESSFUL, then
  `grep -o 'tests="[0-9]*"' shared/build/test-results/testAndroidHostTest/TEST-com.humbertouchiyama.phota.core.data.PhotoRepositoryTest.xml` → `tests="19"`.
- [ ] **Step 6: Mutation sanity.** Delete the `CancellationException` catch in `resultOf`; rerun Step 3 →
  `apiCancellationIsRethrown` FAILS. Restore.
- [ ] **Step 7:** commit both files: `feat(data): PhotoRepository with in-memory cache`.

### Task 3: KtorPhotoApi + Koin modules

**Files:** Create `$M/core/network/KtorPhotoApi.kt`; modify `$M/core/network/NetworkModule.kt`,
`$M/core/data/DataModule.kt` (replace placeholder body and comment).

**Interfaces — Consumes:** Tasks 1–2. **Produces:** `createHttpClient()`, `KtorPhotoApi(client, simulator = FakePhotoApi())`,
`networkModule`, `dataModule` (names fixed — `di/Koin.kt` imports them).

- [ ] **Step 1:** `KtorPhotoApi.kt`:
  - `fun createHttpClient(): HttpClient = HttpClient { expectSuccess = true; install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }` — no engine argument.
  - `@Serializable private data class PicsumPhoto(val id: String, val author: String)` + `toPhoto()` (spec mapping).
  - `listPhotos` = `client.get("https://picsum.photos/v2/list").body<List<PicsumPhoto>>()` mapped;
    `getPhoto` = `.../id/$id/info`; `upload`/`generate` delegate to `simulator`.
- [ ] **Step 2:** modules — exactly:

```kotlin
val networkModule = module {
    single { createHttpClient() }
    single<PhotoApi> { FakePhotoApi() }
}
```

  `val dataModule = module { single<PhotoRepository> { PhotoRepositoryImpl(get()) } }`. No comment
  in `NetworkModule.kt` mentions `PhotoApi` (AC4).
- [ ] **Step 3:** run AC4 and AC5 greps from the spec → one line / no output.
- [ ] **Step 4:** run the spec's `verify` whole → green.
- [ ] **Step 5:** commit the three files: `feat(network): KtorPhotoApi and Koin bindings`.

### Task 4: Native gate + scope

- [ ] **Step 1:** `./gradlew :shared:iosSimulatorArm64Test --tests "com.humbertouchiyama.phota.core.data.PhotoRepositoryTest" --console=plain`
  → BUILD SUCCESSFUL; `grep -o 'tests="[0-9]*"' shared/build/test-results/iosSimulatorArm64Test/*.xml` → `tests="19"`.
- [ ] **Step 2:** run AC6 → exactly the 8 files the spec lists. Revert anything else.
