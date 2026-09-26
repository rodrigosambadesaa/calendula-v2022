/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.util;

import org.junit.Test;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.RepeatType;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;

import static org.junit.Assert.assertEquals;

public class ScheduleCreatorTest {

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
}
