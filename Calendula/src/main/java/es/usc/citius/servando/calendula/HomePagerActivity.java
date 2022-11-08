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

package es.usc.citius.servando.calendula;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.ColorInt;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.viewpager.widget.ViewPager;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import org.greenrobot.eventbus.Subscribe;
import org.joda.time.DateTime;

import java.util.LinkedList;
import java.util.Queue;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.activities.CalendarActivity;
import es.usc.citius.servando.calendula.activities.CheckBatterySavingActivity;
import es.usc.citius.servando.calendula.activities.ConfirmActivity;
import es.usc.citius.servando.calendula.activities.LeftDrawerMgr;
import es.usc.citius.servando.calendula.activities.MaterialIntroActivity;
import es.usc.citius.servando.calendula.activities.MedicineInfoActivity;
import es.usc.citius.servando.calendula.activities.RoutinesActivity;
import es.usc.citius.servando.calendula.activities.StartActivity;
import es.usc.citius.servando.calendula.activities.schedules.ScheduleBuildActivity;
import es.usc.citius.servando.calendula.adapters.HomePageAdapter;
import es.usc.citius.servando.calendula.adapters.HomePages;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.events.ActiveMedUpdateEvent;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.events.StockRunningOutEvent;
import es.usc.citius.servando.calendula.fragments.ActiveMedicationListFragment;
import es.usc.citius.servando.calendula.fragments.DailyAgendaFragment;
import es.usc.citius.servando.calendula.fragments.HomeProfileMgr;
import es.usc.citius.servando.calendula.fragments.MedicinesListFragment;
import es.usc.citius.servando.calendula.fragments.RoutinesListFragment;
import es.usc.citius.servando.calendula.fragments.ScheduleListFragment;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.Agenda;
import es.usc.citius.servando.calendula.settings.CalendulaSettingsActivity;
import es.usc.citius.servando.calendula.util.FragmentUtils;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.HealthcareProviderTypeface;
import es.usc.citius.servando.calendula.util.stock.StockDisplayUtils;

import static es.usc.citius.servando.calendula.events.PersistenceEvents.SCHEDULE_EVENT;

