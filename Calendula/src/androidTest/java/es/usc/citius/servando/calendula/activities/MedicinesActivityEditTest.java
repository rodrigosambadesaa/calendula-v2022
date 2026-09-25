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

package es.usc.citius.servando.calendula.activities;

import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.util.TestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class MedicinesActivityEditTest {

    public static final String MEDICINE_NAME = "Aspirin";

    @Rule
    public ActivityTestRule<MedicinesActivity> activityRule =
            new ActivityTestRule<>(MedicinesActivity.class, true, false);

    private MedicinesActivity mActivity;


    @Before
    public void setUp() throws Exception {
        CalendulaApp.disableReceivers = true;
        DB.init(InstrumentationRegistry.getInstrumentation().getTargetContext());
        TestUtils.resetDatabase();
        // create medicine
        Medicine created = new Medicine(MEDICINE_NAME, Presentation.EFFERVESCENT);
        created.setPatient(DB.patients().getDefault());
        created.save();

        // set edit intent
        Intent i = new Intent();
        i.putExtra(CalendulaApp.INTENT_EXTRA_MEDICINE_ID, created.getId());


        mActivity = activityRule.launchActivity(i);
        TestUtils.unlockScreen(mActivity);
    }

    @Test
    public void testActivityCreated() {
        assertNotNull(mActivity);
    }


    @Test
    public void testEditMedicine() {

        assertEquals(1, DB.medicines().count());
        assertEquals(MEDICINE_NAME, DB.medicines().findAll().get(0).getName());

        TestUtils.sleep(1500);
        // select capsules presentation
        onView(withTagValue(new PresentationTagMatcher(Presentation.CAPSULES)))
                .perform(click());
        TestUtils.sleep(200);

        // click save
        onView(withId(R.id.add_button))
                .perform(click());

        // find edited med and do assertions
        Medicine m = DB.medicines().findOneBy(Medicine.COLUMN_NAME, MEDICINE_NAME);
        assertEquals(1, DB.medicines().count());
        assertNotNull(m);
        assertEquals(Presentation.CAPSULES, m.getPresentation());
    }


}