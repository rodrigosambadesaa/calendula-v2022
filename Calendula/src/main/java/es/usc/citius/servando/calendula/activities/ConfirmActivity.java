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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.IIcon;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.CalendulaActivity;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.HomePagerActivity;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.schedules.ScheduleBuildActivity;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.scheduling.Agenda;
import es.usc.citius.servando.calendula.scheduling.ScheduleDisplayUtils;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntryEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.RepeatType;
import es.usc.citius.servando.calendula.util.AvatarMgr;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.ScreenUtils;
import es.usc.citius.servando.calendula.util.HealthcareProviderTypeface;
import es.usc.citius.servando.calendula.util.Snack;
import es.usc.citius.servando.calendula.util.view.ArcTranslateAnimation;

public class ConfirmActivity extends CalendulaActivity {

    private static final int DEFAULT_CHECK_MARGIN = 3;
    private static final String TAG = "ConfirmActivity";

    @BindView(R.id.appbar)
    protected AppBarLayout appBarLayout;
    @BindView(R.id.collapsing_toolbar)
    protected CollapsingToolbarLayout toolbarLayout;
    @BindView(R.id.myFAB)
    protected FloatingActionButton fab;
    @BindView(R.id.patient_avatar)
    protected ImageView avatar;
    @BindView(R.id.patient_avatar_title)
    protected ImageView avatarTitle;
    @BindView(R.id.check_all_image)
    protected ImageView checkAllImage;
    @BindView(R.id.listView)
    protected RecyclerView listView;
    @BindView(R.id.user_friendly_time)
    protected TextView friendlyTime;
    @BindView(R.id.routines_list_item_hour)
    protected TextView hour;
    @BindView(R.id.routines_list_item_minute)
    protected TextView minute;
    @BindView(R.id.textView3)
    protected TextView takeMedsMessage;
    @BindView(R.id.routine_name)
    protected TextView title;
    @BindView(R.id.routine_name_title)
    protected TextView titleTitle;
    @BindView(R.id.check_overlay)
    protected View checkAllOverlay;

    private boolean fromNotification = false;
    private boolean isDistant;
    private boolean isInWindow;
    private boolean isToday;
    private boolean stateChanged = false;
    private ConfirmItemAdapter itemAdapter;
    private DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.dateTimeNoMillis();
    private DateTimeFormatter timeFormatter = DateTimeFormat.forPattern("HH:mm");
    private IconicsDrawable checkedIcon;
    private IconicsDrawable uncheckedIcon;
    private int color;
    private int position = -1;
    private List<EventInstance> items = new ArrayList<>();
    private Patient patient;
    private String action;
    private String relativeTime = "";
    private DateTime dateTime;

    /*
     * Returns the intake margin interval
     */
    public Pair<DateTime, DateTime> getCheckMarginInterval(DateTime intakeTime) {

        String checkMarginStr = PreferenceUtils.getString(PreferenceKeys.CONFIRM_CHECK_WINDOW_MARGIN, String.valueOf(DEFAULT_CHECK_MARGIN));

        int checkMargin = Integer.parseInt(checkMarginStr);

        DateTime start = intakeTime.minusMinutes(30);
        DateTime end = intakeTime.plusHours(checkMargin);
        return new Pair<>(start, end);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.confirm, menu);

        MenuItem item = menu.findItem(R.id.action_delay);

