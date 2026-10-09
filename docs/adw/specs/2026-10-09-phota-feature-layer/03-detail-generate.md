unit: u3
intent: phota-feature-layer
depth: D3
packaging: chain A / sub-PR 3
after: [u2]
verify: bash -c 'for f in shared/src/commonMain/kotlin/com/humbertouchiyama/phota/feature/generate/GenerateState.kt shared/src/commonMain/kotlin/com/humbertouchiyama/phota/feature/generate/GenerateViewModel.kt shared/src/commonMain/kotlin/com/humbertouchiyama/phota/feature/gallery/DetailViewModel.kt shared/src/commonMain/kotlin/com/humbertouchiyama/phota/feature/gallery/DetailScreen.kt shared/src/commonTest/kotlin/com/humbertouchiyama/phota/feature/generate/GenerateViewModelTest.kt shared/src/commonTest/kotlin/com/humbertouchiyama/phota/feature/gallery/DetailViewModelTest.kt; do [ -f "$f" ] || { echo "VERIFY missing $f"; exit 1; }; done && ! grep -rnE "GlobalScope|runBlocking|import io.ktor" shared/src/commonMain/kotlin/com/humbertouchiyama/phota/feature && ./gradlew :shared:testAndroidHostTest --tests "com.humbertouchiyama.phota.feature.generate.GenerateViewModelTest" --tests "com.humbertouchiyama.phota.feature.gallery.DetailViewModelTest" --rerun --console=plain && ./gradlew :shared:iosSimulatorArm64Test --tests "com.humbertouchiyama.phota.feature.generate.GenerateViewModelTest" --tests "com.humbertouchiyama.phota.feature.gallery.DetailViewModelTest" --console=plain'

# u3 — Detail screen and generate flow

`verify` is the brief's, unchanged; further file checks are ACs. Plan: `docs/adw/plans/2026-10-09-phota-feature-layer-03-detail-generate.md`.

## Problem

After u2, a gallery tile opens `DetailPlaceholder(photoId, onBack)` in `App.kt`, and `generateModule` is empty. u1's
`PhotoRepository` offers `photo(id)`, `upload()`, `generate(sourceId)`, but nothing shows a photo or owns the
upload-then-generate pipeline's progress, failure and cancellation.

## Change

- `feature/gallery`: `DetailUiState.kt` (`DetailUiState`, `DetailUiEvent`), `DetailViewModel(photoId, repository)`,
  `DetailScreen(photoId, onBack)`; `galleryModule` gains the Detail binding.
- `feature/generate`: `GenerateState.kt` (`GenerateState`, `Phase`, `GenerateUiEvent`), `GenerateViewModel(sourceId, repository)`,
  stateless `GeneratePanel(state, onEvent)`; `generateModule` fills its placeholder.
- `feature/PhotoImage.kt`: `PhotoImage(photo, modifier)`, the one Coil call site for Detail and Done.
- `App.kt`: Detail branch → `DetailScreen(photoId, onBack = { screen = Screen.Gallery })`; `DetailPlaceholder` deleted.
- `commonTest`: `feature/ScriptedPhotoRepository.kt` (package `com.humbertouchiyama.phota.feature`), the two tests.

No catalog/build change, no `Koin.kt` edit, no icons dependency.

## Decisions

