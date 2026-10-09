# u3 Detail + Generate Implementation Plan

> **Executed by:** /adw-build §3.1 implementer.

**Goal:** Replace u2's Detail seam with a detail screen that loads one photo and drives a cancellable
upload-then-generate flow, both machines tested on the JVM host and the iOS simulator.

**Architecture:** spec "Change" and "Decisions" (two ViewModels, Koin-parameterised, one screen).

**Tech Stack:** Kotlin 2.4 KMP, Compose Multiplatform 1.12 + material3, lifecycle ViewModel 2.11, Koin 4.2
(`koin-compose-viewmodel`), Coil 3, kotlin.test + Turbine 1.2 + kotlinx-coroutines-test 1.11.

**Spec:** `docs/adw/specs/2026-10-09-phota-feature-layer/03-detail-generate.md` ("spec").

`F` / `T` as in the spec's AC preamble. Gradle commands run from the worktree root, one at a time (repo-profile §10).
`superpowers:test-driven-development`: each test task runs red before its production task.

---

### Task 0: Preconditions (read only)

- [ ] Read u1's `Photo`/`GenerateEvent` file under `$F/core/network/` and `$F/core/data/PhotoRepository.kt`; signatures
  must equal the spec's pinned contract, else stop and report.
- [ ] Read u2's `$F/App.kt`, `$F/feature/gallery/GalleryModule.kt`, `$F/feature/gallery/GalleryScreen.kt`. Confirm
  `Screen.Detail(photoId)`, the private `DetailPlaceholder` seam, and `LaunchedEffect(Unit) { …Load }` in `GalleryScreen`.
  A missing reload-on-entry is reported as a u2 defect, not fixed here.
- [ ] Collisions: `grep -rn -e "fun testPhoto(" -e "class ScriptedPhotoRepository" -e "class FakeError" -e "fun photo(" -e "FakePhotoRepository" shared/src/commonTest`.
  u2's `FakePhotoRepository` and any `fun photo(` it has stay untouched; if one of our three names already exists in
  package `com.humbertouchiyama.phota.feature`, suffix ours with `U3`.

### Task 1: Scripted fake

**File:** create `$T/feature/ScriptedPhotoRepository.kt`, package `com.humbertouchiyama.phota.feature`.

- [ ] Top-level `fun testPhoto(id: String) = Photo(id, "author-$id", "https://example.test/$id.jpg")` and
  `class FakeError : Exception("boom")`.
- [ ] `class ScriptedPhotoRepository : PhotoRepository` with these test controls:
  - `var photoResult: suspend (String) -> Result<Photo>` (default `Result.success(testPhoto(it))`), `photoRequests: MutableList<String>`.
  - `upload()`: creates a fresh `Channel<Float>(Channel.UNLIMITED)`, appends it to `uploads`, returns
    `consumeAsFlow().onCompletion { completions += it }`. `generate(sourceId)`: same, appends `sourceId to channel` to `generates`.
  - `photos()` returns `Result.success(emptyList())`.
- Test drives: `trySend(x)` emits; `close()` completes; `close(FakeError())` fails; `close(CancellationException("x"))` is a
  foreign cancellation. Channels exist once the VM's job has started collecting (`runCurrent()` on a Standard Main).
- Tests import `com.humbertouchiyama.phota.feature.{ScriptedPhotoRepository, testPhoto, FakeError}` explicitly.
  `CancellationException` is `kotlinx.coroutines.CancellationException` everywhere.

### Task 2: GenerateState

- [ ] Create `$F/feature/generate/GenerateState.kt` with the types in spec "Generate machine", plus
  `val GenerateState.isRunning get() = this is GenerateState.Uploading || this is GenerateState.Generating`.

### Task 3: GenerateViewModelTest (red)

**File:** create `$T/feature/generate/GenerateViewModelTest.kt`.

