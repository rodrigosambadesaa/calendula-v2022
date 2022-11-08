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

package es.usc.citius.servando.calendula.healthcareprovider.persistence;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import es.usc.citius.servando.calendula.util.LogUtil;

/**
 * Types of active med items, with their codings.
 * Possible values:
 * <li>{@link #DCPF}</li>
 * <li>{@link #NATIONAL_CODE}</li>
 */
public enum ActiveMedType {

    /**
     * Active med is a DCPF code, not a specific med.
     */
    DCPF("2.16.724.4.21.5.15.3"),
    /**
     * Active med is a national code (specific med).
     */
    NATIONAL_CODE("2.16.724.4.21.5.15.4");

    private static final String TAG = "ActiveMedType";
    private static final Map<String, ActiveMedType> codingToEnum;

    static {
        HashMap<String, ActiveMedType> theMap = new HashMap<>();
        for (ActiveMedType areaCode : values()) {
            theMap.put(areaCode.getCoding(), areaCode);
        }
        codingToEnum = Collections.unmodifiableMap(theMap);
    }

    private final String coding;

    ActiveMedType(String coding) {
        this.coding = coding;
    }

    public static ActiveMedType forCoding(final String coding) {
        final ActiveMedType type = codingToEnum.get(coding);
        if (type == null) {
            LogUtil.e(TAG, "forCoding: unknown coding " + coding);
        }
        return type;
    }

    public String getCoding() {
        return coding;
    }
}
