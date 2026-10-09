# Repo profile — `humbertouchiyama/phota-kmp-template`

Every repo-specific value the shared ADW contract needs. Started from
`.claude/adw/cache/docs/repo-profile-EXAMPLE.md` (contract 63c944f); every value below is this
repo's own.

The format is prose and tables, not a parsed config. The reader is an agent, so precision matters
more than syntax.

**Section anchors are frozen** — the contract files cite `repo-profile §1`…`§18` by number. Do not
renumber. Add new sections at the end. §19 and §20 are absent (§19 → derived lane map; §20 → no
`shape: port` units).

| § | Section | Read by |
|---|---|---|
| §1 | Identity and layout | orientation |
| §2 | Paths | adw-core §1, adw-init Phase 4, adw-build §3.5, pr-ready §2/§5 |
| §3 | Branches | adw-core §2/§6, adw-build Phase 0 |
| §4 | Worktree provisioning | adw-build Phase 2, review-core §1.2, pr-ready §4.3 |
| §5 | Gates | adw-build §3.2, review-core §4, pr-ready §4.3 |
| §6 | Layer map — which gates a diff obligates | review-core §4, adw-build §3.2 |
| §7 | Gate files — the tripwire list | pr-ready §4.2 |
| §8 | Blast-radius paths | review-core §7 |
| §9 | CI coverage | pr-ready §"When to reach for this" |
| §10 | Environment faults | adw-build §3.3 |
| §11 | Trap domains | adw-core §3, adw-build §3.1 |
| §12 | Running a single test | adw-core §2, adw-build §3.2 |
| §13 | Review layers and their standards docs | code-review Phase 2 |
| §14 | Risk scoring | code-review Phase 3 |
| §15 | Review emphasis + Blocking severity classes | code-review Phase 4, review-core §9 |
| §16 | Project rule tables | code-review Phase 5.2, 5.3 |
| §17 | Convention doc routing | code-review Phase 6.2 |
| §18 | Disposable state | cleanup Phase 3, 5 |

---

## §1 — Identity and layout

Kotlin Multiplatform template for a small gen-AI photo app (image grid, detail, fake
generate/upload). Android + iOS (`iosArm64`, `iosSimulatorArm64`), Compose Multiplatform UI on both.
Gradle with a version catalog (`gradle/libs.versions.toml`). Layout follows the current KMP wizard /
`Kotlin/KMP-App-Template`.

| Root | What |
|---|---|
| `shared/` | The only KMP module: Compose UI, ViewModels, data, DI. Android target via `com.android.kotlin.multiplatform.library`; iOS static framework `Shared`. |
| `shared/src/commonMain/kotlin/com/humbertouchiyama/phota/` | `feature/gallery`, `feature/generate`, `core/network`, `core/data`, `di` |
| `shared/src/commonTest/` | tests; run on Android host (JVM) and iOS simulator |
| `androidApp/` | Android application module — `Application` + `Activity` only |
| `iosApp/` | Xcode project; SwiftUI shell hosting `MainViewController()`. Its build phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode` |

No `CLAUDE.md` yet. `README.md` holds the architecture and decisions.

## §2 — Paths

| Role | Value |
|---|---|
| **specs dir** | `docs/adw/specs/` |
| **plans dir** | `docs/adw/plans/` |
| **legacy specs dir** | none |
| **legacy plans dir** | none |
| **artifact exclusion pathspec** | `':!docs/adw/**'` |
| **ADW design doc** | `.claude/adw/cache/docs/00-design.md` — fetched with the contract; cited as `design §N` |
| **source roots** | `shared/` `androidApp/` `iosApp/` |
| **subproject roots** | `shared/` `androidApp/` `iosApp/` |
| **code extensions** | `.kt` `.kts` `.swift` `.xml` `.toml` `.pbxproj` `.xcconfig` `.plist` |
| **non-code paths** | `*.md` `docs/` `.claude/` |
| **standards docs** | `README.md` (`##`) — the only one so far |
| **comment rule** | match the surrounding code; one terse line, never a multi-line rationale comment |
| **learning docs** | none yet |

Gitignored build output a path check must skip: `**/build/`, `.gradle/`, `.kotlin/` — resolve with
`git check-ignore -q`.

