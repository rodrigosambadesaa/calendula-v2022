/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.fhir

import org.joda.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FhirTimeParsingTest {

    @Test
    fun parsesMidnightWithoutSeconds() {
        assertEquals(LocalTime(0, 0), FHIRUtil.parseFhirLocalTime("00:00"))
    }

    @Test
    fun parsesMidnightWithSeconds() {
        assertEquals(LocalTime(0, 0, 0), FHIRUtil.parseFhirLocalTime("00:00:00"))
    }

    @Test
    fun parsesEndOfDayClockValue() {
        assertEquals(LocalTime(23, 59), FHIRUtil.parseFhirLocalTime("23:59"))
    }

    @Test
    fun parsesNormalTimeWithSeconds() {
        assertEquals(LocalTime(8, 5, 7), FHIRUtil.parseFhirLocalTime("08:05:07"))
    }
}
