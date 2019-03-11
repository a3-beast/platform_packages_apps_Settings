/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.deviceinfo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.R;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.deviceinfo.AbstractWifiMacAddressPreferenceController;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.ext.ISettingsMiscExt;

/**
 * Concrete subclass of WIFI MAC address preference controller
 */
public class WifiMacAddressPreferenceController extends AbstractWifiMacAddressPreferenceController
        implements PreferenceControllerMixin {
    private ISettingsMiscExt mExt;
    private Preference mWifiMacAddressPreference;
    private final WifiManager mWifiManager;
    static final String KEY_WIFI_MAC_ADDRESS = "wifi_mac_address";

    public WifiMacAddressPreferenceController(Context context, Lifecycle lifecycle) {
        super(context, lifecycle);
        mWifiManager = context.getSystemService(WifiManager.class);
        mExt = UtilsExt.getMiscPlugin(mContext);
    }

    @Override
    public boolean isAvailable() {
        return mContext.getResources().getBoolean(R.bool.config_show_wifi_mac_address);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mWifiMacAddressPreference = screen.findPreference(KEY_WIFI_MAC_ADDRESS);
        updateConnectivity();
    }

    // This space intentionally left blank
    @SuppressLint("HardwareIds")
    @Override
    protected void updateConnectivity() {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        final int macRandomizationMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.WIFI_CONNECTED_MAC_RANDOMIZATION_ENABLED, 0);
        final String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        Log.i("mtk81234", macAddress == null ? "deviceinfo macAddress = null" : "deviceinfo " + macAddress);
        if (null == mWifiMacAddressPreference) {
            return;
        }
        if (TextUtils.isEmpty(macAddress)) {
            mWifiMacAddressPreference.setSummary(R.string.status_unavailable);
        } else if (macRandomizationMode == 1 && WifiInfo.DEFAULT_MAC_ADDRESS.equals(macAddress)) {
            mWifiMacAddressPreference.setSummary(R.string.wifi_status_mac_randomized);
        } else {
            mWifiMacAddressPreference.setSummary(
                    mExt.customizeMacAddressString(macAddress,
                    mContext.getString(R.string.status_unavailable)));
        }
    }
}
