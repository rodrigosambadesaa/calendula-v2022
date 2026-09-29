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

import android.app.Activity;
import android.content.Context;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import java.lang.reflect.Constructor;

import es.usc.citius.servando.calendula.util.LogUtil;

public class HomePageAdapter extends FragmentPagerAdapter {

    private static final String TAG = "HomePageAdapter";

    public HomePageAdapter(FragmentManager fm, Context ctx, Activity activity) {
        super(fm);
    }

    @Override
    public Fragment getItem(int position) {
        final String cn = HomePages.values()[position].className;
        try {
            final Constructor<?> constructor = Class.forName(cn).getConstructor();
            return (Fragment) constructor.newInstance();
        } catch (Exception e) {
            LogUtil.e(TAG, "getItem: cannot instantiate fragment.", e);
        }

        return null;
    }

    @Override
    public int getCount() {
        return HomePages.values().length;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return "";
    }
}
