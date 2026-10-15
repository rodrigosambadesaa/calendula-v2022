/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.download;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class InstallDatabaseServiceLifecycleTest {
    @After
    public void reset() {
        InstallDatabaseService.isRunning = false;
    }

    @Test
    public void unexpectedServiceDestructionClearsStaleCatalogBusyFlag() {
        // Simulate a service that did not reach onComplete/onFailure.
        org.robolectric.android.controller.ServiceController<InstallDatabaseService> service =
                Robolectric.buildService(InstallDatabaseService.class).create();
        InstallDatabaseService.isRunning = true;
        service.destroy();
        assertFalse("A destroyed installer must never report a running import",
                InstallDatabaseService.isRunning);
    }
}
