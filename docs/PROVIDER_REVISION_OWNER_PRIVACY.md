# Private ownership for remote medication revision metadata

The provider synchronization preference previously stored the owner key as a readable `<local SQLite row ID>:<authenticated account subject>` string. This key is used both to guard conditional provider revisions and to scope the latest provider update summary.

## Current behavior (proposed in PR)

- Compute a deterministic **versioned, domain-separated SHA-256 owner fingerprint** from the persisted patient ID and the authenticated account subject, using UTF-8 and NUL field separators.
- Persist only the `sha256-v2:<64 lowercase hex>` fingerprint alongside the remote revision in a single committed SharedPreferences edit. The associated summary uses the same opaque owner key.
- Reject unowned/malformed/old readable owner keys when deciding whether to reuse a conditional HTTP revision or display a cached provider summary. Legacy metadata therefore triggers an **unconditional full provider fetch** when the next authenticated update is attempted. It is **not** reinterpreted as another patient's identity.
- Reject attempts to write plaintext owner strings through `RemoteRevisionCommitter`.
- The hash is a **comparison fingerprint, not encryption**: it does not protect low-entropy account subjects from offline guessing. Android private storage and appropriate backup/privacy controls remain necessary.
- Do **not** silently remove existing stored revisions, medical history, prescriptions, or installed databases. Previously stored plaintext metadata may remain in old preferences or backups until rewritten by a verified update; a separate audited migration may handle proactive cleanup.

## Verification and release gates

Only synthetic account codes and patient records may be used. JVM tests check deterministic output, subject privacy, account isolation, legacy-key rejection, and write validation; existing real SQLite + SharedPreferences emulator smoke verifies two-patient isolation and safe full-refresh fallback across API 23, 33, 36 and 37.

Successful GitHub CI does not certify provider interoperability, backup/restore or clinical use. A manual nonproduction OAuth/FHIR regression, interruptions between SQLite COMMIT and preference write, full-refresh retry, and independent privacy review remain necessary before approving a release.
