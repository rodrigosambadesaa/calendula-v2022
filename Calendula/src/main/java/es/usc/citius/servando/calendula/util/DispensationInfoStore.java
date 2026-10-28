/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses>.
 */

package es.usc.citius.servando.calendula.util;


import androidx.core.util.Pair;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.Interval;
import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;

public class DispensationInfoStore {

    private static final String TAG = "DispensationInfoStore";
    private final int MAX_DAYS = 10;

    private List<DispensationInfoEntity> dispensationInfoList;
    private Map<LocalDate, List<DispensationInfoEntity>> pickupsMap = new HashMap<>();
    private Map<LocalDate, List<DispensationInfoEntity>> pickupsEndMap = new HashMap<>();
    private Pair<LocalDate, List<DispensationInfoEntity>> bestDay;

    public DispensationInfoStore(List<DispensationInfoEntity> dispensationInfoList) {
        if (dispensationInfoList == null) {
            throw new IllegalArgumentException("Provider dispensation collection is missing");
        }
        // Snapshot provider rows before sorting. The caller may supply an
        // unmodifiable list or share it with a concurrent UI refresh; never
        // reorder or retain its mutable list as our own calendar model.
        this.dispensationInfoList = new ArrayList<>(dispensationInfoList);
        for (DispensationInfoEntity entry : this.dispensationInfoList) {
            // A partially imported interval must invalidate this entire
            // snapshot, not silently produce a partial pickup recommendation.
            if (entry == null || entry.getDispenseInterval() == null) {
                throw new IllegalArgumentException("Provider dispensation interval is missing");
            }
        }
        this.bestDay = null;
        Collections.sort(this.dispensationInfoList, DispensationInfoEntity.DispensationEntityComparator.getInstance());
        pickupsMap.clear();

        for (DispensationInfoEntity entry : this.dispensationInfoList) {
            if (!pickupsMap.containsKey(entry.getDispenseInterval().getStart().toLocalDate())) {
                pickupsMap.put(entry.getDispenseInterval().getStart().toLocalDate(), new ArrayList<DispensationInfoEntity>());
            }
            if (!pickupsEndMap.containsKey(entry.getDispenseInterval().getEnd().toLocalDate())) {
                pickupsEndMap.put(entry.getDispenseInterval().getEnd().toLocalDate(), new ArrayList<DispensationInfoEntity>());
            }
            pickupsMap.get(entry.getDispenseInterval().getStart().toLocalDate()).add(entry);
            pickupsEndMap.get(entry.getDispenseInterval().getEnd().toLocalDate()).add(entry);
        }

    }

    /**
     * Get urgent meds, that are meds we can take within a margin of one or two days
     * before the next intake is delayed
     *
     * @return the meds
     */
    public List<DispensationInfoEntity> urgentMeds() {
        // Refresh against the current clock: a calendar can remain open
        // overnight and provider pickup windows need not last ten days.
        DateTime now = DateTime.now();
        List<DispensationInfoEntity> urgent = new ArrayList<>();
        for (DispensationInfoEntity pickup : dispensationInfoList) {
            if (isUrgentWithinProviderWindow(pickup, now, 3)) {
                urgent.add(pickup);
            }
        }
        return urgent;
    }

    /**
     * A short provider window must become urgent near its actual expiry.
     * Never classify a future, expired, dispensed or unverified interval as
     * a currently eligible pickup. This only affects the presentation badge;
     * it does not authorize a pharmacy to dispense medication.
     */
    static boolean isUrgentWithinProviderWindow(
            DispensationInfoEntity pickup, DateTime now, int daysBeforeEnd) {
        if (pickup == null || pickup.isDispensed() || now == null
                || daysBeforeEnd <= 0 || pickup.getDispenseInterval() == null) {
            return false;
        }
        Interval allowed = pickup.getDispenseInterval();
        return allowed.contains(now) &&
                !allowed.getEnd().isAfter(now.plusDays(daysBeforeEnd));
    }

