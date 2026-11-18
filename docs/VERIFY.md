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

## 3. What the app can do

- Permissions are declared in `app/src/main/AndroidManifest.xml`.
- Network destinations are listed in the README section "Network requests and privacy".
- The full source is in this repository; you can build the same app yourself (see README, "Build and run").

## 4. Installing

Android asks you to allow installation from the app you opened the file with (browser, Telegram, file manager). Google Play Protect may warn because the app is not distributed through Google Play. Revoke the permission afterwards if you no longer need it.

## Reporting problems

Open an issue and describe the device, Android version and what you did. Never post your WARP private key or full config.
