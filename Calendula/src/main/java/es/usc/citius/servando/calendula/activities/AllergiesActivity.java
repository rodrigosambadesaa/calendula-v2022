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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IItem;
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter;
import com.mikepenz.fastadapter.expandable.ExpandableExtension;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import es.usc.citius.servando.calendula.CalendulaActivity;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.adapters.items.allergylist.AllergyGroupItem;
import es.usc.citius.servando.calendula.adapters.items.allergylist.AllergyGroupSubItem;
import es.usc.citius.servando.calendula.adapters.items.allergylist.AllergyItem;
import es.usc.citius.servando.calendula.allergies.AllergenConversionUtil;
import es.usc.citius.servando.calendula.allergies.AllergenFacade;
import es.usc.citius.servando.calendula.allergies.AllergenGroupWrapper;
import es.usc.citius.servando.calendula.allergies.AllergenVO;
import es.usc.citius.servando.calendula.allergies.AllergyAlertUtil;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert.AllergyAlertInfo;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.Snack;
import es.usc.citius.servando.calendula.util.alerts.AlertManager;

@SuppressWarnings("unchecked")
public class AllergiesActivity extends CalendulaActivity {


    private static final String TAG = "AllergiesActivity";
    private static final ExecutorService ALLERGY_EXECUTOR = Executors.newSingleThreadExecutor();
    // main view
    @BindView(R.id.add_button)
    protected FloatingActionButton addButton;
    @BindView(R.id.allergies_recycler)
    protected RecyclerView allergiesRecycler;
    @BindView(R.id.textview_no_allergies_placeholder)
    protected TextView allergiesPlaceholder;

    // general
    @BindView(R.id.main_progress_bar)
    protected ProgressBar progressBar;

    private int color;
    private Long openedPatientId;
    private String openedPatientFingerprint;
    private static final String STATE_OPENED_PATIENT_ID = "allergies_original_patient_id";
    private static final String STATE_OPENED_PATIENT_FINGERPRINT =
            "allergies_original_patient_fingerprint";
    private FastItemAdapter allergiesAdapter;