## §3 — Branches

| Role | Value |
|---|---|
| **default base** | `main` |
| **production branch** | `main` |
| **long-lived integration branches** | none |
| **merge convention** | squash |

## §4 — Worktree provisioning — before any gate

```bash
git worktree add "$WT" <branch>
cp "$MAIN/local.properties" "$WT/local.properties" 2>/dev/null || true   # Android SDK path, untracked
```

Gradle needs no dependency provisioning: the catalog resolves into the shared `~/.gradle` cache.
Without `local.properties` and without `ANDROID_HOME`, every Gradle task fails at configuration with
`SDK location not found` — a provisioning fault, not the PR's defect.

### Conditional provisioning

- **Diff touches `gradle/libs.versions.toml` or a `build.gradle.kts`** → nothing extra; the first
  gate re-resolves. Expect minutes on a cold cache.
- **Diff touches `iosApp/`** → gate 6 needs Xcode on `PATH`. No CocoaPods, no SPM step.

## §5 — Gates, cheapest first

Run in the worktree. `<BASE>` is the caller's diff anchor. All Gradle gates take `--console=plain`.

| # | Command | Runs when | Notes |
|---|---|---|---|
| 1 | `( cd "$WT" && ./gradlew :shared:compileKotlinIosSimulatorArm64 --console=plain )` | Kotlin layer | Cheapest type check of `commonMain` + `iosMain`. Catches an `expect` with no iOS `actual`. |
| 2 | `( cd "$WT" && ./gradlew :shared:testAndroidHostTest --console=plain )` | Kotlin layer | `commonTest` on the JVM. Gate on the **test count** in `shared/build/test-results/testAndroidHostTest/`, not only the exit code — see §12. |
| 3 | `( cd "$WT" && ./gradlew :shared:iosSimulatorArm64Test --console=plain )` | Kotlin layer | `commonTest` on Kotlin/Native. Finds Native-only defects (dispatchers, memory model). macOS only. |
| 4 | `( cd "$WT" && ./gradlew :androidApp:assembleDebug :androidApp:lintDebug --console=plain )` | Kotlin/Android layer | Full Android build + Lint. Lint fails only on severity `Error`; read `androidApp/build/reports/lint-results-debug.html` for new warnings. |
| 5 | `( cd "$WT" && ./gradlew build --console=plain )` | build files, catalog, or before a PR is marked ready | Everything: both apps, Lint, both test targets, release iOS frameworks. Slow (~8 min cold). |
| 6 | `( cd "$WT" && xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'generic/platform=iOS Simulator' -configuration Debug ARCHS=arm64 CODE_SIGNING_ALLOWED=NO -derivedDataPath build/xcode build )` | `iosApp/` diff, or `shared/src/iosMain/` | **`ARCHS=arm64` is mandatory**: the generic simulator destination also asks for `x86_64`, which has no Gradle target, and fails with `Unknown iOS simulator arch: 'x86_64'`. Errors are lines matching `error:`. |

**No audit gate.** This repo has no `scripts/audit.sh`, detekt or ktlint yet. Say so; do not
invent one.

**Gate commands run as subshells** against the worktree path — never a bare persistent `cd`.

### The `verify:` command allowlist

A spec's `verify:` first token must be one of: `cd` `./gradlew` `bash` `grep` `git` `xcodebuild`.
Any other tool wraps in `bash -c '…'`.

## §6 — Layer map — which gates a diff obligates

Computed from `git diff --name-only <BASE>...HEAD`, never judged.

| Diff contains | Gates |
|---|---|
| `shared/src/commonMain/` or `shared/src/commonTest/` | §5 gates 1–4 |
| `shared/src/iosMain/` | §5 gates 1, 3, 6 |
| `shared/src/androidMain/` or `androidApp/` | §5 gates 2, 4 |
| `iosApp/` | §5 gate 6 |
| `gradle/`, any `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` | §5 gate 5 (and 6 if the iOS framework config changed) |
| `.github/workflows/` | static parse per changed file: `ruby -ryaml -e 'puts YAML.load_file(ARGV[0])["jobs"].keys.sort' <file>` — must exit 0 and list the jobs. Never `N/A — docs-only`. |
| docs / `.claude` only, and no `.github/workflows/` | skip all code gates; say `verification: N/A — docs-only, no code gates apply` |

