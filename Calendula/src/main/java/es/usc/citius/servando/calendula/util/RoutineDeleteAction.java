/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

/** Keep routine editing available when dependent-schedule deletion fails. */
public final class RoutineDeleteAction {
    private RoutineDeleteAction() {
    }

    public static boolean run(Runnable deletion) {
        if (deletion == null) {
            return false;
        }
        try {
            deletion.run();
            return true;
        } catch (RuntimeException expected) {
            // Do not log exception messages: they may contain prescription
            // metadata. Caller shows a localized, content-free error.
            return false;
        }
    }
}
