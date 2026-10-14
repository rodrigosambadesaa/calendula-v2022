/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/** Pure owner/identity rule; no real patient records. */
public class PatientDetailEntryGuardTest {
    @Test
    public void newPatientIsAllowedOnlyUsingExplicitNewProfileSentinel() {
        assertTrue(PatientDetailEntryGuard.canOpen(-1L, null));
        assertFalse(PatientDetailEntryGuard.canOpen(-2L, null));
        assertFalse(PatientDetailEntryGuard.canOpen(0L, null));
    }

    @Test
    public void existingProfileRequiresSamePositivePersistedId() {
        assertTrue(PatientDetailEntryGuard.canOpen(301L, 301L));
        assertFalse(PatientDetailEntryGuard.canOpen(301L, null));
        assertFalse(PatientDetailEntryGuard.canOpen(301L, 302L));
        assertFalse(PatientDetailEntryGuard.canOpen(301L, -1L));
    }
}
