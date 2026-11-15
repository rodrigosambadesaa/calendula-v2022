package es.usc.citius.servando.calendula.database;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import static org.junit.Assert.*;

/** Uses only a disposable synthetic patient on the CI emulator SQLite DB. */
@RunWith(AndroidJUnit4.class)
public class DeletedActivePatientSmokeTest {
    @Test
    public void staleSelectionCannotActivateRemovedPatient() throws Exception {
        assertTrue(DB.initialized);
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = PreferenceUtils.instance().preferences();
        String key = PreferenceKeys.PATIENTS_ACTIVE.key();
        String bindingKey = PreferenceKeys.PATIENTS_ACTIVE_OWNER.key();
        Object previousSelection = prefs.getAll().get(key);
        Object previousBinding = prefs.getAll().get(bindingKey);
        String markerKey = PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key();
        Object previousMarker = prefs.getAll().get(markerKey);
        Patient disposable = new Patient();
        disposable.setName("Synthetic deleted patient");
        disposable.setCode("ci-deleted-active-" + System.nanoTime());
        try {
            assertEquals(1, DB.patients().create(disposable));
            assertNotNull(disposable.getId());
            assertTrue(prefs.edit()
                    .putLong(key, disposable.getId())
                    .putString(bindingKey, PatientDao.stablePatientDigest(disposable.getCode()))
                    .commit());
            assertTrue(DB.patients().isActive(disposable, ctx));
            DB.patients().remove(disposable);
            assertFalse("A saved ID alone must not authorize a deleted patient",
                    DB.patients().isActive(disposable, ctx));
        } finally {
            if (disposable.getId() != null
                    && DB.patients().findById(disposable.getId()) != null) {
                DB.patients().remove(disposable);
            }
            SharedPreferences.Editor e = prefs.edit();
            if (previousSelection == null) e.remove(key);
            else if (previousSelection instanceof Long) e.putLong(key, (Long) previousSelection);
            else if (previousSelection instanceof String) e.putString(key, (String) previousSelection);
            else if (previousSelection instanceof Integer) e.putInt(key, (Integer) previousSelection);
            else if (previousSelection instanceof Boolean) e.putBoolean(key, (Boolean) previousSelection);
            else throw new AssertionError("Unexpected saved active profile type");
            if (previousBinding == null) e.remove(bindingKey);
            else if (previousBinding instanceof String) e.putString(bindingKey, (String) previousBinding);
            else if (previousBinding instanceof Long) e.putLong(bindingKey, (Long) previousBinding);
            else if (previousBinding instanceof Boolean) e.putBoolean(bindingKey, (Boolean) previousBinding);
            else if (previousBinding instanceof Integer) e.putInt(bindingKey, (Integer) previousBinding);
            else throw new AssertionError("Unexpected saved active owner binding type");
            if (previousMarker == null) e.remove(markerKey);
            else if (previousMarker instanceof Boolean)
                e.putBoolean(markerKey, (Boolean) previousMarker);
            else if (previousMarker instanceof String)
                e.putString(markerKey, (String) previousMarker);
            else if (previousMarker instanceof Integer)
                e.putInt(markerKey, (Integer) previousMarker);
            else throw new AssertionError("Unexpected selection recovery marker type");
            assertTrue(e.commit());
        }
    }
}
