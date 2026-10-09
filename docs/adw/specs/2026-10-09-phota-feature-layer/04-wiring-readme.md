unit: u4
intent: phota-feature-layer
depth: D2
packaging: chain A / sub-PR 3
after: [u3]
verify: bash -c 'for f in shared/src/commonTest/kotlin/com/humbertouchiyama/phota/di/KoinGraphTest.kt; do [ -f "$f" ] || { echo "VERIFY missing $f"; exit 1; }; done && ! grep -qE "^-[[:space:]]*$" README.md && grep -q "KtorPhotoApi" README.md && ./gradlew :shared:iosSimulatorArm64Test :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug --console=plain && xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "generic/platform=iOS Simulator" -configuration Debug ARCHS=arm64 CODE_SIGNING_ALLOWED=NO -derivedDataPath build/xcode build'

# u4: Koin graph proof, README write-up, full-app verification

## Problem

u1-u3 test their own layers, but nothing proves the four Koin modules compose: a missing binding
would only crash at launch. The README says "No features yet", lacks every feature decision, and
ends in an empty `-`. No single run has built both test targets, Android lint and the iOS app.

## Decided / approved (recorded)

- Q1: real API is `KtorPhotoApi` on `picsum.photos/v2/list`; fake bound by default; swap is one line in `networkModule`, documented in the README.
- Q2: `FakePhotoApi` lives in commonMain. Q3: a finished generate adds the photo to the gallery through the repository cache. Q4: upload simulated, no file picker. Q5: this unit adds the iOS test and the full build.
- Owner: one `shared` module, Compose Multiplatform on both, no MockK, no database, no new catalog dependency.
- Hand-resolved beans, not `module.verify()` / `checkModules`: `verify` is JVM-only.
- Test the real entry point: `initKoin()` (no-arg, returns Unit, calls `startKoin`) then `KoinPlatform.getKoin()`, so the module list in `di/Koin.kt` cannot drift from the test. Fallback only if `initKoin()` proves unusable in a test: `startKoin` with the four modules listed by hand, and say why in the PR.
- `di/Koin.kt` is not edited. No production code changes.

## Acceptance criteria

| # | Criterion | Check |
|---|---|---|
| AC1 | `KoinGraphTest` resolves the repository and the three ViewModels on the JVM host, and ran | `./gradlew :shared:testAndroidHostTest --tests '*KoinGraphTest*' --rerun --console=plain`; then `grep -h -o 'tests="[0-9]*"' shared/build/test-results/testAndroidHostTest/*KoinGraphTest*.xml` shows the 5 tests (§12: UP-TO-DATE ran nothing) |
| AC2 | Same on Native | `./gradlew :shared:iosSimulatorArm64Test --tests '*KoinGraphTest*' --rerun --console=plain`; then the same `grep` on `shared/build/test-results/iosSimulatorArm64Test/*KoinGraphTest*.xml` |
| AC3 | Default `PhotoApi` is `FakePhotoApi` | test `defaultPhotoApiIsFake` (in AC1/AC2 counts) |
| AC4 | `PhotoRepository` is one shared instance (Q3 depends on it) | test `repositoryIsSingleton`: `get<PhotoRepository>() === get<PhotoRepository>()` |
| AC5 | No empty README bullet | `bash -c '! grep -qE "^-[[:space:]]*$" README.md'` |
| AC6 | README names the swap and the line | `bash -c 'grep -q KtorPhotoApi README.md && grep -q "Switching to the real API" README.md && grep -qF "$(grep -m1 -E "PhotoApi" shared/src/commonMain/kotlin/com/humbertouchiyama/phota/core/network/NetworkModule.kt | sed "s/^[[:space:]]*//")" README.md'` (the README quotes verbatim the one line `single<PhotoApi> { FakePhotoApi() }`, the only `PhotoApi` line in that file, pinned by u1) |
| AC7 | Intro updated | `bash -c '! grep -q "No features yet" README.md'` |
| AC8 | Exactly 18 bold-led bullets: 3 "How to run" + 5 existing + 10 new decisions | `bash -c '[ "$(grep -c "^- \*\*" README.md)" -eq 18 ]'` (a further u1-u3 decision is folded into one of the 10 lines) |
| AC9 | Android assembles and lints | `./gradlew :androidApp:assembleDebug :androidApp:lintDebug --console=plain` |
| AC10 | iOS app builds | the `xcodebuild` command of `verify` |
| AC11 | Diff is only the test and the README (spec files excluded) | `bash -c 'git diff --name-only origin/main...HEAD -- . ":!docs/adw/**" | sort | tr "\n" " "'` prints exactly `README.md shared/src/commonTest/kotlin/com/humbertouchiyama/phota/di/KoinGraphTest.kt` |

