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

package es.usc.citius.servando.calendula.activities;


import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.util.Pair;
import androidx.core.widget.NestedScrollView;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.roomorama.caldroid.CaldroidFragment;
import com.roomorama.caldroid.CaldroidGridAdapter;
import com.roomorama.caldroid.CaldroidListener;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import es.usc.citius.servando.calendula.CalendulaActivity;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;
import es.usc.citius.servando.calendula.util.AvatarMgr;
import es.usc.citius.servando.calendula.util.DispensationInfoStore;
import es.usc.citius.servando.calendula.util.LogUtil;

public class CalendarActivity extends CalendulaActivity {

    public static final int ACTION_SHOW_REMINDERS = 1;
    private static final String TAG = "CalendarActivity";
    private static final ExecutorService PICKUPS_EXECUTOR = Executors.newSingleThreadExecutor();
    private final DateFormat dtf2 = new SimpleDateFormat("dd/MMM", Locale.getDefault());
    /** Per-window clinical state; never shared between patient calendars. */
    private DispensationInfoStore dispensationInfoStore;

    @BindView(R.id.pickup_list_container)
    View bottomSheet;
    @BindView(R.id.appbar)
    AppBarLayout appBarLayout;
    @BindView(R.id.collapsing_toolbar)
    CollapsingToolbarLayout toolbarLayout;
    @BindView(R.id.routine_name)
    TextView subtitle;
    @BindView(R.id.top_background)
    View topBg;
    @BindView(R.id.nestedScrollView)
    NestedScrollView nestedScrollView;
    @BindView(R.id.collapsed_title_container)
    View titleCollapsedContainer;

    private String df;
    private Patient patient;
    private Date selectedDate = null;
    private CharSequence bestDayText;
    private CaldroidFragment caldroidFragment;
    private Pair<LocalDate, List<DispensationInfoEntity>> bestDay;

