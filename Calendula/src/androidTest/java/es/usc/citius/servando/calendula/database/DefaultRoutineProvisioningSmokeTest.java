/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.joda.time.LocalTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import es.usc.citius.servando.calendula.DefaultDataGenerator;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.RepeatType;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Routine;
import static org.junit.Assert.*;

/** Regression for repeated/default-login bootstrapping on real Android SQLite. */
@RunWith(AndroidJUnit4.class)
public class DefaultRoutineProvisioningSmokeTest {
    @Test
    public void multipleBootstrapCallsPreserveCustomMealAndAvoidDuplicateDefaults() throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String seed = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-meals-owner-" + seed);
        owner.setName("Synthetic meal owner");
        Patient other = new Patient();
        other.setCode("ci-meals-other-" + seed);
        other.setName("Synthetic meal unrelated");
        Routine customizedBreakfast = new Routine(owner, new LocalTime(10, 25),
                "My own breakfast time");
        customizedBreakfast.setDailyEventType(RepeatType.CM);
        Routine otherLunch = new Routine(other, new LocalTime(14, 5),
                "Another owner's lunch");
        otherLunch.setDailyEventType(RepeatType.CD);
        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.patients().create(other));
            assertEquals(1, DB.routines().create(customizedBreakfast));
            assertEquals(1, DB.routines().create(otherLunch));

            DefaultDataGenerator.generateDefaultRoutines(owner, context);
            DefaultDataGenerator.generateDefaultRoutines(owner, context);
            List<Routine> routines = DB.routines().findAll(owner.getId());
            assertEquals("Repeated login/bootstrap must produce exactly three meal routines",
                    3, routines.size());
            assertEquals(customizedBreakfast.getId(),
                    DB.routines().findByPatientAndDailyEvent(owner, RepeatType.CM).getId());
            assertEquals(new LocalTime(10, 25),
                    DB.routines().findById(customizedBreakfast.getId()).getTime());
            assertEquals("My own breakfast time",
                    DB.routines().findById(customizedBreakfast.getId()).getName());
            assertNotNull(DB.routines().findByPatientAndDailyEvent(owner, RepeatType.CD));
            assertNotNull(DB.routines().findByPatientAndDailyEvent(owner, RepeatType.CV));
            assertEquals("Another patient must not be provisioned implicitly", 1,
                    DB.routines().findAll(other.getId()).size());

            boolean rejected = false;
            try {
                DefaultDataGenerator.generateDefaultRoutines(new Patient(), context);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            assertTrue("An unsaved patient must not cause orphan default routines", rejected);
        } finally {
            for (Patient patient : new Patient[]{owner, other}) {
                if (patient.getId() != null) {
                    for (Routine routine : DB.routines().findAll(patient.getId())) {
                        DB.routines().remove(routine);
                    }
                    if (DB.patients().findById(patient.getId()) != null) {
                        DB.patients().remove(patient);
                    }
                }
            }
        }
    }
}
