package es.usc.citius.servando.calendula.healthcareprovider.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.DispensationInfoVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.DosageEntryVO;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntryEntity;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;


public class DBUtil {

    private static final String TAG = "DBUtil";

    public static boolean isValidDB() {
        return PreferenceUtils.getString(PreferenceKeys.DRUGDB_CURRENT_DB, CalendulaApp.getContext().getString(R.string.database_none_id))
                .equals(CalendulaApp.getContext().getString(R.string.database_aemps_id));
    }

    public static List<DispensationInfoVO> toDispensationVoList(Collection<DispensationInfoEntity> entities) {
        List<DispensationInfoVO> ret = new ArrayList<>(entities.size());
        for (DispensationInfoEntity entity : entities) {
            ret.add(new DispensationInfoVO(entity));
        }
        return ret;
    }

    public static Collection<DispensationInfoEntity> toDispensationEntityFC(List<DispensationInfoVO> vos, ActiveMedVO activeMedVO) {
        Collection<DispensationInfoEntity> fc = new ArrayList<>(vos.size());
        for (DispensationInfoVO vo : vos) {
            fc.add(vo.toEntity(activeMedVO));
        }
        return fc;
    }

    public static List<DosageEntryVO> toDosageEntryList(Collection<DosageEntryEntity> entities) {
        List<DosageEntryVO> vos = new ArrayList<>(entities.size());
        for (DosageEntryEntity entry : entities) {
            vos.add(new DosageEntryVO(entry));
        }
        return vos;
    }

    public static Collection<DosageEntryEntity> toDosageEntryFc(List<DosageEntryVO> vos) {
        List<DosageEntryEntity> entities = new ArrayList<>(vos.size());
        for (DosageEntryVO vo : vos) {
            entities.add(vo.entity());
        }
        return entities;
    }
}
