# Replacing the retired medicine archive with official AEMPS CIMA data

Status: exploratory, read-only implementation foundation, **not an enabled importer**.
Reviewed 2026-10-08.

## Evidence and official sources

- AEMPS [CIMA Nomenclátor de prescripción](https://cima.aemps.es/cima/publico/nomenclator.html)
  advertises open medicine datasets, Excel downloads and REST services.
- The public AEMPS REST endpoint
  `https://cima.aemps.es/cima/rest/medicamento?nregistro=51347`
  responded over HTTPS with structured JSON on 2026-10-08.
  This response contains fields such as `nregistro`, `nombre`, `dosis`
  and `presentaciones[].cn`.
- The historical Calendula service `http://tec.citius.usc.es/calendula/dbs/`
  no longer serves its expected `versions.json` (issue #216).

## Why a URL replacement is insufficient

The existing AEMPS installer expects a legacy archive with SQL statements.
CIMA REST provides JSON whose medication, presentation, active-ingredient and
package relationships must be reconciled with Caléndula's ORMLite schema.
Passing JSON bytes to the legacy SQL installer would be incorrect and unsafe.
Do not manufacture an unauthenticated SQL archive from these results.

## Implemented foundation

`CimaRestCatalog` constructs a constrained official HTTPS detail URL and
validates a bounded JSON response into an immutable **non-persisted**
`MedicineSnapshot`. It preserves the distinction between one registration
number and potentially multiple national presentation codes. It performs
**no network request, SQL execution, medicine import, dosage recommendation,
prescription linkage or patient-data mutation**.

## Release-blocking next steps

1. Validate AEMPS data licensing, attribution, update cadence and rate limits.
2. Model registration, presentation and active-ingredient relationships
   against existing Caléndula data without dropping historical identifiers.
3. Support incremental updates with source timestamps, rollback and
   transactional imports; never execute remote SQL.
4. Validate certificates and the official origin; define acceptable cache
   authenticity and anti-rollback rules for offline datasets.
5. Test parsing, conflict resolution, upgrades and rollback against synthetic
   medicine catalog fixtures, including interrupted downloads, application
   process death and database migrations.
6. Test actual on-device end-to-end medicine selection and notification
   delivery on API 23, 33, 36, including offline behavior.
7. Disable or clearly deprecate the retired backend only when a verified
   replacement is ready, with a user-visible migration path.

Until the above are complete, this read-only parsing support **must not**
be interpreted as a clinical or production release.
