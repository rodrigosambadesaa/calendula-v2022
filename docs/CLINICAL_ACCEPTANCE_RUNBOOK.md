# Clinical data and Android release-acceptance runbook

**Status: NOT APPROVED FOR CLINICAL USE.** This unofficial Calendula fork is a development project. A green build or emulator suite is **not** proof that medication schedules, dosage decisions, dispensing records, or reminders are safe in a real patient workflow.

This runbook defines the evidence required before deciding whether the first **technical prerelease** can be published. It does not grant permission to use the application for real treatment decisions. See [Modernization status](MODERNIZATION_STATUS.md), [Android 17 compatibility](ANDROID_17_COMPATIBILITY.md), and the related issues (#216, #525, #552, #570, #944, #946, #956, #961).

## 1. Freeze a reproducible candidate

Record the exact source **commit SHA**, the base branch, the application `versionCode`/`versionName`, dependency lock/verification status where applicable, signed APK/AAB SHA-256, build environment, and signing-key custody (never the key itself). Do not substitute green checks from an earlier commit or a different merge result.

- [ ] Confirm the candidate contains the intended PRs, without double-merging overlapping draft branches.
- [ ] Confirm the candidate has no outstanding unresolved release-blocking security, privacy or clinical-integrity defect.
- [ ] Verify **Android CI** is successful on the frozen SHA (compile, unit tests, R8, lint, instrumentation APK).
- [ ] Verify **Android emulator smoke (API 23/33/36)** is successful on the same SHA.
- [ ] Verify **Android 17 runtime (API 37.0)** is successful on the same SHA. Runtime success does **not** certify a future `targetSdk 37` migration.
- [ ] Retest the *combined* `main` merge commit. An independently green feature branch is not enough.
- [ ] Keep test-result files, failure logs (redacted), toolchain versions and build artifacts linked to the candidate.

A cancelled, missing, skipped or partial workflow is **not** a pass. If any source or dependency changes, invalidate the evidence for the old SHA and rerun the relevant checks.

## 2. Test-data and privacy rules

Use only disposable, invented profiles, drug descriptions, schedule times and provider fixtures. Never insert real patient records, real healthcare credentials, patient identifiers, prescriptions, or unredacted tokens in GitHub Actions, issues, screenshots or logs. Never test destructive scenarios on a production phone or production database.

Create at least these independently labeled synthetic fixtures:

| Fixture | Scenario |
| --- | --- |
| Patient A | Durable nonblank local owner code; one medicine, stock, schedule, allergy and reminders |
| Patient B | Different durable owner code and otherwise comparable medication/times |
| Recycled ID | Different durable owner code deliberately assigned A's former numeric SQLite ID in a disposable database |
| Legacy row | Missing stable owner code, requiring explicit migration and reselection before clinical actions |
| Corrupt link | Cross-patient alert, official treatment or schedule reference without valid ownership |
| Missing instance | Deleted/recreated reminder row with original numeric ID but a different immutable alarm UUID |

**Invariants:** a missing or mismatched owner must fail closed without leaking names, doses or private schedule details. A rejected action must never mutate another patient's medicine, intake history, stock, alerts or OS alarms. An SQL rollback must leave the original data intact.

## 3. Manual clinical and SQLite acceptance matrix

Mark each item PASS, FAIL or NOT RUN with a device/database fixture, precise test steps, expected and actual result, and evidence link. A PASS requires someone independent of the implementation to review the outcome.

| Area | Minimum acceptance scenarios |
| --- | --- |
| Patient selection and restored state | Switch A→B with editor, routine list, schedule list, home agenda and medicine details open; process death; recreated saved-state `Bundle`; recycled ID; malformed/legacy preference |
| Medication CRUD | Create/edit/delete only with valid A; reject stale A after selecting or restoring B; delayed callback; invalid and missing IDs; duplicate drug/medicine-ID reuse |
| Treatment and stock | Confirm, delay and cancel a synthetic dose; double tap and concurrent operations; shared stock; rollback/SQL exception; no duplicate completion or negative stock |
| Prescription and provider identity | Official FHIR treatment, dispensation and asynchronous results must be bound to selected patient **and** exact treatment/medicine revision; no cross-owner source display |
| Alerts and allergies | Read and update only matching stable owner; cross-owner/malformed rows abort; HIGH allergy warnings are not lost on a failed medication update; blocked schedules remain consistent |
| Profile erasure | Remove A atomically, preserving B's medicines, alerts, pickups, routines, intake history and alarms; trigger SQL failure halfway and verify full rollback |
| Database migration | Restore representative legacy schema versions through v4 into disposable databases; preserve all committed medication and dose history; roll back deliberately failed migrations |
| Backup and recovery | Restore an old backup with recycled row IDs; no stale UI, PendingIntent or active selection is authorized for the successor |
| Alarm identity | Schedule, reschedule, deliver and cancel immutable UUID-backed reminder; reject stale ID/UUID, notification URI, displayed-dose fingerprint, and queued job replay |
| Restart and clock | App process death; force stop; reboot; timezone/DST and wall-clock changes; Doze; permission removal/regrant; no missed, duplicate or cross-patient deliveries |
| External catalog | The retired unsigned archive remains blocked. A replacement catalog must have authenticated source, integrity verification and a migration test before enabling installation |
| Accessibility and UX | Large fonts, screen reader, Spanish/Galician strings, navigation and notification visibility; Android 6 and 17 plus at least one tablet/large-screen form factor |

Any FAIL in ownership, dose/stock integrity, deletion atomicity, alarm isolation or migration is a release blocker, even when the app builds.

## 4. Representative physical-device verification

Emulator smoke tests are necessary but insufficient. On disposable installations, independently exercise the full notification and medication flows on representative real devices at least across an older supported Android version and a recent version. Specifically document exact-alarm permission availability, notification permission, battery optimization/Doze, reboot recovery, background restrictions, OS notification actions and process-death restoration.

For Android 17, distinguish **running on API 37** from **targeting API 37**; do not claim the new target behavior has been certified while `compileSdk`/`targetSdk` remain at 36.

If the test uses a healthcare-provider integration, use only authorized non-production accounts/fixtures and independently verify the consent and data boundary. Never claim a working FHIR/OAuth clinical integration on the basis of synthetic UI responses alone.

## 5. Release decision and sign-off

Do not close an issue solely because an associated pull request compiled or passed tests. Require explicit decisions for the clinical/privacy blockers and document any intentionally disabled feature.

| Evidence field | Record |
| --- | --- |
| Candidate SHA and source branch | |
| APK/AAB hash, version and signing provenance | |
| Android CI / emulator 23,33,36 / Android 17 API37 URLs | |
| Manual tester(s), reviewer(s), dates and test devices | |
| Synthetic backup/schema fixture IDs | |
| Cases PASS / FAIL / NOT RUN and evidence links | |
| Open P0/P1 defects and mitigation/feature-disable decisions | |
| Independent security/privacy review and approval | |
| Final technical prerelease decision (GO / NO-GO) | |

**Default decision is NO-GO** while critical evidence is missing, tests fail, or the retired medication catalog has no trusted replacement for the intended feature set. If a limited technical prerelease is eventually published, it must clearly state that it is an experimental build **not clinically validated**, identify disabled/unsupported features, and provide a safe migration/rollback plan. Never publish with real patient data included.
