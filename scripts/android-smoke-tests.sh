#!/usr/bin/env bash
# Shared disposable-fixture instrumentation suite for Android API 23/33/36/37.
# A single list keeps the primary and Android 17 emulator jobs in sync.
# Do not run against a device with real patient records.
set -euo pipefail

TEST_CLASSES='es.usc.citius.servando.calendula.InstallationSmokeTest,es.usc.citius.servando.calendula.NotificationPermissionSmokeTest,es.usc.citius.servando.calendula.ZipExtractionSmokeTest,es.usc.citius.servando.calendula.DatabaseMigrationSmokeTest,es.usc.citius.servando.calendula.scheduling.ReminderPendingIntentSmokeTest,es.usc.citius.servando.calendula.scheduling.DailyMidnightAlarmSmokeTest,es.usc.citius.servando.calendula.scheduling.ClockChangeAlarmSmokeTest,es.usc.citius.servando.calendula.scheduling.ReminderCleanupSmokeTest,es.usc.citius.servando.calendula.scheduling.ExpiredReminderPruneSmokeTest,es.usc.citius.servando.calendula.scheduling.NullablePatientDaoSmokeTest,es.usc.citius.servando.calendula.scheduling.OrphanReminderAlarmSmokeTest,es.usc.citius.servando.calendula.scheduling.PatientScopedReminderDeliverySmokeTest,es.usc.citius.servando.calendula.scheduling.AtomicMedicationDecisionSmokeTest,es.usc.citius.servando.calendula.scheduling.PendingDoseSelectionSmokeTest,es.usc.citius.servando.calendula.scheduling.DailyAgendaWindowQuerySmokeTest,es.usc.citius.servando.calendula.scheduling.PublicReminderPostCommitSmokeTest,es.usc.citius.servando.calendula.scheduling.CancelledIntakeAlarmCleanupSmokeTest,es.usc.citius.servando.calendula.activities.UnverifiedMedicationAlertSmokeTest,es.usc.citius.servando.calendula.scheduling.NoReminderResurrectionSmokeTest,es.usc.citius.servando.calendula.scheduling.CheckAllActiveIntakesSmokeTest,es.usc.citius.servando.calendula.scheduling.StartupReminderRearmSmokeTest,es.usc.citius.servando.calendula.scheduling.AtomicIntakeStockSmokeTest,es.usc.citius.servando.calendula.NativeMultidexSmokeTest'
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.scheduling.ReminderCandidateWindowSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.database.PatientDeletionScopeSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.database.AtomicMedicineCascadeSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.database.GenericDaoDelegationSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.persistence.PatientProfilePersistenceSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.util.alerts.AtomicHighAlertSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.persistence.alerts.StockAlertCardSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.persistence.alerts.AllergyAlertCardSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.adapters.items.LegacyMedicinePresentationSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.adapters.items.ProviderCardRecyclingSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.adapters.items.MedicineAlertBadgeSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.util.alerts.StockAlertOwnershipSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.util.alerts.AlertDeletionIdentitySmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.database.DefaultRoutineProvisioningSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.scheduling.RoutineDeleteDependencySmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.activities.schedules.ScheduleEditorRoutineScopeSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.healthcareprovider.util.ProviderScheduleMappingAtomicitySmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.login.LoginPatientBootstrapSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.database.AlertScheduleOwnershipSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.database.AlertReadOwnershipSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.healthcareprovider.database.ActiveMedOwnerQuerySmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.allergies.AllergyAlertOwnershipSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.allergies.AllergenWarningCleanupSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.allergies.AllergenIdentitySmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.activities.RoutineEditorOwnershipSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.activities.ScheduleEditorOwnershipSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.fragments.DailyAgendaReviewWarningSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.fragments.OfficialTreatmentPatientIsolationSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.activities.OfficialNotificationSourceSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.activities.ConfirmIntakePatientIsolationSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.scheduling.NotificationSnapshotSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.scheduling.NotificationReceiverDispatchSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.scheduling.DailyAgendaRollbackRetrySmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.scheduling.IntakeHistoryRetentionSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.scheduling.ConfirmScreenDelayResultSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.scheduling.CurrentReminderIdentitySmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.fragments.DailyAgendaPatientIsolationSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.persistence.ScheduleReconciliationSmokeTest"
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.activities.ProviderDocumentOwnerSmokeTest"
# Verify every selectable appearance asset really decodes on every supported API.
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.fragments.HomeBackgroundAssetsSmokeTest"
# Validate malformed catalog preference recovery on each Android emulator API.
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.util.DrugCatalogSelectionSmokeTest"
# Verify generic public medication notification on the actual Android API matrix.
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.healthcareprovider.notifications.ActiveMedLockscreenPrivacySmokeTest"
# Validate restored low-stock alert thresholds on the Android emulator matrix.
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.util.alerts.StockWarningPreferenceRestoreSmokeTest"
# Verify restored remote medication status on Android emulators.
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.util.RemoteMedicationPreferenceRestoreSmokeTest"
# Prevent malformed medicine selection results from mutating the active editor.
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.activities.MedicineSearchResultSafetySmokeTest"
# Corrupt active owner preference must not expose other patient profiles.
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.database.CorruptActivePatientPreferenceSmokeTest"
# Optional manifests let feature PRs add independent emulator tests without
# editing this shared, historically conflict-prone list. Every file in the
# directory is scanned in lexical order on API 23/33/36/37.
SMOKE_CLASS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/android-smoke-classes.d"
for manifest in "$SMOKE_CLASS_DIR"/*.txt; do
  [[ -f "$manifest" ]] || continue
  while IFS= read -r class || [[ -n "$class" ]]; do
    [[ -z "$class" || "$class" == \#* ]] && continue
    if [[ ! "$class" =~ ^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$ ]]; then
      printf 'Invalid Android instrumentation class in %s\n' "$manifest" >&2
      exit 2
    fi
    case ",$TEST_CLASSES," in
      *",$class,"*)
        printf 'Duplicate Android instrumentation class in %s\n' "$manifest" >&2
        exit 2
        ;;
    esac
    TEST_CLASSES="${TEST_CLASSES},${class}"
  done < "$manifest"
done

# Side-effect-free inspection mode for CI manifest validation. No emulator
# or Gradle task starts when listing the exact classes to run.
if [[ "${CALENDULA_SMOKE_LIST_ONLY:-0}" == "1" ]]; then
  tr ',' '\n' <<< "$TEST_CLASSES"
  exit 0
fi

# API 36's SQLite emits one WARN log per value imported from the
# bundled legacy medicine catalog (100k+ lines before the first SQLite test
# completes). The Gradle instrumentation process has lost its test connection
# during that unrelated logging flood. This only changes diagnostic verbosity
# on the *disposable CI emulator*: SQL errors remain visible and neither
# SQLite execution nor any clinical checks are skipped.
# Fail closed if adb cannot set the property; do not claim an unverified run.
if ! command -v adb >/dev/null 2>&1; then
  echo "Android emulator instrumentation requires adb; no tests were executed" >&2
  exit 1
fi
DEVICE_API="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ -z "$DEVICE_API" ]]; then
  echo "Could not verify connected disposable Android emulator API" >&2
  exit 1
fi
if [[ "$DEVICE_API" == "36" ]]; then
  echo "Android API 36: retain SQLite ERROR diagnostics, suppress repetitive WARN-only legacy catalog import logs"
  adb shell setprop log.tag.SQLiteLog ERROR
  if [[ "$(adb shell getprop log.tag.SQLiteLog | tr -d '\r')" != "ERROR" ]]; then
    echo "Could not configure SQLiteLog diagnostic threshold on CI emulator" >&2
    exit 1
  fi
fi

exec ./gradlew connectedCiDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=${TEST_CLASSES}" \
  --stacktrace --no-daemon
