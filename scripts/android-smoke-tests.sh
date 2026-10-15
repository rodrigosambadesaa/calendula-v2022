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
# Check patient pickup notification redaction with the installed Android notification stack.
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.activities.PickupNotificationPrivacySmokeTest"
# Validate restored low-stock alert thresholds on the Android emulator matrix.
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.util.alerts.StockWarningPreferenceRestoreSmokeTest"
# Verify restored remote medication status on Android emulators.
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.util.RemoteMedicationPreferenceRestoreSmokeTest"
# Prevent malformed medicine selection results from mutating the active editor.
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.activities.MedicineSearchResultSafetySmokeTest"
# Protect patient QR links after the active profile changes.
TEST_CLASSES="${TEST_CLASSES},es.usc.citius.servando.calendula.activities.PatientLinkScanOwnerSmokeTest"
exec ./gradlew connectedCiDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=${TEST_CLASSES}" \
  --stacktrace --no-daemon
