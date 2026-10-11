/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.allergies.AllergenType;
import es.usc.citius.servando.calendula.allergies.AllergenVO;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** All values are fabricated. Log formatting must not expose medical details. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ClinicalModelStringPrivacyTest {

    @Test
    public void allergyDiagnosticsHideDisplayNameAndCatalogIdentifier() {
        String sensitiveName = "synthetic-allergen-private-name";
        String sensitiveIdentifier = "synthetic-allergen-private-id";
        AllergenVO allergen = new AllergenVO(
                AllergenType.ACTIVE_INGREDIENT, sensitiveName, sensitiveIdentifier);
        String diagnostic = allergen.toString();

        assertFalse(diagnostic.contains(sensitiveName));
        assertFalse(diagnostic.contains(sensitiveIdentifier));
        assertTrue(diagnostic.contains("hasName=true"));
        // The UI still has access to the genuine clinical values.
        assertEquals(sensitiveName, allergen.getName());
        assertEquals(sensitiveIdentifier, allergen.getIdentifier());
    }

    @Test
    public void unlinkedDosageDiagnosticIsNullSafeAndDoesNotPrintIds() {
        DosageEntity dose = new DosageEntity();
        dose.setId(987654321L);
        String diagnostic = dose.toString();

        assertFalse(diagnostic.contains("987654321"));
        assertTrue(diagnostic.contains("hasActiveMed=false"));
        assertEquals(Long.valueOf(987654321L), dose.getId());
    }

    @Test
    public void recurrenceDiagnosticHidesPatientScheduleButRetainsPersistenceRule() {
        RecurringEvent recurrence = new RecurringEvent("FREQ=DAILY;INTERVAL=2");
        DateTime start = new DateTime(2031, 6, 12, 8, 43);
        DateTime end = new DateTime(2031, 7, 12, 8, 43);
        recurrence.setStartDateTime(start);
        recurrence.setEndDateTime(end);

        String diagnostic = recurrence.toString();
        assertFalse(diagnostic.contains("2031"));
        assertFalse(diagnostic.contains("08:43"));
        assertFalse(diagnostic.contains("FREQ="));
        assertFalse(diagnostic.contains("INTERVAL="));
        assertTrue(diagnostic.contains("hasStart=true"));
        assertTrue(diagnostic.contains("hasEnd=true"));
        assertTrue(recurrence.recurrenceRule().contains("FREQ=DAILY"));
    }
}