public class HomePagerActivity extends CalendulaActivity implements
        RoutinesListFragment.OnRoutineSelectedListener,
        MedicinesListFragment.OnMedicineSelectedListener,
        ScheduleListFragment.OnScheduleSelectedListener {

    public static final int REQ_CODE_EXTERNAL_STORAGE = 10;
    private static final String TAG = "HomePagerActivity";

    @MenuRes
    private static final int[] MENU_ITEMS = {
            R.id.action_sort, R.id.action_expand, R.id.action_last_update, R.id.action_calendar, R.id.action_schedules_help
    };


    @BindView(R.id.appbar)
    public AppBarLayout appBarLayout;
    @BindView(R.id.collapsing_toolbar)
    CollapsingToolbarLayout toolbarLayout;
    @BindView(R.id.add_button)
    FloatingActionButton fab;
    @BindView(R.id.user_info_fragment)
    View userInfoFragment;
    @BindView(R.id.main_content)
    CoordinatorLayout coordinatorLayout;
    @BindView(R.id.toolbar_title)
    TextView toolbarTitle;
    @BindView(R.id.container)
    ViewPager mViewPager;
    @BindView(R.id.sliding_tabs)
    TabLayout tabLayout;

    private boolean appBarLayoutExpanded = true;
    private boolean active = false;
    private boolean appBarDraggable = true;
    private Drawable icAgendaMore;
    private Drawable icAgendaLess;
    private FabMenuMgr fabMgr;
    private HomeProfileMgr homeProfileMgr;
    private HomePageAdapter mSectionsPagerAdapter;
    private LeftDrawerMgr drawerMgr;
    private Patient activePatient;
    private int pendingRefresh = -2;
    private Queue<Object> pendingEvents = new LinkedList<>();
    private Handler handler;
    private Snackbar snackbar;
    private ActiveMedUpdateEvent.Status lastStatus = null;

    private SparseArray<MenuItem> menuItems;

    @ColorInt
    private int previousColor = -1;

    public void showPagerItem(int position) {
        showPagerItem(position, true);
    }

    public void showPagerItem(int position, boolean updateDrawer) {
        if (position >= 0 && position < mViewPager.getChildCount()) {
            mViewPager.setCurrentItem(position);
            if (updateDrawer) {
                drawerMgr.onPagerPositionChange(position);
            }
        }
    }

    public int getPagerPosition() {
        return mViewPager.getCurrentItem();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.home, menu);
        menuItems = new SparseArray<>(menu.size());
        for (int menuItem : MENU_ITEMS) {
            menuItems.put(menuItem, menu.findItem(menuItem));
        }
        menuItems.get(R.id.action_sort).setIcon(IconUtils.icon(getApplicationContext(), CommunityMaterial.Icon2.cmd_sort, R.color.white));
        menuItems.get(R.id.action_last_update).setIcon(IconUtils.icon(getApplicationContext(), CommunityMaterial.Icon2.cmd_refresh, R.color.white).actionBar());
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        final int pageNum = mViewPager.getCurrentItem();
        final HomePages page = HomePages.getPage(pageNum);

        // hide all items first
        for (int menuItem : MENU_ITEMS) {
            menuItems.get(menuItem).setVisible(false);
        }

        // show items relevant to the current page
        switch (page) {
            case HOME:
                final boolean expanded = ((DailyAgendaFragment) getViewPagerFragment(HomePages.HOME)).isExpanded();
                menuItems.get(R.id.action_expand).setVisible(true);
                menuItems.get(R.id.action_expand).setIcon(!expanded ? icAgendaMore : icAgendaLess);
                break;
            case ACTIVE_MEDICATION:
                menuItems.get(R.id.action_last_update).setVisible(true);
                break;
            case MEDICINES:
                menuItems.get(R.id.action_sort).setVisible(true);
                break;
            case SCHEDULES:
                break;
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_calendar:
                startActivity(new Intent(this, CalendarActivity.class));
                return true;
            case R.id.action_expand:

                final boolean expanded = ((DailyAgendaFragment) getViewPagerFragment(HomePages.HOME)).isExpanded();
                appBarLayout.setExpanded(expanded);


                boolean delay = appBarLayoutExpanded && !expanded || !appBarLayoutExpanded && expanded;

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ((DailyAgendaFragment) getViewPagerFragment(HomePages.HOME)).toggleViewMode();
                    }
                }, delay ? 500 : 0);

                item.setIcon(expanded ? icAgendaMore : icAgendaLess);
                return true;
            case R.id.action_sort:
                ((MedicinesListFragment) getViewPagerFragment(HomePages.MEDICINES)).toggleSort();
                return true;

            case R.id.action_last_update:
                showLastUpdatedMsg();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    public void launchActivityDelayed(final Class<?> activityClazz, int delay) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(HomePagerActivity.this, activityClazz));
                overridePendingTransition(0, 0);
            }
        }, delay);

    }

    @Override
    public void onRoutineSelected(Routine r) {
        Intent i = new Intent(this, RoutinesActivity.class);
        i.putExtra(CalendulaApp.INTENT_EXTRA_ROUTINE_ID, r.getId());
        launchActivity(i);
    }

    @Override
    public void onCreateRoutine() {
        //do nothing
    }

    @Override
    public void onMedicineSelected(Medicine m) {
        Intent i = new Intent(this, MedicineInfoActivity.class);
        i.putExtra(CalendulaApp.INTENT_EXTRA_MEDICINE_ID, m.getId());
        launchActivity(i);
    }

    @Override
    public void onCreateMedicine() {

        //do nothing
    }

    @Override
    public void onScheduleSelected(Schedule r) {
        Intent i = new Intent(this, ScheduleBuildActivity.class);
        i.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID, r.getId());
        launchActivity(i);
    }

    @Override
    public void onCreateSchedule() {

    }

    // Method called from the event bus
    @Subscribe
    public void handleEvent(final Object event) {
        if (active) {
            handler.post(new Runnable() {
                @Override
                public void run() {

                    if (event instanceof PersistenceEvents.ModelCreateOrUpdateEvent) {
                        PersistenceEvents.ModelCreateOrUpdateEvent modelCreateOrUpdateEvent = (PersistenceEvents.ModelCreateOrUpdateEvent) event;
                        LogUtil.d(TAG, "handleEvent: " + modelCreateOrUpdateEvent.clazz.getName());
                        ((DailyAgendaFragment) getViewPagerFragment(HomePages.HOME)).notifyDataChange();
                        ((RoutinesListFragment) getViewPagerFragment(HomePages.ROUTINES)).notifyDataChange();
                        ((MedicinesListFragment) getViewPagerFragment(HomePages.MEDICINES)).notifyDataChange();
                        ((ScheduleListFragment) getViewPagerFragment(HomePages.SCHEDULES)).notifyDataChange();
                        if (event.equals(SCHEDULE_EVENT)) drawerMgr.onSchedulesUpdated();
                        ((ActiveMedicationListFragment) getViewPagerFragment(HomePages.ACTIVE_MEDICATION)).notifyDataChange();
                    } else if (event instanceof PersistenceEvents.IntakeConfirmedEvent) {
                        // dismiss "take all" button, update checkboxes
                        ((DailyAgendaFragment) getViewPagerFragment(HomePages.HOME)).notifyDataChange();
                        // stock info may need to be updated
                        ((MedicinesListFragment) getViewPagerFragment(HomePages.MEDICINES)).notifyDataChange();
                    } else if (event instanceof PersistenceEvents.ActiveUserChangeEvent) {
                        activePatient = ((PersistenceEvents.ActiveUserChangeEvent) event).patient;
                        updateTitle(mViewPager.getCurrentItem());
                        toolbarLayout.setContentScrimColor(activePatient.getColor());
                        fabMgr.onPatientUpdate(activePatient);
                    } else if (event instanceof PersistenceEvents.UserUpdateEvent) {
                        Patient p = ((PersistenceEvents.UserUpdateEvent) event).patient;
                        ((DailyAgendaFragment) getViewPagerFragment(HomePages.HOME)).onUserUpdate();
                        drawerMgr.onPatientUpdated(p);
                        if (DB.patients().isActive(p, HomePagerActivity.this)) {
                            activePatient = p;
                            updateTitle(mViewPager.getCurrentItem());
                            toolbarLayout.setContentScrimColor(activePatient.getColor());
                            fabMgr.onPatientUpdate(activePatient);
                        }
                    } else if (event instanceof PersistenceEvents.UserCreateEvent) {
                        Patient created = ((PersistenceEvents.UserCreateEvent) event).patient;
                        drawerMgr.onPatientCreated(created);
                    } else if (event instanceof HomeProfileMgr.BackgroundUpdatedEvent) {
                        ((DailyAgendaFragment) getViewPagerFragment(HomePages.HOME)).refresh();
                    } else if (event instanceof ConfirmActivity.ConfirmStateChangeEvent) {
                        pendingRefresh = ((ConfirmActivity.ConfirmStateChangeEvent) event).position;
                        ((DailyAgendaFragment) getViewPagerFragment(HomePages.HOME)).notifyDataChange();
                    } else if (event instanceof Agenda.AgendaUpdatedEvent) {
                        ((DailyAgendaFragment) getViewPagerFragment(HomePages.HOME)).notifyDataChange();
                        homeProfileMgr.updateDate();
                    } else if (event instanceof StockRunningOutEvent) {
                        final StockRunningOutEvent sro = (StockRunningOutEvent) event;
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                StockDisplayUtils.showStockRunningOutDialog(HomePagerActivity.this, sro.m, sro.days);
                            }
                        }, 1000);
                    } else if (event instanceof ActiveMedUpdateEvent) {
                        checkUpdateStatus();
                    }
                }
            });
        } else {
            pendingEvents.add(event);
        }
    }

    /**
     * If the app has been updated from an old Drug DB model, we need to re-install it and re-link the meds to their prescription.
     */
    public void checkDatabaseUpdateNeeded() {
        final boolean needPrompt = PreferenceUtils.getBoolean(PreferenceKeys.DRUGDB_DB_PROMPT, false);
        if (needPrompt) {
            new MaterialStyledDialog.Builder(this)
                    .setStyle(Style.HEADER_WITH_ICON)
                    .setIcon(IconUtils.icon(this, CommunityMaterial.Icon.cmd_database, R.color.white, 100))
                    .setHeaderColor(R.color.android_blue)
                    .withDialogAnimation(true)
                    .setTitle(R.string.enable_prescriptions_dialog_title)
                    .setDescription(R.string.reinstall_prescriptions_dialog_message)
                    .setCancelable(false)
                    .setPositiveText(getString(R.string.dialog_yes_option))
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            Intent i = new Intent(HomePagerActivity.this, CalendulaSettingsActivity.class);
                            i.putExtra(CalendulaSettingsActivity.EXTRA_SHOW_DB_DIALOG, true);
                            startActivity(i);
                            PreferenceUtils.edit().remove(PreferenceKeys.DRUGDB_DB_PROMPT.key()).apply();
                        }
                    })
                    .setNegativeText(R.string.dialog_no_option)
                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.cancel();
                            PreferenceUtils.edit().remove(PreferenceKeys.DRUGDB_DB_PROMPT.key()).apply();
                        }
                    })
                    .show();
        }

    }

    public Snackbar makeSnackbar(@StringRes final int text) {
        return makeSnackbar(text, Snackbar.LENGTH_SHORT);
    }

    public Snackbar makeSnackbar(@StringRes final int text, final int length) {
        return makeSnackbar(getString(text), length);
    }

    public Snackbar makeSnackbar(@NonNull final String text, final int length) {
        return Snackbar.make(coordinatorLayout, text, length)
                .addCallback(new Snackbar.Callback() {
                    @Override
                    public void onShown(Snackbar sb) {
                        super.onShown(sb);
                        snackbar = sb;
                        LogUtil.v(TAG, "snack shown");
                    }

                    @Override
                    public void onDismissed(Snackbar transientBottomBar, int event) {
                        super.onDismissed(transientBottomBar, event);
                        snackbar = null;
                        LogUtil.v(TAG, "snack dismissed");
                    }
                });
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        this.setIntent(newIntent);
        processIntent();
    }

    Fragment getViewPagerFragment(HomePages page) {
        String tag = FragmentUtils.makeViewPagerFragmentName(R.id.container, page.ordinal());
        return getSupportFragmentManager().findFragmentByTag(tag);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        setupToolbar(null, Color.TRANSPARENT);
        initializeDrawer(savedInstanceState);
        setupStatusBar(Color.TRANSPARENT);
        subscribeToEvents();
        handler = new Handler();

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new HomePageAdapter(getSupportFragmentManager(), this, this);

        // Set up the ViewPager with the sections adapter.
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.addOnPageChangeListener(getPageChangeListener());
        mViewPager.setOffscreenPageLimit(5);

        // Set up home profile
        homeProfileMgr = new HomeProfileMgr();
        homeProfileMgr.init(userInfoFragment, this);

        activePatient = DB.patients().getActive(this);
        updateScrim(0);


        fabMgr = new FabMenuMgr(fab, drawerMgr, this);
        fabMgr.init();

        fabMgr.onPatientUpdate(activePatient);


        // Setup the tabLayout
        setupTabLayout();

        AppBarLayout.OnOffsetChangedListener mListener = new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {

                //LogUtil.d(TAG, "Values: (" + toolbarLayout.getHeight()+ " + " +verticalOffset + ") < (2 * " + ViewCompat.getMinimumHeight(toolbarLayout) + ")");

                if ((toolbarLayout.getHeight() + verticalOffset) < (1.8 * ViewCompat.getMinimumHeight(toolbarLayout))) {
                    homeProfileMgr.onCollapse();
                    toolbarTitle.animate().alpha(1);
                    appBarLayoutExpanded = false;
                    LogUtil.d(TAG, "OnCollapse");
                } else {
                    appBarLayoutExpanded = true;
                    if (mViewPager.getCurrentItem() == 0) {
                        toolbarTitle.animate().alpha(0);
                    }
                    homeProfileMgr.onExpand();
                    LogUtil.d(TAG, "OnExpand");
                }


            }
        };
        appBarLayout.addOnOffsetChangedListener(mListener);

        CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams();
        ((AppBarLayout.Behavior)layoutParams.getBehavior()).setDragCallback(new AppBarLayout.Behavior.DragCallback() {
            @Override
            public boolean canDrag(AppBarLayout appBarLayout) {
                return appBarDraggable;
            }
        });

        icAgendaLess = new IconicsDrawable(this)
                .icon(CommunityMaterial.Icon2.cmd_unfold_less_horizontal)
                .color(Color.WHITE)
                .sizeDp(24);

        icAgendaMore = new IconicsDrawable(this)
                .icon(CommunityMaterial.Icon2.cmd_unfold_more_horizontal)
                .color(Color.WHITE)
                .sizeDp(24);

        if (!PreferenceUtils.getBoolean(PreferenceKeys.HOME_INTRO_SHOWN, false)) {
            launchActivityForResult(new Intent(HomePagerActivity.this, MaterialIntroActivity.class),MaterialIntroActivity.MATERIAL_INTRO_ACTIVITY);
        }

        if (getIntent() != null && getIntent().getBooleanExtra("invalid_notification_error", false)) {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showInvalidNotificationError();
                }
            }, 500);
        }

        // check status of last update
        checkUpdateStatus();

        processIntent();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Patient p = DB.patients().getActive(this);
        drawerMgr.onActivityResume(p);
        active = true;


        processIntent();

        // process pending events
        while (!pendingEvents.isEmpty()) {
            LogUtil.d(TAG, "Processing pending event...");
            handleEvent(pendingEvents.poll());
        }
    }

    @Override
    protected void onPause() {
        active = false;
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MaterialIntroActivity.MATERIAL_INTRO_ACTIVITY && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M ) {
            launchActivity(new Intent(HomePagerActivity.this, CheckBatterySavingActivity.class));
        }
    }//onActivityResult

    private void processIntent() {
        final String action = getIntent().getStringExtra(IntentParams.EXTRA_ACTION);
        if (action != null && action.equals(IntentParams.ACTION_SHOW_ACTIVE_MEDS)) {
            mViewPager.setCurrentItem(HomePages.ACTIVE_MEDICATION.ordinal());
        }
    }

    private void showLastUpdatedMsg() {
        String msg = PreferenceUtils.getString(PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_SUMMARY, "");
        DateTime lastUpdated = DB.healthcareProviderDB().activeMeds().findLastUpdateDate(activePatient);
        if (lastUpdated != null) {
            new MaterialStyledDialog.Builder(this)
                    .setTitle(lastUpdated.toString(getString(R.string.active_med_last_updated_time)))
                    .setStyle(Style.HEADER_WITH_ICON)
                    .setIcon(IconUtils.icon(this, HealthcareProviderTypeface.Icon.hcp_healthcare_provider, R.color.white, 48))
                    .setHeaderColor(R.color.healthcare_provider_dark)
                    .withDialogAnimation(true)
                    .setDescription(msg)
                    .setNegativeText(R.string.update_hma)
                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            ((ActiveMedicationListFragment) getViewPagerFragment(HomePages.ACTIVE_MEDICATION)).refresh();
                            dialog.dismiss();
                        }
                    })
                    .setPositiveText(R.string.tutorial_understood)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                        }
                    })
                    .show();
        } else {
            Toast.makeText(this, R.string.active_med_no_last_updated_time, Toast.LENGTH_SHORT).show();
        }
    }


    // Interface implementations

    /**
     * Check update status and make relevant display changes
     */
    private void checkUpdateStatus() {
        final String updateStatus = PreferenceUtils.getString(PreferenceKeys.REMOTE_UPDATE_STATUS, null);
        if (!TextUtils.isEmpty(updateStatus)) {
            try {
                ActiveMedUpdateEvent.Status status = ActiveMedUpdateEvent.Status.valueOf(updateStatus);
                LogUtil.d(TAG, "checkUpdateStatus: Status is:  " + status + ", last status is: " + lastStatus);
                if (status != lastStatus) {
                    switch (status) {
                        case UPDATE_START:
                            dismissSnackbar();
                            lastStatus = null;
                            break;
                        case SUCCESS:
                            // update has succeeded, dismiss errors
                            dismissSnackbar();
                            makeSnackbar(R.string.remote_update_successful, Snackbar.LENGTH_SHORT).show();
                            lastStatus = null;
                            break;
                        case ERROR_NO_CONNECTION:
                            dismissSnackbar();
                            makeSnackbar(R.string.remote_update_no_internet, Snackbar.LENGTH_INDEFINITE)
                                    .setAction(R.string.ok, new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            snackbar.dismiss();
                                        }
                                    })
                                    .show();
                            // show error
                            lastStatus = null;
                            break;
                        case ERROR_AUTHORIZATION:
                        case ERROR_AUTHORIZATION_LEVEL:
                            dismissSnackbar();
                            makeSnackbar(R.string.remote_update_auth_error, Snackbar.LENGTH_INDEFINITE)
                                    .setAction(R.string.login_button_login, new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            startActivity(new Intent(HomePagerActivity.this, StartActivity.class));
                                            finish();
                                        }
                                    })
                                    .show();
                            lastStatus = status;
                            break;
                        case ERROR_GENERIC:
                            dismissSnackbar();
                            makeSnackbar(R.string.remote_update_generic_error, Snackbar.LENGTH_INDEFINITE)
                                    .show();
                            lastStatus = status;
                            break;
                        case ERROR_NO_USER_OR_DB:
                            LogUtil.e(TAG, "checkUpdateStatus: no user or db?");
                            dismissSnackbar();
                            makeSnackbar(R.string.remote_update_auth_error, Snackbar.LENGTH_INDEFINITE)
                                    .setAction(R.string.login_button_login, new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            startActivity(new Intent(HomePagerActivity.this, StartActivity.class));
                                            finish();
                                        }
                                    })
                                    .show();
                            // TODO: 7/11/17 decide what to do here
                            lastStatus = status;
                            break;
                    }
                } else {
                    LogUtil.d(TAG, "checkUpdateStatus: status and lastStatus are equal");
                }
            } catch (IllegalArgumentException e) {
                LogUtil.e(TAG, "checkUpdateStatus: ", e);
            }
        } else {
            LogUtil.d(TAG, "checkUpdateStatus: No status");
            dismissSnackbar();
        }
    }

    private void dismissSnackbar() {
        if (snackbar != null) {
            snackbar.dismiss();
        }
    }

    private void showInvalidNotificationError() {

        final boolean expanded = ((DailyAgendaFragment) getViewPagerFragment(HomePages.HOME)).isExpanded();

        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_error_title)
                .setMessage(R.string.notification_error_msg)
                .setCancelable(true)
                .setIcon(IconUtils.icon(this, CommunityMaterial.Icon.cmd_bug, R.color.black))
                .setPositiveButton(R.string.tutorial_understood, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (!expanded) {
                            appBarLayout.setExpanded(expanded);
                            menuItems.get(R.id.action_expand).setIcon(expanded ? icAgendaMore : icAgendaLess);
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    ((DailyAgendaFragment) getViewPagerFragment(HomePages.HOME)).toggleViewMode();
                                }
                            }, 200);
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    ((DailyAgendaFragment) getViewPagerFragment(HomePages.HOME)).scrollTo(DateTime.now());
                                }
                            }, 600);
                        }
                    }
                }).create().show();
    }

    private void setupTabLayout() {

        tabLayout.setupWithViewPager(mViewPager);

        for (int i = 0; i < tabLayout.getTabCount(); i++) {

            Drawable icon = new IconicsDrawable(this)
                    .icon(HomePages.values()[i].icon)
                    .alpha(80)
                    .paddingDp(2)
                    .color(Color.WHITE)
                    .sizeDp(24);

            tabLayout.getTabAt(i).setIcon(icon);
        }
    }

    private ViewPager.OnPageChangeListener getPageChangeListener() {
        return new ViewPager.OnPageChangeListener() {

            private ArgbEvaluator argbEvaluator = new ArgbEvaluator();


            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

                HomePages page = HomePages.getPage(position);
                HomePages nextPage;


                final boolean isLast = position == (mSectionsPagerAdapter.getCount() - 1);
                if (isLast) {
                    nextPage = page;
                } else {
                    nextPage = HomePages.getPage(position + 1);
                }


                mViewPager.setBackgroundColor((Integer) argbEvaluator.evaluate(positionOffset, page.backgroundColor, nextPage.backgroundColor));

            }

            @Override
            public void onPageSelected(int position) {
                updateTitle(position);
                updateScrim(position);
                fabMgr.onViewPagerItemChange(position);
                if (position == HomePages.HOME.ordinal()) {
                    appBarLayout.setExpanded(!((DailyAgendaFragment) getViewPagerFragment(HomePages.HOME)).isExpanded());
                    appBarDraggable=true;

                } else {
                    appBarLayout.setExpanded(false);
                    appBarDraggable=false;
                }

                invalidateOptionsMenu();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        };
    }

    private void updateScrim(int position) {
        @ColorInt final int color = (position == HomePages.HOME.ordinal()) ? ContextCompat.getColor(this, R.color.transparent_black) : activePatient.getColor();

        if (previousColor != -1) {
            ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), previousColor, color);
            colorAnimation.setDuration(250); // milliseconds
            colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    toolbarLayout.setContentScrimColor((int) animator.getAnimatedValue());
                }

            });
            colorAnimation.start();
        } else {
            toolbarLayout.setContentScrimColor(color);
        }
        previousColor = color;
    }

    private void updateTitle(int page) {
        String title;
        if (page == HomePages.HOME.ordinal()) {
            title = getString(R.string.app_name);
        } else {
            title = getString(R.string.relation_user_possession_thing, activePatient.getName(), getString(HomePages.values()[page].title));
        }

        toolbarTitle.setText(title);
    }

    private void initializeDrawer(Bundle savedInstanceState) {
        drawerMgr = new LeftDrawerMgr(this, toolbar);
        drawerMgr.init(savedInstanceState);
    }

    private void launchActivity(Intent i) {
        startActivity(i);
        this.overridePendingTransition(0, 0);
    }

    private void launchActivityForResult(Intent i, int requestCode) {
        startActivityForResult(i, requestCode);
        this.overridePendingTransition(0, 0);
    }

    /*
    public void askForWEEPermissionsIfNeeded() {
        String p = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        if(!PermissionUtils.hasAskedForPermission(this, p) && PermissionUtils.shouldAskForPermission(this, p)){
            PermissionUtils.requestPermissions(this, new String[]{p}, REQ_CODE_EXTERNAL_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQ_CODE_EXTERNAL_STORAGE: {
                PermissionUtils.markedPermissionAsAsked(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(HomePagerActivity.this, "Granted   !", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(HomePagerActivity.this, "Refused   !", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }

    }
    */

}
