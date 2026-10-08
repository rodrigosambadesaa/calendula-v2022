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

`CimaRestCatalog` constructs a constrained HTTPS detail URL and validates a
bounded JSON response into an immutable, **non-persisted** `MedicineSnapshot`.
Its two-argument parser requires the returned registration to match the
requested identifier. The model distinguishes one medicine registration from
multiple national presentation codes and makes no dosage inference.

`CimaRestClient.fetchMedicine(context, registration)` is a **read-only HTTPS
client**. It uses the existing VPN-aware backend preflight, Android's standard
certificate/hostname validation, an official hardcoded CIMA endpoint, fixed
connect/read timeouts, no redirect following, strict HTTP 200/JSON response
validation and an enforced 256 KiB streamed size limit. It always validates
the response registration before returning data. The HTTP request must be
invoked off the Android UI thread. The client does **not** store results,
execute SQL, change user prescriptions, alter medication schedules or update
installed medicine databases.

Regression tests use synthetic JSON and in-memory byte streams to avoid
making app builds dependent on an external service. The official HTTPS
endpoint was separately checked on 2026-10-08, but full device/network
interoperability is still an acceptance criterion.

## Presentation-level mapping preview (not yet an importer)

A CIMA medicine registration can contain multiple **national presentation codes
(CN)** with different pack sizes and names. `CimaRestCatalog` retains
`presentaciones[].nombre` alongside each validated CN, rejects contradictory
duplicate CN entries, and keeps the existing national-code list API stable.
`CimaPrescriptionCandidates.from()` produces immutable, **non-persisted**
candidates that keep the registration number, CN, commercial name and
source-reported dose text distinct. A registration with no presentations
produces no candidates; no default package codes are fabricated.

These candidates are not clinical prescriptions, medical advice, ORM entities,
or installed catalog entries. They are input to a future **transactional**
mapping and reconciliation stage. Active ingredients, authorization status,
product discontinuations and patient-linked historical IDs still need explicit
mapping and validation before any production import.

## Official read-only medicine-name search (paged)

CIMA REST v1.23 also documents the `GET /medicamentos?nombre=...&pagina=N`
endpoint and response properties including `totalFilas`, `pagina` and
`resultados`. The `CimaMedicineSearch` helper encodes a bounded UTF-8 name
query, constrains page numbers, checks registration identifiers and names,
treats missing marketing/prescription flags as unknown, detects contradictory
duplicate records and yields immutable search-result pages.

`CimaRestClient.searchByName(context, name, page)` uses the **same official
HTTPS-only, no-redirect, strict JSON/UTF-8 and streamed size limits** as detail
lookups. Search results alone are never prescriptions: before a selected
medicine can enter a catalog importer, its official detail must be fetched
and validated against the exact registration ID and reconciled with any
existing patient-linked records. Search currently has no screen integration
and makes no database changes. Bulk listing, incremental paging beyond
search and real-device network interoperability remain to be validated.

Source: AEMPS CIMA REST API v1.23,
https://cima.aemps.es/cima/resources/docs/CIMA_REST_API.pdf

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
