package es.usc.citius.servando.calendula.database;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.j256.ormlite.dao.Dao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.sql.SQLException;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

/**
 * Regression reproducer for issue #946. Synthetic owners only.
 * A restored preference containing a recycled numeric ID must never select
 * the replacement patient's clinical context without a stable owner binding.
 *
 * Uses only synthetic owner codes. A missing binding requires explicit reselection.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PatientRestoredSelectionIsolationRegressionTest {
    @Test
    public void bindingIsFixedLengthAndDoesNotStoreRawProviderSubject() {
        String subject = "synthetic-provider-subject";
        String digest = PatientDao.stablePatientDigest(subject);
        assertEquals(64, digest.length());
        assertTrue(digest.matches("[0-9a-f]{64}"));
        assertNotEquals(subject, digest);
        assertNotEquals(digest, PatientDao.stablePatientDigest("different-subject"));
        assertNull(PatientDao.stablePatientDigest("  "));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void validBindingAllowsOwnerButNotRecycledRowOrCorruptBinding()
            throws SQLException {
        final long id = 98602L;
        Dao<Patient, Long> raw = (Dao<Patient, Long>) mock(Dao.class);
        DatabaseHelper helper = mock(DatabaseHelper.class);
        when(helper.getPatientDao()).thenReturn(raw);
        PatientDao patients = new PatientDao(helper);
        Patient current = new Patient();
        current.setId(id);
        current.setCode("local-synthetic-original");
        Patient recycled = new Patient();
        recycled.setId(id);
        recycled.setCode("local-synthetic-different");
        when(raw.queryForId(id)).thenReturn(current, current, recycled, recycled);

        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getLong(
                    PreferenceKeys.PATIENTS_ACTIVE, -1L)).thenReturn(id);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.PATIENTS_ACTIVE_OWNER, null))
                    .thenReturn(PatientDao.stablePatientDigest(current.getCode()));
            assertSame(current, patients.getActive(null));
            assertTrue(patients.isActive(current, null));
            assertNull("An equal row ID never bypasses an owner-code mismatch",
                    patients.getActive(null));
            assertFalse(patients.isActive(recycled, null));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void restoredNumericIdMustNotSelectReplacementOwner() throws SQLException {
        final long recycledId = 98601L;
        Dao<Patient, Long> raw = (Dao<Patient, Long>) mock(Dao.class);
        DatabaseHelper helper = mock(DatabaseHelper.class);
        when(helper.getPatientDao()).thenReturn(raw);
        PatientDao patients = new PatientDao(helper);

        Patient replacement = new Patient();
        replacement.setId(recycledId);
        replacement.setCode("local-synthetic-replacement-owner");
        when(raw.queryForId(recycledId)).thenReturn(replacement);
        Context context = ApplicationProvider.getApplicationContext();

        // Simulates preferences retained across database restore: the
        // original owner's ID survives, but their stable identity does not.
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getLong(
                    PreferenceKeys.PATIENTS_ACTIVE, -1)).thenReturn(recycledId);
            assertNull("Recycled row ID must require explicit profile reselection",
                    patients.getActive(context));
        }
    }
}
