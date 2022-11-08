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

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.IIcon;
import com.mikepenz.iconics.view.IconicsImageView;

import org.greenrobot.eventbus.Subscribe;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.CalendulaActivity;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.adapters.MedInfoPageAdapter;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.drugdb.DBRegistry;
import es.usc.citius.servando.calendula.drugdb.PrescriptionDBMgr;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.fragments.ActiveMedInfoFragment;
import es.usc.citius.servando.calendula.fragments.AlertListFragment;
import es.usc.citius.servando.calendula.fragments.MedInfoFragment;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.util.FragmentUtils;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.HealthcareProviderTypeface;
import es.usc.citius.servando.calendula.util.Snack;

public class MedicineInfoActivity extends CalendulaActivity {

    public static final String ACTION_SHOW_ALERTS = "es.usc.citius.servando.calendula.activities.MedicineInfoActivity.SHOW_ALERTS";
    public static final String ACTION_SHOW_ONLY_ACTIVE_MED = "es.usc.citius.servando.calendula.activities.MedicineInfoActiviy.SHOW_ONLY_ACTIVE_MED";
    public static final String EXTRA_ACTIVE_MED = "calendula.MedicineInfoActivity.extras.ACTIVE_MED";

    private static final String TAG = "MedicineInfoActivity";

    @BindView(R.id.appbar)
    AppBarLayout appBarLayout;
    @BindView(R.id.medicine_icon)
    ImageView medIcon;
    @BindView(R.id.medicine_name)
    TextView medName;
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    @BindView(R.id.container)
    ViewPager mViewPager;
    @BindView(R.id.collapsing_toolbar)
    CollapsingToolbarLayout toolbarLayout;
    @BindView(R.id.toolbar_title)
    TextView toolbarTitle;
    @BindView(R.id.sliding_tabs)
    TabLayout tabLayout;
    @BindView(R.id.provider_badge)
    IconicsImageView providerBadge;

    Patient activePatient;
    Medicine medicine;
    ActiveMedVO activeMedVO;
    PrescriptionDBMgr dbMgr;
    int alertLevel = -1;

    private MedInfoPageAdapter mSectionsPagerAdapter;
    private boolean showAlerts = false;
    private boolean showActiveMed = false;

