/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure failure injection, without Android alarms or real clinical records. */
public class AllergenDeletionPostCommitTest {

    @Test
    public void checkedFailureStillCleansLaterAllergensAndNotifiesMedicines() {
        List<String> calls = new ArrayList<>();
        boolean complete = AllergenDeletionPostCommit.reconcile(
                Arrays.asList("first", "second", "third"),
                item -> {
                    calls.add(item);
                    if ("first".equals(item)) {
                        throw new SQLException("synthetic first warning failure");
                    }
                },
                () -> calls.add("observer"));
        assertFalse("SQLite committed but warning recovery is incomplete", complete);
        assertEquals(Arrays.asList("first", "second", "third", "observer"), calls);
    }

    @Test
    public void runtimeFailureInLaterCleanupDoesNotSuppressMedicineObserver() {
        List<String> calls = new ArrayList<>();
        boolean complete = AllergenDeletionPostCommit.reconcile(
                Arrays.asList("first", "second"),
                item -> {
                    calls.add(item);
                    if ("second".equals(item)) {
                        throw new IllegalStateException("synthetic stale owner");
                    }
                },
                () -> calls.add("observer"));
        assertFalse(complete);
        assertEquals(Arrays.asList("first", "second", "observer"), calls);
    }

    @Test
    public void observerFailureCannotBeReportedAsFullRecovery() {
        List<String> calls = new ArrayList<>();
        boolean complete = AllergenDeletionPostCommit.reconcile(
                Arrays.asList("only"),
                calls::add,
                () -> {
                    calls.add("observer");
                    throw new IllegalStateException("synthetic failed observer");
                });
        assertFalse(complete);
        assertEquals(Arrays.asList("only", "observer"), calls);
    }

    @Test
    public void healthyBatchRunsEachCleanupAndObserverExactlyOnce() {
        List<String> calls = new ArrayList<>();
        boolean complete = AllergenDeletionPostCommit.reconcile(
                Arrays.asList("one", "two"),
                calls::add,
                () -> calls.add("observer"));
        assertTrue(complete);
        assertEquals(Arrays.asList("one", "two", "observer"), calls);
    }

    @Test
    public void invalidNullEntryCannotSilentlyCountAsRecovered() {
        List<String> calls = new ArrayList<>();
        boolean complete = AllergenDeletionPostCommit.reconcile(
                Arrays.asList("one", null, "two"), calls::add, null);
        assertFalse(complete);
        assertEquals(Arrays.asList("one", "two"), calls);
    }
}
