# RetroCam — Product Requirements Document

_Source: Claude Docs PRD, 2026-11-13. This is a copy for the coding agents; the doc is the source of truth._

## Overview

RetroCam is a free-feeling, one-screen Android camera that makes every photo look like it came from a disposable or point-and-shoot film camera. Users pick a camera type (20 at launch), point, shoot, and get a finished film-look photo in the gallery — no editing step, no watermark.
**Problem.** Phone photos look clinical. Existing film-filter apps bury the look behind subscriptions, watermarks, ads or multi-step editors.
**Goals**

- Shoot-to-saved film look in one tap, with the preview matching the saved photo.
- 20 camera types at v1.0, each with its own colour, grain, flash and flare character.
- Works in portrait and landscape with correct photo orientation.
- Free forever: no watermark, no account, no ads, no in-app purchases, no network permission.
- Smooth on mid-range phones: 30 fps filtered preview, under 1 s to saved photo.
**Non-goals (v1)**

- Video recording, RAW/DNG capture, manual exposure controls.
- A full photo editor or social feed.
- iOS version and Play Store release; the APK ships only through GitHub Releases.
- Cloud sync or accounts.

## Users and use cases

The primary user is a casual phone photographer, 16–35, who wants film-style photos for social posts and memories without editing.

| Persona | Wants | Key need |
|---|---|---|
| Casual social poster | Nostalgic photos for Instagram/TikTok | One-tap look, fast save |
| Party and travel shooter | Flash snaps at night, holiday shots | Convincing cheap-flash look, date stamp |
| Film hobbyist | Character of specific stocks without film cost | Distinct, faithful camera types, grain control |
| Developer (you) | Portfolio piece | Clean architecture, CI-built APK |

**User stories**

- As a user, I open the app and can shoot within 1 second.
- As a user, I swipe or tap to pick a camera type and see the look live in the viewfinder.
- As a user, I toggle flash, grain, lens flare and light leaks with one tap each.
- As a user, I rotate the phone and the photo saves in the right orientation.
- As a user, I find my photos in the phone's gallery under Pictures/RetroCam, with no watermark.
- As a user, I can optionally add the classic orange date stamp.
- As a user, I can switch to "Disposable mode" with a 27-shot roll counter.

## Camera types

RetroCam ships 20 camera types at v1.0 and 5 more in v1.1, across six families. Each is an original approximation of a film camera's character, not an official profile. In-app names avoid brand and film-stock trademarks in case the app is ever published beyond GitHub; the "Inspired by" column is for the team only.

### Disposables

| In-app name | Inspired by | Colour character | Grain | Flash | Extras | Release |
|---|---|---|---|---|---|---|
| Gold Snap | Kodak FunSaver (Gold/UltraMax 400) | Warm yellow-orange, punchy reds, lifted shadows | Medium | Harsh hot centre, fast falloff | Strong vignette, soft corners | v1.0 |
| Power 800 | Kodak Max 800 single-use | Warm, contrasty, slightly muddy shadows | Heavy | Strong, bright | Medium vignette | v1.0 |
| Green Snap | Fujifilm QuickSnap Flash 400 | Cool greens, cyan shadows, neutral skin | Medium | Cool white | Mild vignette, crisper centre | v1.0 |
| Mono Snap | Ilford HP5 Plus single-use | Black and white, punchy mids | Heavy | Harsh | Strong vignette | v1.0 |
| Pop Simple | Lomography Simple Use (CN 400) | Saturated, warm, high contrast | Medium | Optional colour gels (red, blue, yellow) | Heavy vignette | v1.0 |
| Lebox Red | AgfaPhoto LeBox | Red-leaning, warm, saturated reds | Medium | Warm | Medium vignette | v1.1 |
| Aqua | Fujifilm QuickSnap Marine / Kodak Sport | Blue-green cast, low contrast | Medium | None | Soft overall, waterline-blue tint | v1.1 |

### Point-and-shoot compacts

