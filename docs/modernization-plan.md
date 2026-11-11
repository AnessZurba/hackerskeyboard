# Hacker's Keyboard 2 AZ — modernization plan

Plan only; no files in the repo were changed. All paths are under `/home/user/hackerskeyboard`; Java sources are in `app/src/main/java/kr/jcb1ee/hackerskeyboard2/` (abbreviated `…/` below). Line numbers are from the tree as read at the start of this pass (then HEAD `306c42f`). The repo was committed to by someone else while I was reading (HEAD is now `56630d9`, a loupe/toolbar change), so line numbers may be off by a few lines in `CursorLoupe.java`, `KeyboardToolbar.java` and nearby.

Claims marked **[verified]** were checked by grep/reading in this pass. Nothing was built or run.

## 0. Summary

- The app is ~21k lines of Java in one flat package: a 2010 AOSP LatinIME core (text-prediction keyboard) carrying a 2024–26 layer of well-isolated new features (toolbar, trackpad, clipboard, adaptive targets).
- The new code is mostly decent and small (`CursorTrackpad` 70 lines, `DeleteSwipeAccel` 60, `CursorDrag` 419, `ClipboardHistory` 439). The problem is where it plugs in: everything goes through `LatinIME.sInstance` and the three old god classes.
- The single biggest lever is that **the whole suggestion/dictionary stack serves a feature that cannot currently produce output** (no main dictionary; strip hidden when there is none). It accounts for roughly 4,500 lines of Java, 2,000 lines of C++, the NDK build, four `AsyncTask` users, two SQLite databases and two permissions. Deciding its fate first makes every later step cheaper.
- Recommended order: safety net and build hygiene → delete verified-dead code → decide suggestions → extract seams out of `LatinIME` → untangle touch handling → settings → toolbar split. No rewrite, no Kotlin conversion as a goal in itself.

---

## 1. Badly written main components (worst first)

### 1.1 `LatinIME.java` — 4,421 lines, about 250 methods, about 120 mutable fields
The service is simultaneously:
- IME lifecycle (`onCreate` 363, `onStartInputView` 805–963, `onUpdateSelection`, insets, fullscreen);
- settings store: about 45 `m*` preference mirrors (lines 180–240), loaded in three places that must agree — `onCreate` (377–411), `loadSettings()` (4231), `onSharedPreferenceChanged()` (3762–3882, a 120-line `if/else if` chain);
- modifier/terminal key encoder (`sendSpecialKey` 1793, `sendModifiableKeyChar` 1946, `sendTab`, `sendEscape`, Ctrl/Alt/Meta chording 1653–1790, `ESC_SEQUENCES`/`CTRL_SEQUENCES` statics at 95–96);
- composing/prediction engine (`handleCharacter` 3094, `handleSeparator` 3145, `handleBackspace` 2286, `pickSuggestion*`, `revertLastWord`, re-correction 3553–3620, auto-space/punctuation swapping 1447–1527);
- Hangul composition glue (1547–1594) and compose/dead-key sequences;
- new-feature host: autofill chips (2396–2520), cursor/selection API for the toolbar (2521–2880), image commit (2607–2672), delete-swipe state machine (2880–2978);
- feedback (sound/vibrate 4084–4190), notification (475–556), options dialog, swipe/volume action dispatcher (`doSwipeAction` 3885), language switching (`toggleLanguage` 3736).

Why this is the worst: every feature added since the fork had to add fields and package-private methods here, and tests have to boot the whole service (`Robolectric.buildService(LatinIME.class)`) and poke private fields by reflection (`SelectionPanelTest.java:546, 666` reads `mDeleteSwipeField`).

Specific smells:
- `toggleLanguage(true, true)` is used as "rebuild everything" for height changes, mode changes and pref changes (3857, 3930, 3938, `onStartInputView` 827). It also re-runs `initSuggest`, reopening three dictionaries.
- `initSuggest()` (609–657) and `KeyboardSwitcher` (347, 366) temporarily **mutate the process-wide `Resources` configuration locale** with the deprecated `updateConfiguration` to load localized XML, then restore it. Same in `InputLanguageSelection` 241/261. Fragile and not re-entrant.
- `mHandler` (345) is a non-static inner `Handler` created with the deprecated no-arg constructor; `onStartInput` creates another ad-hoc `Handler` (792).
- Statics used as cross-component mailboxes: `sPendingImage*` (2607–2609) written by `ImagePickActivity`, read by the service.

### 1.2 Static singletons and global mutable state
**[verified]** by grep:
- `LatinIME.sInstance` (228) — read from `PointerTracker` (14 references), `Keyboard`, `LatinKeyboardBaseView`, `KeyboardToolbar`, `ComposeSequence`, `CandidateView`, `LanguageSwitcher`, `LatinIMESettings` and even a preference widget (`VibratePreference.java:13`).
- `LatinIME.sKeyboardSettings` (227, `GlobalKeyboardSettings`) — a public bag of mutable public fields that is partly preferences, partly runtime state (`useExtension`, `keyboardMode`, `inputLocale`) and partly a debug mailbox (`editorPackageName` etc., written on every `onStartInputView` at 812–815 just so the settings screen can display it).
- `KeyboardSwitcher.sInstance` (`KeyboardSwitcher.java:149`), initialised by a static `init(ims)`; holds the service, the input view and the toolbar.
- `AdaptiveKeyTargets.sInstance` (108), `ClipboardHistory.sInstance` (97), `PointerTracker.sSlideKeyHack`/`sSlideKeys` (104–105), `LatinKeyboardBaseView.sSetRenderMode`/`sPrevRenderMode` (315–316), `LatinKeyboard.sSpacebarVerticalCorrection`, all of `TextEntryState` (static state machine), `PluginManager.mPluginDicts` (static map with an instance-style name), `AutoDictionary.sOpenHelper`, `UserBigramDictionary.sOpenHelper`/`sUpdatingDB`.

Consequences: views cannot be instantiated without a live service (the tests work around this), the layering is circular (model `Keyboard` → service → model), and state leaks between Robolectric tests.

