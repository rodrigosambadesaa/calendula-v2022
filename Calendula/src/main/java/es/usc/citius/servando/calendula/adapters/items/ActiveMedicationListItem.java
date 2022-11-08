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

package es.usc.citius.servando.calendula.adapters.items;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.IIcon;

import org.joda.time.DateTime;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.drugdb.DBRegistry;
import es.usc.citius.servando.calendula.drugdb.PrescriptionDBMgr;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.ScheduleDisplayUtils;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedCNVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedDCPFVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVisualizationType;
import es.usc.citius.servando.calendula.healthcareprovider.model.DosageEntryVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.DosageVO;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType;
import es.usc.citius.servando.calendula.healthcareprovider.util.StringUtils;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.Strings;


public class ActiveMedicationListItem extends AbstractItem<ActiveMedicationListItem, ActiveMedicationListItem.ActiveMedicationViewHolder> {


    private static final String TAG = "ActiveMedicationListIte";

    private ActiveMedVO vo;

    public ActiveMedicationListItem(ActiveMedVO vo) {
        this.vo = vo;
    }

    public ActiveMedVO getVo() {
        return vo;
    }

    public void setVo(ActiveMedVO vo) {
        this.vo = vo;
    }

    @Override
    public int getType() {
        return R.id.fastadapter_active_medication_list_item;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.active_medication_list_item;
    }

    @Override
    public boolean isSelectable() {
        return false;
    }

