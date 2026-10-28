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

package es.usc.citius.servando.calendula.fragments;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IAdapter;
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter;
import com.mikepenz.fastadapter.listeners.OnClickListener;
import com.mikepenz.fastadapter.listeners.OnLongClickListener;

import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.adapters.items.ScheduleListItem;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.LogUtil;

public class ScheduleListFragment extends Fragment {

    private static final String TAG = "ScheduleListFragment";
    private static final ExecutorService RELOAD_EXECUTOR = Executors.newSingleThreadExecutor();
    // UI results from a previous profile or a destroyed fragment must never
    // become visible merely because its background database query finished.
    private final AtomicLong reloadGeneration = new AtomicLong();
    private Long displayedPatientId;

    List<Schedule> mSchedules;
    OnScheduleSelectedListener mScheduleSelectedCallback;

    FastItemAdapter<ScheduleListItem> adapter;

    @BindView(R.id.schedule_list)
    RecyclerView recyclerView;
    @BindView(android.R.id.empty)
    View empty;

    Unbinder unbinder;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_schedule_list, container, false);
        unbinder = ButterKnife.bind(this, rootView);

        Patient active = DB.patients().getActive(getContext());
        List<Schedule> initial = DB.schedules().findAllForActivePatient(getContext());
        boolean safe = ownsEverySchedule(active, initial);
        mSchedules = safe ? initial : new ArrayList<>();
        displayedPatientId = safe ? active.getId() : null;
        if (mSchedules.size() > 0)
            empty.setVisibility(View.GONE);
        setupRecyclerView();

        return rootView;
    }

    @Override
    public void onDestroyView() {
        reloadGeneration.incrementAndGet();
        displayedPatientId = null;
        super.onDestroyView();
        if (unbinder != null)
            unbinder.unbind();
    }

    public void notifyDataChange() {
        LogUtil.d(TAG, "Schedules - Notify data change");
        reloadItemsAsync();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        LogUtil.d(TAG, "Activity "
                + context.getClass().getName()
                + ", "
                + (context instanceof OnScheduleSelectedListener));
        // If the container activity has implemented
        // the callback interface, set it as listener
        if (context instanceof OnScheduleSelectedListener) {
            mScheduleSelectedCallback = (OnScheduleSelectedListener) context;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        CalendulaApp.eventBus().register(this);
        Context context = getContext();
        Patient selected = context == null ? null : DB.patients().getActive(context);
        if (selected == null || selected.getId() == null
                || !selected.getId().equals(displayedPatientId)
                || !ownsEverySchedule(selected, mSchedules)) {
            clearVisibleSchedules();
        }
        reloadItemsAsync();
    }

    @Override
    public void onStop() {
        reloadGeneration.incrementAndGet();
        CalendulaApp.eventBus().unregister(this);
        super.onStop();
    }

    // Method called from the event bus
    @SuppressWarnings("unused")
    @Subscribe
    public void handleActiveUserChange(final PersistenceEvents.ActiveUserChangeEvent event) {
        // May be delivered off the main thread; clear the old profile's
        // visible clinical rows before scheduling the next query.
        reloadGeneration.incrementAndGet();
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            clearVisibleSchedules();
            reloadItemsAsync();
        });
    }

    private void clearVisibleSchedules() {
        mSchedules = new ArrayList<>();
        displayedPatientId = null;
        if (adapter != null) {
            adapter.clear();
        }
        if (empty != null) {
            empty.setVisibility(View.VISIBLE);
        }
    }

    /** Never apply a mixed-owner or an obsolete background schedule snapshot. */
    static boolean ownsEverySchedule(Patient expected, List<Schedule> rows) {
        if (expected == null || expected.getId() == null || expected.getId() <= 0L
                || rows == null) {
            return false;
        }
        for (Schedule schedule : rows) {
            if (!canReviewDeleteForActivePatient(schedule, expected)) {
                return false;
            }
        }
        return true;
    }

    static boolean mayApplyScheduleReload(long requestedGeneration,
                                          long currentGeneration, Patient requested,
                                          Patient currentlySelected, List<Schedule> rows) {
        return requestedGeneration == currentGeneration
                && requested != null && currentlySelected != null
                && requested.getId() != null && requested.getId() > 0L
                && requested.getId().equals(currentlySelected.getId())
                && ownsEverySchedule(requested, rows);
    }

    /** Prevent displaying a stale patient's schedule details at dialog opening. */
    static boolean canReviewDeleteForActivePatient(Schedule schedule,
                                                    es.usc.citius.servando.calendula.persistence.Patient active) {
        return schedule != null && schedule.getId() != null && schedule.getId() > 0L
                && schedule.getPatient() != null && schedule.getPatient().getId() != null
                && active != null && active.getId() != null && active.getId() > 0L
                && active.getId().equals(schedule.getPatient().getId());
    }

    void showDeleteConfirmationDialog(final Schedule s) {
        Context context = getContext();
        if (context == null || !canReviewDeleteForActivePatient(
                s, DB.patients().getActive(context))) {
            // Do not show another profile's medication name after a switch.
            // The SQL path repeats this check when confirmation is pressed.
            LogUtil.w(TAG, "Refusing stale schedule deletion dialog");
            return;
        }
        String medicineName = s.getMedicine() == null || s.getMedicine().getName() == null
                ? getString(R.string.not_available_short) : s.getMedicine().getName();
        new MaterialStyledDialog.Builder(getActivity())
                .setStyle(Style.HEADER_WITH_ICON)
                .setIcon(IconUtils.icon(getActivity(), CommunityMaterial.Icon.cmd_calendar, R.color.white, 100))
                .setHeaderColor(R.color.android_red)
                .withDialogAnimation(true)
                .setTitle(getString(R.string.remove_reminder_dialog_title))
                .setDescription(String.format(getString(R.string.remove_reminder_message), medicineName))
                .setCancelable(true)
                .setNeutralText(getString(R.string.dialog_no_option))
                .setPositiveText(getString(R.string.dialog_yes_option))
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        final Context currentContext = getContext();
                        if (currentContext == null) {
                            return;
                        }
                        try {
                            ScheduleUtils.instance().removeVerifiedActiveSchedule(
                                    currentContext, s, true);
                        } catch (RuntimeException noLongerValid) {
                            // A stale/deleted schedule must never crash the
                            // clinical UI or claim a successful deletion.
                            LogUtil.w(TAG, "Schedule deletion rejected; refresh required");
                            Toast.makeText(currentContext, R.string.reminder_delete_failed,
                                    Toast.LENGTH_LONG).show();
                            notifyDataChange();
                            return;
                        }
                        notifyDataChange();
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

    private void setupRecyclerView() {
        LinearLayoutManager llm = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(llm);

        adapter = new FastItemAdapter<>();
        adapter.withSelectable(false);
//        adapter.withPositionBasedStateManagement(false);
        for (Schedule schedule : mSchedules) {
            adapter.add(new ScheduleListItem(schedule));
        }
        adapter.withOnClickListener(new OnClickListener<ScheduleListItem>() {
            @Override
            public boolean onClick(View v, IAdapter<ScheduleListItem> adapter, ScheduleListItem item, int position) {
                Schedule s = item.getSchedule();
                Context context = getContext();
                Patient active = context == null ? null : DB.patients().getActive(context);
                if (!canReviewDeleteForActivePatient(s, active)) {
                    LogUtil.w(TAG, "Ignoring schedule selection for a stale patient");
                    return true;
                }
                if (mScheduleSelectedCallback != null) {
                    LogUtil.d(TAG, "Schedule selected");
                    mScheduleSelectedCallback.onScheduleSelected(s);
                } else {
                    LogUtil.d(TAG, "No callback set");
                }
                return true;
            }
        });
        adapter.withOnLongClickListener(new OnLongClickListener<ScheduleListItem>() {
            @Override
            public boolean onLongClick(View v, IAdapter<ScheduleListItem> adapter, ScheduleListItem item, int position) {
                showDeleteConfirmationDialog(item.getSchedule());
                return true;
            }
        });
        recyclerView.setAdapter(adapter);
    }

    // Container Activity must implement this interface
    public interface OnScheduleSelectedListener {
        void onScheduleSelected(Schedule r);

        void onCreateSchedule();
    }

    private void reloadItemsAsync() {
        final Context context = getContext();
        if (context == null) {
            return;
        }
        final Context applicationContext = context.getApplicationContext();
        final Patient requested = DB.patients().getActive(applicationContext);
        final long generation = reloadGeneration.incrementAndGet();
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        RELOAD_EXECUTOR.execute(() -> {
            final List<Schedule> schedules;
            try {
                schedules = DB.schedules().findAllForActivePatient(applicationContext);
            } catch (RuntimeException unavailable) {
                LogUtil.w(TAG, "Schedule reload could not verify active patient");
                mainHandler.post(() -> {
                    if (generation == reloadGeneration.get() && isAdded()
                            && getView() != null) {
                        clearVisibleSchedules();
                    }
                });
                return;
            }
            mainHandler.post(() -> {
                if (!isAdded() || getView() == null || generation != reloadGeneration.get()) {
                    return;
                }
                final Patient now = DB.patients().getActive(applicationContext);
                if (!mayApplyScheduleReload(generation, reloadGeneration.get(),
                        requested, now, schedules)) {
                    // Profile switched or a cross-owned legacy row appeared
                    // while SQLite was being queried: never render any subset.
                    clearVisibleSchedules();
                    return;
                }
                displayedPatientId = requested.getId();
                mSchedules = schedules;
                empty.setVisibility(mSchedules.isEmpty() ? View.VISIBLE : View.GONE);
                adapter.clear();
                for (Schedule schedule : mSchedules) {
                    adapter.add(new ScheduleListItem(schedule));
                }
                adapter.notifyAdapterDataSetChanged();
            });
        });
    }

}