### 1.3 Touch pipeline: `LatinKeyboardBaseView` (1,922) + `LatinKeyboardView` (664) + `PointerTracker` (834)
- `LatinKeyboardBaseView` is view, renderer (`onDraw`/buffer drawing from 916, with commented-out bitmap buffer code at 948–955, 1885), popup/mini-keyboard manager, long-press/repeat timer (`UIHandler` 328), multi-touch arbiter and gesture recogniser in one class.
- `onTouchEvent` (1665–1830) stacks **three** swipe recognisers ahead of key handling: the new vertical-stroke detector (`mSwipeInProgress`, 1688–1714), the 2010 `GestureDetector.onFling` with `SwipeTracker` (629–674, 1717), and `LatinKeyboardView` adds its own on top (sudden-jump filter, extension-row drag, space-bar language drag at 340–410).
- `PointerTracker` now also owns the space-bar trackpad (`startCursorSlide` 537, `moveCursor` 589) and backspace swipe (624–640), calling `LatinIME.sInstance` directly and reaching into the toolbar for the gesture label (`mKeyboardSwitcher.getInputContainer()` 543). A per-pointer state machine has become a second feature host.
- `LatinKeyboard.isInside()` (639–700) mixes hit-testing with the space-bar language-drag state machine and the "preferred letters" hack from the old prediction engine; `AdaptiveKeyTargets` now adjusts hit areas too, so there are two hit-area biasing mechanisms.

### 1.4 `KeyboardToolbar.java` — 1,764 lines, a new god class
One `LinearLayout` subclass contains the toolbar, the selection panel (443–680), the clipboard panel and its adapter (794–1060), the emoji panel, grid adapter, skin tones, recents and search with a fake `InputConnection` (1064–1660), autofill chip strip (274–365), key-repeat and backspace-swipe handling (683–790), colour derivation (1672–1725) and gesture label. All UI is built in code with hand-rolled `dp()` helpers. It duplicates trackpad wiring that also lives in `PointerTracker` (`KeyboardToolbar.java:519–580` vs `PointerTracker.java:589–612`) and backspace-swipe wiring (`KeyboardToolbar.java:719–770` vs `PointerTracker.java:405–521`).

### 1.5 Threading
- `android.os.AsyncTask` (deprecated API 30) in `ExpandableDictionary` 495, `AutoDictionary` 222, `UserBigramDictionary` 302, plus `LatinIMEUtil.cancelTask`.
- Raw threads: `UserDictionary.java:102` (`new Thread("addWord")`), `AdaptiveKeyTargets.java:519`.
- A private single-thread executor in `ClipboardHistory.java:78`.
- Three unrelated models (AsyncTask, raw threads, executor) with coarse `synchronized` methods and static "updating" flags; no shared IO executor, no lifecycle cancellation.
- `InputConnection` round trips (`getExtractedText`, `getTextBeforeCursor`) run synchronously on the main thread inside touch handling (`moveCursor` 2521, `startDeleteSwipe` 2894, `CursorDrag`). Unavoidable per call, but they are scattered and unbudgeted; they should sit behind one editor facade.

### 1.6 Settings model
- Same preference read in several places with separately spelled defaults: 135 `get*()` calls across the six main classes **[verified count]**. Example: `PREF_QUICK_FIXES` defaults to a resource bool in `initSuggest` (622) but to literal `true` in `loadSettings` (4244).
- Two `PreferenceManager`s in use: deprecated `android.preference.PreferenceManager` in `LatinIME`, `KeyboardSwitcher`, `LanguageSwitcher`, `KeyboardToolbar`; `androidx.preference.PreferenceManager` in `InputLanguageSelection`.
- Integers stored as strings (`getPrefInt`, `SeekBarPreferenceString`), versioned keys (`pref_label_scale_v2`), and four near-identical settings activities (`LatinIMESettings`, `PrefScreenActions`, `PrefScreenView`, `PrefScreenFeedback`, each with its own `SettingsFragment` and manifest entry).

### 1.7 Smaller but real
- `Keyboard.java` (1,385) is a fork of the framework `Keyboard` whose XML parser reads `LatinIME.sKeyboardSettings`/`sInstance` (10 references) — the model depends on the service.
- `ComposeSequence.java` (1,136) is a compose table as Java code: fine functionally, but it is data, not logic.
- Mixed tabs/spaces and three licence-header generations; no formatter.

---

## 2. Outdated tooling

