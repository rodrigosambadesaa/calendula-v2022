package es.usc.citius.servando.calendula.healthcareprovider.util;

import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.ProviderRevisionGuard;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RemoteRevisionCommitterTest {
    private static final String OPAQUE_OWNER =
            ProviderRevisionGuard.ownerKey(123L, "synthetic-owner");

    private static SharedPreferences.Editor editor(boolean successful) {
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(editor.putString(any(String.class), any())).thenReturn(editor);
        when(editor.commit()).thenReturn(successful);
        return editor;
    }

    @Test
    public void ownerAndRevisionMustBeCommittedTogetherWithoutAsyncApply() {
        SharedPreferences.Editor editor = editor(true);
        RemoteRevisionCommitter.commit(editor, OPAQUE_OWNER, "synthetic-revision");
        verify(editor).putString(PreferenceKeys.REMOTE_LAST_HASH_OWNER.key(),
                OPAQUE_OWNER);
        verify(editor).putString(PreferenceKeys.REMOTE_LAST_HASH.key(),
                "synthetic-revision");
        verify(editor).putString(PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_SUMMARY.key(), "");
        verify(editor).putString(eq(
                PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_SUMMARY_OWNER.key()), isNull());
        verify(editor, times(1)).commit();
        verify(editor, never()).apply();
    }

    @Test
    public void failedDurableWriteMustNotBeAcknowledgedAsSuccessful() {
        SharedPreferences.Editor editor = editor(false);
        try {
            RemoteRevisionCommitter.commit(editor, OPAQUE_OWNER, "revision");
            fail("A failed disk write must keep the provider revision retryable");
        } catch (IllegalStateException expected) {
            // Never publish success, notifications or completion on failure.
        }
        verify(editor, times(1)).commit();
        verify(editor, never()).apply();
    }

    @Test
    public void absentOwnerAndMissingEditorMustFailBeforeAnyWrite() {
        SharedPreferences.Editor editor = editor(true);
        for (String invalid : new String[]{null, "", " ",
                "123:synthetic-owner", "sha256-v2:invalid"}) {
            try {
                RemoteRevisionCommitter.commit(editor, invalid, "revision");
                fail("Unowned revision must not be persisted");
            } catch (IllegalArgumentException expected) {
                // Required patient scope.
            }
        }
        try {
            RemoteRevisionCommitter.commit(editor, OPAQUE_OWNER, null);
            fail("Missing revision must be rejected");
        } catch (IllegalArgumentException expected) {
            // Required source.
        }
        verifyNoInteractions(editor);
        try {
            RemoteRevisionCommitter.commit(null, OPAQUE_OWNER, "revision");
            fail("No editor must not be mistaken for successful persistence");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
