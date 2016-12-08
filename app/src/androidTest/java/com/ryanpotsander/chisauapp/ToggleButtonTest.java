package com.ryanpotsander.chisauapp;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.isChecked;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;

/**
 * Created by Ryan on 9/13/16.
 */
@RunWith(AndroidJUnit4.class)
public class ToggleButtonTest {

    @Rule
    public ActivityTestRule<MainActivity> mTestRule = new ActivityTestRule<MainActivity>(MainActivity.class);

    @Test
    public void clickToggleButton() throws Exception {
        //onView(withId(R.id.toggle)).perform(click());
        //onView(withId(R.id.toggle)).check(matches(isChecked()));
    }

}