    private MedInfoPageAdapter.MedInfoPageSet pageSet = null;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.medicine_info, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.getItem(0).setIcon(IconUtils.icon(this, CommunityMaterial.Icon2.cmd_pencil, R.color.white, 24, 2));
        if (pageSet != null && pageSet.equals(MedInfoPageAdapter.MedInfoPageSet.ONLY_ACTIVE_MED)) {
            menu.getItem(0).setVisible(false);
            menu.getItem(1).setVisible(false);
        } else if (medicine != null && medicine.isFromActiveMed()) {
            menu.getItem(1).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_edit:
                Intent intent = new Intent(this, MedicinesActivity.class);
                intent.putExtra(CalendulaApp.INTENT_EXTRA_MEDICINE_ID, medicine.getId());
                startActivity(intent);
                return true;
            case R.id.action_remove:
                showDeleteConfirmationDialog(medicine);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showDeleteConfirmationDialog(final Medicine m) {
        String message;
        if (!DB.schedules().findByMedicine(m).isEmpty()) {
            message = String.format(getString(R.string.remove_medicine_message_long), m.getName());
        } else {
            message = String.format(getString(R.string.remove_medicine_message_short), m.getName());
        }

        new MaterialStyledDialog.Builder(this)
                .setStyle(Style.HEADER_WITH_ICON)
                .setIcon(IconUtils.icon(this, CommunityMaterial.Icon2.cmd_pill, R.color.white, 100))
                .setHeaderColor(R.color.android_red)
                .withDialogAnimation(true)
                .setTitle(getString(R.string.remove_medicine_dialog_title))
                .setDescription(message)
                .setCancelable(true)
                .setNeutralText(getString(R.string.dialog_no_option))
                .setPositiveText(getString(R.string.dialog_yes_option))
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        DB.medicines().deleteCascade(m, true);
                        CalendulaApp.eventBus().post(PersistenceEvents.MEDICINE_EVENT);
                        finish();
                    }
                })
                .onNeutral(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.cancel();
                    }
                })
                .show();
    }

    // Method called from the event bus
    @Subscribe
    public void handleEvent(final PersistenceEvents.ModelCreateOrUpdateEvent event) {
        Class<?> cls = event.clazz;
        Object model = event.model;
        if (cls.equals(Medicine.class) && model != null) {

            Medicine med = (Medicine) model;

            if (med.getId().equals(medicine.getId())) {

                if (medicine.getCn() == null && med.getCn() != null) {
                    Snack.show(R.string.message_med_linked_success, this, Snackbar.LENGTH_SHORT);
                }
                ((MedInfoFragment) getViewPagerFragment(0)).notifyDataChange();
                ((AlertListFragment) getViewPagerFragment(1)).notifyDataChange();
                DB.medicines().refresh(medicine);
                updateMedDetails();

            }
        }
        else if (cls.equals(Schedule.class)) {
            switch (pageSet) {
                case NO_ACTIVE_MED:
                    ((MedInfoFragment) getViewPagerFragment(0)).notifyDataChange();
                    break;
                case ONLY_ACTIVE_MED:
                    ((ActiveMedInfoFragment) getViewPagerFragment(0)).notifyDataChange();
                    break;
                case ALL:
                    ((MedInfoFragment) getViewPagerFragment(0)).notifyDataChange();
                    ((ActiveMedInfoFragment) getViewPagerFragment(1)).notifyDataChange();
                    break;
            }
        }
    }

    Fragment getViewPagerFragment(int position) {
        String tag = FragmentUtils.makeViewPagerFragmentName(R.id.container, position);
        return getSupportFragmentManager().findFragmentByTag(tag);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_info);
        ButterKnife.bind(this);
        setupToolbar(null, Color.TRANSPARENT);
        setupStatusBar(Color.TRANSPARENT);

        activePatient = DB.patients().getActive(this);
        dbMgr = DBRegistry.instance().current();

        processIntent();

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new MedInfoPageAdapter(getSupportFragmentManager(), medicine, activeMedVO, pageSet);

        // Set up the ViewPager with the sections adapter.
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.addOnPageChangeListener(getPageChangeListener());
        mViewPager.setOffscreenPageLimit(5);

        toolbarLayout.setContentScrimColor(activePatient.getColor());
        toolbarLayout.setBackgroundColor(activePatient.getColor());
        updateMedDetails();
        // Setup the tabLayout
        setupTabLayout();

        AppBarLayout.OnOffsetChangedListener mListener = new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                verticalOffset = Math.abs(verticalOffset);
                if (verticalOffset > 100) {
                    // collapse
                    toolbarTitle.animate().alpha(1);
                } else {
                    // expand
                    toolbarTitle.animate().alpha(0);
                }
            }
        };
        appBarLayout.addOnOffsetChangedListener(mListener);
        toolbarTitle.animate().alpha(0);

        if (showAlerts) {
            switch (pageSet) {
                case ALL:
                    mViewPager.setCurrentItem(2);
                    break;
                case NO_ACTIVE_MED:
                    mViewPager.setCurrentItem(1);
                    break;
                case ONLY_ACTIVE_MED:
                    LogUtil.e(TAG, "onCreate: showAlerts is true, but there is no alerts tab in the active med only pageset");
                    break;
            }
        } else if (showActiveMed) {
            switch (pageSet) {
                case ALL:
                    mViewPager.setCurrentItem(1);
                    break;
                case NO_ACTIVE_MED:
                    mViewPager.setCurrentItem(1);
                    break;
                case ONLY_ACTIVE_MED:
                    LogUtil.e(TAG, "onCreate: showAlerts is true, but there is no alerts tab in the active med only pageset");
                    break;
            }
        }

        if (!pageSet.equals(MedInfoPageAdapter.MedInfoPageSet.ONLY_ACTIVE_MED) && medicine.isFromActiveMed()) {
            providerBadge.setVisibility(View.VISIBLE);
        }

        subscribeToEvents();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void updateMedDetails() {
        toolbarTitle.setText(getString(R.string.label_info_short) + " | " + getCurrentName());
        medName.setText(getCurrentName());
        medIcon.setImageDrawable(IconUtils.icon(this, getCurrentIcon(), R.color.white));
    }

    private String getCurrentName() {
        if (pageSet != MedInfoPageAdapter.MedInfoPageSet.ONLY_ACTIVE_MED) {
            return medicine.getName();
        } else {
            return activeMedVO.getDisplay();
        }
    }

    private IIcon getCurrentIcon() {
        if (pageSet != MedInfoPageAdapter.MedInfoPageSet.ONLY_ACTIVE_MED) {
            return medicine.getPresentation().icon();
        } else {
            final Presentation presentation = DBRegistry.instance().current().expectedPresentation(activeMedVO.getDisplay(), "");
            return presentation != null ? presentation.icon() : Presentation.UNKNOWN.icon();
        }
    }

    private void processIntent() {

        final Intent intent = getIntent();
        if (intent == null) {
            LogUtil.w(TAG, "processIntent: no intent!");
            return;
        }

        final long medId = intent.getLongExtra("medicine_id", -1);
        final String activeMedCode = intent.getStringExtra(EXTRA_ACTIVE_MED);

        medicine = DB.medicines().findById(medId);
        if (activeMedCode != null) {
            final ActiveMedEntity amEntity = DB.healthcareProviderDB().activeMeds().findOneBy(ActiveMedEntity.COLUMN_CODE, activeMedCode);
            activeMedVO = amEntity!=null ? ActiveMedVO.forEntity(amEntity) : null;
        }

        final String action = intent.getAction();

        if (action != null) {
            LogUtil.d(TAG, "processIntent: action is " + action);
            switch (action) {
                case ACTION_SHOW_ALERTS:
                    showAlerts = true;
                    break;
                case ACTION_SHOW_ONLY_ACTIVE_MED:
                    pageSet = medicine != null ? MedInfoPageAdapter.MedInfoPageSet.ALL : MedInfoPageAdapter.MedInfoPageSet.ONLY_ACTIVE_MED;
                    showActiveMed = true;
                    if (activeMedCode == null) {
                        LogUtil.e(TAG, "processIntent: action is SHOW_ONLY_ACTIVE_MED but no active med received");
                        throw new IllegalArgumentException("Action is SHOW_ONLY_ACTIVE_MED but no active med received");
                    }
                    if (medId != -1) {
                        LogUtil.w(TAG, "processIntent: Action is SHOW_ONLY_ACTIVE_MED but received medicine as well!");
                    }
                    break;

            }
        }
        if (pageSet == null) {
            // if action is not SHOW_ONLY_ACTIVE_MED, medicine is required
            if (medicine == null) {
                Toast.makeText(MedicineInfoActivity.this, R.string.medicine_not_found_error, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                // decide if we show the active med page
                if (medicine.isFromActiveMed()) {
                    pageSet = MedInfoPageAdapter.MedInfoPageSet.ALL;
                    activeMedVO = ActiveMedVO.forEntity(DB.healthcareProviderDB().activeMeds().findById(medicine.getActiveMedId()));
                } else {
                    pageSet = MedInfoPageAdapter.MedInfoPageSet.NO_ACTIVE_MED;
                }
            }
            List<PatientAlert> alerts = DB.alerts().findBy(PatientAlert.COLUMN_MEDICINE, medicine);
            for (PatientAlert a : alerts) {
                if (a.getLevel() > alertLevel) {
                    alertLevel = a.getLevel();
                }
            }
        }
        LogUtil.d(TAG, "processIntent: pageSet is " + pageSet);
    }

    private void setupTabLayout() {

        tabLayout.setupWithViewPager(mViewPager);

        IIcon[] icons = null;

        switch (pageSet) {
            case ALL:
                icons = new IIcon[]{
                        CommunityMaterial.Icon.cmd_book_open,
                        HealthcareProviderTypeface.Icon.hcp_healthcare_provider,
                        CommunityMaterial.Icon2.cmd_message_alert
                };
                break;
            case NO_ACTIVE_MED:
                icons = new IIcon[]{
                        CommunityMaterial.Icon.cmd_book_open,
                        CommunityMaterial.Icon2.cmd_message_alert
                };
                break;
            case ONLY_ACTIVE_MED:
                icons = new IIcon[]{
                        HealthcareProviderTypeface.Icon.hcp_healthcare_provider,
                };
                break;
        }


        for (int i = 0; i < tabLayout.getTabCount(); i++) {

            Drawable icon = new IconicsDrawable(this)
                    .icon(icons[i])
                    .alpha(80)
                    .paddingDp(2)
                    .color(Color.WHITE)
                    .sizeDp(24);

            tabLayout.getTabAt(i).setIcon(icon);
        }

        if (pageSet.equals(MedInfoPageAdapter.MedInfoPageSet.ONLY_ACTIVE_MED)) {
            tabLayout.setVisibility(View.GONE);
        }
    }

    private ViewPager.OnPageChangeListener getPageChangeListener() {
        return new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (medicine != null) {
                    if (position == 0) {
                        toolbarTitle.setText(getString(R.string.label_info_short) + " | " + medicine.getName());
                    } else if (position == 1) {
                        toolbarTitle.setText(getString(R.string.label_alerts_short) + " | " + medicine.getName());
                    }

                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        };
    }

}
