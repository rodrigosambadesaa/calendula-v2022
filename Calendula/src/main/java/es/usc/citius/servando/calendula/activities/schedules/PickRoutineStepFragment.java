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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.codetroopers.betterpickers.numberpicker.NumberPickerBuilder;
import com.codetroopers.betterpickers.numberpicker.NumberPickerDialogFragment;
import com.codetroopers.betterpickers.radialtimepicker.RadialTimePickerDialogFragment;
import com.codetroopers.betterpickers.timepicker.TimePickerBuilder;
import com.codetroopers.betterpickers.timepicker.TimePickerDialogFragment;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IAdapter;
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.listeners.OnClickListener;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.IIcon;
import com.shawnlin.numberpicker.NumberPicker;
import com.stepstone.stepper.Step;
import com.stepstone.stepper.VerificationError;

import org.greenrobot.eventbus.Subscribe;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.RoutinesActivity;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime;
import es.usc.citius.servando.calendula.util.Comparators;
import es.usc.citius.servando.calendula.util.LogUtil;

/**
 *
 */

public class PickRoutineStepFragment extends ScheduleBuildStepFragment implements Step,
        RadialTimePickerDialogFragment.OnTimeSetListener,
        TimePickerDialogFragment.TimePickerDialogHandler,
        NumberPickerDialogFragment.NumberPickerDialogHandlerV2 {

    public static final String TAG = "PickRoutineStepFragment";
    private final List<Integer> hours = getHours();
    @BindView(R.id.hourly_interval)
    NumberPicker hoursRv;
    @BindView(R.id.hourly_selection_indicator)
    ImageView hourlySelectionIndicator;
    @BindView(R.id.routines_rv)
    RecyclerView recyclerView;
    private String[] items;
    private Map<Long, DailyFixedTime> selected;
    private Map<Long, DailyFixedTime> times;
    private List<DailyFixedTime> timesList = new ArrayList<>();
    private boolean initialScrollDone = false;
    private boolean hourlySelection = false;
    private int selectedHours = 0;
    private FastItemAdapter<DailyFixedTimeItem> fastAdapter;

    private static final int DIALOG_MANUAL_HOURLY_INTERVAL = 1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_pick_routine_step, container, false);
        ButterKnife.bind(this, rootView);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            selected = new ArrayMap<>();
            times = new ArrayMap<>();
        } else {
            selected = new HashMap<>();
            times = new HashMap<>();
        }

        items = getResources().getStringArray(R.array.routine_offsets);
        setupRecyclerView();

        hoursRv.setMinValue(1);
        hoursRv.setMaxValue(hours.size());
        hoursRv.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker numberPicker, int prev, int current) {
                LogUtil.d(TAG, "ValueChange: " + current + ", selected: " + hours.get(current - 1));
                setSelectedHours(hours.get(current - 1));
            }
        });

        hourlySelectionIndicator.setImageDrawable(buildActivity.selectionIcon(hourlySelection));
        hourlySelectionIndicator.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hourlySelection) {
                    buildActivity.setDosage(dosage());
                    hourlySelection = true;
                    selectedHours = hours.get(hoursRv.getValue()-1);
                    hourlySelectionIndicator.setImageDrawable(buildActivity.selectionIcon(true));
                    selected.clear();
                    fastAdapter.notifyDataSetChanged();
                }
                updateActivity();
            }
        });

        CalendulaApp.eventBus().register(this);
        return rootView;
    }

    private void setSelectedHours(int hours) {
        selectedHours = hours;
        selected.clear();
        if (!hourlySelection) {
            buildActivity.setDosage(dosage());
            hourlySelection = true;
            hourlySelectionIndicator.setImageDrawable(buildActivity.selectionIcon(true));
            fastAdapter.notifyDataSetChanged();
        }
        updateActivity();
    }

    public void showAddNewDailyFixedTimeDialog() {
        DateTime now = DateTime.now();
        float density = getResources().getDisplayMetrics().densityDpi;
        if (density >= DisplayMetrics.DENSITY_XHIGH) {
            RadialTimePickerDialogFragment timePickerDialog =
                    new RadialTimePickerDialogFragment()
                            .setOnTimeSetListener(PickRoutineStepFragment.this)
                            .setStartTime(now.getHourOfDay(), now.getMinuteOfHour());
            timePickerDialog.show(getChildFragmentManager(), "111");
        } else {
            TimePickerBuilder tpb = new TimePickerBuilder()
                    .setFragmentManager(getChildFragmentManager())
                    .setStyleResId(R.style.BetterPickersDialogFragment_Light);
            tpb.addTimePickerDialogHandler(PickRoutineStepFragment.this);
            tpb.show();
        }
    }

    public void showDailyFixedTimeOffsetDialog(final DailyFixedTime dft) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.routine_offset_dialog_title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        EventInstance.EventOffset offset;
                        if (which == 1) {
                            offset = EventInstance.EventOffset.BEFORE;
                        } else if (which == 2) {
                            offset = EventInstance.EventOffset.NONE;
                        } else if (which == 3) {
                            offset = EventInstance.EventOffset.AFTER;
                        } else {
                            offset = null;
                        }

                        dft.setOffset(offset);
                        if (selected.containsKey(dft.getReference())) {
                            selected.get(dft.getReference()).setOffset(dft.offset());
                        }
                        fastAdapter.notifyDataSetChanged();
                        updateActivity();
                    }
                });
        builder.create().show();
    }

    @Override
    public void onTimeSet(RadialTimePickerDialogFragment dialog, int hourOfDay, int minute) {
        LocalTime t = new LocalTime(hourOfDay, minute);
        Routine r = DB.routines().findByPatientAndTime(getPatient(), t);
        if (r != null) {
            // if the routine exists, select it
            if (selected.containsKey(r.getId())) {
                Toast.makeText(buildActivity, getString(R.string.schedule_build_custom_time_selected), Toast.LENGTH_SHORT).show();
            } else {
                DailyFixedTime dft = new DailyFixedTime(r.getId(), DailyFixedTime.ReferenceType.ROUTINE);
                selected.put(r.getId(), dft);
            }
        } else {
            // in other case, create a new routine
            r = new Routine(t, null);
            r.setPatient(getPatient());
            DB.routines().save(r);
            // select it by adding a new fixed time which references it
            DailyFixedTime dft = new DailyFixedTime(r.getId(), DailyFixedTime.ReferenceType.ROUTINE);
            selected.put(r.getId(), dft);
            // fire event to update the list
        }
        DB.routines().fireEvent();
    }

    @Override
    public void onDialogTimeSet(int ref, int hour, int minute) {
        onTimeSet(null, hour, minute);
    }

    @Override
    public VerificationError verifyStep() {
        //return null if the user can go to the next step, create a new VerificationError instance otherwise
        if ((hourlySelection && selectedHours > 0) || !selected.isEmpty()) {
            updateActivity();
            return null;
        } else {
            return error(getString(R.string.schedule_build_pick_routine_error_empty), ScheduleBuildActivity.ScheduleStep.ROUTINES);
        }
    }

    @Override
    public void onSelected() {
        if (!initialScrollDone) {
            if (buildActivity.isInEditMode()) {
                if (buildActivity.getHourlyInterval() > 0) {
                    hourlySelection = true;
                    selectedHours = buildActivity.getHourlyInterval();
                    hourlySelectionIndicator.setImageDrawable(buildActivity.selectionIcon(true));
                    hoursRv.setValue(selectedHours);
                } else {
                    List<DailyFixedTime> fixedTimes = buildActivity.getRecurringEvent().getDailyFixedTimes();
                    if (fixedTimes != null && fixedTimes.size() > 0) {
                        for (DailyFixedTime dft : fixedTimes) {
                            selected.put(dft.getReference(), dft);
                            times.put(dft.getReference(), dft);
                        }
                        timesList.clear();
                        timesList.addAll(times.values());
                        Collections.sort(timesList, Comparators.DAILY_FIXED_TIME);
                    }
                    hourlySelection = false;
                    hourlySelectionIndicator.setImageDrawable(buildActivity.selectionIcon(false));
                    fastAdapter.notifyDataSetChanged();
                    updateActivity();
                }
            } else {
                setSelectedHours(8);
            }
        }
        initialScrollDone = true;
    }

    @Override
    public void onDestroy() {
        CalendulaApp.eventBus().unregister(this);
        super.onDestroy();
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void handleModelUpdate(final PersistenceEvents.ModelCreateOrUpdateEvent event) {
        if (event.clazz.equals(Routine.class)) {
            fastAdapter.clear();
            populateAdapter();
            updateActivity();
        }
    }

    @Override
    public void onError(@NonNull VerificationError error) {
        //handle error inside of the fragment, e.g. show error on EditText
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            if (getView() != null) {
                fastAdapter.notifyDataSetChanged();
                updateActivity();
            }
        }
    }

    public List<Integer> getHours() {
        List<Integer> hours = new ArrayList<>();
        for (int i = 1; i <= 72; i++) {
            hours.add(i);
        }
        return hours;
    }

    @Override
    public void onDialogNumberSet(int reference, BigInteger number, double decimal, boolean isNegative, BigDecimal fullNumber) {
        int amount = fullNumber.intValue();
        LogUtil.d(TAG, "Amount: " + amount);
        hoursRv.setValue(amount);
        setSelectedHours(amount);
    }

    private void setupRecyclerView() {

        fastAdapter = new FastItemAdapter<>();
        populateAdapter();
        fastAdapter.withSelectable(true);
        fastAdapter.withOnClickListener(new OnClickListener<DailyFixedTimeItem>() {
            @Override
            public boolean onClick(View v, IAdapter<DailyFixedTimeItem> adapter, DailyFixedTimeItem item, int position) {
                if (position == adapter.getAdapterItemCount() - 1) {
                    showAddNewDailyFixedTimeDialog();
                } else {
                    onDailyFixedTimeClick(item.getDailyFixedTime());
                }
                return true;
            }
        });

        fastAdapter.withEventHook(new ClickEventHook<DailyFixedTimeItem>() {
            @Nullable
            @Override
            public View onBind(@NonNull RecyclerView.ViewHolder viewHolder) {
                return null;
            }

            @Nullable
            @Override
            public List<View> onBindMany(@NonNull RecyclerView.ViewHolder viewHolder) {
                ViewHolder holder = (ViewHolder) viewHolder;
                List<View> views = new ArrayList<>();
                views.add(holder.offsetIcon);
                return views;
            }

            @Override
            public void onClick(View v, int position, FastAdapter<DailyFixedTimeItem> adapter, final DailyFixedTimeItem item) {
                if (item.isAddButton) {
                    return;
                } else if (R.id.offset_icon == v.getId()) {
                    showDailyFixedTimeOffsetDialog(item.getDailyFixedTime());
                }
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(fastAdapter);
    }

    private void onDailyFixedTimeClick(DailyFixedTime dft) {
        final Routine r = DB.routines().findById(dft.getReference());
        if (selected.containsKey(dft.getReference())) {
            selected.remove(dft.getReference());
            removeDosage(dft.getReference());
        } else {
            selected.put(dft.getReference(), dft);
            addDosage(r.getId(), dosage(r.getTime()));
        }
        if (!selected.isEmpty()) {
            hourlySelection = false;
            removeDosage(Schedule.DEFAULT_DOSAGE_KEY);
        } else {
            hourlySelection = true;
        }
        hourlySelectionIndicator.setImageDrawable(buildActivity.selectionIcon(hourlySelection));
        fastAdapter.notifyDataSetChanged();
        updateActivity();
    }

    private void populateAdapter() {
        List<Routine> routines = DB.routines().findAllForActivePatient(getContext());
        for (Routine r : routines) {
            times.put(r.getId(), new DailyFixedTime(r.getId(), DailyFixedTime.ReferenceType.ROUTINE));
        }
        timesList = new ArrayList<>(times.values());
        Collections.sort(timesList, Comparators.DAILY_FIXED_TIME);
        if (fastAdapter.getAdapterItemCount() != timesList.size() + 1) {
            DailyFixedTime dummy = new DailyFixedTime();
            dummy.setReference(-1L);
            timesList.add(dummy);
            fastAdapter.clear();
            for (DailyFixedTime t : timesList) {
                fastAdapter.add(new DailyFixedTimeItem(t));
            }
            fastAdapter.notifyAdapterDataSetChanged();
        }
    }

    private Drawable offsetIcon(EventInstance.EventOffset offset) {

        IIcon icon;
        if (EventInstance.EventOffset.BEFORE.equals(offset)) {
            icon = CommunityMaterial.Icon2.cmd_undo;
        } else if (EventInstance.EventOffset.AFTER.equals(offset)) {
            icon = CommunityMaterial.Icon2.cmd_redo;
        } else if (EventInstance.EventOffset.NONE.equals(offset)) {
            icon = CommunityMaterial.Icon.cmd_clock_alert;
        } else {
            icon = CommunityMaterial.Icon.cmd_clock;
        }

        return new IconicsDrawable(getContext())
                .icon(icon)
                .colorRes(R.color.activity_schedule_build_foreground_alpha)
                .paddingDp(4)
                .sizeDp(30);

    }

    private void updateActivity() {
        if (hourlySelection && selectedHours > 0) {
            buildActivity.setHourlyInterval(selectedHours);
        } else if (!selected.isEmpty()) {
            buildActivity.setDailyFixedTimes(new ArrayList<>(selected.values()));
        }
    }

    protected class ViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.routine_name)
        protected TextView routineName;
        @BindView(R.id.offset_icon)
        protected ImageButton offsetIcon;
        @BindView(R.id.selection_indicator)
        protected ImageView selectionIndicator;
        @BindView(R.id.routine_time)
        protected TextView routineTime;

        public ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }

    private class DailyFixedTimeItem extends AbstractItem<DailyFixedTimeItem, ViewHolder> {

        static final int ID = 1;
        private final DailyFixedTime dft;
        boolean isAddButton = false;

        public DailyFixedTimeItem(DailyFixedTime dft) {
            this.dft = dft;
            isAddButton = dft.getReference() == -1;
        }

        public DailyFixedTime getDailyFixedTime() {
            return dft;
        }

        //The unique ID for this type of item
        @Override
        public int getType() {
            return ID;
        }

        @Override
        public int getLayoutRes() {
            return R.layout.pick_routine_list_item;
        }

        @Override
        public void bindView(ViewHolder viewHolder, List<Object> payloads) {
            super.bindView(viewHolder, payloads);

            if (!isAddButton) {
                final Routine r = DB.routines().findById(dft.getReference());
                boolean isSelected = selected.containsKey(dft.getReference());
                EventInstance.EventOffset offset = isSelected ? selected.get(dft.getReference()).offset() : null;
                viewHolder.selectionIndicator.setImageDrawable(buildActivity.selectionIcon(isSelected));
                viewHolder.routineTime.setText(dft.getTime().toString("HH:mm"));
                if (r.getName() == null || r.getName().isEmpty()) {
                    viewHolder.routineName.setText(R.string.schedule_build_pick_routine_add);
                    viewHolder.routineName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                    viewHolder.routineName.setPaintFlags(viewHolder.routineName.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                    viewHolder.routineName.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(getContext(), RoutinesActivity.class);
                            intent.putExtra(CalendulaApp.INTENT_EXTRA_ROUTINE_ID, r.getId());
                            startActivity(intent);
                        }
                    });
                } else {
                    viewHolder.routineName.setPaintFlags(viewHolder.routineName.getPaintFlags() & (~Paint.UNDERLINE_TEXT_FLAG));
                    viewHolder.routineName.setText(r.getName());
                }
                viewHolder.offsetIcon.setImageDrawable(offsetIcon(offset));
            } else {
                viewHolder.offsetIcon.setImageDrawable(new IconicsDrawable(getContext())
                        .icon(CommunityMaterial.Icon2.cmd_plus_circle)
                        .colorRes(R.color.activity_schedule_build_foreground_alpha)
                        .paddingDp(6)
                        .sizeDp(35));
                viewHolder.routineTime.setText(R.string.schedule_build_add_custom_time);
            }
        }

        @Override
        public ViewHolder getViewHolder(View v) {
            return new ViewHolder(v);
        }
    }

}