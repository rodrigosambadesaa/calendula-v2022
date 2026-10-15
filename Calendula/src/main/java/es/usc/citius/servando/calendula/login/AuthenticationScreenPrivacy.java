/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.login;

import android.app.Activity;
import android.view.WindowManager;

import es.usc.citius.servando.calendula.util.SecureWindowPolicy;

/**
 * OAuth and startup activities do not subclass CalendulaActivity. Apply the
 * same explicit screenshot policy before their UI, token callback or dialogs
 * become visible, without changing a user's opt-out.
 */
public final class AuthenticationScreenPrivacy {
    private AuthenticationScreenPrivacy() {
    }

    public static void protectIfConfigured(Activity activity) {
        if (SecureWindowPolicy.isProtected()) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
}
