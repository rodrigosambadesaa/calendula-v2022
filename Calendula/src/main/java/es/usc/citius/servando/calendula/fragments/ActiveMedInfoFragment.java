/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2017 CITIUS - USC
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

package es.usc.citius.servando.calendula.fragments;


import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.IIcon;
import com.roomorama.caldroid.CaldroidFragment;
import com.roomorama.caldroid.CaldroidGridAdapter;
import com.roomorama.caldroid.CaldroidListener;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.PickupCalendarAdapter;
import es.usc.citius.servando.calendula.activities.schedules.ScheduleBuildActivity;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.DosageEntryVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.DosageVO;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;
import es.usc.citius.servando.calendula.util.DispensationInfoStore;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.LogUtil;


public class ActiveMedInfoFragment extends Fragment {

    private static final String TAG = "ActiveMedInfoFragment";

    private final static String ID_ARG = "active_med_id";

    private final static DateTimeFormatter dateFormat = DateTimeFormat.forPattern("dd/MM/yyyy");
    private static DispensationInfoStore dispensationInfoStore;
    @BindView(R.id.active_med_from_container)
    View validityStartContainer;
    @BindView(R.id.active_med_to_container)
    View validityEndContainer;
    @BindView(R.id.schedule_info_date_from)
    TextView validityStartText;
    @BindView(R.id.schedule_info_date_to)
    TextView validityEndText;
    @BindView(R.id.active_med_schedule_text)
    TextView scheduleText;
    @BindView(R.id.active_med_intake_instructions)
    TextView intakeInstructions;
    @BindView(R.id.dispensation_interval_info)
    TextView dispensationIntervalInfo;
    @BindView(R.id.dispensation_text)
    TextView dispensationInfoText;
    @BindView(R.id.enable_reminders_button)
    Button remindersButton;
    private CaldroidFragment caldroidFragment;
    private ActiveMedVO activeMed;

    private Unbinder unbinder;
    private Context c = getActivity();

    Drawable intervalColor = new ColorDrawable(Color.parseColor("#ececec"));
    ArrayList<Date> intervalDates = new ArrayList<>();

