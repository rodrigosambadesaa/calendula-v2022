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

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IAdapter;
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.listeners.OnClickListener;

import org.greenrobot.eventbus.Subscribe;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.HomePagerActivity;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.MedicineInfoActivity;
import es.usc.citius.servando.calendula.activities.PdfViewActivity;
import es.usc.citius.servando.calendula.activities.WebViewRequest;
import es.usc.citius.servando.calendula.activities.schedules.ScheduleBuildActivity;
import es.usc.citius.servando.calendula.adapters.items.ActiveMedicationListItem;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.events.ActiveMedUpdateEvent;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.healthcareprovider.jobs.UpdateMedicationFromServiceJob;
import es.usc.citius.servando.calendula.healthcareprovider.jobs.UpdateMedicationHelper;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

public class ActiveMedicationListFragment extends Fragment {

    private static final String TAG = "MedicinesListFragment";
    private static final ExecutorService RELOAD_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Duration minUpdateWaitPeriod = Duration.standardMinutes(1);
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (isAdded() && getView() != null && handler != null) {
                updateViewsRunnable.run();
                // Header text is minute-granularity: do not run a repeated
                // one-second callback (nor retain a destroyed fragment).
                handler.postDelayed(this, 60_000L);
            }
        }
    };
    @BindView(R.id.active_med_list)
    RecyclerView recyclerView;
    @BindView(android.R.id.empty)
    View emptyView;
    @BindView(R.id.active_med_list_header)
    TextView headerText;
    @BindView(R.id.active_med_list_time_since_update)
    TextView timeSinceUpdateText;
    @BindView(R.id.swipeRefreshLayout)
    SwipeRefreshLayout refreshLayout;
    private final Runnable updateViewsRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded() && getView() != null) {
                updateHeaderText();
                updateProgressBarVisibility();
            }
        }
    };

    private FastItemAdapter<ActiveMedicationListItem> adapter;
    private final AtomicLong reloadGeneration = new AtomicLong();
    private Long displayedPatientId;
    private Handler handler;
    private Unbinder unbinder;

    /**
     * Recheck the displayed owner, active profile and persisted provider
     * record when a user interacts with a medical card, even if a profile
     * change notification has not arrived yet.
     */
    private boolean canOpenProviderMedicine(ActiveMedicationListItem item) {
        if (item == null || item.getVo() == null
                || item.getVo().getBackingEntity() == null || getContext() == null) {
            return false;
        }
        ActiveMedEntity requested = item.getVo().getBackingEntity();
        if (requested.getId() == null || requested.getPatient() == null) {
            return false;
        }
        Patient active = DB.patients().getActive(getContext());
        ActiveMedEntity persisted = DB.healthcareProviderDB().activeMeds()
                .findById(requested.getId());
        if (persisted == null || persisted.getPatient() == null) {
            return false;
        }
        Long activeId = active == null ? null : active.getId();
        return ActiveMedicationOwnerGuard.matchingPatient(
                        displayedPatientId, activeId, requested.getPatient().getId())
                && ActiveMedicationOwnerGuard.matchingPatient(
                        displayedPatientId, activeId, persisted.getPatient().getId());
    }


    /**
     * Re-query and revalidate on every click, including dialog selection:
     * profile switches and provider updates can invalidate a displayed card.
     */
    private void openVerifiedReminder(ActiveMedicationListItem item, @Nullable Long chosenScheduleId) {
        if (!canOpenProviderMedicine(item) || getContext() == null) {
            notifyDataChange();
            return;
        }
        final Context context = getContext();
        final ActiveMedEntity provider = item.getVo().getBackingEntity();
        final Patient patient = DB.patients().getActive(context);
        if (patient == null || provider.getPatient() == null) {
            notifyDataChange();
            return;
        }
        final List<Schedule> links = DB.schedules().findBy(Schedule.BOUND_TO, provider.getId());
        final OfficialReminderLinkSelector.Selection selection =
                OfficialReminderLinkSelector.inspect(
                        displayedPatientId, patient.getId(),
                        provider.getPatient().getId(), links);

        if (selection.action == OfficialReminderLinkSelector.Action.UNAVAILABLE) {
            Toast.makeText(context, R.string.aml_reminder_unavailable, Toast.LENGTH_LONG).show();
            notifyDataChange();
            return;
        }

        if (chosenScheduleId != null) {
            for (Schedule schedule : selection.schedules) {
                if (chosenScheduleId.equals(schedule.getId())) {
                    Intent edit = new Intent(context, ScheduleBuildActivity.class);
                    edit.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID, chosenScheduleId);
                    startActivity(edit);
                    return;
                }
            }
            notifyDataChange();
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
                            openVerifiedReminder(item, selection.schedules.get(which).getId());
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        Intent intent = new Intent(context, ScheduleBuildActivity.class);
        if (selection.action == OfficialReminderLinkSelector.Action.EDIT) {
            intent.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID,
                    selection.schedules.get(0).getId());
        } else {
            intent.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_CREATE_REMINDER);
            intent.putExtra(IntentParams.EXTRA_ACTIVEMED_ID, provider.getId());
        }
        startActivity(intent);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_active_med_list, container, false);
        unbinder = ButterKnife.bind(this, rootView);

        updateHeaderText();

        handler = new Handler(Looper.getMainLooper());

        updateProgressBarVisibility();

        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                updateMedication();
            }
        });

        setupRecyclerView();
        return rootView;
    }

    @Override
    public void onDestroyView() {
        reloadGeneration.incrementAndGet();
        displayedPatientId = null;
        if (handler != null) {
            handler.removeCallbacks(ticker);
            handler.removeCallbacks(updateViewsRunnable);
        }
        super.onDestroyView();
        if (unbinder != null)
            unbinder.unbind();
    }

    public void refresh() {
        refreshLayout.post(new Runnable() {
            @Override
            public void run() {
//                refreshLayout.setRefreshing(true);
                updateMedication();
            }
        });
    }

    public void notifyDataChange() {
        LogUtil.d(TAG, "Active med list - Notify data change");
        reloadItemsAsync();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (handler != null) {
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }
        CalendulaApp.eventBus().register(this);
        // onStop cancels pending generations; onStart must refresh again.
        reloadItemsAsync();
    }

    @Override
    public void onStop() {
        reloadGeneration.incrementAndGet();
        if (handler != null) {
            handler.removeCallbacks(ticker);
            handler.removeCallbacks(updateViewsRunnable);
        }
        CalendulaApp.eventBus().unregister(this);
        super.onStop();
    }

    // Method called from the event bus
    @SuppressWarnings("unused")
    @Subscribe
    public void handleActiveUserChange(final PersistenceEvents.ActiveUserChangeEvent event) {
        notifyDataChange();
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void handleModelUpdateEvent(final PersistenceEvents.ModelCreateOrUpdateEvent event) {
        if (event.clazz.equals(ActiveMedEntity.class)) {
            notifyDataChange();
        }
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void handleActiveMedUpdate(final ActiveMedUpdateEvent event) {
        LogUtil.v(TAG, "Received provider medication update");
        final FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (isAdded() && getView() != null) {
                    updateViewsRunnable.run();
                }
            });
        }
    }


    public void updateProgressBarVisibility() {
        if (refreshLayout != null) {
            final String statusString = ProviderPreferenceReader.read(PreferenceKeys.REMOTE_UPDATE_STATUS, "");
            if (ActiveMedUpdateEvent.Status.UPDATE_START.name().equals(statusString)) {
                refreshLayout.setRefreshing(true);
            } else {
                refreshLayout.setRefreshing(false);
            }
        }
    }

    private void updateMedication() {
        final String lastGoodUpdate = ProviderPreferenceReader.read(PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_DATE, "");
        if (lastGoodUpdate.equals("")) {
            LogUtil.d(TAG, "updateMedication: no last good update. Scheduling now");
            UpdateMedicationFromServiceJob.scheduleOneShot(false);
            return;
        }

        try {
            final DateTime lastGoodUpdateTime = DateTime.parse(lastGoodUpdate);
            Duration diff = new Duration(lastGoodUpdateTime, DateTime.now());

            if (diff.getMillis() <= 0) {
                LogUtil.e(TAG, "Invalid time AML update check interval. Duration =" + diff.getMillis() + "ms");
                UpdateMedicationHelper.notifyStatusUpdate(ActiveMedUpdateEvent.Status.ERROR_GENERIC);
            } else {
                // skip wait in debug builds
                if (diff.isShorterThan(minUpdateWaitPeriod)) {
                    final Duration d = minUpdateWaitPeriod.minus(diff);
                    final String waitStr = DateUtils.getRelativeTimeSpanString(d.getMillis(), 0, DateUtils.SECOND_IN_MILLIS).toString();
                    ((HomePagerActivity) getActivity()).makeSnackbar(getString(R.string.wait_until_reload, waitStr.toLowerCase(Locale.getDefault())), Snackbar.LENGTH_LONG).show();
                    refreshLayout.setRefreshing(false);
                } else {
                    UpdateMedicationFromServiceJob.scheduleOneShot(false);
                }
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "updateMedication: ", e);
            UpdateMedicationFromServiceJob.scheduleOneShot(false);
        }
    }

    private void updateViewTimer() {
        updateHeaderText();
        handler.postDelayed(ticker, 1000);
    }

    @UiThread
    private void updateHeaderText() {
        if (timeSinceUpdateText == null) {
            return;
        }
        String last = ProviderPreferenceReader.read(
                PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_DATE, null);
        if (!TextUtils.isEmpty(last)) {
            try {
                DateTime date = DateTime.parse(last);
                DateTime now = DateTime.now();
                String relativeTime = DateUtils.getRelativeTimeSpanString(
                        date.getMillis(), now.getMillis(),
                        DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_ALL)
                        .toString();
                timeSinceUpdateText.setText(
                        getString(R.string.active_med_last_checked_time, relativeTime));
                return;
            } catch (IllegalArgumentException malformed) {
                LogUtil.w(TAG, "Ignoring malformed provider update timestamp");
            }
        }
        timeSinceUpdateText.setText(R.string.active_med_not_checked_yet);
    }

    private void setupRecyclerView() {
        LinearLayoutManager llm = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(llm);
        adapter = new FastItemAdapter<>();
        adapter.withSelectable(false);
//        adapter.withPositionBasedStateManagement(false);

        adapter.withOnClickListener(new OnClickListener<ActiveMedicationListItem>() {
            @Override
            public boolean onClick(View v, IAdapter<ActiveMedicationListItem> adapter, ActiveMedicationListItem item, int position) {
                if (!canOpenProviderMedicine(item)) {
                    notifyDataChange();
                    return true;
                }
                final ActiveMedVO vo = item.getVo();
                Medicine m = DB.medicines().findOneBy(
                        Medicine.COLUMN_ACTIVE_MED_ID, vo.getBackingEntity().getId());
                final Patient activePatient = DB.patients().getActive(getContext());
                if (m != null && (m.getPatient() == null || activePatient == null
                        || !ActiveMedicationOwnerGuard.matchingPatient(
                            displayedPatientId, activePatient.getId(),
                            m.getPatient().getId()))) {
                    m = null;
                }

                Intent i = new Intent(getActivity(), MedicineInfoActivity.class);
                i.setAction(MedicineInfoActivity.ACTION_SHOW_ONLY_ACTIVE_MED);
                if (m != null) {
                    i.putExtra(CalendulaApp.INTENT_EXTRA_MEDICINE_ID, m.getId());
                }
                i.putExtra(MedicineInfoActivity.EXTRA_ACTIVE_MED, vo.getCode());
                startActivity(i);
                return true;
            }
        });

        adapter.withEventHook(new ClickEventHook<ActiveMedicationListItem>() {
            @Nullable
            @Override
            public View onBind(@NonNull RecyclerView.ViewHolder viewHolder) {
                return null;
            }

            @Nullable
            @Override
            public List<View> onBindMany(@NonNull RecyclerView.ViewHolder viewHolder) {
                ActiveMedicationListItem.ActiveMedicationViewHolder holder = (ActiveMedicationListItem.ActiveMedicationViewHolder) viewHolder;
                List<View> views = new ArrayList<>();
                views.add(holder.reminderButton);
                views.add(holder.infoButton);
                return views;
            }

            @Override
            public void onClick(View v, int position, FastAdapter<ActiveMedicationListItem> fastAdapter, ActiveMedicationListItem item) {
                if (!canOpenProviderMedicine(item)) {
                    notifyDataChange();
                    return;
                }
                Intent intent;
                switch (v.getId()) {
                    case R.id.enable_reminders_button:
                        openVerifiedReminder(item, null);
                        break;
                    case R.id.get_info_button:
                        intent = new Intent(getContext(), PdfViewActivity.class);

                        WebViewRequest request = new WebViewRequest(null);
                        request.setConnectionErrorMessage(getContext().getString(R.string.message_extrainfo_connection_error));
                        request.setNotFoundErrorMessage(getContext().getString(R.string.message_extrainfo_not_found_error));
                        request.setLoadingMessage(getContext().getString(R.string.message_extrainfo_loading));
                        request.setTitle(getContext().getString(R.string.title_extrainfo_webview));
                        request.setCacheType(WebViewRequest.CacheType.NO_CACHE);
                        request.setJavaScriptAllowed(false);
                        request.setExternalLinksEnabled(false);

                        intent.putExtra(PdfViewActivity.PARAM_PDFVIEW_REQUEST, request);
                        getContext().startActivity(intent);

                        break;
                }
            }
        });


        recyclerView.setAdapter(adapter);
        reloadItemsAsync();
    }


    private void checkPlaceholder() {
        if (emptyView != null) {
            if (adapter.getItemCount() > 0) { // just in case the reload task is called before the activity is loaded
                emptyView.setVisibility(View.GONE);
            } else {
                emptyView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void reloadItemsAsync() {
        final long requestedVersion = reloadGeneration.incrementAndGet();
        final Context context = getContext();
        if (context == null) {
            return;
        }
        final Context applicationContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        final Patient active = DB.patients().getActive(applicationContext);
        final Long ownerId = active == null ? null : active.getId();
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        mainHandler.post(() -> {
            if (!isAdded() || getView() == null || adapter == null
                    || requestedVersion != reloadGeneration.get()) {
                // An obsolete reload must not clear cards populated by a
                // newer request for the current patient.
                return;
            }
            if (displayedPatientId != null && !displayedPatientId.equals(ownerId)) {
                // Do not leave the previous patient's medical data displayed
                // while a potentially slow replacement query is in flight.
                adapter.setNewList(new ArrayList<>());
                displayedPatientId = null;
                checkPlaceholder();
            }
        });

        RELOAD_EXECUTOR.execute(() -> {
            final List<ActiveMedicationListItem> items = new ArrayList<>();
            if (ownerId != null && ownerId > 0L) {
                try {
                    final List<ActiveMedEntity> entities =
                            DB.healthcareProviderDB().activeMeds().findBy(
                                    ActiveMedEntity.COLUMN_PATIENT, active);
                    if (entities != null) {
                        for (ActiveMedEntity entity : entities) {
                            if (entity != null && entity.getPatient() != null
                                    && ownerId.equals(entity.getPatient().getId())) {
                                final ActiveMedVO vo = ActiveMedVO.forEntity(entity);
                                if (vo != null) {
                                    items.add(new ActiveMedicationListItem(vo));
                                }
                            }
                        }
                    }
                    Collections.sort(items, amComparator);
                } catch (RuntimeException error) {
                    // Never publish a partial set from an invalid/failed
                    // clinical query or retain cards from another patient.
                    LogUtil.e(TAG, "Unable to load verified provider medicines", error);
                    items.clear();
                }
            }
            mainHandler.post(() -> {
                if (!isAdded() || getView() == null || adapter == null) {
                    return;
                }
                final Patient nowActive = DB.patients().getActive(applicationContext);
                final Long nowOwnerId = nowActive == null ? null : nowActive.getId();
                if (!ActiveMedicationOwnerGuard.canApply(
                        requestedVersion, reloadGeneration.get(), ownerId, nowOwnerId)) {
                    return;
                }
                adapter.setNewList(items);
                displayedPatientId = ownerId;
                checkPlaceholder();
            });
        });
    }


    private final Comparator<ActiveMedicationListItem> amComparator =
            new Comparator<ActiveMedicationListItem>() {
        @Override
        public int compare(ActiveMedicationListItem first, ActiveMedicationListItem second) {
            if (first == second) {
                return 0;
            }
            boolean firstMissing = first == null || first.getVo() == null;
            boolean secondMissing = second == null || second.getVo() == null;
            if (firstMissing || secondMissing) {
                // Comparing two invalid/empty legacy rows must also return 0.
                return Boolean.compare(firstMissing, secondMissing);
            }
            final ActiveMedVO left = first.getVo();
            final ActiveMedVO right = second.getVo();
            return ActiveMedicationOrdering.compare(
                    ActiveMedEntity.ActiveMedState.INACTIVE.equals(left.getState()),
                    ActiveMedEntity.ActiveMedState.INACTIVE.equals(right.getState()),
                    left.getLastUpdated() == null ? null : left.getLastUpdated().getMillis(),
                    right.getLastUpdated() == null ? null : right.getLastUpdated().getMillis());
        }
    };

}