- [ ] Fixture: `@BeforeTest Dispatchers.setMain(UnconfinedTestDispatcher())`, `@AfterTest Dispatchers.resetMain()`,
  `repo = ScriptedPhotoRepository()`, `vm = GenerateViewModel("src-1", repo)`. Tests are `runTest { vm.state.test { … } }`
  ending in `expectNoEvents()` or `cancelAndIgnoreRemainingEvents()`. StateFlow drops equal consecutive values: send
  `0.5f`, `1f`, never a first `0f`.
- [ ] Job assertions use `vm.job` (internal, Task 4). Capture it before `Cancel` (`val j = vm.job!!`).
- [ ] The 17 tests:

| Test | Drive → assert |
|---|---|
| `success_endsDone_withFinishedPhoto` | upload 0.5f, close; Progress(0.5f), Finished(testPhoto("gen-1")) → Idle, Uploading(0f), Uploading(.5f), Generating(0f), Generating(.5f), Done(testPhoto("gen-1")) |
| `generate_usesSourceId` | close upload → `repo.generates.single().first == "src-1"` |
| `uploadFailure_isFailedUpload_noLiveJob` | upload `close(FakeError())` → Failed(Upload, true); `vm.job!!.isActive == false` |
| `generateFailure_isFailedGenerate` | generate `close(FakeError())` → Failed(Generate, true) |
| `generateWithoutFinished_isNotRetryable` | Progress(1f), close → Failed(Generate, false); Retry → no event |
| `afterFinished_laterSignalsIgnored` | send Finished, Progress(0.9f), then `close(FakeError())` → last item Done; no further event |
| `retry_afterFailure_succeeds` | upload fails; Retry → Uploading(0f) … Done; `repo.uploads.size == 2` |
| `reset_fromDone_isIdle` | success; Reset → Idle |
| `reset_fromFailed_isIdle` | upload fails; Reset → Idle |
| `start_whileRunning_isIgnored` | Start, Start → `repo.uploads.size == 1`, no extra item |
| `retry_whileRunning_isIgnored` | Start, Retry → no extra item, `repo.uploads.size == 1` |
| `start_fromDoneOrFailed_isIgnored` | at Done: Start → no event; Reset, fail a run, at Failed: Start → no event |
| `cancel_fromIdle_isNoOp` | Cancel → no event, `vm.job == null` |
| `cancel_midUpload_isIdle` | upload 0.5f; Cancel → Idle; `j.isCancelled`; `repo.completions.single() is CancellationException` |
| `cancel_midGenerate_isIdle` | to Generating(.5f); Cancel → Idle; `j.isCancelled`; last completion is `CancellationException` |
| `foreignCancellation_isRethrown_neverFailed` | upload `close(CancellationException("x"))` → Idle, never Failed; `vm.job!!.isCancelled` (a swallow completes it normally) |
| `cancelThenStart_staleRunCannotOverwrite` | see below |

- [ ] `cancelThenStart_staleRunCannotOverwrite` overrides Main with `StandardTestDispatcher(testScheduler)` (call
  `Dispatchers.setMain` again inside `runTest`, build the VM after it). Start; `runCurrent()` (job 1 suspends in the
  upload collect); Cancel; Start; `advanceUntilIdle()`. Assert `vm.state.value == Uploading(0f)` and
  `repo.uploads.size == 2`. Job 1's cancellation handler is dispatched, so it resumes after the second Start: without the
  guard it writes `Idle` and this test fails. It must not read `vm.state` through Turbine (ordering is the point).
- [ ] Run, expect compile failure:
  `./gradlew :shared:testAndroidHostTest --tests "com.humbertouchiyama.phota.feature.generate.GenerateViewModelTest" --rerun --console=plain`

### Task 4: GenerateViewModel (green)

**File:** create `$F/feature/generate/GenerateViewModel.kt`.