| Area | Now (evidence) | Issue |
|---|---|---|
| AGP | `8.3.2` (`build.gradle`) | Early-2024; needed newer for compileSdk 35/36. Uses legacy `buildscript {}`/`apply plugin`, `allprojects {}`, `task clean(type: Delete) { delete rootProject.buildDir }` (deprecated `buildDir`), `lintOptions {}` (deprecated; `lint {}`), `compileSdkVersion`/`minSdkVersion` old DSL. |
| Gradle | wrapper `8.10.2` | Acceptable; bump together with AGP. No version catalog, no dependency locking/verification. |
| SDK levels | compile 34 / target 34 / min 21 (`app/build.gradle`) | Play has required target 35 since Aug 2025 for updates; README still says "Targets Android 5–14". Target 35 brings forced edge-to-edge for the activities (`Main`, settings) and 16 KB page size (already handled in `app/CMakeLists.txt`). Current AndroidX releases need minSdk 23, so min 21 pins old libraries. |
| Java | no `compileOptions` → source/target 8 | Move to 17 (toolchain already resolvable via foojay plugin in `settings.gradle`). |
| Dependencies | appcompat 1.6.1, core 1.12.0, material 1.11.0, recyclerview **1.1.0**, robolectric 4.13 | All stale. `android.enableJetifier=true` in `gradle.properties` with no support-library dependency — only `app/libs/voiceimeutils.jar` (2011) could need it, and that jar is unused (§4). The `configurations.all` Kotlin-stdlib pin in `app/build.gradle` is a workaround that an upgrade should remove. `implementation fileTree(libs)` pulls in that jar blindly. |
| Native | `app/CMakeLists.txt` (`cmake_minimum_required 3.4.1`), `app/src/main/cpp/*` ~2,000 lines, `libjni_pckeyboard.so` for 4 ABIs; empty `externalNativeBuild.cmake.cppFlags`; no `ndkVersion` pinned | 2010 dictionary lookup JNI (`BinaryDictionary.java:64,116–124`). Built and shipped for a 34-byte placeholder dictionary. Unpinned NDK = non-reproducible builds. |
| Shrinking | `minifyEnabled false`, `proguard-android.txt` (non-optimizing) | No R8, no resource shrinking; ~45 unreferenced drawable names ship (§4). |
| Lint | `checkReleaseBuilds false`; no baseline, no lint task anywhere | Deprecation and unused-resource findings are invisible. |
| CI | only `.github/workflows/release.yml`, triggered by `v*` tags; runs `assembleRelease` only | **No CI on push/PR; the 66 Robolectric tests never run in CI.** Hardcodes `build-tools/34.0.0` for `apksigner`. |
| Signing | `signingConfigs.release` falls back to `release-hackerskeyboard2.jks` with empty passwords when env vars are absent | A local `assembleRelease` fails late and confusingly; make the release signing config conditional. Keystore name still that of the upstream fork. |
| Build script side effects | `generateVersionResource` writes `src/main/res/values/auto-version.xml` into the source tree on every build | Use `resValue`/`BuildConfig` instead (the script already uses `resValue` for `app_package`). |
| Manifest | `android:hardwareAccelerated="false"` application-wide, plus a "render mode" pref set via reflection (`LatinKeyboardBaseView.java:489–510`) | 2011-era workaround; activities re-enable it individually. |
| Backup | `LatinIMEBackupAgent` (`BackupAgentHelper`, key/value backup) + `allowBackup="true"`, no `dataExtractionRules` | Legacy; on Android 12+ auto-backup rules should say explicitly that clipboard history, clip images, learned words stay out. |
| Repo hygiene | `.gitignore` lists `app/src/androidTest/` but two files there are tracked; `metadata/kr.jcb1ee.hackerskeyboard2.yml` has `<owner>` placeholders and the upstream app id; `java/*.pl|sh` (2011 Perl helpers), `dictionaries/sample.xml`, `README-hackerskeyboard.txt` are leftovers; `.imgbot` | Confusing for contributors; README does not describe this fork's features. |

Deprecated platform APIs in use **[verified by grep]**:
- `android.preference.PreferenceManager`/`PreferenceActivity` imports (`LatinIME.java:47–48`, `KeyboardSwitcher.java:22`, `LanguageSwitcher.java:23`).
- `AsyncTask` (four files, §1.5).
- `android.text.AutoText` (`Suggest.java:26,334`, `LatinIMESettings.java:30,138`) — a hidden/removed-from-SDK framework class from Android 1.x.
- `Resources.updateConfiguration` and `Configuration.locale` (`LatinIME.java:612–656`, `KeyboardSwitcher.java:347,366`, `InputLanguageSelection.java:241,261`).
- `android.app.AlertDialog`, `Vibrator.vibrate(long)` style, `Handler()` without a Looper (`LatinIME.java:345`, `LatinKeyboardView.java:553`).
- Reflection for APIs that exist at minSdk 21: `View.setLayerType` (`LatinKeyboardBaseView.java:491`), `getSelectedText`/`setComposingRegion` (`EditingUtil.java:38–40`), `SharedPreferences.Editor.apply` (`SharedPreferencesCompat.java`).

---

## 3. Contradicting / overlapping features

1. **Three swipe recognisers on the key area** (§1.3): new vertical-stroke detector, old fling detector with `SwipeTracker`/`config_swipeDisambiguation`, and `LatinKeyboardView`'s above-the-keyboard drag (`swipeUp()` at 391 before opening the dead extension row). All end in the same `doSwipeAction`. `SwipeGestureTest` (9 tests) covers the new behaviour — a good base for collapsing to one recogniser.
2. **Configurable swipe actions vs the toolbar.** `doSwipeAction` still offers `suggestions`, `extension` (silently aliased to the toolbar, `LatinIME.java:3917`), `height_up/down`, `full_mode`, `lang_prev/next`. Horizontal swipes now compete with the space-bar trackpad and backspace swipe; "suggestions" toggles a strip that has no content.
3. **Space bar has two drag meanings.** Slide = language switch (`LatinKeyboard.isInside` 655–670, `getLanguageChangeDirection` 586), hold-then-slide = trackpad (`PointerTracker.startCursorSlide`). They are arbitrated by a `mCursorSliding` flag pushed into the model (`LatinKeyboard.setCursorSliding` 578) and a slop check. Works, but the model class carries gesture state for two features.
4. **Candidate strip vs toolbar vs autofill chips.** Three things want the band above the keys: the framework candidates view (`onCreateCandidatesView` 749, `CandidateView` 492 lines, with a workaround at 723 for "already has a parent"), the toolbar inside the input view (`KeyboardSwitcher.getInputContainer()`), and inline autofill chips rendered into the toolbar. `isCandidateStripVisible()` (3278) hides the strip whenever there is no main dictionary and no app completions, i.e. almost always. `onComputeInsets` has to account for both.
5. **Cursor movement has at least five paths:**
   - keyboard arrow keys → `sendSpecialKey`/`sendModifiedKeyDownUp` (key events);
   - toolbar arrows → `LatinIME.moveCursor`/`moveCursorVertically` (`setSelection` on extracted text; `KeyboardToolbar.java:630–652`), falling back to key events via `CursorDrag.sendKey` (677);
   - space-bar and toolbar trackpads → `CursorDrag` (arrow keys with in-flight tracking and its own text snapshot);
   - backspace swipe → its own text snapshot and `setSelection` (`LatinIME.java:2894–2978`);
   - `onExtractedCursorMovement` (1148).
   Selection memory is split between `LatinIME.mSetAnchor/mSetFocus` (2540) and `CursorDrag.mAnchor/mFocus`. This is the area with the most recent bug-fix commits ("keep the selection where Shift+arrow drops it", "fix up/down with Select on", "no jumps past the first/last line") — a sign the logic wants one owner.
