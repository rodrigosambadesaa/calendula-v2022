/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression coverage for comparator antisymmetry, reflexivity and transitivity. */
public class DailyAgendaOrderingTest {
    private static final class Row {
        final long time;
        final boolean spacer;
        final boolean events;

        Row(long time, boolean spacer, boolean events) {
            this.time = time;
            this.spacer = spacer;
            this.events = events;
        }
    }

    private static int compare(Row a, Row b) {
        return DailyAgendaOrdering.compareStub(
                a.time, a.spacer, a.events, b.time, b.spacer, b.events);
    }

    @Test
    public void identicalSpacerAndMedicationRowsMustCompareEqual() {
        for (boolean spacer : new boolean[]{false, true}) {
            for (boolean events : new boolean[]{false, true}) {
                assertEquals(0, DailyAgendaOrdering.compareStub(
                        1000L, spacer, events, 1000L, spacer, events));
            }
        }
        assertTrue(DailyAgendaOrdering.compareStub(
                1000L, true, false, 1000L, false, true) < 0);
        assertTrue(DailyAgendaOrdering.compareStub(
                1000L, false, true, 1000L, false, false) < 0);
    }

    @Test
    public void agendaOrderingIsAntisymmetricAndTransitive() {
        Row[] rows = {
                new Row(1L, true, false), new Row(1L, true, false),
                new Row(1L, false, true), new Row(1L, false, true),
                new Row(1L, false, false), new Row(2L, true, false),
                new Row(2L, false, true), new Row(3L, false, false)
        };
        for (Row a : rows) {
            assertEquals(0, compare(a, a));
            for (Row b : rows) {
                assertEquals(Integer.signum(compare(a, b)),
                        -Integer.signum(compare(b, a)));
                for (Row c : rows) {
                    if (compare(a, b) <= 0 && compare(b, c) <= 0) {
                        assertTrue(compare(a, c) <= 0);
                    }
                }
            }
        }
    }

    private static final class Dose {
        final String minute;
        final boolean taken;
        final String medicine;

        Dose(String minute, boolean taken, String medicine) {
            this.minute = minute;
            this.taken = taken;
            this.medicine = medicine;
        }
    }

    private static int compare(Dose a, Dose b) {
        return DailyAgendaOrdering.compareElement(
                a.minute, a.taken, a.medicine, b.minute, b.taken, b.medicine);
    }

    @Test
    public void matchingUntakenDosesCompareEqualAndHandledOnesSortFirst() {
        Dose pending = new Dose("30", false, "A");
        Dose taken = new Dose("30", true, "A");
        assertEquals(0, compare(pending, pending));
        assertEquals(0, compare(new Dose("30", false, "A"), pending));
        assertTrue(compare(taken, pending) < 0);
        assertTrue(compare(pending, taken) > 0);
    }

    @Test
    public void doseOrderingIsAntisymmetricAndTransitiveWithMissingLegacyFields() {
        Dose[] rows = {
                new Dose("00", true, "A"), new Dose("00", true, "B"),
                new Dose("00", false, "A"), new Dose("00", false, "A"),
                new Dose("30", false, "A"), new Dose("30", true, null),
                new Dose(null, false, "A")
        };
        for (Dose a : rows) {
            assertEquals(0, compare(a, a));
            for (Dose b : rows) {
                assertEquals(Integer.signum(compare(a, b)),
                        -Integer.signum(compare(b, a)));
                for (Dose c : rows) {
                    if (compare(a, b) <= 0 && compare(b, c) <= 0) {
                        assertTrue(compare(a, c) <= 0);
                    }
                }
            }
        }
    }
}
