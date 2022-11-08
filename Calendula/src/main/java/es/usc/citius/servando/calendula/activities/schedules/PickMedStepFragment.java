/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
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

package es.usc.citius.servando.calendula.activities.schedules;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IAdapter;
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter.listeners.OnClickListener;
import com.mikepenz.iconics.IconicsDrawable;
import com.stepstone.stepper.Step;
import com.stepstone.stepper.VerificationError;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.MedicinesActivity;
import es.usc.citius.servando.calendula.activities.schedules.ScheduleBuildActivity.ScheduleStep;
import es.usc.citius.servando.calendula.allergies.AllergyAlertUtil;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.util.LogUtil;

/**
 *
 */

public class PickMedStepFragment extends ScheduleBuildStepFragment implements Step {

    private static final String TAG = "PickMedStepFragment";

    @BindView(R.id.medicines_list)
    RecyclerView recyclerView;

    private FastItemAdapter<MedicineItem> fastAdapter;
    private List<Medicine> mMedicines;
    private Medicine selectedMedicine = null;

    private boolean recyclerSetupDone;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_pick_med_step, container, false);
        ButterKnife.bind(this, rootView);
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (fastAdapter != null) {
            populateAdapter();
        }
    }

    @Override
    public VerificationError verifyStep() {
        if (selectedMedicine == null) {
            return error(getString(R.string.schedule_build_med_error_unselected), ScheduleStep.MEDICINE);
        }
        buildActivity.setMedicine(selectedMedicine);
        return null;
    }

    @Override
    public void onSelected() {
        if (!recyclerSetupDone) {
            recyclerSetupDone = true;
            selectedMedicine = buildActivity.getMedicine();
            setupRecyclerView();
        }
    }

    @Override
    public void onError(@NonNull VerificationError error) {
        //handle error inside of the fragment, e.g. show error on EditText
    }

    private void setupRecyclerView() {
        fastAdapter = new FastItemAdapter<>();
        populateAdapter();
        fastAdapter.withSelectable(true);
        fastAdapter.withOnClickListener(new OnClickListener<MedicineItem>() {
            @Override
            public boolean onClick(View v, IAdapter<MedicineItem> adapter, MedicineItem item, int position) {
                if (item.medicine.getId() == -1) {
                    onAddNewMedicine();
                } else {
                    selectedMedicine = item.medicine;
                    fastAdapter.notifyAdapterDataSetChanged();
                }
                return true;
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(fastAdapter);
    }

    private void populateAdapter() {
        mMedicines = DB.medicines().findAllForActivePatient(getContext());
        if (fastAdapter.getAdapterItemCount() != mMedicines.size() + 1) {
            Collections.sort(mMedicines);
            Medicine dummy = new Medicine();
            dummy.setName(getString(R.string.schedule_build_med_create_new));
            dummy.setId(-1L);
            mMedicines.add(dummy);
            fastAdapter.clear();
            for (Medicine m : mMedicines) {
                fastAdapter.add(new MedicineItem(m));
            }
            fastAdapter.notifyAdapterDataSetChanged();
        }
    }

    private void onAddNewMedicine() {
        Intent i = new Intent(getActivity(), MedicinesActivity.class);
        i.putExtra("create", true);
        startActivity(i);
    }

    private boolean hasAllergies(Medicine m) {
        try {
            if (m.getId() != -1 && AllergyAlertUtil.hasAllergyAlerts(m)) {
                return true;
            }
        } catch (SQLException e) {
            LogUtil.e(TAG, "createMedicineListItem: ", e);
            return true;
        }
        return false;
    }

    private boolean isSelectedMed(Medicine m) {
        return selectedMedicine != null && selectedMedicine.getId().equals(m.getId());
    }

    protected class ViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.medicines_list_item_name)
        protected TextView name;
        @BindView(R.id.imageButton)
        protected ImageView icon;
        @BindView(R.id.selection_indicator)
        protected ImageView selection;

        public ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }

    private class MedicineItem extends AbstractItem<MedicineItem, ViewHolder> {

        static final int ID = 1;
        private final Medicine medicine;
        boolean isSelected = false;
        boolean isDisabled = false;

        public MedicineItem(Medicine m) {
            this.medicine = m;
            isDisabled = hasAllergies(m);
        }

        public Medicine getMedicine() {
            return medicine;
        }

        //The unique ID for this type of item
        @Override
        public int getType() {
            return ID;
        }

        @Override
        public int getLayoutRes() {
            return R.layout.pick_med_list_item;
        }

        @Override
        public void bindView(ViewHolder viewHolder, List<Object> payloads) {
            super.bindView(viewHolder, payloads);
            Context c = viewHolder.itemView.getContext();
            isSelected = isSelectedMed(medicine);
            //bind our data if has changed
            if (!viewHolder.name.getText().equals(medicine.getName())) {
                viewHolder.name.setText(medicine.getName());
                viewHolder.name.setTextColor(c.getResources().getColor(isDisabled ? R.color.activity_schedule_build_foreground_alpha : R.color.activity_schedule_build_foreground));
                if (medicine.getId() != -1) {
                    viewHolder.icon.setImageDrawable(buildActivity.iconFor(medicine.getPresentation()));
                } else {
                    viewHolder.icon.setImageDrawable(new IconicsDrawable(getContext())
                            .icon(CommunityMaterial.Icon2.cmd_plus_circle)
                            .colorRes(R.color.activity_schedule_build_foreground_alpha)
                            .paddingDp(5)
                            .sizeDp(40));
                    viewHolder.name.setTextColor(getResources().getColor(R.color.activity_schedule_build_foreground_alpha));
                }

            }
            if (medicine.getId() != -1) {
                viewHolder.selection.setImageDrawable(buildActivity.selectionIcon(isSelected));
            } else {
                viewHolder.selection.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        public ViewHolder getViewHolder(View v) {
            return new ViewHolder(v);
        }
    }


}