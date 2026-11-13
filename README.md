# RetroCam

<img src="docs/icon-512.png" width="96" align="right" alt="RetroCam icon" />

A one-screen Android camera that makes every photo look like it came from a disposable or
point-and-shoot film camera. One GPU shader drives both the live preview and the saved
photo. No watermark, no ads, no analytics, no network permission.

Status: Phase 0 scaffold. Public APIs are stubs; see `docs/ARCHITECTURE.md`.

## Modules

| Module | Package | Role |
|---|---|---|
| `:app` | `com.retrocam` | Compose UI, view model, settings, capture orchestration |
| `:camera` | `com.retrocam.camera` | CameraX binding, capture, orientation, saving |
| `:filmengine` | `com.retrocam.filmengine` | GL shader, LUTs, off-screen renderer, frames, date stamp |
| `:profiles` | `com.retrocam.profiles` | Camera-type JSON + LUTs, params mapper, card art |

`:app` depends on the other three; `:profiles` depends on `:filmengine`.

## Build locally

Requires JDK 21 and the Android SDK (platform 36, build-tools 36.0.0).

```sh
export ANDROID_HOME=/path/to/android-sdk   # or put sdk.dir=... in local.properties
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew assembleRelease                  # unsigned unless signing env vars are set
```

Release signing reads `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Docs

- [Product requirements](docs/PRD.md)
- [Architecture and module contracts](docs/ARCHITECTURE.md)
