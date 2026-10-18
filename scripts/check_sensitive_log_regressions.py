#!/usr/bin/env python3
"""Small, dependency-free CI guard for previously observed clinical Logcat leaks.

This intentionally checks only known sensitive logging sites. It avoids
printing any matching source line, because that could repeat secret content.
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
RULES = {
    "Calendula/src/main/java/es/usc/citius/servando/calendula/settings/privacy/PrivacyPrefsPresenter.kt": (
        (r"data=\$data\b", "raw PIN settings Intent interpolated into diagnostics"),
        (r"LogUtil\.\w+\s*\([^\n]*EXTRA_NEW_PIN", "PIN extra directly logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/pinlock/view/NumberPadView.java": (
        (r"LogUtil\.\w+\s*\([^\n]*numberValue\b", "PIN keypad digit logged"),
        (r"Number clicked:\s*", "legacy numeric PIN keypress diagnostic reintroduced"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/LeftDrawerMgr.java": (
        (r"LogUtil\.\w+\s*\([^\n]*p\.getName\s*\(", "patient name directly logged"),
        (r"Adding patient to drawer:\s*", "legacy patient-name log reintroduced"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/ConfirmActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*eventInstance\.toString\s*\(", "confirmation intake event serialized to log"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/schedules/ScheduleBuildActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*tmpSchedule\.toString\s*\(", "medication schedule serialized to log"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/DailyAgendaRecyclerAdapter.java": (
        (r"LogUtil\.\w+\s*\([^\n]*item\.toString\s*\(", "agenda row serialized to log"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/fragments/ActiveMedicationListFragment.java": (
        (r"LogUtil\.\w+\s*\([^\n]*event\.toString\s*\(", "provider medication update event serialized to log"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/CalendarActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*msg\.toString\s*\(", "patient medication calendar rendered text logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/util/stock/StockDisplayUtils.kt": (
        (r"updateStockText: estimated end date is", "stock depletion date logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/util/DispensationInfoStore.java": (
        (r"LogUtil\.\w+\s*\([^\n]*day\.toString\s*\(", "dispensation dates logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/scheduling/ScheduleDisplayUtils.java": (
        (r"displayDose: invalid decimalPart value! value =", "raw medication dose logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/schedules/PickRoutineStepFragment.java": (
        (r"ValueChange:[^\n]*selected:", "selected medication routine hour logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/fragments/RoutineCreateOrEditFragment.java": (
        (r"LogUtil\.\w+\s*\([^\n]*r\.getName\s*\(", "routine name printed in clinical logs"),
        (r"Routine set:\s*", "legacy patient routine name diagnostics reintroduced"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/fragments/RoutinesListFragment.java": (
        (r"LogUtil\.\w+\s*\([^\n]*r\.getName\s*\(", "selected routine name printed in clinical logs"),
        (r"Click at\s*", "legacy clicked routine name diagnostic reintroduced"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/database/migrationHelpers/DrugModelMigrationHelper.kt": (
        (r"linkMedsAfterUpdate: linked med", "medication and prescription object logged during relink"),
        (r"LogUtil\.\w+\s*\([^\n]*linkablePrescription\b", "prescription model interpolated into Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/scheduling/Agenda.java": (
        (r"LogUtil\.e\s*\(\s*TAG\s*,[^\n]*,\s*(?:e|error)\s*\)", "daily agenda or clinical reminder throwable exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/persistence/ScheduleUtils.java": (
        (r"LogUtil\.e\s*\(\s*TAG\s*,[^\n]*,\s*(?:alertError|schedulingError|listenerError)\s*\)", "post-commit medication/stock throwable exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/fragments/AlertListFragment.java": (
        (r"LogUtil\.e\s*\(\s*TAG\s*,[^\n]*,\s*e\s*\)", "patient clinical alert exception exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/healthcareprovider/persistence/ActiveMedType.java": (
        (r"LogUtil\.\w+\s*\([^\n]*\+\s*coding\b", "unknown provider medication classification leaked to Logcat"),
        (r"forCoding:\s*unknown coding", "legacy provider coding diagnostic reintroduced"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/healthcareprovider/model/ActiveMedCodingSystemType.java": (
        (r"LogUtil\.\w+\s*\([^\n]*\+\s*coding\b", "unknown provider medication system leaked to Logcat"),
        (r"forCoding:\s*unknown coding", "legacy coding system diagnostic reintroduced"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/database/DatabaseHelper.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "database creation/migration exception exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/login/LogoutHelper.java": (
        (r"LogUtil\.\w+\s*\([^\n]*\+\s*ctx\b", "logout Context details serialized to Logcat"),
        (r"clearData\(\) called with:\s*ctx", "legacy logout context diagnostic reintroduced"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/AllergiesActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*\+\s*(?:allergen|item|fastAdapter|view)\b", "clinical allergy object or UI row dumped to Logcat"),
        (r"LogUtil\.\w+\s*\([^\n]*,\s*(?:e|error|failure)\s*\)", "patient allergen database exception exposed to Logcat"),
        (r"storeAllergen:\s*inserted allergen into database:", "legacy allergy record dump reintroduced"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/login/LoginActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*(?:e|exception)\s*\)", "login exception could expose authentication details"),
        (r"LogUtil\.\w+\s*\([^\n]*serviceConfig\.authorizationEndpoint\b", "OAuth provider endpoint exposed to diagnostic log"),
        (r"Making auth request to", "legacy OAuth endpoint diagnostic reintroduced"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/database/RoutineDao.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "clinical routine SQL exception exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/util/HttpDownloadUtil.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "medicine catalog download file or URL exception exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/jobs/CheckDatabaseUpdatesJob.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "catalog update request exception exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/MedicinesSearchActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*\+\s*event\b", "medical database installed event serialized into logs"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/util/AvatarMgr.java": (
        (r"LogUtil\.\w+\s*\([^\n]*\+\s*avatar\b", "potentially untrusted avatar identifier logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/scheduling/BootReceiver.java": (
        (r"LogUtil\.\w+\s*\([^\n]*\+\s*action\b", "untrusted broadcast action logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/healthcareprovider/remote/RemoteQueryManager.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "remote clinical/FHIR parsing exception exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/modules/ModuleManager.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "application module startup exception exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/modules/ModuleRegistry.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "module reflection exception exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/drugdb/model/database/ATCCodeDAO.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "catalog DAO throwable exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/drugdb/model/database/ActiveIngredientDAO.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "catalog DAO throwable exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/drugdb/model/database/ContentUnitDAO.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "catalog DAO throwable exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/drugdb/model/database/ExcipientDAO.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "catalog DAO throwable exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/drugdb/model/database/HomogeneousGroupDAO.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "catalog DAO throwable exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/drugdb/model/database/PackageTypeDAO.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "catalog DAO throwable exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/drugdb/model/database/PrescriptionActiveIngredientDAO.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "catalog DAO throwable exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/drugdb/model/database/PrescriptionExcipientDAO.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "catalog DAO throwable exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/drugdb/model/database/PresentationFormDAO.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "catalog DAO throwable exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/drugdb/model/database/PrescriptionDAO.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "catalog DAO throwable exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/login/LogoutActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*exception\s*\)", "logout failure exception exposes credentials"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/util/ScreenUtils.java": (
        (r"LogUtil\.\w+\s*\([^\n]*e\.getMessage\(\)", "image path/URI exception exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/settings/CalendulaSettingsActivity.kt": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "settings exception exposes configuration details"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/MedicinesActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*\+\s*searchText\b", "medical search terms exposed in diagnostic logs"),
        (r"showSearchView\(\) called with:\s*searchText", "legacy medication search data dump reintroduced"),
        (r"LogUtil\.\w+\s*\([^\n]*,\s*(?:e|error|failure)\s*\)", "medication database exception exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/MedicineInfoActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*(?:e|error|failure)\s*\)", "clinical alert verification exception exposed to Logcat"),
        (r"LogUtil\.\w+\s*\([^\n]*\+\s*action\b", "untrusted medicine detail intent action exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/database/PatientAlertDao.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "patient alert SQL exception exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/database/AllergyGroupDao.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "allergy group SQL exception exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/util/CloseableUtil.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "closing a medical file exposed stream exception details"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/CalendulaApp.java": (
        (r"LogUtil\.\w+\s*\([^\n]*Failed to export database[^\n]*,\s*e\s*\)", "medical database export path exposed to Logcat"),
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "application module startup exception exposed to Logcat"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/pinlock/PINManager.java": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "PIN hash or salt error stack logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/util/security/SecurePrefBundle.kt": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "encrypted credentials bundle error stack logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/util/security/LibraryEncryptionProvider.kt": (
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "encrypted preference error stack logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/util/HtmlCacheManager.java": (
        (r"LogUtil\.\w+\s*\([^\n]*\+\s*(?:entry|hashCode)\b", "patient medical HTML cache object/hash logged"),
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "medical cache SQL throwable logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/healthcareprovider/fhir/FHIRUtil.kt": (
        (r"LogUtil\.\w+\s*\([^\n]*\+\s*type\.toString\s*\(", "imported FHIR resource type serialized to diagnostics"),
        (r"LogUtil\.\w+\s*\([^\n]*,\s*e\s*\)", "imported FHIR clinical exception logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/PdfViewActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*\+\s*(?:url|previewUrl)\b", "provider document URL logged"),
        (r"Finished loading URL:", "legacy clinical document URI log reintroduced"),
        (r"LogUtil\.e\s*\(\s*TAG\s*,[^\n]*,\s*e\s*\)", "sensitive provider document exception trace logged"),
    ),
}


def violations(relative_path: str, source: str) -> list[str]:
    """Do not include source text (which may itself be sensitive) in errors."""
    return [
        description
        for pattern, description in RULES[relative_path]
        if re.search(pattern, source, flags=re.MULTILINE)
    ]


def self_test() -> None:
    # Resolve by explicit path: adding clinical guards must never break self-test.
    pin = "Calendula/src/main/java/es/usc/citius/servando/calendula/settings/privacy/PrivacyPrefsPresenter.kt"
    drawer = "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/LeftDrawerMgr.java"
    pdf = "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/PdfViewActivity.java"
    assert violations(pin, 'LogUtil.d(TAG, "data=$data")')
    assert violations(pin, 'LogUtil.d(TAG, EXTRA_NEW_PIN)')
    assert violations(drawer, 'LogUtil.d(TAG, "Name: " + p.getName())')
    pin_pad = "Calendula/src/main/java/es/usc/citius/servando/calendula/pinlock/view/NumberPadView.java"
    assert violations(pin_pad, 'LogUtil.v(TAG, "Number clicked: " + numberValue)')
    assert violations(pin_pad, 'LogUtil.d(TAG, "PIN digit " + numberValue)')
    assert violations(pdf, 'LogUtil.d(TAG, "Document: " + previewUrl)')
    assert violations(pdf, 'LogUtil.d(TAG, "Document: " + url)')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/activities/ConfirmActivity.java", 'LogUtil.d(TAG, eventInstance.toString())')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/activities/schedules/ScheduleBuildActivity.java", 'LogUtil.d(TAG, tmpSchedule.toString())')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/DailyAgendaRecyclerAdapter.java", 'LogUtil.d(TAG, item.toString())')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/fragments/ActiveMedicationListFragment.java", 'LogUtil.d(TAG, event.toString())')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/activities/CalendarActivity.java", 'LogUtil.d(TAG, msg.toString())')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/util/stock/StockDisplayUtils.kt", "LogUtil.d(TAG, \"updateStockText: estimated end date is \" + privateDate)")
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/util/DispensationInfoStore.java", "LogUtil.d(TAG, day.toString(\"dd/MM/YYYY\"))")
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/scheduling/ScheduleDisplayUtils.java", "LogUtil.d(TAG, \"displayDose: invalid decimalPart value! value = \" + decimalPart)")
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/activities/schedules/PickRoutineStepFragment.java", "LogUtil.d(TAG, \"ValueChange: \" + current + \", selected: \" + hours.get(current - 1))")
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/fragments/RoutineCreateOrEditFragment.java", 'LogUtil.d(TAG, "Routine set: " + r.getName())')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/fragments/RoutinesListFragment.java", 'LogUtil.d(TAG, "Click at " + r.getName())')
    migration = "Calendula/src/main/java/es/usc/citius/servando/calendula/database/migrationHelpers/DrugModelMigrationHelper.kt"
    assert violations(migration, 'LogUtil.d(TAG, "linkMedsAfterUpdate: linked med $med to prescription $linkablePrescription")')
    assert violations(migration, 'LogUtil.d(TAG, "Re-linked " + linkablePrescription)')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/scheduling/Agenda.java", 'LogUtil.e(TAG, "Error updating agenda", e)')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/persistence/ScheduleUtils.java", 'LogUtil.e(TAG, "Post-commit reminder error", schedulingError)')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/fragments/AlertListFragment.java", 'LogUtil.e(TAG, "Unable to load patient clinical alerts", e)')
    med_type = "Calendula/src/main/java/es/usc/citius/servando/calendula/healthcareprovider/persistence/ActiveMedType.java"
    coding_system = "Calendula/src/main/java/es/usc/citius/servando/calendula/healthcareprovider/model/ActiveMedCodingSystemType.java"
    assert violations(med_type, 'LogUtil.e(TAG, "forCoding: unknown coding " + coding)')
    assert violations(coding_system, 'LogUtil.d(TAG, "Coding " + coding)')
    pin_crypto = "Calendula/src/main/java/es/usc/citius/servando/calendula/pinlock/PINManager.java"
    secret_bundle = "Calendula/src/main/java/es/usc/citius/servando/calendula/util/security/SecurePrefBundle.kt"
    encryption = "Calendula/src/main/java/es/usc/citius/servando/calendula/util/security/LibraryEncryptionProvider.kt"
    assert violations(pin_crypto, 'LogUtil.e(TAG, "checkPIN: malformed hash", e)')
    assert violations(secret_bundle, 'LogUtil.e(TAG, "serialize failed", e)')
    assert violations(encryption, 'LogUtil.d(TAG, "Error decrypting property", e)')
    cache = "Calendula/src/main/java/es/usc/citius/servando/calendula/util/HtmlCacheManager.java"
    fhir = "Calendula/src/main/java/es/usc/citius/servando/calendula/healthcareprovider/fhir/FHIRUtil.kt"
    assert violations(cache, 'LogUtil.d(TAG, "remove: removing entry: " + entry)')
    assert violations(cache, 'LogUtil.e(TAG, "retrieveByHash", e)')
    assert violations(fhir, 'LogUtil.e(TAG, "Unexpected FHIR type " + type.toString())')
    assert violations(fhir, 'LogUtil.e(TAG, "genVOsFromResponse: ", e)')
    alert_dao = "Calendula/src/main/java/es/usc/citius/servando/calendula/database/PatientAlertDao.java"
    allergy_dao = "Calendula/src/main/java/es/usc/citius/servando/calendula/database/AllergyGroupDao.java"
    closeable = "Calendula/src/main/java/es/usc/citius/servando/calendula/util/CloseableUtil.java"
    db_export = "Calendula/src/main/java/es/usc/citius/servando/calendula/CalendulaApp.java"
    assert violations(alert_dao, 'LogUtil.e(TAG, "findByMedicineAndLevel: ", e)')
    assert violations(allergy_dao, 'LogUtil.e(TAG, "findAllOrderByPrecedence: ", e)')
    assert violations(closeable, 'LogUtil.e(TAG, "close: ", e)')
    assert violations(db_export, 'LogUtil.e(TAG, "Failed to export database", e)')
    schema_db = "Calendula/src/main/java/es/usc/citius/servando/calendula/database/DatabaseHelper.java"
    logout_ctx = "Calendula/src/main/java/es/usc/citius/servando/calendula/login/LogoutHelper.java"
    assert violations(schema_db, 'LogUtil.e(TAG, "Cannot create database", e)')
    assert violations(schema_db, 'LogUtil.e(TAG, "Database upgrade failed", e)')
    assert violations(logout_ctx, 'LogUtil.d(TAG, "clearData() called with: ctx = [" + ctx + "]")')
    allergy_screen = "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/AllergiesActivity.java"
    assert violations(allergy_screen, 'LogUtil.d(TAG, "Saved allergen " + allergen)')
    assert violations(allergy_screen, 'LogUtil.w(TAG, "Unexpected allergy item " + item)')
    assert violations(allergy_screen, 'LogUtil.e(TAG, "Unable to save patient allergens", failure)')
    assert violations(allergy_screen, 'LogUtil.d(TAG, "storeAllergen: inserted allergen into database: " + allergen)')
    login_screen = "Calendula/src/main/java/es/usc/citius/servando/calendula/login/LoginActivity.java"
    routine_dao = "Calendula/src/main/java/es/usc/citius/servando/calendula/database/RoutineDao.java"
    download_util = "Calendula/src/main/java/es/usc/citius/servando/calendula/util/HttpDownloadUtil.java"
    update_job = "Calendula/src/main/java/es/usc/citius/servando/calendula/jobs/CheckDatabaseUpdatesJob.java"
    assert violations(login_screen, 'LogUtil.d(TAG, "Making auth request to " + serviceConfig.authorizationEndpoint)')
    assert violations(login_screen, 'LogUtil.e(TAG, "No token", exception)')
    assert violations(routine_dao, 'LogUtil.e(TAG, "Error in findInHour", e)')
    assert violations(download_util, 'LogUtil.e(TAG, "downloadFile", e)')
    assert violations(update_job, 'LogUtil.e(TAG, "onRunJob", e)')
    med_search = "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/MedicinesSearchActivity.java"
    avatar_mgr = "Calendula/src/main/java/es/usc/citius/servando/calendula/util/AvatarMgr.java"
    boot_receiver = "Calendula/src/main/java/es/usc/citius/servando/calendula/scheduling/BootReceiver.java"
    assert violations(med_search, 'LogUtil.d(TAG, "Database event: " + event)')
    assert violations(avatar_mgr, 'LogUtil.w(TAG, "Invalid avatar " + avatar)')
    assert violations(boot_receiver, 'LogUtil.w(TAG, "Unexpected action " + action)')
    clinical_remote = "Calendula/src/main/java/es/usc/citius/servando/calendula/healthcareprovider/remote/RemoteQueryManager.java"
    mod_manager = "Calendula/src/main/java/es/usc/citius/servando/calendula/modules/ModuleManager.java"
    mod_registry = "Calendula/src/main/java/es/usc/citius/servando/calendula/modules/ModuleRegistry.java"
    app_lifecycle = "Calendula/src/main/java/es/usc/citius/servando/calendula/CalendulaApp.java"
    assert violations(clinical_remote, 'LogUtil.e(TAG, "Parsing remote response", e)')
    assert violations(mod_manager, 'LogUtil.e(TAG, "Module failed", e)')
    assert violations(mod_registry, 'LogUtil.e(TAG, "Failed to load module", e)')
    assert violations(app_lifecycle, 'LogUtil.e(TAG, "Module startup failed", e)')
    for dao_name in ["ATCCodeDAO","ActiveIngredientDAO","ContentUnitDAO","ExcipientDAO","HomogeneousGroupDAO","PackageTypeDAO","PrescriptionActiveIngredientDAO","PrescriptionExcipientDAO","PresentationFormDAO","PrescriptionDAO"]:
        dao_path = "Calendula/src/main/java/es/usc/citius/servando/calendula/drugdb/model/database/" + dao_name + ".java"
        assert violations(dao_path, 'LogUtil.e(TAG, "Cannot initialize catalog DAO", e)')
    logout = "Calendula/src/main/java/es/usc/citius/servando/calendula/login/LogoutActivity.java"
    images = "Calendula/src/main/java/es/usc/citius/servando/calendula/util/ScreenUtils.java"
    settings = "Calendula/src/main/java/es/usc/citius/servando/calendula/settings/CalendulaSettingsActivity.kt"
    assert violations(logout, 'LogUtil.e(TAG, "onInstanceIDError:", exception)')
    assert violations(images, 'LogUtil.e(TAG, e.getMessage(), e)')
    assert violations(settings, 'LogUtil.e(TAG, "Preferences", e)')
    medicine_editor = "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/MedicinesActivity.java"
    assert violations(medicine_editor, 'LogUtil.d(TAG, "Search " + searchText)')
    assert violations(medicine_editor, 'LogUtil.d(TAG, "showSearchView() called with: searchText = [" + searchText + "]")')
    med_info = "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/MedicineInfoActivity.java"
    assert violations(medicine_editor, 'LogUtil.e(TAG, "Medicine save failed", e)')
    assert violations(medicine_editor, 'LogUtil.e(TAG, "Delete failed", failure)')
    assert violations(med_info, 'LogUtil.e(TAG, "Clinical alerts failed", error)')
    assert violations(med_info, 'LogUtil.d(TAG, "Action " + action)')
    for name in RULES:
        assert not violations(name, 'LogUtil.d(TAG, "Operation completed")')
    print("Sensitive log regression guard self-test passed")


def main() -> int:
    if len(sys.argv) > 1:
        if sys.argv[1:] == ["--self-test"]:
            self_test()
            return 0
        print("Usage: check_sensitive_log_regressions.py [--self-test]", file=sys.stderr)
        return 2
    failed = False
    for path in RULES:
        source_path = ROOT / path
        if not source_path.is_file():
            print(f"Required sensitive logging guard target is missing: {path}", file=sys.stderr)
            failed = True
            continue
        for reason in violations(path, source_path.read_text(encoding="utf-8")):
            print(f"Sensitive Logcat regression: {path}: {reason}", file=sys.stderr)
            failed = True
    if not failed:
        print("Known PIN, patient, routine, medication and clinical document Logcat sites are sanitized")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
