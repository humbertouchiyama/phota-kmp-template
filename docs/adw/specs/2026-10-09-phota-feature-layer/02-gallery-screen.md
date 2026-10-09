unit: u2
intent: phota-feature-layer
depth: D2
packaging: chain A / sub-PR 2
after: [u1]
verify: bash -c 'for f in shared/src/commonMain/kotlin/com/humbertouchiyama/phota/feature/gallery/GalleryUiState.kt shared/src/commonMain/kotlin/com/humbertouchiyama/phota/feature/gallery/GalleryViewModel.kt shared/src/commonMain/kotlin/com/humbertouchiyama/phota/feature/gallery/GalleryScreen.kt shared/src/commonTest/kotlin/com/humbertouchiyama/phota/feature/gallery/GalleryViewModelTest.kt; do [ -f "$f" ] || { echo "VERIFY missing $f"; exit 1; }; done && ! grep -rn "import io.ktor" shared/src/commonMain/kotlin/com/humbertouchiyama/phota/feature && ./gradlew :shared:testAndroidHostTest --tests "com.humbertouchiyama.phota.feature.gallery.GalleryViewModelTest" --rerun --console=plain && ./gradlew :shared:compileKotlinIosSimulatorArm64 --console=plain'

# u2 — Gallery screen

## Problem

`App.kt` renders a static text and `galleryModule` is an empty placeholder. u1 delivers `PhotoRepository`, but nothing shows photos. u3 (detail, which hosts generate) needs a navigation shell, and a finished generate (Q3) must appear in the gallery on return.

## Change

A MVI-lite gallery slice in `feature/gallery`: sealed `GalleryUiState` and `GalleryEvent`, a `GalleryViewModel` with one `onEvent` entry and a read-only `StateFlow`, and a `GalleryScreen` (`LazyVerticalGrid`, Coil 3 `AsyncImage`). `galleryModule` registers the ViewModel. `App.kt` becomes a state-based navigation shell over a sealed `Screen`. Coil gets an explicit singleton `ImageLoader` with the Ktor fetcher. No new dependency, no `Koin.kt` edit.

## Decisions (bound)

- **State**: `Loading`, `Content(photos: List<Photo>, selectedId: String? = null)`, `Empty`, `Error(canRetry: Boolean)`. `canRetry` is `true` for a failed `photos()`; the screen shows Retry only when it is set.
- **Events**: `Load`, `Retry`, `Refresh`, `Select(photoId)`.
- **Transitions** (T = `Result` of `photos()`):
  - `Load`: `photos(false)`. From `Content`: silent, no `Loading`. From anything else: `Loading` first.
  - `Retry`: `photos(false)`, always via `Loading`.
  - `Refresh`: `photos(true)`, silent from `Content`, otherwise via `Loading`.
  - Success: empty list gives `Empty`; otherwise `Content(list)`. Failure: from `Content` (silent Load/Refresh) the grid is KEPT, state unchanged, no `Error`; from any other state `Error(canRetry = true)`.
  - `Select(id)`: sets `selectedId` on `Content`; the tile with that id renders a 2dp primary-colour border. Load/Refresh preserve `selectedId` when that id is still in the new list, else null. Navigation is the screen's `onOpenPhoto(id)` callback fired right after `onEvent(Select)`.