    public Pair<LocalDate, List<DispensationInfoEntity>> getBestDay() {

        if (this.bestDay != null) {
            return this.bestDay;
        }

        HashMap<LocalDate, List<DispensationInfoEntity>> bestDays = new HashMap<>();
        if (dispensationInfoList.size() > 0) {
            LocalDate today = LocalDate.now();
            LocalDate first = LocalDate.now();
            LocalDate now = LocalDate.now().minusDays(MAX_DAYS);

            if (now.getDayOfWeek() == DateTimeConstants.SUNDAY) {
                now = now.plusDays(1);
            }

            // get the date of the first med we can take from 10 days ago
            for (DispensationInfoEntity p : dispensationInfoList) {
                if (p.getDispenseInterval().getStart().isAfter(now.toDateTimeAtStartOfDay()) && !p.isDispensed()) {
                    first = p.getDispenseInterval().getStart().toLocalDate();
                    break;
                }
            }

            for (int i = 0; i < 10; i++) {
                LocalDate d = first.plusDays(i);
                if (!d.isAfter(today) && d.getDayOfWeek() != DateTimeConstants.SUNDAY) {
                    // only take care of days after today that are not sundays
                    continue;
                }

                // compute the number of meds we cant take for each day
                for (DispensationInfoEntity p : dispensationInfoList) {
                    // Both the server-authorized interval AND our
                    // conservative local horizon must include the date.
                    // The old code checked only start+MAX_DAYS and could
                    // recommend dates after the provider's actual end.
                    if (eligibleWithinProviderInterval(p, d, MAX_DAYS)) {
                        if (!bestDays.containsKey(d)) {
                            bestDays.put(d, new ArrayList<DispensationInfoEntity>());
                        }
                        bestDays.get(d).add(p);
                    }
                }
            }

            // Choose only days actually considered eligible by the interval
            // checks above. Moving a Sunday candidate to Saturday without
            // rechecking every medication interval can recommend medicines
            // before their authorized pickup windows and lose their list.
            this.bestDay = chooseBestNonSundayPickup(bestDays);
            return this.bestDay;
        }

        return null;
    }

    /**
     * Local "best day" ranking may narrow provider authorization, never
     * extend it. Keep the previous conservative midnight instant check so
     * this correction cannot move a pickup recommendation earlier.
     */
    static boolean eligibleWithinProviderInterval(DispensationInfoEntity pickup,
                                                   LocalDate day, int maxDays) {
        if (pickup == null || pickup.isDispensed() || day == null || maxDays <= 1
                || pickup.getDispenseInterval() == null) {
            return false;
        }
        Interval authorized = pickup.getDispenseInterval();
        DateTime instant = day.toDateTimeAtStartOfDay();
        Interval localHorizon = new Interval(authorized.getStart(),
                authorized.getStart().plusDays(maxDays - 1));
        return authorized.contains(instant) && localHorizon.contains(instant);
    }

    /**
     * A recommendation must refer to a real eligible pickup day and that
     * day's exact medicines; never remap Sunday to an unchecked Saturday.
     * Ties preserve the existing latest-eligible-day selection.
     */
    static Pair<LocalDate, List<DispensationInfoEntity>> chooseBestNonSundayPickup(
            Map<LocalDate, List<DispensationInfoEntity>> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        ArrayList<LocalDate> days = new ArrayList<>(candidates.keySet());
        days.remove(null);
        Collections.sort(days);
        LocalDate bestDate = null;
        List<DispensationInfoEntity> bestMedicines = null;
        for (LocalDate day : days) {
            if (day == null || day.getDayOfWeek() == DateTimeConstants.SUNDAY) {
                continue;
            }
            List<DispensationInfoEntity> medicines = candidates.get(day);
            if (medicines == null || medicines.isEmpty()) {
                continue;
            }
            if (bestMedicines == null || medicines.size() >= bestMedicines.size()) {
                bestDate = day;
                bestMedicines = medicines;
            }
        }
        return bestDate == null ? null : new Pair<>(bestDate, new ArrayList<>(bestMedicines));
    }

    public Patient getPatient(DispensationInfoEntity p) {
        return p.getActiveMed().getPatient();
    }


    public List<DispensationInfoEntity> pickups() {
        return dispensationInfoList;
    }

    public DispensationInfoEntity lastPickedUp() {
        // Entries are sorted by their *eligible pickup interval*, not by when
        // medicines were actually dispensed. The latter is the only valid
        // ordering for the "last picked up" patient-facing summary.
        DispensationInfoEntity latest = null;
        for (DispensationInfoEntity entry : dispensationInfoList) {
            if (entry == null || !entry.isDispensed()
                    || entry.getDispensedDateTime() == null) {
                continue;
            }
            if (latest == null || entry.getDispensedDateTime()
                    .isAfter(latest.getDispensedDateTime())) {
                latest = entry;
            }
        }
        return latest;
    }

    public DispensationInfoEntity nextForPickUp(DateTime date) {
        for (DispensationInfoEntity e : dispensationInfoList) {
            if (!e.isDispensed()) {
                if (e.getDispenseInterval().contains(date)) {
                    return e;
                }
                DateTime from = e.getDispenseInterval().getStart();
                DateTime to = e.getDispenseInterval().getEnd();
                if (from.isAfter(date) || (from.isBefore(date) && to.isAfter(date))) {
                    return e;
                }
            }
        }

        return null;
    }


    public Map<LocalDate, List<DispensationInfoEntity>> pickupsMap() {
        return pickupsMap;
    }

    public Map<LocalDate, List<DispensationInfoEntity>> pickupsEndMap() {
        return pickupsEndMap;
    }


}
