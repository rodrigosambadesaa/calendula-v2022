/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.persistence;

import org.hl7.fhir.dstu3.model.Timing;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RepeatTypeTest {

    @Test
    public void allSupportedMealTimingCodesKeepTheirMeaning() throws Exception {
        assertMapping("CM", RepeatType.CM);
        assertMapping("CD", RepeatType.CD);
        assertMapping("CV", RepeatType.CV);
        assertMapping("ACM", RepeatType.ACM);
        assertMapping("ACD", RepeatType.ACD);
        assertMapping("ACV", RepeatType.ACV);
        assertMapping("PCM", RepeatType.PCM);
        assertMapping("PCD", RepeatType.PCD);
        assertMapping("PCV", RepeatType.PCV);
    }

    @Test
    public void beforeDinnerDoesNotBecomeAfterDinner() throws Exception {
        Timing.EventTiming beforeDinner = Timing.EventTiming.fromCode("ACV");

        assertEquals(RepeatType.ACV, RepeatType.fromEventTiming(beforeDinner));
    }

    @Test
    public void supportedMealTypesRoundTripThroughFhirCode() throws Exception {
        RepeatType[] mealTypes = {
                RepeatType.CM, RepeatType.CD, RepeatType.CV,
                RepeatType.ACM, RepeatType.ACD, RepeatType.ACV,
                RepeatType.PCM, RepeatType.PCD, RepeatType.PCV
        };
        for (RepeatType repeatType : mealTypes) {
            assertEquals(
                    repeatType,
                    RepeatType.fromEventTiming(repeatType.toEventTiming()));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullTimingIsRejectedExplicitly() {
        RepeatType.fromEventTiming(null);
    }

    private void assertMapping(String code, RepeatType expected) throws Exception {
        assertEquals(expected, RepeatType.fromEventTiming(Timing.EventTiming.fromCode(code)));
    }
}