- **Single in-flight load**: each new load cancels the previous (`loadJob?.cancel()`); a superseded job's late result never writes state.
- **Cancellation**: the VM never catches `Throwable`/`CancellationException`; it folds the `Result`. A cancelled `photos()` leaves state untouched, never `Error`.
- **Q3**: `PhotoRepository` has no observable flow, so the gallery reloads on entry: `GalleryScreen` runs `LaunchedEffect(Unit) { onEvent(Load) }`, and the Gallery branch leaves composition while Detail shows, so each return re-fires it. u1's cache already holds the generated photo, so `photos(false)` returns it; the silent rule avoids a flash. The ViewModel is `koinViewModel()` in the Activity/ViewController owner, so it survives navigation.
- **Navigation shell**: `sealed interface Screen { data object Gallery; data class Detail(val photoId: String) }` in `App` via `rememberSaveable` with a `Saver` over two pure functions, `Screen.encode()` (`"gallery"` / `"detail:<id>"`) and `decodeScreen(s)` (`s.split(":", limit = 2)`, so ids containing `:` round-trip; unknown input gives `Gallery`). `when (screen)` is exhaustive, no `else`. The Detail branch calls private `DetailPlaceholder(photoId, onBack)`, the named seam u3 replaces.
- **Back**: in-UI back button, no common `BackHandler`. Detail (u3) shows a text Back button setting `screen = Screen.Gallery`. Known cost: Android system back on Detail exits the app.
- **Insets**: `App` applies `Modifier.safeDrawingPadding()` to the content container so the top row and grid sit clear of system bars.
- **Coil on iOS (hazard: blank images)**: `shared/build.gradle.kts` already declares `coil-compose` and `coil-network-ktor3` (commonMain) and `ktor-client-darwin` (iosMain). New `ImageLoaderSetup.kt` has `@Composable fun SetUpImageLoader()` calling `setSingletonImageLoaderFactory { ctx -> ImageLoader.Builder(ctx).components { add(KtorNetworkFetcherFactory()) }.build() }`; `App` calls it first. `KtorNetworkFetcherFactory` is in `coil3.network.ktor3`, not `io.ktor`.
- **Icons**: none; text buttons and `CircularProgressIndicator`.
- **Answers**: Q1/Q2/Q4 are u1's. Q3 above. Q5: Android host tests plus iOS compile; iOS rendering is a manual follow-up.

## Acceptance criteria

`T` = `shared/build/test-results/testAndroidHostTest`. `G` = `shared/src/commonMain/kotlin/com/humbertouchiyama/phota`.

| # | Criterion | Check |
|---|---|---|
| AC1 | Success, empty, error(canRetry), retry-after-error, failed silent reload keeps grid | `./gradlew :shared:testAndroidHostTest --tests "com.humbertouchiyama.phota.feature.gallery.GalleryViewModelTest" --rerun --console=plain` |
| AC2 | Test count read from XML (§12): 9 tests, 0 failures | `grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"' T/TEST-com.humbertouchiyama.phota.feature.gallery.GalleryViewModelTest.xml` prints `tests="9" skipped="0" failures="0"` |
| AC3 | Thrown cancellation is not `Error`; a superseding load cancels an in-flight one and the late result does not overwrite state | tests `cancelledLoadIsNotError`, `supersededLoadIgnoresLateResult` in the AC1 run |
| AC4 | `Load` from `Content` emits no `Loading` and shows the NEW list (different list on 2nd load) | test `reloadFromContentKeepsGrid` in the AC1 run |
| AC5 | Saver round-trips `Gallery`, `Detail("a")`, `Detail("a:b")` | `./gradlew :shared:testAndroidHostTest --tests "com.humbertouchiyama.phota.ScreenSaverTest" --rerun --console=plain` |
| AC6 | No `io.ktor`/`java.`/`android.` imports in feature, App, ImageLoaderSetup, commonTest | `bash -c '! grep -rnE -e "import io\.ktor" -e "import java\." -e "import android\." G/feature G/App.kt G/ImageLoaderSetup.kt shared/src/commonTest'` (zero hits) |
| AC7 | New and edited files exist | `bash -c 'for f in G/ImageLoaderSetup.kt G/App.kt G/feature/gallery/GalleryModule.kt shared/src/commonTest/kotlin/com/humbertouchiyama/phota/ScreenSaverTest.kt; do [ -f "$f" ] \|\| exit 1; done'` and `git diff --name-only $(git merge-base adw/phota-feature-layer HEAD) -- G/App.kt G/feature/gallery/GalleryModule.kt` lists both |
| AC8 | Exhaustive `when`, keyed grid | `bash -c '! grep -n "else ->" G/feature/gallery/GalleryScreen.kt'` and `grep -c "key = { it.id }" G/feature/gallery/GalleryScreen.kt` prints 1 |
| AC9 | Ktor fetcher registered and called (iOS blank-image guard); insets handled | `grep -n "KtorNetworkFetcherFactory" G/ImageLoaderSetup.kt`, `grep -n "SetUpImageLoader()" G/App.kt`, `grep -n "safeDrawingPadding" G/App.kt` each print a line |
| AC10 | `Koin.kt` untouched vs the chain base; `galleryModule` binds the ViewModel | `git diff --quiet $(git merge-base adw/phota-feature-layer HEAD) -- G/di/Koin.kt` exits 0; `grep -n "GalleryViewModel" G/feature/gallery/GalleryModule.kt` prints a line |
| AC11 | iOS compiles; iOS simulator tests pass | `./gradlew :shared:compileKotlinIosSimulatorArm64 --console=plain`; `./gradlew :shared:iosSimulatorArm64Test --console=plain` |

