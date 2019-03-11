package com.mediatek.settings.ext;

import android.content.Context;
import android.support.v7.preference.PreferenceScreen;
import android.util.Log;

import com.android.internal.telephony.Phone;

public class DefaultStatusExt implements IStatusExt {

    private static final String TAG = "DefaultStatusExt";

    /**
    * customize imei & imei sv display name.
    * @param imeikey: the name of imei
    * @param imeiSvKey: the name of imei software version
    * @param parent: parent preference
    * @param slotId: slot id
    */
    public void customizeImei(String imeiKey, String imeiSvKey, PreferenceScreen parent,
            int slotId) {
   }

    /**
     * Customize phone number based on SIM.
     * @param currentNumber current mobile number shared.
     * @param slotId slot id
     * @param context Activity contxt
     * @return String to display formatted number
     * @internal
     */
    public String updatePhoneNumber(String currentNumber, int slotId, Context context) {
        Log.d(TAG, "updatePhoneNumber = " + currentNumber);
        return currentNumber;
    }

    /**
     * Update UI handling when resume.
     * @param context Activity contxt
     * @param phone phone object
     * @param preferenceScreen UI preference screen
     * @internal
     */
    public void onResume(Context context, Phone phone, PreferenceScreen preferenceScreen) {
        return;
    }

    /**
     * Update UI handling when onPause.
     * @param context Activity contxt
     * @param phone phone object
     * @internal
     */
    public void onPause(Context context, Phone phone) {
        return;
    }
}
