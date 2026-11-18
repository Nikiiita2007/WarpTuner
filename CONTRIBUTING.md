# Contributing

Thanks for helping improve WarpTuner.

## Workflow

1. Create a branch from `main`: `feat/…`, `fix/…`, `refactor/…`, `test/…`, `docs/…`, `ci/…`.
2. Use Conventional Commit messages, for example `fix(watchdog): disconnect unverified candidates`.
3. Open a pull request. `main` is protected: every change goes through a PR, and the checks **Build & unit tests**, **Lint**, **Dependency review** and CodeQL must pass.
4. Add the `run-instrumented` label to a PR to also run emulator tests and the release smoke test (they always run on `main`).
5. Squash-merge; the branch is deleted automatically.

## Local checks

Requirements: JDK 17+ (CI uses 21), Android SDK with platform 37.

```bash
./gradlew testDebugUnitTest lintDebug     # unit tests + lint
./gradlew assembleDebug                   # debug APK
./gradlew connectedDebugAndroidTest       # instrumented tests, needs a device or emulator
./gradlew assembleRelease                 # minified, unsigned release APK
```

## Code guidelines

- Keep pure logic (config building, scoring, parsing) in plain Kotlin functions and cover it with JVM unit tests in `app/src/test`.
- Anything that needs Android (Keystore, services, ViewModel) goes to `app/src/androidTest`.
- Secrets (private keys, account JSON, configs) must go through `Store`, which encrypts them; never log them.
- New dependencies go to `gradle/libs.versions.toml`.

## Releases

Releases are built only by CI: bump `versionCode` / `versionName` in `app/build.gradle.kts`, update `CHANGELOG.md`, merge, then push a tag `vX.Y` matching `versionName`. The release workflow signs the APK and publishes checksums. Signing keys never live in the repository.
