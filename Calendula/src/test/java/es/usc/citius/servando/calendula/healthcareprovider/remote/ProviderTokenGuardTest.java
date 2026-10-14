/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.remote;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProviderTokenGuardTest {
    @Test
    public void noMissingOrWhitespaceBearerCredentialCanBeSentToProvider() {
        assertFalse(ProviderTokenGuard.isUsable(null));
        assertFalse(ProviderTokenGuard.isUsable(""));
        assertFalse(ProviderTokenGuard.isUsable("  "));
        assertFalse(ProviderTokenGuard.isUsable("part1\npart2"));
        assertFalse(ProviderTokenGuard.isUsable("part1\rpart2"));
        assertFalse(ProviderTokenGuard.isUsable("part1\tpart2"));
        assertFalse(ProviderTokenGuard.isUsable("bad bearer"));
        assertFalse(ProviderTokenGuard.isUsable("\u0000"));
        assertTrue(ProviderTokenGuard.isUsable("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.signature"));
        assertTrue(ProviderTokenGuard.isUsable("abc-._~+/123=="));
    }
}
