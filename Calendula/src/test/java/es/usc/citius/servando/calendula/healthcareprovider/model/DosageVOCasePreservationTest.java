/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.model;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;

import org.joda.time.LocalTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Synthetic dose descriptions only; no prescription, stock or SQLite writes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DosageVOCasePreservationTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void clinicalUnitCaseIsPreservedInMultiEntryDosage() {
        DosageVO.Builder builder = new DosageVO.Builder().type(DosageType.DETAILED);
        builder.newEntry().repeatAt(new LocalTime(9, 0))
                .quantity(5.0d, "mL").done();
        builder.newEntry().repeatAt(new LocalTime(18, 0))
                .quantity(3.0d, "IU").done();

        String description = builder.build().toReadableString(context);
        assertTrue("Milliliter notation must retain the capital L", description.contains("mL"));
        assertTrue("International-unit notation must retain its letters", description.contains("IU"));
        assertFalse("Do not lowercase clinical units", description.contains(" ml"));
        assertFalse("Do not lowercase clinical units", description.contains(" iu"));
    }

    @Test
    public void aggregationStillFormatsOneBulletPerEntry() {
        DosageVO.Builder builder = new DosageVO.Builder().type(DosageType.DETAILED);
        builder.newEntry().repeatAt(new LocalTime(8, 30))
                .quantity(1.0d, "mcg/mL").done();

        String description = builder.build().toReadableString(context);
        assertTrue(description.startsWith("● "));
        assertTrue(description.endsWith("\n"));
        assertTrue(description.contains("mcg/mL"));
        assertEquals(1, description.split("● ", -1).length - 1);
    }
}
