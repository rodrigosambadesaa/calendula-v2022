package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Check the single-process serialization contract without modifying any
 * clinical SQLite data or scheduling real Android alarms.
 */
public class AgendaDailyUpdateConcurrencyTest {
    @Test
    public void dailyRebuildUsesTheSingletonMonitorForTheWholeCommitLifecycle()
            throws Exception {
        Method daily = Agenda.class.getMethod("onDailyUpdate", Context.class);
        assertTrue("Concurrent startup/midnight calls must never interleave SQLite generation",
                Modifier.isSynchronized(daily.getModifiers()));
        assertFalse("The lock must cover the single shared Agenda instance, not a class-wide static method",
                Modifier.isStatic(daily.getModifiers()));
        assertSame(Agenda.instance(), Agenda.instance());
    }
}
