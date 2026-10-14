/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence.typeSerializers;

import org.junit.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class DosagePersisterCorruptionTest {
    @SuppressWarnings("unchecked")
    @Test
    public void validScheduleMapRoundTripsAndPreservesLegacyDefaultReference() throws Exception {
        DosageMapPersister parser = new DosageMapPersister();
        Map<Long, Double> doses = (Map<Long, Double>) parser.sqlArgToJava(
                null, "{\"-10\":1.25,\"42\":0.5}", 0);
        assertEquals(1.25d, doses.get(-10L), 0d);
        assertEquals(0.5d, doses.get(42L), 0d);
        String encoded = (String) parser.javaToSqlArg(null, doses);
        Map<Long, Double> restored = (Map<Long, Double>) parser.sqlArgToJava(null, encoded, 0);
        assertEquals(doses, restored);
        assertNull(parser.sqlArgToJava(null, null, 0));
        assertNull("A historical literal null value remains readable",
                parser.sqlArgToJava(null, "null", 0));
    }

    @Test
    public void invalidMapMustRaiseCheckedSqlErrorWithoutReplacingDosages() throws Exception {
        DosageMapPersister parser = new DosageMapPersister();
        for (Object invalid : new Object[]{"not json", "{", "[]", "{\"42\":\"invalid\"}", 999}) {
            try {
                parser.sqlArgToJava(null, invalid, 0);
                fail("Corrupted schedule map must be rejected");
            } catch (SQLException expected) {
                // Fail closed; do not fabricate doses from broken SQLite.
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void legacyListRoundTripsAndDistinguishesAbsentFromEmpty() throws Exception {
        DosageListPersister parser = new DosageListPersister();
        assertNull(parser.sqlArgToJava(null, null, 0));
        assertEquals(Arrays.asList(1d, 0.125d),
                parser.sqlArgToJava(null, "1.0|0.125", 0));
        assertEquals(java.util.Collections.emptyList(),
                parser.sqlArgToJava(null, "", 0));
        List<Double> original = Arrays.asList(1d, 0.25d);
        assertEquals(original, parser.sqlArgToJava(null,
                parser.javaToSqlArg(null, original), 0));
    }

    @Test
    public void corruptLegacyDosesNeverBecomePartialPillSchedules() throws Exception {
        DosageListPersister parser = new DosageListPersister();
        for (Object invalid : new Object[]{"not a number", "1|bad", "1||2", "|1", "2|", 42}) {
            try {
                parser.sqlArgToJava(null, invalid, 0);
                fail("Malformed legacy dose entry accepted");
            } catch (SQLException expected) {
                // Preserve corrupt source data for explicit recovery.
            }
        }
    }
}
