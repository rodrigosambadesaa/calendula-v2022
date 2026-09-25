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

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.joda.time.LocalTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.fragments.RoutineCreateOrEditFragment;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.util.TestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;


public class RoutinesActivityCreateTest {

    public static final String NAME = "breakfast";

    @Rule
    public ActivityTestRule<RoutinesActivity> activityRule =
            new ActivityTestRule<>(RoutinesActivity.class, true, false);

    private RoutinesActivity mActivity;


    @Before
    public void setUp() throws Exception {
        CalendulaApp.disableReceivers = true;
        DB.init(InstrumentationRegistry.getInstrumentation().getTargetContext());
        TestUtils.resetDatabase();
        mActivity = activityRule.launchActivity(null);
        TestUtils.unlockScreen(mActivity);
    }

    @Test
    public void testActivityCreated() {
        assertNotNull(mActivity);
    }

    @Test
    public void testCreateRoutine() {
        // ensure there are no routines
        assertEquals(DB.routines().count(), 0);
        // type name
        onView(withId(R.id.routine_edit_name)).perform(typeText(NAME));
        // close Soft Keyboard
        TestUtils.closeKeyboard();
        // set routine time (not possible vía UI)
        setTimepickerTime(18, 30);

        // time picker is not consistent across screen sizes/android versions.
        // It cannot be tested this way.
//        // open time picker
//        onView(withId(R.id.button2)).perform(click());
//        // check its open
//        onView(withId(R.id.done_button)).check(matches(isDisplayed()));
//        // press done
//        onView(withId(R.id.done_button)).perform(click());

        // check button has the correct time
        onView(withId(R.id.button2)).check(matches(withText("18:30")));
        // click save
        onView(withId(R.id.add_button)).perform(click());

        // find routine and do assertions
        Routine r = DB.routines().findOneBy(Routine.COLUMN_NAME, NAME);
        assertNotNull(r);
        assertEquals(NAME, r.getName());
        assertEquals(new LocalTime(18, 30), r.getTime());
    }


    private void setTimepickerTime(final int hour, final int minute) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RoutineCreateOrEditFragment f = mActivity.routineFragment;
                f.onDialogTimeSet(0, hour, minute);
            }
        });
    }


}