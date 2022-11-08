
package es.usc.citius.servando.calendula.drugdb.model.database;

import com.j256.ormlite.dao.Dao;

import java.sql.SQLException;

import es.usc.citius.servando.calendula.database.DatabaseHelper;
import es.usc.citius.servando.calendula.database.GenericDao;
import es.usc.citius.servando.calendula.drugdb.model.persistence.ATCCode;
import es.usc.citius.servando.calendula.util.LogUtil;

/**
* This class was generated automatically.
* Please check its consistency and completeness carefully.
*/
public class ATCCodeDAO extends GenericDao<ATCCode, Long> {

    private static final String TAG = "ATCCodeDAO";

    private Dao<ATCCode, Long> daoInstance = null;

    public ATCCodeDAO(DatabaseHelper db) {
        super(db);
    }

    @Override
    public Dao<ATCCode, Long> getConcreteDao() {
        try {
            if (daoInstance == null)
                daoInstance = dbHelper.getDao(ATCCode.class);
            return daoInstance;
        } catch (SQLException e) {
            LogUtil.e(TAG, "Error creating ATCCode DAO", e);
            throw new RuntimeException("Error creating ATCCode DAO", e);
        }
    }


}
