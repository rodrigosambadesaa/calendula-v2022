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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
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
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

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
import es.usc.citius.servando.calendula.healthcareprovider.database.ActiveMedOwnerGuard;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;
import es.usc.citius.servando.calendula.util.DispensationInfoStore;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.LogUtil;


public class ActiveMedInfoFragment extends Fragment {

    private static final String TAG = "ActiveMedInfoFragment";
    private static final ExecutorService DISPENSATION_EXECUTOR = Executors.newSingleThreadExecutor();

    private final static String ID_ARG = "active_med_id";

    private final static DateTimeFormatter dateFormat = DateTimeFormat.forPattern("dd/MM/yyyy");
    private DispensationInfoStore dispensationInfoStore;
    private final AtomicLong dispensationLoadGeneration = new AtomicLong();
    @Nullable private ProgressDialog dispensationProgressDialog;
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
        refreshData();
        if (activeMed == null) {
            closeUnavailableProviderPage();
            return;
        }
        setupView();
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
        invalidateDispensationLoad();
        super.onDestroyView();
        if (unbinder != null)
            unbinder.unbind();
    }

    @Override
    public void onStop() {
        invalidateDispensationLoad();
        super.onStop();
    }

    private void invalidateDispensationLoad() {
        dispensationLoadGeneration.incrementAndGet();
        if (dispensationProgressDialog != null) {
            if (dispensationProgressDialog.isShowing()) {
                dispensationProgressDialog.dismiss();
            }
            dispensationProgressDialog = null;
        }
    }

    /** A callback from an older refresh must not overwrite a newer calendar. */
    static boolean isCurrentDispensationLoad(long requestedGeneration, long currentGeneration) {
        return requestedGeneration == currentGeneration;
    }

    public void notifyDataChange() {
        refreshData();
        if (activeMed == null) {
            closeUnavailableProviderPage();
        } else if (!isStateSaved() && getView() != null) {
            setupView();
        }
    }

    private void closeUnavailableProviderPage() {
        invalidateDispensationLoad();
        // The selected profile/official medication may have changed while
        // this fragment was paused. Never leave the old dosage visible.
        if (getView() != null) {
            getView().setVisibility(View.GONE);
        }
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private void refreshData() {
        activeMed = null;
        if (getArguments() == null || getContext() == null) {
            return;
        }
        Patient patient = DB.patients().getActive(getContext());
        final ActiveMedEntity medEntity = DB.healthcareProviderDB().activeMeds()
                .findByCodeForPatient(getArguments().getString(ID_ARG, null), patient);
        if (medEntity != null) {
            activeMed = ActiveMedVO.forEntity(medEntity);
        }
    }

    /** Verify all current owner links again after a click or dialog selection. */
    private void openVerifiedReminder(@Nullable Long chosenScheduleId) {
        if (getContext() == null || activeMed == null
                || activeMed.getBackingEntity() == null) {
            return;
        }
        final Context context = getContext();
        final Long providerId = activeMed.getBackingEntity().getId();
        final Patient nowActive = DB.patients().getActive(context);
        final ActiveMedEntity provider = DB.healthcareProviderDB().activeMeds()
                .findByIdForPatient(providerId, nowActive);
        if (provider == null || provider.getPatient() == null || nowActive == null) {
            closeUnavailableProviderPage();
            return;
        }
        final OfficialReminderLinkSelector.Selection selection =
                OfficialReminderLinkSelector.inspect(
                        provider.getPatient().getId(), nowActive.getId(),
                        provider.getPatient().getId(),
                        DB.schedules().findBy(Schedule.BOUND_TO, providerId));
        if (selection.action == OfficialReminderLinkSelector.Action.UNAVAILABLE) {
            closeUnavailableProviderPage();
            return;
        }
        final Intent intent = new Intent(context, ScheduleBuildActivity.class);
        if (chosenScheduleId != null) {
            for (Schedule schedule : selection.schedules) {
                if (chosenScheduleId.equals(schedule.getId())) {
                    intent.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID, chosenScheduleId);
                    startActivity(intent);
                    return;
                }
            }
            closeUnavailableProviderPage();
            return;
        }
        if (selection.action == OfficialReminderLinkSelector.Action.CHOOSE) {
            final CharSequence[] labels = new CharSequence[selection.schedules.size()];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = context.getString(R.string.aml_reminder_choice_item, i + 1);
            }
            new AlertDialog.Builder(context)
                    .setTitle(R.string.aml_reminder_choose_title)
                    .setItems(labels, (dialog, which) -> {
                        if (which >= 0 && which < selection.schedules.size()) {
                            openVerifiedReminder(selection.schedules.get(which).getId());
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        if (selection.action == OfficialReminderLinkSelector.Action.EDIT) {
            intent.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID,
                    selection.schedules.get(0).getId());
        } else {
            intent.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_CREATE_REMINDER);
            intent.putExtra(IntentParams.EXTRA_ACTIVEMED_ID, providerId);
        }
        startActivity(intent);
    }

    /** Malformed provider dosage entries must not crash the detail fragment. */
    static String firstPatientInstructionOrNull(List<DosageEntryVO> entries) {
        if (entries == null || entries.isEmpty() || entries.get(0) == null) {
            return null;
        }
        String text = entries.get(0).getPatientInstruction();
        return org.apache.commons.lang3.StringUtils.isBlank(text) ? null : text;
    }

    /** Reset or restore each provider validity field on a reused detail view. */
    static void bindValidityDate(View container, TextView label, @Nullable DateTime date) {
        if (date == null) {
            label.setText(null);
            container.setVisibility(View.GONE);
        } else {
            label.setText(date.toString(dateFormat));
            container.setVisibility(View.VISIBLE);
        }
    }

    /** Missing updated dosage must not leave a previous prescription visible. */
    static void bindDosageText(TextView label, @Nullable String readable) {
        if (readable == null || readable.trim().isEmpty()) {
            label.setText(R.string.dosage_string_not_available);
        } else {
            label.setText(readable);
        }
    }

    private void setupView() {
        if (activeMed == null || getView() == null || getContext() == null) {
            return;
        }
        Patient currentPatient = DB.patients().getActive(getContext());
        ActiveMedEntity provider = activeMed.getBackingEntity();
        if (provider == null || provider.getPatient() == null
                || currentPatient == null || !ActiveMedOwnerGuard.samePatient(
                        provider.getPatient().getId(), currentPatient.getId())) {
            closeUnavailableProviderPage();
            return;
        }
        final Long providerId = provider.getId();
        if (providerId == null || providerId <= 0L) {
            closeUnavailableProviderPage();
            return;
        }
        final OfficialReminderLinkSelector.Selection linked =
                OfficialReminderLinkSelector.inspect(
                        provider.getPatient().getId(), currentPatient.getId(),
                        provider.getPatient().getId(),
                        DB.schedules().findBy(Schedule.BOUND_TO, providerId));
        if (linked.action == OfficialReminderLinkSelector.Action.UNAVAILABLE) {
            // A single foreign/ownerless link makes every reminder inaccessible.
            closeUnavailableProviderPage();
            return;
        }
        // validity period
        final DateTime validityStart = activeMed.getValidityStart();
        final DateTime validityEnd = activeMed.getValidityEnd();

        // This fragment's view can be reused after a provider refresh.
        // Clear absent values and restore hidden containers for new values.
        bindValidityDate(validityStartContainer, validityStartText, validityStart);
        bindValidityDate(validityEndContainer, validityEndText, validityEnd);

        final DosageVO dosage = activeMed.getDosage();
        bindDosageText(scheduleText,
                dosage == null ? null : dosage.toReadableString(getContext()));

        List<DosageEntryVO> entries = dosage == null ? null : dosage.getEntries();
        String instructions = firstPatientInstructionOrNull(entries);
        if (instructions != null) {
            intakeInstructions.setText(getInfoMessage(
                    getContext().getString(R.string.instructions_prefix) + " " + instructions,
                    CommunityMaterial.Icon2.cmd_medical_bag, getContext()));
            intakeInstructions.setVisibility(View.VISIBLE);
        } else {
            intakeInstructions.setVisibility(View.GONE);
        }

        final boolean hasReminders =
                linked.action != OfficialReminderLinkSelector.Action.CREATE;

        remindersButton.setCompoundDrawables(null, null, IconUtils.icon(getContext(),
                hasReminders ? CommunityMaterial.Icon.cmd_bell_ring : CommunityMaterial.Icon.cmd_bell_plus,
                R.color.android_green, 30, 5), null);
        remindersButton.setText(getString(hasReminders ?
                R.string.edit_reminder_text : R.string.create_reminder_text));
        remindersButton.setOnClickListener(view -> openVerifiedReminder(null));

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

        loadDispensationInfoAsync();
    }

    private void setupDispensationInfo() {

        setDispensationSummaryText();
        for (DispensationInfoEntity e : dispensationInfoStore.pickups()) {
            if (e == null) {
                continue;
            }
            Interval interval = e.getDispenseInterval();
            if (interval != null && interval.contains(DateTime.now())) {
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
        // Retained nested fragments must carry their own owner-specific data.
        // An empty fallback prevents another detail page's cache being reused.
        private DispensationInfoStore ownStore;

        public void setStore(DispensationInfoStore store) {
            this.ownStore = store;
        }

        @Override
        public CaldroidGridAdapter getNewDatesGridAdapter(int month, int year) {
            DispensationInfoStore data = ownStore == null
                    ? new DispensationInfoStore(new ArrayList<>()) : ownStore;
            return new PickupCalendarAdapter(getActivity(), month, year, getCaldroidData(), extraData, data);
        }
    }

    private void loadDispensationInfoAsync() {
        final Context context = getContext();
        if (context == null || activeMed == null) {
            return;
        }

        LogUtil.d(TAG, "loadDispensationInfoAsync: starting");
        // Serial background work can finish after a newer medication refresh.
        // Discard stale results and retire the previous loading dialog.
        invalidateDispensationLoad();
        final long requestedGeneration = dispensationLoadGeneration.get();
        final ProgressDialog dialog = new ProgressDialog(context);
        dialog.setIndeterminate(true);
        dialog.setMessage(getString(R.string.calendar_updating));
        dialog.show();
        dispensationProgressDialog = dialog;

        final ActiveMedEntity activeMedEntity = activeMed.getBackingEntity();
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        DISPENSATION_EXECUTOR.execute(() -> {
            DispensationInfoStore loaded = null;
            try {
                if (activeMedEntity != null) {
                    final List<DispensationInfoEntity> rows = new ArrayList<>();
                    java.util.Collection<DispensationInfoEntity> available =
                            activeMedEntity.getDispensationInfo();
                    if (available != null) {
                        rows.addAll(available);
                    }
                    loaded = new DispensationInfoStore(rows);
                }
            } catch (RuntimeException malformedDispensation) {
                // A broken lazy SQLite collection must not leave a loading
                // dialog on screen or leak clinical details through Logcat.
                LogUtil.w(TAG, "Official dispensation data unavailable");
            }
            final DispensationInfoStore store = loaded;

            mainHandler.post(() -> {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
                if (dispensationProgressDialog == dialog) {
                    dispensationProgressDialog = null;
                }
                if (!isCurrentDispensationLoad(requestedGeneration,
                        dispensationLoadGeneration.get())) {
                    return;
                }
                if (!isAdded() || getView() == null || getFragmentManager() == null) {
                    return;
                }
                // Import and profile selection can change while the background
                // worker reads dispensations. Re-resolve the official record
                // under the *currently active* persisted patient before using
                // any result in this fragment.
                try {
                    Patient nowActive = DB.patients().getActive(context);
                    ActiveMedEntity nowStored = DB.healthcareProviderDB().activeMeds()
                            .findByIdForPatient(
                                    activeMedEntity == null ? null : activeMedEntity.getId(),
                                    nowActive);
                    ActiveMedEntity displayed = activeMed == null
                            ? null : activeMed.getBackingEntity();
                    if (!ProviderDispensationRenderGuard.canApply(
                            activeMedEntity, displayed, nowStored, nowActive)) {
                        closeUnavailableProviderPage();
                        return;
                    }
                } catch (RuntimeException staleOrCorruptProvider) {
                    closeUnavailableProviderPage();
                    return;
                }

                if (store == null) {
                    closeUnavailableProviderPage();
                    return;
                }
                dispensationInfoStore = store;
                if (caldroidFragment instanceof DispensationFragment) {
                    ((DispensationFragment) caldroidFragment).setStore(store);
                }
                FragmentTransaction t = getFragmentManager().beginTransaction();
                t.replace(R.id.dispensation_calendar, caldroidFragment);
                t.commit();
                setupDispensationInfo();
            });
        });
    }
}