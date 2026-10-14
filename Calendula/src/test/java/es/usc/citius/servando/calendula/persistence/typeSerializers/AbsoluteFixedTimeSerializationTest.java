/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence.typeSerializers;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.junit.Test;

import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;
import es.usc.citius.servando.calendula.util.GsonUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AbsoluteFixedTimeSerializationTest {
    @Test
    public void absoluteClockTimeAndDosageReferenceBothSurvivePersistence()
            throws Exception {
        DailyFixedTime slot = new DailyFixedTime(
                DailyFixedTime.ReferenceType.ABSOLUTE, new LocalTime(20, 37));
        slot.setReference(900371L);
        RecurringEvent recurrence = new RecurringEvent.Builder()
                .repeatDaily().from(new LocalDate(2026, 10, 14))
                .atFixedTime(slot).build();

        String json = GsonUtil.get().toJson(recurrence);
        assertTrue("Serialized dosage reference is retained",
                json.contains("\"ref\":900371"));
        assertTrue("Absolute time must not disappear when a reference exists",
                json.contains("\"time\":\"20:37\""));
        RecurringEvent restored = GsonUtil.get().fromJson(json, RecurringEvent.class);
        assertEquals(1, restored.getDailyFixedTimes().size());
        assertEquals(Long.valueOf(900371L),
                restored.getDailyFixedTimes().get(0).getReference());
        assertEquals(new LocalTime(20, 37),
                restored.getDailyFixedTimes().get(0).getTime());

        DateTime from = new LocalDate(2026, 10, 14).toDateTimeAtStartOfDay();
        assertFalse("Persisted recurrence can still generate the scheduled dose",
                restored.occurrencesIn(from, from.plusDays(2)).isEmpty());
    }

    @Test
    public void referenceOnlyAbsoluteTimeCannotBeSilentlyReconstructed() {
        // A historical serializer dropped the clock time when a reference
        // was present. Never invent or guess a medication administration hour.
        String corrupt = "{\"rule\":\"FREQ=DAILY\",\"dailyTimes\":["
                + "{\"refType\":\"ABSOLUTE\",\"ref\":900371}]}";
        try {
            GsonUtil.get().fromJson(corrupt, RecurringEvent.class);
            org.junit.Assert.fail("A missing historical dose time must be rejected");
        } catch (com.google.gson.JsonParseException expected) {
            // Gson wraps the adapter's checked IOException in a parse error.
        }
    }

    @Test
    public void invalidStoredRecurrenceCannotBecomeAnInventedDailyTreatment() {
        String corrupt = "{\"rule\":\"not-a-valid-recurrence-rule\"}";
        try {
            GsonUtil.get().fromJson(corrupt, RecurringEvent.class);
            org.junit.Assert.fail("A corrupt recurrence must not be replaced with DAILY");
        } catch (com.google.gson.JsonParseException expected) {
            // The original database bytes must be retained for explicit repair.
        }
    }

    @Test
    public void historicAbsoluteTimeWithoutReferenceStillRoundTrips()
            throws Exception {
        DailyFixedTime slot = new DailyFixedTime(
                DailyFixedTime.ReferenceType.ABSOLUTE, new LocalTime(9, 5));
        RecurringEvent event = new RecurringEvent.Builder()
                .repeatDaily().from(new LocalDate(2026, 10, 14))
                .atFixedTime(slot).build();
        String json = GsonUtil.get().toJson(event);
        assertTrue(json.contains("\"time\":\"09:05\""));
        RecurringEvent restored = GsonUtil.get().fromJson(json, RecurringEvent.class);
        assertEquals(new LocalTime(9, 5),
                restored.getDailyFixedTimes().get(0).getTime());
    }
}