| In-app name | Inspired by | Colour character | Grain | Flash | Extras | Release |
|---|---|---|---|---|---|---|
| Classic 200 | Kodak Gold 200 in a 90s compact | Warm golden, slightly yellow highlights | Fine–medium | Compact flash, even | Light vignette, sharper lens | v1.0 |
| Studio 400 | Kodak Portra 400 | Soft pastel, accurate warm skin, low contrast | Fine | Soft | Minimal vignette | v1.0 |
| Vivid 100 | Kodak Ektar 100 | High saturation, deep blues, crisp | Very fine | Even | Sharp | v1.0 |
| Street 400 | Fujifilm Superia 400 | Greenish, crisp, cool shadows | Medium | Cool | Light vignette | v1.0 |
| Airy 400 | Fujifilm Pro 400H (overexposed) | Bright, mint greens, pastel blues | Fine | Soft | +0.7 EV bias, lifted blacks | v1.0 |
| Budget 200 | Fujicolor C200 | Slightly cool, green shadows, modest saturation | Medium | Even | Light vignette | v1.0 |
| Luxe Compact | Contax T2 / Olympus mju-II | Neutral-warm, contrasty, rich | Fine | Bright, punchy | Sharp centre, slight vignette | v1.1 |

### Instant

| In-app name | Inspired by | Colour character | Grain | Flash | Extras | Release |
|---|---|---|---|---|---|---|
| Instant Square | Fujifilm Instax Square | Vivid, high-key, punchy; dense shadows, highlights blow early, clean slightly cool whites | Fine | Bright, fairly even, neutral-cool | White square frame option, a touch softer than Mini | v1.0 |
| Instant Mini | Fujifilm Instax Mini | Vivid, high-key, punchy; dense shadows, highlights blow early, clean slightly cool whites | Fine | Bright, fairly even, neutral-cool | Credit-card frame option | v1.0 |

### Black and white

| In-app name | Inspired by | Colour character | Grain | Flash | Extras | Release |
|---|---|---|---|---|---|---|
| Grit 400 | Kodak Tri-X 400 | High contrast, deep blacks | Heavy | Harsh | Strong vignette | v1.0 |
| Silver 100 | Ilford Delta / T-Max 100 | Smooth tones, gentle contrast | Fine | Soft | Minimal vignette | v1.0 |

### Slide and cinema

| In-app name | Inspired by | Colour character | Grain | Flash | Extras | Release |
|---|---|---|---|---|---|---|
| Slide 100 | Fujifilm Velvia / Kodak Ektachrome | Very saturated, deep blacks, punchy greens | Fine | Even | High contrast | v1.0 |
| Night 800T | CineStill 800T | Tungsten-balanced cool, teal shadows | Medium–heavy | Cool | Strong red halation around lights | v1.0 |
| Cross Process | Slide film developed in C-41 | Yellow-green highlights, cyan-blue shadows, extreme contrast | Medium | Harsh | Random colour shift per shot | v1.0 |

### Toy and special

| In-app name | Inspired by | Colour character | Grain | Flash | Extras | Release |
|---|---|---|---|---|---|---|
| Plastic 120 | Holga / Diana | Saturated, warm | Medium | None | Square crop, heavy vignette, blurred edges, frequent light leaks | v1.0 |
| Expired 90s | Expired consumer film | Magenta or cyan shift, fogged blacks, faded | Heavy | Harsh | Random light leaks and dust, colour drift per shot | v1.0 |
| Half Frame | Kodak Ektar H35 / Olympus Pen | Warm | Medium | Even | Two vertical frames side by side per photo | v1.1 |
| Purple Haze | LomoChrome Purple | Purple foliage, teal skies | Medium | Even | Colour swap curve | v1.1 |

### What defines a camera type

Each camera type is one profile file (JSON) plus one LUT image, so new types need no code changes:

| Parameter | Range or format | Example (Gold Snap) |
|---|---|---|
| Colour LUT | 33×33×33 3D LUT stored as a 1089×33 PNG | gold_snap.png |
| Exposure bias | −1.0 to +1.0 EV | −0.3 |
| Black lift / contrast | 0–0.15 / 0.7–1.4 | 0.06 / 1.1 |
| Grain amount / size | 0–1 / 1–3 px | 0.45 / 1.6 |
| Vignette strength / softness | 0–1 / 0–1 | 0.55 / 0.7 |
| Halation strength / tint | 0–1 / RGB | 0.2 / red |
| Chromatic aberration | 0–4 px at corners | 1.5 |
| Edge softness | 0–1 | 0.4 |
| Flash profile | hot-spot size, falloff, colour temperature | small, fast, 6500 K |
| Flare and leak style | none / warm / rainbow / random | warm |
| Frame format | full / square / instant / half-frame | full |
| Date stamp colour | RGB | orange |

