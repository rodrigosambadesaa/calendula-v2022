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

import android.graphics.PorterDuff;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.shawnlin.numberpicker.NumberPicker;
import com.stepstone.stepper.Step;
import com.stepstone.stepper.VerificationError;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.dmfs.rfc5545.recur.Freq;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;
import es.usc.citius.servando.calendula.util.LogUtil;

/**
 *
 */
public class DaySelectionStepFragment extends ScheduleBuildStepFragment implements Step {

    private static final String TAG = "DaySelectionStepFragment";

    private final LocalDate today = LocalDate.now();
    private final DateTimeFormatter dateFmt = DateTimeFormat.forPattern("EEEE, dd MMMM");
    // Boxes to show/hide
    @BindView(R.id.interval_box)
    View intervalBox;
    @BindView(R.id.period_box)
    View periodBox;
    @BindView(R.id.rest_box)
    View restBox;
    @BindView(R.id.day_selection_box)
    View daySelectionBox;
    @BindView(R.id.custom_box_content)
    View customBoxContent;
    @BindView(R.id.custom_box)
    View customBox;
    // Components
    @BindView(R.id.every_day_selection)
    ImageView everyDaySelection;
    @BindView(R.id.custom_title_selection)
    ImageView customSelection;
    @BindView(R.id.repeat_freq)
    Spinner freqSpinner;
    @BindView(R.id.repeat_interval)
    NumberPicker intervalRv;
    @BindView(R.id.repeat_freq_after)
    TextView freqUnitsTv;
    @BindView(R.id.period_interval)
    NumberPicker periodRv;
    @BindView(R.id.rest_interval)
    NumberPicker restRv;
    @BindView(R.id.from_date)
    TextView fromDate;
    @BindView(R.id.to_date)
    TextView toDate;
    @BindView(R.id.first_time_date)
    TextView fromTime;
    @BindView(R.id.first_time_title)
    TextView fromTimeLabel;
    @BindView(R.id.clear_from)
    ImageView clearStart;
    @BindView(R.id.clear_to)
    ImageView clearEnd;
    private boolean[] selectedWeekDays = selectedToday();
    private IconicsDrawable selectedIc;
    private IconicsDrawable unselectedIc;
    //PickerLayoutManager intervalLM;
    private SparseIntArray weekDayViews;
    private TextView[] days;
    private ViewMode viewMode = ViewMode.EVERY_DAY;
    private ViewMode lastCustomViewMode = null;
    private boolean restSetupDone = false;
    private boolean intervalSetupDone = false;
    private boolean startSetByUser = false;
    private LocalDate start = today;
    private LocalDate originalStart = start;
    private LocalDate end;
    private LocalTime startTime = LocalTime.now().withMinuteOfHour(0);
    private IconicsDrawable clearIc;
    private boolean setupDone = false;
    private boolean isFirstSelection = true;

    private int selectedInterval = 2;
    private int selectedRest = 7;
    private int selectedPeriod = 21;

    RecurringEvent.Builder builder;
    RecurringEvent recurringEvent;

