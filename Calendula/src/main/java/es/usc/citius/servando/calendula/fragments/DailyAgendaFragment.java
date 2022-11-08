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

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.fragment.app.Fragment;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.IIcon;

import org.greenrobot.eventbus.Subscribe;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.ISODateTimeFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.DailyAgendaRecyclerAdapter;
import es.usc.citius.servando.calendula.HomePagerActivity;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.ConfirmActivity;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.DosageEntryVO;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.util.DailyAgendaItemStub;
import es.usc.citius.servando.calendula.util.DailyAgendaItemStub.DailyAgendaItemStubElement;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.HealthcareProviderTypeface;

/**
 * Daily agenda fragment
 */
public class DailyAgendaFragment extends Fragment {

    private static final String TAG = "DailyAgendaFragment";
    View emptyView;

    LinearLayoutManager llm;

    RecyclerView rv;
    DailyAgendaRecyclerAdapter rvAdapter;
    DailyAgendaRecyclerListener rvListener;

    List<DailyAgendaItemStub> items = new ArrayList<>();

    IIcon emptyViewIcon = IconUtils.randomNiceIcon();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        items = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_daily_agenda, container, false);
        rv = (RecyclerView) rootView.findViewById(R.id.rv);
        emptyView = rootView.findViewById(R.id.empty_view_placeholder);
        CalendulaApp.eventBus().register(this);
        setupRecyclerView();
        setupEmptyView();

        boolean expanded = PreferenceUtils.getBoolean(PreferenceKeys.HOME_DAILYAGENDA_EXPANDED, false);
        if (expanded != isExpanded()) {
            toggleViewMode();
            ((HomePagerActivity) getActivity()).appBarLayout.setExpanded(!expanded);
        }

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        CalendulaApp.eventBus().unregister(this);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        notifyDataChange();

    }

    public List<DailyAgendaItemStub> buildItems() {

        Drawable providerDecorator = new IconicsDrawable(getContext())
                .icon(HealthcareProviderTypeface.Icon.hcp_healthcare_provider)
                .backgroundColorRes(R.color.white)
                .colorRes(R.color.healthcare_provider_dark)
                .paddingDp(0)
                .sizeDp(30);

        List<DailyAgendaItemStub> stubs = new ArrayList<>();
        Map<DateTime, List<EventInstance>> grouped = new HashMap<>();

        for (EventInstance evt : DB.eventInstances().findByType(EventType.MEDICATION_INTAKE)) {
            if (!grouped.containsKey(evt.getTime())) {
                grouped.put(evt.getTime(), new ArrayList<EventInstance>());
            }
            grouped.get(evt.getTime()).add(evt);
        }

        for (DateTime dateTime : grouped.keySet()) {
            Map<Long, DailyAgendaItemStub> patStubs = new HashMap<>();
            List<EventInstance> eventInstances = grouped.get(dateTime);
            LocalTime time = dateTime.toLocalTime();
            LocalDate date = dateTime.toLocalDate();
            for (EventInstance e : eventInstances) {

                DailyAgendaItemStub stub;
                Long scheduleId = e.getRef();
                Schedule s = DB.schedules().findById(scheduleId);
                Patient p = s.getPatient();

                if (patStubs.containsKey(p.getId())) {
                    stub = patStubs.get(p.getId());
                } else {
                    stub = new DailyAgendaItemStub(date, time);
                    stub.title = ScheduleUtils.instance().getIntakeTitle(p, time, getContext());
                    stub.hasEvents = true;
                    stub.patient = p;
                    patStubs.put(p.getId(), stub);
                }

                DailyAgendaItemStubElement el = new DailyAgendaItemStubElement();
                el.medName = s.getMedicine().getName();
                el.dose = e.getDoubleParam(EventInstance.PARAM_DOSE);
                el.displayDose = "Display " + el.dose;
                el.presentation = s.getMedicine().getPresentation();
                el.minute = time.toString("mm");
                el.taken = e.completed();
                el.eventId = e.getId();
                if (s.isBoundToActiveMed()) {
                    ActiveMedEntity am = DB.healthcareProviderDB().activeMeds().findById(s.getActiveMedId());
                    ActiveMedVO vo = ActiveMedVO.forEntity(am);
                    List<DosageEntryVO> entries = vo.getDosage().getEntries();
                    if (entries == null || entries.size()==0) {
                        el.instructions = null;
                    }
                    else {
                        String instructions = vo.getDosage().getEntries().get(0).getPatientInstruction();
                        if (instructions == null || org.apache.commons.lang3.StringUtils.isBlank(instructions)) {
                            el.instructions = null;
                        } else {
                            el.instructions = getInfoMessage(getContext().getString(R.string.instructions_prefix) + " " + instructions, CommunityMaterial.Icon2.cmd_medical_bag, getContext());
                        }
                    }
                }
                else el.instructions = null;
                if (s.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)) {
                    el.medNameDecorator = providerDecorator;
                }
                stub.meds.add(el);
            }
            LogUtil.d(TAG, "Adding " + patStubs.size() + " stubs to the agenda");
            stubs.addAll(patStubs.values());
        }

        addEmptyHours(stubs, DateTime.now().minusDays(1), DateTime.now().withTimeAtStartOfDay().plusDays(1));
        Collections.sort(stubs, DailyAgendaItemStubComparator.instance);
        return stubs;
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

    public void addEmptyHours(List<DailyAgendaItemStub> stubs, DateTime min, DateTime max) {

        min = min.withTimeAtStartOfDay();
        max = max.withTimeAtStartOfDay().plusDays(1); // end of the day

        // add empty hours if there is not an item with the same hour
        for (DateTime start = min; start.isBefore(max); start = start.plusHours(1)) {

            boolean exact = false;
            for (DailyAgendaItemStub item : stubs) {
                if (start.equals(item.dateTime())) {
                    exact = true;
                    break;
                }
            }

            Interval hour = new Interval(start, start.plusHours(1));
            if (!exact || hour.contains(DateTime.now())) {
                stubs.add(new DailyAgendaItemStub(start.toLocalDate(), start.toLocalTime()));
            }

            if (start.getHourOfDay() == 0) {
                DailyAgendaItemStub spacer = new DailyAgendaItemStub(start.toLocalDate(), start.toLocalTime());
                spacer.isSpacer = true;
                stubs.add(spacer);
            }
        }
    }

    public void showOrHideEmptyView(boolean show) {
        if (show) {
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    public void toggleViewMode() {
        rvAdapter.toggleCollapseMode();
    }

    public void refresh() {
        rvAdapter.notifyDataSetChanged();
    }

    public void refreshPosition(int position) {
        if (position == -1) {
            notifyDataChange();
        } else if (position >= 0 && position < items.size()) {
            rvAdapter.updatePosition(position);
        }
    }

    public void scrollTo(DateTime time) {
        int position = 0;
        for (DailyAgendaItemStub stub : items) {
            if (stub.dateTime().isAfter(time)) {
                break;
            }
            position++;
        }

        if (position > 0)
            llm.smoothScrollToPosition(rv, null, position - 1);
    }

    public boolean isExpanded() {
        return rvAdapter.isExpanded();
    }

    public void notifyDataChange() {
        try {
            LogUtil.d(TAG, "AgendaView NotifyDataChange");
            items.clear();
            items.addAll(buildItems());
            LogUtil.d(TAG, "Items after rebuild " + items.size());
            rvAdapter.notifyDataSetChanged();
            // show empty list view if there are no items
            rv.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showOrHideEmptyView(!rvAdapter.isShowingSomething());
                }
            }, 100);
        } catch (Exception e) {
            LogUtil.e(TAG, "Error onPostExecute", e);
        }
    }

    public void onUserUpdate() {
        notifyDataChange();
    }

    // Method called from the event bus
    @Subscribe
    public void handleBackgroundUpdatedEvent(final HomeProfileMgr.BackgroundUpdatedEvent event) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                onBackgroundChange(HomeProfileMgr.colorForCurrent(getActivity()));
            }
        }, 500);
    }

    private void setupRecyclerView() {
        llm = new LinearLayoutManager(getContext());
        rv.setLayoutManager(llm);
        rvAdapter = new DailyAgendaRecyclerAdapter(items, rv, llm, getActivity());
        rv.setAdapter(rvAdapter);
        rv.setItemAnimator(new DefaultItemAnimator());

        rvListener = new DailyAgendaRecyclerListener();

        rvAdapter.setListener(rvListener);
    }

    private void setupEmptyView() {
        int color = HomeProfileMgr.colorForCurrent(getActivity());
        Drawable icon = new IconicsDrawable(getContext())
                .icon(emptyViewIcon)
                .color(color)
                .sizeDp(90)
                .paddingDp(0);
        ((ImageView) emptyView.findViewById(R.id.imageView_ok)).setImageDrawable(icon);
    }

    private void showConfirmActivity(View view, DailyAgendaItemStub item, int position) {

        Intent i = new Intent(getContext(), ConfirmActivity.class);
        i.putExtra(IntentParams.EXTRA_POSITION, position);
        i.putExtra(IntentParams.EXTRA_PATIENT_ID, item.patient.getId());
        i.putExtra(IntentParams.EXTRA_DATETIME, item.dateTime().toString(ISODateTimeFormat.dateTimeNoMillis()));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            View v1 = view.findViewById(R.id.patient_avatar);
            View v2 = view.findViewById(R.id.linearLayout);
            View v3 = view.findViewById(R.id.routines_list_item_name);

            if (v1 != null && v2 != null && v3 != null) {
                ActivityOptionsCompat activityOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        new Pair<>(v1, "avatar_transition"),
                        new Pair<>(v2, "time"),
                        new Pair<>(v3, "title")
                );
                ActivityCompat.startActivity(getActivity(), i, activityOptions.toBundle());
            } else {
                startActivity(i);
            }
        } else {
            startActivity(i);
        }
    }

    private void onBackgroundChange(int color) {
        Drawable icon = new IconicsDrawable(getContext())
                .icon(emptyViewIcon)
                .color(color)
                .sizeDp(90)
                .paddingDp(0);
        ((ImageView) emptyView.findViewById(R.id.imageView_ok)).setImageDrawable(icon);
    }

    private static class DailyAgendaItemStubComparator implements Comparator<DailyAgendaItemStub> {

        static final DailyAgendaItemStubComparator instance = new DailyAgendaItemStubComparator();

        private DailyAgendaItemStubComparator() {
        }

        @Override
        public int compare(DailyAgendaItemStub a, DailyAgendaItemStub b) {

            DateTime aT = a.date.toDateTime(a.time);
            DateTime bT = b.date.toDateTime(b.time);

            if (aT.compareTo(bT) == 0 && a.isSpacer) {
                return -1;
            } else if (aT.compareTo(bT) == 0 && b.isSpacer) {
                return 1;
            } else if (aT.compareTo(bT) == 0) {
                return a.hasEvents ? -1 : 1;
            }
            return aT.compareTo(bT);
        }
    }

    private class DailyAgendaRecyclerListener implements DailyAgendaRecyclerAdapter.EventListener {
        DateTime firstTime = null;

        @Override
        public void onItemClick(View v, DailyAgendaItemStub item, int position) {
            showConfirmActivity(v, item, position);
        }

        @Override
        public void onBeforeToggleCollapse(boolean expanded, boolean somethingVisible) {

            int firstPosition = llm.findFirstVisibleItemPosition();
            firstTime = firstPosition >= 0 && firstPosition < items.size() ? items.get(firstPosition).dateTime() : null;

            LogUtil.d(TAG, "OnBeforeCollapse, somethingVisible is " + somethingVisible);

            if (expanded) {
                showOrHideEmptyView(false);
            } else if (!expanded && somethingVisible) {
                showOrHideEmptyView(false);
            } else {
                showOrHideEmptyView(true);
            }
        }

        @Override
        public void onAfterToggleCollapse(boolean expanded, boolean somethingVisible) {
            PreferenceUtils.edit().putBoolean(PreferenceKeys.HOME_DAILYAGENDA_EXPANDED.key(), expanded).apply();
            /*if (expanded && firstTime != null) {
                scrollTo(firstTime);
                firstTime = null;
            } else */
            if (expanded) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        scrollTo(DateTime.now());
                    }
                }, 600);
            }
        }
    }
}