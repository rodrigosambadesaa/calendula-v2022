# Calendula v2022 — modernization status

_Last reviewed: 2026-10-10. This is an unofficial development fork, not a released or medically validated product. Tests described below were verified on their respective PR revisions; do not interpret them as end-to-end certification._

## Verified build baseline

| Item | Main-branch configuration |
| --- | --- |
| Minimum supported Android | API 23 (Android 6.0) |
| Compile / target SDK | API 36 / API 36 |
| Android Gradle Plugin | 8.10.1 |
| Gradle wrapper | 8.14.6 |
| Kotlin | 2.1.21 |
| JDK | 17 |
| Android 17 runtime | Isolated API 37.0 smoke workflow (SDK compile/target remain API 36) |

Android CI assembles `ciDebug`, validates `minifyDevelopReleaseWithR8`,
executes JVM unit tests, builds the instrumentation APK and runs Android lint.
A separate GitHub Actions workflow **executes instrumented smoke tests on
emulators** for API 23 (Android 6), API 33 (Android 13) and API 36 (Android 16).
Another independent workflow now runs a focused smoke subset on API 37.0 (Android 17); it completed successfully on PR #569 before merging. The existing `compileSdk` and `targetSdk` remain at 36. See [Android 17 compatibility](ANDROID_17_COMPATIBILITY.md) and #570 for the separate targetSdk 37 migration.
The initial matrix and the subsequent boot, notification-permission, and SQLite
test branches have passed on all three APIs. The smoke tests are intentionally
narrow: they verify selected Android platform integration paths, **not** a
complete medication-administration or database-migration workflow. Lint passing
does not imply all legacy accessibility warnings have been resolved.

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
- Added GitHub Actions emulator tests for Android 6, 13 and 16, with on-device
  checks for component registration, secure network rules, receiver recovery,
  Android notification permissions and basic local database schema queries.
- Introduced an owner-only, explicit-opt-in automatic squash-merge workflow
  that now requires **three** green suites on the same immutable commit:
  Android CI, the API 23/33/36 emulator matrix and Android 17 API 37.0
  runtime smoke (extended by merged PR #572).

## Unresolved release blockers

### P0 — Prescription-database supply-chain integrity

The configured database service is still
`http://tec.citius.usc.es/calendula/dbs/`. A diagnostic probe dated
2026-09-29 recorded **HTTP 404 after following the historical redirect to
HTTPS** for `versions.json` (see issue #216). The endpoint is not presently
a viable production source. This is a separate availability problem from the
fact that a version manifest or archive delivered over plain HTTP is
**unauthenticated**. VPN-aware network checks, calendar-date validation,
redirect checks, and ZIP size/traversal defenses improve robustness but
**do not authenticate medical content**. The AEMPS importer executes SQL
contained in the retrieved archive; consequently this is a critical release
gate, not merely a missing software update feature.

**Interim safety policy:** until the above trust requirements are met,
`LegacyRemoteArchivePolicy` blocks the historical unsigned remote SQL
manifest/download/install flow before any network request or archive setup.
This is intentionally **fail-closed**: it means remote prescription database
installation and updates are unavailable, rather than executing potentially
attacker-modified SQL. Previously installed database files and explicit local
setup inputs are not deleted by this policy. This is a temporary release
safety measure, **not** a CIMA importer or a replacement medical catalog.
Its removal requires verified source authenticity, replay/downgrade defenses
and representative end-to-end migration tests.

**Acceptance criteria:** verify a working HTTPS endpoint under trusted
certificate validation; ensure every manifest/archive request stays on an
approved origin or safe equivalent; authenticate archive bytes using an
independently trusted signature or audited digest, with a defined signing
key/manifest trust model; reject unverified or partial archives before
installation; test tampering, redirect and rollback scenarios.
Do not silently change the server URL or delete old user data until
availability and migration behavior are verified.

### P0 — Preserve medical records across schema upgrades

The legacy database helper previously handled an upgrade exception by calling
`dropAndCreateAllTables()`, which erased patient, medicine, schedule and
reminder records. An unsupported downgrade is also rejected rather than being silently accepted.
A failed schema migration must now **propagate the error**
and allow the SQLiteOpenHelper transaction to roll back, never destroy user
data as a fallback. Isolated regression tests deliberately exercise failed
v1/v2 upgrade paths while keeping a synthetic patient row.

**Acceptance criteria:** assemble representative pre-upgrade database fixtures
for each historical supported version and validate successful upgrade,
interrupted upgrade, rollback, restart and referential integrity. Test the
real upgrade journey on Android emulators and supported physical devices.
The removal of destructive fallback is only one prerequisite, **not proof**
that every historic database version migrates correctly.

### P0 — Functional testing on actual Android runtimes

The API 23/33/36 emulator matrix now runs
`connectedCiDebugAndroidTest` for focused installation/integration checks.
Passing tests have exercised application installation, manifest privacy,
notification channels and permissions, connectivity, local SQLite initialization,
and boot/package-update receiver callbacks. This **does not yet demonstrate**
actual delivery of medication reminders after device reboot or Doze, permission
prompt journeys, legacy-to-current database migration, OAuth/FHIR interoperability,
or prescription archive authenticity.

**Acceptance criteria:** add isolated end-to-end tests with safe synthetic
fixtures across supported API levels and API 36. Verify actual alarm delivery
(including Doze, reboot, permission revocation and fallback), notification
permission requests, background service restrictions, VPN-only states,
rotations, process death, database installation/migration, and representative
OAuth/FHIR flows. Never use live patient records in CI.

### P1 — Android API 36/37 behavioral and UX audit

- Validate predictive back/navigation and foreground service semantics.
- Test Android 17 runtime behavior (API 37) and audit its targetSdk 37 changes
  independently; do not confuse an emulator smoke pass with a targetSdk migration.
  See issue #570 and [the Android 17 validation plan](ANDROID_17_COMPATIBILITY.md).
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
