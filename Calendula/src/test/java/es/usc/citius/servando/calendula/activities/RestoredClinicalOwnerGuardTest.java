/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Shared patient isolation rules for restored medicine and allergy screens. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RestoredClinicalOwnerGuardTest {
    private static final String OWNER = "original_patient";

    @Test
    public void freshActivityUsesOnlyAValidActiveOwner() {
        assertEquals(Long.valueOf(42L),
                RestoredClinicalOwnerGuard.restoredOwnerOrNull(null, OWNER, 42L));
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(null, OWNER, null));
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(null, OWNER, 0L));
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(null, OWNER, -7L));
    }

    @Test
    public void missingOrWrongTypeRestoredOwnerDoesNotAdoptActivePatient() {
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(
                new Bundle(), OWNER, 42L));
        Bundle stringOwner = new Bundle();
        stringOwner.putString(OWNER, "42");
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(
                stringOwner, OWNER, 42L));
        Bundle integerOwner = new Bundle();
        integerOwner.putInt(OWNER, 42);
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(
                integerOwner, OWNER, 42L));
    }

    @Test
    public void rejectsStaleOrInvalidClinicalOwner() {
        Bundle saved = new Bundle();
        saved.putLong(OWNER, 42L);
        assertEquals(Long.valueOf(42L),
                RestoredClinicalOwnerGuard.restoredOwnerOrNull(saved, OWNER, 42L));
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(saved, OWNER, 43L));
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(saved, OWNER, null));
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(saved, null, 42L));
        saved.putLong(OWNER, -1L);
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(saved, OWNER, 42L));
    }

    @Test
    @Config(sdk = 33)
    public void android13StillRejectsMalformedRestoredState() {
        Bundle saved = new Bundle();
        saved.putString(OWNER, "42");
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(saved, OWNER, 42L));
        saved.putLong(OWNER, 42L);
        assertEquals(Long.valueOf(42L),
                RestoredClinicalOwnerGuard.restoredOwnerOrNull(saved, OWNER, 42L));
    }
}
