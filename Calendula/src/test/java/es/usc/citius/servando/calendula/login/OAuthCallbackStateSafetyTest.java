package es.usc.citius.servando.calendula.login;

import android.content.Intent;

import net.openid.appauth.AuthState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OAuthCallbackStateSafetyTest {
    @Test
    public void missingMalformedAndWrongTypeIntentExtrasCannotAuthenticate() {
        assertThrows(IllegalArgumentException.class,
                () -> LoginReceiveActivity.getAuthStateFromIntent(null));
        assertThrows(IllegalArgumentException.class,
                () -> LoginReceiveActivity.getAuthStateFromIntent(new Intent()));
        assertThrows(IllegalArgumentException.class,
                () -> LoginReceiveActivity.getAuthStateFromIntent(
                        new Intent().putExtra("authState", 42)));
        assertThrows(IllegalArgumentException.class,
                () -> LoginReceiveActivity.getAuthStateFromIntent(
                        new Intent().putExtra("authState", "{not valid JSON}")));
    }

    @Test
    public void oversizedCorruptStateIsRejectedBeforeParsing() {
        assertThrows(IllegalArgumentException.class,
                () -> LoginReceiveActivity.parseSavedAuthState(
                        new String(new char[262145]).replace((char) 0, 'z')));
    }

    @Test
    public void legitimateAppAuthStateStillRestoresForNormalSignIn() {
        String serialized = new AuthState().jsonSerializeString();
        assertNotNull(LoginReceiveActivity.parseSavedAuthState(serialized));
        assertNotNull(LoginReceiveActivity.getAuthStateFromIntent(
                new Intent().putExtra("authState", serialized)));
    }
}