6. **Two hit-area biasing mechanisms:** the old `mPrefLetterFrequencies`/`setPreferredLetters` path fed by `Suggest` (`LatinKeyboard.java:684`, cleared in `updateSuggestions` 3331) and the new `AdaptiveKeyTargets`. Without a dictionary the old one never has data.
7. **Two "learned words" stores:** `AutoDictionary` + `UserBigramDictionary` (SQLite, old) and `AdaptiveKeyTargets` user words file (new, `AdaptiveKeyTargets.java:622–665`). Different privacy rules (the new one caps counts per commit `dc5095b`).
8. **Voice input:** the current path is `VoiceInputActivity` + broadcast back to the service (live, `LatinIME.java:659, 2164, 4071`). Around it sit remnants of the two previous implementations (§4). `hasVoice` is also threaded through every `KeyboardId` in `KeyboardSwitcher` (about 40 references), so the mic key multiplies cached keyboard variants.
9. **Hebrew `he`/`iw`:** commit `306c42f` dedupes the language list and maps saved `he` → `iw` (`LanguageSwitcher.java:94–102`), but the duplicate resource directories remain: `res/values-he` + `res/values-iw` and `res/xml-he` + `res/xml-iw`, **byte-identical [verified with `diff -rq`]**. `AdaptiveKeyTargets.wordListFor` also handles both.
10. **Settings surfaces:** four settings activities + `InputLanguageSelection` + `Main` (which is also a settings/test screen) + the long-press options dialog (`showOptionsMenu` 4296) + a persistent notification with `NotificationReceiver` (a pre-Android-4 way to open a keyboard that apps hide). `settings_key` pref and `KEYBOARDMODE_*_WITH_SETTINGS_KEY` double every alphabet mode id (`KeyboardSwitcher.java:52–67`).
11. **Themes:** eight layouts in `KeyboardSwitcher.THEMES` (70–79): Gingerbread, Stone, ICS, ICS darker, Trans/Neon, three Material. The toolbar derives its colours by sampling the keyboard background (`KeyboardToolbar.applyColours` 1672) because themes are 9-patch/layout based rather than colour tokens.
12. **Connectbot handling vs generic terminal detection:** package-name list (`isConnectbot` 1633, four ancient package names) alongside the new `isTerminalField()` (2496, input type `TYPE_NULL`) used for chips and backspace swipe.

---

## 4. Dead code islands

Verified = no reference found outside the item itself (Java, res, manifest) or guarded by a constant that is never true.

**Dead Java (verified)**
- Extension-row keyboard: `KeyboardSwitcher.java:353–357` has the only `setExtension` calls commented out and `LatinIME.java:869` forces `useExtension = false`, so `LatinKeyboard.getExtension()` is always null. Dead: `LatinKeyboardView` 368–410, 429–490 (`openExtension`, `closeExtension`, `mExtensionPopup`, `setExtensionLayoutResId`, `mIsExtensionType`), `LatinKeyboard.setExtension/getExtension`, `Keyboard.mUseExtension`, `KeyboardId.mUsingExtension`, and `res/xml/kbd_extension.xml`, `kbd_extension_full.xml` (referenced only from the comments).
- `ContactsDictionary.java` (152 lines): only instantiations are commented out (`LatinIME.java:631–647, 674`); `Suggest.setContactsDictionary` is never called, so every `mContactsDictionary != null` branch in `Suggest` is dead.
- `LatinKeyboardView.java:533–664`: `DEBUG_AUTO_PLAY`/`DEBUG_LINE` are `static final false`; the whole auto-play handler and its fields are unreachable.
- `LatinIME.measureCps()` (4379): no callers. `MSG_START_TUTORIAL`, `MSG_VOICE_RESULTS` constants: unused. `mWordToSuggestions` (288): only ever cleared. `mBigramSuggestionEnabled` is `final false`.
- `TextEntryState` file logging: `LOGGING` is never set true (`TextEntryState.java:34`), so `key.txt`/`action.txt` writers and the session counters are dead. The state enum itself is live.
- `handleSuddenJump` (`LatinKeyboardView.java:257–335`): only runs when `!hasDistinctMultitouch()` (346); same for the "non-distinct multitouch" branches in `LatinKeyboardBaseView` (1674, 1790–1810). Reachable only on hardware without `FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT`, which no API 21+ phone lacks in practice. Effectively dead; the in-code comment at 345 says as much.
- Reflection shims whose fallback branch can never run at minSdk 21: `sSetRenderMode` null-check (`PrefScreenView.java:86`), `EditingUtil` method cache, `SharedPreferencesCompat`, `DeadAccentSequence.java:76` (`SDK_INT >= 9`).
- `LatinIMEUtil.GCUtils` (OOM retry loop around keyboard loading) — live but a 2010 workaround; `RingCharBuffer` is written at `LatinIME.java:2236` and feeds only the old correction logic.

**Dead libraries / resources (verified)**
- `app/libs/voiceimeutils.jar` (2011 `com.google.android.voiceime`): zero references in Java or XML, yet packaged via `fileTree`.
- Layouts with no reference: `bubble_text`, `input_stone_normal`, `recognition_status`, `voice_punctuation_hint`, `voice_swipe_hint`.
- `res/xml/prefs_for_debug.xml`: no reference.
- About 45 drawable names with no `@drawable/`/`R.drawable.` reference and no `getIdentifier` lookup for drawables (the only `getIdentifier` calls are for dictionaries): all `btn_keyboard_key_*_material_{black,dark,light}` and `btn_keyboard_spacebar_*_material_*` (25), `speak_now_level0–6`, `ic_mic_dialog`, `ic_subtype_mic`, `ic_subtype_keyboard`, `voice_swipe_hint`, `working`, `caution`, `cancel`, `dialog_bubble_step02/07`, `ic_suggest_scroll_background`, `sym_keyboard_done`, `sym_keyboard_feedback_done`, `sym_keyboard_shift_lock`, `btn_keyboard_key_popup_selected_holo`. Confirm with `lint --check UnusedResources` before deleting (strings were not audited in this pass and will have many more).
- Duplicate `values-he`/`xml-he` (§3.9) — dead once the `he`→`iw` mapping is the only path. **Check first** that Android resource resolution still maps a `he` locale to `-iw` on all supported API levels (it does via the legacy-code aliasing, but the app sets the locale by hand through `updateConfiguration`).

