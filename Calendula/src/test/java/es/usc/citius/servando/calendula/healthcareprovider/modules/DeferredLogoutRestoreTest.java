/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.modules;

import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** No SQLite rows, active patient selection or real login state is touched. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DeferredLogoutRestoreTest {
    @Test
    public void explicitFalseOrMissingFlagKeepsExistingSelection() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.PATIENTS_REMOVE_ACTIVE_ON_BOOT, false))
                    .thenReturn(false);
            assertFalse(RemoveActivePatientOnBootModule.shouldRemoveActivePatient());
            new RemoveActivePatientOnBootModule().onApplicationStartup(null);
            prefs.verify(PreferenceUtils::edit, org.mockito.Mockito.never());
        }
    }

    @Test
    public void incompatibleFlagPerformsPendingLogoutWithoutDeletingAnyPatient() {
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(editor.remove(PreferenceKeys.PATIENTS_ACTIVE.key())).thenReturn(editor);
        when(editor.remove(PreferenceKeys.PATIENTS_REMOVE_ACTIVE_ON_BOOT.key()))
                .thenReturn(editor);
        when(editor.commit()).thenReturn(true);
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.PATIENTS_REMOVE_ACTIVE_ON_BOOT, false))
                    .thenThrow(new ClassCastException("Synthetic restored integer"));
            prefs.when(PreferenceUtils::edit).thenReturn(editor);
            assertTrue(RemoveActivePatientOnBootModule.shouldRemoveActivePatient());
            new RemoveActivePatientOnBootModule().onApplicationStartup(null);
            verify(editor).remove(PreferenceKeys.PATIENTS_ACTIVE.key());
            verify(editor).remove(PreferenceKeys.PATIENTS_REMOVE_ACTIVE_ON_BOOT.key());
            verify(editor).commit();
        }
    }

    @Test
    public void explicitTrueStillRemovesActiveSelectionOnStartup() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.PATIENTS_REMOVE_ACTIVE_ON_BOOT, false))
                    .thenReturn(true);
            assertTrue(RemoveActivePatientOnBootModule.shouldRemoveActivePatient());
        }
    }
}
