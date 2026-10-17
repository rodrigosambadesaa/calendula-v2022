package es.usc.citius.servando.calendula.login;

import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.openid.appauth.AuthState;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class OAuthCallbackStateSmokeTest {
    @Test
    public void malformedSavedCallbackNeverCreatesAuthorizationState() {
        Intent malformed = new Intent().putExtra("authState", true);
        try {
            LoginReceiveActivity.getAuthStateFromIntent(malformed);
            fail("Corrupt restored callback must be rejected");
        } catch (IllegalArgumentException expected) {
            // The callback only returns to login; no credentials are written.
        }
    }

    @Test
    public void emptyNormalAppAuthStateCanBeDeserialized() {
        String state = new AuthState().jsonSerializeString();
        assertNotNull(LoginReceiveActivity.getAuthStateFromIntent(
                new Intent().putExtra("authState", state)));
    }
}