**Alive but serving a feature that produces nothing today (the "zombie" island)**
With `res/raw/main.dict` at 34 bytes, `Suggest.hasMainDictionary()` is false unless an external dictionary pack is installed, and then `isCandidateStripVisible()` is false. Still built, loaded and run on every keystroke/session:
- `Suggest` (538), `BinaryDictionary` (287) + `app/src/main/cpp` (~2,000 lines) + CMake/NDK, `ExpandableDictionary` (691), `UserDictionary` (145), `AutoDictionary` (259), `UserBigramDictionary` (401), `Dictionary`, `WordComposer` (partly), `CandidateView` (492), `PluginManager` (291), `EditingUtil` re-correction helpers, `TextEntryState`, and roughly 700 lines of `LatinIME` (3240–3720).
- Permissions `READ_USER_DICTIONARY`/`WRITE_USER_DICTIONARY` (ignored for non-default IMEs/spell checkers since API 23 anyway) and the `<queries>` block for dictionary packs.
- Not verified: whether any still-published dictionary pack works with this fork's package/intent names. `PluginManager` looks for `org.pocketworkstation.DICT` and AnySoftKeyboard packs; the latter still exist, so this path may work for users who install one. That is the one thing to check on a device before choosing option A in §6 Phase 2.

**Not dead (do not delete on sight):** `VoiceInputActivity` and `KEYCODE_VOICE`; `SwipeTracker` (used by the fling detector); `MiniKeyboardKeyDetector`, `ProximityKeyDetector`; `AutoSummary*Preference`, `VibratePreference`, `SeekBarPreferenceString` (referenced from prefs XML); `LatinIMEBackupAgent` (manifest); `HangulComposer`, `ComposeSequence`, `DeadAccentSequence`; `popup_mic.xml`, `popup_smileys.xml`.

---

## 5. Outdated features and workarounds

- **Pre-Lollipop/Honeycomb shims** — reflection for `setLayerType`, `getSelectedText`, `Editor.apply`, `SDK_INT >= 9`; hard-coded numeric constants with the real name in a comment (`111 /*KEYCODE_ESCAPE*/`, `0xe0 /*WEB_PASSWORD*/`, `SDK_INT >= 28 /* P */`).
- **Software rendering + "render mode" preference** (`pref_render_mode`, `hardwareAccelerated=false`) — from the Honeycomb transition.
- **Slide-key hack** (`pref_slide_keys_int`, `PointerTracker.java:104–105, 362–397`): types the first/last/all keys slid across. A static list shared across pointers; overlaps conceptually with what the user explicitly does not want (swipe typing) and conflicts with trackpad/delete slides. Candidate for removal; needs an owner decision.
- **Sudden-jump filter and non-distinct-multitouch emulation** (§4).
- **Dictionary-pack mechanism** (`PluginManager`): loads raw resources from other apps via `createPackageContext`; needs the `<queries>` block; the pack ecosystem is essentially gone. **AutoText "quick fixes"** relies on a framework class with no data on modern Android.
- **Re-correction** (`recorrection_enabled`) still shows a toast "doesn't work right on pre-Gingerbread phones" (`LatinIME.java:3803`).
- **Connectbot special cases** (`isConnectbot` 1633; Tab sent as DPAD_CENTER + `i` at 2046–2056; ESC as char 27; forced fullscreen override at 1268; `connectbot_tab_hack` pref). Modern ConnectBot and Termux handle `KEYCODE_TAB`/`KEYCODE_ESCAPE` with meta state. Two of the four package names are abandoned forks. Keep the generic terminal path; make the hack opt-in only and schedule removal after a device check.
- **Persistent notification to open the keyboard** (`keyboard_notification`, `NotificationReceiver`) and **`force_keyboard_on`**, **`fullscreen_override`** — workarounds for Android 2.x/3.x app behaviour; the notification now also costs the `POST_NOTIFICATIONS` runtime permission.
- **Volume-key actions** (`pref_vol_up/down` → `doSwipeAction`) — intercepts hardware volume keys while the keyboard is visible.
- **`GCUtils` OOM retry**, `SoftReference` keyboard cache (`KeyboardSwitcher.java:106`), manual bitmap icon scaling in `onBufferDraw` (1177).
- **Gingerbread/Stone/ICS themes** built from hdpi-only 9-patches (`res/drawable-hdpi` has 106 files; xhdpi 13).
- **Sound feedback** scaled against music volume with a hand-rolled dB curve (`getKeyClickVolume` 4093) plus a bundled `raw/type3.ogg` click method.
- **Legacy locale pseudo-regions** (`-rQY`, `-rCX`, `-rDV`, `-rNE`, `-rPH`, `-rST`, `-rLA`) to smuggle layout variants through the resource system. Not urgent, but it is why the app must mutate `Configuration.locale` (§1.1) and why `lint` complains. Long-term: layouts keyed by id, not by fake locale.

---

## 6. Phased plan

Principles: the app ships after every step; each step is one reviewable PR; delete before refactoring; characterise behaviour with tests before moving it; do not convert to Kotlin or Compose as a goal (an IME gains little and the risk is high).

Effort: S = up to a day, M = 2–4 days, L = 1–2 weeks. Risk is to user-visible behaviour.

### Phase 0 — Safety net and build hygiene (do first; value high, risk low)

| # | Item | Effort | Risk | Notes |
|---|---|---|---|---|
| 0.1 | CI workflow on push/PR: `testDebugUnitTest`, `lintDebug`, `assembleDebug` | S | none | Reuse the JDK/SDK setup from `release.yml`. Robolectric jars need network or a cache step. Add test run to the release workflow too. |
| 0.2 | Enable lint with a generated baseline; turn `checkReleaseBuilds` back on | S | none | Baseline freezes current debt; new code must be clean. |
| 0.3 | Pin `ndkVersion`; make release signing conditional on env vars; move `auto_version` to `resValue`; fix `.gitignore` vs tracked `androidTest` | S | none | |
| 0.4 | Drop `voiceimeutils.jar` and `fileTree(libs)`, turn off Jetifier, remove the Kotlin-stdlib pin if the build passes without it | S | low | Verified unused; build confirms. |
| 0.5 | AGP → current stable 8.x, Gradle to match, `plugins {}` DSL, version catalog, Java 17, `lint {}` block | M | low | No source change expected. Do before the SDK bump. |
| 0.6 | compileSdk/targetSdk → 35 (then 36); audit edge-to-edge on `Main`, settings activities, `ImagePickActivity`; add `dataExtractionRules` | M | medium | IME window insets (`onComputeInsets`, toolbar height, nav-bar padding) must be checked on a device/emulator with gesture and 3-button navigation — Robolectric will not catch this. |
| 0.7 | Add the missing characterisation tests (list in §8) | M–L | none | Gate for Phases 2–4. |