- **Names.** `DetailUiEvent` / `GenerateUiEvent`, never `GenerateEvent` (u1's `core.network` type). Code names the detail
  error as `DetailUiState.Error`, never bare `Error` (would resolve to `kotlin.Error`).
- **Detail machine.** `Loading` · `Content(photo)` · `Error(canRetry)`; events `Load` · `Retry`. `init { load() }`, declared
  after `_state` and `job`. The VM folds `Result`; any failure, a missing id included, is `Error(canRetry = true)` (no
  typed not-found in the pinned contract; same rule as u2). `Error(canRetry = false)` is unreachable in u3.
  `Load` re-runs only in `Loading` with no active job, else no-op; `Retry` runs only from `Error(canRetry = true)`, else
  no-op. A cancelled load (a `CancellationException` escaping `photo()`) is never `Error`: state stays `Loading`, the job
  ends, and the next `Load` re-runs it. `DetailScreen` sends `Load` in `LaunchedEffect(Unit)`, so re-entry recovers.
- **Generate machine.** `Idle` · `Uploading(progress)` · `Generating(progress)` · `Done(photo)` · `Failed(phase, canRetry)`,
  `Phase { Upload, Generate }`; events `Start` · `Cancel` · `Retry` · `Reset`. Every pair not in the table is a no-op.

  | From | Event / signal | To |
  |---|---|---|
  | Idle | Start | Uploading(0f), new run |
  | Failed(canRetry = true) | Retry | Uploading(0f), new run from upload (Q4: upload leaves nothing to resume) |
  | Uploading, Generating | Cancel | Idle, run cancelled |
  | Done, Failed | Reset | Idle |
  | Uploading | emits p / completes | Uploading(p) / Generating(0f) |
  | Generating | Progress(f) | Generating(f) |
  | Generating | `Finished(photo)` | Done(photo) **at once**; collection stops (later Progress, a later throw or no completion are never seen) |
  | Generating | completes with no `Finished` | Failed(Generate, canRetry = false) |
  | running | non-cancellation `Exception` | Failed(phase running at the throw, canRetry = true) |
  | running | `CancellationException` from anywhere | Idle, rethrown, job ends cancelled; never Failed |

  Done on `Finished`, not on completion: u1 prepends to the cache at `Finished`, so the photo already exists.
- **Stale-run guard.** Each run takes `run = ++runId`; every write inside the job is conditional on `runId == run`.
  `Cancel` bumps `runId`, cancels, writes `Idle` synchronously. Otherwise a cancelled run whose handler resumes after a
  new `Start` would overwrite it with `Idle`. A counter, not `job === self`: on `Main.immediate` the body runs before
  `job` is assigned. `onEvent` is main-thread only; no atomics.
- **Cancellation.** `catch (CancellationException)` (`kotlinx.coroutines`) precedes `catch (Exception)` and rethrows;
  no `Throwable` catch. The job is `internal` read-only so tests can assert it ended cancelled.
- **Koin.** `viewModel { (id: String) -> DetailViewModel(id, get()) }` in `galleryModule`, same for `GenerateViewModel`
  in `generateModule`; DSL import `org.koin.core.module.dsl.viewModel` (multiplatform; not the Android-only
  `org.koin.androidx…`). Not `viewModelOf`: its constructor-reference form resolves the `String` with `get()` and would rely
  on Koin's parameter fallback. If the import does not resolve on the pinned Koin, stop and report; no catalog change.
  Resolution is proven by u4's `KoinGraphTest`, which must use `parametersOf("1")` for **both** ViewModels.
- **Per-photo keys.** `koinViewModel(key = "detail:$photoId") { parametersOf(photoId) }` and `key = "generate:$photoId"`:
  the store owner is the Activity/ViewController (no nav library), so an unkeyed second photo would reuse the first VM.
- **Leaving Detail does not cancel a run.** It finishes into the repository cache; re-opening that photo shows its state.
  Cost: one idle VM pair per visited photo per process.
- **Q3 gap — known limitation.** u2 reloads the gallery on entry (`LaunchedEffect(Unit) { Load }`). A run that reaches
  `Done` while the user is on Detail shows up on Back. A run that finishes **after** the user left Detail is in the cache
  but not on screen until u2's `Refresh`, or the next re-entry. No new machinery. **u4 copies this README line:**
  `- **Gallery reloads on entry, no observable cache** — a generate that finishes after leaving Detail appears on the next Refresh or re-entry; alternative: a StateFlow-backed cache in the repository.`
- **Screen.** Text "Back" `TextButton` (u2: no `BackHandler`), then exhaustive `when (DetailUiState)`: `Loading`
  spinner; `Error` message + "Retry" if `canRetry`; `Content` `PhotoImage` + `GeneratePanel`. `GeneratePanel` exhaustive
  `when (GenerateState)`: `Idle` "Generate"; `Uploading`/`Generating` phase label + determinate `LinearProgressIndicator`
  + percent + "Cancel"; `Done` `PhotoImage(new photo)` + "Generate again" (`Reset`); `Failed` phase message + "Retry" if
  `canRetry` + "Dismiss" (`Reset`). `PhotoImage` = Coil `SubcomposeAsyncImage` with a `loading` slot (box + spinner) and
  an `error` slot (box + text "Image unavailable"), author as `contentDescription`.

## Approved answers

- **Q3** — as "Q3 gap" above; a test asserts `Done` carries the `Finished` photo.
- **Q4** — upload consumed as `Flow<Float>`; Retry restarts from upload.
- **Q5** — both test classes run on `iosSimulatorArm64Test`.

## Acceptance criteria

Run from the repo root after `F=shared/src/commonMain/kotlin/com/humbertouchiyama/phota; T=shared/src/commonTest/kotlin/com/humbertouchiyama/phota`.

| # | Criterion | Check |
|---|---|---|
| AC1 | 17 generate + 8 detail tests ran and passed on the JVM | `verify`; then `grep -ho 'tests="[0-9]*"' shared/build/test-results/testAndroidHostTest/TEST-com.humbertouchiyama.phota.feature.generate.GenerateViewModelTest.xml shared/build/test-results/testAndroidHostTest/TEST-com.humbertouchiyama.phota.feature.gallery.DetailViewModelTest.xml` prints `tests="17"`, `tests="8"` |
| AC2 | Same on Kotlin/Native | `./gradlew :shared:iosSimulatorArm64Test --tests "com.humbertouchiyama.phota.feature.generate.GenerateViewModelTest" --tests "com.humbertouchiyama.phota.feature.gallery.DetailViewModelTest" --rerun --console=plain`, then the AC1 grep under `shared/build/test-results/iosSimulatorArm64Test/` prints 17 and 8 |
| AC3 | Machines behave per the tables (named tests, plan Tasks 3, 5) | covered by AC1/AC2 |
| AC4 | Extra files exist | `ls "$F/feature/gallery/DetailUiState.kt" "$F/feature/generate/GeneratePanel.kt" "$F/feature/PhotoImage.kt" "$T/feature/ScriptedPhotoRepository.kt"` exits 0 |
| AC5 | Keys + parameters | `grep -c -e 'key = "detail:$photoId"' -e 'key = "generate:$photoId"' "$F/feature/gallery/DetailScreen.kt"` prints 2; `grep -c "parametersOf(photoId)" "$F/feature/gallery/DetailScreen.kt"` prints 2 |
| AC6 | Bindings take the id | `grep -c "(id: String) ->" "$F/feature/gallery/GalleryModule.kt" "$F/feature/generate/GenerateModule.kt"` prints `:1` for each |
| AC7 | No `else` branch in any `when` | `! grep -nE "else[[:space:]]*->" "$F/feature/gallery/DetailScreen.kt" "$F/feature/generate/GeneratePanel.kt" "$F/feature/gallery/DetailViewModel.kt" "$F/feature/generate/GenerateViewModel.kt"` |
| AC8 | Private mutable state, one entry point | for each of `"$F/feature/gallery/DetailViewModel.kt" "$F/feature/generate/GenerateViewModel.kt"`: `grep -c "private val _state = MutableStateFlow"` prints 1 and `grep -c "fun onEvent("` prints 1 |
| AC9 | Forbidden imports | `! grep -rn -e "^import java\." -e "^import android\." -e "^import io\.ktor" -e "^import io\.mockk" "$F/feature" "$F/App.kt" shared/src/commonTest` |
| AC10 | Seam replaced, Koin untouched | `! grep -n DetailPlaceholder "$F/App.kt"` and `git diff --quiet "$(git merge-base adw/phota-feature-layer HEAD)" HEAD -- "$F/di/Koin.kt"` |
| AC11 | Image loading/error fallback, single Coil call site | `grep -c "Image unavailable" "$F/feature/PhotoImage.kt"` prints 1; `! grep -n "AsyncImage(" "$F/feature/gallery/DetailScreen.kt" "$F/feature/generate/GeneratePanel.kt"` |
| AC12 | Whole suites + Android build | `./gradlew :shared:testAndroidHostTest --console=plain`, `./gradlew :shared:iosSimulatorArm64Test --rerun --console=plain`, `./gradlew :androidApp:assembleDebug :androidApp:lintDebug --console=plain` |

Manual (declared skip allowed, repo-profile §6): open a photo, Generate, Cancel, Generate, wait on Detail for Done, Back →
the generated photo is first in the grid. Turn networking off → the photo box shows "Image unavailable".

## Design

**Chosen:** two ViewModels, one machine and one `onEvent` each, composed by one screen; ids via Koin parameters with
per-photo keys; a run counter guarding in-job writes; Done on `Finished`.

**Rejected.**
- *One ViewModel for both machines* — couples a one-shot load to a long job; doubles every test fixture.
- *Cancel on dispose* — Android rotation disposes the composition and would kill the run.
- *Per-destination `ViewModelStoreOwner`* — needs a nav library or an API not pinned in lifecycle 2.11.
- *Resume Retry at the failed phase* — nothing to resume (Q4); one more state.
- *Done on flow completion* — a throw after `Finished` would show Failed for a photo already in the gallery.
- *Map a cancelled detail load to `Error`* — cancellation is not failure (repo-profile §15); `Load` recovers instead.

## Out of scope

Navigating to the generated photo, a system back handler, Compose UI tests, any `PhotoRepository` change, an observable
cache, Koin resolution tests (u4), gates 5/6, README edits (u4).
