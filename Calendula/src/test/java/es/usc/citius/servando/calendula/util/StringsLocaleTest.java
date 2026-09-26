/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;

import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StringsLocaleTest {

    private Locale originalLocale;

    @Before
    public void setTurkishLocale() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    public void properCaseDoesNotDependOnDeviceLocale() {
        assertEquals("Insulina", Strings.toProperCase("INSULINA"));
    }

    @Test
    public void firstPartDoesNotDependOnDeviceLocale() {
        assertEquals("Insulina", Strings.firstPart("INSULINA RETARD"));
    }

    @Test
    public void prescriptionShortNameDoesNotDependOnDeviceLocale() {
        Prescription prescription = new Prescription();
        prescription.setName("INSULINA RETARD");

        assertEquals("Insulina", prescription.shortName());
    }
}
