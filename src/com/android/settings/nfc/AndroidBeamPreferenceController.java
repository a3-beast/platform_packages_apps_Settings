/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.settings.nfc;

import android.content.Context;
import android.nfc.NfcAdapter;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.RestrictedPreference;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnPause;
import com.android.settingslib.core.lifecycle.events.OnResume;
import com.mediatek.settings.FeatureOption;
import android.util.Log;

import java.util.List;

public class AndroidBeamPreferenceController extends BasePreferenceController
        implements LifecycleObserver, OnResume, OnPause {

    public static final String KEY_ANDROID_BEAM_SETTINGS = "android_beam_settings";

    /// M: Add MTK nfc seting @{
    private static final String KEY_MTK_TOGGLE_NFC = "toggle_mtk_nfc";
    private static final String TAG = "NfcPreferenceController";

    private final NfcAdapter mNfcAdapter;
    private AndroidBeamEnabler mAndroidBeamEnabler;
    private NfcAirplaneModeObserver mAirplaneModeObserver;

    public AndroidBeamPreferenceController(Context context, String key) {
        super(context, key);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(context);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        if (!isAvailable()) {
            /// M: Remove MTK NFC setting if NFC is unavailable
            setVisible(screen, KEY_MTK_TOGGLE_NFC, false /* visible */);
            mAndroidBeamEnabler = null;
            return;
        }

        final RestrictedPreference restrictedPreference =
                (RestrictedPreference) screen.findPreference(getPreferenceKey());
        mAndroidBeamEnabler = new AndroidBeamEnabler(mContext, restrictedPreference);
        if (FeatureOption.MTK_NFC_ADDON_SUPPORT) {
            Log.d(TAG, "MTK NFC support");
            setVisible(screen, KEY_ANDROID_BEAM_SETTINGS, false /* visible */);
        } else {
            Log.d(TAG, "MTK NFC not support");
            setVisible(screen, KEY_MTK_TOGGLE_NFC, false /* visible */);
        }
        // Manually set dependencies for NFC when not toggleable.
        if (!NfcPreferenceController.isToggleableInAirplaneMode(mContext)) {
            mAirplaneModeObserver = new NfcAirplaneModeObserver(mContext, mNfcAdapter,
                    (Preference) restrictedPreference);
        }
    }

    @Override
    @AvailabilityStatus
    public int getAvailabilityStatus() {
        return mNfcAdapter != null
                ? AVAILABLE
                : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void onResume() {
        if (mAirplaneModeObserver != null) {
            mAirplaneModeObserver.register();
        }
        if (mAndroidBeamEnabler != null) {
            mAndroidBeamEnabler.resume();
        }
    }

    @Override
    public void onPause() {
        if (mAirplaneModeObserver != null) {
            mAirplaneModeObserver.unregister();
        }
        if (mAndroidBeamEnabler != null) {
            mAndroidBeamEnabler.pause();
        }
    }

    @Override
    public void updateNonIndexableKeys(List<String> keys) {
        if (isAvailable()) {
            if (FeatureOption.MTK_NFC_ADDON_SUPPORT) {
                keys.add(getPreferenceKey());
            } else {
                keys.add(KEY_MTK_TOGGLE_NFC);
            }
        }
    }
}