**Done: minSdk 24** (with AGP 9.5, compileSdk 37 and current AndroidX). Was: decide in this phase: **minSdk 21 → 23 or 24.** Recommended 24: it unlocks current AndroidX, removes `SDK_INT` branches at 23/24/25 (`CursorLoupe.java:280`, `LatinIME.java:1602`, `2660`), and costs a negligible user share. Needs the owner's say.

### Phase 1 — Delete verified-dead code (value high, risk low, no tests needed beyond the existing suite)

| # | Item | Effort |
|---|---|---|
| 1.1 | Extension-row keyboard code + `kbd_extension*.xml` + `useExtension`/`mUsingExtension` | S |
| 1.2 | `ContactsDictionary`, `DEBUG_AUTO_PLAY` block, `measureCps`, unused message constants, `mWordToSuggestions`, `TextEntryState` file logging, `prefs_for_debug.xml` | S |
| 1.3 | Reflection shims → direct calls (`setLayerType`, `EditingUtil`, `SharedPreferencesCompat`, `DeadAccentSequence`); named constants instead of magic numbers | S |
| 1.4 | Unreferenced layouts and drawables (list in §4), then `UnusedResources` lint for strings/arrays/dimens | S–M |
| 1.5 | Sudden-jump filter and non-distinct-multitouch branches | S (after 0.7's touch tests) |
| 1.6 | `values-he`/`xml-he` duplicates | S, but verify locale resolution on API 21–36 first; otherwise generate one from the other at build time |
| 1.7 | Stale repo files: `java/*.pl|sh`, `dictionaries/sample.xml`, `README-hackerskeyboard.txt`; rewrite `README.md` and `metadata/*.yml` for this fork | S |

Expected removal: roughly 1,000–1,500 lines of Java and 50+ resource files with no behaviour change.

### Phase 2 — Decide the suggestion stack (the pivotal decision; needs the owner)

Three options:

- **A. Remove word suggestions/auto-correct entirely.** Delete `Suggest`, `BinaryDictionary`, native code and the CMake build, `ExpandableDictionary`, `UserDictionary`, `AutoDictionary`, `UserBigramDictionary`, `PluginManager`, `CandidateView`, `TextEntryState`, re-correction, AutoText, the old preferred-letters bias, the two dictionary permissions, `<queries>`, and the prefs `quick_fixes`, `show_suggestions`, `auto_complete`, `recorrection_enabled`, `suggestions_in_landscape`, `pref_candidate_scale`, swipe action `suggestions`. About 4,500 Java lines, all C++, all four `AsyncTask`s, both SQLite DBs. The APK becomes pure-Java (no ABI splits, no 16 KB concern). Keep: composing for Hangul and compose/dead keys, auto-cap, double-space period, app-supplied completions if still wanted (they could go into the toolbar strip). Effort L. Risk: users relying on a dictionary pack lose suggestions.
- **B. Keep suggestions but make them real:** bundle a dictionary, render candidates in the toolbar strip instead of the framework candidates view, replace `AsyncTask`. Effort well beyond L; brings back a feature area that the fork's direction (terminals, PC layout, adaptive targets instead of correction) has not invested in.
- **C. Freeze:** leave as is behind a single `SuggestionEngine` interface so `LatinIME` stops knowing the details. Effort M. Keeps the weight.

**Recommendation: A**, staged so it is reversible:
1. Tests first (§8: typing, separators, auto-cap, backspace, Hangul, compose with suggestions off).
2. Introduce a narrow `SuggestionEngine` interface with the current implementation and a no-op one; ship with the no-op selected when no main dictionary is present (which is today's effective behaviour). One release in the field.
3. Delete the old implementation, native build and prefs; migrate/clear the obsolete pref keys.
Before step 3, check on a device whether an AnySoftKeyboard dictionary pack currently produces suggestions (§4). If it does and the owner wants that kept, stop at step 2 (= option C).

Also in this phase, with the same owner input: voice key (keep the small `VoiceInputActivity` path, drop the `hasVoice` dimension from `KeyboardId` by making the mic key a normal key-visibility flag), slide-key hack, Connectbot hack default, keyboard notification, volume-key actions, legacy themes. Each is an independent S–M removal; none blocks the rest.

### Phase 3 — Carve seams out of `LatinIME` (value high, risk medium; tests required)

Extract in this order; each step moves code behind an interface with no behaviour change, and the service keeps delegating:

1. **`Settings` (immutable snapshot)** — one class that reads all prefs once with one set of defaults and exposes a typed value object; replaces the `m*` mirrors, `GlobalKeyboardSettings` public fields and the 120-line listener chain with "reload snapshot, diff, apply". Split runtime state (`keyboardMode`, `inputLocale`, editor info) out of `GlobalKeyboardSettings`. Switch everything to `androidx.preference.PreferenceManager`. M. Tests: `SettingsScreensTest` exists; add a defaults test.
2. **`EditorSession` / editor facade** — the only class that talks to `InputConnection`: text snapshot, selection memory (`mSetAnchor/mSetFocus`), `moveCursor*`, `selectAtCursor`, `swapSelectionEnds`, commit image, terminal detection. `CursorDrag` and delete-swipe become clients of it instead of each keeping their own snapshot. This unifies the five cursor paths of §3.5 down to two (key events for terminals, selection for editors) behind one API. L. Tests: `SelectionPanelTest` (26), `CursorMoveTest`, `ReversedSelectionTest` already cover a lot; they should be re-pointed at the facade with a fake `InputConnection`, so they stop needing the whole service.
3. **`KeyEventSender` / terminal encoder** — modifiers, chording, escape and control sequences, Tab/Esc, Connectbot special cases (`LatinIME.java:1644–2068`, about 430 lines). Pure function of (key, modifier state, target kind) → list of `InputConnection` operations; ideal for plain JUnit table tests. M. **Write the tests first** — this is the app's core value for SSH users and currently has none.
4. **`ModifierState`** — Shift/Ctrl/Alt/Meta/Fn, multitouch shift, caps lock (`2980–3086`, six `ModifierKeyState` fields, `mMod*` booleans). S–M.
5. **`DeleteSwipeController`** (state at `2880–2978`), **`AutofillChipsController`** (`2396–2520`), **`FeedbackController`** (sound/vibrate), **`ImageInsertion`** (replace static `sPendingImage*` with a result passed through the facade). S each.
6. **`ActionDispatcher`** for swipe/volume actions (`doSwipeAction`) and a real "reconfigure keyboard" method to replace `toggleLanguage(true, true)`. S.
7. Replace `LatinIME.sInstance` usages: pass the collaborators above into `KeyboardSwitcher`, `PointerTracker`, `KeyboardToolbar` via constructors/setters. Remove `sInstance` last, when the reference count reaches zero. M, spread across the steps.

Target after Phase 3: `LatinIME` under ~1,200 lines — lifecycle, wiring and `onKey` dispatch only.

### Phase 4 — Touch pipeline (value medium-high, risk high; do after Phase 3)

1. One `GestureArbiter` in front of key handling that decides, per stroke: keys, vertical swipe action, space-bar slide (language) or trackpad, backspace swipe. Remove the fling detector and `SwipeTracker` once `SwipeGestureTest` passes against the single recogniser. M.
2. Move trackpad and delete-swipe driving out of `PointerTracker` into gesture handlers that both the key area and the toolbar panels use (removes the duplicate wiring in `KeyboardToolbar.java:519–580, 719–770`). M.
3. Move the space-bar language-drag state out of `LatinKeyboard.isInside` into a handler; `Keyboard`/`LatinKeyboard` become models without gesture state. M.
4. Split `LatinKeyboardBaseView`: `KeyboardRenderer` (drawing), `KeyPreviewController`/`PopupKeyboardController`, `KeyTimers`. Enable hardware acceleration for the IME window and retire `pref_render_mode` once rendering is verified (`RenderKeyboardTest`, `LayoutOutputTest`, `SeamTest` give a pixel baseline). L.
5. Stop mutating `Configuration.locale`: load layout XML through `createConfigurationContext(locale)` in one `KeyboardLayoutLoader`. M. Risk: the pseudo-region locales; test every language once (`LayoutOutputTest` can be extended to iterate all of `InputLanguageSelection`'s lists).

### Phase 5 — Toolbar and settings UI (value medium, risk low-medium)

1. Split `KeyboardToolbar` into `ToolbarBar`, `SelectionPanel`, `ClipboardPanel`, `EmojiPanel` (+ `EmojiSearch`, `EmojiRepository`), `AutofillStrip`, `ToolbarTheme`, each with a small interface to the editor facade. Mechanical once Phase 3.2 exists. M–L. `ToolbarTest`/`SelectionPanelTest` protect it.
2. Collapse the four settings activities into one activity with `PreferenceFragmentCompat` navigation; remove obsolete prefs; migrate string-typed ints. M. Keep preference **keys** stable (backup/restore and existing installs) and write a one-time migration for removed ones.
3. Theme tokens (colours per theme) instead of sampling the background drawable; lets legacy themes be dropped or kept cheaply. M.
4. One shared background executor for `ClipboardHistory`, `AdaptiveKeyTargets` loading and file writes; cancel on `onDestroy`. S.

### Leave alone
- **Keyboard layout XML** (`res/xml*/kbd_*.xml`, `donottranslate-keymap.xml`, `donottranslate-altchars.xml` per language) — large, data-like, correct, and the product's core asset.
- **`ComposeSequence`, `DeadAccentSequence`, `HangulComposer`** — self-contained and working; only decouple from `LatinIME` via a small output interface when touched.
- **`CursorTrackpad`, `DeleteSwipeAccel`, `CursorLoupe`, `GestureLabel`, `InlineContentClipView`, `ClipboardHistory`, `CrashLog`** — small, recent, tested or simple. Only change their wiring.
- **`AdaptiveKeyTargets`** internals (686 lines, has its own word lists and tooling in `tools/mkwordlist.py`) — keep; just replace its singleton with injection in Phase 3.7.
- **Language**: stay on Java. New files may be Kotlin later if the owner wants, but a conversion pass buys nothing here.
- **No swipe typing, no new prediction engine** (per requirements).
- **Pseudo-region locale scheme** — live with it until Phase 4.5; do not rename resource directories earlier.

---

## 7. Target architecture sketch

Single Gradle module to start; packages first, modules only if build time or enforcement demands it later. Arrows are allowed dependencies (downward only).

```
ime/            LatinIME (InputMethodService: lifecycle, wiring, onKey dispatch)
  │             ActionDispatcher, FeedbackController, AutofillChipsController
  ▼
input/          GestureArbiter, PointerTracker (keys only), KeyTimers
  │             TrackpadGesture, DeleteSwipeGesture, SpaceLanguageSlide, SwipeActionGesture
  ▼
editor/         EditorSession (sole owner of InputConnection): text snapshot, selection,
  │             cursor moves, commit text/image, terminal-vs-editor detection
  │             KeyEventSender (modifiers, chording, ESC/CTRL sequences), ModifierState
  │             CursorDrag, Composer (Hangul, compose/dead keys, auto-cap, punctuation)
  ▼
keyboard/       Keyboard, Key, LatinKeyboard (pure model), KeyboardId, KeyboardLayoutLoader,
  │             KeyboardSwitcher (instance, not singleton), KeyDetector(s), AdaptiveKeyTargets
  ▼
view/           KeyboardView + KeyboardRenderer, KeyPreviewController, PopupKeyboardController,
                CursorLoupe, GestureLabel
toolbar/        ToolbarBar, SelectionPanel, ClipboardPanel, EmojiPanel, EmojiSearch, AutofillStrip
                (depends on editor/ and view/, never on ime/)
data/           Settings (immutable snapshot + change diff), ClipboardHistory, EmojiRepository,
                LanguageSwitcher, IoExecutor
settings/       Main, SettingsActivity (+ fragments), InputLanguageSelection, custom Preference widgets
                (depends on data/ only — no reference to the running service)
util/           CrashLog, small helpers
```

Boundary rules (enforce with a lint/ArchUnit-style test once packages exist):
- Only `editor/` imports `InputConnection`.
- `keyboard/` and `view/` do not import `ime/`; no `static` instance of the service anywhere.
- `settings/` talks to the keyboard only through `SharedPreferences` (the debug "input connection info" row gets its data via a small persisted/debug provider, not a static field).
- Preferences are read only in `data/Settings`.
- If option A (§6 Phase 2) is not taken: `suggest/` sits beside `editor/` behind `SuggestionEngine`, and the native library stays confined to it.

---

## 8. Testing strategy

Current state: 12 Robolectric classes, 66 tests **[counted at HEAD `56630d9`]**, concentrated on the new features (`SelectionPanelTest` 26, `SwipeGestureTest` 9, `SettingsScreensTest` 9, `CursorMoveTest` 6, `ToolbarTest` 5). Two `androidTest` smoke tests exist but are git-ignored and not run anywhere. The 2010 core has **no tests**: key→output encoding, modifiers, composing, auto-cap, Hangul, compose sequences, keyboard XML parsing, language switching.

Add, in this order (Phase 0.7; each is a prerequisite for the phase named):
1. **Key output table tests** (before 3.3/3.4): for a fake `InputConnection` recording calls — plain letters, Shift/Caps, Ctrl+letter, Alt, Meta, Fn row, arrows with modifiers, Tab/Esc, F-keys, chording on/off, in an editor field and in a `TYPE_NULL` terminal field, plus the Connectbot package case. Record current behaviour as golden data.
2. **Typing-flow tests** (before Phase 2): type word + space + punctuation, double-space period, auto-cap at sentence start, backspace through composing text, Enter (the "duplicated word on Enter" bug from `abcd89a`), password field, with suggestions off and with app completions.
3. **Hangul and compose/dead-key tests**: pure JUnit against `HangulComposer`, `ComposeSequence`, `DeadAccentSequence` (no Android needed after a tiny output interface).
4. **Layout load test for every language × mode** (before 1.6, 4.5): iterate the language lists, load each keyboard XML in portrait/landscape, 4-row/5-row; assert no exception and a stable key-count/label snapshot. Extends `LayoutOutputTest`.
5. **Touch tests** (before 1.5 and Phase 4): two-thumb rollover, long-press popup, slide into popup, key repeat, space-bar slide vs hold-slide, backspace swipe, vertical swipe actions — `SwipeGestureTest` is the pattern.
6. **Settings defaults test** (before 3.1): fresh install → snapshot equals documented defaults; each pref change triggers the expected reload class.
7. **Render snapshots**: `RenderKeyboardTest`/`SeamTest` already render natively; store PNG goldens per theme for Phase 4.4/5.3.
8. **Device smoke (manual or emulator in CI, later)**: un-ignore `androidTest`, run `LatinIMEStartupTest`/`SettingsActivityLaunchTest` on API 23/29/35 emulators; manual checklist for insets, autofill chips with a real password manager, image insertion, Termux/ConnectBot Tab/Esc/Ctrl — things Robolectric cannot show.

Test design rules going forward: new logic goes into plain classes testable without `buildService`; no reflection on private fields (remove the two cases in `SelectionPanelTest` when 3.5 lands); reset any remaining singleton in `@After`.

---

## 9. Keeping the app working throughout

- One concern per PR; deletions and moves separate from behaviour changes; every PR green on CI (0.1).
- Strangler pattern for `LatinIME`: introduce the collaborator, delegate from the old method, migrate callers, delete the old method — never a parallel rewrite.
- Preference keys are a public contract (backup/restore, existing installs): keep keys, add a versioned one-time migration when removing or retyping any.
- Behaviour removals (suggestions, slide keys, Connectbot hack, notification, themes) ship one at a time, each called out in release notes, ideally one release with the feature merely disabled-by-default before the code goes.
- Release cadence: tag after each phase at minimum; Phases 0–1 can ship as a single "no visible change" release, which also validates the upgraded toolchain and signing before riskier work.
- Manual device pass before each tag (checklist in §8.8), because IME insets, autofill inline suggestions and terminal apps are not covered by Robolectric.

## 10. Top risks

| Risk | Where | Mitigation |
|---|---|---|
| Regressing terminal key output (the app's reason to exist) | 3.3, 3.4, Connectbot removal | Golden table tests first; device check in Termux and ConnectBot. |
| Selection/cursor regressions in editors that normalise selections | 3.2 | Existing 30+ tests; keep `CursorDrag` logic intact, change only who owns the snapshot. |
| Inset/edge-to-edge breakage at target 35/36 | 0.6 | Emulator matrix; separate PR from the AGP bump. |
| Locale/resource resolution (`iw`/`he`, pseudo-regions) | 1.6, 4.5 | All-languages layout test before touching. |
| Users who do rely on a dictionary pack | Phase 2 | Device check; staged no-op engine release; release note. |
| Lost user settings | 3.1, 5.2 | Key stability + migration test. |
| Touch feel changes (timing, slop) | Phase 4 | Keep constants; touch tests; beta build to the owner before tagging. |

## 11. Open questions for the owner

1. Suggestions: remove (A), revive (B) or freeze (C)? Does anyone use an external dictionary pack with this fork?
2. minSdk 23 or 24 acceptable?
3. Keep: voice key, slide-key option, volume-key actions, persistent notification, Connectbot Tab hack, the five pre-Material themes?
4. Is Google Play a distribution target (drives the targetSdk deadline and backup rules), or GitHub releases/F-Droid only?
