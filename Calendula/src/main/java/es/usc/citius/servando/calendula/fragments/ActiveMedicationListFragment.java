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

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.Fragment;
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
    private static final Duration minUpdateWaitPeriod = Duration.standardMinutes(1);
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            final FragmentActivity activity = getActivity();
            if (activity != null) {
                getActivity().runOnUiThread(updateViewsRunnable);
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
            updateHeaderText();
            updateProgressBarVisibility();
        }
    };

    private FastItemAdapter<ActiveMedicationListItem> adapter;
    private Handler handler;
    private Unbinder unbinder;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_active_med_list, container, false);
        unbinder = ButterKnife.bind(this, rootView);

        updateHeaderText();

        handler = new Handler();

        handler.postDelayed(ticker, 1000);

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
        new ReloadItemsTask().execute();
    }

    @Override
    public void onStart() {
        super.onStart();
        handler.postDelayed(ticker, 1000);
        CalendulaApp.eventBus().register(this);
    }

    @Override
    public void onStop() {
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
        LogUtil.v(TAG, "Received active med event: " + event.toString());
        getActivity().runOnUiThread(updateViewsRunnable);
    }


    public void updateProgressBarVisibility() {
        if (refreshLayout != null) {
            final String statusString = PreferenceUtils.getString(PreferenceKeys.REMOTE_UPDATE_STATUS, "");
            if (!TextUtils.isEmpty(statusString) && ActiveMedUpdateEvent.Status.valueOf(statusString) == ActiveMedUpdateEvent.Status.UPDATE_START) {
                refreshLayout.setRefreshing(true);
            } else {
                refreshLayout.setRefreshing(false);
            }
        }
    }

    private void updateMedication() {
        final String lastGoodUpdate = PreferenceUtils.getString(PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_DATE, "");
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
                    ((HomePagerActivity) getActivity()).makeSnackbar(getString(R.string.wait_until_reload, waitStr.toLowerCase()), Snackbar.LENGTH_LONG).show();
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
        String last = PreferenceUtils.getString(PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_DATE, null);
        if (last != null) {
            DateTime t = DateTime.parse(last);
            DateTime now = DateTime.now();
            String relativeTimeStr = DateUtils.getRelativeTimeSpanString(
                    t.getMillis(),
                    now.getMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_ALL).toString();
            timeSinceUpdateText.setText(getString(R.string.active_med_last_checked_time, relativeTimeStr));
        } else {
            timeSinceUpdateText.setText(getString(R.string.active_med_not_checked_yet));
        }
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
                final ActiveMedVO vo = item.getVo();

                Medicine m = DB.medicines().findOneBy(Medicine.COLUMN_ACTIVE_MED_ID, vo.getBackingEntity().getId());

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
                Intent intent;
                Schedule s;
                Long id;
                switch (v.getId()) {
                    case R.id.enable_reminders_button:
                        id = item.getVo().getBackingEntity().getId();
                        s = DB.schedules().findOneBy(Schedule.BOUND_TO, id);
                        intent = new Intent(getContext(), ScheduleBuildActivity.class);
                        if (s != null) {
                            intent.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID, s.getId());
                        } else {
                            intent.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_CREATE_REMINDER);
                            intent.putExtra(IntentParams.EXTRA_ACTIVEMED_ID, id);
                        }
                        getContext().startActivity(intent);
                        break;
                    case R.id.get_info_button:
                        intent = new Intent(getContext(), PdfViewActivity.class);

                        WebViewRequest request = new WebViewRequest(null);
                        request.setConnectionErrorMessage(getContext().getString(R.string.message_extrainfo_connection_error));
                        request.setNotFoundErrorMessage(getContext().getString(R.string.message_extrainfo_not_found_error));
                        request.setLoadingMessage(getContext().getString(R.string.message_extrainfo_loading));
                        request.setTitle(getContext().getString(R.string.title_extrainfo_webview));
                        request.setCacheType(WebViewRequest.CacheType.NO_CACHE);
                        request.setJavaScriptEnabled(false);
                        request.setExternalLinksEnabled(false);

                        intent.putExtra(PdfViewActivity.PARAM_PDFVIEW_REQUEST, request);
                        getContext().startActivity(intent);

                        break;
                }
            }
        });


        recyclerView.setAdapter(adapter);
        new ReloadItemsTask().execute();
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

    private class ReloadItemsTask extends AsyncTask<Void, Void, List<ActiveMedicationListItem>> {

        @Override
        protected List<ActiveMedicationListItem> doInBackground(Void... params) {
            LogUtil.d(TAG, "Reloading items...");
            final List<ActiveMedEntity> entities = DB.healthcareProviderDB().activeMeds().findBy(ActiveMedEntity.COLUMN_PATIENT, DB.patients().getActive(getContext()));
            final List<ActiveMedicationListItem> items = new ArrayList<>(entities.size());
            Collections.sort(items, amComparator);
            for (ActiveMedEntity entity : entities) {
                ActiveMedVO vo = ActiveMedVO.forEntity(entity);
                items.add(new ActiveMedicationListItem(vo));
            }
            return items;
        }

        @Override
        protected void onPostExecute(List<ActiveMedicationListItem> items) {
            LogUtil.d(TAG, "Reloaded items, count: " + items.size());
            adapter.setNewList(items);
            checkPlaceholder();
        }
    }


    private Comparator<ActiveMedicationListItem> amComparator = new Comparator<ActiveMedicationListItem>() {
        @Override
        public int compare(ActiveMedicationListItem o1, ActiveMedicationListItem o2) {
            if (ActiveMedEntity.ActiveMedState.INACTIVE.equals(o1.getVo().getState())) {
                return 1;
            } else if (ActiveMedEntity.ActiveMedState.INACTIVE.equals(o2.getVo().getState())) {
                return -1;
            } else {
                return o1.getVo().getLastUpdated().compareTo(o2.getVo().getLastUpdated());
            }
        }
    };

}