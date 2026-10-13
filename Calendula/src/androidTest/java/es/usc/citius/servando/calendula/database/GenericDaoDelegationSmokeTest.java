/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.j256.ormlite.dao.Dao;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** ORMLite delegation must not mutate any real medicine or patient records. */
@RunWith(AndroidJUnit4.class)
public class GenericDaoDelegationSmokeTest {

    @Test
    public void genericDaoExposesItsActualSQLiteTableName() {
        assertTrue(DB.initialized);
        assertEquals("EventReminders", DB.eventReminders().getTableName());
        assertEquals("EventInstances", DB.eventInstances().getTableName());
        assertEquals("PatientAlerts", DB.alerts().getTableName());
    }

    @Test
    public void explicitChangeNotificationsReachRegisteredDaoObservers() {
        assertTrue(DB.initialized);
        final AtomicInteger calls = new AtomicInteger();
        final Dao.DaoObserver observer = new Dao.DaoObserver() {
            @Override
            public void onChange() {
                calls.incrementAndGet();
            }
        };
        DB.eventReminders().registerObserver(observer);
        try {
            DB.eventReminders().notifyChanges();
            assertEquals("Wrapped ORMLite observers must receive explicit notifications",
                    1, calls.get());
        } finally {
            DB.eventReminders().unregisterObserver(observer);
        }
    }
}