**Device smoke (UI changes only):** run the app on an emulator or simulator if you can. A declared
skip is the norm in an agent run; it routes visual QA to the human. An undeclared skip is a stop.

## §7 — Gate files — the tripwire list

Every file a §5 gate command reads as configuration. A PR that edits one cannot earn a green under
them (pr-ready §4.2).

```
gradle/libs.versions.toml  gradle/wrapper/gradle-wrapper.properties  gradle.properties
settings.gradle.kts  build.gradle.kts  shared/build.gradle.kts  androidApp/build.gradle.kts
iosApp/iosApp.xcodeproj/project.pbxproj  iosApp/Configuration/Config.xcconfig
gradlew  gradlew.bat
```

Never enter a directory — name the files. Test files are a gate's subject, not its harness.

## §8 — Blast-radius paths

When applied remediation touched any of these, fall back to comment-only — do not push or merge.

- Every §7 file — build and toolchain.
- `shared/src/commonMain/kotlin/com/humbertouchiyama/phota/di/` — wiring every screen depends on.
- `.claude/commands/*.md`, `.claude/*.md` — every future agent run (covers this file).
- `.github/workflows/**` — no gate reads these files.
- `README.md` — the standards doc (§2) and the take-home write-up.

## §9 — CI coverage

`.github/workflows/ci.yml`. **If this section stops matching that file, the file wins.**

Runs on every push and every pull request, no labels, no path filters.

| | in `ci.yml`? |
|---|---|
| `android` job (ubuntu): `:androidApp:assembleDebug :androidApp:lintDebug :shared:testAndroidHostTest` | yes — §5 gates 2 and 4 |
| `ios` job (macos): `:shared:iosSimulatorArm64Test` | yes — §5 gate 3 (implies gate 1) |
| `./gradlew build` (gate 5) | no — CI runs the parts, not the release frameworks |
| `xcodebuild` (gate 6) | no |
| review posted after the last code commit | no |

## §10 — Environment faults — abort, never consume a cycle

- `SDK location not found` → §4's `local.properties` step did not run.
- `Gradle build daemon disappeared unexpectedly` → memory pressure during Kotlin/Native linking
  (seen 2026-10-09 on a cold first build). Re-run once; do not start a fix cycle.
- `Unknown iOS simulator arch: 'x86_64'` from `xcodebuild` → gate 6 ran without `ARCHS=arm64`.
- First Kotlin/Native run downloads LLVM into `~/.konan` — minutes of silence, not a hang.
- Two Gradle gates in the same worktree serialize on the daemon lock; run gates sequentially per
  worktree.

## §11 — Trap domains

| Domain | Match |
|---|---|
| build config | path is any §7 file |
| iOS interop | path prefix `shared/src/iosMain/` or `iosApp/` |
| concurrency/cancellation | `GlobalScope\|runBlocking\|Dispatchers\.Main\|viewModelScope\|SupervisorJob\|CancellationException` |
| state | `MutableStateFlow\|stateIn\|SharingStarted\|UiState` |
| DI | `startKoin\|module \{\|single<\|factory<\|viewModelOf` |
| network | `HttpClient\|ContentNegotiation\|install\(` |

Deliberately coarse proxies — a false hit costs a bounce to D2, the cheap direction.

## §12 — Running a single test

```bash
./gradlew :shared:testAndroidHostTest --tests 'com.humbertouchiyama.phota.SmokeTest' --console=plain
./gradlew :shared:iosSimulatorArm64Test --tests 'com.humbertouchiyama.phota.SmokeTest' --console=plain
```

A `--tests` filter matching nothing **fails** with `No tests found for given includes` — good. But a
task reported `UP-TO-DATE` ran nothing; read the XML under `shared/build/test-results/<task>/` for
the count, or add `--rerun`.

A verify that must prove a unit's added files survived needs an existence guard first:

```bash
for f in <paths>; do [ -f "$f" ] || { echo "VERIFY missing $f"; exit 1; }; done && <gates>
```

## §13 — Review layers and their standards docs

