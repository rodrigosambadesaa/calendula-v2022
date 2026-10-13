/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.util;

import org.junit.Test;
import org.joda.time.DateTime;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntryEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.RepeatType;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScheduleCreatorTest {

    @Test
    public void importedAsNeededOrAbsentDosageMustNotInventEightHourlyMedication() {
        ScheduleCreator.requireAutomaticallySchedulableDosage(DosageType.GENERAL);
        ScheduleCreator.requireAutomaticallySchedulableDosage(DosageType.DETAILED);
        for (DosageType type : new DosageType[]{
                DosageType.AS_NEEDED, DosageType.NONE, null}) {
            boolean rejected = false;
            try {
                ScheduleCreator.requireAutomaticallySchedulableDosage(type);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            assertTrue("A missing/as-needed prescription cannot imply an automatic pill every 8 hours",
                    rejected);
        }
    }

    @Test
    public void importedPrescriptionRejectsReversedOrEmptyValidityInterval() {
        DateTime start = new DateTime(2026, 10, 13, 9, 0);
        DateTime later = start.plusMinutes(1);
        ScheduleCreator.requireValidProviderInterval(start, later);
        ScheduleCreator.requireValidProviderInterval(null, later);
        ScheduleCreator.requireValidProviderInterval(start, null);
        ScheduleCreator.requireValidProviderInterval(null, null);
        for (DateTime invalidEnd : new DateTime[]{start, start.minusMillis(1), start.minusDays(1)}) {
            boolean rejected = false;
            try {
                ScheduleCreator.requireValidProviderInterval(start, invalidEnd);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            assertTrue("An empty or reversed official prescription must not be scheduled",
                    rejected);
        }
    }

    @Test
    public void beforeMealCodesUseBeforeOffset() {
        assertEquals(EventInstance.EventOffset.BEFORE,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.ACM));
        assertEquals(EventInstance.EventOffset.BEFORE,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.ACD));
        assertEquals(EventInstance.EventOffset.BEFORE,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.ACV));
    }

    @Test
    public void afterMealCodesUseAfterOffset() {
        assertEquals(EventInstance.EventOffset.AFTER,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.PCM));
        assertEquals(EventInstance.EventOffset.AFTER,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.PCD));
        assertEquals(EventInstance.EventOffset.AFTER,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.PCV));
    }

    @Test
    public void withMealAndNonMealCodesUseNoOffset() {
        assertEquals(EventInstance.EventOffset.NONE,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.CM));
        assertEquals(EventInstance.EventOffset.NONE,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.CD));
        assertEquals(EventInstance.EventOffset.NONE,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.CV));
        assertEquals(EventInstance.EventOffset.NONE,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.TIME_OF_DAY));
        assertEquals(EventInstance.EventOffset.NONE,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.PERIOD));
        assertEquals(EventInstance.EventOffset.NONE,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.DURATION));
        assertEquals(EventInstance.EventOffset.NONE,
                ScheduleCreator.eventOffsetForRepeatType(null));
    }

    @Test
    public void beforeDinnerNeverBecomesAfterDinner() {
        assertEquals(EventInstance.EventOffset.BEFORE,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.ACV));
        assertEquals(EventInstance.EventOffset.AFTER,
                ScheduleCreator.eventOffsetForRepeatType(RepeatType.PCV));
    }

    @Test
    public void detailedMappingNeedsAtLeastOneMappedEntry() {
        assertEquals(ScheduleCreator.ScheduleMappingResult.NONE,
                ScheduleCreator.detailedMappingResult(0, 0));
        assertEquals(ScheduleCreator.ScheduleMappingResult.NONE,
                ScheduleCreator.detailedMappingResult(0, 3));
    }

    @Test
    public void detailedMappingIsPartialWhenAnyEntryIsSkipped() {
        assertEquals(ScheduleCreator.ScheduleMappingResult.PARTIAL,
                ScheduleCreator.detailedMappingResult(1, 2));
        assertEquals(ScheduleCreator.ScheduleMappingResult.PARTIAL,
                ScheduleCreator.detailedMappingResult(2, 3));
    }

    @Test
    public void detailedMappingIsCompleteOnlyWhenEveryEntryMaps() {
        assertEquals(ScheduleCreator.ScheduleMappingResult.COMPLETE,
                ScheduleCreator.detailedMappingResult(1, 1));
        assertEquals(ScheduleCreator.ScheduleMappingResult.COMPLETE,
                ScheduleCreator.detailedMappingResult(3, 3));
    }

    @Test
    public void generalProviderDosageNeverDropsExtraEntriesSilently() {
        DosageEntryEntity one = new DosageEntryEntity();
        DosageEntryEntity two = new DosageEntryEntity();
        assertTrue(ScheduleCreator.requireSingleGeneralEntry(
                java.util.Collections.singletonList(one)) == one);
        for (java.util.Collection<DosageEntryEntity> candidates :
                java.util.Arrays.<java.util.Collection<DosageEntryEntity>>asList(
                        null,
                        java.util.Collections.<DosageEntryEntity>emptyList(),
                        java.util.Arrays.asList(one, two),
                        java.util.Collections.<DosageEntryEntity>singletonList(null))) {
            boolean rejected = false;
            try {
                ScheduleCreator.requireSingleGeneralEntry(candidates);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            assertTrue("An absent or multi-entry GENERAL dose requires review",
                    rejected);
        }
    }

    @Test
    public void scheduleQuantityMustBePresentAndPositive() {
        DosageEntryEntity entry = new DosageEntryEntity();

        assertFalse(ScheduleCreator.hasUsableQuantity(entry));

        entry.setQuantityValue(0d);
        assertFalse(ScheduleCreator.hasUsableQuantity(entry));

        entry.setQuantityValue(-1d);
        assertFalse(ScheduleCreator.hasUsableQuantity(entry));

        entry.setQuantityValue(0.5d);
        assertTrue(ScheduleCreator.hasUsableQuantity(entry));

        entry.setQuantityValue(Double.POSITIVE_INFINITY);
        assertFalse("Infinite doses must never be scheduled",
                ScheduleCreator.hasUsableQuantity(entry));
        entry.setQuantityValue(Double.NEGATIVE_INFINITY);
        assertFalse(ScheduleCreator.hasUsableQuantity(entry));
        entry.setQuantityValue(Double.NaN);
        assertFalse(ScheduleCreator.hasUsableQuantity(entry));
    }

    @Test
    public void importedRepeatIntervalMustBeFiniteAndExactWholeNumber() {
        assertTrue(ScheduleCreator.hasUsableRepeatValue(1d));
        assertTrue(ScheduleCreator.hasUsableRepeatValue(8d));
        assertFalse(ScheduleCreator.hasUsableRepeatValue(null));
        assertFalse(ScheduleCreator.hasUsableRepeatValue(0d));
        assertFalse(ScheduleCreator.hasUsableRepeatValue(-1d));
        assertFalse(ScheduleCreator.hasUsableRepeatValue(0.5d));
        assertFalse("Fractional repeat must not be truncated to whole hours",
                ScheduleCreator.hasUsableRepeatValue(2.5d));
        assertFalse(ScheduleCreator.hasUsableRepeatValue(Double.NaN));
        assertFalse(ScheduleCreator.hasUsableRepeatValue(Double.POSITIVE_INFINITY));
        assertFalse(ScheduleCreator.hasUsableRepeatValue((double) Integer.MAX_VALUE + 1d));
    }
}
