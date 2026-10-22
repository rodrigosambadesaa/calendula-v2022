package es.usc.citius.servando.calendula.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mockStatic;

/** No real credentials, prescriptions, or HTTP requests. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ProviderRevisionGuardTest {

    @Test
    public void onlyIdenticalAccountAndPersistedPatientCanReuseRemoteRevision() {
        Patient a = patient(101L, "account-a");
        Patient b = patient(102L, "account-b");
        String aOwner = ProviderRevisionGuard.ownerKey(101L, "account-a");

        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.REMOTE_LAST_HASH_OWNER, null)).thenReturn(aOwner);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.REMOTE_LAST_HASH, null)).thenReturn("opaque-revision-A");

            assertEquals("opaque-revision-A",
                    ProviderRevisionGuard.lastRevisionForPatient(a, "account-a"));
            assertNull(ProviderRevisionGuard.lastRevisionForPatient(b, "account-b"));
            assertNull(ProviderRevisionGuard.lastRevisionForPatient(a, "account-b"));
            assertNull(ProviderRevisionGuard.lastRevisionForPatient(null, "account-a"));
            assertNull(ProviderRevisionGuard.lastRevisionForPatient(a, null));
        }
    }

    @Test
    public void legacyAndMalformedOwnerCannotAuthorizeIncrementalSync() {
        Patient a = patient(101L, "account-a");
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.REMOTE_LAST_HASH, null)).thenReturn("legacy-revision");
            for (String invalidOwner : new String[]{null, "", "101:account-b", "102:account-a"}) {
                prefs.when(() -> PreferenceUtils.getString(
                        PreferenceKeys.REMOTE_LAST_HASH_OWNER, null)).thenReturn(invalidOwner);
                assertNull(ProviderRevisionGuard.lastRevisionForPatient(a, "account-a"));
            }
        }
        assertNull(ProviderRevisionGuard.ownerKey(null, "account-a"));
        assertNull(ProviderRevisionGuard.ownerKey(0L, "account-a"));
        assertNull(ProviderRevisionGuard.ownerKey(101L, "  "));
        assertFalse(ProviderRevisionGuard.isSameOwner(null, null));
        assertTrue(ProviderRevisionGuard.isSameOwner("101:account-a", "101:account-a"));
    }

    private static Patient patient(Long id, String code) {
        Patient p = new Patient();
        p.setId(id);
        p.setCode(code);
        return p;
    }
}
