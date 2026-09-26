# Clean Android Rebuild Specification

## Goal
Create a brand-new Android application for the sentence-study app rather than modifying or re-signing any previous APK. The resulting APK must install as a separate app on the user's Android 16 Samsung device and preserve the existing sentence-study experience, with reliable Previous/Next navigation in both normal and mixed modes.

## User-visible behavior
- App installs under a new package/application ID, independent from all earlier builds.
- Existing Turkish/English sentence content is embedded in the APK and works offline except for features that inherently require internet access.
- Bottom navigation keeps Previous, Play/Pause, Next, Grammar, and Settings.
- Normal mode: Next moves to the following sentence; Previous moves to the prior sentence.
- Mixed mode: a shuffled order is generated and kept stable; Next/Previous move through that shuffled order without sticking or jumping back.
- Displayed sentence, current playback sentence, and saved position always refer to the same logical item.
- Manual Previous/Next stops the current utterance before moving, then resumes from the newly displayed sentence only when playback was already active.
- Auto-advance uses the exact same navigation state as manual navigation.
- Turkish text is spoken with Turkish TTS; English text is spoken with English TTS.
- Voice settings remain persistent, including selected Turkish/English voice, favorites, hidden voices, and preview-before-apply behavior.
- Background/screen-off playback continues through a foreground service; removing the app from recents stops playback; no boot auto-start.

## Architecture
- New Gradle Android app project; do not reuse or modify the binary structure of previous APKs.
- Android WebView hosts the existing HTML UI/content.
- Native Android bridge handles TTS, persistent voice preferences, foreground playback service, and playback-position synchronization.
- A single source of truth for navigation is used: `order[]` plus `orderPos`; `index` is derived from that state and never advances independently.
- Mixed mode only changes the contents/order of `order[]`; all navigation calls use the same `navigate(delta)` path.

## Android packaging requirements
- New package ID: `net.ytutor.sentencestudy.clean`.
- Build with Android Gradle Plugin using `assembleDebug` so the APK receives normal Android debug signing rather than manual ZIP/JAR repackaging.
- Java 17 build environment.
- Compile/target SDK compatible with Android 16; minimum SDK chosen low enough for current supported devices while preserving required foreground-service APIs.
- Manifest explicitly declares required foreground-service, wake-lock, notification, and internet permissions.
- No boot receiver.
- `PlaybackService` uses `START_NOT_STICKY` and stops on task removal.

## Navigation invariants
1. `orderPos` is always within `0..order.length-1` when data exists.
2. `index === order[orderPos]` after every navigation, shuffle, restore, and autoplay transition.
3. Manual Next/Previous never writes `index` directly.
4. Auto-advance never increments `index` or `orderPos` through a separate path.
5. Shuffle creates a permutation of the active dataset and resets or restores `orderPos` predictably.
6. UI synchronization from native playback state may update `orderPos`, then derives `index` and renders once.

## Verification requirements before handoff
- JavaScript syntax check passes for every inline script.
- Unit/instrumentation-level tests or deterministic script tests cover normal and mixed Previous/Next transitions, wraparound, autoplay transition, and state restore.
- `gradle :app:assembleDebug` succeeds from a clean checkout.
- `unzip -t` succeeds on the produced APK.
- Android build-tools signature verification succeeds and shows a valid APK signature.
- APK manifest/package ID is inspected and matches `net.ytutor.sentencestudy.clean`.
- Packaged `assets/index.html` is inspected to confirm mixed-navigation fix and expected content are actually present.
- Only after all checks pass should the APK be handed to the user.

## Non-goals
- No redesign of the study UI unless needed for Android compatibility.
- No migration of data from broken earlier APKs.
- No manual APK ZIP editing or post-build binary patching.