## Implementation

Precondition: u1-u3 are merged in the chain. First re-read `01-*`, `02-*`, `03-*` in this folder and
the real code; their names, packages and decisions win over this spec's. Reconcile before writing.

| # | File | Exact change | Command |
|---|---|---|---|
| 1 | `shared/src/commonTest/kotlin/com/humbertouchiyama/phota/di/KoinGraphTest.kt` (new) | `@BeforeTest`: `Dispatchers.setMain(UnconfinedTestDispatcher())` (the JVM host has no Main for `viewModelScope`), then `initKoin()`, then `koin = KoinPlatform.getKoin()`. `@AfterTest`, unconditional and in this order: `stopKoin()` (runs even if start threw), then `Dispatchers.resetMain()`. Five tests: repository resolves; `repositoryIsSingleton`; `defaultPhotoApiIsFake` (`assertIs<FakePhotoApi>`); gallery, detail, generate ViewModels resolve (detail and generate both via `parametersOf("1")`, since u3 binds each with a photo-id parameter). A resolved ViewModel may launch in `init`: no `runBlocking`; run the unconfined dispatcher so jobs finish or suspend on the fakes, and if one outlives the test, clear it (a `ViewModelStore` the test fills and `clear()`s in teardown, before `stopKoin()`) so no job leaks. Existing deps only. Ref AC1-AC4. | AC1 and AC2 commands |
| 2 | `README.md` intro | Replace "No features yet." with 1-2 sentences naming the gallery/detail and fake generate/upload over a swappable Ktor API. | AC7 |
| 3 | `README.md` decisions | One line each, existing style (`- **Choice** — reason; alternative: X.`), 10 new: sealed `UiState` per screen with `onEvent` + `StateFlow`; `Result`-based repository, ViewModels never see Ktor; `FakePhotoApi` in commonMain swapped by Koin (Q1, Q2); in-memory cache, no database; state-based navigation, no library; Coil 3 with the iOS fetcher setup (copy wiring from u2); ViewModel tests with Turbine and hand-written fakes; finished generate joins the gallery via the repository cache (Q3), with u3's known limitation (a run that ends after the user left Detail shows in the grid only after Refresh or re-entry; quote u3's "Q3 gap" wording); upload simulated, no picker (Q4); `KoinGraphTest` resolves beans by hand through `initKoin()`. Keep the existing 5. | AC8 |
| 4 | `README.md` swap | `### Switching to the real API`: 3-5 lines, default `FakePhotoApi`, quote the line `single<PhotoApi> { FakePhotoApi() }` verbatim and say to replace it in `NetworkModule.kt` with `single<PhotoApi> { KtorPhotoApi(get()) }`, name `defaultPhotoApiIsFake`. | AC6 |
| 5 | `README.md` more time | Replace `-` with one-line bullets: real API with auth; persistence; navigation library; compose-ui-test UI tests; real file picker; pagination; detekt/ktlint; SwiftUI alternative; `KtorPhotoApi` and `createHttpClient` are untested (no `ktor-client-mock`, which needs a catalog change) and the client has no `HttpTimeout`. | AC5 |
| 6 | `README.md` architecture | Make the tree match the real directories if u1-u3 changed them. | `find shared/src/commonMain -type d` |
| 7 | Whole app | Run the header `verify` command. | `verify` |

Blocked outcome: if a failure comes from u1-u3 code (a missing binding, a broken test, lint, build),
stop, report u4 as `blocked` naming the file and the error, and patch nothing outside the two files.

## Not doing

No `module.verify()`, no `di/Koin.kt` edit, no catalog change, no UI tests, no CI change.

## Owner-only question

None.
