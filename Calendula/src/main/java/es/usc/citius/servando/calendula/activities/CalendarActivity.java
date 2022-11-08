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
import android.os.AsyncTask;
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
    private static final DateFormat dtf2 = new SimpleDateFormat("dd/MMM");
    private static DispensationInfoStore dispensationInfoStore;

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

        new UpdatePickupsTask().execute();
        checkIntent();
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
            Drawable d = getResources().getDrawable(AvatarMgr.res(patient.getAvatar()));
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

                String bestStr = best.equals(LocalDate.now().plusDays(1)) ? getString(R.string.calendar_date_tomorrow) : best.first.toString(getString(R.string.best_date_format));

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

        LogUtil.d(TAG, msg.toString());
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
                onDaySelected(d);
                if (bestDay != null && bestDay.first != null && bestDay.first.equals(d)) {
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
                subtitle.setText(date.toString("MMMM YYYY").toUpperCase());
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
        selectedDate = date.toDateTimeAtStartOfDay().toDate();
        return showPickupsInfo(date);
    }

    private boolean showPickupsInfo(final LocalDate date) {
        clearSelectedInterval();
        final List<DispensationInfoEntity> from = dispensationInfoStore.pickupsMap().get(date);
        if (from != null && !from.isEmpty()) {
            if (from.size() == 1) {
                selectInterval(from.get(0));
            }

            TextView title = ((TextView) bottomSheet.findViewById(R.id.bottom_sheet_title));

            LayoutInflater i = getLayoutInflater();
            LinearLayout list = (LinearLayout) findViewById(R.id.pickup_list);
            list.removeAllViews();
            for (final DispensationInfoEntity p : from) {

                Patient pat = p.getActiveMed().getPatient();

                if (pat.getId().equals(patient.getId())) {

                    View v = i.inflate(R.layout.calendar_pickup_list_item, null);
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
            }
            nestedScrollView.scrollBy(0, bottomSheet.getHeight());
            showBottomSheet();
            int total = list.getChildCount();
            title.setText(total + " " + getResources().getString(R.string.title_pickups_bottom_sheet, date.toString(df)));
            appBarLayout.setExpanded(false, true);
            return true;
        }
        return false;
    }

    private void selectInterval(DispensationInfoEntity p) {
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
            caldroidFragment.clearBackgroundDrawableForDates(selectedDates);
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
            return new PickupCalendarAdapter(getActivity(), month, year, getCaldroidData(), extraData, dispensationInfoStore);
        }
    }

    private class UpdatePickupsTask extends AsyncTask<Void, Void, Void> {


        ProgressDialog dialog;

        @Override
        protected Void doInBackground(Void... params) {
            final List<ActiveMedEntity> activeMedEntities = DB.healthcareProviderDB().activeMeds().findBy(ActiveMedEntity.COLUMN_PATIENT, patient);
            final List<DispensationInfoEntity> dispensationInfoEntities = new ArrayList<>();
            for (ActiveMedEntity activeMedEntity : activeMedEntities) {
                dispensationInfoEntities.addAll(activeMedEntity.getDispensationInfo());
            }
            dispensationInfoStore = new DispensationInfoStore(dispensationInfoEntities);
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            LogUtil.d(TAG, "onPreExecute: starting UpdatePickupsTask");
            dialog = new ProgressDialog(CalendarActivity.this);
            dialog.setIndeterminate(true);
            dialog.setMessage(getString(R.string.calendar_updating));
            dialog.show();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            setupNewCalendar();
        }
    }

}
