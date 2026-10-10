#!/usr/bin/env bash
# Shared disposable-fixture instrumentation suite for Android API 23/33/36/37.
# A single list keeps the primary and Android 17 emulator jobs in sync.
# Do not run against a device with real patient records.
set -euo pipefail

TEST_CLASSES='es.usc.citius.servando.calendula.InstallationSmokeTest,es.usc.citius.servando.calendula.NotificationPermissionSmokeTest,es.usc.citius.servando.calendula.ZipExtractionSmokeTest,es.usc.citius.servando.calendula.DatabaseMigrationSmokeTest,es.usc.citius.servando.calendula.scheduling.ReminderPendingIntentSmokeTest,es.usc.citius.servando.calendula.scheduling.DailyMidnightAlarmSmokeTest,es.usc.citius.servando.calendula.scheduling.ClockChangeAlarmSmokeTest,es.usc.citius.servando.calendula.scheduling.ReminderCleanupSmokeTest,es.usc.citius.servando.calendula.scheduling.ExpiredReminderPruneSmokeTest,es.usc.citius.servando.calendula.scheduling.NullablePatientDaoSmokeTest,es.usc.citius.servando.calendula.scheduling.OrphanReminderAlarmSmokeTest,es.usc.citius.servando.calendula.scheduling.PatientScopedReminderDeliverySmokeTest,es.usc.citius.servando.calendula.scheduling.AtomicMedicationDecisionSmokeTest,es.usc.citius.servando.calendula.scheduling.PendingDoseSelectionSmokeTest,es.usc.citius.servando.calendula.scheduling.PublicReminderPostCommitSmokeTest,es.usc.citius.servando.calendula.scheduling.CancelledIntakeAlarmCleanupSmokeTest,es.usc.citius.servando.calendula.activities.UnverifiedMedicationAlertSmokeTest,es.usc.citius.servando.calendula.scheduling.NoReminderResurrectionSmokeTest,es.usc.citius.servando.calendula.scheduling.CheckAllActiveIntakesSmokeTest,es.usc.citius.servando.calendula.scheduling.StartupReminderRearmSmokeTest,es.usc.citius.servando.calendula.scheduling.AtomicIntakeStockSmokeTest,es.usc.citius.servando.calendula.NativeMultidexSmokeTest'
exec ./gradlew connectedCiDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=${TEST_CLASSES}" \
  --stacktrace --no-daemon