- [ ] Fields, in order: `private val _state = MutableStateFlow<GenerateState>(GenerateState.Idle)`, public
  `val state: StateFlow<GenerateState> = _state.asStateFlow()`, `private var runId = 0`,
  `internal var job: Job? = null; private set`.
- [ ] `fun onEvent(event: GenerateUiEvent)`: exhaustive `when (event)`, each branch guarded exactly per the spec table
  (`Start` only from `Idle`; `Retry` only from `Failed` with `canRetry`; `Cancel` only while `isRunning`, doing
  `runId++`, `job?.cancel()`, `_state.value = Idle` in that order; `Reset` only from `Done`/`Failed`).
- [ ] `startRun()` invariants (write it, do not copy it from here):
  1. `val run = ++runId` and `_state.value = Uploading(0f)` happen **before** `viewModelScope.launch`.
  2. Inside the job, every state write goes through a local `set(s)` that writes only when `runId == run`.
  3. A local `phase` starts `Upload` and becomes `Generate` **before** `repository.generate(sourceId)` is called and
     before `Generating(0f)` is written.
  4. Generate is collected through `transformWhile { emit(it); it !is GenerateEvent.Finished }`; `Finished` writes
     `Done(photo)`; if collection ends without one, write `Failed(Generate, canRetry = false)`.
  5. `catch (e: CancellationException) { set(Idle); throw e }` first, then `catch (e: Exception) { set(Failed(phase, true)) }`.
- [ ] Run Task 3's command → `BUILD SUCCESSFUL`; `grep -o 'tests="[0-9]*"' shared/build/test-results/testAndroidHostTest/TEST-com.humbertouchiyama.phota.feature.generate.GenerateViewModelTest.xml` → `tests="17"`.
- [ ] Mutation check (revert each): remove the `runId == run` condition → `cancelThenStart_staleRunCannotOverwrite` fails;
  remove `throw e` → `foreignCancellation_isRethrown_neverFailed` fails; swap the catch order → that test fails.
  A mutation that leaves the suite green is a defect in this task: report it, do not ship.

### Task 5: DetailViewModelTest (red)

**Files:** create `$F/feature/gallery/DetailUiState.kt` (types per spec "Detail machine") and `$T/feature/gallery/DetailViewModelTest.kt`.

- [ ] Fixture as Task 3 (Unconfined Main). `init` loads at construction, so tests that observe `Loading` set
  `repo.photoResult = { gate.await() }` with `val gate = CompletableDeferred<Result<Photo>>()` **before** building the VM,
  then complete `gate` inside `test {}`. Refer to `DetailUiState.Error` qualified.
- [ ] The 8 tests:

| Test | Drive → assert |
|---|---|
| `loads_content` | gate; Loading; `gate.complete(success(testPhoto("7")))` → Content(testPhoto("7")) |
| `missingPhoto_isError` | `photoResult = { Result.failure(FakeError()) }` → Error(canRetry = true) |
| `retry_afterError_isContent` | Error(true); switch to success; Retry → Loading, Content |
| `cancelledLoad_staysLoading_andLoadRecovers` | first call throws `CancellationException("x")`, second succeeds → Loading, no Error (`expectNoEvents`); Load → Content; `repo.photoRequests.size == 2` |
| `requests_ownPhotoId` | `repo.photoRequests == listOf("7")` |
| `retry_fromContent_isNoOp` | Content; Retry → no event, 1 request |
| `retry_whileLoading_isNoOp` | gate pending; Retry → no event, 1 request |
| `load_whileActiveOrContent_isNoOp` | gate pending: Load → 1 request; complete → Content; Load → still 1 request |

- [ ] Run, expect compile failure:
  `./gradlew :shared:testAndroidHostTest --tests "com.humbertouchiyama.phota.feature.gallery.DetailViewModelTest" --rerun --console=plain`

### Task 6: DetailViewModel (green)

**File:** create `$F/feature/gallery/DetailViewModel.kt`.