| Pattern | Layer | Ref |
|---|---|---|
| `shared/src/commonMain/**/feature/` | Feature UI + ViewModel | `README.md` |
| `shared/src/commonMain/**/core/` | Data / network | `README.md` |
| `shared/src/commonMain/**/di/` | DI wiring | `README.md` |
| `shared/src/iosMain/`, `iosApp/` | iOS interop | `README.md` |
| `shared/src/androidMain/`, `androidApp/` | Android entry | `README.md` |
| `shared/src/commonTest/` | Tests | `README.md` |
| §7 files | Build config | no standards doc — the file is the contract |
| `.github/workflows/` | CI | no standards doc — the workflow file is the contract |

**Ignore:** `*.png`, `res/mipmap-*`, `Assets.xcassets/`, `.md` docs.

## §14 — Risk scoring

| Signal | Pts | Detection |
|---|---|---|
| §7 build file touched | +4 | path match |
| `.github/workflows/` touched | +5 | no other gate reads these files |
| `iosMain/` or `iosApp/` touched | +3 | interop; Swift sees the ObjC header |
| `di/` touched | +3 | every screen depends on it |
| `.claude/commands/` or a root `.claude/*.md` | +3 | every future agent run |
| New feature (>3 added files) | +2 | `git diff --diff-filter=A --name-only` |
| >500 lines changed | +2 | `--stat` |
| >10 files changed | +1 | `--name-only` count |

## §15 — Review emphasis

**Stack context:** KMP (Kotlin 2.4, AGP 9, Compose Multiplatform 1.12), one `shared` module,
androidx lifecycle ViewModel (multiplatform), Ktor + kotlinx.serialization, Koin, Coil 3. Tests:
kotlin.test + Turbine + kotlinx-coroutines-test with hand-written fakes.

**Weight above style polish:** every screen state has a UI (Loading / Content / Empty / Error) ·
ViewModel exposes `StateFlow`, takes events through one entry point · ViewModel never touches Ktor
· cancellation handled (no swallowed `CancellationException`) · no MockK or JVM-only libraries in
`commonMain`/`commonTest` · Swift-visible API stays ObjC-header-friendly.

### Blocking severity classes

- a JVM-only dependency or API in `commonMain` / `commonTest` (breaks iOS)
- swallowed `CancellationException` (`catch (e: Exception)` around a suspend call without rethrow)
- `GlobalScope` or `runBlocking` in production code
- a screen state with no UI
- secret/credential leak

## §16 — Project rule tables

### 16.1 — Kotlin (`shared/`)

| # | Anti-pattern | How to detect | Severity |
|---|---|---|---|
| 1 | MockK / Mockito in tests | grep `io\.mockk\|org\.mockito` | Blocking |
| 2 | `GlobalScope` / `runBlocking` in `src/*Main` | grep | Blocking |
| 3 | `catch (e: Exception)` / `catch (e: Throwable)` in suspend code without rethrowing `CancellationException` | grep + read | Blocking |
| 4 | `MutableStateFlow` exposed publicly | grep `val \w+ = MutableStateFlow` without `private` | Warning |
| 5 | Ktor types imported under `feature/` | grep `import io\.ktor` in `feature/**` | Warning |
| 6 | `java.` / `android.` import in `commonMain` | grep `^import (java\|android)\.` | Blocking |

### 16.2 — Structural checks

- **New screen:** has a `UiState` with Loading / Content / Empty / Error, each rendered.
- **New ViewModel:** has a `commonTest` with a fake repository, covering success and error.
- **New Koin binding:** registered in the right module and reachable from `initKoin`.

## §17 — Convention doc routing

| The finding's surface | Target doc |
|---|---|
| anything | `README.md` (Decisions and trade-offs) until a `CLAUDE.md` exists |

## §18 — Disposable state

### 18.1 — Inside the worktree — reclaimed for free

`build/`, `*/build/`, `.gradle/`, `.kotlin/`, `build/xcode/` (gate 6's derived data) are all inside
the worktree and gitignored. `git worktree remove` reclaims them.

### 18.2 — Gradle daemons

| | |
|---|---|
| **Discovery** | `./gradlew --status` |
| **Teardown** | `./gradlew --stop` — stops every daemon of this Gradle version, safe to run any time |

No servers, databases or containers.
