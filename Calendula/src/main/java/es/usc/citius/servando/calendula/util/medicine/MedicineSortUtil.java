/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
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
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.util.medicine;

import androidx.annotation.StringRes;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.Presentation;

public class MedicineSortUtil {

    public enum MedSortType {
        NAME(R.string.sort_by_name, new MedicineNameComparator()),
        PRESENTATION(R.string.sort_by_presentation, new MedicinePresentationComparator()),
        ALERTS(R.string.sort_by_alerts, new MedicineAlertComparator()),
        ACTIVE_MED(R.string.healthcare_provider, new MedicineActiveMedComparator());

        private int displayName;
        private Comparator<Medicine> comparator;

        MedSortType(@StringRes int displayName, Comparator<Medicine> comparator) {
            this.displayName = displayName;
            this.comparator = comparator;
        }

        public @StringRes int getDisplayNameRes() {
            return this.displayName;
        }

        public Comparator<Medicine> comparator() {
            // A fresh comparator for each sort avoids retaining clinical alert
            // summaries across patients or subsequent database mutations.
            return this == ALERTS ? new MedicineAlertComparator() : comparator;
        }
    }

    /**
     * Sort legacy/partially imported records without crashing on missing
     * names or presentations. Unknown values sort after known values.
     */
    private static int compareNullableMedicines(Medicine left, Medicine right) {
        if (left == right) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return Integer.MIN_VALUE; // Both records exist; compare their fields.
    }

    private static class MedicineNameComparator implements Comparator<Medicine> {
        @Override
        public int compare(Medicine o1, Medicine o2) {
            int missing = compareNullableMedicines(o1, o2);
            if (missing != Integer.MIN_VALUE) return missing;
            String name1 = o1.getName();
            String name2 = o2.getName();
            if (name1 == name2) return 0;
            if (name1 == null) return 1;
            if (name2 == null) return -1;
            return name1.compareTo(name2);
        }
    }

    private static class MedicinePresentationComparator implements Comparator<Medicine> {
        @Override
        public int compare(Medicine o1, Medicine o2) {
            int missing = compareNullableMedicines(o1, o2);
            if (missing != Integer.MIN_VALUE) return missing;
            Presentation p1 = o1.getPresentation() == null
                    ? Presentation.UNKNOWN : o1.getPresentation();
            Presentation p2 = o2.getPresentation() == null
                    ? Presentation.UNKNOWN : o2.getPresentation();
            return Integer.compare(p1.ordinal(), p2.ordinal());
        }
    }

    /**
     * Exposed as a package-level factory for deterministic cache tests. The
     * production provider always uses the validated, patient-scoped DAO.
     */
    static Comparator<Medicine> alertComparatorWith(
            Function<Medicine, List<PatientAlert>> provider) {
        return new MedicineAlertComparator(provider);
    }

    private static class MedicineAlertComparator implements Comparator<Medicine> {
        private final Function<Medicine, List<PatientAlert>> fetch;
        private final Map<Medicine, AlertRank> snapshot = new IdentityHashMap<>();

        MedicineAlertComparator() {
            this(m -> DB.alerts().findByMedicineSortByLevel(m));
        }

        MedicineAlertComparator(Function<Medicine, List<PatientAlert>> fetch) {
            this.fetch = fetch;
        }

        private AlertRank rank(Medicine medicine) {
            if (snapshot.containsKey(medicine)) {
                return snapshot.get(medicine);
            }
            // Preserve clinical ownership failures: a DB read error must not
            // silently turn a HIGH allergy alert into 'no alerts'.
            List<PatientAlert> alerts = fetch.apply(medicine);
            int maxLevel = 0;
            for (PatientAlert alert : alerts) {
                maxLevel = Math.max(maxLevel, alert.getLevel());
            }
            AlertRank rank = new AlertRank(alerts.size(), maxLevel);
            snapshot.put(medicine, rank);
            return rank;
        }

        @Override
        public int compare(Medicine o1, Medicine o2) {
            int missing = compareNullableMedicines(o1, o2);
            if (missing != Integer.MIN_VALUE) return missing;
            AlertRank a = rank(o1);
            AlertRank b = rank(o2);
            // Preserve legacy order: any alerted medicine sorts ahead of no
            // alerts, otherwise prioritize the highest clinical severity.
            if (a.count == 0 || b.count == 0) {
                return Integer.compare(b.count, a.count);
            }
            return Integer.compare(b.maxLevel, a.maxLevel);
        }
    }

    private static final class AlertRank {
        final int count;
        final int maxLevel;

        AlertRank(int count, int maxLevel) {
            this.count = count;
            this.maxLevel = maxLevel;
        }
    }

    private static class MedicineActiveMedComparator implements Comparator<Medicine> {

        @Override
        public int compare(Medicine o1, Medicine o2) {
            return Boolean.valueOf(o2.isFromActiveMed()).compareTo(o1.isFromActiveMed());
        }
    }

}