    private final Drawable intervalColor = new ColorDrawable(Color.parseColor("#ececec"));
    private ArrayList<Date> selectedDates = new ArrayList<>();

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.calendar, menu);

        IconicsDrawable icon = new IconicsDrawable(this, CommunityMaterial.Icon2.cmd_information_outline)
                .sizeDp(48)
                .paddingDp(6)
                .color(Color.WHITE);

        menu.getItem(0).setIcon(icon);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_best_day:
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onBackPressed() {
        if (bottomSheet.getVisibility() == View.VISIBLE) {
            hideBottomSheet();
        } else {
            super.onBackPressed();
        }
    }

    @OnClick(R.id.close_pickup_list)
    void hideBottomSheet() {
        LinearLayout list = (LinearLayout) findViewById(R.id.pickup_list);
        list.removeAllViews();
        appBarLayout.setExpanded(true, true);
        bottomSheet.setVisibility(View.INVISIBLE);
        clearSelectedInterval();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        ButterKnife.bind(this);

        patient = DB.patients().getActive(this);

        setupStatusBar(Color.TRANSPARENT);
        setupToolbar(getString(R.string.title_activity_pickup_calendar_short), Color.TRANSPARENT, Color.WHITE);
        toolbar.setTitleTextColor(Color.WHITE);

        df = getString(R.string.pickup_date_format);
        bottomSheet.setVisibility(View.INVISIBLE);

        updatePickupsAsync();
        checkIntent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // An already-open calendar may reappear after profile selection in
        // another screen, without the user clicking a day first.
        if (patient != null) {
            currentPickupPatientIsActive();
        }
    }

    @Override
    protected void onDestroy() {
        selectedDate = null;
        super.onDestroy();
    }

    private CharSequence addPickupList(CharSequence msg, List<DispensationInfoEntity> dispensationInfoEntities) {

        Paint textPaint = new Paint();
        //obviously, we have to set textSize into Paint object
        textPaint.setTextSize(getResources().getDimensionPixelOffset(R.dimen.medium_font_size));
        Paint.FontMetricsInt fontMetrics = textPaint.getFontMetricsInt();

        for (DispensationInfoEntity infoEntity : dispensationInfoEntities) {
            Patient patient = dispensationInfoStore.getPatient(infoEntity);
            int color = patient.getColor();
            String str = "       " + infoEntity.getActiveMed().getDefaultDisplay() + " (" + dtf2.format(infoEntity.getDispenseInterval().getStart().toDate()) + " - " + dtf2.format(infoEntity.getDispenseInterval().getEnd().toDate()) + ")\n";
            Spannable text = new SpannableString(str);
            text.setSpan(new ForegroundColorSpan(color), 0, str.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            Drawable d = androidx.core.content.ContextCompat.getDrawable(this, AvatarMgr.res(patient.getAvatar()));
            d.setBounds(0, 0, fontMetrics.bottom, fontMetrics.bottom);
            ImageSpan span = new ImageSpan(d, ImageSpan.ALIGN_BASELINE);
            text.setSpan(span, 0, 5, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            msg = TextUtils.concat(msg, text);
        }
        return msg;
    }

    private void showBottomSheet() {
        bottomSheet.setVisibility(View.VISIBLE);
    }

    /** Compare the recommended date, never the Pair wrapping it. */
    static boolean isTomorrowPickup(LocalDate proposed, LocalDate today) {
        return proposed != null && today != null && proposed.equals(today.plusDays(1));
    }

    /** A recommended date need not appear among interval *start* dates. */
    static boolean isRecommendedPickupDate(LocalDate selected,
            Pair<LocalDate, List<DispensationInfoEntity>> recommendation) {
        return selected != null && recommendation != null
                && recommendation.first != null && selected.equals(recommendation.first)
                && recommendation.second != null && !recommendation.second.isEmpty();
    }

    private CharSequence getBestDayText() {

        final List<DispensationInfoEntity> urgent = dispensationInfoStore.urgentMeds();
        Pair<LocalDate, List<DispensationInfoEntity>> best = dispensationInfoStore.getBestDay();

        final List<DispensationInfoEntity> next = (best == null || best.first == null || best.second == null) ? new ArrayList<DispensationInfoEntity>() : best.second;

        CharSequence msg = new SpannableString(getString(R.string.calendar_no_medicines_to_pick_up));
        LocalDate today = LocalDate.now();

        // there are not urgent meds, but there are others to pickup
        if (urgent.isEmpty() && best != null) {

//            LogUtil.d(TAG, "Urgent: " + urgent.size());
//            LogUtil.d(TAG, "Next: " + next.size());

            LogUtil.d(TAG, "there are not urgent meds, but there are others to pickup");
            if (next.size() > 1) {
                msg = new SpannableString(getString(R.string.best_single_day_message, best.first.toString(getString(R.string.best_date_format)), Integer.toString(next.size())) + "\n\n");
            } else {
                msg = new SpannableString(getString(R.string.best_single_day_message_one_med, best.first.toString(getString(R.string.best_date_format))) + "\n\n");
            }
            msg = addPickupList(msg, next);
        }

        // there are urgent meds
        LogUtil.d(TAG, "there are urgent meds");
        if (!urgent.isEmpty()) {
            // and others
            LogUtil.d(TAG, "and others");
            if (best != null) {

                String bestStr = isTomorrowPickup(best.first, today)
                        ? getString(R.string.calendar_date_tomorrow)
                        : best.first.toString(getString(R.string.best_date_format));

                // and the others date is near
                LogUtil.d(TAG, "and the others date is near");
                if (today.plusDays(3).isAfter(best.first)) {
                    List<DispensationInfoEntity> all = new ArrayList<>();
                    all.addAll(urgent);
                    all.addAll(next);
                    msg = new SpannableString(getString(R.string.best_single_day_message, bestStr, Integer.toString(all.size())) + "\n\n");
                    msg = addPickupList(msg, all);
                }
                // and the others date is not near
                else {

                    LogUtil.d(TAG, "and the others date is not near");
                    msg = addPickupList(new SpannableString(getString(R.string.pending_meds_msg) + "\n\n"), urgent);

                    msg = TextUtils.concat(msg, new SpannableString("\n"));
                    if (next.size() > 1) {
                        LogUtil.d(TAG, " size > 1");
                        msg = TextUtils.concat(msg, getString(R.string.best_single_day_message_after_pending, bestStr, Integer.toString(next.size())) + "\n\n");
                    } else {
                        LogUtil.d(TAG, " size <= 1");
                        msg = TextUtils.concat(msg, getString(R.string.best_single_day_message_after_pending_one_med, bestStr) + "\n\n");
                    }
                    msg = addPickupList(msg, next);
                }
            } else {
                LogUtil.d(TAG, " there are only urgent meds");
                // there are only urgent meds
                msg = addPickupList(getString(R.string.pending_meds_msg) + "\n\n", urgent);
            }
        }

        // msg is the patient-facing medication pickup list; do not log it.
        return msg;
    }

    private void setupNewCalendar() {
        caldroidFragment = new CaldroidSampleCustomFragment();
        Bundle args = new Bundle();
        DateTime now = DateTime.now();
        args.putInt(CaldroidFragment.MONTH, now.getMonthOfYear());
        args.putInt(CaldroidFragment.YEAR, now.getYear());
        args.putBoolean(CaldroidFragment.SHOW_NAVIGATION_ARROWS, true);
        args.putBoolean(CaldroidFragment.SIX_WEEKS_IN_CALENDAR, true);
        args.putInt(CaldroidFragment.START_DAY_OF_WEEK, CaldroidFragment.MONDAY);
        args.putInt(CaldroidFragment.THEME_RESOURCE, R.style.CaldroidScheduleSummary);
        caldroidFragment.setArguments(args);
        FragmentTransaction t = getSupportFragmentManager().beginTransaction();
        t.replace(R.id.calendar, caldroidFragment);
        t.commit();

        final CaldroidListener listener = new CaldroidListener() {

            @Override
            public void onSelectDate(Date date, View view) {
                LocalDate d = LocalDate.fromDateFields(date);
                // Profile selection may change after the async pickup query
                // completed. Never reveal a cached patient summary after it.
                if (!currentPickupPatientIsActive()) {
                    return;
                }
                // A best-day recommendation may fall inside a pickup interval
                // rather than on its first date. The per-day bottom sheet is
                // indexed by interval starts, so an empty sheet must NOT hide
                // the separately validated recommendation.
                onDaySelected(d);
                if (isFinishing() || isDestroyed() || !currentPickupPatientIsActive()) {
                    return;
                }
                if (isRecommendedPickupDate(d, bestDay)
                        && allPickupsBelongToPatient(patient, bestDay.second)) {
                    //Toast.makeText(CalendarActivity.this, "Best day!", Toast.LENGTH_SHORT).show();
                    AlertDialog.Builder builder = new AlertDialog.Builder(CalendarActivity.this);
                    builder.setTitle(R.string.best_date_recommendation_title)
                            .setPositiveButton(getString(R.string.driving_warning_gotit), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    AlertDialog alertDialog = builder.create();
                    alertDialog.setMessage(bestDayText);
                    alertDialog.show();
                }
            }

            @Override
            public void onChangeMonth(int month, int year) {
                DateTime date = DateTime.now().withYear(year).withMonthOfYear(month);
                subtitle.setText(date.toString("MMMM YYYY").toUpperCase(Locale.getDefault()));
            }

            @Override
            public void onLongClickDate(Date date, View view) {

            }

            @Override
            public void onCaldroidViewCreated() {
                super.onCaldroidViewCreated();
                subtitle.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (caldroidFragment.getView() != null) {
                            caldroidFragment.getView().findViewById(R.id.calendar_title_view).setVisibility(View.GONE);
                            caldroidFragment.getMonthTitleTextView().setVisibility(View.GONE);
                        }
                    }
                }, 50);

            }

        };

        this.bestDay = dispensationInfoStore.getBestDay();
        this.bestDayText = getBestDayText();

        if (this.bestDay != null && this.bestDay.first != null) {
            //Toast.makeText(CalendarActivity.this, "Best day: " + bestDay.first.toString("dd/MM/YY"), Toast.LENGTH_SHORT).show();
            caldroidFragment.setBackgroundDrawableForDate(getSelectedDrawable(R.color.android_green), this.bestDay.first.toDate());
            caldroidFragment.setTextColorForDate(R.color.white, this.bestDay.first.toDate());
        }
        caldroidFragment.refreshView();
        caldroidFragment.setCaldroidListener(listener);
    }

    private void checkIntent() {

        int action = getIntent().getIntExtra(CalendulaApp.INTENT_EXTRA_ACTION, -1);
        if (action == ACTION_SHOW_REMINDERS) {
            //onBestDaySelected();
        }
    }

    private boolean onDaySelected(LocalDate date) {
        if (!currentPickupPatientIsActive()) {
            return false;
        }
        selectedDate = date.toDateTimeAtStartOfDay().toDate();
        return showPickupsInfo(date);
    }

    private boolean currentPickupPatientIsActive() {
        try {
            if (samePersistedPatient(patient, DB.patients().getActive(this))) {
                return true;
            }
        } catch (RuntimeException unavailable) {
            // Do not display stale clinical information if the owner cannot
            // be resolved at interaction time.
            LogUtil.w(TAG, "Pickup calendar owner cannot be verified");
        }
        bottomSheet.setVisibility(View.INVISIBLE);
        finish();
        return false;
    }

    private boolean showPickupsInfo(final LocalDate date) {
        clearSelectedInterval();
        final List<DispensationInfoEntity> from = dispensationInfoStore.pickupsMap().get(date);
        if (from != null && !from.isEmpty()) {
            // A corrupt or re-linked row must block the entire date, not
            // show part of another patient's treatment or highlight it.
            if (!allPickupsBelongToPatient(patient, from)) {
                bottomSheet.setVisibility(View.INVISIBLE);
                finish();
                return false;
            }
            if (from.size() == 1) {
                selectInterval(from.get(0));
            }

            TextView title = ((TextView) bottomSheet.findViewById(R.id.bottom_sheet_title));

            LayoutInflater i = getLayoutInflater();
            LinearLayout list = (LinearLayout) findViewById(R.id.pickup_list);
            list.removeAllViews();
            for (final DispensationInfoEntity p : from) {
                Patient pat = p.getActiveMed().getPatient();
                View v = i.inflate(R.layout.calendar_pickup_list_item, list, false);
                    TextView tv1 = ((TextView) v.findViewById(R.id.textView));
                    TextView tv2 = ((TextView) v.findViewById(R.id.textView2));
                    ImageView avatar = ((ImageView) v.findViewById(R.id.avatar));
                    String interval = getResources().getString(R.string.pickup_interval, p.getDispenseInterval().getEnd().toString(df));

                    if (p.isDispensed()) {
                        interval += " ✔";
                        tv1.setAlpha(0.5f);
                    } else {
                        tv1.setAlpha(1f);
                        tv2.setAlpha(1f);
                    }

                    tv1.setText(p.getActiveMed().getDefaultDisplay());
                    tv2.setText(interval);
                    avatar.setImageResource(AvatarMgr.res(pat.getAvatar()));

                    tv1.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            selectInterval(p);
                        }
                    });
                    list.addView(v);
            }
            nestedScrollView.scrollBy(0, bottomSheet.getHeight());
            showBottomSheet();
            int total = list.getChildCount();
            title.setText(getString(R.string.count_with_text, total, getResources().getString(R.string.title_pickups_bottom_sheet, date.toString(df))));
            appBarLayout.setExpanded(false, true);
            return true;
        }
        // An empty selection must not leave the previously selected day's
        // medication list visible beneath the new calendar date.
        hideBottomSheet();
        return false;
    }

    private void selectInterval(DispensationInfoEntity p) {
        if (!currentPickupPatientIsActive() || p == null
                || !isOwnedPickup(patient, p.getActiveMed(), p)) {
            return;
        }
        clearSelectedInterval();
        Interval interval = p.getDispenseInterval();
        for (DateTime date = interval.getStart(); date.isBefore(interval.getEnd().plusDays(1)); date = date.plusDays(1)) {
            Date d = date.toDate();
            caldroidFragment.setBackgroundDrawableForDate(intervalColor, d);
            selectedDates.add(d);
        }
        caldroidFragment.refreshView();
    }

    private void clearSelectedInterval() {
        if (!selectedDates.isEmpty()) {
            caldroidFragment.clearBackgroundDrawableForDates(new ArrayList<>(selectedDates));
            selectedDates.clear();
            caldroidFragment.refreshView();
        }
    }

    private Drawable getSelectedDrawable(int color) {
        return new IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_comment)
                .colorRes(color)
                .paddingDp(8)
                .sizeDp(20);
    }

    public static class CaldroidSampleCustomFragment extends CaldroidFragment {
        @Override
        public CaldroidGridAdapter getNewDatesGridAdapter(int month, int year) {
            CalendarActivity host = getActivity() instanceof CalendarActivity
                    ? (CalendarActivity) getActivity() : null;
            // A detached/restored fragment must not inherit data from another
            // patient's CalendarActivity. The empty model renders no pickups.
            DispensationInfoStore ownStore = host != null && host.dispensationInfoStore != null
                    ? host.dispensationInfoStore
                    : new DispensationInfoStore(new ArrayList<>());
            return new PickupCalendarAdapter(getActivity(), month, year,
                    getCaldroidData(), extraData, ownStore);
        }
    }

    /** Reject the result of a patient-specific background query after switching profiles. */
    static boolean samePersistedPatient(Patient expected, Patient active) {
        return expected != null && active != null && expected.getId() != null
                && expected.getId() > 0L && expected.getId().equals(active.getId());
    }

    /** A dispensation row must belong to the exact patient-owned provider medicine. */
    static boolean isOwnedPickup(Patient selected, ActiveMedEntity provider,
                                 DispensationInfoEntity pickup) {
        if (selected == null || provider == null || provider.getId() == null
                || provider.getId() <= 0L || !samePersistedPatient(selected, provider.getPatient())
                || pickup == null || pickup.getDispenseInterval() == null
                || pickup.getActiveMed() == null) {
            return false;
        }
        ActiveMedEntity linked = pickup.getActiveMed();
        return provider.getId().equals(linked.getId())
                && samePersistedPatient(selected, linked.getPatient());
    }

    /** Reject the entire day's selection rather than filtering an unsafe row. */
    static boolean allPickupsBelongToPatient(
            Patient expected, List<DispensationInfoEntity> candidates) {
        if (candidates == null) {
            return false;
        }
        for (DispensationInfoEntity row : candidates) {
            if (row == null || !isOwnedPickup(expected, row.getActiveMed(), row)) {
                return false;
            }
        }
        return true;
    }

    private void updatePickupsAsync() {
        LogUtil.d(TAG, "updatePickupsAsync: starting");
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setIndeterminate(true);
        dialog.setMessage(getString(R.string.calendar_updating));
        dialog.show();

        final Patient patientForQuery = patient;
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        PICKUPS_EXECUTOR.execute(() -> {
            DispensationInfoStore fetched = null;
            try {
                if (patientForQuery != null && patientForQuery.getId() != null
                        && patientForQuery.getId() > 0L) {
                    final List<ActiveMedEntity> activeMeds = DB.healthcareProviderDB()
                            .activeMeds().findBy(ActiveMedEntity.COLUMN_PATIENT, patientForQuery);
                    final List<DispensationInfoEntity> rows = new ArrayList<>();
                    for (ActiveMedEntity med : activeMeds) {
                        if (med == null || med.getPatient() == null
                                || !samePersistedPatient(patientForQuery, med.getPatient())) {
                            throw new IllegalStateException("Provider pickup owner mismatch");
                        }
                        java.util.Collection<DispensationInfoEntity> dispensed =
                                med.getDispensationInfo();
                        if (dispensed != null) {
                            for (DispensationInfoEntity entry : dispensed) {
                                if (!isOwnedPickup(patientForQuery, med, entry)) {
                                    throw new IllegalStateException("Provider pickup link is invalid");
                                }
                                rows.add(entry);
                            }
                        }
                    }
                    fetched = new DispensationInfoStore(rows);
                }
            } catch (RuntimeException invalidProviderData) {
                LogUtil.w(TAG, "Patient pickup calendar could not be verified");
            }
            final DispensationInfoStore store = fetched;

            mainHandler.post(() -> {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Patient nowActive = DB.patients().getActive(CalendarActivity.this);
                if (store == null || !samePersistedPatient(patientForQuery, nowActive)) {
                    // Do not present data returned for another login/profile.
                    finish();
                    return;
                }
                dispensationInfoStore = store;
                setupNewCalendar();
            });
        });
    }

}