    public static ActiveMedInfoFragment newInstance(ActiveMedVO m) {
        ActiveMedInfoFragment fragment = new ActiveMedInfoFragment();
        Bundle args = new Bundle();
        args.putString(ID_ARG, m.getCode());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();
        setupView();
        refreshData();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        refreshData();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_med_active_med_info, container, false);
        unbinder = ButterKnife.bind(this, rootView);
        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (unbinder != null)
            unbinder.unbind();
    }

    public void notifyDataChange() {
        refreshData();
        if (!isStateSaved())
            setupView();
    }

    private void refreshData() {
        if (getArguments() != null) {
            final ActiveMedEntity medEntity = DB.healthcareProviderDB().activeMeds().findOneBy(ActiveMedEntity.COLUMN_CODE, getArguments().getString(ID_ARG, null));
            activeMed = ActiveMedVO.forEntity(medEntity);
        }
    }

    private void setupView() {
        // validity period
        final DateTime validityStart = activeMed.getValidityStart();
        final DateTime validityEnd = activeMed.getValidityEnd();

        if (validityStart != null) {
            validityStartText.setText(validityStart.toString(dateFormat));
        } else {
            validityStartContainer.setVisibility(View.GONE);
        }
        if (validityEnd != null) {
            validityEndText.setText(validityEnd.toString(dateFormat));
        } else {
            validityEndContainer.setVisibility(View.GONE);
        }
        // schedule (dosage)
        final DosageVO dosage = activeMed.getDosage();
        if (dosage != null) {
            scheduleText.setText(dosage.toReadableString(getContext()));
        }

        List<DosageEntryVO> entries = dosage.getEntries();
        if(entries.size()!=0) {
            String instructions = entries.get(0).getPatientInstruction();
            if (instructions == null || org.apache.commons.lang3.StringUtils.isBlank(instructions)){
                intakeInstructions.setVisibility(View.GONE);
            }
            else {
                intakeInstructions.setText(getInfoMessage(getContext().getString(R.string.instructions_prefix) + " " + instructions, CommunityMaterial.Icon2.cmd_medical_bag, getContext()));
                intakeInstructions.setVisibility(View.VISIBLE);
            }
        }
        else {
            intakeInstructions.setVisibility(View.GONE);
        }

        final Schedule schedule = DB.schedules().findOneBy(Schedule.BOUND_TO, activeMed.getBackingEntity().getId());
        final boolean hasReminders = schedule != null;

        remindersButton.setCompoundDrawables(null, null, IconUtils.icon(getContext(),
                hasReminders ? CommunityMaterial.Icon.cmd_bell_ring : CommunityMaterial.Icon.cmd_bell_plus,
                R.color.android_green, 30, 5), null);

        remindersButton.setText(getString(hasReminders ? R.string.edit_reminder_text : R.string.create_reminder_text) + " ");

        remindersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Long id = activeMed.getBackingEntity().getId();
                Intent intent = new Intent(getContext(), ScheduleBuildActivity.class);
                if (hasReminders) {
                    intent.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID, schedule.getId());
                } else {
                    intent.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_CREATE_REMINDER);
                    intent.putExtra(IntentParams.EXTRA_ACTIVEMED_ID, id);
                }
                getContext().startActivity(intent);
            }
        });

        caldroidFragment = new DispensationFragment();
        Bundle args = new Bundle();
        DateTime now = DateTime.now();
        args.putInt(CaldroidFragment.MONTH, now.getMonthOfYear());
        args.putInt(CaldroidFragment.YEAR, now.getYear());
        args.putBoolean(CaldroidFragment.SHOW_NAVIGATION_ARROWS, true);
        args.putBoolean(CaldroidFragment.SIX_WEEKS_IN_CALENDAR, false);
        args.putInt(CaldroidFragment.START_DAY_OF_WEEK, CaldroidFragment.MONDAY);
        args.putInt(CaldroidFragment.THEME_RESOURCE, R.style.CaldroidScheduleSummary);
        caldroidFragment.setArguments(args);

        final CaldroidListener listener = new CaldroidListener() {

            @Override
            public void onSelectDate(Date date, View view) {
                if (dispensationInfoStore!=null) {
                    LocalDate d = LocalDate.fromDateFields(date);
                    List<DispensationInfoEntity> from = dispensationInfoStore.pickupsMap().get(d);
                    List<DispensationInfoEntity> to = dispensationInfoStore.pickupsEndMap().get(d);
                    if (from != null && from.size() == 1) {
                        Interval interval = from.get(0).getDispenseInterval();
                        setSelectedInterval(interval);
                    } else if (to != null && to.size() == 1) {
                        Interval interval = to.get(0).getDispenseInterval();
                        setSelectedInterval(interval);
                    }
                }
            }

            @Override
            public void onChangeMonth(int month, int year) {
            }

            @Override
            public void onLongClickDate(Date date, View view) {
            }

            @Override
            public void onCaldroidViewCreated() {
                super.onCaldroidViewCreated();
                if (caldroidFragment.getView() != null) {
                    Button leftButton = caldroidFragment.getLeftArrowButton();
                    Button rightButton = caldroidFragment.getRightArrowButton();
                    leftButton.setBackground(IconUtils.icon(getContext(), CommunityMaterial.Icon.cmd_chevron_left, R.color.dark_grey_text, 30, 10));
                    rightButton.setBackground(IconUtils.icon(getContext(), CommunityMaterial.Icon.cmd_chevron_right, R.color.dark_grey_text, 30, 10));
                }
            }
        };


        caldroidFragment.setCaldroidListener(listener);

        new LoadDispensationInfoTask().execute();
    }

    private void setupDispensationInfo() {

        setDispensationSummaryText();
        for (DispensationInfoEntity e : dispensationInfoStore.pickups()) {
            Interval interval = e.getDispenseInterval();
            if (interval.contains(DateTime.now())) {
                setSelectedInterval(interval);
            }
        }
        caldroidFragment.refreshView();
    }

    private void setDispensationSummaryText() {
        DispensationInfoEntity last = dispensationInfoStore.lastPickedUp();
        DispensationInfoEntity next = dispensationInfoStore.nextForPickUp(DateTime.now());
        String dtf = getString(R.string.pickup_date_format);
        String lastString;
        if (last != null && last.getDispensedDateTime() != null) {
            lastString = last.getDispensedDateTime().toString(dtf);
        } else {
            lastString = getString(R.string.not_available_short);
        }
        String nextString;
        if (next != null && next.getDispenseInterval() != null && next.getDispenseInterval().getStart() != null) {
            if (next.getDispenseInterval().getStart().isAfterNow()) {
                nextString = next.getDispenseInterval().getStart().toString(dtf);
            } else {
                nextString = DateTime.now().toString(dtf);
            }
        } else {
            nextString = getString(R.string.not_available_short);
        }

        dispensationInfoText.setText(getString(R.string.dispensation_summary_text, lastString, nextString));

        if (next != null && next.getDispenseInterval() != null && next.getDispenseInterval().getStart() != null) {
            if (next.getDispenseInterval().getStart().isBeforeNow()) {

                Drawable drawable = new IconicsDrawable(getContext())
                        .icon(CommunityMaterial.Icon2.cmd_information_variant)
                        .backgroundColorRes(R.color.white)
                        .roundedCornersDp(2)
                        .colorRes(R.color.android_green_dark)
                        .paddingDp(3)
                        .sizeDp(20);

                drawable.mutate();
                ImageSpan imageSpan = new ImageSpan(drawable);
                SpannableString span = new SpannableString("  " + getString(R.string.dispensation_interval_open));
                span.setSpan(imageSpan, 0, 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                dispensationIntervalInfo.setText(span);
            }
        } else {
            dispensationIntervalInfo.setVisibility(View.GONE);
        }
    }

    void setSelectedInterval(Interval interval) {
        clearSelectedInterval();
        for (DateTime date = interval.getStart(); date.isBefore(interval.getEnd().plusDays(1)); date = date.plusDays(1)) {
            caldroidFragment.setBackgroundDrawableForDate(intervalColor, date.toDate());
            intervalDates.add(date.toDate());
        }
        caldroidFragment.refreshView();
    }

    private void clearSelectedInterval() {
        if (!intervalDates.isEmpty()) {
            caldroidFragment.clearBackgroundDrawableForDates(intervalDates);
            caldroidFragment.refreshView();
        }
    }

    private SpannableString getInfoMessage(String msg, IIcon icon, Context ctx) {
        Drawable drawable = new IconicsDrawable(ctx)
                .icon(icon)
                .backgroundColorRes(R.color.android_blue_dark)
                .roundedCornersDp(2)
                .colorRes(R.color.white)
                .paddingDp(3)
                .sizeDp(16);
        drawable.mutate();
        ImageSpan imageSpan = new ImageSpan(drawable);
        SpannableString spannableString = new SpannableString("* " + msg);
        spannableString.setSpan(imageSpan, 0, 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return spannableString;
    }


    public static class DispensationFragment extends CaldroidFragment {
        @Override
        public CaldroidGridAdapter getNewDatesGridAdapter(int month, int year) {
            return new PickupCalendarAdapter(getActivity(), month, year, getCaldroidData(), extraData, dispensationInfoStore);
        }
    }

    private class LoadDispensationInfoTask extends AsyncTask<Void, Void, Void> {


        ProgressDialog dialog;

        @Override
        protected Void doInBackground(Void... params) {
            final List<DispensationInfoEntity> dispensationInfoEntities = new ArrayList<>();
            dispensationInfoEntities.addAll(activeMed.getBackingEntity().getDispensationInfo());
            dispensationInfoStore = new DispensationInfoStore(dispensationInfoEntities);
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            LogUtil.d(TAG, "onPreExecute: starting UpdatePickupsTask");
            dialog = new ProgressDialog(getContext());
            dialog.setIndeterminate(true);
            dialog.setMessage(getString(R.string.calendar_updating));
            dialog.show();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            FragmentTransaction t = getFragmentManager().beginTransaction();
            t.replace(R.id.dispensation_calendar, caldroidFragment);
            t.commit();
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            setupDispensationInfo();
        }
    }
}