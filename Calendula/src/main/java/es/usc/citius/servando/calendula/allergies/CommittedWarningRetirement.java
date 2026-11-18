/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.allergies;

import java.util.List;
import java.util.function.Consumer;

/**
 * Run each already-committed warning retirement independently. An individual
 * post-COMMIT failure must not suppress other retirements or observer updates.
 * The first failure remains explicit so callers never suggest retrying the
 * entire allergen database deletion.
 */
final class CommittedWarningRetirement {
    private CommittedWarningRetirement() {
    }

    static <T> void finish(List<T> toRemove, Consumer<T> removeOne,
                           Runnable notifyWarnings) {
        if (toRemove == null || removeOne == null) {
            throw new IllegalArgumentException("Warning retirements must be verifiable");
        }
        RuntimeException firstFailure = null;
        for (T warning : toRemove) {
            try {
                if (warning == null) {
                    throw new IllegalStateException("Missing committed warning snapshot");
                }
                removeOne.accept(warning);
            } catch (RuntimeException failure) {
                if (firstFailure == null) {
                    firstFailure = failure;
                }
            }
        }
        if (notifyWarnings != null) {
            try {
                notifyWarnings.run();
            } catch (RuntimeException failure) {
                if (firstFailure == null) {
                    firstFailure = failure;
                }
            }
        }
        if (firstFailure != null) {
            throw new IllegalStateException(
                    "Warning SQL changes committed; post-commit recovery incomplete",
                    firstFailure);
        }
    }
}
