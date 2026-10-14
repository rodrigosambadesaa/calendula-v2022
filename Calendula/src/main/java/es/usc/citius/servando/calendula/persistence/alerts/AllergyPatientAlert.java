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

package es.usc.citius.servando.calendula.persistence.alerts;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.adapters.AlertViewRecyclerAdapter;
import es.usc.citius.servando.calendula.allergies.AllergenVO;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.util.Strings;

public class AllergyPatientAlert extends PatientAlert<AllergyPatientAlert, AllergyPatientAlert.AllergyAlertInfo> {


    public AllergyPatientAlert() {
        setType(AllergyPatientAlert.class.getCanonicalName());
    }

    public AllergyPatientAlert(final Medicine medicine, final List<AllergenVO> allergens) {
        this();
        setLevel(Level.HIGH);
        setMedicine(medicine);
        setPatient(medicine.getPatient());
        setDetails(new AllergyAlertInfo(allergens));
    }

    private static Spanned genAllergenList(PatientAlert incoming, Context context) {
        // Old imports may omit details, contain null items or have names that
        // cannot be recovered. Keep the HIGH warning visible and explicitly
        // describe missing detail instead of crashing or hiding risk.
        final String unavailable = context.getString(R.string.allergy_details_unavailable);
        try {
            AllergyPatientAlert alert = incoming instanceof AllergyPatientAlert
                    ? (AllergyPatientAlert) incoming
                    : (AllergyPatientAlert) incoming.map();
            AllergyAlertInfo details = alert.getDetails();
            List<AllergenVO> vos = details == null ? null : details.getAllergens();
            if (vos == null || vos.isEmpty()) {
                return Html.fromHtml(unavailable);
            }
            List<String> titles = new ArrayList<>(vos.size());
            for (AllergenVO vo : vos) {
                titles.add(vo == null ? null : vo.getName());
            }
            if (!AllergyAlertPresentationGuard.hasCompleteNames(titles)) {
                return Html.fromHtml(unavailable);
            }
            // genBulletList internally calls Html.fromHtml. Escape names
            // supplied by patients/imports so 'A < B' is visible literally,
            // not interpreted as HTML markup or a clickable injected link.
            List<String> escaped = new ArrayList<>(titles.size());
            for (String name : titles) {
                escaped.add(Html.escapeHtml(name));
            }
            return Strings.genBulletList(escaped);
        } catch (RuntimeException invalidDetails) {
            // No clinical identifiers or exception contents in device logs.
            return Html.fromHtml(unavailable);
        }
    }

    @Override
    public Class<?> getDetailsType() {
        return AllergyAlertInfo.class;
    }

    @Override
    public Class<?> viewProviderType() {
        return AllergyAlertViewProvider.class;
    }

    public static class AllergyAlertInfo {
        private List<AllergenVO> allergens;

        public AllergyAlertInfo() {
        }

        public AllergyAlertInfo(List<AllergenVO> allergens) {
            this.allergens = allergens;
        }

        public List<AllergenVO> getAllergens() {
            return allergens;
        }

        public void setAllergens(List<AllergenVO> allergens) {
            this.allergens = allergens;
        }
    }

    /**
     * Provides a mechanism for creating driving alert views
     */
    public static class AllergyAlertViewProvider implements AlertViewRecyclerAdapter.AlertViewProvider {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.allergy_alert_list_item, parent, false);
            return new AllergyAlertViewHolder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, PatientAlert alert) {
            AllergyAlertViewHolder viewHolder = (AllergyAlertViewHolder) holder;
            viewHolder.alert = alert;
            Drawable icon = new IconicsDrawable(viewHolder.context)
                    .icon(CommunityMaterial.Icon.cmd_alert)
                    .colorRes(R.color.android_red)
                    .sizeDp(24)
                    .paddingDp(4);

            viewHolder.alertIcon.setImageDrawable(icon);
            viewHolder.title.setText(R.string.title_alert_allergy);
            viewHolder.description.setText(Html.fromHtml(viewHolder.description.getContext().getString(R.string.description_alert_allergy)));
            viewHolder.allergens.setText(genAllergenList(alert, viewHolder.context));
        }

        public static class AllergyAlertViewHolder extends RecyclerView.ViewHolder {

            Context context;
            PatientAlert alert;
            LayoutInflater inflater;
            @BindView(R.id.alert_icon)
            ImageView alertIcon;
            @BindView(R.id.alert_title)
            TextView title;
            @BindView(R.id.alert_description)
            TextView description;
            @BindView(R.id.allergens_list)
            TextView allergens;

            public AllergyAlertViewHolder(View itemView) {
                super(itemView);
                this.context = itemView.getContext();
                this.inflater = LayoutInflater.from(itemView.getContext());
                ButterKnife.bind(this, itemView);
            }
        }

    }
}
