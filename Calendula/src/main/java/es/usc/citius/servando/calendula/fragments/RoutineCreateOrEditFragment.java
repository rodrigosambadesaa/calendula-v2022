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

package es.usc.citius.servando.calendula.fragments;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.codetroopers.betterpickers.radialtimepicker.RadialTimePickerDialogFragment;
import com.codetroopers.betterpickers.timepicker.TimePickerBuilder;
import com.codetroopers.betterpickers.timepicker.TimePickerDialogFragment;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.util.KeyboardUtils;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.RoutineOwnerGuard;
import es.usc.citius.servando.calendula.util.Snack;


public class RoutineCreateOrEditFragment extends DialogFragment implements RadialTimePickerDialogFragment.OnTimeSetListener, TimePickerDialogFragment.TimePickerDialogHandler {

    private static final String TAG = "RoutineCoEFragment";

    OnRoutineEditListener mRoutineEditCallback;
    Routine mRoutine;


    @BindView(R.id.button2)
    Button timeButton;
    @BindView(R.id.routine_edit_name)
    TextView mNameTextView;

    Button mConfirmButton;

    Unbinder unbinder = null;

    int hour;
    int minute;

    int pColor;
    private boolean blockedByPatientChange;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_create_or_edit_routine, container, false);
        unbinder = ButterKnife.bind(this, rootView);

        Patient openingPatient = DB.patients().getActive(getActivity());
        pColor = RoutineOwnerGuard.hasActivePatient(openingPatient)
                ? openingPatient.getColor()
                : getResources().getColor(R.color.android_blue_dark);

        timeButton.setTextColor(pColor);

        long routineId = -1;

        if (getArguments() != null) {
            routineId = getArguments().getLong(CalendulaApp.INTENT_EXTRA_ROUTINE_ID, -1);
        }

        if (routineId == -1 && savedInstanceState != null) {
            routineId = savedInstanceState.getLong(CalendulaApp.INTENT_EXTRA_ROUTINE_ID, -1);
        }

        setRoutine(routineId);

        if (getDialog() != null) {
            getDialog().setTitle(R.string.title_create_routine_activity);
            mConfirmButton = (Button) rootView.findViewById(R.id.done_button);
            mConfirmButton.setVisibility(View.VISIBLE);
            mConfirmButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onEdit();
                }
            });
        }


        timeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                float density = getResources().getDisplayMetrics().densityDpi;
                LogUtil.d(TAG, "Density: " + density);
                if (density >= DisplayMetrics.DENSITY_XHIGH) {
                    RadialTimePickerDialogFragment timePickerFragment = new RadialTimePickerDialogFragment()
                            .setOnTimeSetListener(RoutineCreateOrEditFragment.this)
                            .setStartTime(hour, minute);
                    timePickerFragment.show(getActivity().getSupportFragmentManager(), "TIME_PICKER_FRAGMENT");
                } else {
                    TimePickerBuilder tpb = new TimePickerBuilder()
                            .setFragmentManager(getActivity().getSupportFragmentManager())
                            .setStyleResId(R.style.BetterPickersDialogFragment_Light);
                    tpb.addTimePickerDialogHandler(RoutineCreateOrEditFragment.this);
                    tpb.show();
                }

            }
        });

        updateTime();


        return rootView;
    }

    public void setRoutine(long routineId) {
        Patient active = DB.patients().getActive(getContext());
        Routine persisted = routineId == -1 ? null : DB.routines().findById(routineId);
        blockedByPatientChange = !RoutineOwnerGuard.hasActivePatient(active)
                || (routineId != -1 && !RoutineOwnerGuard.isOwnedBy(persisted, active));
        if (blockedByPatientChange) {
            // Never prefill the name of a stale/deleted/foreign routine.
            mRoutine = null;
            mNameTextView.setText("");
            mNameTextView.setEnabled(false);
            timeButton.setEnabled(false);
            if (mConfirmButton != null) {
                mConfirmButton.setEnabled(false);
            }
            LogUtil.w(TAG, "Routine editor unavailable for active patient");
            return;
        }
        mNameTextView.setEnabled(true);
        timeButton.setEnabled(true);
        if (mConfirmButton != null) {
            mConfirmButton.setEnabled(true);
        }
        if (routineId != -1) {
            mRoutine = persisted;
            if (mRoutine.getTime() == null) {
                blockedByPatientChange = true;
                mRoutine = null;
                mNameTextView.setText("");
                mNameTextView.setEnabled(false);
                timeButton.setEnabled(false);
                return;
            }
            hour = mRoutine.getTime().getHourOfDay();
            minute = mRoutine.getTime().getMinuteOfHour();
            setRoutine(mRoutine);
        } else {
            mRoutine = null;
            DateTime now = DateTime.now();
            hour = now.getHourOfDay();
            minute = now.getMinuteOfHour();
        }
    }

    public void setTime(LocalTime time) {
        hour = time.getHourOfDay();
        minute = time.getMinuteOfHour();
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mRoutine != null)
            outState.putLong(CalendulaApp.INTENT_EXTRA_ROUTINE_ID, mRoutine.getId());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (unbinder != null) {
            unbinder.unbind();
            unbinder = null;
        }
    }

    /**
     * SQLite may materialize the same routine ID as a different boxed Long.
     * A routine must not conflict with itself simply because the objects
     * are different; other existing routines must still be rejected.
     */
    static boolean conflictsWithEditedRoutine(Routine candidate, Routine edited) {
        if (candidate == null || candidate == edited) {
            return false;
        }
        Long candidateId = candidate.getId();
        Long editedId = edited == null ? null : edited.getId();
        return candidateId == null || editedId == null || !candidateId.equals(editedId);
    }

    public void onEdit() {

        String name = mNameTextView.getText().toString();
        Patient p = DB.patients().getActive(getContext());
        // A patient can be switched/deleted while this editor is open.
        // Re-read stored ownership immediately before any mutation.
        if (blockedByPatientChange || !RoutineOwnerGuard.hasActivePatient(p)
                || (mRoutine != null && (!RoutineOwnerGuard.isOwnedBy(mRoutine, p)
                    || !RoutineOwnerGuard.isOwnedBy(
                        DB.routines().findById(mRoutine.getId()), p)))) {
            Snack.show(R.string.routine_profile_unavailable, getActivity());
            return;
        }
        LocalTime time = new LocalTime(hour, minute);

        if (name != null && name.length() > 0) {
            // if editing
            if (mRoutine != null) {
                Routine pt = DB.routines().findByPatientAndTime(p, time);
                Routine pn = DB.routines().findByPatientAndName(name, p);
                if (conflictsWithEditedRoutine(pt, mRoutine)) {
                    Snack.show(getString(R.string.routine_time_already_exists, time.toString("HH:mm")), getActivity());
                } else if (conflictsWithEditedRoutine(pn, mRoutine)) {
                    Snack.show(getString(R.string.routine_name_already_exists, name), getActivity());
                } else {
                    mRoutine.setName(name);
                    mRoutine.setTime(time);
                    DB.routines().saveAndFireEvent(mRoutine);
                    if (mRoutineEditCallback != null) {
                        mRoutineEditCallback.onRoutineEdited(mRoutine);
                    }
                }
            }
            // if creating
            else {
                if (DB.routines().findByPatientAndTime(p, time) != null) {
                    Snack.show(getString(R.string.routine_time_already_exists, time.toString("HH:mm")), getActivity());
                } else if (DB.routines().findByPatientAndName(name, p) != null) {
                    Snack.show(getString(R.string.routine_name_already_exists, name), getActivity());
                } else {
                    mRoutine = new Routine(time, name);
                    mRoutine.setPatient(p);
                    LogUtil.d(TAG, "Routine created");
                    DB.routines().saveAndFireEvent(mRoutine);
                    if (mRoutineEditCallback != null) {
                        mRoutineEditCallback.onRoutineCreated(mRoutine);
                    }
                }
            }
            KeyboardUtils.hideKeyboard(getActivity());
        } else {
            Snack.show(R.string.medicine_no_name_error_message, getActivity());
        }
    }

    public void showDeleteConfirmationDialog(final Routine r) {
        Patient active = DB.patients().getActive(getContext());
        if (!RoutineOwnerGuard.isOwnedBy(r, active)
                || !RoutineOwnerGuard.isOwnedBy(
                        DB.routines().findById(r.getId()), active)) {
            Snack.show(R.string.routine_profile_unavailable, getActivity());
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        String message;

        if (ScheduleUtils.instance().schedulesLinkedTo(r) > 0) {
            message = String.format(getString(R.string.remove_routine_message_long), r.getName());
        } else {
            message = String.format(getString(R.string.remove_routine_message_short), r.getName());
        }

        builder.setMessage(message)
                .setCancelable(true)
                .setTitle(getString(R.string.remove_routine_dialog_title))
                .setPositiveButton(getString(R.string.dialog_yes_option), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (mRoutineEditCallback != null) {
                            mRoutineEditCallback.onRoutineDeleted(mRoutine);
                        }
                    }
                })
                .setNegativeButton(getString(R.string.dialog_no_option), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        LogUtil.d(TAG, "Activity " + context.getClass().getName() + ", " + (context instanceof OnRoutineEditListener));
        // If the container activity has implemented
        // the callback interface, set it as listener
        if (context instanceof OnRoutineEditListener) {
            LogUtil.d(TAG, "Set onRoutineEditListener onAttach");
            mRoutineEditCallback = (OnRoutineEditListener) context;
        }
    }

    // optionally set the listener manually
    public void setOnRoutineEditListener(OnRoutineEditListener l) {
        mRoutineEditCallback = l;
    }

    @Override
    public void onDialogTimeSet(int ref, int hour, int minute) {
        onTimeSet(null, hour, minute);
    }

    @Override
    public void onTimeSet(RadialTimePickerDialogFragment dialog, int hourOfDay, int minute) {
        this.hour = hourOfDay;
        this.minute = minute;
        updateTime();
    }

    void updateTime() {
        timeButton.setText(new LocalTime(hour, minute).toString("HH:mm"));
    }

    private void setRoutine(Routine r) {
        LogUtil.d(TAG, "Routine set: " + r.getName());
        mRoutine = r;
        mNameTextView.setText(mRoutine.getName());
        updateTime();
    }

    // Container Activity must implement this interface
    public interface OnRoutineEditListener {
        void onRoutineEdited(Routine r);

        void onRoutineCreated(Routine r);

        void onRoutineDeleted(Routine r);
    }

}