## Functional requirements

Priority: P0 = must ship in v1.0, P1 = should ship in v1.0, P2 = later.

| ID | Requirement | Priority |
|---|---|---|
| F1 | Live viewfinder shows the selected camera type's look in real time (colour, grain, vignette). | P0 |
| F2 | One-tap capture saves a full-resolution JPEG with the same look as the preview. | P0 |
| F3 | Camera type picker: horizontal strip of camera cards; swipe on the viewfinder also changes type. | P0 |
| F4 | Flash: Off / Auto / On. Fires the real flash and applies the camera type's flash look (hot spot, falloff, colour). On the front camera (no flash unit) the toggle is Off / On only (a stored Auto reads as On) and uses the screen as the flash: at the moment of capture, after any self-timer, the screen goes pure white at full window brightness, then the previous brightness is restored; brightness is never raised in the preview or on toggle. The shot gets the same flash look as a real flash. AE lock is not available while it is on. | P0 |
| F5 | Grain: Off / Low / Normal / High, scaled per camera type. Luminance-dependent (stronger in shadows), new pattern every shot. | P0 |
| F6 | Lens flare: Off / Auto (added when a bright highlight is detected) / Always. | P1 |
| F7 | Light leaks: Off / Subtle / Strong; random position and colour per shot. | P1 |
| F8 | Date stamp: Off by default; classic orange 7-segment date in the bottom-right; format options 'YY MM DD and DD MM 'YY. | P1 |
| F9 | Portrait and landscape: photo orientation follows the phone; controls rotate in place. | P0 |
| F10 | Front and back camera switch. | P0 |
| F11 | Save to Pictures/RetroCam via MediaStore with EXIF (date, orientation; location off by default). | P0 |
| F12 | Last-shot thumbnail opens RetroCam's in-app gallery: a grid of its photos, a swipeable full-screen viewer with zoom, share, delete and "open in Photos", and multi-select delete. | P0 |
| F13 | Self-timer: Off / 3 s / 10 s. | P1 |
| F14 | Tap to focus and exposure. Zoom by pinch and by lens buttons (0.5×, 1×, 2×, telephoto) that use the phone's ultra-wide and telephoto lenses where available; see Zoom and lenses. | P1 |
| F15 | Disposable mode: 27-shot roll counter and a simple viewfinder; photos save immediately (no delayed develop). | P2 |
| F16 | Frame option for Instant types (white border) and Half Frame layout. | P1 |
| F17 | Settings persist between sessions (last camera type, toggles). | P0 |
| F18 | Re-develop: apply a different camera type to an already-taken photo (original kept in app storage). | P2 |
| F19 | Import a photo from the gallery and apply a camera type. | P2 |
| F20 | Shutter sound and haptic, with a mute option. | P1 |
| F21 | Photo ratio: 4:3 (default, full sensor frame), 3:2, 16:9, 1:1. Applies to normal cameras and to Instant Mini without its frame; square, instant-framed and Half Frame cameras keep their own format (the chip shows it dimmed and explains on tap). Centre crop after the film look, so the saved photo matches the viewfinder mask; the ratio follows the photo's orientation (portrait 3:4 / 2:3 / 9:16, landscape 4:3 / 3:2 / 16:9). The choice is remembered. | P1 |
| F22 | Exposure compensation (EV): an EV chip next to the ratio chip opens a slider on the bottom edge of the viewfinder (the camera's own range and steps, e.g. −2 to +2 in 1/3 steps; value label "±0", "+0.7", "−1.3"; double-tap the value to reset; TalkBack announces the EV). Applies to the live preview and the saved photo. Held for the session only: back to ±0 on a fresh launch, kept across camera types, front/back and the gallery; hidden when the phone reports no compensation. AE lock (toggle in the same row, "AE LOCK" pill on the viewfinder, tap the pill to release) holds auto exposure and white balance; it is released on a camera or lens switch and is not available while the flash is on or auto. Tap to focus keeps its point (no auto-reset); while AE is locked a tap only refocuses. | P1 |
| F23 | Mirror front camera photos (setting, off by default): save selfies as they look in the viewfinder. Applied after the film look and before the frame/ratio crop and the date stamp, so light leaks and flare match the preview and the date text is never mirrored. Back camera photos are never mirrored. | P1 |

**Hard rules**

- No watermark, logo or app name is ever drawn on a photo. The date stamp is the only overlay, and it is off by default.
- No internet permission, no ads, no analytics SDKs in v1.
- Only the CAMERA permission is required; location is an opt-in permission.

### Zoom and lenses

RetroCam uses every back lens the phone shares with apps (ultra-wide, main, telephoto) and falls back to digital zoom on the main lens when it doesn't.
- Primary method: read the device's zoom range from CameraX. On phones that group their lenses into one logical camera, a zoom ratio below 1.0 switches to the ultra-wide and higher ratios switch to the telephoto; the phone's camera driver does the switch, so zoom stays smooth.
- Fallback: if a phone lists a lens as a separate camera instead, switch to it with CameraX's physical-camera selection (CameraX 1.4+) by rebinding the camera; a short preview blink is acceptable.
- Lens buttons: a row of chips above the shutter shows only the stops the phone supports, e.g. 0.6× · 1× · 2× · 3×. Pinch moves smoothly between them.
- Zoom limit: by default zoom stops at the longest optical lens (or 2× digital on single-lens phones) to keep image quality; "Unlock full zoom" in settings allows the device maximum.
- Look per lens: each camera profile can scale vignette, edge softness and chromatic aberration by lens, so the ultra-wide looks more like a cheap wide plastic lens.
- Front camera: zoom chips hidden unless the front camera reports a range above 1×.
- Limitation: some phones don't expose all lenses to third-party apps (some Samsung telephotos, for example); RetroCam then simply hides those chips. Test lens switching on any phone with an ultra-wide or telephoto that you can borrow.

## UX and interface

RetroCam is one screen: a full-bleed viewfinder with every control one tap away, and no menus deeper than one sheet.
Screen zones (portrait; landscape mirrors to the short edge)

| Zone | Contents |
|---|---|
| Top bar | Flash (front camera: Off / On, screen flash), grain, flare, light leak, date stamp, timer, filter strength, settings — icon toggles that cycle on tap; long-press shows a label. Below 352dp width the bar scrolls sideways instead of hiding toggles |
| Viewfinder | Live filtered preview in a fixed 3:4 box, with a crop mask for 3:2, 16:9 and 1:1 (square types); "AE LOCK" pill top-left while locked; tap to focus; pinch to zoom; swipe left/right to change camera type |
| Ratio, EV and zoom chips | Row above the camera strip: a ratio chip (4:3 · 3:2 · 16:9 · 1:1, tap to cycle; dimmed with the fixed format name on fixed-format cameras), an EV chip (current compensation, e.g. "EV +0.7"; tap opens the exposure slider with the AE lock toggle, replacing the strength row if open; hidden if unsupported), then the lens chips. The ratio chip shows even on single-lens phones. Ratio also available in settings |
| Camera strip | Horizontal strip of camera cards (icon + in-app name), selected card centred |
| Bottom bar | Last-shot thumbnail (left), large shutter (centre), front/back switch (right) |
| Settings sheet | Opened from a small gear: shutter sound, date format, photo ratio, white frame, mirror front camera photos, EXIF location, zoom unlock, about |

Orientation behaviour
- The activity layout stays fixed to the sensor (like the stock camera); icons and labels rotate 90° with a 200 ms animation.
- An orientation listener sets the capture rotation, so saved photos are upright in both orientations.
- No activity restart on rotation, so the camera never re-opens mid-shot.
Visual style
- Dark UI so the preview reads true; a single accent colour.
- Camera cards: flat vector illustrations of each camera body in the app icon's style, drawn in-house as Android vector drawables (original artwork, no brand logos).
- Material 3 components, dynamic colour off (consistent retro look).

Approved card style: one body shape per camera family (disposable, instant, compact, toy), recoloured per camera type; the selected card gets an orange outline.
Capture feedback
- Shutter click sound + haptic, a brief white flash on screen when flash is on, thumbnail animates in when the save finishes.
Accessibility
- All icons have content descriptions; touch targets at least 48 dp; shutter also triggered by volume keys.
App icon
A charcoal retro compact camera with warm amber and orange stripes, on a cream background with an amber sun disc. It's an original design with no brand logos.

- Adaptive icon (Android 8+, so every supported version): vector foreground, background and monochrome layers, so it stays sharp on every screen and needs no PNG sizes.
- Monochrome layer supports Android 13+ themed icons.
- Files: res/drawable/ic_launcher_foreground.xmlres/drawable/ic_launcher_foreground.xml, ic_launcher_background.xmlic_launcher_background.xml, ic_launcher_monochrome.xmlic_launcher_monochrome.xml, res/mipmap-anydpi-v26/ic_launcher.xmlres/mipmap-anydpi-v26/ic_launcher.xml and ic_launcher_round.xmlic_launcher_round.xml, plus a 512 px PNG for the GitHub README.
- Colours: cream #F4E8D2, amber #F2B544, orange #E2683A, charcoal #2B2733.

## Architecture and tech stack

RetroCam is a native Kotlin app where one GPU shader processes both the live preview and the full-resolution photo, so what you see is what you save.
The top path runs every preview frame; the bottom path runs once per shot on a background thread. Camera profiles and the user's toggles set the shader's parameters for both.

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin 2.x (K2 compiler), coroutines | Current Android standard, fast builds |
| UI | Jetpack Compose + Material 3, single activity | Little code, easy rotation of icons |
| Camera | CameraX 1.5+ (Preview, ImageCapture, CameraEffect / SurfaceProcessor) | Handles device quirks, plugs a GPU effect into the stream |
| GPU effects | OpenGL ES 3.0 fragment shader on a dedicated GL thread; EGL off-screen surface for captures | Real-time at 30 fps, identical output for saved photos |
| Colour grading | 33³ 3D LUT per camera type, packed as a PNG and sampled as a 3D texture | One lookup per pixel, tiny files |
| Grain and leaks | Procedural noise and gradients in the shader | No large texture assets |
| Image output | ImageCapture in maximize-quality mode, JPEG quality 95 + androidx.exifinterface | Correct orientation and date metadata |
| Storage | MediaStore (photos), DataStore Preferences (settings) | No storage permission needed |
| Dependency injection | Manual (constructor injection) | Faster startup, smaller APK than Hilt |
| Build | Latest stable Android Gradle Plugin, version catalog, R8 full mode, resource shrinking, baseline + startup profiles, arm64-v8a only | Small and fast-starting APK |
| Testing | JUnit for profile parsing; instrumented golden-image tests for the shader | Catch look regressions automatically |
| LUT authoring | Python script (numpy + colour-science) that turns reference photo pairs into .cube files, then PNG | Reproducible, runs in Claude Code cloud |

Module layout
- :app:app — activity, Compose screens, view models.
- :camera:camera — CameraX setup, capture, orientation, flash control.
- :filmengine:filmengine — shaders, LUT loader, effect parameters, off-screen renderer.
- :profiles:profiles — camera type JSON files, LUT PNGs, camera-card artwork.

## Non-functional requirements

Targets are measured on your own phone as the reference device.

| Area | Target |
|---|---|
| Cold start to live preview | ≤ 700 ms |
| Preview frame rate with full effects | ≥ 30 fps, no dropped frames when switching camera type |
| Shutter lag (tap to capture) | ≤ 150 ms without flash |
| Tap to photo saved (12 MP) | ≤ 1.0 s, processed in the background so the next shot is not blocked |
| Release APK size | ≤ 10 MB (arm64-v8a) |
| Memory while shooting | ≤ 250 MB peak |
| Minimum Android | 9 (API 28); target and compile SDK 36 |
| Devices | Any phone with OpenGL ES 3.0 (all API 28+ phones in practice) |
| Crash-free sessions | ≥ 99.5% in beta |
| Battery | Preview pipeline idles when the app is backgrounded; camera released in onStop |

Privacy and permissions
- Permissions: CAMERA (required), ACCESS_FINE_LOCATION (only if the user enables location EXIF), VIBRATE.
- No INTERNET permission. Photos never leave the device.
- No READ_MEDIA permissions needed: saving uses MediaStore, and the thumbnail and in-app gallery list only photos this install of RetroCam saved. Photos saved by an earlier install are not listed (scoped storage hides them without a read permission); they stay in the phone's gallery.
Quality of the look
- Saved photo and preview differ by no more than a visually negligible amount (same shader, same LUT, same parameters).
- Grain is resolution-aware so it looks the same on the 12 MP file as on the preview.

## Build and delivery

Claude Code on the web writes the code; GitHub Actions compiles the APK, because cloud sessions can't reliably download the Android SDK. No compiler is needed on your machine.
- Code lives in a GitHub repo (retrocamretrocam, private is fine) opened in Claude Code on the web.
- Every push to mainmain and every pull request runs .github/workflows/build.yml.github/workflows/build.yml on ubuntu-latestubuntu-latest (Android SDK preinstalled): JDK 21 → Gradle cache → lint + unit tests → assembleReleaseassembleRelease.
- The release APK is signed with a keystore stored as GitHub secrets (KEYSTORE_BASE64KEYSTORE_BASE64, KEYSTORE_PASSWORDKEYSTORE_PASSWORD, KEY_ALIASKEY_ALIAS, KEY_PASSWORDKEY_PASSWORD).
- The APK is uploaded as a workflow artifact; pushing a tag v*v* also attaches it to a GitHub Release.
- Claude Code watches the run with gh run watchgh run watch, reads failures with gh run view --log-failedgh run view --log-failed, fixes them and pushes again.
- You install the APK on your phone from the Release page (allow "install unknown apps" once).
Notes
- gh prgh pr and gh issuegh issue don't work from cloud sessions (GitHub GraphQL is blocked); use gh api repos/...gh api repos/... or the GitHub website for PRs.
- Back up the keystore outside GitHub: losing it means users can't update the app in place.
- A debug APK build also runs on pull requests for quick testing.

## Development plan

v1.0 takes about 6–8 weeks of part-time work in six phases; each phase ends with a working APK from GitHub Actions.
Phase 0 proves the cloud build works before any camera code is written; phase 2's gate protects the core promise that the saved photo looks like the preview.
Deliverables per phase
- Phase 0 — retrocamretrocam repo, Gradle project with version catalog, build.ymlbuild.yml workflow, signing secrets, empty app APK.
- Phase 1 — CameraX preview and capture, MediaStore save, flash modes, orientation handling, front/back switch.
- Phase 2 — :filmengine:filmengine module, GL SurfaceProcessor, LUT loader, grain and vignette, off-screen renderer, 5 camera types, golden-image test.
- Phase 3 — flare, light leaks, halation, chromatic aberration, date stamp, timer, camera strip, settings sheet, DataStore.
- Phase 4 — LUT authoring script, all 20 v1.0 profiles and LUTs, camera-card illustrations.
- Phase 5 — baseline and startup profiles, R8 tuning, tests on your phone and a friend's phone from a different brand, performance check against targets, v1.0 GitHub Release.
- v1.1 — 5 more camera types, Disposable mode, re-develop, import from gallery.
How to work with Claude Code per phase
- Start each phase in a fresh Claude Code on the web session pointing at this PRD and the repo.
- Ask for a short plan first, then the code; push and let GitHub Actions build.
- Use a subagent only for an independent code review at the end of phases 2 and 5.

## Risks

Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Film looks feel generic or similar to each other | Core value lost | Build LUTs from reference photos per stock; side-by-side review of all 20 before release; cut weak ones |
| Preview and saved photo don't match | Users distrust the preview | One shared shader for both paths; automated pixel-diff test on sample images |
| Device camera quirks (Samsung, Xiaomi) | Crashes, wrong orientation | CameraX only, test on your phone and a friend's phone from a different brand, crash log export so friends can send bug reports |
| Full-res processing too slow on low-end phones | Long save times, memory errors | Process on GPU in tiles; fall back to 8 MP output under 3 GB RAM |
| Trademark complaints if the app is shared widely | Forced rename | Generic in-app names, no logos, no brand words in the listing |
| Losing the signing key | Can't ship updates | Keystore backed up in two places outside GitHub |
| Pro plan usage limits during heavy coding | Slower development | Small, focused sessions per phase; subagents only for reviews and log reading |



### Diagrams (described)

- **Image pipeline:** CameraX Preview (live frames) → Film shader (GPU) → Viewfinder (Compose, 30 fps). ImageCapture (full resolution) → same Film shader → JPEG + EXIF (orientation, date) → MediaStore (Pictures/RetroCam) → Thumbnail (opens the in-app gallery). Camera profile (JSON + LUT image) and Controls (camera type, toggles) feed the shader parameters for both paths.
- **Roadmap gates:** Phase 0 gate: signed APK from CI installs on phone. Phase 2 gate: saved photo matches the preview (golden-image test). Phase 5 gate: all performance targets met on the reference phone.
