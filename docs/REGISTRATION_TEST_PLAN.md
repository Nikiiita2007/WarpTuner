# WARP registration verification

Related issue: #20. This plan does not assert that direct registration is fixed.

## Current baseline

Issue #20 reports default-route registration failing during connect on Wi-Fi and mobile data, with timeout or io classifications. These observations do not establish whether DNS, TCP, TLS, or network filtering is responsible. The route=direct label denotes default routing, not a verified physical-network binding.

## Automated checks

- Run build, JVM unit tests and lint.
- Run the existing Instrumented tests job with the run-instrumented PR label; its workflow also invokes the release smoke test.
- Transport JVM tests verify socket timeout settings, HTTPS-only URLs, disabled redirects and unchanged TLS verifier/socket factory.
- Entry-point cancellation tests cover failure before the first registration attempt only.
- Existing emulator tests and smoke tests do not demonstrate successful WARP registration on the affected network.

## Device acceptance matrix

Use a test device/profile. Never delete the only working account or configuration merely to test first launch. Keep private backups out of issues and logs.

1. On a clean test profile without a saved configuration or third-party VPN, attempt registration on Wi-Fi and then mobile data. Record success or sanitized failure fields and elapsed time.
2. With a saved working configuration and no active tunnel, reproduce direct failure and test saved-tunnel fallback. Verify the actual route and final VPN state.
3. With a user tunnel already active, test registration only after tunnel ownership/restoration is implemented; this scenario is currently a merge-review concern, not a guaranteed behavior.
4. Cancel the registration coroutine before starting, between attempts, and during a stalled request using a test harness. Verify that cancellation is not presented as a normal registration error. There is not yet a dedicated UI cancellation action in this change.
5. Simulate HTTP rejection, malformed JSON, TLS validation failure, and secure-storage failure. Verify these do not qualify for saved-tunnel fallback.
6. Verify no raw configuration, private key, bearer token, account response, or authorization header is logged.

## Outstanding implementation and tests

- An overall registration deadline shared across API variants and follow-up requests; do not describe socket timeouts as this deadline.
- Cancellation of active network work, including bounded worker/resource usage and stale-result rejection. Merely timing out the caller is insufficient.
- Cancellation checks between POST and follow-up GET and before persistent side effects.
- Injectable request execution and deterministic tests for mid-request cancellation, fallback classification and deadline races.
- Exclusive tunnel ownership, coordination with tuning/watchdog/manual actions, and previous-state restoration.
- Account/config persistence consistency; the current writes are separate and not atomic.

## Evidence to attach to the PR

Record commit SHA, CI job results, device/Android version, network type, whether an external VPN or saved configuration was present, observed final tunnel state, and sanitized WarpRegistration fields: route, stage, kind, status, phase, elapsed_ms and detail. Do not infer a confirmed API block solely from a timeout.
