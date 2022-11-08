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

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.TextView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IAdapter;
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter.listeners.OnClickListener;
import com.stepstone.stepper.Step;
import com.stepstone.stepper.VerificationError;

import org.joda.time.LocalTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.fragments.dosePickers.DefaultDosePickerFragment;
import es.usc.citius.servando.calendula.fragments.dosePickers.DosePickerFragment;
import es.usc.citius.servando.calendula.fragments.dosePickers.PillDosePickerFragment;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.ScheduleDisplayUtils;
import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime;
import es.usc.citius.servando.calendula.util.Comparators;
import es.usc.citius.servando.calendula.util.LogUtil;

/**
 *
 */

public class DosageStepFragment extends ScheduleBuildStepFragment implements Step {

    private static final String TAG = "PickMedStepFragment";
    private static final Comparator<DosageItem> dosageItemComparator = new Comparator<DosageStepFragment.DosageItem>() {
        @Override
        public int compare(DosageStepFragment.DosageItem o1, DosageStepFragment.DosageItem o2) {
            if (o1.time == null) {
                return 0;
            }
            return o1.time.compareTo(o2.time);
        }
    };
    @BindView(R.id.dosage_list)
    RecyclerView recyclerView;
    @BindView(R.id.title)
    TextView subtitle;
    @BindView(R.id.meals_title)
    TextView mealsTitle;
    @BindView(R.id.during)
    RadioButton duringMeals;
    @BindView(R.id.before)
    RadioButton beforeMeals;
    @BindView(R.id.after)
    RadioButton afterMeals;
    private boolean firstDoseChange = true;
    private FastItemAdapter<DosageItem> fastAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_dosage_step, container, false);
        ButterKnife.bind(this, rootView);
        return rootView;
    }

    @Override
    public VerificationError verifyStep() {
        return null;
    }

    @Override
    public void onSelected() {
        setupRecyclerView();
        setupMealRadioButtons();
        updateSubtitle();
    }

    @Override
    public void onError(@NonNull VerificationError error) {
        //handle error inside of the fragment, e.g. show error on EditText
    }

    private void showDosePicker(Medicine m, double dose, DosePickerFragment.OnMultipleDoseSelectedListener l) {
        Presentation p = m.getPresentation();
        final DosePickerFragment dpf = getDosePickerFragment(p, dose);
        FragmentManager fm = getActivity().getSupportFragmentManager();
        dpf.setOnMultiDoseSelectedListener(l);
        dpf.show(fm, "fragment_select_dose");
    }

    private void setupMealRadioButtons() {
        CompoundButton.OnCheckedChangeListener changeListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                switch (buttonView.getId()) {
                    case R.id.during:

                        break;
                    case R.id.before:

                        break;
                    case R.id.after:

                        break;
                }
            }
        };
        duringMeals.setOnCheckedChangeListener(changeListener);
        beforeMeals.setOnCheckedChangeListener(changeListener);
        afterMeals.setOnCheckedChangeListener(changeListener);

        // hide by now
        mealsTitle.setVisibility(View.GONE);
        duringMeals.setVisibility(View.GONE);
        beforeMeals.setVisibility(View.GONE);
        afterMeals.setVisibility(View.GONE);
    }

    private void updateSubtitle() {
        int intakesByDay = 0;
        if (buildActivity.getHourlyInterval() > 0) {
            intakesByDay = 24 / buildActivity.getHourlyInterval();
        } else if (!buildActivity.getIntakes().isEmpty()) {
            intakesByDay = buildActivity.getIntakes().size();
        }
        final String dosageSummary = getResources().getQuantityString(R.plurals.intakes_per_day, intakesByDay, intakesByDay);
        subtitle.setText(dosageSummary);
    }

    private void setupRecyclerView() {

        if (fastAdapter == null) {
            fastAdapter = new FastItemAdapter<>();
            fastAdapter.withSelectable(true);
            fastAdapter.withOnClickListener(new OnClickListener<DosageItem>() {
                @Override
                public boolean onClick(View v, IAdapter<DosageItem> adapter, final DosageItem item, final int position) {

                    showDosePicker(buildActivity.getMedicine(), item.value, new DosePickerFragment.OnMultipleDoseSelectedListener() {
                        @Override
                        public void onDoseSelected(double dose, boolean all) {
                            if (all) {
                                for (DosageItem dosageItem : fastAdapter.getAdapterItems()) {
                                    dosageItem.value = dose;
                                }
                                fastAdapter.notifyAdapterDataSetChanged();
                                firstDoseChange = false;
                            } else {
                                item.value = dose;
                                fastAdapter.notifyAdapterItemChanged(position);
                            }
                            updateDosages();
                        }
                    });
                    return true;
                }
            });
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
            recyclerView.setAdapter(fastAdapter);

        }

        if (schedule().getRecur().hasHourlyFrequency()) {
            fastAdapter.clear();
            fastAdapter.add(new DosageItem(Schedule.DEFAULT_DOSAGE_KEY, getString(R.string.schedule_build_routine_picker_hourly_every) +
                    " " + schedule().getRecur().getInterval() + " " + getString(R.string.hours), dosage()));
        } else if (schedule().getRecur().hasDailyFixedTimes()) {
            List<DailyFixedTime> times = schedule().getRecur().getDailyFixedTimes();
            List<DosageItem> all = fastAdapter.getAdapterItems();
            Collections.sort(times, Comparators.DAILY_FIXED_TIME);
            // remove items in the adapter that are no in the user selection
            List<DosageItem> itemsToRemove = new ArrayList<>();
            for (DosageItem item : fastAdapter.getAdapterItems()) {
                boolean remove = true;
                for (DailyFixedTime r : times) {
                    if (r.getTime().equals(item.time)) {
                        remove = false;
                        break;
                    }
                }
                if (remove) {
                    itemsToRemove.add(item);
                }
            }
            LogUtil.d(TAG, "Remove: " + itemsToRemove.size());
            all.removeAll(itemsToRemove);

            // add items that are not in the adapter
            List<DosageItem> itemsToAdd = new ArrayList<>();

            for (DailyFixedTime r : times) {
                boolean add = true;
                for (DosageItem item : fastAdapter.getAdapterItems()) {
                    if (r.getTime().equals(item.time)) {
                        add = false;
                        break;
                    }
                }
                if (add) {

                    Double dose = dosage(r.getTime());
                    if (dose == null) {
                        dose = 1d;
                    }

                    String text;
                    LocalTime t;

                    if (r.getReferenceType().equals(DailyFixedTime.ReferenceType.ROUTINE)) {
                        Routine routine = DB.routines().findById(r.getReference());
                        t = routine.getTime();
                        if (routine.getName() != null && !routine.getName().isEmpty()) {
                            text = routine.getName();
                        } else {
                            text = routine.getTime().toString("HH:mm");
                        }
                    } else {
                        t = r.getTime();
                        text = t.toString("HH:mm");
                    }
                    itemsToAdd.add(new DosageItem(r.getReference(), text, dose, t));
                }
            }
            LogUtil.d(TAG, "ToAdd: " + itemsToAdd.size());
            all.addAll(itemsToAdd);
            Collections.sort(all, dosageItemComparator);
            fastAdapter.setNewList(all);
        }
        updateDosages();
          }

    private void updateDosages() {
        Map<Long, Double> newDosages = new HashMap<>(fastAdapter.getAdapterItems().size());
        for (DosageItem d : fastAdapter.getAdapterItems()) {
            newDosages.put(d.ref, d.value);
        }
        buildActivity.setDosages(newDosages);
    }

    private DosePickerFragment getDosePickerFragment(Presentation p, double dose) {
        DosePickerFragment dpf;
        Bundle arguments = new Bundle();

        if (p != null && (p.equals(Presentation.DROPS)
                || p.equals(Presentation.PILLS)
                || p.equals(Presentation.CAPSULES)
                || p.equals(Presentation.EFFERVESCENT))) {
            dpf = new PillDosePickerFragment();
        } else {
            dpf = new DefaultDosePickerFragment();
        }
        arguments.putSerializable("presentation", p);
        arguments.putDouble("dose", dose);
        dpf.setArguments(arguments);
        return dpf;

    }

    public class DosageItem extends AbstractItem<DosageItem, ViewHolder> {

        static final int ID = 1;
        String text;
        Double value;
        LocalTime time;
        Long ref;

        public DosageItem(Long ref, String text, Double value) {
            this.text = text;
            this.value = value;
            this.ref = ref;
        }

        public DosageItem(Long ref, String text, Double value, LocalTime time) {
            this.text = text;
            this.value = value;
            this.time = time;
            this.ref = ref;
        }

        //The unique ID for this type of item
        @Override
        public int getType() {
            return ID;
        }

        @Override
        public int getLayoutRes() {
            return R.layout.pick_dosage_list_item;
        }

        @Override
        public void bindView(ViewHolder viewHolder, List<Object> payloads) {
            super.bindView(viewHolder, payloads);

            LogUtil.d(TAG, "bindView: " + text);
            LogUtil.d(TAG, "bindView: " + value);
            LogUtil.d(TAG, "bindView: " + time);
            LogUtil.d(TAG, "bindView: " + ref);

            //bind our data if has changed
            viewHolder.text.setText(text);
            viewHolder.dosage.setText(ScheduleDisplayUtils.displayDose(value, buildActivity.getMedicine().getPresentation(), getResources()));
        }

        @Override
        public ViewHolder getViewHolder(View v) {
            return new ViewHolder(v);
        }
    }

    protected class ViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.text)
        protected TextView text;
        @BindView(R.id.dosage)
        protected TextView dosage;

        public ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }

}