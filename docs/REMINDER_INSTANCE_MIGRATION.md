# Reminder-instance identity migration — staging only

**Status:** draft foundational change for P0 #956. **NOT a fix for reminder delivery/cancellation by itself.** Do not release until the receiver, scheduler, legacy-token retirement, notifications and all cleanup paths are integrated and validated.

## Persistent schema

Database version 4 adds `EventReminders.AlarmInstance`, an opaque randomly generated UUID per persisted reminder row, with a unique index. This value must not depend on the SQLite row ID, a patient name/code, medication details, original/next trigger timestamps, or mutable delay/auto-repeat metadata. No clinical identifiers are written to its string representation.

Existing v1/v2/v3 databases upgrade in-place through a SQLiteOpenHelper transaction. The v4 upgrade backfills every existing reminder; a failed row UPDATE rolls back **all** new tokens, the ALTER TABLE and schema version. The Android API 23/33/36/37 disposable migrations include rollback injection and preserved-row tests. Schema downgrades remain unsupported and fail closed; never reset the database.

New `EventReminder` Java objects receive an instance UUID at construction; loading existing rows uses the persisted value. An external restore of old, malformed or NULL values must not be trusted for alarm delivery: the future scheduler/receiver change must reject them, or migrate them transactionally before scheduling.

## Required follow-up before any release

- Form the platform PendingIntent's **identity-bearing URI** from the persisted instance UUID, alongside numeric ID for lookup; include and check it in receiver -> background-job delivery. Extras alone are not part of PendingIntent equality and are insufficient.
- Cancel only the **captured old reminder instance** after commit, never a new row with a reused numeric ID. Avoid cancelling ID-only legacy tokens after a concurrent replacement is scheduled.
- Reject existing ID-only legacy/in-flight broadcasts before any medication is shown, changed, confirmed or postponed. Re-arm valid live reminders with their migrated instance IDs on startup/reboot, without duplicate or lost notifications.
- Enforce current persisted instance matching in snooze/auto-repeat and any stale-object cleanup path, with deterministic race and post-COMMIT crash injection.
- Validate real AlarmManager and SQLite on disposable API 23/33/36/37 emulators and physical devices; independent privacy/clinical review is mandatory. This schema PR must remain separate and draft while #955 is under review.

Related: #956, #952, #955, #944, #946.

## Stacked platform delivery work (draft branch)

A second branch stacked on the v4 schema adds the canonical random UUID to the
identity-bearing Android reminder broadcast URI and to the service queue. Both
receiver and queued service reject malformed/missing UUIDs and a mismatched URI
without querying medication records; Agenda re-reads SQLite and rejects a
different persisted instance before handling a reminder. Cancellation uses the
captured instance token rather than looking up a new row by its reusable ID,
and retires pre-v4 numeric URI tokens separately. Synthetic JVM and on-device
PendingIntent/SQLite regressions cover equal numeric IDs with distinct UUIDs,
delayed cleanup, forged URI/extras and stale prior-patient broadcasts.

**This is not a clinical release gate pass.** The following remain open:

- Integrate the platform change with #955 (patient erasure, owner binding and
  pruning); all 3 Actions workflows must be green on the *combined* final head.
- Update all notification-button PendingIntent identities and queued actions
  to bind the exact immutable instance, retaining clinical consent snapshots.
- Review every stale-object deletion, cleanup, auto-repeat, backup/restore
  and multi-process race with deterministic interleaving; a transaction must
  guard any SQL row deletion against ID reuse.
- Reschedule migrated reminders safely after upgrades, including overdue alarms
  and legacy tokens already in flight; validate reboot, Doze, permission changes,
  process death and stock/inventory behavior.
- Run physical-device clinical/privacy acceptance. Tests use disposable,
  synthetic patients only and cannot establish production safety on their own.
