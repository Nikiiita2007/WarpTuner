# Changelog

All notable changes to this project are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow `versionName` in `app/build.gradle.kts`.

## [Unreleased]

### Security
- WARP private key, account and configs are encrypted at rest with an Android Keystore AES-256-GCM key; existing plaintext values are migrated on first read.
- Copied configs are marked as sensitive on Android 13+ and the clipboard is cleared after 60 seconds.
- Explicit network security config: no cleartext traffic, user-installed CAs not trusted.
- Backup and device-transfer rules exclude all app data.
- Release builds are minified with R8 (APK 47 MB → 37 MB); debug logs are stripped.

### Fixed
- `Engine` created the tunnel backend without synchronization, so the tuning service, the watchdog and the UI could race and create two backends for one tunnel.
- Config validation before saving and applying settings (#3).
- Watchdog disconnects unverified recovery candidates (#3).
- Imported per-app rules are cleared when split tunneling is off (#3).

### Changed
- CI: lint, instrumented tests on an emulator, release smoke test, dependency review, Gradle dependency submission, pinned action SHAs.
- Releases are built, signed and published by CI with SHA-256, certificate fingerprint and build provenance.
- Dependencies moved to the version catalog and updated: Kotlin 2.4.21, Compose BOM 2026.09.00, core-ktx 1.19.1, lifecycle 2.11.0, activity-compose 1.13.0, androidx.test junit 1.3.0, espresso 3.7.0, org.json 20260814, Gradle 9.9.1.
- Build tooling classpath pinned to patched Bouncy Castle 1.87, Guava 33.7.2, jose4j 0.9.7, JDOM 2.0.6.1, Commons Lang 3.22.0 and HttpClient 4.5.14 (Dependabot alerts from AGP/lint transitive dependencies; none of them ship in the APK).
- Replaced deprecated `LinearProgressIndicator(progress: Float)` overload.
- Community files: SECURITY, CONTRIBUTING, CODE_OF_CONDUCT, issue and PR templates, CODEOWNERS.
- `gradlew` is stored as executable.

## [3.0] - 2026-11-19

### Added
- Local storage of the last 20 completed tuning sessions (no history screen yet).
- New WT launcher icon, edge-to-edge layout with max content width.

### Changed
- Main and settings screens moved to `MainViewModel`; screens split into separate files; preferences injected through a ViewModel factory.

## [2.1] - 2026-11-18

### Added
- Tuning modes: fast, normal, thorough; faster probing.
- Unit tests for `Tuner`, `Prober.score`, result JSON and config helpers.
- README, VERIFY guide, MIT license.

### Changed
- Android backup of app data disabled.

[Unreleased]: https://github.com/Nikiiita2007/WarpTuner/compare/v3.0...HEAD
[3.0]: https://github.com/Nikiiita2007/WarpTuner/compare/v2.1...v3.0
[2.1]: https://github.com/Nikiiita2007/WarpTuner/releases/tag/v2.1