    //PickerLayoutManager restLayoutMgr, periodLayoutMgr;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_day_selection_step, container, false);
        ButterKnife.bind(this, rootView);
        recurringEvent = buildActivity.getRecurringEvent();
        builder = recurringEvent.edit();
        clearIc = clearIcon();
        selectedIc = buildActivity.selectionIcon(true);
        unselectedIc = buildActivity.selectionIcon(false);
        setupWeekDayList(rootView);
        setupFreqSpinner();
        setupDateTimePickers();
        everyDaySelection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adaptViewTo(ViewMode.EVERY_DAY);
                onViewModeChanged();
            }
        });
        customSelection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adaptViewTo(lastCustomViewMode != null ? lastCustomViewMode : ViewMode.CUSTOM_SOME_DAYS);
                onViewModeChanged();
            }
        });
        return rootView;
    }

    @Override
    public VerificationError verifyStep() {
        try {
            if (customBoxContent.getVisibility() == View.VISIBLE && daySelectionBox.getVisibility() == View.VISIBLE) {
                if (!BooleanUtils.or(selectedWeekDays)) {
                    return error(getString(R.string.schedule_build_day_select_error_empty_days), ScheduleBuildActivity.ScheduleStep.REPEAT);
                }
            }

            buildActivity.setRecurringEvent(builder.build());


        } catch (RecurringEvent.InvalidRecurringEventException e) {
            LogUtil.e(TAG, "Invalid recurrence", e);
            return error(getString(R.string.schedule_build_day_select_error_invalid), ScheduleBuildActivity.ScheduleStep.REPEAT);
        }
        return null;
    }

    @Override
    public void onSelected() {

        if (buildActivity.getHourlyInterval() > 0) {
            customBox.setVisibility(View.GONE);
            fromTimeLabel.setVisibility(View.VISIBLE);
            fromTime.setVisibility(View.VISIBLE);
        } else {
            customBox.setVisibility(View.VISIBLE);
            fromTimeLabel.setVisibility(View.GONE);
            fromTime.setVisibility(View.GONE);
        }

        recurringEvent = buildActivity.getRecurringEvent();
        builder = recurringEvent.edit();
        if (buildActivity.isInEditMode() && buildActivity.getRecurringEvent() != null) { //&& !setupDone
            setupFromRecurrence(buildActivity.getRecurringEvent());
            setupDone = true;
        } else {
            adaptViewTo(viewMode);
        }
    }

    @Override
    public void onError(@NonNull VerificationError error) {
        //handle error inside of the fragment, e.g. show error on EditText
    }

    boolean[] allWeekSelected() {
        boolean[] week = new boolean[7];
        for (int i = 0; i < week.length; i++) {
            week[i] = true;
        }
        return week;
    }

    List<String> getIntervalItems() {
        List<String> items = new ArrayList<>();
        for (int i = 2; i < 60; i++) {
            items.add(String.valueOf(i));
        }
        return items;
    }

    List<String> getPeriodRestItems() {
        List<String> items = new ArrayList<>();
        for (int i = 1; i < 60; i++) {
            items.add(String.valueOf(i));
        }
        return items;
    }

    private void updateWeekdayViews() {
        for (int i = 0; i < days.length; i++) {
            if (selectedWeekDays[i]) {
                days[i].setTextAppearance(getContext(), R.style.schedule_weekday_selected);
                days[i].setBackgroundResource(R.drawable.weekday_selector);
            } else {
                days[i].setTextAppearance(getContext(), R.style.schedule_weekday_unselected);
                days[i].setBackgroundResource(R.drawable.weekday_unselected_selector);
            }
        }
    }

    private void setupWeekDayList(View rootView) {

        days = new TextView[]{
                (TextView) rootView.findViewById(R.id.day_mo),
                (TextView) rootView.findViewById(R.id.day_tu),
                (TextView) rootView.findViewById(R.id.day_we),
                (TextView) rootView.findViewById(R.id.day_th),
                (TextView) rootView.findViewById(R.id.day_fr),
                (TextView) rootView.findViewById(R.id.day_sa),
                (TextView) rootView.findViewById(R.id.day_su)
        };

        // map ids with day index
        weekDayViews = new SparseIntArray();
        for (int i = 0; i < days.length; i++) {
            weekDayViews.put(days[i].getId(), i);
        }

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int index = weekDayViews.get(view.getId());
                selectedWeekDays[index] = !selectedWeekDays[index];
                TextView tv = days[index];
                if (!selectedWeekDays[index]) {
                    tv.setTextAppearance(getContext(), R.style.schedule_weekday_unselected);
                    tv.setBackgroundResource(R.drawable.weekday_unselected_selector);
                } else {
                    tv.setTextAppearance(getContext(), R.style.schedule_weekday_selected);
                    tv.setBackgroundResource(R.drawable.weekday_selector);
                }
                builder.repeatWeekdays(selectedWeekDays);
                updateRecurringEvent();
                syncStartIfNeeded();
            }
        };
        for (TextView tv : days) {
            tv.setOnClickListener(listener);
        }
        updateWeekdayViews();
    }

    private void adaptViewTo(ViewMode mode) {
        viewMode = mode;
        if (mode == ViewMode.EVERY_DAY) {
            customBoxContent.setVisibility(View.GONE);
            everyDaySelection.setImageDrawable(selectedIc);
            customSelection.setImageDrawable(unselectedIc);
        } else {
            lastCustomViewMode = mode;
            customBoxContent.setVisibility(View.VISIBLE);
            everyDaySelection.setImageDrawable(unselectedIc);
            customSelection.setImageDrawable(selectedIc);
            switch (mode) {
                case CUSTOM_SOME_DAYS:
                    intervalBox.setVisibility(View.GONE);
                    restBox.setVisibility(View.GONE);
                    periodBox.setVisibility(View.GONE);
                    daySelectionBox.setVisibility(View.VISIBLE);
                    break;
                case CUSTOM_DAILY:
                    if (!intervalSetupDone) {
                        intervalSetupDone = true;
                        setupIntervalPicker();
                    }
                    restBox.setVisibility(View.GONE);
                    periodBox.setVisibility(View.GONE);
                    daySelectionBox.setVisibility(View.GONE);
                    intervalBox.setVisibility(View.VISIBLE);
                    freqUnitsTv.setText(R.string.schedule_repeat_frequency_days);
                    break;
                case CUSTOM_WEEKLY:
                    if (!intervalSetupDone) {
                        intervalSetupDone = true;
                        setupIntervalPicker();
                    }
                    restBox.setVisibility(View.GONE);
                    periodBox.setVisibility(View.GONE);
                    daySelectionBox.setVisibility(View.VISIBLE);
                    intervalBox.setVisibility(View.VISIBLE);
                    freqUnitsTv.setText(R.string.weeks);
                    break;
                case CUSTOM_REST:
                    restBox.setVisibility(View.VISIBLE);
                    periodBox.setVisibility(View.VISIBLE);
                    daySelectionBox.setVisibility(View.GONE);
                    intervalBox.setVisibility(View.GONE);
                    if (!restSetupDone) {
                        restSetupDone = true;
                        setupRestPickers();
                    }
                    break;
            }
            if (daySelectionBox.getVisibility() == View.VISIBLE) {
                syncStartIfNeeded();
            }
        }
    }

    void onViewModeChanged () {
        switch (viewMode) {
            case EVERY_DAY:
                builder.clearWeekdays();
                if (!recurringEvent.hasHourlyFrequency()) {
                    // can have routines or not
                    builder.repeatDaily();
                }
                break;
            case CUSTOM_DAILY:
                // check interval
                builder.clearWeekdays().repeatEvery(selectedInterval, Freq.DAILY);
                break;
            case CUSTOM_SOME_DAYS:
                // check days[]
                builder.repeatWeekdays(selectedWeekDays);
                builder.repeatEvery(1, Freq.DAILY);
                break;
            case CUSTOM_WEEKLY:
                // check interval && days[]
                builder.repeatEvery(selectedInterval, Freq.WEEKLY);
                builder.repeatWeekdays(selectedWeekDays);
                break;
            case CUSTOM_REST:
                // check cycle
                builder.clearWeekdays()
                        .repeatDaily()
                        .repeatCyclic(selectedPeriod, selectedRest);
                break;
        }

        if (viewMode != ViewMode.CUSTOM_REST) {
            builder.clearCycle();
        }
    }

    private boolean[] selectedToday() {
        boolean[] week = new boolean[7];
        week[DateTime.now().getDayOfWeek() - 1] = true;
        return week;
    }

    private void setupFromRecurrence(RecurringEvent evt) {
        this.start = evt.hasStart() ? evt.getStartDateTime().toLocalDate() : null;
        this.end = evt.hasEnd() ? evt.getEndDateTime().toLocalDate() : null;
        this.startTime = evt.getStartDateTime().toLocalTime();
        this.originalStart = start;
        startSetByUser = true;
        Freq frequency = evt.getFreq();
        int interval = evt.getInterval();

        if (evt.isCyclic()) {
            viewMode = ViewMode.CUSTOM_REST;
            selectedPeriod = evt.getCycleActiveDays();
            selectedRest = evt.getCycleInactiveDays();
            periodRv.setValue(selectedPeriod);
            restRv.setValue(selectedRest);
        } else if (evt.hasHourlyFrequency()) {
            viewMode = ViewMode.EVERY_DAY;
        } else if (frequency.equals(Freq.DAILY) && interval == 1) {
            // get days of the week
            if (evt.allWeekdaysSelected()) {
                viewMode = ViewMode.EVERY_DAY;
            } else {
                viewMode = ViewMode.CUSTOM_SOME_DAYS;
                selectedWeekDays = evt.byDay();
                updateWeekdayViews();
            }
        } else if (frequency.equals(Freq.DAILY) && interval > 1) {
            viewMode = ViewMode.CUSTOM_DAILY;
            selectedInterval = interval;
            intervalRv.setValue(selectedInterval);
        } else if (frequency.equals(Freq.WEEKLY)) {
            viewMode = ViewMode.CUSTOM_WEEKLY;
            selectedInterval = interval;
            intervalRv.setValue(selectedInterval);
            selectedWeekDays = evt.byDay();
            updateWeekdayViews();
        }

        onStartDateUpdated();
        onEndDateUpdated();
        onStartTimeUpdated();

        if (viewMode.code < 4) {
            setSelectionSilent(freqSpinner, viewMode.code);
        }

        adaptViewTo(viewMode);
    }

    private void updateStartTime(LocalTime t) {
        startTime = t;
        onStartTimeUpdated();

        if (recurringEvent.hasHourlyFrequency()) {
            builder.from(start.toDateTime(startTime.withSecondOfMinute(0)));
            updateRecurringEvent();
        }
    }

    private void onStartTimeUpdated() {
        if (startTime != null) {
            fromTime.setText(startTime.toString("kk:mm 'h'"));
        }
    }

    private void updateRecurringEvent() {
        try {
            builder.commit();
            buildActivity.setRecurringEvent(recurringEvent);
        } catch (RecurringEvent.InvalidRecurringEventException e) {
            LogUtil.e(TAG, "Invalid recurrence", e);
        }
    }

    private void updateStartDate(LocalDate d) {
        start = d;
        onStartDateUpdated();

        if (recurringEvent.hasHourlyFrequency()) {
            builder.from(start.toDateTime(startTime.withSecondOfMinute(0)));
        } else {
            builder.from(start);
        }
        updateRecurringEvent();
    }

    private void onStartDateUpdated() {
        String text;
        if (start.equals(today)) {
            text = getString(R.string.today);
        } else if (start.equals(today.plusDays(1))) {
            text = getString(R.string.tomorrow);
        } else if (start.equals(today.minusDays(1))) {
            text = getString(R.string.yesterday);
        } else {
            text = StringUtils.capitalize(start.toString(dateFmt));
        }
        fromDate.setText(text);

        if (start.equals(originalStart)) {
            clearStart.setImageDrawable(null);
        } else {
            clearStart.setImageDrawable(clearIc);
        }
    }

    private void updateEndDate(LocalDate d) {
        end = d;
        onEndDateUpdated();
        builder.to(end);
        updateRecurringEvent();
    }

    private void onEndDateUpdated() {
        if (end != null) {
            toDate.setText(StringUtils.capitalize(end.toString(dateFmt)));
            clearEnd.setImageDrawable(clearIc);
        } else {
            toDate.setText(R.string.schedule_build_dayselect_repeat_indefinitely);
            clearEnd.setImageDrawable(null);
        }
    }

    private void syncStartIfNeeded() {


        // selected by user and already sync
        if (selectedWeekDays[start.getDayOfWeek() - 1] && startSetByUser) {
            return;
        }

        // auto selection, find best
        int startIndex = originalStart.getDayOfWeek() - 1;
        int first = -1;
        for (int i = 0; i < selectedWeekDays.length; i++) {
            if (selectedWeekDays[i]) {
                if (i == startIndex) {
                    startSetByUser = false;
                    updateStartDate(originalStart);
                    return;
                } else if (i > startIndex) {
                    startSetByUser = false;
                    updateStartDate(originalStart.plusDays(i - startIndex));
                    return;
                } else if (first == -1) {
                    first = i;
                }
            }
        }
        if (first > -1) {
            startSetByUser = false;
            updateStartDate(originalStart.plusWeeks(1).minusDays(startIndex - first));
        }
    }

    private void setupFreqSpinner() {
        String[] items = getResources().getStringArray(R.array.schedule_build_dayselect_repeat_types);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(), R.layout.freq_spinner_item, items);
        adapter.setDropDownViewResource(R.layout.freq_spinner_item_inverse);
        freqSpinner.getBackground().setColorFilter(getResources().getColor(R.color.activity_schedule_build_foreground), PorterDuff.Mode.SRC_ATOP);
        freqSpinner.setAdapter(adapter);
        freqSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isFirstSelection) {
                    isFirstSelection = false;
                    return;
                }
                adaptViewTo(ViewMode.from(position));
                onViewModeChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void setupIntervalPicker() {
        intervalRv.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker numberPicker, int prev, int current) {
                selectedInterval = current;
                switch (viewMode) {
                    case CUSTOM_DAILY:
                        // check interval
                        builder.repeatEvery(selectedInterval, Freq.DAILY);
                        break;
                    case CUSTOM_WEEKLY:
                        // check interval && days[]
                        builder.repeatEvery(selectedInterval, Freq.WEEKLY);
                        break;
                }
            }
        });
        intervalRv.setValue(selectedInterval);
    }

    private void setupRestPickers() {

        periodRv.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker numberPicker, int prev, int current) {
                selectedPeriod = current;
                builder.repeatCyclic(selectedPeriod, selectedRest);
                updateRecurringEvent();
            }
        });
        restRv.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker numberPicker, int prev, int current) {
                selectedRest = current;
                builder.repeatCyclic(selectedPeriod, selectedRest);
                updateRecurringEvent();
            }
        });


        if (!buildActivity.isInEditMode()) {
            restRv.setValue(selectedRest);
            periodRv.setValue(selectedPeriod);
        }
    }


    private IconicsDrawable clearIcon() {
        return new IconicsDrawable(getContext())
                .icon(CommunityMaterial.Icon.cmd_close_circle_outline)
                .colorRes(R.color.activity_schedule_build_foreground)
                .paddingDp(5)
                .sizeDp(40);
    }

    private void setupDateTimePickers() {

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.from_date:
                        showStartDatePicker();
                        break;
                    case R.id.to_date:
                        showEndDatePicker();
                        break;
                    case R.id.first_time_date:
                        showStartTimePicker();
                        break;
                    case R.id.clear_from:
                        if (!start.equals(originalStart)) {
                            updateStartDate(originalStart);
                        }
                        break;
                    case R.id.clear_to:
                        if (end != null) {
                            updateEndDate(null);
                        }
                        break;
                }
            }
        };

        fromDate.setOnClickListener(listener);
        toDate.setOnClickListener(listener);
        fromTime.setOnClickListener(listener);
        clearStart.setOnClickListener(listener);
        clearEnd.setOnClickListener(listener);

        onStartDateUpdated();
        onEndDateUpdated();
        onStartTimeUpdated();
    }

    private void showStartDatePicker() {
        LocalDate d = start != null ? start : today;
        DatePickerDialog dpd = DatePickerDialog.newInstance(
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePickerDialog datePickerDialog, int year, int monthOfYear, int dayOfMonth) {
                        startSetByUser = true;
                        updateStartDate(new LocalDate(year, monthOfYear + 1, dayOfMonth));
                    }
                }, d.getYear(), d.getMonthOfYear() - 1, d.getDayOfMonth());
        dpd.setVersion(DatePickerDialog.Version.VERSION_2);
        dpd.setAccentColor(getResources().getColor(R.color.activity_schedule_build_primary));
        dpd.setMinDate(today.toDateTimeAtStartOfDay().toCalendar(Locale.getDefault()));
        dpd.setDateRangeLimiter(new StartDateLimiter());
        dpd.show(getActivity().getFragmentManager(), "StartDatePicker");
    }

    private void showEndDatePicker() {
        LocalDate d = end != null ? end : (start != null ? start.plusDays(7) : today.plusDays(7));
        DatePickerDialog dpd = DatePickerDialog.newInstance(
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePickerDialog datePickerDialog, int year, int monthOfYear, int dayOfMonth) {
                        updateEndDate(new LocalDate(year, monthOfYear + 1, dayOfMonth));
                    }
                }, d.getYear(), d.getMonthOfYear() - 1, d.getDayOfMonth());
        dpd.setVersion(DatePickerDialog.Version.VERSION_2);
        dpd.setAccentColor(getResources().getColor(R.color.activity_schedule_build_primary));
        dpd.setDateRangeLimiter(new EndDateLimiter());
        dpd.show(getActivity().getFragmentManager(), "EndDatePicker");

    }

    private void showStartTimePicker() {
        LocalTime t = startTime != null ? startTime : LocalTime.now();
        TimePickerDialog tpd = TimePickerDialog.newInstance(new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePickerDialog timePickerDialog, int hourOfDay, int minute, int s) {
                updateStartTime(new LocalTime(hourOfDay, minute));
            }
        }, t.getHourOfDay(), t.getMinuteOfHour(), true);
        tpd.setVersion(TimePickerDialog.Version.VERSION_2);
        tpd.setAccentColor(getResources().getColor(R.color.activity_schedule_build_primary));
        tpd.show(getActivity().getFragmentManager(), "StartTimePicker");
    }

    private class StartDateLimiter extends BaseDateRangeLimiter {
        @Override
        boolean isValid(LocalDate d) {
            // if weekdays are important for the current selection
            if (customBox.getVisibility() == View.VISIBLE && customBoxContent.getVisibility() == View.VISIBLE && daySelectionBox.getVisibility() == View.VISIBLE) {
                return !d.isBefore(today) && selectedWeekDays[d.getDayOfWeek() - 1];
            }
            return true;
        }
    }

    private class EndDateLimiter extends BaseDateRangeLimiter {
        @Override
        boolean isValid(LocalDate d) {
            return d.isAfter(start);
        }
    }
}