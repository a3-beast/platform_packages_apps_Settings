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
package com.mediatek.faceid;

import android.content.Context;
import android.os.SystemProperties;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.security.SecuritySettings;
import com.android.settingslib.core.AbstractPreferenceController;

public class FaceIdPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin {

    private static final String KEY_FACEID = "faceid";
    private Preference mPreference;
    protected final SecuritySettings mHost;

    public FaceIdPreferenceController(Context context, SecuritySettings host) {
        super(context);
        mHost = host;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(KEY_FACEID);
    }

    @Override
    public void updateState(Preference preference) {
//        preference.setSummary("test summary");
    }

    @Override
    public String getPreferenceKey() {
        return KEY_FACEID;
    }

    @Override
    public boolean isAvailable() {
        Log.d("faceid", "isAvailable "+SystemProperties.get("ro.vendor.mtk_cam_security"));
        return SystemProperties.get("ro.vendor.mtk_cam_security").equals("1");
        //return true;
    }

    public void updateSummary() {
        updateState(mPreference);
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return super.handlePreferenceTreeClick(preference);
        }
        final String key = preference.getKey();
        Log.d("faceid", "handlePreferenceTreeClick "+key);
        if(KEY_FACEID.equals(key)) {
            Log.d("faceid", "onPreferenceClick FACEID");
            new SubSettingLauncher(mContext)
                .setDestination(FaceIdSettings.class.getName())
                .setTitle(R.string.faceid_settings_title)
                .setSourceMetricsCategory(mHost.getMetricsCategory())
                .launch();
            //startFragment(this, FaceIdSettings.class.getName(), R.string.faceid_settings_title, FACEID_REQUEST, null);
        }
        return true;
    }
}