## Implementation

`M/` = `shared/src/commonMain/kotlin/com/humbertouchiyama/phota/`, `T/` = `shared/src/commonTest/kotlin/com/humbertouchiyama/phota/`. Tests first (`superpowers:test-driven-development`).

| # | File | Exact change |
|---|---|---|
| 1 | `T/feature/gallery/GalleryViewModelTest.kt` (new) | In-file `FakePhotoRepository : PhotoRepository`: settable `photosResult`, `photosCalls`/`lastForceRefresh`, optional `CompletableDeferred` gate; other members stubbed. `setMain(UnconfinedTestDispatcher())` / `resetMain()`. Turbine tests (9): `success`, `emptyList`, `errorCanRetry`, `retryAfterError`, `cancelledLoadIsNotError` (fake throws `CancellationException`), `supersededLoadIgnoresLateResult` (Load blocks on gate, Refresh completes, then gate released with an old list; state stays the Refresh result), `reloadFromContentKeepsGrid` (2nd list differs, no `Loading`, new content), `failedReloadKeepsGrid`, `refreshUsesForceRefresh`. |
| 2 | `T/ScreenSaverTest.kt` (new) | Round-trip `decodeScreen(screen.encode())` for the three AC5 cases plus garbage input gives `Gallery`. |
| 3 | `M/feature/gallery/GalleryUiState.kt` (new) | `GalleryUiState` and `GalleryEvent` sealed interfaces per Decisions. |
| 4 | `M/feature/gallery/GalleryViewModel.kt` (new) | `class GalleryViewModel(repository: PhotoRepository) : ViewModel()`; private `MutableStateFlow(Loading)`, public `state`; exhaustive `onEvent`; private `load(forceRefresh, viaLoading)` in `viewModelScope` per Transitions. No `init` load. |
| 5 | `M/feature/gallery/GalleryScreen.kt` (new) | `GalleryScreen(onOpenPhoto, viewModel = koinViewModel())`; `collectAsStateWithLifecycle()`; entry `Load`; exhaustive `when`: `Loading` progress, `Empty` text, `Error` text plus Retry if `canRetry`, `Content` `LazyVerticalGrid(GridCells.Adaptive(120.dp))` `items(photos, key = { it.id })`, `AsyncImage`, selected border, a text Refresh button in the top row. |
| 6 | `M/feature/gallery/GalleryModule.kt` (edit) | `module { viewModel { GalleryViewModel(get()) } }` (`org.koin.core.module.dsl.viewModel`; fallback `viewModelOf`). |
| 7 | `M/ImageLoaderSetup.kt` (new) | `SetUpImageLoader()`; comment `// Without the Ktor fetcher Coil 3 renders blank images on iOS (u2 spec).` |
| 8 | `M/App.kt` (edit) | `Screen`, `encode`/`decodeScreen`, Saver; `SetUpImageLoader()`; `MaterialTheme { Surface { Box(Modifier.safeDrawingPadding()) { when (screen) ... } } }`; Gallery -> `GalleryScreen { screen = Screen.Detail(it) }`; Detail -> `DetailPlaceholder` with `// Seam: u3 replaces this with the real DetailScreen`. |
| 9 | gates (implementer runs, in order) | 1. AC1/AC2/AC3/AC4/AC5 test commands. 2. AC6-AC10 greps. 3. `./gradlew :shared:compileKotlinIosSimulatorArm64 --console=plain` then `./gradlew :shared:iosSimulatorArm64Test --console=plain`. 4. `./gradlew :androidApp:assembleDebug :androidApp:lintDebug --console=plain`. |

## Out of scope

Detail UI and `photo(id)` use (u3); generate/upload UI; `PhotoRepository` changes; Compose UI tests; MockK; catalog changes.
