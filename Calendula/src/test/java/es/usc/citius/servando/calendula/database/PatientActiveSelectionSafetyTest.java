/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.j256.ormlite.dao.Dao;

import org.greenrobot.eventbus.EventBus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.sql.SQLException;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.healthcareprovider.notifications.ActiveMedNotification;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Pure DAO fault injection: never connects to a real user or clinical database.
 * Invalid patient selection must not overwrite the active-profile preference.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PatientActiveSelectionSafetyTest {
    @SuppressWarnings("unchecked")
    private static Dao<Patient, Long> rawDao() {
        return (Dao<Patient, Long>) mock(Dao.class);
    }

    private static PatientDao createSubject(Dao<Patient, Long> raw) throws SQLException {
        DatabaseHelper helper = mock(DatabaseHelper.class);
        when(helper.getPatientDao()).thenReturn(raw);
        return new PatientDao(helper);
    }

    @Test
    public void missingOrUnsavedPatientCannotOverwriteActiveProfile() throws Exception {
        Dao<Patient, Long> raw = rawDao();
        PatientDao subject = createSubject(raw);
        Patient unsaved = new Patient();
        Patient stale = new Patient();
        stale.setId(98432L);
        when(raw.queryForId(98432L)).thenReturn(null);

        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            assertThrows(IllegalArgumentException.class, () -> subject.setActive(null));
            assertThrows(IllegalArgumentException.class, () -> subject.setActive(unsaved));
            assertThrows(IllegalArgumentException.class, () -> subject.setActive(stale));
            assertThrows(IllegalArgumentException.class, () -> subject.setActiveById(null));
            assertThrows(IllegalArgumentException.class, () -> subject.setActiveById(98432L));
            prefs.verifyNoInteractions();
        }
    }

    @Test
    public void removedActivePatientWithoutDefaultClearsDanglingSelection() throws Exception {
        Dao<Patient, Long> raw = rawDao();
        PatientDao subject = spy(createSubject(raw));
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(editor.remove(anyString())).thenReturn(editor);
        when(editor.putBoolean(eq(PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key()), eq(true))).thenReturn(editor);
        when(raw.queryForId(98433L)).thenReturn(null);
        doReturn(null).when(subject).getDefault();

        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class);
             MockedStatic<CalendulaApp> app = mockStatic(CalendulaApp.class)) {
            prefs.when(() -> PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1))
                    .thenReturn(98433L);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.PATIENTS_ACTIVE_OWNER, null))
                    .thenReturn("a".repeat(64));
            prefs.when(PreferenceUtils::edit).thenReturn(editor);
            app.when(CalendulaApp::getContext).thenReturn(context);

            assertNull(subject.getActive(context));
            verify(editor).remove(PreferenceKeys.PATIENTS_ACTIVE.key());
            verify(editor).remove(PreferenceKeys.PATIENTS_ACTIVE_OWNER.key());
            verify(editor).putBoolean(PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key(), true);
            verify(editor).apply();
            prefs.verify(PreferenceUtils::edit);
        }
    }

    @Test
    public void activeComparisonUsesNumericPrimaryKeysInsteadOfObjectIdentity()
            throws Exception {
        Dao<Patient, Long> raw = rawDao();
        PatientDao subject = createSubject(raw);
        Patient sameIdDifferentBox = new Patient();
        sameIdDifferentBox.setId(Long.valueOf("98434"));
        sameIdDifferentBox.setCode("ci-active-synthetic-98434");
        when(raw.queryForId(98434L)).thenReturn(sameIdDifferentBox);
        Patient unsaved = new Patient();
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1))
                    .thenReturn(98434L);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.PATIENTS_ACTIVE_OWNER, null))
                    .thenReturn(PatientDao.stablePatientDigest(sameIdDifferentBox.getCode()));
            assertTrue(subject.isActive(sameIdDifferentBox, null));
            assertFalse(subject.isActive(unsaved, null));
            assertFalse(subject.isActive(null, null));
        }
    }
    @Test
    public void recycledPatientIdCannotAuthorizeActivationOrActiveProfileCheck()
            throws Exception {
        Dao<Patient, Long> raw = rawDao();
        PatientDao subject = createSubject(raw);
        Patient persisted = new Patient();
        persisted.setId(98441L);
        persisted.setCode("ci-replacement-profile");
        Patient stale = new Patient();
        stale.setId(98441L);
        stale.setCode("ci-deleted-previous-profile");
        Patient missingCode = new Patient();
        missingCode.setId(98441L);
        when(raw.queryForId(98441L)).thenReturn(persisted);

        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1))
                    .thenReturn(98441L);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.PATIENTS_ACTIVE_OWNER, null))
                    .thenReturn(PatientDao.stablePatientDigest(persisted.getCode()));
            assertFalse("Reused ID with changed owner code is not active",
                    subject.isActive(stale, null));
            assertFalse("Unknown patient identity is not active",
                    subject.isActive(missingCode, null));
            assertTrue(subject.isActive(persisted, null));
            assertThrows(IllegalArgumentException.class, () -> subject.setActive(stale));
            assertThrows(IllegalArgumentException.class, () -> subject.setActive(missingCode));
            // Neither rejected selection may modify the active preference.
            prefs.verify(PreferenceUtils::edit, org.mockito.Mockito.never());
        }
    }

    @Test
    public void legacyUnboundSelectionCannotAuthorizeClinicalAccess()
            throws Exception {
        Dao<Patient, Long> raw = rawDao();
        PatientDao subject = createSubject(raw);
        Patient offlineDefault = new Patient();
        offlineDefault.setId(98442L);
        offlineDefault.setDefault(true);
        Patient reloaded = new Patient();
        reloaded.setId(98442L);
        reloaded.setDefault(true);
        when(raw.queryForId(98442L)).thenReturn(offlineDefault);

        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1))
                    .thenReturn(98442L);
            assertFalse(subject.isActive(reloaded, null));
            reloaded.setCode("ci-stale-code");
            assertFalse(subject.isActive(reloaded, null));
        }
    }

    @Test
    public void deletedPatientIsNotActiveEvenWhenStalePreferenceStillMatches() throws Exception {
        Dao<Patient, Long> raw = rawDao();
        PatientDao subject = createSubject(raw);
        Patient deleted = new Patient();
        deleted.setId(98440L);
        when(raw.queryForId(98440L)).thenReturn(null);
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1))
                    .thenReturn(98440L);
            assertFalse("A removed SQLite patient must not stay active by preference alone",
                    subject.isActive(deleted, null));
        }
    }

    @Test
    public void existingPatientStillActivatesAndNotifiesListeners() throws Exception {
        Dao<Patient, Long> raw = rawDao();
        PatientDao subject = createSubject(raw);
        Context context = ApplicationProvider.getApplicationContext();
        Patient existing = new Patient();
        existing.setId(98435L);
        existing.setCode("ci-existing-profile");
        existing.setName("Canonical synthetic profile");
        Patient staleFromUi = new Patient();
        staleFromUi.setId(98435L);
        staleFromUi.setCode("ci-existing-profile");
        staleFromUi.setName("Outdated synthetic profile");
        when(raw.queryForId(98435L)).thenReturn(existing);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        EventBus eventBus = mock(EventBus.class);
        when(editor.putLong(anyString(), eq(98435L))).thenReturn(editor);
        when(editor.putString(eq(PreferenceKeys.PATIENTS_ACTIVE_OWNER.key()), anyString()))
                .thenReturn(editor);
        when(editor.remove(PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key())).thenReturn(editor);

        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class);
             MockedStatic<CalendulaApp> app = mockStatic(CalendulaApp.class)) {
            app.when(CalendulaApp::getContext).thenReturn(context);
            app.when(CalendulaApp::eventBus).thenReturn(eventBus);
            prefs.when(PreferenceUtils::edit).thenReturn(editor);
            subject.setActive(staleFromUi);
            verify(editor).putLong(PreferenceKeys.PATIENTS_ACTIVE.key(), 98435L);
            verify(editor).putString(eq(PreferenceKeys.PATIENTS_ACTIVE_OWNER.key()),
                    eq(PatientDao.stablePatientDigest(existing.getCode())));
            verify(editor).apply();
            ArgumentCaptor<es.usc.citius.servando.calendula.events.PersistenceEvents.ActiveUserChangeEvent> event =
                    ArgumentCaptor.forClass(
                            es.usc.citius.servando.calendula.events.PersistenceEvents.ActiveUserChangeEvent.class);
            verify(eventBus).post(event.capture());
            assertSame("The change event must carry the persisted profile, not stale UI data",
                    existing, event.getValue().patient);
        }
    }



    @Test
    public void switchingProfilesCancelsOnlyTheOldProviderNotification() throws Exception {
        Dao<Patient, Long> raw = rawDao();
        PatientDao subject = createSubject(raw);
        Context context = ApplicationProvider.getApplicationContext();
        Patient newPatient = new Patient();
        newPatient.setId(98435L);
        newPatient.setCode("ci-switch-profile");
        when(raw.queryForId(98435L)).thenReturn(newPatient);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(editor.putLong(anyString(), eq(98435L))).thenReturn(editor);
        when(editor.putString(eq(PreferenceKeys.PATIENTS_ACTIVE_OWNER.key()), anyString()))
                .thenReturn(editor);
        when(editor.remove(PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key())).thenReturn(editor);
        EventBus bus = mock(EventBus.class);

        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class);
             MockedStatic<CalendulaApp> app = mockStatic(CalendulaApp.class);
             MockedStatic<ActiveMedNotification> notification =
                     mockStatic(ActiveMedNotification.class)) {
            prefs.when(PreferenceUtils::edit).thenReturn(editor);
            prefs.when(() -> PreferenceUtils.getLong(
                    PreferenceKeys.PATIENTS_ACTIVE, -1L)).thenReturn(98434L, 98435L);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.PATIENTS_ACTIVE_OWNER, null))
                    .thenReturn(null, PatientDao.stablePatientDigest(newPatient.getCode()));
            app.when(CalendulaApp::getContext).thenReturn(context);
            app.when(CalendulaApp::eventBus).thenReturn(bus);

            subject.setActive(newPatient);
            notification.verify(() -> ActiveMedNotification.cancel(context), times(1));
            verify(editor).putLong(PreferenceKeys.PATIENTS_ACTIVE.key(), 98435L);
            // Selecting the same profile again must not dismiss its own
            // newly delivered provider notification.
            subject.setActive(newPatient);
            notification.verify(() -> ActiveMedNotification.cancel(context), times(1));
            verify(bus, times(2)).post(
                    org.mockito.ArgumentMatchers.any(
                            es.usc.citius.servando.calendula.events.PersistenceEvents.ActiveUserChangeEvent.class));
        }
    }

    @Test
    public void sameRecycledNumericIdWithDifferentBindingCancelsOldProviderNotification()
            throws Exception {
        Dao<Patient, Long> raw = rawDao();
        PatientDao subject = createSubject(raw);
        Context context = ApplicationProvider.getApplicationContext();
        Patient replacement = new Patient();
        replacement.setId(98603L);
        replacement.setCode("local-ci-new-owner");
        when(raw.queryForId(98603L)).thenReturn(replacement);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(editor.putLong(eq(PreferenceKeys.PATIENTS_ACTIVE.key()), eq(98603L)))
                .thenReturn(editor);
        when(editor.putString(eq(PreferenceKeys.PATIENTS_ACTIVE_OWNER.key()), anyString()))
                .thenReturn(editor);
        when(editor.remove(PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key()))
                .thenReturn(editor);
        EventBus bus = mock(EventBus.class);
        String oldDigest = PatientDao.stablePatientDigest("local-ci-old-owner");
        String newDigest = PatientDao.stablePatientDigest(replacement.getCode());

        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class);
             MockedStatic<CalendulaApp> app = mockStatic(CalendulaApp.class);
             MockedStatic<ActiveMedNotification> notification =
                     mockStatic(ActiveMedNotification.class)) {
            prefs.when(() -> PreferenceUtils.getLong(
                    PreferenceKeys.PATIENTS_ACTIVE, -1L)).thenReturn(98603L);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.PATIENTS_ACTIVE_OWNER, null)).thenReturn(oldDigest);
            prefs.when(PreferenceUtils::edit).thenReturn(editor);
            app.when(CalendulaApp::getContext).thenReturn(context);
            app.when(CalendulaApp::eventBus).thenReturn(bus);

            subject.setActive(replacement);
            verify(editor).putString(PreferenceKeys.PATIENTS_ACTIVE_OWNER.key(),
                    newDigest);
            verify(editor).remove(PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key());
            verify(editor).apply();
            notification.verify(() -> ActiveMedNotification.cancel(context), times(1));
        }
    }

    @Test
    public void corruptedActivePatientPreferenceCannotImpersonateAnOwner() throws Exception {
        PatientDao subject = createSubject(rawDao());
        Patient previouslySelected = new Patient();
        previouslySelected.setId(98436L);
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1))
                    .thenThrow(new ClassCastException("Synthetic restored String"));
            assertFalse("A broken patient selection is never authorization",
                    subject.isActive(previouslySelected, null));
        }
    }

    @Test
    public void deletedSelectionMarkerForcesExplicitRecoveryEvenWithoutSavedId()
            throws Exception {
        PatientDao subject = spy(createSubject(rawDao()));
        Context context = ApplicationProvider.getApplicationContext();
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getLong(
                    PreferenceKeys.PATIENTS_ACTIVE, -1L)).thenReturn(-1L);
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.PATIENTS_RESELECTION_REQUIRED, false))
                    .thenReturn(true);
            assertNull(subject.getActive(context));
            org.mockito.Mockito.verify(subject, org.mockito.Mockito.never()).getDefault();
        }
    }

    @Test
    public void corruptOrphanedBindingCannotSelectFallbackDefault()
            throws Exception {
        PatientDao subject = spy(createSubject(rawDao()));
        Context context = ApplicationProvider.getApplicationContext();
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getLong(
                    PreferenceKeys.PATIENTS_ACTIVE, -1L)).thenReturn(-1L);
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.PATIENTS_RESELECTION_REQUIRED, false)).thenReturn(false);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.PATIENTS_ACTIVE_OWNER, null))
                    .thenThrow(new ClassCastException("Synthetic corrupted digest type"));
            assertNull(subject.getActive(context));
            org.mockito.Mockito.verify(subject, org.mockito.Mockito.never()).getDefault();
        }
    }

    @Test
    public void corruptSelectionDoesNotImplicitlySwitchToAnotherPatient()
            throws Exception {
        PatientDao subject = spy(createSubject(rawDao()));
        Context context = ApplicationProvider.getApplicationContext();
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1))
                    .thenThrow(new ClassCastException("Synthetic restored Boolean"));
            assertNull("Unknown selected owner must not expose a default patient",
                    subject.getActive(context));
            org.mockito.Mockito.verify(subject, org.mockito.Mockito.never()).getDefault();
            prefs.verify(PreferenceUtils::edit, org.mockito.Mockito.never());
        }
    }
}
