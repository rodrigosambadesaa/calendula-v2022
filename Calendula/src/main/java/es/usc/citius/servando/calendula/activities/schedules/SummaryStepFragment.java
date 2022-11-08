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

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.roomorama.caldroid.CaldroidFragment;
import com.roomorama.caldroid.CaldroidListener;
import com.stepstone.stepper.Step;
import com.stepstone.stepper.VerificationError;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;

/**
 *
 */

public class SummaryStepFragment extends ScheduleBuildStepFragment implements Step {

    public static final String TAG = "PickMedStepFragment";
    @BindView(R.id.prev_month)
    ImageView prevMonth;
    @BindView(R.id.next_month)
    ImageView nextMonth;
    @BindView(R.id.month_name)
    TextView monthName;
    private CaldroidFragment calendar;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_summary_step, container, false);
        ButterKnife.bind(this, rootView);
        return rootView;
    }


    @Override
    public VerificationError verifyStep() {


        return null;
    }

    @Override
    public void onSelected() {
        prevMonth.postDelayed(new Runnable() {
            @Override
            public void run() {
                setupCalendarView();
            }
        }, 200);
    }

    @Override
    public void onError(@NonNull VerificationError verificationError) {

    }

    private Drawable getSelectedDrawable(int color) {
        return new IconicsDrawable(getContext())
                .icon(CommunityMaterial.Icon.cmd_checkbox_blank_circle_outline)
                .colorRes(color)
                .paddingDp(5)
                .sizeDp(20);
    }

    private Drawable controlIcon(CommunityMaterial.Icon i) {
        return new IconicsDrawable(getContext())
                .icon(i)
                .colorRes(R.color.activity_schedule_build_foreground)
                .paddingDp(5)
                .sizeDp(20);
    }

    private void setupCalendarView() {
        calendar = new CaldroidFragment();
        Bundle args = new Bundle();
        DateTime now = DateTime.now();
        args.putInt(CaldroidFragment.MONTH, now.getMonthOfYear());
        args.putInt(CaldroidFragment.YEAR, now.getYear());
        args.putBoolean(CaldroidFragment.SHOW_NAVIGATION_ARROWS, true);
        args.putBoolean(CaldroidFragment.SIX_WEEKS_IN_CALENDAR, false);
        args.putInt(CaldroidFragment.START_DAY_OF_WEEK, CaldroidFragment.MONDAY);
        args.putInt(CaldroidFragment.THEME_RESOURCE, R.style.CaldroidScheduleSummary);
        calendar.setArguments(args);

        FragmentTransaction t = getActivity().getSupportFragmentManager().beginTransaction();
        t.replace(R.id.calendar, calendar);
        t.commit();

        final List<Integer> monthsShown = new ArrayList<>();
        DateTime today = DateTime.now();

        if (buildActivity.getRecurringEvent() != null) {
            final RecurringEvent evt = buildActivity.getRecurringEvent();
            final LocalDate start = evt.hasStart() ? evt.getStartDateTime().toLocalDate() : today.toLocalDate();
            final Drawable d1 = getSelectedDrawable(R.color.activity_schedule_build_contrast);
            final Drawable d2 = getSelectedDrawable(R.color.android_green);


            calendar.setCaldroidListener(new CaldroidListener() {
                @Override
                public void onSelectDate(Date date, View view) {

                }

                @Override
                public void onChangeMonth(int month, int year) {
                    super.onChangeMonth(month, year);
                    DateTime date = DateTime.now().withYear(year).withMonthOfYear(month);
                    monthName.setText(date.toString("MMMM YYYY").toUpperCase());
                    if (!monthsShown.contains(month)) {
                        DateTime from = date.withDayOfMonth(1);
                        DateTime to = from.plusMonths(1).plusWeeks(1);
                        List<RecurringEvent.EventTimeInfo> dateTimes = evt.occurrencesIn(from, to);
                        // clear ?
                        for (RecurringEvent.EventTimeInfo eti : dateTimes) {
                            DateTime dt = eti.time();
                            Date toDate = dt.toDate();
                            boolean isStart = dt.toLocalDate().equals(start);
                            calendar.setTextColorForDate(isStart ? R.color.android_green : R.color.activity_schedule_build_foreground, toDate);
                            calendar.setBackgroundDrawableForDate(isStart ? d2 : d1, toDate);
                        }
                    }
                    monthsShown.add(month);
                }

                @Override
                public void onCaldroidViewCreated() {
                    super.onCaldroidViewCreated();
                    calendar.getView().findViewById(R.id.calendar_title_view).setVisibility(View.GONE);
                    calendar.getMonthTitleTextView().setVisibility(View.GONE);
                }
            });
        }
        calendar.refreshView();
        prevMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calendar.prevMonth();
            }
        });
        nextMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calendar.nextMonth();
            }
        });
        prevMonth.setImageDrawable(controlIcon(CommunityMaterial.Icon.cmd_chevron_left));
        nextMonth.setImageDrawable(controlIcon(CommunityMaterial.Icon.cmd_chevron_right));

    }
}