- [ ] Order: `_state` (private, `Loading`), `state`, `private var job: Job? = null`, **then** `init { load() }`.
- [ ] `onEvent`: exhaustive `when`; `Load` → `load()` only if state is `Loading` and `job?.isActive != true`; `Retry` →
  `load()` only from `DetailUiState.Error(canRetry = true)`.
- [ ] `load()`: write `Loading`, launch, write `repository.photo(photoId).fold(::Content) { DetailUiState.Error(true) }`.
  No try/catch: an escaping `CancellationException` ends the job and leaves `Loading` (spec).
- [ ] Run Task 5's command → `BUILD SUCCESSFUL`; XML `tests="8"`.

### Task 7: Koin bindings

- [ ] `$F/feature/generate/GenerateModule.kt`: import `org.koin.core.module.dsl.viewModel`; body
  `viewModel { (id: String) -> GenerateViewModel(id, get()) }`; placeholder comment becomes
  `// Generate flow ViewModel, keyed by source photo id (u3 spec).`
- [ ] `$F/feature/gallery/GalleryModule.kt`: add `viewModel { (id: String) -> DetailViewModel(id, get()) }` beside u2's line.
- [ ] No `viewModelOf` (spec "Koin"); `$F/di/Koin.kt` untouched. Resolution is u4's test.

### Task 8: UI

- [ ] `$F/feature/PhotoImage.kt` (package `com.humbertouchiyama.phota.feature`): `@Composable fun PhotoImage(photo: Photo, modifier: Modifier = Modifier)`
  = `SubcomposeAsyncImage` (`coil3.compose`) with `contentScale = ContentScale.Crop`, `loading` and `error` slots per spec "Screen".
- [ ] `$F/feature/generate/GeneratePanel.kt`: `GeneratePanel(state, onEvent, modifier)` per spec "Screen"; progress text
  `"${(progress * 100).toInt()}%"`; text buttons only.
- [ ] `$F/feature/gallery/DetailScreen.kt`: `DetailScreen(photoId, onBack)`; both VMs via
  `koinViewModel<…>(key = "detail:$photoId") { parametersOf(photoId) }` / `"generate:$photoId"`
  (`org.koin.compose.viewmodel.koinViewModel`, `org.koin.core.parameter.parametersOf`); `LaunchedEffect(Unit) { detail.onEvent(DetailUiEvent.Load) }`;
  `collectAsStateWithLifecycle()`; a `verticalScroll` column: Back, then the spec's `when`.
- [ ] `$F/App.kt`: Detail branch → `DetailScreen(photoId = <branch>.photoId, onBack = { screen = Screen.Gallery })`;
  delete `DetailPlaceholder` and its seam comment; nothing else.
- [ ] `./gradlew :shared:compileKotlinIosSimulatorArm64 --console=plain` → `BUILD SUCCESSFUL`; then spec AC4–AC11, each as written.

### Task 9: Full verification

- [ ] Spec header `verify`, whole → both runs `BUILD SUCCESSFUL`.
- [ ] `./gradlew :shared:testAndroidHostTest --console=plain` (unfiltered: u1/u2 tests still green).
- [ ] `./gradlew :shared:iosSimulatorArm64Test --rerun --console=plain` (unconditional `--rerun`), then read
  `grep -ho 'tests="[0-9]*"'` on both test XMLs under `shared/build/test-results/iosSimulatorArm64Test/` and
  `testAndroidHostTest/` → 17 and 8 on each (AC1, AC2).
- [ ] Native-only failure → `superpowers:systematic-debugging` (suspects: Main set after VM construction; a JVM-only API).
- [ ] `./gradlew :androidApp:assembleDebug :androidApp:lintDebug --console=plain` (gate 4); no new Lint `Error`.
- [ ] Device smoke per spec "Manual", or declare the skip.
- [ ] `superpowers:verification-before-completion` before reporting.