        if (!isInWindow) {
            item.setVisible(false);
        } else {
            item.setIcon(new IconicsDrawable(this)
                    .icon(CommunityMaterial.Icon2.cmd_history)
                    .color(Color.WHITE)
                    .sizeDp(24));
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                if (fromNotification) {
                    startActivity(new Intent(this, StartActivity.class));
                    finish();
                } else {
                    supportFinishAfterTransition();
                }
                return true;
            case R.id.action_delay:
                showDelayDialog();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showDelayDialog() {
        final int[] values = this.getResources().getIntArray(R.array.delays_array_values);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.notification_delay)
                .setItems(R.array.delays_array, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        int minutes = values[which];
                        delayIntake(minutes);
                        String msg = ConfirmActivity.this.getString(R.string.alarm_delayed_message, String.valueOf(minutes));
                        Toast.makeText(ConfirmActivity.this, msg, Toast.LENGTH_SHORT).show();
                        supportFinishAfterTransition();
                    }
                });
        builder.create().show();
    }

    public void showEnsureConfirmDialog(final DialogInterface.OnClickListener listener, boolean uncheck) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        DateTime t = dateTime;

        String title = t.isAfterNow() ? getString(R.string.intake_not_available) :
                getString(R.string.meds_from) + " " + dateTime.toString("EEEE dd") + " " + getString(R.string.at_time_connector) + " " + dateTime.toString(timeFormatter);

        String msg = t.isAfterNow() ? getString(R.string.confirm_future_intake_warning, relativeTime)
                : uncheck ? getString(R.string.unconfirm_past_intake_warning, relativeTime)
                : getString(R.string.confirm_past_intake_warning);


        builder.setMessage(msg)
                .setCancelable(true)
                .setIcon(IconUtils.icon(this, CommunityMaterial.Icon2.cmd_history, R.color.black, 36))
                .setTitle(title);

        if (t.isAfterNow()) {
            builder.setNegativeButton(getString(R.string.tutorial_understood), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });
        } else {
            builder.setPositiveButton(uncheck ? getString(R.string.meds_unconfirm_ok) : getString(R.string.meds_confirm_ok), listener)
                    .setNegativeButton(uncheck ? getString(R.string.meds_unconfirm_cancel) : getString(R.string.meds_confirm_cancel), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
        }

        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void onBackPressed() {
        if (fromNotification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask();
                startActivity(new Intent(this, StartActivity.class));
            } else {
                finish();
            }
        } else {
            super.onBackPressed();
        }
    }

    void onClickFab() {
        boolean somethingChecked = false;
        for (EventInstance item : items) {
            if (!item.completed()) {
                ScheduleUtils.instance().setIntakeCompleted(this, item, true);
                somethingChecked = true;
            }
        }

        if (somethingChecked) {
            itemAdapter.notifyDataSetChanged();
            stateChanged = true;
            fab.postDelayed(new Runnable() {
                @Override
                public void run() {
                    animateAllChecked();
                }
            }, 100);
        } else {
            supportFinishAfterTransition();
        }
    }

    void moveArrowsDown(int duration) {
        checkAllImage.animate()
                .translationY(ScreenUtils.dpToPx(getResources(), 150f))
                .setDuration(duration)
                .setInterpolator(new OvershootInterpolator())
                .start();
    }

    void showRippleByApi(int x, int y) {

        int duration = 500;
        int arrowDuration = 400;

        checkAllOverlay.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    finishAndRemoveTask();
                } else {
                    finish();
                }
            }
        }, duration + 300);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            showRipple(x, y, duration);
        } else {
            checkAllOverlay.setVisibility(View.VISIBLE);
            checkAllOverlay.animate().alpha(1).setDuration(duration).start();
        }
        moveArrowsDown(arrowDuration);
    }

    SpannableString getIntakeTitle(String title) {
        Drawable drawable = new IconicsDrawable(ConfirmActivity.this)
                .icon(HealthcareProviderTypeface.Icon.hcp_healthcare_provider)
                .backgroundColorRes(R.color.white)
                .colorRes(R.color.healthcare_provider_dark)
                .paddingDp(0)
                .sizeDp(25);

        drawable.mutate();
        ImageSpan imageSpan = new ImageSpan(drawable);
        SpannableString spannableString = new SpannableString("   " + title);
        spannableString.setSpan(imageSpan, 0, 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return spannableString;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        processIntent();
        setContentView(R.layout.activity_confirm);
        ButterKnife.bind(this);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        isToday = LocalDate.now().equals(dateTime.toLocalDate());
        isInWindow = Agenda.instance().isInIntakeWindow(dateTime);

        DateTime now = DateTime.now();
        Pair<DateTime, DateTime> interval = getCheckMarginInterval(dateTime);
        isDistant = !new Interval(interval.first, interval.second).contains(now);
        color = AvatarMgr.colorsFor(getResources(), patient.getAvatar())[0];
        color = Color.parseColor("#263238");

        setupStatusBar(Color.TRANSPARENT);
        setupToolbar("", Color.TRANSPARENT, Color.WHITE);
        toolbar.setTitleTextColor(Color.WHITE);

        avatar.setImageResource(AvatarMgr.res(patient.getAvatar()));
        avatarTitle.setImageResource(AvatarMgr.res(patient.getAvatar()));
        titleTitle.setText(patient.getName());
        title.setText(ScheduleUtils.instance().getIntakeTitle(patient, dateTime.toLocalTime(), this));
        takeMedsMessage.setText(isInWindow ? getString(R.string.agenda_zoom_meds_time) : getString(R.string.meds_from) + " " + dateTime.toString("EEEE dd"));


        relativeTime = DateUtils.getRelativeTimeSpanString(dateTime.getMillis(), now.getMillis(), 5 * DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_ALL).toString();

        hour.setText(dateTime.toString("kk:"));
        minute.setText(dateTime.toString("mm"));
        friendlyTime.setText(relativeTime.substring(0, 1).toUpperCase() + relativeTime.substring(1));

        if (isDistant) {
            fab.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.android_orange_dark)));
        }

        fab.setImageDrawable(new IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_check_all)
                .color(Color.WHITE)
                .sizeDp(24)
                .paddingDp(0));


        checkAllImage.setImageDrawable(new IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_check_all)
                .color(Color.WHITE)
                .sizeDp(100)
                .paddingDp(0));

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                boolean somethingToCheck = false;
                for (EventInstance item : items) {
                    if (!item.completed()) {
                        somethingToCheck = true;
                        break;
                    }
                }
                if (somethingToCheck) {
                    if (isDistant) {
                        showEnsureConfirmDialog(new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                onClickFab();
                            }
                        }, false);
                    } else {
                        onClickFab();
                    }
                } else {
                    Snack.show(getResources().getString(R.string.all_meds_taken), ConfirmActivity.this);
                }
            }
        });


        toolbarLayout.setContentScrimColor(patient.getColor());
        setupListView();

        if ("delay".equals(action)) {
            showDelayDialog();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // finish and restart with the new params
        finish();
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Toast.makeText(this, "Is distant: " + isDistant + " (" + date.toDateTime(time).toString("dd/MM/YYYY, kk:mm")+")", Toast.LENGTH_LONG).show();
    }

    protected void onDailyAgendaItemCheck(final ImageButton v) {
        int total = items.size();
        int checked = 0;

        for (EventInstance i : items) {
            if (i.completed())
                checked++;
        }
    }

    @Override
    protected void onDestroy() {

        if (stateChanged) {
            CalendulaApp.eventBus().post(new ConfirmStateChangeEvent(position));
        }
        super.onDestroy();
    }

    private void delayIntake(int minutes) {
        ScheduleUtils.instance().delayIntake(ConfirmActivity.this, patient, dateTime, minutes);
    }

    private void animateAllChecked() {

        int width = appBarLayout.getWidth();
        int middle = width / 2;
        int fabCentered = middle - fab.getWidth() / 2;
        int translationX = (int) fab.getX() - fabCentered;
        int translationY = ScreenUtils.dpToPx(getResources(), 150);

        final int rippleX = middle;
        final int rippleY = (int) (fab.getY() + fab.getHeight() / 2) - translationY;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            Animation arcAnimation = new ArcTranslateAnimation(0, -translationX, 0, -translationY);
            arcAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    showRippleByApi(rippleX, rippleY);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });

            arcAnimation.setInterpolator(new DecelerateInterpolator());
            arcAnimation.setDuration(200);
            arcAnimation.setFillAfter(true);
            fab.startAnimation(arcAnimation);

        } else {
            ViewPropertyAnimator animator = fab.animate().translationX(-translationX).setDuration(300);
            animator.setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    showRippleByApi(rippleX, rippleY);
                }
            });
            animator.start();
        }
    }

    private void showRipple(int x, int y, int duration) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            LogUtil.d(TAG, "Ripple x,y [" + x + ", " + y + "]");
            checkAllOverlay.setVisibility(View.INVISIBLE);
            // get the final radius for the clipping circle
            int finalRadius = (int) Math.hypot(checkAllOverlay.getWidth(), checkAllOverlay.getHeight());
            // create the animator for this view (the start radius is zero)
            Animator anim = ViewAnimationUtils.createCircularReveal(checkAllOverlay, x, y, fab.getWidth() / 2, finalRadius);
            anim.setInterpolator(new DecelerateInterpolator());
            // make the view visible and start the animation
            checkAllOverlay.setVisibility(View.VISIBLE);
            anim.setDuration(duration).start();
        }
    }

    private void setupListView() {

        loadItems();
        itemAdapter = new ConfirmItemAdapter();
        LinearLayoutManager llm = new LinearLayoutManager(this);
        listView.setLayoutManager(llm);
        listView.setAdapter(itemAdapter);
        listView.setItemAnimator(new DefaultItemAnimator());
    }

    private void processIntent() {
        Intent i = getIntent();

        Long patientId = i.getLongExtra(IntentParams.EXTRA_PATIENT_ID, -1);
        String dateTimeStr = i.getStringExtra(IntentParams.EXTRA_DATETIME);
        action = i.getStringExtra(IntentParams.EXTRA_ACTION);
        position = i.getIntExtra(IntentParams.EXTRA_POSITION, -1);
        fromNotification = position == -1;
        patient = DB.patients().findById(patientId);

        if (dateTimeStr != null) {
            dateTime = DateTime.parse(dateTimeStr, dateTimeFormatter);
        } else {
            // this should never happen, but, just in case, redirect to home and show error
            Intent intent = new Intent(this, HomePagerActivity.class);
            intent.putExtra("invalid_notification_error", true);
            startActivity(intent);
            finish();
        }
    }

    private void loadItems() {
        items.addAll(ScheduleUtils.instance().intakeEvents(patient, dateTime));
    }

    private Drawable getCheckedIcon(int color) {
        if (checkedIcon == null) {
            checkedIcon = new IconicsDrawable(this, CommunityMaterial.Icon.cmd_checkbox_marked_circle_outline) //cmd_checkbox_marked_outline
                    .sizeDp(30)
                    .paddingDp(0)
                    .color(color);
        }
        return checkedIcon;
    }

    private Drawable getUncheckedIcon(int color) {
        if (uncheckedIcon == null) {
            uncheckedIcon = new IconicsDrawable(this, CommunityMaterial.Icon.cmd_checkbox_blank_circle_outline) //cmd_checkbox_blank_outline
                    .sizeDp(30)
                    .paddingDp(0)
                    .color(color);
        }
        return uncheckedIcon;
    }

    @NonNull
    private String getOffsetString(EventInstance.EventOffset offset, Routine r) {

        boolean isTime = r.getName() == null;
        String value = isTime ? r.getTime().toString(timeFormatter) : r.getName();
        String prefix;
        switch (offset) {
            case BEFORE:
                prefix = getString(R.string.before_generic);
                break;
            case AFTER:
                prefix = getString(R.string.after_generic);
                break;
            default:
                prefix = getString(isTime ? R.string.at_generic : R.string.with_generic);
                break;
        }
        return prefix + " " + value;
    }

    private RepeatType getMealWithOffset(RepeatType rt, EventInstance.EventOffset offset) {

        if (EventInstance.EventOffset.NONE.equals(offset)) {
            return rt;
        }
        switch (rt) {
            case CD:
                return EventInstance.EventOffset.AFTER.equals(offset) ? RepeatType.PCD : RepeatType.ACD;
            case CM:
                return EventInstance.EventOffset.AFTER.equals(offset) ? RepeatType.PCM : RepeatType.ACM;
            case CV:
                return EventInstance.EventOffset.AFTER.equals(offset) ? RepeatType.PCV : RepeatType.ACV;
            default:
                return rt;
        }
    }

    private SpannableString getInfoMessage(String msg, IIcon icon) {
        Drawable drawable = new IconicsDrawable(ConfirmActivity.this)
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

    public static class ConfirmStateChangeEvent {
        public int position = -1;

        public ConfirmStateChangeEvent(int position) {
            this.position = position;
        }
    }

    class ConfirmItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {


        private ConfirmItemViewHolder h;
        private EventInstance eventInstance;
        private Schedule s;
        private Medicine m;
        private Presentation p;

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.confirm_activity_list_item, parent, false);
            return new ConfirmItemViewHolder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {

            h = (ConfirmItemViewHolder) holder;
            eventInstance = items.get(position);
            s = DB.schedules().findById(eventInstance.getRef());
            m = s.getMedicine();
            p = m.getPresentation();

            Double dose = eventInstance.getDoubleParam(EventInstance.PARAM_DOSE);
            String status = getString(R.string.med_not_taken);
            if (eventInstance.completedAt() != null) {
                status = (eventInstance.completed() ? getString(R.string.med_taken_at) : getString(R.string.med_cancelled_at)) + " " + eventInstance.completedAt().toString("HH:mm") + "h";
            }

            h.info.setVisibility(View.GONE);
            if (s.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)) {
                ActiveMedEntity am = DB.healthcareProviderDB().activeMeds().findById(s.getActiveMedId());
                Collection<DosageEntryEntity> entries = am.getDosage().getEntries();
                if (entries.size() >= 1) {
                    String instr = entries.iterator().next().getPatientInstruction();
                    if (!StringUtils.isBlank(instr)) {
                        h.info.setVisibility(View.VISIBLE);
                        h.info.setText(getInfoMessage(getString(R.string.instructions_prefix) + " " + instr, CommunityMaterial.Icon2.cmd_medical_bag));
                    }
                }
            }

            h.offset.setVisibility(View.GONE);
            if (eventInstance.getOffset() != null) {
                Routine routine = DB.routines().findByPatientAndTime(patient, dateTime.toLocalTime());
                if (routine != null && routine.dailyEventType() != null) {
                    String offsetStr;
                    if (routine.dailyEventType().isMeal()) {
                        RepeatType repeatType = getMealWithOffset(routine.dailyEventType(), eventInstance.getOffset());
                        offsetStr = repeatType.getMealContextString(ConfirmActivity.this);
                    } else {
                        offsetStr = getOffsetString(eventInstance.getOffset(), routine);
                    }
                    h.offset.setVisibility(View.VISIBLE);
                    h.offset.setText(getInfoMessage(StringUtils.capitalize(offsetStr), CommunityMaterial.Icon.cmd_alarm));
                }
            }

            if (s.hasState(Schedule.ScheduleState.DIFFERS_FROM_OFFICIAL)) {
                h.alert.setVisibility(View.VISIBLE);
                Drawable drawable = new IconicsDrawable(ConfirmActivity.this)
                        .icon(HealthcareProviderTypeface.Icon.hcp_healthcare_provider)
                        .backgroundColorRes(R.color.android_orange)
                        .roundedCornersDp(2)
                        .colorRes(R.color.white)
                        .paddingDp(3)
                        .sizeDp(16);

                drawable.mutate();
                ImageSpan imageSpan = new ImageSpan(drawable);
                SpannableString spannableString = new SpannableString(h.alert.getText());
                spannableString.setSpan(imageSpan, 0, 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                h.alert.setText(spannableString);
                h.alert.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(ConfirmActivity.this, ScheduleBuildActivity.class);
                        i.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID, s.getId());
                        startActivity(i);
                    }
                });
            } else {
                h.alert.setVisibility(View.GONE);
                h.alert.setOnClickListener(null);
            }

            h.med.setText(s.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL) ? getIntakeTitle(m.getName()) : m.getName());
            h.dose.setText(ScheduleDisplayUtils.displayDose(dose, m.getPresentation(), getResources()));
            h.status.setText(status);
            h.event = eventInstance;
            LogUtil.d(TAG, eventInstance.toString());
            updateCheckedStatus();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        void updateCheckedStatus() {
            Drawable medDrawable = new IconicsDrawable(ConfirmActivity.this)
                    .icon(p.icon())
                    .color(eventInstance.completed() ? Color.parseColor("#81c784") : Color.parseColor("#11000000"))
                    .sizeDp(36)
                    .paddingDp(0);

            Drawable checkDrawable = eventInstance.completed() ?
                    getCheckedIcon(Color.parseColor("#81c784"))
                    : getUncheckedIcon(Color.parseColor("#11000000"));

            h.check.setImageDrawable(checkDrawable);
            h.icon.setImageDrawable(medDrawable);
        }

        class ConfirmItemViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

            @BindView(R.id.med_item_name)
            TextView med;
            @BindView(R.id.med_item_dose)
            TextView dose;
            @BindView(R.id.med_item_status)
            TextView status;
            @BindView(R.id.med_item_info)
            TextView info;
            @BindView(R.id.med_item_offset)
            TextView offset;
            @BindView(R.id.check_button)
            ImageButton check;
            @BindView(R.id.imageView)
            ImageView icon;
            @BindView(R.id.med_item_alert)
            TextView alert;

            EventInstance event;

            public ConfirmItemViewHolder(View itemView) {
                super(itemView);
                ButterKnife.bind(this, itemView);
                itemView.setOnClickListener(this);
                check.setOnClickListener(this);
            }

            @Override
            public void onClick(View view) {
                final boolean taken = event.completed();
                if (isDistant) {
                    showEnsureConfirmDialog(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ScheduleUtils.instance().setIntakeCompleted(ConfirmActivity.this, event, !taken);
                            stateChanged = true;
                            onDailyAgendaItemCheck(check);
                            notifyItemChanged(getAdapterPosition());
                        }
                    }, taken);
                } else {
                    ScheduleUtils.instance().setIntakeCompleted(ConfirmActivity.this, event, !taken);
                    stateChanged = true;
                    onDailyAgendaItemCheck(check);
                    notifyItemChanged(getAdapterPosition());
                }
            }
        }
    }


}
