# WarpTuner

[![Android CI](https://github.com/Nikiiita2007/WarpTuner/actions/workflows/android.yml/badge.svg)](https://github.com/Nikiiita2007/WarpTuner/actions/workflows/android.yml)

Android app (Kotlin, Jetpack Compose) that registers a free Cloudflare WARP device and **tunes AmneziaWG obfuscation parameters and endpoints for the user's own network**.

## Why this project exists

In some networks a plain WARP/WireGuard handshake is throttled or blocked. AmneziaWG adds junk-packet obfuscation (`Jc`, `Jmin`, `Jmax`), but the values that work depend on the ISP and change over time. Trying them by hand is slow and noisy: Wi-Fi speed drifts between runs, so "it feels faster" proves nothing. WarpTuner automates registration, parameter search and verification, and keeps the tunnel healthy afterwards. It was built to solve this problem for the author and friends.

## Features

- Registers a free WARP device through Cloudflare's client API; if the API is unreachable, retries through an already saved config.
- Background tuning (foreground service, notification with a Stop button) that survives minimizing the app; the last result is persisted.
- Search over `Jc/Jmin/Jmax` (random sampling, then mutation of the best candidates) with a repeated measurement of the finalists and of the user's current config.
- Endpoint comparison with paired measurements (reference config vs. candidate, same round) to cancel Wi-Fi drift.
- Tunnel watchdog: checks `warp=on` periodically, reconnects, then falls back to reserve configs from the last tuning.
- Per-app split tunneling (exclude apps or include only selected apps).
- Config import from clipboard, `.conf` file or QR code; first-run onboarding; battery-optimization hint.
- Tuning modes: fast, normal, thorough.

## Screenshots

<!-- TODO: add screenshots to docs/screenshots and reference them here -->

## Architecture

```mermaid
flowchart LR
  UI[MainActivity / Compose screens] --> Store[(Store: SharedPreferences)]
  UI --> TuneService
  UI --> WatchdogService
  UI --> Engine
  UI --> WarpApi[WarpApi: registration]
  TuneService --> Tuner --> Prober
  Tuner --> Engine
  WatchdogService --> Engine
  WatchdogService --> Prober
  Engine --> GoBackend[AmneziaWG GoBackend]
  TuneService --> TuneState((TuneState StateFlow))
  TuneState --> UI
```

| Part | Responsibility |
|---|---|
| `MainActivity.kt` | Compose screens, tunnel `Engine` wrapper, config helpers (currently one file, see Roadmap) |
| `Tuner.kt` | `Prober` (trace, site reachability, speed, scoring) and `Tuner` (search strategy) |
| `TuneService.kt` | Foreground service that runs a tuning session and publishes `TuneState` |
| `WatchdogService.kt` | Foreground service that monitors the tunnel and fails over |
| `TuneState.kt` | Shared UI state and JSON (de)serialization of results |
| `Warp.kt` | `Store` (preferences), `WarpApi` (registration), account creation |

## How candidates are scored

`score = 100 * share_of_reachable_sites * (0.4 * min(speed, 50) / 50 + 0.3 * (1 - rtt / 1000) + 0.3 * (1 - loss))`

Speed is a short download through the tunnel, RTT is the median of repeated `trace` requests, loss is the share of failed requests, and reachability covers YouTube, Telegram and Telegram Web. Finalists and the user's current config are measured twice and averaged, because single measurements fluctuate a lot.

## Build and run

Requirements: Android Studio (recent stable), JDK 17, an Android device or emulator with Google Play services for QR scanning.

1. Clone the repository and open it in Android Studio.
2. Let Gradle sync finish (see `app/build.gradle.kts` for dependencies and `minSdk`).
3. Run the `app` configuration on a device (USB debugging) or build a debug APK with `./gradlew assembleDebug`.
4. Run unit tests with `./gradlew testDebugUnitTest`.

Release signing keys are never stored in the repository.

<!-- TODO: describe where the AmneziaWG tunnel library comes from in this project (Maven coordinates or local module) and its license. -->

## Network requests and privacy

The app talks only to: `api.cloudflareclient.com` (registration), `www.cloudflare.com/cdn-cgi/trace`, `speed.cloudflare.com`, `www.youtube.com`, `api.telegram.org` and `web.telegram.org` (reachability checks). There is no analytics, advertising or crash-reporting SDK. The WARP private key is stored in the app's private storage on the device.

## Design decisions and trade-offs

- **One tunnel at a time.** Android allows a single active VPN, so candidates are measured sequentially. Speed is gained by cheaper measurements and fewer candidates, not by parallelism.
- **Paired measurements instead of absolute numbers.** Absolute speed depends on the moment; the ratio to a reference measured in the same round is much more stable.
- **No automatic re-tuning.** The watchdog only reconnects or switches to reserve configs; a full tuning drops the tunnel and uses traffic, so the user decides.
- **Foreground services.** Long tuning and monitoring need to survive backgrounding; this costs a persistent notification.

## Known limitations

- Registration relies on Cloudflare's unofficial client API and may stop working at any time.
- The `vpn://` import format of the Amnezia client is not supported, only plain `.conf` text.
- The QR scanner depends on Google Play services.

## Roadmap

- [ ] Split `MainActivity.kt`, introduce ViewModels and a data layer, add dependency injection
- [ ] Unit tests for pure functions (`applyEndpoint`, `applySplit`, `tryImport`, `outcomeFromJson`, `readParams`)
- [ ] Tuning history with network type, diagnostic log export
- [ ] Encrypted storage of the private key, backup exclusion
- [ ] Update check, signed release with published SHA-256 and signing certificate fingerprint

## Disclaimer

Unofficial project, not affiliated with Cloudflare or the Amnezia project. Check the terms of the services you use.
