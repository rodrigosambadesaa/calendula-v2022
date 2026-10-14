/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.allergies;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Synthetic values only: never reads or alters patient records. */
@RunWith(AndroidJUnit4.class)
public class AllergenIdentitySmokeTest {
    @Test
    public void malformedAllergensDoNotCrashOrMatchOtherMissingIdentities() {
        AllergenVO broken1 = new AllergenVO(null, null, null);
        AllergenVO broken2 = new AllergenVO(null, null, null);
        assertTrue(broken1.equals(broken1));
        assertFalse("Distinct missing identities must never be equated", broken1.equals(broken2));
        assertFalse(broken1.equals(null));
        broken1.hashCode();
        broken2.hashCode();

        AllergenVO incomplete = new AllergenVO(AllergenType.EXCIPIENT, "Synthetic", null);
        AllergenVO alsoIncomplete = new AllergenVO(AllergenType.EXCIPIENT, "Synthetic", null);
        assertFalse(incomplete.equals(alsoIncomplete));
        incomplete.hashCode();

        AllergenVO validA = new AllergenVO(AllergenType.EXCIPIENT, "Synthetic", "test-1");
        AllergenVO validB = new AllergenVO(AllergenType.EXCIPIENT, "Different label", "test-1");
        AllergenVO otherType = new AllergenVO(AllergenType.ACTIVE_INGREDIENT, "Synthetic", "test-1");
        assertEquals(validA, validB);
        assertEquals(validA.hashCode(), validB.hashCode());
        assertNotEquals(validA, otherType);
    }

    @Test
    public void unknownParcelableTypeOrdinalBecomesUnverifiedInsteadOfCrashing() {
        Parcel p = Parcel.obtain();
        try {
            p.writeInt(Integer.MAX_VALUE);
            p.writeString("Synthetic missing type");
            p.writeString("test-id");
            p.setDataPosition(0);
            AllergenVO restored = AllergenVO.CREATOR.createFromParcel(p);
            assertNull(restored.getType());
            assertEquals("Synthetic missing type", restored.getName());
            assertEquals("test-id", restored.getIdentifier());
            assertFalse(restored.equals(
                new AllergenVO(AllergenType.EXCIPIENT, "Synthetic", "test-id")));
            restored.hashCode();
        } finally {
            p.recycle();
        }
    }
}
