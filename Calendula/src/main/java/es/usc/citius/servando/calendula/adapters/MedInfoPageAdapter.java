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

package es.usc.citius.servando.calendula.adapters;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import es.usc.citius.servando.calendula.fragments.AlertListFragment;
import es.usc.citius.servando.calendula.fragments.ActiveMedInfoFragment;
import es.usc.citius.servando.calendula.fragments.MedInfoFragment;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.util.LogUtil;

public class MedInfoPageAdapter extends FragmentPagerAdapter {

    private final static String TAG = MedInfoPageAdapter.class.getSimpleName();
    private final MedInfoPageSet pageSet;

    Medicine medicine;
    ActiveMedVO activeMed;


    public MedInfoPageAdapter(FragmentManager fm, Medicine medicine, ActiveMedVO activeMed, MedInfoPageSet type) {
        super(fm);
        this.medicine = medicine;
        this.activeMed = activeMed;
        this.pageSet = type;
    }

    @Override
    public Fragment getItem(int position) {

        switch (position) {
            case 0:
                switch (pageSet) {
                    case ALL:
                    case NO_ACTIVE_MED:
                        return MedInfoFragment.newInstance(medicine);
                    case ONLY_ACTIVE_MED:
                        return ActiveMedInfoFragment.newInstance(activeMed);
                }
                break;
            case 1:
                switch (pageSet) {
                    case ALL:
                        return ActiveMedInfoFragment.newInstance(activeMed);
                    case NO_ACTIVE_MED:
                        return AlertListFragment.newInstance(medicine);
                    case ONLY_ACTIVE_MED:
                        LogUtil.e(TAG, "getItem: " + getPositionError(position));
                        break;
                }
                break;
            case 2:
                switch (pageSet) {
                    case ALL:
                        return AlertListFragment.newInstance(medicine);
                    case NO_ACTIVE_MED:
                    case ONLY_ACTIVE_MED:
                        LogUtil.e(TAG, "getItem: " + getPositionError(position));
                        break;
                }
                break;
        }
        return null;
    }

    @Override
    public int getCount() {
        return pageSet.pageCount;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return "";
    }

    private String getPositionError(int position) {
        return String.format("Bad position %d for adapter pageset %s", position, pageSet.toString());
    }

    /**
     * Specifies which tabs will be shown: <br/>
     * <ul>
     * <li><code>ALL</code>: Info, active med info, alerts</li>
     * <li><code>NO_ACTIVE_MED</code>: Info, alerts</li>
     * <li><code>ONLY_ACTIVE_MED</code>: Active med info</li>
     * </ul>
     */
    public enum MedInfoPageSet {
        ALL(3), NO_ACTIVE_MED(2), ONLY_ACTIVE_MED(1);

        int pageCount;

        MedInfoPageSet(final int count) {
            this.pageCount = count;
        }
    }
}
