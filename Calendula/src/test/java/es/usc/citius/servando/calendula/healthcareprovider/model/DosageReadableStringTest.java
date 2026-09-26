/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.model;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.hl7.fhir.dstu3.model.Timing;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntryEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.RepeatType;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DosageReadableStringTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void missingRepeatTypeReturnsNotAvailableInsteadOfCrashing() {
        DosageEntryVO entry = new DosageEntryVO(new DosageEntryEntity());

        assertEquals(notAvailable(), entry.toReadableString(context));
    }

    @Test
    public void timeOfDayWithoutTimeReturnsNotAvailable() {
        DosageEntryEntity entity = new DosageEntryEntity();
        entity.setRepeatType(RepeatType.TIME_OF_DAY);
        entity.setQuantityValue(1.0);
        entity.setQuantityUnits("tablet");

        assertEquals(notAvailable(), new DosageEntryVO(entity).toReadableString(context));
    }

    @Test
    public void periodWithoutRepeatValueReturnsNotAvailable() {
        DosageEntryEntity entity = new DosageEntryEntity();
        entity.setRepeatType(RepeatType.PERIOD);
        entity.setQuantityValue(1.0);
        entity.setQuantityUnits("tablet");
        entity.setRepeatUnits(Timing.UnitsOfTime.H);

        assertEquals(notAvailable(), new DosageEntryVO(entity).toReadableString(context));
    }

    @Test
    public void durationWithoutUnitsReturnsNotAvailable() {
        DosageEntryEntity entity = new DosageEntryEntity();
        entity.setRepeatType(RepeatType.DURATION);
        entity.setRepeatValue(8.0);

        assertEquals(notAvailable(), new DosageEntryVO(entity).toReadableString(context));
    }

    @Test
    public void mealWithoutQuantityReturnsNotAvailable() {
        DosageEntryEntity entity = new DosageEntryEntity();
        entity.setRepeatType(RepeatType.CM);

        assertEquals(notAvailable(), new DosageEntryVO(entity).toReadableString(context));
    }

    @Test
    public void emptyDosageHasEmptyReadableDescription() {
        assertEquals("", new DosageVO().toReadableString(context));
    }

    private String notAvailable() {
        return context.getString(R.string.dosage_string_not_available);
    }
}
