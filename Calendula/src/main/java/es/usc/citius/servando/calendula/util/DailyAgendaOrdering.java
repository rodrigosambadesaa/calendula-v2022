/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

/** Contract-compliant orderings for home agenda rows and individual doses. */
public final class DailyAgendaOrdering {
    private DailyAgendaOrdering() {
    }

    /** Chronological; at identical times spacer, medication group, then blank row. */
    public static int compareStub(long leftTimeMs, boolean leftSpacer, boolean leftEvents,
                                  long rightTimeMs, boolean rightSpacer, boolean rightEvents) {
        int result = Long.compare(leftTimeMs, rightTimeMs);
        if (result != 0) {
            return result;
        }
        if (leftSpacer != rightSpacer) {
            return leftSpacer ? -1 : 1;
        }
        if (leftEvents != rightEvents) {
            return leftEvents ? -1 : 1;
        }
        return 0;
    }

    /** Chronological minute; handled doses before pending; name as stable tiebreak. */
    public static int compareElement(String leftMinute, boolean leftTaken, String leftName,
                                     String rightMinute, boolean rightTaken, String rightName) {
        int result = compareNullable(leftMinute, rightMinute);
        if (result != 0) {
            return result;
        }
        if (leftTaken != rightTaken) {
            return leftTaken ? -1 : 1;
        }
        return compareNullable(leftName, rightName);
    }

    private static int compareNullable(String a, String b) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return a.compareTo(b);
    }
}
