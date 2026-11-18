# Security Policy

## Supported versions

Only the latest release receives security fixes.

## Reporting a vulnerability

Use GitHub private vulnerability reporting: open the **Security** tab of this repository and click **Report a vulnerability**.

- Do not open a public issue for security problems.
- Never include your WARP private key, account token or a full config. Remove the `PrivateKey` line before sharing anything.
- You will get a first response within 7 days. Confirmed issues are fixed in the next release and credited in the changelog unless you prefer otherwise.

## Scope

In scope:
- the WarpTuner Android app (storage of keys and configs, exported components, network requests);
- the build and release pipeline in `.github/workflows` (signing, published checksums, provenance).

Out of scope (report upstream):
- Cloudflare WARP services and the client API;
- the AmneziaWG tunnel library `com.zaneschepke:amneziawg-android`.

## Verifying releases

Every release built by CI publishes the APK SHA-256, the signing certificate fingerprint and a GitHub build provenance attestation. See [docs/VERIFY.md](docs/VERIFY.md).