    private boolean isOriginalPatientActive() {
        Patient active = DB.patients().getActive(this);
        return AllergenSearchOwnerGuard.samePatient(
                openedPatientId, openedPatientFingerprint,
                active == null ? null : active.getId(),
                active == null ? null : active.getCode());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (openedPatientId != null && openedPatientId > 0L) {
            outState.putLong(STATE_OPENED_PATIENT_ID, openedPatientId);
            if (openedPatientFingerprint != null) {
                outState.putString(STATE_OPENED_PATIENT_FINGERPRINT,
                        openedPatientFingerprint);
            }
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (openedPatientId != null && !isOriginalPatientActive()) {
            finish();
        }
    }
    private AllergiesStore store;
    private ExpandableExtension<IItem> expandableExtension;


    @OnClick(R.id.add_button)
    void showSearchView() {
        if (!isOriginalPatientActive()) {
            finish();
            return;
        }
        Intent i = new Intent(this, AllergiesSearchActivity.class);
        startActivityForResult(i, AllergiesSearchActivity.REQUEST_NEW_ALLERGIES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AllergiesSearchActivity.REQUEST_NEW_ALLERGIES
                && resultCode == Activity.RESULT_OK && data != null) {
            if (!isOriginalPatientActive()) {
                finish();
                return;
            }
            final ArrayList<AllergenGroupWrapper> result =
                    data.getParcelableArrayListExtra("result");
            if (result != null && !result.isEmpty()) {
                saveAllergiesAsync(result);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_allergies);
        ButterKnife.bind(this);

        addButton.setImageDrawable(new IconicsDrawable(this)
                .icon(GoogleMaterial.Icon.gmd_plus)
                .paddingDp(5)
                .sizeDp(24)
                .colorRes(R.color.fab_default_icon_color));

        //setup toolbar and status bar
        final Patient patient = DB.patients().getActive(this);
        if (patient == null || patient.getId() == null) {
            finish();
            return;
        }
        Long savedOwner = RestoredClinicalOwnerGuard.restoredOwnerOrNull(
                savedInstanceState, STATE_OPENED_PATIENT_ID, patient.getId());
        String restoredFingerprint;
        try {
            restoredFingerprint = savedInstanceState == null
                    ? AllergenSearchOwnerGuard.fingerprint(patient.getCode())
                    : savedInstanceState.getString(STATE_OPENED_PATIENT_FINGERPRINT);
        } catch (RuntimeException malformedState) {
            restoredFingerprint = null;
        }
        if (!AllergenSearchOwnerGuard.samePatient(
                savedOwner, restoredFingerprint, patient.getId(), patient.getCode())) {
            finish();
            return;
        }
        openedPatientId = savedOwner;
        openedPatientFingerprint = restoredFingerprint;
        color = patient.getColor();
        final String name = patient.getName();
        setupToolbar(getString(R.string.relation_user_possession_thing, name, getString(R.string.title_activity_allergies)), color);
        setupStatusBar(color);

        //initialize allergies store
        store = new AllergiesStore();

        //setup recycler
        setupAllergiesList();

        progressBar.getIndeterminateDrawable().setColorFilter(color,
                android.graphics.PorterDuff.Mode.MULTIPLY);

        //load allergies, set placeholder if needed
        loadAllergiesAsync();

        showWarningIfNeeded();
    }

    private void showWarningIfNeeded() {
        final SharedPreferences prefs = PreferenceUtils.instance().preferences();
        // A legacy/restored wrong-type value is never consent to skip the
        // experimental-allergies warning. Keep the persisted data untouched.
        if (AllergiesWarningPreferencePolicy.shouldShowWarning(prefs)) {
            new MaterialStyledDialog.Builder(this)
                    .setStyle(Style.HEADER_WITH_ICON)
                    .setIcon(IconUtils.icon(this, GoogleMaterial.Icon.gmd_alert_circle, R.color.white, 100))
                    .setHeaderColor(R.color.android_orange_dark)
                    .withDialogAnimation(true)
                    .setTitle(R.string.important_info)
                    .setDescription(R.string.message_allergies_experimental_warning)
                    .setCancelable(false)
                    .setPositiveText(getString(R.string.dialog_continue_option))
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            prefs.edit().putBoolean(PreferenceKeys.ALLERGIES_WARNING_SHOWN.key(), true).apply();
                            dialog.dismiss();
                        }
                    })
                    .setNegativeText(R.string.dialog_get_me_out)
                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.cancel();
                            finish();
                        }
                    })
                    .show();
        }
    }

    private void checkPlaceholder() {
        if (allergiesPlaceholder.getVisibility() == View.VISIBLE) {
            if (!store.isEmpty())
                allergiesPlaceholder.setVisibility(View.GONE);
        } else if (store.isEmpty()) {
            allergiesPlaceholder.setVisibility(View.VISIBLE);
        }
    }

    private List<AbstractItem> getAllergyItems() {
        final List<PatientAllergen> allergies = new ArrayList<>(store.getAllergies());

        List<AbstractItem> items = new ArrayList<>(allergies.size());
        List<PatientAllergen> toRemove = new ArrayList<>();

        Map<String, List<AllergyGroupSubItem>> groups = new HashMap<>();

        for (PatientAllergen allergen : allergies) {
            final String group = allergen.getGroup();
            if (group != null && !group.isEmpty()) {
                if (!groups.keySet().contains(group)) {
                    groups.put(group, new ArrayList<AllergyGroupSubItem>());
                }
                groups.get(group).add(new AllergyGroupSubItem(allergen, this));
                toRemove.add(allergen);
            }
        }
        allergies.removeAll(toRemove);
        for (String key : groups.keySet()) {
            AllergyGroupItem g = new AllergyGroupItem(key, this);
            g.withSubItems(groups.get(key));
            items.add(g);
        }
        for (PatientAllergen allergen : allergies) {
            items.add(new AllergyItem(allergen, this));
        }
        return items;
    }

    private void hideAllergiesView(boolean hide) {
        final int visibility = hide ? View.GONE : View.VISIBLE;
        allergiesRecycler.setVisibility(visibility);
        //TODO: check collateral effects
//        addButton.setVisibility(visibility);
        addButton.show();
        if (!hide)
            checkPlaceholder();

    }

    private void setupAllergiesList() {
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        allergiesRecycler.setLayoutManager(llm);
        allergiesAdapter = new FastItemAdapter<>();
        allergiesAdapter.withSelectable(false);
        expandableExtension = new ExpandableExtension<>();
        allergiesAdapter.addExtension(expandableExtension);
        allergiesAdapter.withEventHook(new ClickEventHook<AbstractItem>() {

            @Override
            public void onClick(View view, int i, FastAdapter fastAdapter, AbstractItem item) {
                LogUtil.d(TAG, "Allergy row action received");
                switch (view.getId()) {
                    case R.id.delete_button:
                        // check if group or item
                        switch (item.getType()) {
                            case R.id.fastadapter_allergy_group_item:
                                showDeleteConfirmationDialog((AllergyGroupItem) item);
                                break;
                            case R.id.fastadapter_allergy_item:
                                showDeleteConfirmationDialog((AllergyItem) item);
                                break;
                            default:
                                LogUtil.w(TAG, "Unexpected allergy row type");
                                break;
                        }
                        break;
                    case R.id.group_button:
                        AllergyGroupItem g = (AllergyGroupItem) item;
                        boolean expand = !g.isExpanded();
                        float angle = expand ? 180 : 0;
                        ViewCompat.animate(view).rotation(angle);
                        if (expand)
                            expandableExtension.expand(i);
                        else
                            expandableExtension.collapse(i);
                        view.setContentDescription(getString(
                                expand ? R.string.accessibility_collapse : R.string.accessibility_expand));
                        break;
                    default:
                        LogUtil.w(TAG, "Unexpected allergy row action");
                        break;
                }
            }

            @Nullable
            @Override
            public List<View> onBindMany(@NonNull RecyclerView.ViewHolder viewHolder) {
                List<View> vl = new ArrayList<>();
                if (viewHolder instanceof AllergyGroupItem.ViewHolder) {
                    AllergyGroupItem.ViewHolder vh = (AllergyGroupItem.ViewHolder) viewHolder;
                    vl.add(vh.deleteButton);
                    vl.add(vh.dropButton);
                    return vl;
                } else if (viewHolder instanceof AllergyItem.ViewHolder) {
                    vl.add(((AllergyItem.ViewHolder) viewHolder).deleteButton);
                    return vl;
                }
                return null;
            }
        });
        allergiesRecycler.setAdapter(allergiesAdapter);
    }

    private void showDeleteConfirmationDialog(final AllergyItem a) {
        showDeleteConfirmationDialog(getString(R.string.remove_allergy_message_short, a.getAllergen().getName()), new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                deleteAllergyAsync(a);
                dialog.dismiss();
            }
        }, new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                dialog.cancel();
            }
        });
    }

    private void showDeleteConfirmationDialog(final AllergyGroupItem a) {
        showDeleteConfirmationDialog(getString(R.string.remove_allergy_message_short, a.getTitle()), new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                deleteAllergyGroupAsync(a);
                dialog.dismiss();
            }
        }, new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                dialog.cancel();
            }
        });
    }

    private void showDeleteConfirmationDialog(String message, MaterialDialog.SingleButtonCallback onPositive, MaterialDialog.SingleButtonCallback onNegative) {
        new MaterialStyledDialog.Builder(this)
                .setStyle(Style.HEADER_WITH_ICON)
                .setIcon(IconUtils.icon(this, CommunityMaterial.Icon.cmd_delete, R.color.white, 100))
                .setHeaderColor(R.color.android_red)
                .withDialogAnimation(true)
                .setDescription(message)
                .setCancelable(true)
                .setNegativeText(getString(R.string.dialog_no_option))
                .setPositiveText(getString(R.string.dialog_yes_option))
                .onPositive(onPositive)
                .onNegative(onNegative)
                .show();
    }

    private void showNewAllergyConflictDialog() {
        new MaterialStyledDialog.Builder(this)
                .setStyle(Style.HEADER_WITH_ICON)
                .setIcon(IconUtils.icon(this, CommunityMaterial.Icon.cmd_exclamation, R.color.white, 100))
                .setHeaderColor(R.color.android_red)
                .withDialogAnimation(true)
                .setTitle(R.string.title_allergies_detected_dialog)
                .setDescription(R.string.message_allergies_detected_dialog)
                .setCancelable(false)
                .setPositiveText(getString(R.string.ok))
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    public enum SaveResult {
        OK, ERROR, ALLERGY, COMMITTED_RECOVERY_REQUIRED
    }

    public class AllergiesStore {


        private List<PatientAllergen> currentAllergies;
        private Context context;

        public AllergiesStore() {
        }

        private Long loadedPatientId;
        private String loadedPatientFingerprint;

        /**
         * All database deletes in a group COMMIT together. Clinical alert and
         * Android alarm cleanup runs only afterward, never inside the outer
         * SQLite transaction (which could later roll back).
         */
        private int deleteVerifiedAllergens(final List<PatientAllergen> candidates,
                                            boolean notify) {
            if (candidates == null || candidates.isEmpty() || context == null
                    || loadedPatientId == null || loadedPatientId <= 0L
                    || loadedPatientFingerprint == null) {
                return -2;
            }
            final List<PatientAllergen> deleted;
            try {
                deleted = (List<PatientAllergen>) DB.transaction(() -> {
                    Patient active = DB.patients().getActive(context);
                    Long activeId = active == null ? null : active.getId();
                    String activeCode = active == null ? null : active.getCode();
                    if (!AllergenSearchOwnerGuard.samePatient(
                            loadedPatientId, loadedPatientFingerprint, activeId, activeCode)
                            || !AllergenSearchOwnerGuard.samePatient(
                                    openedPatientId, openedPatientFingerprint, activeId,
                                    activeCode)) {
                        throw new SQLException("Allergen deletion patient identity changed");
                    }
                    List<PatientAllergen> live = new ArrayList<>(candidates.size());
                    java.util.Set<Long> seen = new java.util.HashSet<>();
                    for (PatientAllergen candidate : candidates) {
                        if (candidate == null || candidate.getId() == null
                                || candidate.getPatient() == null
                                || !seen.add(candidate.getId())) {
                            throw new SQLException("Missing or duplicate allergen ID");
                        }
                        PatientAllergen stored = DB.patientAllergens().findById(
                                candidate.getId());
                        if (stored == null || DB.patientAllergens().refresh(stored) != 1
                                || stored.getPatient() == null
                                || !AllergenSearchOwnerGuard.samePatient(
                                        loadedPatientId, loadedPatientFingerprint,
                                        candidate.getPatient().getId(),
                                        candidate.getPatient().getCode())
                                || !AllergenSearchOwnerGuard.samePatient(
                                        loadedPatientId, loadedPatientFingerprint,
                                        stored.getPatient().getId(),
                                        stored.getPatient().getCode())
                                || !AllergenDeletionGuard.isSamePersistedOwner(
                                    loadedPatientId, activeId,
                                    candidate.getId(), candidate.getPatient().getId(),
                                    stored.getId(), stored.getPatient().getId(),
                                    candidate.getType(), stored.getType(),
                                    candidate.getIdentifier(), stored.getIdentifier())) {
                            throw new SQLException("Allergen ownership changed before deletion");
                        }
                        live.add(stored);
                    }
                    // Recheck profile immediately before the first SQL write.
                    Patient current = DB.patients().getActive(context);
                    if (current == null || !AllergenSearchOwnerGuard.samePatient(
                            loadedPatientId, loadedPatientFingerprint,
                            current.getId(), current.getCode())) {
                        throw new SQLException("Active patient changed while deleting allergens");
                    }
                    for (PatientAllergen stored : live) {
                        // The DAO's bare deleteById() can report zero for a
                        // readable generated row. Use the single-row,
                        // owner/type/identifier-scoped SQL deletion instead;
                        // its affected-row check aborts the ENTIRE batch.
                        DB.patientAllergens().deleteVerifiedOwned(stored, current);
                    }
                    return live;
                });
            } catch (RuntimeException failure) {
                LogUtil.e(TAG, "Unable to delete verified patient allergens");
                return -2;
            }

            // Never run AlertManager.removeAlert inside the batch transaction.
            // If a subsequent alert operation fails, conservative HIGH alerts
            // remain, and the UI reloads SQLite rather than claiming success.
            try {
                for (PatientAllergen allergen : deleted) {
                    AllergyAlertUtil.removeAllergyAlerts(context, allergen);
                }
            } catch (SQLException | RuntimeException failure) {
                LogUtil.e(TAG, "Allergen removal committed; clinical warning cleanup needs review");
                return -3; // SQLite already committed; do not imply retrying the deletion.
            }
            if (notify) {
                try {
                    CalendulaApp.eventBus().post(PersistenceEvents.MEDICINE_EVENT);
                } catch (RuntimeException failure) {
                    LogUtil.e(TAG, "Allergen removal committed; observer recovery incomplete");
                    return -3;
                }
            }
            return 0;
        }

        public int deleteAllergen(PatientAllergen allergen, boolean notify) {
            if (allergen == null || currentAllergies == null) {
                return -2;
            }
            // The caller rebuilds its adapter from the committed SQLite
            // state; a cached list index must never drive clinical UI cleanup.
            return deleteVerifiedAllergens(
                    Collections.singletonList(allergen), notify);
        }

        public int deleteAllergens(final List<PatientAllergen> allergens) {
            return deleteVerifiedAllergens(allergens, true);
        }

        public List<PatientAllergen> getAllergies() {
            return currentAllergies;
        }

        public List<AllergenVO> getAllergiesVO() {
            return AllergenConversionUtil.toVO(currentAllergies);
        }

        public boolean isEmpty() {
            return currentAllergies.isEmpty();
        }

        public void load(Context ctx) {
            context = ctx;
            Patient opened = DB.patients().getActive(ctx);
            loadedPatientId = opened == null ? null : opened.getId();
            loadedPatientFingerprint = opened == null ? null
                    : AllergenSearchOwnerGuard.fingerprint(opened.getCode());
            reload();
        }

        public void reload() {
            Patient active = context == null ? null : DB.patients().getActive(context);
            if (active == null || !AllergenSearchOwnerGuard.samePatient(
                    loadedPatientId, loadedPatientFingerprint,
                    active.getId(), active.getCode())) {
                currentAllergies = new ArrayList<>();
                return;
            }
            currentAllergies = DB.patientAllergens().findAllForActivePatient(context);
            Collections.sort(currentAllergies, new Comparator<PatientAllergen>() {
                @Override
                public int compare(PatientAllergen o1, PatientAllergen o2) {
                    // Imported legacy rows may have no usable display name.
                    // Keep them in the list for review rather than crashing
                    // while sorting (their clinical rows must not disappear).
                    String first = o1.getName();
                    String second = o2.getName();
                    if (first == null || first.trim().isEmpty()) {
                        return second == null || second.trim().isEmpty() ? 0 : 1;
                    }
                    if (second == null || second.trim().isEmpty()) return -1;
                    return first.compareTo(second);
                }
            });
        }

        public SaveResult storeAllergen(PatientAllergen allergen) {
            // Identical atomic policy for the historical single-item caller.
            return storeAllergens(Collections.singletonList(allergen));
        }

        public SaveResult storeAllergens(final Collection<PatientAllergen> allergens) {
            if (context == null || currentAllergies == null
                    || allergens == null || allergens.isEmpty()) {
                return SaveResult.ERROR;
            }
            Patient active = DB.patients().getActive(context);
            Long id = active == null ? null : active.getId();
            String code = active == null ? null : active.getCode();
            if (!AllergenSearchOwnerGuard.samePatient(
                    openedPatientId, openedPatientFingerprint, id, code)
                    || !AllergenSearchOwnerGuard.samePatient(
                            loadedPatientId, loadedPatientFingerprint, id, code)) {
                return SaveResult.ERROR;
            }
            final List<PatientAllergen> batch = new ArrayList<>(allergens);
            for (PatientAllergen candidate : batch) {
                if (candidate == null || candidate.getPatient() == null
                        || !AllergenSearchOwnerGuard.canStore(
                                loadedPatientId, loadedPatientFingerprint,
                                id, code, candidate.getPatient().getId(),
                                candidate.getPatient().getCode())) {
                    return SaveResult.ERROR;
                }
            }
            try {
                // The new helper owns the complete SQL transaction and
                // performs Android effects only after the final COMMIT.
                // Neither this activity nor the store may nest an outer
                // transaction around that call.
                es.usc.citius.servando.calendula.util.alerts.AtomicAllergenRegistration.Result
                        committed = es.usc.citius.servando.calendula.util.alerts
                        .AtomicAllergenRegistration.register(context, active, batch);
                // Do not append to an in-memory list inside SQLite: a
                // failed later INSERT must leave the UI ready for retry.
                reload();
                return committed.hasConflicts() ? SaveResult.ALLERGY : SaveResult.OK;
            } catch (es.usc.citius.servando.calendula.util.alerts
                    .AtomicAllergenRegistration.PostCommitRecoveryException committed) {
                // SQLite DID commit. Never claim the allergen INSERT failed
                // or invite a second write that could create duplicates.
                LogUtil.e(TAG, "Committed allergy registration needs reminder recovery");
                reload();
                return SaveResult.COMMITTED_RECOVERY_REQUIRED;
            } catch (RuntimeException failure) {
                LogUtil.e(TAG, "Allergy registration rolled back or requires review");
                reload();
                return SaveResult.ERROR;
            }
        }
    }

    private void deleteAllergyGroupAsync(final AllergyGroupItem item) {
        progressBar.setVisibility(View.VISIBLE);

        final List<AllergyGroupSubItem> subItems = new ArrayList<>(item.getSubItems());
        final List<PatientAllergen> allergens = new ArrayList<>(subItems.size());
        for (AllergyGroupSubItem subItem : subItems) {
            allergens.add(subItem.getAllergen());
        }

        ALLERGY_EXECUTOR.execute(() -> {
            final int outcome = store.deleteAllergens(allergens);
            // SQL may already have committed when post-COMMIT warning cleanup
            // fails. Refresh even on errors; never leave a phantom group row.
            store.reload();
            final List<AbstractItem> authoritativeItems = getAllergyItems();

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!isOriginalPatientActive()) {
                    finish();
                    return;
                }
                allergiesAdapter.set(authoritativeItems);
                checkPlaceholder();
                showAllergenDeletionOutcome(outcome);
                progressBar.setVisibility(View.GONE);
                hideAllergiesView(false);
            });
        });
    }

    private void deleteAllergyAsync(final AllergyItem item) {
        progressBar.setVisibility(View.VISIBLE);
        final PatientAllergen allergen = item.getAllergen();

        ALLERGY_EXECUTOR.execute(() -> {
            final int outcome = store.deleteAllergen(allergen, true);
            store.reload();
            final List<AbstractItem> authoritativeItems = getAllergyItems();

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!isOriginalPatientActive()) {
                    finish();
                    return;
                }
                allergiesAdapter.set(authoritativeItems);
                checkPlaceholder();
                showAllergenDeletionOutcome(outcome);
                progressBar.setVisibility(View.GONE);
                hideAllergiesView(false);
            });
        });
    }

    /** Distinguish an SQL rollback from a committed deletion needing recovery. */
    private void showAllergenDeletionOutcome(int outcome) {
        if (outcome == -3) {
            Snack.show(R.string.message_allergy_removed_recovery_required, this);
        } else if (outcome < 0) {
            Snack.show(R.string.delete_allergen_error, this);
        }
    }

    private void loadAllergiesAsync() {
        hideAllergiesView(true);
        progressBar.setVisibility(View.VISIBLE);

        ALLERGY_EXECUTOR.execute(() -> {
            store.load(AllergiesActivity.this);
            final List<AbstractItem> items = getAllergyItems();

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || !isOriginalPatientActive()) {
                    if (!isFinishing()) {
                        finish();
                    }
                    return;
                }
                allergiesAdapter.add(items);
                progressBar.setVisibility(View.GONE);
                checkPlaceholder();
                hideAllergiesView(false);
            });
        });
    }

    private void saveAllergiesAsync(final Collection<AllergenGroupWrapper> wrappers) {
        hideAllergiesView(true);
        progressBar.setVisibility(View.VISIBLE);
        final Collection<AllergenGroupWrapper> items = new ArrayList<>(wrappers);

        ALLERGY_EXECUTOR.execute(() -> {
            final Collection<PatientAllergen> patientAllergens = new ArrayList<>(items.size());
            final Patient patient = DB.patients().getActive(AllergiesActivity.this);
            if (!AllergenSearchOwnerGuard.samePatient(
                    openedPatientId, openedPatientFingerprint,
                    patient == null ? null : patient.getId(),
                    patient == null ? null : patient.getCode())) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) finish();
                });
                return;
            }
            for (AllergenGroupWrapper wrapper : items) {
                if (wrapper.getGroup() != null) {
                    patientAllergens.add(new PatientAllergen(
                            wrapper.getVo(), patient, wrapper.getGroup()));
                } else {
                    patientAllergens.add(new PatientAllergen(wrapper.getVo(), patient));
                }
            }

            SaveResult result;
            try {
                result = store.storeAllergens(patientAllergens);
            } catch (RuntimeException error) {
                LogUtil.e(TAG, "Unable to save patient allergens safely");
                result = SaveResult.ERROR;
            }
            final boolean saved = result == SaveResult.OK || result == SaveResult.ALLERGY
                    || result == SaveResult.COMMITTED_RECOVERY_REQUIRED;
            final boolean conflicts = result == SaveResult.ALLERGY;
            final boolean recovery = result == SaveResult.COMMITTED_RECOVERY_REQUIRED;
            final List<AbstractItem> allergyItems = getAllergyItems();

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (!isOriginalPatientActive()) {
                    finish();
                    return;
                }
                progressBar.setVisibility(View.GONE);
                if (saved) {
                    allergiesAdapter.set(allergyItems);
                    if (conflicts) {
                        showNewAllergyConflictDialog();
                    }
                    hideAllergiesView(false);
                    if (recovery) {
                        Snack.show(R.string.message_allergy_saved_recovery_required,
                                AllergiesActivity.this);
                    } else {
                        Snack.show(getString(R.string.message_allergy_add_multiple_success),
                                AllergiesActivity.this);
                    }
                } else {
                    Snack.show(R.string.message_allergy_add_failure, AllergiesActivity.this);
                }
            });
        });
    }


}
