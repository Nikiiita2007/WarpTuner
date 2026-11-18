# Verifying a download

Run these checks before installing an APK that you did not build yourself. The expected values are published in the notes of each [release](https://github.com/Nikiiita2007/WarpTuner/releases).

## 1. File checksum (SHA-256)

Windows (PowerShell):

```
Get-FileHash .\WarpTuner-<version>.apk -Algorithm SHA256
```

Linux / macOS:

```
sha256sum WarpTuner-<version>.apk
```

The result must match the SHA-256 in the release notes. If it does not, do not install the file.

## 2. Signing certificate

`apksigner` ships with the Android SDK build tools (folder `build-tools/<version>`):

```
apksigner verify --print-certs WarpTuner-<version>.apk
```

Compare the line `Signer #1 certificate SHA-256 digest` with the fingerprint in the release notes. It stays the same for every version signed by the author; if it changes, Android will also refuse to update an installed copy.

## 3. Build provenance (releases built by CI)

Releases from 3.1 on are built by GitHub Actions, which publishes a signed provenance attestation. With the [GitHub CLI](https://cli.github.com/):

```
gh attestation verify WarpTuner-<version>.apk -R Nikiiita2007/WarpTuner
```

A successful result proves the APK was built from this repository by the `Release` workflow, not on someone's computer.

## 4. What the app can do

- Permissions are declared in `app/src/main/AndroidManifest.xml`.
- Network destinations are listed in the README section "Network requests and privacy".
- The full source is in this repository; you can build the same app yourself (see README, "Build and run").

## 5. Installing

Android asks you to allow installation from the app you opened the file with (browser, Telegram, file manager). Google Play Protect may warn because the app is not distributed through Google Play. Revoke the permission afterwards if you no longer need it.

## Reporting problems

Security problems: use private reporting (Security tab → Report a vulnerability), see [SECURITY.md](../SECURITY.md). For other problems open an issue and describe the device, Android version and what you did. Never post your WARP private key or full config.
