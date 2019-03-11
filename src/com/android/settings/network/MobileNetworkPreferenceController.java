/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.settings.network;

import static android.os.UserHandle.myUserId;
import static android.os.UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.support.annotation.VisibleForTesting;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedPreference;
import com.android.settingslib.Utils;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.List;

public class MobileNetworkPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, LifecycleObserver, OnStart, OnStop {

    private static final String KEY_MOBILE_NETWORK_SETTINGS = "mobile_network_settings";

    private final boolean mIsSecondaryUser;
    private final TelephonyManager mTelephonyManager;
    private final UserManager mUserManager;
    private Preference mPreference;
    @VisibleForTesting
    PhoneStateListener mPhoneStateListener;

    private BroadcastReceiver mAirplanModeChangedReceiver;

    private static final String TAG = "MobileNetworkPreferenceController";

    public MobileNetworkPreferenceController(Context context) {
        super(context);
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mIsSecondaryUser = !mUserManager.isAdminUser();

        mAirplanModeChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateState(mPreference);
            }
        };
    }

    @Override
    public boolean isAvailable() {
        return !isUserRestricted() && !Utils.isWifiOnly(mContext);
    }

    public boolean isUserRestricted() {
        return mIsSecondaryUser ||
                RestrictedLockUtils.hasBaseUserRestriction(
                        mContext,
                        DISALLOW_CONFIG_MOBILE_NETWORKS,
                        myUserId());
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public String getPreferenceKey() {
        return KEY_MOBILE_NETWORK_SETTINGS;
    }

    @Override
    public void onStart() {
        IntentFilter intentFilter =
            new IntentFilter(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        mContext.registerReceiver(mReceiver, intentFilter);
        if (isAvailable()) {
            if (Looper.myLooper() == null) {
                Log.d(TAG, "onResume Looper is null.");
                return;
            }
            if (mPhoneStateListener == null) {
                mPhoneStateListener = new PhoneStateListener() {
                    @Override
                    public void onCallStateChanged(int state, String incomingNumber) {
                        super.onCallStateChanged(state, incomingNumber);
                        Log.d(TAG, "PhoneStateListener, new state=" + state);
                        if (state == TelephonyManager.CALL_STATE_IDLE) {
                            updateMobileNetworkEnabled();
                        }
                    }

                    @Override
                    public void onServiceStateChanged(ServiceState serviceState) {
                        //updateState(mPreference);
                    }
                };
            }
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE |
                    PhoneStateListener.LISTEN_CALL_STATE);
        }
        if (mAirplanModeChangedReceiver != null) {
            mContext.registerReceiver(mAirplanModeChangedReceiver,
                new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED));
        }
    }

    @Override
    public void onStop() {
        if (mPhoneStateListener != null) {
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        if (mAirplanModeChangedReceiver != null) {
            mContext.unregisterReceiver(mAirplanModeChangedReceiver);
        }
        if (null != mReceiver) {
            mContext.unregisterReceiver(mReceiver);
        }
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);

        if (preference instanceof RestrictedPreference &&
            ((RestrictedPreference) preference).isDisabledByAdmin()) {
                Log.d(TAG, "updateState,Mobile Network preference disabled by Admin");
                return;
        }
        boolean isAirplaneModeOff = Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 0;
        Log.d(TAG, "updateState,isAirplaneModeOff = " + isAirplaneModeOff);
        if (!isAirplaneModeOff) {
            preference.setEnabled(false);
            return;
        } else {
            preference.setEnabled(isAirplaneModeOff);
        }
        List<SubscriptionInfo> si = SubscriptionManager.from(mContext).
                getActiveSubscriptionInfoList();
        try {
            if (si == null) {
                Log.d(TAG, "updateState,si == null");
                preference.setEnabled(false);
            } else {
                TelephonyManager telephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
                int callState = telephonyManager.getCallState();
                Log.d(TAG, "updateState,callState = " + callState);
                if (callState == TelephonyManager.CALL_STATE_IDLE) {
                    preference.setEnabled(true);
                } else {
                    preference.setEnabled(false);
                }
            }
        //Plugin need setSummary when two sims?
        } catch (IndexOutOfBoundsException ex) {
            android.util.Log.e("MobileNetworkPreferenceController", "IndexOutOfBoundsException");
        }
    }

    @Override
    public CharSequence getSummary() {
        return ""; //mTelephonyManager.getNetworkOperatorName();
    }


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED.equals(action)) {
                Log.d("MobileNetworkPreferenceController", "ACTION_SIM_INFO_UPDATE received");
                updateMobileNetworkEnabled();
            // when received Carrier config changes, update WFC buttons
            }
        }
    };

    /// M: update MOBILE_NETWORK_SETTINGS enabled state by multiple conditions
    private void updateMobileNetworkEnabled() {
        if (mPreference == null) {
            return;
        }
        if (mPreference instanceof RestrictedPreference) {
            RestrictedPreference rp = (RestrictedPreference) mPreference;
            if (rp.isDisabledByAdmin()) {
                Log.d(TAG, "updateMobileNetworkEnabled,Mobile Network disabled by Admin");
                return;
            }
        }
        boolean isAirplaneModeOff = Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 0;
        Log.d(TAG, "updateMobileNetworkEnabled,isAirplaneModeOff = " + isAirplaneModeOff);
        if (!isAirplaneModeOff) {
            mPreference.setEnabled(false);
            return;
        }
        int simNum = SubscriptionManager.from(mContext).getActiveSubscriptionInfoCount();
            TelephonyManager telephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            int callState = telephonyManager.getCallState();
            Log.d("MobileNetworkPreferenceController", "callstate = " + callState +
                    " simNum = " + simNum);
            if (simNum > 0 && callState == TelephonyManager.CALL_STATE_IDLE) {
                mPreference.setEnabled(true);
            } else {
                /// M: for plug-in
                mPreference.setEnabled(false);
            }
    }
}
