/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;

import com.j256.ormlite.dao.Dao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.persistence.Schedule;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * No patient means no medication, schedules, routines or allergies; even
 * queries for a NULL owner must not look up unrelated/unassigned SQLite rows.
 * All DAOs use mocks: no real clinical data is opened.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PatientScopedReadGuardTest {
    @SuppressWarnings("unchecked")
    @Test
    public void missingActivePatientCannotQueryOtherClinicalRows() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseHelper helper = mock(DatabaseHelper.class);
        Dao<Medicine, Long> rawMedicines = (Dao<Medicine, Long>) mock(Dao.class);
        Dao<Routine, Long> rawRoutines = (Dao<Routine, Long>) mock(Dao.class);
        Dao<Schedule, Long> rawSchedules = (Dao<Schedule, Long>) mock(Dao.class);
        Dao<PatientAllergen, Long> rawAllergies =
                (Dao<PatientAllergen, Long>) mock(Dao.class);

        when(helper.getMedicinesDao()).thenReturn(rawMedicines);
        when(helper.getRoutinesDao()).thenReturn(rawRoutines);
        when(helper.getSchedulesDao()).thenReturn(rawSchedules);
        when(helper.getDao(PatientAllergen.class)).thenReturn(rawAllergies);

        MedicineDao medicines = new MedicineDao(helper);
        RoutineDao routines = new RoutineDao(helper);
        ScheduleDao schedules = new ScheduleDao(helper);
        PatientAllergenDao allergies = new PatientAllergenDao(helper);

        PatientDao patients = mock(PatientDao.class);
        when(patients.getActive(context)).thenReturn(null);
        try (MockedStatic<DB> db = mockStatic(DB.class)) {
            db.when(DB::patients).thenReturn(patients);
            assertTrue(medicines.findAllForActivePatient(context).isEmpty());
            assertTrue(routines.findAllForActivePatient(context).isEmpty());
            assertTrue(routines.findAllWithNameForActivePatient(context).isEmpty());
            assertTrue(schedules.findAllForActivePatient(context).isEmpty());
            assertTrue(allergies.findAllForActivePatient(context).isEmpty());
        }

        Patient unsaved = new Patient();
        assertTrue(medicines.findAll((Patient) null).isEmpty());
        assertTrue(medicines.findAll(unsaved).isEmpty());
        assertTrue(medicines.findAll((Long) null).isEmpty());
        assertTrue(medicines.findAllByGroup(null, new String[]{"synthetic-group"}).isEmpty());
        assertTrue(medicines.findAllByGroup(null, null).isEmpty());
        assertTrue(medicines.findAllByGroup(98765L, null).isEmpty());
        assertTrue(medicines.findAllByGroup(98765L, new String[0]).isEmpty());
        assertTrue(routines.findAll((Patient) null).isEmpty());
        assertTrue(routines.findAll(unsaved).isEmpty());
        assertTrue(routines.findAll((Long) null).isEmpty());
        assertTrue(routines.findAllWithName(null).isEmpty());
        assertTrue(schedules.findAll((Patient) null).isEmpty());
        assertTrue(schedules.findAll(unsaved).isEmpty());
        assertTrue(schedules.findAll((Long) null).isEmpty());
        assertTrue(allergies.findAll((Patient) null).isEmpty());
        assertTrue(allergies.findAll(unsaved).isEmpty());
        assertTrue(allergies.findAll((Long) null).isEmpty());

        // The main security assertion: no queryForAll / WHERE Patient IS NULL
        // or any other underlying SQLite operation was attempted.
        verifyNoInteractions(rawMedicines, rawRoutines, rawSchedules, rawAllergies);
    }
}
