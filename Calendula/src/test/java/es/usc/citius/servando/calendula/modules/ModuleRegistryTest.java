/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.modules;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.Locale;

import es.usc.citius.servando.calendula.modules.modules.AllergiesModule;
import es.usc.citius.servando.calendula.modules.modules.StockModule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ModuleRegistryTest {

    @Test
    public void ciConfigLookupDoesNotDependOnTurkishLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            List<CalendulaModule> modules = ModuleRegistry.getModulesForConfig("ci");

            assertEquals(5, modules.size());
            assertTrue(containsClass(modules, StockModule.class));
            assertTrue(containsClass(modules, AllergiesModule.class));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void productConfigAcceptsLowercaseName() {
        assertEquals(3, ModuleRegistry.getModulesForConfig("product").size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullConfigNameIsRejectedExplicitly() {
        ModuleRegistry.getModulesForConfig((String) null);
    }

    private boolean containsClass(List<CalendulaModule> modules, Class<?> expectedClass) {
        for (CalendulaModule module : modules) {
            if (module.getClass().equals(expectedClass)) {
                return true;
            }
        }
        return false;
    }
}
