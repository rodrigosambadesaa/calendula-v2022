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

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.FragmentManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.stepstone.stepper.Step;
import com.stepstone.stepper.StepperLayout;
import com.stepstone.stepper.VerificationError;
import com.stepstone.stepper.adapter.AbstractFragmentStepAdapter;
import com.stepstone.stepper.viewmodel.StepViewModel;

import org.apache.commons.lang3.StringUtils;
import org.dmfs.rfc5545.recur.Freq;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import es.usc.citius.servando.calendula.CalendulaActivity;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.healthcareprovider.util.ScheduleComparator;
import es.usc.citius.servando.calendula.healthcareprovider.util.ScheduleCreator;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.ScreenUtils;
import es.usc.citius.servando.calendula.util.HealthcareProviderTypeface;

import static es.usc.citius.servando.calendula.activities.schedules.ScheduleBuildActivity.ScheduleStep.CONFIRM;
import static es.usc.citius.servando.calendula.activities.schedules.ScheduleBuildActivity.ScheduleStep.DOSAGE;
import static es.usc.citius.servando.calendula.activities.schedules.ScheduleBuildActivity.ScheduleStep.MEDICINE;
import static es.usc.citius.servando.calendula.activities.schedules.ScheduleBuildActivity.ScheduleStep.REPEAT;
import static es.usc.citius.servando.calendula.activities.schedules.ScheduleBuildActivity.ScheduleStep.ROUTINES;

import com.mikepenz.iconics.typeface.IIcon;

public class ScheduleBuildActivity extends CalendulaActivity implements StepperLayout.StepperListener {

    private static final String TAG = "ScheduleBuildActivity";
    private final IIcon[] tabIcons = new IIcon[]{
            CommunityMaterial.Icon2.cmd_pill,
            CommunityMaterial.Icon.cmd_clock,
            CommunityMaterial.Icon.cmd_cup,
            CommunityMaterial.Icon2.cmd_history,
            CommunityMaterial.Icon.cmd_calendar,
    };
    Schedule tmpSchedule;
    Schedule editingSchedule;
    @BindView(R.id.root_view)
    View rootView;
    @BindView(R.id.stepperLayout)
    StepperLayout mStepperLayout;
    @BindView(R.id.schedule_step_title)
    TextView stepTitle;
    @BindView(R.id.dots)
    TextView dotsTv;
    @BindView(R.id.next_step_button)
    Button nextStep;
    @BindView(R.id.prev_step_button)
    Button prevStep;
    @BindView(R.id.bottom_navigation)
    View bottomNavigation;
    @BindView(R.id.appbar)
    AppBarLayout appBarLayout;
    @BindView(R.id.collapsing_toolbar)
    CollapsingToolbarLayout collapsingToolbarLayout;
    @BindView(R.id.toolbar_title)
    TextView title;
    @BindView(R.id.tabs)
    TabLayout tabs;
    @BindView(R.id.active_med_info_container)
    View activeMedInfoContainer;
    @BindView(R.id.provider_icon)
    ImageView providerIcon;
    @BindView(R.id.changes_icon)
    ImageView changesIcon;
    private IconicsDrawable selectedIc;
    private IconicsDrawable unselectedIc;
    private ScheduleStepperAdapter adapter;
    private IconicsDrawable nextIc;
    private IconicsDrawable prevIc;
    private IconicsDrawable changesIc;
    private IconicsDrawable noChangesIc;
    private IconicsDrawable checkingChangesIc;
    private Collection<ScheduleComparator.Change> changes;
    private CheckChangesTask checkChangesTask;
    private EditMode editMode = EditMode.CREATE;
    private ActiveMedVO activeMed;

    private boolean differsFromPrescription = false;

