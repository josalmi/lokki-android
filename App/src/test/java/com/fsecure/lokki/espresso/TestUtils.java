package com.fsecure.lokki.espresso;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.test.espresso.ViewAction;
import android.support.test.espresso.action.CoordinatesProvider;
import android.support.test.espresso.action.GeneralClickAction;
import android.support.test.espresso.action.Press;
import android.support.test.espresso.action.Tap;
import android.view.View;

import com.fsecure.lokki.MainActivity;
import com.fsecure.lokki.utils.PreferenceUtils;

public class TestUtils {

    final static String VALUE_TEST_USER_ACCOUNT = "test@test.com";
    final static String VALUE_TEST_USER_ID = "a1b2c3d4e5f6g7h8i9j10k11l12m13n14o15p16q";
    final static String VALUE_TEST_AUTH_TOKEN = "ABCDEFGHIJ";


    public static void clearAppData(Context targetContext) {
        MainActivity.firstTimeLaunch = null;
        SharedPreferences.Editor editor = targetContext.getSharedPreferences(targetContext.getPackageName(), Context.MODE_PRIVATE).edit();
        editor.clear();
        editor.commit();
    }

    public static void setUserRegistrationData(Context targetContext) {
        MainActivity.firstTimeLaunch = null;
        PreferenceUtils.setValue(targetContext, PreferenceUtils.KEY_USER_ACCOUNT, VALUE_TEST_USER_ACCOUNT);
        PreferenceUtils.setValue(targetContext, PreferenceUtils.KEY_USER_ID, VALUE_TEST_USER_ID);
        PreferenceUtils.setValue(targetContext, PreferenceUtils.KEY_AUTH_TOKEN, VALUE_TEST_AUTH_TOKEN);
    }


    public static ViewAction clickScreenPosition(final int x, final int y) {
        return new GeneralClickAction(
                Tap.SINGLE,
                new CoordinatesProvider() {
                    @Override
                    public float[] calculateCoordinates(View view) {

                        final int[] screenPos = new int[2];
                        view.getLocationOnScreen(screenPos);

                        final float screenX = screenPos[0] + x;
                        final float screenY = screenPos[1] + y;
                        return new float[]{screenX, screenY};
                    }
                },
                Press.FINGER);
    }

}
