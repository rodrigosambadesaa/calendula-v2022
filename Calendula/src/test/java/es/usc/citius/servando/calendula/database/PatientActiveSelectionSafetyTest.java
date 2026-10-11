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
        when(raw.queryForId(98433L)).thenReturn(null);
        doReturn(null).when(subject).getDefault();

        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class);
             MockedStatic<CalendulaApp> app = mockStatic(CalendulaApp.class)) {
            prefs.when(() -> PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1))
                    .thenReturn(98433L);
            prefs.when(PreferenceUtils::edit).thenReturn(editor);
            app.when(CalendulaApp::getContext).thenReturn(context);

            assertNull(subject.getActive(context));
            verify(editor).remove(PreferenceKeys.PATIENTS_ACTIVE.key());
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
        Patient unsaved = new Patient();
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1))
                    .thenReturn(98434L);
            assertTrue(subject.isActive(sameIdDifferentBox, null));
            assertFalse(subject.isActive(unsaved, null));
            assertFalse(subject.isActive(null, null));
        }
    }
    @Test
    public void existingPatientStillActivatesAndNotifiesListeners() throws Exception {
        Dao<Patient, Long> raw = rawDao();
        PatientDao subject = createSubject(raw);
        Context context = ApplicationProvider.getApplicationContext();
        Patient existing = new Patient();
        existing.setId(98435L);
        existing.setName("Canonical synthetic profile");
        Patient staleFromUi = new Patient();
        staleFromUi.setId(98435L);
        staleFromUi.setName("Outdated synthetic profile");
        when(raw.queryForId(98435L)).thenReturn(existing);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        EventBus eventBus = mock(EventBus.class);
        when(editor.putLong(anyString(), eq(98435L))).thenReturn(editor);

        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class);
             MockedStatic<CalendulaApp> app = mockStatic(CalendulaApp.class)) {
            app.when(CalendulaApp::getContext).thenReturn(context);
            app.when(CalendulaApp::eventBus).thenReturn(eventBus);
            prefs.when(PreferenceUtils::edit).thenReturn(editor);
            subject.setActive(staleFromUi);
            verify(editor).putLong(PreferenceKeys.PATIENTS_ACTIVE.key(), 98435L);
            verify(editor).apply();
            ArgumentCaptor<es.usc.citius.servando.calendula.events.PersistenceEvents.ActiveUserChangeEvent> event =
                    ArgumentCaptor.forClass(
                            es.usc.citius.servando.calendula.events.PersistenceEvents.ActiveUserChangeEvent.class);
            verify(eventBus).post(event.capture());
            assertSame("The change event must carry the persisted profile, not stale UI data",
                    existing, event.getValue().patient);
        }
    }


}