    @Override
    public void onCompleted(View completeButton) {
        if (differsFromPrescription) {
            new MaterialStyledDialog.Builder(this)
                    .setTitle(R.string.schedule_build_reminder_different_from_schedule)
                    .setStyle(Style.HEADER_WITH_ICON)
                    .setIcon(IconUtils.icon(this, HealthcareProviderTypeface.Icon.hcp_healthcare_provider, R.color.white, 48))
                    .setHeaderColor(R.color.healthcare_provider_dark)
                    .withDialogAnimation(false)
                    .setDescription(R.string.save_schedule_ignore_conflicts_msg)
                    .setPositiveText(getString(R.string.dialog_yes_option))
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                            new OnCompleteTask().execute();
                        }
                    })
                    .setNegativeText(getString(R.string.cancel))
                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                        }
                    })
                    .show();
        } else {
            new OnCompleteTask().execute();
        }
    }

    @Override
    public void onError(VerificationError verificationError) {

        final Snackbar snackbar = Snackbar.make(rootView, verificationError.getErrorMessage(), Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.tutorial_understood, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snackbar.dismiss();
            }
        });
        snackbar.show();

        if (isInEditMode() && verificationError instanceof ScheduleVerificationError) {
            ScheduleVerificationError e = (ScheduleVerificationError) verificationError;
            tabs.setScrollPosition(e.step.ordinal(), 0f, true);
        }
    }

    @Override
    public void onStepSelected(int newStepPosition) {

        String t = getTile(newStepPosition);
        setToolbarTitle(StringUtils.abbreviate(getToolbarTitleFor(newStepPosition), 25));
        stepTitle.setText(t);
        dotsTv.setText(getDots(newStepPosition));
        nextStep.setText(newStepPosition < adapter.getCount() - 1 ? getString(R.string.schedule_build_control_next) : getString(R.string.schedule_build_control_confirm));
        if (editingSchedule != null && editingSchedule.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL))
            nextStep.setEnabled(newStepPosition < adapter.getCount() - 1 ? true: false);
        else
            nextStep.setEnabled(true);
        prevStep.setText(newStepPosition > 0 ? getString(R.string.schedule_build_control_previous) : "");
        nextStep.setCompoundDrawables(null, null, newStepPosition < adapter.getCount() - 1 ? nextIc : null, null);
        prevStep.setCompoundDrawables(newStepPosition > 0 ? prevIc : null, null, null, null);
        tabs.setScrollPosition(newStepPosition, 0f, true);

        if (editMode == EditMode.CREATE && (newStepPosition == adapter.getCount() - 1)) {
            nextStep.setTextColor(getResources().getColor(R.color.android_green));
        } else {
            nextStep.setTextColor(getResources().getColor(R.color.activity_schedule_build_primary));
        }
    }

    @Override
    public void onReturn() {
        finish();
    }

    public void proceed() {
        mStepperLayout.proceed();
    }

    public IconicsDrawable selectionIcon(boolean selected) {
        if (selectedIc == null) {
            selectedIc = new IconicsDrawable(this)
                    .icon(CommunityMaterial.Icon.cmd_checkbox_marked_circle_outline)
                    .colorRes(R.color.activity_schedule_build_contrast)
                    .paddingDp(5)
                    .sizeDp(40);
            unselectedIc = new IconicsDrawable(this)
                    .icon(CommunityMaterial.Icon.cmd_checkbox_blank_circle_outline)
                    .colorRes(R.color.activity_schedule_build_foreground_alpha)
                    .paddingDp(5)
                    .sizeDp(40);
        }
        return selected ? selectedIc : unselectedIc;
    }

    public IconicsDrawable iconFor(Presentation p) {
        return new IconicsDrawable(this)
                .icon(p.icon())
                .colorRes(R.color.activity_schedule_build_foreground)
                .paddingDp(5)
                .sizeDp(55);
    }

    public Medicine getMedicine() {
        return tmpSchedule.getMedicine();
    }

    void setMedicine(Medicine medicine) {
        LogUtil.d(TAG, "setMedicine() called with: medicine = [" + medicine + "]");
        tmpSchedule.setMedicine(medicine);
        launchCheckChangesTask();
    }

    public RecurringEvent getRecurringEvent() {
        return tmpSchedule.getRecur();
    }

    void setRecurringEvent(RecurringEvent recurringEvent) {
        LogUtil.d(TAG, "setRecurringEvent() called with: recurringEvent = [" + recurringEvent + "]");
        tmpSchedule.setRecur(recurringEvent);
        launchCheckChangesTask();
    }

    public List<DailyFixedTime> getIntakes() {
        return tmpSchedule.getRecur().hasDailyFixedTimes() ?
                tmpSchedule.getRecur().getDailyFixedTimes() :
                new ArrayList<DailyFixedTime>();
    }

    public Integer getHourlyInterval() {
        return tmpSchedule.getRecur().getFreq().equals(Freq.HOURLY) ? tmpSchedule.getRecur().getInterval() : 0;
    }

    void setHourlyInterval(Integer hourlyInterval) {
        LogUtil.d(TAG, "setHourlyInterval() called with: hourlyInterval = [" + hourlyInterval + "]");
        try {
            this.tmpSchedule.getRecur().edit().repeatEvery(hourlyInterval, Freq.HOURLY)
                    .clearDailyFixedTimes()
                    .clearWeekdays()
                    .commit();
            launchCheckChangesTask();
        } catch (RecurringEvent.InvalidRecurringEventException e) {
            LogUtil.e(TAG, "setHourlyInterval error", e);
        }

    }

    public boolean isInEditMode() {
        return editMode.equals(EditMode.EDIT);
    }

    void hideBottomNavigation() {
        bottomNavigation.setVisibility(View.GONE);
    }

    void showBottomNavigation() {
        bottomNavigation.setVisibility(View.GONE);
    }

    @OnClick(R.id.next_step_button)
    void onNextClick() {
        mStepperLayout.proceed();
    }

    @OnClick(R.id.prev_step_button)
    void onPrevClick() {
        mStepperLayout.onBackClicked();
    }

    void setDosages(Map<Long, Double> dosages) {
        LogUtil.d(TAG, "setDosages() called with: dosages = [" + dosages + "]");
        this.tmpSchedule.setDosages(dosages);
        launchCheckChangesTask();
    }

    void setDosage(Double dosage) {
        LogUtil.d(TAG, "setDosage() called with: dosages = [" + dosage + "]");
        this.tmpSchedule.setDosage(dosage);
        launchCheckChangesTask();
    }

    void setDailyFixedTimes(List<DailyFixedTime> times) {
        try {
            LogUtil.d(TAG, "setRoutines: " + times.size());
            RecurringEvent.Builder editor = this.tmpSchedule.getRecur().edit();
//            LogUtil.d(TAG, "TmpSchedule: " + tmpSchedule.recur().getInterval() + ", " + tmpSchedule.recur().getFreq());
//            LogUtil.d(TAG, "EditingSchedule: " + editingSchedule.recur().getInterval() +", " + editingSchedule.recur().getFreq());

            if (tmpSchedule.getRecur().getFreq() == null) {
                LogUtil.d(TAG, "Set daily freq");
                editor.repeatEvery(1, Freq.DAILY);
            } else if (tmpSchedule.getRecur().hasHourlyFrequency()) {
                LogUtil.d(TAG, "tmpSchedule has hourly freq");
                if (editingSchedule != null && editingSchedule.getRecur().getFreq() != Freq.HOURLY) {
                    editor.repeatEvery(editingSchedule.getRecur().getInterval(), editingSchedule.getRecur().getFreq());
                    editor.repeatWeekdays(editingSchedule.getRecur().weekDays());
                } else {
                    editor.repeatEvery(1, Freq.DAILY);
                    editor.repeatWeekdays(RecurringEvent.WeekDay.ALL);
                }
            }

            editor.clearDailyFixedTimes();
            editor.dailyRepeatTimes(times);
            editor.commit();
            launchCheckChangesTask();
        } catch (RecurringEvent.InvalidRecurringEventException e) {
            LogUtil.e(TAG, "setHourlyInterval error", e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule_build);
        setupStatusBar(getResources().getColor(R.color.transparent));
        setupToolbar("", getResources().getColor(R.color.transparent));
        ButterKnife.bind(this);

        changesIc = new IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_alert_circle)
                .colorRes(R.color.android_orange_dark)
                .paddingDp(5)
                .sizeDp(30);

        noChangesIc = new IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_check)
                .colorRes(R.color.android_green)
                .paddingDp(5)
                .sizeDp(30);

        checkingChangesIc = new IconicsDrawable(this)
                .icon(CommunityMaterial.Icon2.cmd_sync)
                .colorRes(R.color.android_green)
                .paddingDp(5)
                .sizeDp(30);

        ScheduleUtils.instance().removeUnlinkedAutoRoutines(true);

        String action = getIntent().getStringExtra(IntentParams.EXTRA_ACTION);
        Long editScheduleId = getIntent().getLongExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID, -1);
        if (editScheduleId != -1) {
            Schedule s = DB.schedules().findById(editScheduleId);
            if (s != null) {
                enableEditMode(s);
            }
        } else if (IntentParams.ACTION_CREATE_REMINDER.equals(action)) {
            Long activeMedId = getIntent().getLongExtra(IntentParams.EXTRA_ACTIVEMED_ID, -1);
            Pair<ScheduleCreator.ScheduleMappingResult, Schedule> result = ScheduleCreator.fromActiveMed(activeMedId);
            Toast.makeText(this, result.first + "", Toast.LENGTH_SHORT).show();
            if (result.first != ScheduleCreator.ScheduleMappingResult.NONE) {
                enableEditMode(result.second);
                if (result.first == ScheduleCreator.ScheduleMappingResult.PARTIAL) {
                    showPartialScheduleMappingMsg();
                }
            } else {
                Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            try {
                RecurringEvent e = new RecurringEvent.Builder()
                        .repeatEvery(8, Freq.HOURLY)
                        .from(DateTime.now())
                        .build();
                tmpSchedule = new Schedule(e);
                tmpSchedule.setDosage(1d);
            } catch (Exception e) {
                LogUtil.e(TAG, "onCreate: ", e);
                finish();
            }

        }

        if (isInEditMode() && editingSchedule.isBoundToActiveMed() && editingSchedule.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)) {
            this.activeMed = ActiveMedVO.forEntity(DB.healthcareProviderDB().activeMeds().findById(editingSchedule.getActiveMedId()));
            activeMedInfoContainer.setVisibility(View.VISIBLE);
            providerIcon.setImageDrawable(new IconicsDrawable(this)
                    .icon(HealthcareProviderTypeface.Icon.hcp_healthcare_provider)
                    .colorRes(R.color.activity_schedule_build_foreground)
                    .paddingDp(3)
                    .sizeDp(30));

            activeMedInfoContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showProviderPrescriptionInfo();
                }
            });

            if (editingSchedule.hasState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_DELETE)) {
                showPendingByDeleteDialog();
            } else if (editingSchedule.hasState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_UPDATE)) {
                showPendingByUpdateDialog();
            }
        } else {
            activeMedInfoContainer.setVisibility(View.GONE);
        }

        ScheduleStep[] createStepSequence = new ScheduleStep[] {MEDICINE, ROUTINES, DOSAGE, REPEAT, CONFIRM};
        ScheduleStep[] editStepSequence = new ScheduleStep[] {ROUTINES, DOSAGE, REPEAT, CONFIRM};;
        adapter = new ScheduleStepperAdapter(getSupportFragmentManager(), this, isInEditMode() ? editStepSequence: createStepSequence);
        mStepperLayout.setAdapter(adapter);
        mStepperLayout.setListener(this);
        mStepperLayout.setTabNavigationEnabled(true);
        mStepperLayout.setShowBottomNavigation(false);
        mStepperLayout.setOffscreenPageLimit(10);

        title.setAlpha(0);
        setupTabLayout();

        nextIc = new IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_chevron_right)
                .colorRes(R.color.activity_schedule_build_primary)
                .paddingDp(5)
                .sizeDp(20);

        prevIc = new IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_chevron_left)
                .colorRes(R.color.activity_schedule_build_primary)
                .paddingDp(5)
                .sizeDp(20);


        appBarLayout.addOnOffsetChangedListener(new AppBarStateChangeListener() {
            @Override
            public void onStateChanged(AppBarLayout appBarLayout, State state) {
                if (state == State.COLLAPSED) {
                    showTitle();
                } else if (state == State.EXPANDED) {
                    stepTitle.animate().alpha(1);
                    hideTitle();
                } else {
                    stepTitle.animate().alpha(0);
                    hideTitle();
                }
            }
        });

        if (editMode == EditMode.EDIT) {
            tabs.setVisibility(View.VISIBLE);
            title.setVisibility(View.GONE);
            stepTitle.setAlpha(0);
            stepTitle.setPadding(0, 0, 0, ScreenUtils.dpToPx(getResources(), 70));
            collapsingToolbarLayout.setBackgroundColor(getResources().getColor(R.color.activity_schedule_build_foreground));
            stepTitle.setTextColor(getResources().getColor(R.color.activity_schedule_build_primary));
            appBarLayout.setExpanded(false);
            toolbar.setVisibility(View.INVISIBLE);
//            if (editingSchedule.boundToActiveMed() && editingSchedule.getId() == null) {
//                //showProviderPrescriptionInfo();
//            }
        } else {
            tabs.setVisibility(View.GONE);
            title.setVisibility(View.VISIBLE);
        }

        launchCheckChangesTask();
    }

    private void showPartialScheduleMappingMsg() {

        new MaterialStyledDialog.Builder(this)
                .setTitle(getString(R.string.schedule_partial_match_dialog_title))
                .setStyle(Style.HEADER_WITH_ICON)
                .setIcon(IconUtils.icon(this, HealthcareProviderTypeface.Icon.hcp_healthcare_provider, R.color.white, 48))
                .setHeaderColor(R.color.healthcare_provider_dark)
                .withDialogAnimation(false)
                .setDescription(getString(R.string.schedule_partial_match_dialog_msg))
                .setPositiveText(getString(R.string.tutorial_understood))
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void showTitle() {
        title.animate().alpha(1);
    }

    private void hideTitle() {
        title.animate().alpha(0);
    }

    private void setToolbarTitle(String toolbarTitle) {
        title.setText(toolbarTitle);
    }

    private void enableEditMode(Schedule s) {
        if (s != null) {
            try {
                editingSchedule = s;
                tmpSchedule = new Schedule(s.getRecur().edit().build());
                editMode = EditMode.EDIT;
                tmpSchedule.setMedicine(s.getMedicine());
                tmpSchedule.setDosages(new HashMap<>(s.getDosages()));
                LogUtil.d(TAG, "enableEditMode: " + tmpSchedule.toString());
            } catch (RecurringEvent.InvalidRecurringEventException e) {
                LogUtil.e(TAG, "Invalid recurring event: ", e);
                finish();
            }
        }
    }

    private void launchCheckChangesTask() {
        LogUtil.d(TAG, "launchCheckChangesTask: launching task");
        if (editingSchedule != null && editingSchedule.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)) {
            if (checkChangesTask != null) {
                checkChangesTask.cancel(true);
            }
            mStepperLayout.postDelayed(new Runnable() {
                @Override
                public void run() {
                    changesIcon.setImageDrawable(checkingChangesIc);
                    changesIcon.setOnClickListener(null);
                    checkChangesTask = new CheckChangesTask();
                    checkChangesTask.execute();
                }
            }, 600);
        }
    }

    private void showProviderPrescriptionInfo() {
        if (activeMed != null) {
            String dosage = activeMed.getDosage().toReadableString(this).toLowerCase() + "\n";
            final String dateFormat = getString(R.string.schedule_limits_date_format);
            if(activeMed.getValidityStart() != null){
                dosage += "\n● " + getString(R.string.active_med_from) + " " + activeMed.getValidityStart().toString(dateFormat);
            }
            if(activeMed.getValidityEnd() != null){
                dosage += "\n● " + getString(R.string.active_med_to) + " " + activeMed.getValidityEnd().toString(dateFormat);
            }
            Spannable spannable = new SpannableString(dosage);
            spannable.setSpan(new RelativeSizeSpan(0.9f), 0, dosage.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            String title = getString(R.string.schedule_build_official_prescription);
            showInfoDialog(title, spannable);
        }
    }

    private void showPendingByDeleteDialog() {
        String msg = getString(R.string.schedule_build_pending_by_delete_message);
        Spannable spannable = new SpannableString(msg);
        spannable.setSpan(new RelativeSizeSpan(0.9f), 0, msg.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        String title = getString(R.string.schedule_build_pending_by_delete_title);
        showInfoDialog(title, spannable);
    }

    private void showPendingByUpdateDialog() {
        String msg = getString(R.string.schedule_build_pending_by_update_message);
        Spannable spannable = new SpannableString(msg);
        spannable.setSpan(new RelativeSizeSpan(0.9f), 0, msg.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        String title = getString(R.string.schedule_build_pending_by_update_title);
        showInfoDialog(title, spannable);
    }

    private void showInfoDialog(String title, Spannable msg) {
        if (activeMed != null) {
            new MaterialStyledDialog.Builder(this)
                    .setTitle(title)
                    .setStyle(Style.HEADER_WITH_ICON)
                    .setIcon(IconUtils.icon(this, HealthcareProviderTypeface.Icon.hcp_healthcare_provider, R.color.white, 48))
                    .setHeaderColor(R.color.healthcare_provider_dark)
                    .withDialogAnimation(false)
                    .setDescription(msg)
                    .setPositiveText(getString(R.string.tutorial_understood))
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                        }
                    })
                    .show();
        }
    }

    private void showChanges(Collection<ScheduleComparator.Change> changes) {

        LogUtil.d(TAG, tmpSchedule.toString());

        if (activeMed != null) {
            StringBuilder text = new StringBuilder();
            for (ScheduleComparator.Change c : changes) {
                text.append("● ").append(c.description(this)).append("\n\n");
            }
            Spannable spannable = new SpannableString(text.toString());
            spannable.setSpan(new RelativeSizeSpan(0.9f), 0, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            new MaterialStyledDialog.Builder(this)
                    .setTitle(R.string.schedule_build_reminder_different_from_schedule)
                    .setStyle(Style.HEADER_WITH_ICON)
                    .setIcon(IconUtils.icon(this, HealthcareProviderTypeface.Icon.hcp_healthcare_provider, R.color.white, 48))
                    .setHeaderColor(R.color.healthcare_provider_dark)
                    .withDialogAnimation(false)
                    .setDescription(spannable)
                    .setPositiveText(getString(R.string.tutorial_understood))
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                        }
                    }).setNeutralText(getString(R.string.schedule_build_official_prescription))
                    .onNeutral(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                            showProviderPrescriptionInfo();
                        }
                    })
                    .show();
        }
    }

    private void setupTabLayout() {
        tabs.setSelectedTabIndicatorColor(getResources().getColor(R.color.android_green));
        for (int i = 0; i < adapter.getCount(); i++) {
            tabs.addTab(tabs.newTab().setIcon(
                    new IconicsDrawable(this)
                            .icon(tabIcons[i])
                            .paddingDp(2)
                            .color(Color.WHITE)
                            .sizeDp(24)
            ));
            tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    mStepperLayout.onTabClicked(tab.getPosition());
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {

                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {

                }
            });
        }
    }

    private Spannable getDots(int currentPage) {
        int color = getResources().getColor(R.color.android_green);
        String dots = StringUtils.repeat("●", adapter.getCount());
        Spannable spannable = new SpannableString(dots);
        for (int i = 0; i < adapter.getCount(); i++) {
            spannable.setSpan(new RelativeSizeSpan(1.2f), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (i == currentPage) {
                spannable.setSpan(new ForegroundColorSpan(color), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return spannable;
    }

    private String getTile(int page) {

        if (editMode.equals(EditMode.EDIT)) {
            return getToolbarTitleFor(page);
        }

        String title = "";
        switch (adapter.getStepSequence()[page]) {
            case MEDICINE:
                title = getString(R.string.schedule_build_tab_prompt_medicine);
                break;
            case ROUTINES:
                title = getString(R.string.schedule_build_tab_prompt_intakes, tmpSchedule.getMedicine().getName());
                break;
            case DOSAGE:
                title = getString(R.string.schedule_build_tab_prompt_dosage);
                break;
            case REPEAT:
                title = getString(R.string.schedule_build_tab_prompt_repetition);
                break;
            case CONFIRM:
                title = getString(R.string.schedule_build_tab_prompt_summary);
                break;
        }
        return title;
    }

    private String getToolbarTitleFor(int page) {
        String title = "";
        switch (adapter.getStepSequence()[page]) {
            case MEDICINE:
                title = getString(R.string.schedule_build_tab_title_medicine);
                break;
            case ROUTINES:
                title = getString(R.string.schedule_build_tab_title_intakes);
                break;
            case DOSAGE:
                title = getString(R.string.schedule_build_tab_title_dosage);
                break;
            case REPEAT:
                title = getString(R.string.schedule_build_tab_title_repetition);
                break;
            case CONFIRM:
                title = getString(R.string.schedule_build_tab_title_summary);
                break;
        }
        return title;
    }

    enum EditMode {
        CREATE,
        EDIT
    }

    enum ScheduleStep {
        MEDICINE,
        ROUTINES,
        DOSAGE,
        REPEAT,
        CONFIRM
    }

    public static class ScheduleStepperAdapter extends AbstractFragmentStepAdapter {

        public ScheduleStep[] getStepSequence() {
            return stepSequence;
        }

        private final ScheduleStep[] stepSequence;

        public ScheduleStepperAdapter(FragmentManager fm, Context context, @NonNull ScheduleStep[] stepSequence) {
            super(fm, context);
            this.stepSequence = stepSequence;
        }

        @Override
        public Step createStep(int position) {
            final Step step;
            switch(stepSequence[position]) {
                case MEDICINE:
                    step = new PickMedStepFragment();
                    break;
                case ROUTINES:
                    step = new PickRoutineStepFragment();
                    break;
                case DOSAGE:
                    step = new DosageStepFragment();
                    break;
                case REPEAT:
                    step = new DaySelectionStepFragment();
                    break;
                case CONFIRM:
                    default:
                    step = new SummaryStepFragment();
                    break;
            }
            return step;
        }

        @Override
        public int getCount() {
            return stepSequence.length;
        }


        @NonNull
        @Override
        public StepViewModel getViewModel(@IntRange(from = 0) int position) {
            //Override this method to set Step title for the Tabs, not necessary for other stepper types
            return new StepViewModel.Builder(context)
                    .setTitle("Tab " + position)
                    .setNextButtonLabel(context.getString(R.string.schedule_build_control_next))
                    .setBackButtonLabel(context.getString(R.string.schedule_build_control_previous))
                    .create();
        }
    }

    static class ScheduleVerificationError extends VerificationError {
        final ScheduleStep step;

        ScheduleVerificationError(String error, ScheduleStep step) {
            super(error);
            this.step = step;
        }
    }

    static abstract class AppBarStateChangeListener implements AppBarLayout.OnOffsetChangedListener {

        private State mCurrentState = State.IDLE;

        @Override
        public final void onOffsetChanged(AppBarLayout appBarLayout, int i) {
            if (i == 0) {
                if (mCurrentState != State.EXPANDED) {
                    onStateChanged(appBarLayout, State.EXPANDED);
                }
                mCurrentState = State.EXPANDED;
            } else if (Math.abs(i) >= appBarLayout.getTotalScrollRange()) {
                if (mCurrentState != State.COLLAPSED) {
                    onStateChanged(appBarLayout, State.COLLAPSED);
                }
                mCurrentState = State.COLLAPSED;
            } else {
                if (mCurrentState != State.IDLE) {
                    onStateChanged(appBarLayout, State.IDLE);
                }
                mCurrentState = State.IDLE;
            }
        }

        public abstract void onStateChanged(AppBarLayout appBarLayout, State state);

        enum State {
            EXPANDED,
            COLLAPSED,
            IDLE
        }
    }

    public class CheckChangesTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (activeMed == null || tmpSchedule == null) {
                cancel(true);
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (activeMed != null && tmpSchedule != null) {
                changes = ScheduleComparator.INSTANCE.compare(activeMed, tmpSchedule);
                return !changes.isEmpty();
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean res) {
            super.onPostExecute(res);
            differsFromPrescription = res;
            nextStep.setEnabled(true);
            if (!res) {
                changesIcon.setImageDrawable(noChangesIc);
                changesIcon.setOnClickListener(null);
            } else {
                changesIcon.setImageDrawable(changesIc);
                changesIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showChanges(changes);
                    }
                });
            }
        }
    }

    public class OnCompleteTask extends AsyncTask<Void, Void, Void> {

        ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            String text;
            if (isInEditMode()) {
                text = getString(R.string.schedule_build_updating_reminder_for);
            } else {
                text = getString(R.string.schedule_build_creating_reminder_for);
            }

            text += tmpSchedule.getMedicine().getName() + "...";
            dialog = ProgressDialog.show(ScheduleBuildActivity.this, "", text, true);

        }

        @Override
        protected Void doInBackground(Void... params) {
            if (isInEditMode()) {

                if (editingSchedule.hasState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_DELETE)) {
                    editingSchedule.removeState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_DELETE);
                }
                if (editingSchedule.hasState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_UPDATE)) {
                    editingSchedule.removeState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_UPDATE);
                }

                if (differsFromPrescription) {
                    editingSchedule.addState(Schedule.ScheduleState.DIFFERS_FROM_OFFICIAL);
                } else if (editingSchedule.hasState(Schedule.ScheduleState.DIFFERS_FROM_OFFICIAL)) {
                    editingSchedule.removeState(Schedule.ScheduleState.DIFFERS_FROM_OFFICIAL);
                }

                editingSchedule.setMedicine(tmpSchedule.getMedicine());
                editingSchedule.setDosages(tmpSchedule.getDosages());
                editingSchedule.setRecur(tmpSchedule.getRecur());
                DB.schedules().save(editingSchedule);
                ScheduleUtils.instance().updateEventInstances(ScheduleBuildActivity.this, editingSchedule);
            } else {
                Schedule s = new Schedule(tmpSchedule.getRecur());
                s.setPatient(DB.patients().getActive(ScheduleBuildActivity.this));
                s.setMedicine(tmpSchedule.getMedicine());
                s.setDosages(tmpSchedule.getDosages());

                if (differsFromPrescription) {
                    editingSchedule.addState(Schedule.ScheduleState.DIFFERS_FROM_OFFICIAL);
                }

                DB.schedules().save(s);
                ScheduleUtils.instance().createEventInstances(ScheduleBuildActivity.this, s);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void res) {
            super.onPostExecute(res);
            if (dialog != null) {
                dialog.dismiss();
            }
            finish();
            DB.schedules().fireEvent();
        }
    }

    @Override
    protected void onDestroy() {
        ScheduleUtils.instance().removeUnlinkedAutoRoutines(true);
        super.onDestroy();
    }
}
