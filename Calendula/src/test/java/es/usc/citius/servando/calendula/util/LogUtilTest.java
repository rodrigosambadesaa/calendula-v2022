/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogUtilTest {

    @Test
    public void releaseBuildDisablesAppLogs() {
        assertFalse(LogUtil.logsEnabledForBuildType("release"));
        assertFalse(LogUtil.logsEnabledForBuildType("RELEASE"));
    }

    @Test
    public void nonReleaseBuildsKeepDeveloperLogs() {
        assertTrue(LogUtil.logsEnabledForBuildType("debug"));
        assertTrue(LogUtil.logsEnabledForBuildType("beta"));
        assertTrue(LogUtil.logsEnabledForBuildType("alpha"));
        assertTrue(LogUtil.logsEnabledForBuildType(""));
        assertTrue(LogUtil.logsEnabledForBuildType(null));
    }
}
