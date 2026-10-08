# Calendula v2022 — modernization status

_Last reviewed: 2026-10-08. This is an unofficial development fork, not a released medical product._

## Verified build baseline

| Item | Main-branch configuration |
| --- | --- |
| Minimum supported Android | API 23 (Android 6.0) |
| Compile / target SDK | API 36 / API 36 |
| Android Gradle Plugin | 8.10.1 |
| Gradle wrapper | 8.14.6 |
| Kotlin | 2.1.21 |
| JDK | 17 |

The Android CI workflow currently assembles the `ciDebug` APK, runs
`minifyDevelopReleaseWithR8`, executes JVM unit tests, assembles the Android
instrumentation-test APK, and runs Android lint. These steps have passed on
`main` as of this review. **It does not execute on-device instrumentation
tests.** Lint may pass despite warnings and narrowly documented suppressions.

## Accomplished

- Preserved the public 2022 project as an explicitly unofficial GitHub fork.
- Migrated the compile/target SDK to API 36 and the minimum SDK to API 23.
- Updated core Android and build tooling incrementally with CI regression checks.
- Added VPN-aware connectivity/backend preflight and integration tests for the
  network access utilities.
- Improved handling of HTTP redirects and rejected HTTPS-to-HTTP downgrades.
- Moved the prescription-database download and setup work off the UI thread,
  using a foreground service and controlled download helper.
- Added secure-window/WebView and network-related hardening in targeted changes.
- Addressed multiple layout collision warnings and RecyclerView refresh
  inefficiencies, while documenting intentional legacy visual behavior.

## Unresolved release blockers

### P0 — Prescription-database supply-chain integrity

The configured database service is still
`http://tec.citius.usc.es/calendula/dbs/`, and the app explicitly allows
cleartext traffic to that host in its network-security configuration.
A version manifest or prescription archive delivered over plain HTTP is
**not authenticated**, regardless of DNS/network preflight. Correctly checking
that a connection exists does not establish that its contents are genuine.
A separate change validates that version strings are safe calendar-date path
components; this is useful defense-in-depth **but does not solve authenticity**.

**Acceptance criteria:** verify a working HTTPS endpoint under trusted
certificate validation; ensure every manifest/archive request stays on an
approved origin or safe equivalent; authenticate archive bytes using an
independently trusted signature or audited digest, with a defined signing
key/manifest trust model; reject unverified or partial archives before
installation; test tampering, redirect and rollback scenarios.
Do not silently change the server URL or delete old user data until
availability and migration behavior are verified.

### P0 — Functional testing on actual Android runtimes

CI compiles the instrumentation-test APK but does not call
`connectedCiDebugAndroidTest` or run an emulator/device. Unit tests and an
R8 compilation do not prove that alarms fire, databases install, notification
permissions work, or OAuth/FHIR flows succeed.

**Acceptance criteria:** run a focused emulator smoke suite, then full
instrumentation/functional tests with safe fixtures, across supported API
levels and API 36. Verify reboot, Doze, exact-alarm permissions, notification
permissions, background service restrictions, VPN-only states, rotations,
process death, and data migration. Avoid using live patient data in CI.

### P1 — Android API 36 behavioral and UX audit

- Validate predictive back/navigation and foreground service semantics.
- Verify display insets, edge-to-edge layout and large-screen adaptation.
- Review right-to-left/localization and accessibility; some visual lint
  exceptions intentionally preserve behavior rather than establish
  accessibility correctness.
- Document each intentional lint exception and revisit it after UI review.

### P1 — Legacy dependency and architecture migration

Audit and replace unsupported or fragile integration points incrementally,
with behavior-preserving tests: legacy job scheduling, ORM/data migration
paths, view-binding, adapter/navigation libraries, and remaining
`IntentService` usage. Do not blindly bump major dependency versions.

### P1 — Identity, privacy and release readiness

Test authenticated flows against dedicated nonproduction providers,
validate token lifecycle, ensure no personally identifiable medical data
enters logs/analytics, review data backup/storage and app signing, and
define a separate versioning/release pipeline. Existing links to public
store releases refer to the historical upstream application, not this fork.

## Workflow for each next improvement

1. Make one bounded behavioral change on a branch and record the
   compatibility/security rationale.
2. Add failure-path tests before removing old code.
3. Run the existing CI, inspect lint and test reports, and review the diff.
4. Exercise affected behavior on an emulator/real device before claiming
   production readiness.
5. Merge only after validation, then update this status as work evolves.

**Definition of done:** the application is not considered fully modernized
until the P0 release blockers are solved, the P1 areas have documented
dispositions, and end-to-end tests demonstrate safe behavior with
representative Android devices and services.
