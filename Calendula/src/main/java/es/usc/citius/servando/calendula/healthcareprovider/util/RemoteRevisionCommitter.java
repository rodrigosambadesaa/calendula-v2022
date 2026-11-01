/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.util;

import android.content.SharedPreferences;

import es.usc.citius.servando.calendula.util.PreferenceKeys;

/** A remote revision is acknowledged only after its owner is durably stored. */
public final class RemoteRevisionCommitter {
    private RemoteRevisionCommitter() { }

    /**
     * Called only after a successful SQLite provider reconciliation.
     * Preserve the owner/revision pair as one preference transaction and
     * clear stale clinical summaries in that same transaction.
     */
    public static void commit(SharedPreferences.Editor editor,
                              String authenticatedOwner, String revision) {
        if (editor == null || authenticatedOwner == null
                || authenticatedOwner.trim().isEmpty() || revision == null) {
            throw new IllegalArgumentException("Valid owner and revision are required");
        }
        boolean persisted = editor
                .putString(PreferenceKeys.REMOTE_LAST_HASH_OWNER.key(), authenticatedOwner)
                .putString(PreferenceKeys.REMOTE_LAST_HASH.key(), revision)
                .putString(PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_SUMMARY.key(), "")
                .putString(PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_SUMMARY_OWNER.key(), null)
                .commit();
        if (!persisted) {
            // Never log the revision, patient code, or provider payload.
            throw new IllegalStateException("Remote medication revision could not be persisted");
        }
    }
}
