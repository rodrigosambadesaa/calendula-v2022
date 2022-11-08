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
    private List<DispensationInfoEntity> urgentMeds;
    private Map<LocalDate, List<DispensationInfoEntity>> pickupsMap = new HashMap<>();
    private Map<LocalDate, List<DispensationInfoEntity>> pickupsEndMap = new HashMap<>();
    private Pair<LocalDate, List<DispensationInfoEntity>> bestDay;

    public DispensationInfoStore(List<DispensationInfoEntity> dispensationInfoList) {
        this.dispensationInfoList = dispensationInfoList;
        this.bestDay = null;
        Collections.sort(this.dispensationInfoList, DispensationInfoEntity.DispensationEntityComparator.getInstance());
        pickupsMap.clear();

        for (DispensationInfoEntity entry : dispensationInfoList) {
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
        if (urgentMeds == null) {
            urgentMeds = new ArrayList<>();
            DateTime now = DateTime.now();
            for (DispensationInfoEntity p : dispensationInfoList) {
                if (!p.isDispensed() && p.getDispenseInterval().getEnd().isAfter(now) && p.getDispenseInterval().getStart().plusDays(MAX_DAYS - 3).isBefore(now)) {
                    urgentMeds.add(p);
                }
            }
        }
        return urgentMeds;
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
                    // get the pickup take secure interval
                    DateTime iStart = p.getDispenseInterval().getStart();
                    DateTime iEnd = p.getDispenseInterval().getStart().plusDays(MAX_DAYS - 1);
                    Interval interval = new Interval(iStart, iEnd);
                    // add the pickup to the daily list if we can take it
                    if (!p.isDispensed() && interval.contains(d.toDateTimeAtStartOfDay())) {
                        if (!bestDays.containsKey(d)) {
                            bestDays.put(d, new ArrayList<DispensationInfoEntity>());
                        }
                        bestDays.get(d).add(p);
                    }
                }
            }

            // select the day with the highest number of meds
            int bestDayCount = 0;
            LocalDate bestOption = null;
            Set<LocalDate> LocalDates = bestDays.keySet();
            ArrayList<LocalDate> sorted = new ArrayList<>(LocalDates);
            Collections.sort(sorted);
            for (LocalDate day : sorted) {
                List<DispensationInfoEntity> pks = bestDays.get(day);
                LogUtil.d(TAG, day.toString("dd/MM/YYYY") + ": " + pks.size());
                if (pks.size() >= bestDayCount) {
                    bestDayCount = pks.size();
                    bestOption = day;
                    if (bestOption.getDayOfWeek() == DateTimeConstants.SUNDAY) {
                        bestOption = bestOption.minusDays(1);
                    }
                }
            }
            if (bestOption != null) {
                this.bestDay = new Pair<>(bestOption, bestDays.get(bestOption));
                return this.bestDay;
            }
        }

        return null;
    }

    public Patient getPatient(DispensationInfoEntity p) {
        return p.getActiveMed().getPatient();
    }


    public List<DispensationInfoEntity> pickups() {
        return dispensationInfoList;
    }

    public DispensationInfoEntity lastPickedUp() {
        for (int i = dispensationInfoList.size() - 1; i >= 0; i--) {
            DispensationInfoEntity e = dispensationInfoList.get(i);
            if (e.isDispensed()) {
                return e;
            }
        }
        return null;
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
