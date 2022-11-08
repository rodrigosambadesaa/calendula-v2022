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
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.activities.schedules;

import android.content.Context;
import androidx.fragment.app.Fragment;
import android.widget.AdapterView;
import android.widget.Spinner;

import com.stepstone.stepper.Step;

import org.joda.time.LocalTime;

import es.usc.citius.servando.calendula.activities.schedules.ScheduleBuildActivity.ScheduleStep;
import es.usc.citius.servando.calendula.activities.schedules.ScheduleBuildActivity.ScheduleVerificationError;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;

/**
 *
 */

public abstract class ScheduleBuildStepFragment extends Fragment implements Step {

    ScheduleBuildActivity buildActivity;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (getActivity() instanceof ScheduleBuildActivity) {
            this.buildActivity = (ScheduleBuildActivity) getActivity();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.buildActivity = null;
    }

    ScheduleVerificationError error(String err, ScheduleStep step) {
        return new ScheduleVerificationError(err, step);
    }

    void setSelectionSilent(Spinner s, int selection) {
        AdapterView.OnItemSelectedListener listener = s.getOnItemSelectedListener();
        s.setOnItemSelectedListener(null);
        s.setSelection(selection);
        s.setOnItemSelectedListener(listener);
    }

    Schedule schedule() {
        return buildActivity.tmpSchedule;
    }

    Double dosage(LocalTime time) {
        Double dosage = buildActivity.tmpSchedule.getDosage(time);
        if (dosage == null && buildActivity.editingSchedule != null) {
            dosage = buildActivity.editingSchedule.getDosage(time);
        }
        if (dosage == null) {
            dosage = 1d;
        }
        return dosage;
    }

    Double dosage() {
        return dosage(null);
    }

    void addDosage(Long ref, Double dosage) {
        buildActivity.tmpSchedule.getDosages().put(ref, dosage);
    }

    void removeDosage(Long ref) {
        if (buildActivity.tmpSchedule.getDosages().containsKey(ref)) {
            buildActivity.tmpSchedule.getDosages().remove(ref);
        }
    }

    protected void proceed() {
        if (buildActivity != null) {
            buildActivity.proceed();
        }
    }

    protected Patient getPatient() {
        return DB.patients().getActive(getContext());
    }

}