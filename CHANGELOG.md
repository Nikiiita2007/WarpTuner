# Changelog

All notable changes to this project are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow `versionName` in `app/build.gradle.kts`.

## [Unreleased]

### Fixed
- Config validation before saving and applying settings (#3).
- Watchdog disconnects unverified recovery candidates (#3).
- Imported per-app rules are cleared when split tunneling is off (#3).

### Changed
- CI: lint, instrumented tests on an emulator, release smoke test, dependency review, Gradle dependency submission, pinned action SHAs.
- Releases are built, signed and published by CI with SHA-256, certificate fingerprint and build provenance.
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