    @Override
    public void bindView(ActiveMedicationViewHolder holder, List<Object> payloads) {
        super.bindView(holder, payloads);
        final PrescriptionDBMgr current = DBRegistry.instance().current();

        if (vo != null) {
            try {
                final Context ctx = holder.itemView.getContext();
                final String dateFormat = ctx.getString(R.string.schedule_limits_date_format);
                final boolean isInactive = ActiveMedEntity.ActiveMedState.INACTIVE.equals(vo.getState());
                DosageVO dosage = vo.getDosage();

                DosageType dosageType = dosage.getType();
                if (vo.hasExtraInfo()) {
                    if (vo.getVisualizationType() == ActiveMedVisualizationType.ANTICOAGULANT_TYPE) {
                        holder.infoButton.setVisibility(View.VISIBLE);
                        holder.infoButton.setEnabled(true); holder.reminderButton.setVisibility(View.GONE);
                        holder.reminderButton.setEnabled(false);
                    } else {
                        holder.infoButton.setVisibility(View.GONE);
                        holder.infoButton.setEnabled(false);
                        holder.reminderButton.setVisibility(View.VISIBLE);
                        holder.reminderButton.setEnabled(true);
                    }

                } else if (!DosageType.AS_NEEDED.equals(dosageType)) {
                    Schedule schedule = DB.schedules().findOneBy(Schedule.BOUND_TO, vo.getBackingEntity().getId());
                    if (schedule != null) {
                        holder.reminderButton.setImageDrawable(IconUtils.icon(ctx,
                                CommunityMaterial.Icon.cmd_bell_ring,
                                R.color.black, 30, 5));
                    }
                    else {
                        IconicsDrawable icon = IconUtils.icon(ctx,
                                CommunityMaterial.Icon.cmd_bell_plus,
                                R.color.black, 30, 5);

                        holder.reminderButton.setImageDrawable(icon);
                    }
                    holder.reminderButton.setVisibility(View.VISIBLE);
                    holder.reminderButton.setEnabled(true);
                    holder.infoButton.setVisibility(View.GONE);
                    holder.infoButton.setEnabled(false);
                } else {
                    holder.infoButton.setVisibility(View.GONE);
                    holder.infoButton.setEnabled(false);
                    holder.reminderButton.setVisibility(View.GONE);
                    holder.reminderButton.setEnabled(false);
                }

                String dosageStr;
                List<DosageEntryVO> entries = dosage.getEntries();
                if (vo.hasExtraInfo()) {
                    switch (vo.getVisualizationType()) {
                        case ANTICOAGULANT_TYPE:
                            dosageStr = ctx.getString(R.string.aml_schedule_extra_info);
                            break;
                        case INSULIN_TYPE:
                            dosageStr = ctx.getString(R.string.aml_schedule_insulin);
                            break;
                        default:
                            dosageStr = "";
                    }
                } else {
                    switch (dosageType) {
                        case AS_NEEDED:
                            String units = StringUtils.timeUnitDisplayName(ctx, entries.get(0).getRepeatUnits());
                            dosageStr = ctx.getString(R.string.as_needed) + " - " + ctx.getString(R.string.dosage_string_duration, Strings.prettyDouble(entries.get(0).getRepeatValue()), units);
                            break;
                        case GENERAL:
                            String generalUnits = StringUtils.timeUnitDisplayName(ctx, entries.get(0).getRepeatUnits());
                            dosageStr = ctx.getString(R.string.repeat_every_tostr, Strings.prettyDouble(entries.get(0).getRepeatValue()), generalUnits);
                            break;
                        case DETAILED:
                            dosageStr = ScheduleDisplayUtils.getTimesByDayStr(entries.size(), ctx);
                            break;
                        case NONE:
                            dosageStr = ctx.getString(R.string.aml_schedule_info_missing);
                            break;
                        default:
                            dosageStr = "";
                            break;
                    }
                }

                holder.intakeSummary.setText(dosageStr.replaceAll(" +", " "));
                if(entries.size()!=0) {
                    String instructions = entries.get(0).getPatientInstruction();
                    if (instructions == null || org.apache.commons.lang3.StringUtils.isBlank(instructions)){
                        holder.intakeInstructions.setVisibility(View.GONE);
                    }
                    else {
                        holder.intakeInstructions.setText(getInfoMessage(ctx.getString(R.string.instructions_prefix) + " " + instructions, CommunityMaterial.Icon2.cmd_medical_bag, ctx));
                        holder.intakeInstructions.setVisibility(View.VISIBLE);
                    }
                }
                else {
                    holder.intakeInstructions.setVisibility(View.GONE);
                }


                if (vo.getLastUpdated().isAfter(DateTime.now().minusDays(3)) || !vo.isUpdateSeen()) {


                    IIcon icon = isInactive ? CommunityMaterial.Icon.cmd_delete_circle : CommunityMaterial.Icon.cmd_autorenew;

                    holder.lastUpdateInfo.setCompoundDrawables(IconUtils.icon(ctx, icon, R.color.android_orange, 30, 4), null, null, null);
                    holder.lastUpdateInfo.setText(vo.getLastUpdated().toString("dd MMM"));
                    holder.lastUpdateInfo.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String text;
                            if(isInactive){
                                text=ctx.getString(R.string.aml_removed_date, vo.getLastUpdated().toString(dateFormat));
                            }else {
                                text=ctx.getString(R.string.aml_updated_date, vo.getLastUpdated().toString(dateFormat));
                            }
                            Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                if (vo.getValidityStart() != null) {
                    holder.fromContainer.setVisibility(View.VISIBLE);
                    holder.startDate.setText(vo.getValidityStart().toString(dateFormat));
                } else {
                    holder.fromContainer.setVisibility(View.GONE);
                }

                if (vo.getValidityEnd() != null) {
                    holder.toContainer.setVisibility(View.VISIBLE);
                    holder.endDate.setText(vo.getValidityEnd().toString(dateFormat));
                } else {
                    holder.toContainer.setVisibility(View.GONE);
                }

                switch (vo.getActiveMedType()) {
                    case DCPF:
                        ActiveMedDCPFVO dcpfvo = (ActiveMedDCPFVO) vo;
                        setDefault(holder, vo);
                        if (dcpfvo.getHomogeneousGroup() != null) {
                            String groupName = dcpfvo.getHomogeneousGroup().getName();
                            holder.medName.setText(groupName);
                            final Presentation expectedPresentation = current.expectedPresentation(groupName, groupName);
                            holder.icon.setImageDrawable(IconUtils.icon(holder.itemView.getContext(), expectedPresentation.icon(), R.color.white));
                        }
                        break;
                    case NATIONAL_CODE:
                        ActiveMedCNVO cnvo = (ActiveMedCNVO) vo;
                        if (cnvo.getPrescription() != null) {
                            holder.medName.setText(Strings.toProperCase(cnvo.getPrescription().getName()));
                            final Presentation expectedPresentation = current.expectedPresentation(cnvo.getPrescription());
                            holder.icon.setImageDrawable(IconUtils.icon(holder.itemView.getContext(), expectedPresentation.icon(), R.color.white));
                        } else {
                            setDefault(holder, vo);
                        }
                        break;
                    default:
                        setDefault(holder, vo);
                }

                holder.middleSection.setVisibility(isInactive ? View.GONE : View.VISIBLE);

            } catch (Exception e) {
                LogUtil.e(TAG, "Oooops!", e);
            }
        }
    }

    @NonNull
    @Override
    public ActiveMedicationViewHolder getViewHolder(View v) {
        return new ActiveMedicationViewHolder(v);
    }

    public void setDefault(ActiveMedicationViewHolder holder, ActiveMedVO vo) {
        final Presentation expectedPresentation = DBRegistry.instance().current().expectedPresentation(vo.getDefaultDisplay(), "");
        holder.medName.setText(Strings.toProperCase(vo.getDefaultDisplay()));
        holder.icon.setImageDrawable(IconUtils.icon(holder.itemView.getContext(), expectedPresentation.icon(), R.color.white));
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

    public static class ActiveMedicationViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.enable_reminders_button)
        public ImageButton reminderButton;
        @BindView(R.id.get_info_button)
        public Button infoButton;
        @BindView(R.id.medicine_icon)
        ImageView icon;
        @BindView(R.id.schedule_icon)
        ImageView scheduleIcon;
        @BindView(R.id.med_name)
        TextView medName;
        @BindView(R.id.intake_summary)
        TextView intakeSummary;
        @BindView(R.id.intake_instructions)
        TextView intakeInstructions;
        @BindView(R.id.start_date)
        TextView startDate;
        @BindView(R.id.end_date)
        TextView endDate;
        @BindView(R.id.active_med_dates_container)
        View datesContainer;
        @BindView(R.id.substitution_info)
        TextView substitutionInfo;
        @BindView(R.id.last_update_info)
        Button lastUpdateInfo;
        @BindView(R.id.from_container)
        View fromContainer;
        @BindView(R.id.to_container)
        View toContainer;
        @BindView(R.id.cardView)
        CardView card;
        @BindView(R.id.middle)
        View middleSection;

        public ActiveMedicationViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);

            IconicsDrawable icon = IconUtils.icon(view.getContext(),
                    CommunityMaterial.Icon.cmd_bell_plus,
                    R.color.black, 30, 5);

            reminderButton.setImageDrawable(icon);
            infoButton.setText(R.string.get_info_button);
            scheduleIcon.setImageDrawable(IconUtils.icon(view.getContext(),
                    CommunityMaterial.Icon2.cmd_information_outline,
                    R.color.black, 30, 4));
        }
    }
}
