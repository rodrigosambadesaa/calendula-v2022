/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.download;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DBVersionManagerTest {

    @Test
    public void picksHighestCompatibleSchemaThreshold() {
        Map<Integer, String> versions = new HashMap<>();
        versions.put(1, "20220101");
        versions.put(3, "20261008");
        versions.put(4, "20270101");

        assertEquals("20261008", DBVersionManager.selectLastCompatibleVersion(versions, 3));
        assertEquals("20220101", DBVersionManager.selectLastCompatibleVersion(versions, 2));
        assertNull(DBVersionManager.selectLastCompatibleVersion(versions, 0));
    }

    @Test
    public void missingOrEmptyManifestIsRejected() {
        assertNull(DBVersionManager.selectLastCompatibleVersion(null, 3));
        assertNull(DBVersionManager.selectLastCompatibleVersion(new HashMap<Integer, String>(), 3));
    }

    @Test
    public void invalidSelectedVersionIsRejectedWithoutFallingBackToOlderData() {
        Map<Integer, String> versions = new HashMap<>();
        versions.put(1, "20220101");
        versions.put(3, "../20261008");

        assertNull(DBVersionManager.selectLastCompatibleVersion(versions, 3));
    }

    @Test
    public void archiveVersionRejectsUnsafePathAndMalformedCalendarDate() {
        assertTrue(DBVersionManager.isValidDatabaseVersion("20261008"));
        assertFalse(DBVersionManager.isValidDatabaseVersion(null));
        assertFalse(DBVersionManager.isValidDatabaseVersion(""));
        assertFalse(DBVersionManager.isValidDatabaseVersion("20260230"));
        assertFalse(DBVersionManager.isValidDatabaseVersion("20261301"));
        assertFalse(DBVersionManager.isValidDatabaseVersion("2026-10-08"));
        assertFalse(DBVersionManager.isValidDatabaseVersion("20261008/../../evil"));
        assertFalse(DBVersionManager.isValidDatabaseVersion("20261008?target=evil"));
        assertFalse(DBVersionManager.isValidDatabaseVersion("20261008%2F.."));
    }

    @Test
    public void nullThresholdIsRejected() {
        Map<Integer, String> versions = new HashMap<>();
        versions.put(null, "20261008");

        assertNull(DBVersionManager.selectLastCompatibleVersion(versions, 3));
    }
}
