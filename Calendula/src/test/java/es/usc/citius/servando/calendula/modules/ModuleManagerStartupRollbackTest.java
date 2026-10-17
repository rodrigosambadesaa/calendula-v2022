package es.usc.citius.servando.calendula.modules;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Failed module startup must not expose a half-initialized module as active. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ModuleManagerStartupRollbackTest {

    @Test
    public void failedNewModuleIsNotReportedEnabled() {
        ModuleManager mgr = ModuleManager.getInstance();
        final String id = "test-startup-failed-module";
        mgr.run(module(id, true), null);
        assertFalse(mgr.isModuleEnabled(id));
    }

    @Test
    public void successfulModuleRemainsEnabled() {
        ModuleManager mgr = ModuleManager.getInstance();
        final String id = "test-startup-success-module";
        mgr.run(module(id, false), null);
        assertTrue(mgr.isModuleEnabled(id));
    }

    @Test
    public void failedReplacementRestoresPreviouslyInitializedModule() throws Exception {
        ModuleManager mgr = ModuleManager.getInstance();
        final String id = "test-startup-replaced-module";
        CalendulaModule original = module(id, false);
        mgr.run(original, null);
        mgr.run(module(id, true), null);
        assertTrue(mgr.isModuleEnabled(id));

        Field field = ModuleManager.class.getDeclaredField("modules");
        field.setAccessible(true);
        Map<?, ?> registrations = (Map<?, ?>) field.get(mgr);
        assertSame(original, registrations.get(id));
    }

    private static CalendulaModule module(final String id, final boolean failStartup) {
        return new CalendulaModule() {
            @Override public String getId() {
                return id;
            }

            @Override protected void onApplicationStartup(Context ctx) {
                if (failStartup) {
                    throw new IllegalStateException("synthetic startup failure");
                }
            }
        };
    }
}
