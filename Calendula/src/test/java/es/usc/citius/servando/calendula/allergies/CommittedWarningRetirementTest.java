/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.allergies;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Failure-injection tests: no real patient, medication or Android alarm. */
public class CommittedWarningRetirementTest {

    @Test
    public void firstRetirementFailureDoesNotSkipOthersOrAlertObservers() {
        List<String> calls = new ArrayList<>();
        RuntimeException problem = new IllegalStateException("synthetic first retirement");
        try {
            CommittedWarningRetirement.finish(
                    Arrays.asList("high-1", "high-2", "high-3"),
                    warning -> {
                        calls.add(warning);
                        if ("high-1".equals(warning)) {
                            throw problem;
                        }
                    },
                    () -> calls.add("alert-observer"));
            fail("A committed but unrecovered warning must not report success");
        } catch (IllegalStateException expected) {
            assertSame(problem, expected.getCause());
            assertTrue(expected.getMessage().contains("committed"));
        }
        assertEquals(Arrays.asList(
                "high-1", "high-2", "high-3", "alert-observer"), calls);
    }

    @Test
    public void secondRetirementFailureStillNotifiesAndPreservesFirstCause() {
        List<String> calls = new ArrayList<>();
        RuntimeException first = new IllegalStateException("synthetic second retirement");
        try {
            CommittedWarningRetirement.finish(Arrays.asList("one", "two"),
                    warning -> {
                        calls.add(warning);
                        if ("two".equals(warning)) {
                            throw first;
                        }
                    }, () -> {
                        calls.add("alert-observer");
                        throw new IllegalStateException("synthetic observer failure");
                    });
            fail("Incomplete recovery must not be reported as complete");
        } catch (IllegalStateException expected) {
            assertSame(first, expected.getCause());
        }
        assertEquals(Arrays.asList("one", "two", "alert-observer"), calls);
    }

    @Test
    public void observerFailureAfterHealthyRetirementsRemainsVisible() {
        List<String> calls = new ArrayList<>();
        RuntimeException observer = new IllegalStateException("synthetic observer");
        try {
            CommittedWarningRetirement.finish(Arrays.asList("one"),
                    calls::add,
                    () -> {
                        calls.add("alert-observer");
                        throw observer;
                    });
            fail("An observer failure after COMMIT needs recovery");
        } catch (IllegalStateException expected) {
            assertSame(observer, expected.getCause());
        }
        assertEquals(Arrays.asList("one", "alert-observer"), calls);
    }

    @Test
    public void normalRetirementsAndObserverRunOnceEach() {
        List<String> calls = new ArrayList<>();
        CommittedWarningRetirement.finish(Arrays.asList("one", "two"),
                calls::add, () -> calls.add("alert-observer"));
        assertEquals(Arrays.asList("one", "two", "alert-observer"), calls);
    }

    @Test
    public void medicineWarningCleanupContinuesWithoutAnExtraObserver() {
        // The medicine-specific path already delegates observer dispatch to
        // AlertManager.removeAlert, so it supplies no additional notifier.
        List<String> calls = new ArrayList<>();
        RuntimeException first = new IllegalStateException("synthetic post-commit failure");
        try {
            CommittedWarningRetirement.finish(Arrays.asList("first", "second", "third"),
                    warning -> {
                        calls.add(warning);
                        if ("first".equals(warning)) throw first;
                    }, null);
            fail("An already committed but unrecovered warning cannot report success");
        } catch (IllegalStateException expected) {
            assertSame(first, expected.getCause());
        }
        assertEquals(Arrays.asList("first", "second", "third"), calls);
    }

    @Test
    public void medicineWarningCleanupSucceedsWithoutAnExtraObserver() {
        List<String> calls = new ArrayList<>();
        CommittedWarningRetirement.finish(Arrays.asList("first", "second"),
                calls::add, null);
        assertEquals(Arrays.asList("first", "second"), calls);
    }

    @Test
    public void missingSnapshotFailsClosedButStillAttemptsOtherRetirements() {
        List<String> calls = new ArrayList<>();
        try {
            CommittedWarningRetirement.finish(Arrays.asList("one", null, "three"),
                    calls::add, () -> calls.add("alert-observer"));
            fail("A missing HIGH warning snapshot cannot be treated as removed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("committed"));
        }
        assertEquals(Arrays.asList("one", "three", "alert-observer"), calls);
    }
}
