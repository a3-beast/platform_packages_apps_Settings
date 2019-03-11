/*
 * Copyright (C) 2006 The Android Open Source Project
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

import static android.content.Context.TELEPHONY_SERVICE;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.support.annotation.VisibleForTesting;
import android.support.v14.preference.MultiSelectListPreference;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.util.ArrayUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settingslib.utils.ThreadUtils;

import android.widget.Toast;

import com.android.internal.telephony.TelephonyIntents;
import com.mediatek.apn.ApnUtils;
import com.mediatek.internal.telephony.MtkPhoneConstants;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.cdma.CdmaApnSetting;
import com.mediatek.settings.ext.IApnSettingsExt;
import com.mediatek.settings.sim.SimHotSwapHandler;
import com.mediatek.settings.sim.SimHotSwapHandler.OnSimHotSwapListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mediatek.telephony.MtkTelephony;

import static android.app.Activity.RESULT_OK;

public class ApnEditor extends SettingsPreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener,
                    Preference.OnPreferenceChangeListener, OnKeyListener {

    private final static String TAG = ApnEditor.class.getSimpleName();
    private final static boolean VDBG = false;   // STOPSHIP if true

    private final static String KEY_AUTH_TYPE = "auth_type";
    private final static String KEY_PROTOCOL = "apn_protocol";
    private final static String KEY_ROAMING_PROTOCOL = "apn_roaming_protocol";
    private final static String KEY_CARRIER_ENABLED = "carrier_enabled";
    private final static String KEY_BEARER_MULTI = "bearer_multi";
    private final static String KEY_MVNO_TYPE = "mvno_type";
    private final static String KEY_PASSWORD = "apn_password";

    private static final int MENU_DELETE = Menu.FIRST;
    private static final int MENU_SAVE = Menu.FIRST + 1;
    private static final int MENU_CANCEL = Menu.FIRST + 2;
    /// M: show dialog when change default APN
    private static final int CONFIRM_CHANGE_DIALOG_ID = 1;

    @VisibleForTesting
    static String sNotSet;
    @VisibleForTesting
    EditTextPreference mName;
    @VisibleForTesting
    EditTextPreference mApn;
    @VisibleForTesting
    EditTextPreference mProxy;
    @VisibleForTesting
    EditTextPreference mPort;
    @VisibleForTesting
    EditTextPreference mUser;
    @VisibleForTesting
    EditTextPreference mServer;
    @VisibleForTesting
    EditTextPreference mPassword;
    @VisibleForTesting
    EditTextPreference mMmsc;
    @VisibleForTesting
    EditTextPreference mMcc;
    @VisibleForTesting
    EditTextPreference mMnc;
    @VisibleForTesting
    EditTextPreference mMmsProxy;
    @VisibleForTesting
    EditTextPreference mMmsPort;
    @VisibleForTesting
    ListPreference mAuthType;
    @VisibleForTesting
    EditTextPreference mApnType;
    @VisibleForTesting
    ListPreference mProtocol;
    @VisibleForTesting
    ListPreference mRoamingProtocol;
    @VisibleForTesting
    SwitchPreference mCarrierEnabled;
    @VisibleForTesting
    MultiSelectListPreference mBearerMulti;
    @VisibleForTesting
    ListPreference mMvnoType;
    @VisibleForTesting
    EditTextPreference mMvnoMatchData;

    @VisibleForTesting
    ApnData mApnData;

    private String mCurMnc;
    private String mCurMcc;

    private boolean mNewApn;
    private int mSubId;
    private TelephonyManager mTelephonyManager;
    private int mBearerInitialVal = 0;
    private String mMvnoTypeStr;
    private String mMvnoMatchDataStr;
    private String[] mReadOnlyApnTypes;
    private String[] mReadOnlyApnFields;
    private boolean mReadOnlyApn;
    private Uri mCarrierUri;

    /**
     * Standard projection for the interesting columns of a normal note.
     */
    private static String[] sProjection = new String[] {
            Telephony.Carriers._ID,     // 0
            Telephony.Carriers.NAME,    // 1
            Telephony.Carriers.APN,     // 2
            Telephony.Carriers.PROXY,   // 3
            Telephony.Carriers.PORT,    // 4
            Telephony.Carriers.USER,    // 5
            Telephony.Carriers.SERVER,  // 6
            Telephony.Carriers.PASSWORD, // 7
            Telephony.Carriers.MMSC, // 8
            Telephony.Carriers.MCC, // 9
            Telephony.Carriers.MNC, // 10
            Telephony.Carriers.NUMERIC, // 11
            Telephony.Carriers.MMSPROXY,// 12
            Telephony.Carriers.MMSPORT, // 13
            Telephony.Carriers.AUTH_TYPE, // 14
            Telephony.Carriers.TYPE, // 15
            Telephony.Carriers.PROTOCOL, // 16
            Telephony.Carriers.CARRIER_ENABLED, // 17
            Telephony.Carriers.BEARER, // 18
            Telephony.Carriers.BEARER_BITMASK, // 19
            Telephony.Carriers.ROAMING_PROTOCOL, // 20
            Telephony.Carriers.MVNO_TYPE,   // 21
            Telephony.Carriers.MVNO_MATCH_DATA,  // 22
            Telephony.Carriers.EDITED,   // 23
            Telephony.Carriers.USER_EDITABLE,    //24
            MtkTelephony.Carriers.SOURCE_TYPE //25, M: [APN Source Type]
    };

    private static final int ID_INDEX = 0;
    @VisibleForTesting
    static final int NAME_INDEX = 1;
    @VisibleForTesting
    static final int APN_INDEX = 2;
    private static final int PROXY_INDEX = 3;
    private static final int PORT_INDEX = 4;
    private static final int USER_INDEX = 5;
    private static final int SERVER_INDEX = 6;
    private static final int PASSWORD_INDEX = 7;
    private static final int MMSC_INDEX = 8;
    @VisibleForTesting
    static final int MCC_INDEX = 9;
    @VisibleForTesting
    static final int MNC_INDEX = 10;
    private static final int MMSPROXY_INDEX = 12;
    private static final int MMSPORT_INDEX = 13;
    private static final int AUTH_TYPE_INDEX = 14;
    private static final int TYPE_INDEX = 15;
    private static final int PROTOCOL_INDEX = 16;
    @VisibleForTesting
    static final int CARRIER_ENABLED_INDEX = 17;
    private static final int BEARER_INDEX = 18;
    private static final int BEARER_BITMASK_INDEX = 19;
    private static final int ROAMING_PROTOCOL_INDEX = 20;
    private static final int MVNO_TYPE_INDEX = 21;
    private static final int MVNO_MATCH_DATA_INDEX = 22;
    private static final int EDITED_INDEX = 23;
    private static final int USER_EDITABLE_INDEX = 24;
    /// M: [APN Source Type]
    private static final int SOURCE_TYPE_INDEX = 25;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.apn_editor);

        sNotSet = getResources().getString(R.string.apn_not_set);
        mName = (EditTextPreference) findPreference("apn_name");
        mApn = (EditTextPreference) findPreference("apn_apn");
        mProxy = (EditTextPreference) findPreference("apn_http_proxy");
        mPort = (EditTextPreference) findPreference("apn_http_port");
        mUser = (EditTextPreference) findPreference("apn_user");
        mServer = (EditTextPreference) findPreference("apn_server");
        mPassword = (EditTextPreference) findPreference(KEY_PASSWORD);
        mMmsProxy = (EditTextPreference) findPreference("apn_mms_proxy");
        mMmsPort = (EditTextPreference) findPreference("apn_mms_port");
        mMmsc = (EditTextPreference) findPreference("apn_mmsc");
        mMcc = (EditTextPreference) findPreference("apn_mcc");
        mMnc = (EditTextPreference) findPreference("apn_mnc");
        mApnType = (EditTextPreference) findPreference("apn_type");
        mAuthType = (ListPreference) findPreference(KEY_AUTH_TYPE);
        mAuthType.setOnPreferenceChangeListener(this);
        mProtocol = (ListPreference) findPreference(KEY_PROTOCOL);
        mProtocol.setOnPreferenceChangeListener(this);
        mRoamingProtocol = (ListPreference) findPreference(KEY_ROAMING_PROTOCOL);
        mRoamingProtocol.setOnPreferenceChangeListener(this);
        mCarrierEnabled = (SwitchPreference) findPreference(KEY_CARRIER_ENABLED);
        mBearerMulti = (MultiSelectListPreference) findPreference(KEY_BEARER_MULTI);
        mBearerMulti.setOnPreferenceChangeListener(this);
        mBearerMulti.setPositiveButtonText(android.R.string.ok);
        mBearerMulti.setNegativeButtonText(android.R.string.cancel);
        mMvnoType = (ListPreference) findPreference(KEY_MVNO_TYPE);
        mMvnoType.setOnPreferenceChangeListener(this);
        mMvnoMatchData = (EditTextPreference) findPreference("mvno_match_data");

        final Intent intent = getIntent();
        final String action = intent.getAction();
        mSubId = intent.getIntExtra(ApnSettings.SUB_ID,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        mReadOnlyApn = false;
        mReadOnlyApnTypes = null;
        mReadOnlyApnFields = null;

        /// M: for plug-in @{
        mApnExt = UtilsExt.getApnSettingsExt(getContext());
        /// @}
        CarrierConfigManager configManager = (CarrierConfigManager)
                getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager != null) {
            PersistableBundle b = configManager.getConfig();
            if (b != null) {
                mReadOnlyApnTypes = b.getStringArray(
                        CarrierConfigManager.KEY_READ_ONLY_APN_TYPES_STRING_ARRAY);
                if (!ArrayUtils.isEmpty(mReadOnlyApnTypes)) {
                    for (String apnType : mReadOnlyApnTypes) {
                        Log.d(TAG, "onCreate: read only APN type: " + apnType);
                    }
                }
                mReadOnlyApnFields = b.getStringArray(
                        CarrierConfigManager.KEY_READ_ONLY_APN_FIELDS_STRING_ARRAY);
            }
        }

        Uri uri = null;
        if (action.equals(Intent.ACTION_EDIT)) {
            uri = intent.getData();
            if (!uri.isPathPrefixMatch(Telephony.Carriers.CONTENT_URI)) {
                Log.e(TAG, "Edit request not for carrier table. Uri: " + uri);
                finish();
                return;
            }
            /// M: for [Read Only APN], some APN can not be edited
            mReadOnlyMode = intent.getBooleanExtra("readOnly", false);
            Log.d(TAG, "Read only mode : " + mReadOnlyMode);
        } else if (action.equals(Intent.ACTION_INSERT)) {
            mCarrierUri = intent.getData();
            if (!mCarrierUri.isPathPrefixMatch(Telephony.Carriers.CONTENT_URI)) {
                Log.e(TAG, "Insert request not for carrier table. Uri: " + mCarrierUri);
                finish();
                return;
            }
            mNewApn = true;
            mMvnoTypeStr = intent.getStringExtra(ApnSettings.MVNO_TYPE);
            mMvnoMatchDataStr = intent.getStringExtra(ApnSettings.MVNO_MATCH_DATA);
            Log.d(TAG, "mvnoType = " + mMvnoTypeStr + ", mvnoMatchData =" + mMvnoMatchDataStr);
        } else {
            finish();
            return;
        }

        /// M: for plug-in @{
        sProjection = mApnExt.customizeApnProjection(sProjection);
        mApnExt.customizePreference(mSubId, getPreferenceScreen());
        /// @}

        // Creates an ApnData to store the apn data temporary, so that we don't need the cursor to
        // get the apn data. The uri is null if the action is ACTION_INSERT, that mean there is no
        // record in the database, so create a empty ApnData to represent a empty row of database.
        if (uri != null) {
            mApnData = getApnDataFromUri(uri);
            Log.d(TAG, "uri = " + uri);
        } else {
            mApnData = new ApnData(sProjection.length);
            Log.d(TAG, "sProjection.length = " + sProjection.length);
        }

        mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        boolean isUserEdited = mApnData.getInteger(EDITED_INDEX, Telephony.Carriers.USER_EDITED)
                == Telephony.Carriers.USER_EDITED;

        Log.d(TAG, "onCreate: EDITED " + isUserEdited);
        // if it's not a USER_EDITED apn, check if it's read-only
        if (!isUserEdited && (mApnData.getInteger(USER_EDITABLE_INDEX, 1) == 0
                || apnTypesMatch(mReadOnlyApnTypes, mApnData.getString(TYPE_INDEX)))) {
            Log.d(TAG, "onCreate: apnTypesMatch; read-only APN");
            mReadOnlyApn = true;
            disableAllFields();
        } else if (!ArrayUtils.isEmpty(mReadOnlyApnFields)) {
            disableFields(mReadOnlyApnFields);
        }

        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            getPreferenceScreen().getPreference(i).setOnPreferenceChangeListener(this);
        }

        fillUI(icicle == null);

        /// M: for [SIM Hot Swap] @{
        mSimHotSwapHandler = new SimHotSwapHandler(getContext());
        mSimHotSwapHandler.registerOnSimHotSwap(new OnSimHotSwapListener() {
            @Override
            public void onSimHotSwap() {
                Log.d(TAG, "onSimHotSwap, finish Activity~~");
                finish();
            }
        });
        /// @}

        /// M: listen for Airplane mode && data state @{
        IntentFilter filter = new IntentFilter(
                TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        getContext().registerReceiver(mReceiver, filter);
        /// @}

         /// M: update screen to proper state
        //updateScreenEnableState();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @VisibleForTesting
    static String formatInteger(String value) {
        try {
            final int intValue = Integer.parseInt(value);
            return String.format("%d", intValue);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    /**
     * Check if passed in array of APN types indicates all APN types
     * @param apnTypes array of APN types. "*" indicates all types.
     * @return true if all apn types are included in the array, false otherwise
     */
    static boolean hasAllApns(String[] apnTypes) {
        if (ArrayUtils.isEmpty(apnTypes)) {
            return false;
        }

        List apnList = Arrays.asList(apnTypes);
        if (apnList.contains(PhoneConstants.APN_TYPE_ALL)) {
            Log.d(TAG, "hasAllApns: true because apnList.contains(PhoneConstants.APN_TYPE_ALL)");
            return true;
        }
        for (String apn : PhoneConstants.APN_TYPES) {
            if (!apnList.contains(apn)) {
                return false;
            }
        }

        Log.d(TAG, "hasAllApns: true");
        return true;
    }

    /**
     * Check if APN types overlap.
     * @param apnTypesArray1 array of APNs. Empty array indicates no APN type; "*" indicates all
     *                       types
     * @param apnTypes2 comma separated string of APN types. Empty string represents all types.
     * @return if any apn type matches return true, otherwise return false
     */
    private boolean apnTypesMatch(String[] apnTypesArray1, String apnTypes2) {
        if (ArrayUtils.isEmpty(apnTypesArray1)) {
            return false;
        }

        if (hasAllApns(apnTypesArray1) || TextUtils.isEmpty(apnTypes2)) {
            return true;
        }

        List apnTypesList1 = Arrays.asList(apnTypesArray1);
        String[] apnTypesArray2 = apnTypes2.split(",");

        for (String apn : apnTypesArray2) {
            if (apnTypesList1.contains(apn.trim())) {
                Log.d(TAG, "apnTypesMatch: true because match found for " + apn.trim());
                return true;
            }
        }

        Log.d(TAG, "apnTypesMatch: false");
        return false;
    }

    /**
     * Function to get Preference obj corresponding to an apnField
     * @param apnField apn field name for which pref is needed
     * @return Preference obj corresponding to passed in apnField
     */
    private Preference getPreferenceFromFieldName(String apnField) {
        switch (apnField) {
            case Telephony.Carriers.NAME:
                return mName;
            case Telephony.Carriers.APN:
                return mApn;
            case Telephony.Carriers.PROXY:
                return mProxy;
            case Telephony.Carriers.PORT:
                return mPort;
            case Telephony.Carriers.USER:
                return mUser;
            case Telephony.Carriers.SERVER:
                return mServer;
            case Telephony.Carriers.PASSWORD:
                return mPassword;
            case Telephony.Carriers.MMSPROXY:
                return mMmsProxy;
            case Telephony.Carriers.MMSPORT:
                return mMmsPort;
            case Telephony.Carriers.MMSC:
                return mMmsc;
            case Telephony.Carriers.MCC:
                return mMcc;
            case Telephony.Carriers.MNC:
                return mMnc;
            case Telephony.Carriers.TYPE:
                return mApnType;
            case Telephony.Carriers.AUTH_TYPE:
                return mAuthType;
            case Telephony.Carriers.PROTOCOL:
                return mProtocol;
            case Telephony.Carriers.ROAMING_PROTOCOL:
                return mRoamingProtocol;
            case Telephony.Carriers.CARRIER_ENABLED:
                return mCarrierEnabled;
            case Telephony.Carriers.BEARER:
            case Telephony.Carriers.BEARER_BITMASK:
                return mBearerMulti;
            case Telephony.Carriers.MVNO_TYPE:
                return mMvnoType;
            case Telephony.Carriers.MVNO_MATCH_DATA:
                return mMvnoMatchData;
        }
        return null;
    }

    /**
     * Disables given fields so that user cannot modify them
     *
     * @param apnFields fields to be disabled
     */
    private void disableFields(String[] apnFields) {
        for (String apnField : apnFields) {
            Preference preference = getPreferenceFromFieldName(apnField);
            if (preference != null) {
                preference.setEnabled(false);
            }
        }
    }

    /**
     * Disables all fields so that user cannot modify the APN
     */
    private void disableAllFields() {
        mName.setEnabled(false);
        mApn.setEnabled(false);
        mProxy.setEnabled(false);
        mPort.setEnabled(false);
        mUser.setEnabled(false);
        mServer.setEnabled(false);
        mPassword.setEnabled(false);
        mMmsProxy.setEnabled(false);
        mMmsPort.setEnabled(false);
        mMmsc.setEnabled(false);
        mMcc.setEnabled(false);
        mMnc.setEnabled(false);
        mApnType.setEnabled(false);
        mAuthType.setEnabled(false);
        mProtocol.setEnabled(false);
        mRoamingProtocol.setEnabled(false);
        mCarrierEnabled.setEnabled(false);
        mBearerMulti.setEnabled(false);
        mMvnoType.setEnabled(false);
        mMvnoMatchData.setEnabled(false);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.APN_EDITOR;
    }

    @VisibleForTesting
    void fillUI(boolean firstTime) {
        Log.d(TAG, "fillUi... firstTime = " + firstTime);
        if (firstTime) {
            // Fill in all the values from the db in both text editor and summary
            mName.setText(mApnData.getString(NAME_INDEX));
            mApn.setText(mApnData.getString(APN_INDEX));
            mProxy.setText(mApnData.getString(PROXY_INDEX));
            mPort.setText(mApnData.getString(PORT_INDEX));
            mUser.setText(mApnData.getString(USER_INDEX));
            mServer.setText(mApnData.getString(SERVER_INDEX));
            mPassword.setText(mApnData.getString(PASSWORD_INDEX));
            mMmsProxy.setText(mApnData.getString(MMSPROXY_INDEX));
            mMmsPort.setText(mApnData.getString(MMSPORT_INDEX));
            mMmsc.setText(mApnData.getString(MMSC_INDEX));
            mMcc.setText(mApnData.getString(MCC_INDEX));
            mMnc.setText(mApnData.getString(MNC_INDEX));
            mApnType.setText(mApnData.getString(TYPE_INDEX));

            if (mNewApn) {
                String numeric = mTelephonyManager.getSimOperator(mSubId);
                Log.d(TAG, " fillUi, numeric = " + numeric);

                /// M: for [APN CT Roaming], special APN selection rule for CT
                numeric = CdmaApnSetting.updateMccMncForCdma(numeric, mSubId);

                // MCC is first 3 chars and then in 2 - 3 chars of MNC
                if (numeric != null && numeric.length() > 4) {
                    // Country code
                    String mcc = numeric.substring(0, 3);
                    // Network code
                    String mnc = numeric.substring(3);
                    // Auto populate MNC and MCC for new entries, based on what SIM reports
                    mMcc.setText(mcc);
                    mMnc.setText(mnc);
                    mCurMnc = mnc;
                    mCurMcc = mcc;
                }

                /// M: [APN Source Type]
                mSourceType = ApnUtils.SOURCE_TYPE_USER_EDIT;
            } else {
                /// M: [APN Source Type]
                mSourceType = mApnData.getInteger(SOURCE_TYPE_INDEX);
            }
            int authVal = mApnData.getInteger(AUTH_TYPE_INDEX, -1);
            if (authVal != -1) {
                mAuthType.setValueIndex(authVal);
            } else {
                mAuthType.setValue(null);
            }

            mProtocol.setValue(mApnData.getString(PROTOCOL_INDEX));
            mRoamingProtocol.setValue(mApnData.getString(ROAMING_PROTOCOL_INDEX));
            mCarrierEnabled.setChecked(mApnData.getInteger(CARRIER_ENABLED_INDEX, 1) == 1);
            mBearerInitialVal = mApnData.getInteger(BEARER_INDEX, 0);

            HashSet<String> bearers = new HashSet<String>();
            int bearerBitmask = mApnData.getInteger(BEARER_BITMASK_INDEX, 0);
            if (bearerBitmask == 0) {
                if (mBearerInitialVal == 0) {
                    bearers.add("" + 0);
                }
            } else {
                int i = 1;
                while (bearerBitmask != 0) {
                    if ((bearerBitmask & 1) == 1) {
                        bearers.add("" + i);
                    }
                    bearerBitmask >>= 1;
                    i++;
                }
            }

            if (mBearerInitialVal != 0 && bearers.contains("" + mBearerInitialVal) == false) {
                // add mBearerInitialVal to bearers
                bearers.add("" + mBearerInitialVal);
            }
            mBearerMulti.setValues(bearers);

            mMvnoType.setValue(mApnData.getString(MVNO_TYPE_INDEX));
            mMvnoMatchData.setEnabled(false);
            mMvnoMatchData.setText(mApnData.getString(MVNO_MATCH_DATA_INDEX));
            if (mNewApn && mMvnoTypeStr != null && mMvnoMatchDataStr != null) {
                mMvnoType.setValue(mMvnoTypeStr);
                mMvnoMatchData.setText(mMvnoMatchDataStr);
            }

            /// M: for plug-in
            /*mApnExt.setPreferenceTextAndSummary(
                    mSubId, mApnData.getString(sProjection.length - 1));*/
        }

        mName.setSummary(checkNull(mName.getText()));
        mApn.setSummary(checkNull(mApn.getText()));
        mProxy.setSummary(checkNull(mProxy.getText()));
        mPort.setSummary(checkNull(mPort.getText()));
        mUser.setSummary(checkNull(mUser.getText()));
        mServer.setSummary(checkNull(mServer.getText()));
        mPassword.setSummary(starify(mPassword.getText()));
        mMmsProxy.setSummary(checkNull(mMmsProxy.getText()));
        mMmsPort.setSummary(checkNull(mMmsPort.getText()));
        mMmsc.setSummary(checkNull(mMmsc.getText()));
        mMcc.setSummary(formatInteger(checkNull(mMcc.getText())));
        mMnc.setSummary(formatInteger(checkNull(mMnc.getText())));
        mApnType.setSummary(checkNull(mApnType.getText()));

        String authVal = mAuthType.getValue();
        if (authVal != null) {
            int authValIndex = Integer.parseInt(authVal);
            mAuthType.setValueIndex(authValIndex);

            String[] values = getResources().getStringArray(R.array.apn_auth_entries);
            mAuthType.setSummary(values[authValIndex]);
        } else {
            mAuthType.setSummary(sNotSet);
        }

        mProtocol.setSummary(checkNull(protocolDescription(mProtocol.getValue(), mProtocol)));
        mRoamingProtocol.setSummary(
                checkNull(protocolDescription(mRoamingProtocol.getValue(), mRoamingProtocol)));
        mBearerMulti.setSummary(
                checkNull(bearerMultiDescription(mBearerMulti.getValues())));
        mMvnoType.setSummary(
                checkNull(mvnoDescription(mMvnoType.getValue())));
        mMvnoMatchData.setSummary(checkNull(mMvnoMatchData.getText()));
        // allow user to edit carrier_enabled for some APN
        boolean ceEditable = getResources().getBoolean(R.bool.config_allow_edit_carrier_enabled);
        if (ceEditable) {
            mCarrierEnabled.setEnabled(true);
        } else {
            mCarrierEnabled.setEnabled(false);
        }
    }

    /**
     * Returns the UI choice (e.g., "IPv4/IPv6") corresponding to the given
     * raw value of the protocol preference (e.g., "IPV4V6"). If unknown,
     * return null.
     */
    private String protocolDescription(String raw, ListPreference protocol) {
        int protocolIndex = protocol.findIndexOfValue(raw);
        if (protocolIndex == -1) {
            return null;
        } else {
            String[] values = getResources().getStringArray(R.array.apn_protocol_entries);
            try {
                return values[protocolIndex];
            } catch (ArrayIndexOutOfBoundsException e) {
                return null;
            }
        }
    }

    private String bearerMultiDescription(Set<String> raw) {
        String[] values = getResources().getStringArray(R.array.bearer_entries);
        StringBuilder retVal = new StringBuilder();
        boolean first = true;
        for (String bearer : raw) {
            int bearerIndex = mBearerMulti.findIndexOfValue(bearer);
            try {
                if (first) {
                    retVal.append(values[bearerIndex]);
                    first = false;
                } else {
                    retVal.append(", " + values[bearerIndex]);
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                // ignore
            }
        }
        String val = retVal.toString();
        if (!TextUtils.isEmpty(val)) {
            return val;
        }
        return null;
    }

    private String mvnoDescription(String newValue) {
        int mvnoIndex = mMvnoType.findIndexOfValue(newValue);
        String oldValue = mMvnoType.getValue();

        if (mvnoIndex == -1) {
            return null;
        } else {
            /// M: add PNN type
            //String[] values = getResources().getStringArray(R.array.mvno_type_entries);
            String[] values = getResources().getStringArray(R.array.ext_mvno_type_entries);
            boolean mvnoMatchDataUneditable =
                    mReadOnlyApn || (mReadOnlyApnFields != null
                            && Arrays.asList(mReadOnlyApnFields)
                            .contains(Telephony.Carriers.MVNO_MATCH_DATA));
            mMvnoMatchData.setEnabled(!mvnoMatchDataUneditable && mvnoIndex != 0);
            if (newValue != null && newValue.equals(oldValue) == false) {
                if (values[mvnoIndex].equals("SPN")) {
                    mMvnoMatchData.setText(mTelephonyManager.getSimOperatorName());
                } else if (values[mvnoIndex].equals("IMSI")) {
                    String numeric = mTelephonyManager.getSimOperator(mSubId);
                    mMvnoMatchData.setText(numeric + "x");
                } else if (values[mvnoIndex].equals("GID")) {
                    mMvnoMatchData.setText(mTelephonyManager.getGroupIdLevel1());
                }
            }

            try {
                return values[mvnoIndex];
            } catch (ArrayIndexOutOfBoundsException e) {
                return null;
            }
        }
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (KEY_AUTH_TYPE.equals(key)) {
            try {
                int index = Integer.parseInt((String) newValue);
                mAuthType.setValueIndex(index);

                String[] values = getResources().getStringArray(R.array.apn_auth_entries);
                mAuthType.setSummary(values[index]);
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (KEY_PROTOCOL.equals(key)) {
            String protocol = protocolDescription((String) newValue, mProtocol);
            if (protocol == null) {
                return false;
            }
            mProtocol.setSummary(protocol);
            mProtocol.setValue((String) newValue);
        } else if (KEY_ROAMING_PROTOCOL.equals(key)) {
            String protocol = protocolDescription((String) newValue, mRoamingProtocol);
            if (protocol == null) {
                return false;
            }
            mRoamingProtocol.setSummary(protocol);
            mRoamingProtocol.setValue((String) newValue);
        } else if (KEY_BEARER_MULTI.equals(key)) {
            String bearer = bearerMultiDescription((Set<String>) newValue);
            if (bearer == null) {
                return false;
            }
            mBearerMulti.setValues((Set<String>) newValue);
            mBearerMulti.setSummary(bearer);
        } else if (KEY_MVNO_TYPE.equals(key)) {
            String mvno = mvnoDescription((String) newValue);
            if (mvno == null) {
                return false;
            }
            mMvnoType.setValue((String) newValue);
            mMvnoType.setSummary(mvno);
        /// M: fix AOSP bug 02850319 the preference summary will be reset
        // if (preference.equals(mPassword)) {
        } else if (preference.equals(mPassword)) {
            preference.setSummary(starify(newValue != null ? String.valueOf(newValue) : ""));
        } else if (preference.equals(mCarrierEnabled) || preference.equals(mBearerMulti)) {
            // do nothing
        } else {
            preference.setSummary(checkNull(newValue != null ? String.valueOf(newValue) : null));
        }

        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        /// M: for [Read Only APN] @{
        Log.d(TAG, "onCreateOptionsMenu mReadOnlyMode = " + mReadOnlyMode);
        if (mReadOnlyMode) {
            return;
        }
        /// @}

        // If it's a new APN, then cancel will delete the new entry in onPause
        /// M: [APN Source Type]not show delete for default APN type
        // if (!mNewApn && !mReadOnlyApn) {
        if (!mNewApn && !mReadOnlyApn && mSourceType != ApnUtils.SOURCE_TYPE_DEFAULT) {
            menu.add(0, MENU_DELETE, 0, R.string.menu_delete)
                .setIcon(R.drawable.ic_delete);
        }
        menu.add(0, MENU_SAVE, 0, R.string.menu_save)
            .setIcon(android.R.drawable.ic_menu_save);
        menu.add(0, MENU_CANCEL, 0, R.string.menu_cancel)
            .setIcon(android.R.drawable.ic_menu_close_clear_cancel);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_DELETE:
                deleteApn();
                finish();
                return true;
            case MENU_SAVE:
            /// M: [APN Source Type]show the confirm dialog when save the default APN {@
            if (mSourceType == ApnUtils.SOURCE_TYPE_DEFAULT) {
                showDialog(CONFIRM_CHANGE_DIALOG_ID);
            /// @}
            } else if (validateAndSaveApnData()) {
                    finish();
                }
                return true;
            case MENU_CANCEL:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setOnKeyListener(this);
        view.setFocusableInTouchMode(true);
        view.requestFocus();
    }

    /**
     * Try to save the apn data when pressed the back button. An error message will be displayed if
     * the apn data is invalid.
     *
     * TODO(b/77339593): Try to keep the same behavior between back button and up navigate button.
     * We will save the valid apn data to the database when pressed the back button, but discard all
     * user changed when pressed the up navigate button.
     */
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                if (validateAndSaveApnData()) {
                    finish();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Add key, value to {@code cv} and compare the value against the value at index in
     * {@link #mApnData}.
     *
     * <p>
     * The key, value will not add to {@code cv} if value is null.
     *
     * @return true if values are different. {@code assumeDiff} indicates if values can be assumed
     * different in which case no comparison is needed.
     */
    boolean setStringValueAndCheckIfDiff(
            ContentValues cv, String key, String value, boolean assumeDiff, int index) {
        String valueFromLocalCache = mApnData.getString(index);
        if (VDBG) {
            Log.d(TAG, "setStringValueAndCheckIfDiff: assumeDiff: " + assumeDiff
                    + " key: " + key
                    + " value: '" + value
                    + "' valueFromDb: '" + valueFromLocalCache + "'");
        }
        boolean isDiff = assumeDiff
                || !((TextUtils.isEmpty(value) && TextUtils.isEmpty(valueFromLocalCache))
                || (value != null && value.equals(valueFromLocalCache)));

        if (isDiff && value != null) {
            cv.put(key, value);
        }
        return isDiff;
    }

    /**
     * Add key, value to {@code cv} and compare the value against the value at index in
     * {@link #mApnData}.
     *
     * @return true if values are different. {@code assumeDiff} indicates if values can be assumed
     * different in which case no comparison is needed.
     */
    boolean setIntValueAndCheckIfDiff(
            ContentValues cv, String key, int value, boolean assumeDiff, int index) {
        Integer valueFromLocalCache = mApnData.getInteger(index);
        if (VDBG) {
            Log.d(TAG, "setIntValueAndCheckIfDiff: assumeDiff: " + assumeDiff
                    + " key: " + key
                    + " value: '" + value
                    + "' valueFromDb: '" + valueFromLocalCache + "'");
        }

        boolean isDiff = assumeDiff || value != valueFromLocalCache;
        if (isDiff) {
            cv.put(key, value);
        }
        return isDiff;
    }

    /**
     * Validates the apn data and save it to the database if it's valid.
     *
     * <p>
     * A dialog with error message will be displayed if the APN data is invalid.
     *
     * @return true if there is no error
     */
    @VisibleForTesting
    boolean validateAndSaveApnData() {
         Log.d(TAG, "validateAndSave...");
        // Nothing to do if it's a read only APN
        if (mReadOnlyApn) {
            return true;
        }

        String name = checkNotSet(mName.getText());
        String apn = checkNotSet(mApn.getText());
        String mcc = checkNotSet(mMcc.getText());
        String mnc = checkNotSet(mMnc.getText());

        String errorMsg = validateApnData();
        if (errorMsg != null) {
            showError();
            return false;
        }

        ContentValues values = new ContentValues();
        // call update() if it's a new APN. If not, check if any field differs from the db value;
        // if any diff is found update() should be called
        boolean callUpdate = mNewApn;
        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.NAME,
                name,
                callUpdate,
                NAME_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.APN,
                apn,
                callUpdate,
                APN_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.PROXY,
                checkNotSet(mProxy.getText()),
                callUpdate,
                PROXY_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.PORT,
                checkNotSet(mPort.getText()),
                callUpdate,
                PORT_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.MMSPROXY,
                checkNotSet(mMmsProxy.getText()),
                callUpdate,
                MMSPROXY_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.MMSPORT,
                checkNotSet(mMmsPort.getText()),
                callUpdate,
                MMSPORT_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.USER,
                checkNotSet(mUser.getText()),
                callUpdate,
                USER_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.SERVER,
                checkNotSet(mServer.getText()),
                callUpdate,
                SERVER_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.PASSWORD,
                checkNotSet(mPassword.getText()),
                callUpdate,
                PASSWORD_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.MMSC,
                checkNotSet(mMmsc.getText()),
                callUpdate,
                MMSC_INDEX);

        String authVal = mAuthType.getValue();
        if (authVal != null) {
            callUpdate = setIntValueAndCheckIfDiff(values,
                    Telephony.Carriers.AUTH_TYPE,
                    Integer.parseInt(authVal),
                    callUpdate,
                    AUTH_TYPE_INDEX);
        }

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.PROTOCOL,
                checkNotSet(mProtocol.getValue()),
                callUpdate,
                PROTOCOL_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.ROAMING_PROTOCOL,
                checkNotSet(mRoamingProtocol.getValue()),
                callUpdate,
                ROAMING_PROTOCOL_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.TYPE,
                checkNotSet(getUserEnteredApnType()),
                callUpdate,
                TYPE_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.MCC,
                mcc,
                callUpdate,
                MCC_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.MNC,
                mnc,
                callUpdate,
                MNC_INDEX);

        values.put(Telephony.Carriers.NUMERIC, mcc + mnc);

        if (mCurMnc != null && mCurMcc != null) {
            if (mCurMnc.equals(mnc) && mCurMcc.equals(mcc)) {
                values.put(Telephony.Carriers.CURRENT, 1);
            }
        }

        Set<String> bearerSet = mBearerMulti.getValues();
        int bearerBitmask = 0;
        for (String bearer : bearerSet) {
            if (Integer.parseInt(bearer) == 0) {
                bearerBitmask = 0;
                break;
            } else {
                bearerBitmask |= ServiceState.getBitmaskForTech(Integer.parseInt(bearer));
            }
        }
        callUpdate = setIntValueAndCheckIfDiff(values,
                Telephony.Carriers.BEARER_BITMASK,
                bearerBitmask,
                callUpdate,
                BEARER_BITMASK_INDEX);

        int bearerVal;
        if (bearerBitmask == 0 || mBearerInitialVal == 0) {
            bearerVal = 0;
        } else if (ServiceState.bitmaskHasTech(bearerBitmask, mBearerInitialVal)) {
            bearerVal = mBearerInitialVal;
        } else {
            // bearer field was being used but bitmask has changed now and does not include the
            // initial bearer value -- setting bearer to 0 but maybe better behavior is to choose a
            // random tech from the new bitmask??
            bearerVal = 0;
        }
        callUpdate = setIntValueAndCheckIfDiff(values,
                Telephony.Carriers.BEARER,
                bearerVal,
                callUpdate,
                BEARER_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.MVNO_TYPE,
                checkNotSet(mMvnoType.getValue()),
                callUpdate,
                MVNO_TYPE_INDEX);

        callUpdate = setStringValueAndCheckIfDiff(values,
                Telephony.Carriers.MVNO_MATCH_DATA,
                checkNotSet(mMvnoMatchData.getText()),
                callUpdate,
                MVNO_MATCH_DATA_INDEX);

        callUpdate = setIntValueAndCheckIfDiff(values,
                Telephony.Carriers.CARRIER_ENABLED,
                mCarrierEnabled.isChecked() ? 1 : 0,
                callUpdate,
                CARRIER_ENABLED_INDEX);

        values.put(Telephony.Carriers.EDITED, Telephony.Carriers.USER_EDITED);
       /// M: [APN Source Type]
        values.put(MtkTelephony.Carriers.SOURCE_TYPE, mSourceType);
        /// M: for plug-in
        mApnExt.saveApnValues(values);

        Log.d(TAG, "Save apn " + values.getAsString(Telephony.Carriers.APN));
        if (callUpdate) {
            final Uri uri = mApnData.getUri() == null ? mCarrierUri : mApnData.getUri();
            updateApnDataToDatabase(uri, values);
        } else {
            if (VDBG) Log.d(TAG, "validateAndSaveApnData: not calling update()");
        }

        return true;
    }

    private void updateApnDataToDatabase(Uri uri, ContentValues values) {
        ThreadUtils.postOnBackgroundThread(() -> {
            if (uri.equals(mCarrierUri)) {
                // Add a new apn to the database
                final Uri newUri = getContentResolver().insert(mCarrierUri, values);
                if (newUri == null) {
                    Log.e(TAG, "Can't add a new apn to database " + mCarrierUri);
                }
            } else {
                // Update the existing apn
                getContentResolver().update(
                        uri, values, null /* where */, null /* selection Args */);
            }
        });
    }

    /**
     * Validates whether the apn data is valid.
     *
     * @return An error message if the apn data is invalid, otherwise return null.
     */
    @VisibleForTesting
    String validateApnData() {
        String errorMsg = null;

        String name = checkNotSet(mName.getText());
        String apn = checkNotSet(mApn.getText());
        String mcc = checkNotSet(mMcc.getText());
        String mnc = checkNotSet(mMnc.getText());
        String apnType = mApnType.getText();

        if (TextUtils.isEmpty(name)) {
            errorMsg = getResources().getString(R.string.error_name_empty);
        } else if (TextUtils.isEmpty(apn) && (apnType == null || !apnType.contains("ia"))) {
            errorMsg = getResources().getString(R.string.error_apn_empty);
        } else if (mcc == null || mcc.length() != 3) {
            errorMsg = getResources().getString(R.string.error_mcc_not3);
        } else if ((mnc == null || (mnc.length() & 0xFFFE) != 2)) {
            errorMsg = getResources().getString(R.string.error_mnc_not23);
        }

        if (errorMsg == null) {
            // if carrier does not allow editing certain apn types, make sure type does not include
            // those
            if (!ArrayUtils.isEmpty(mReadOnlyApnTypes)
                    && apnTypesMatch(mReadOnlyApnTypes, getUserEnteredApnType())) {
                StringBuilder stringBuilder = new StringBuilder();
                for (String type : mReadOnlyApnTypes) {
                    stringBuilder.append(type).append(", ");
                    Log.d(TAG, "validateApnData: appending type: " + type);
                }
                // remove last ", "
                if (stringBuilder.length() >= 2) {
                    stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());
                }
                errorMsg = String.format(getResources().getString(R.string.error_adding_apn_type),
                        stringBuilder);
            }
        }

        return errorMsg;
    }

    @Override
    public Dialog onCreateDialog(int id) {
             /// M: add confirm dialog
        if (id == CONFIRM_CHANGE_DIALOG_ID) {
            return  new AlertDialog.Builder(getContext())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.error_title)
            .setMessage(getString(R.string.apn_predefine_change_dialog_notice))
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    if (validateAndSaveApnData()) {
                        finish();
                    }
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .create();
        }
        return super.onCreateDialog(id);
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        if (dialogId == CONFIRM_CHANGE_DIALOG_ID) {
            return MetricsEvent.DIALOG_APN_EDITOR_ERROR;
        }
        return 0;
    }

    @VisibleForTesting
    void showError() {
        ErrorDialog.showError(this);
    }

    private void deleteApn() {
        if (mApnData.getUri() != null) {
            getContentResolver().delete(mApnData.getUri(), null, null);
            mApnData = new ApnData(sProjection.length);
        }
    }

    private String starify(String value) {
        if (value == null || value.length() == 0) {
            return sNotSet;
        } else {
            char[] password = new char[value.length()];
            for (int i = 0; i < password.length; i++) {
                password[i] = '*';
            }
            return new String(password);
        }
    }

    /**
     * Returns {@link #sNotSet} if the given string {@code value} is null or empty. The string
     * {@link #sNotSet} typically used as the default display when an entry in the preference is
     * null or empty.
     */
    private String checkNull(String value) {
        return TextUtils.isEmpty(value) ? sNotSet : value;
    }

    /**
     * Returns null if the given string {@code value} equals to {@link #sNotSet}. This method
     * should be used when convert a string value from preference to database.
     */
    private String checkNotSet(String value) {
        return sNotSet.equals(value) ? null : value;
    }

    private String getUserEnteredApnType() {
        // if user has not specified a type, map it to "ALL APN TYPES THAT ARE NOT READ-ONLY"
        String userEnteredApnType = mApnType.getText();
        if (userEnteredApnType != null) userEnteredApnType = userEnteredApnType.trim();
        if ((TextUtils.isEmpty(userEnteredApnType)
                || PhoneConstants.APN_TYPE_ALL.equals(userEnteredApnType))
                && !ArrayUtils.isEmpty(mReadOnlyApnTypes)) {
            StringBuilder editableApnTypes = new StringBuilder();
            List<String> readOnlyApnTypes = Arrays.asList(mReadOnlyApnTypes);
            boolean first = true;
            for (String apnType : MtkPhoneConstants.MTK_APN_TYPES) {
                if (TextUtils.isEmpty(userEnteredApnType)
                        && apnType.equals(PhoneConstants.APN_TYPE_IMS)) {
                    continue;
                }
                // add APN type if it is not read-only and is not wild-cardable
                if (!readOnlyApnTypes.contains(apnType)
                        && !apnType.equals(PhoneConstants.APN_TYPE_IA)
                        && !apnType.equals(PhoneConstants.APN_TYPE_EMERGENCY)) {
                    if (first) {
                        first = false;
                    } else {
                        editableApnTypes.append(",");
                    }
                    editableApnTypes.append(apnType);
                }
            }
            userEnteredApnType = editableApnTypes.toString();
            Log.d(TAG, "getUserEnteredApnType: changed apn type to editable apn types: "
                    + userEnteredApnType);
        }

        return userEnteredApnType;
    }

    public static class ErrorDialog extends InstrumentedDialogFragment {

        public static void showError(ApnEditor editor) {
            ErrorDialog dialog = new ErrorDialog();
            dialog.setTargetFragment(editor, 0);
            dialog.show(editor.getFragmentManager(), "error");
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            String msg = ((ApnEditor) getTargetFragment()).validateApnData();

            return new AlertDialog.Builder(getContext())
                    .setTitle(R.string.error_title)
                    .setPositiveButton(android.R.string.ok, null)
                    .setMessage(msg)
                    .create();
        }

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.DIALOG_APN_EDITOR_ERROR;
        }
    }

    @VisibleForTesting
    ApnData getApnDataFromUri(Uri uri) {
        ApnData apnData = null;
        try (Cursor cursor = getContentResolver().query(
                uri,
                sProjection,
                null /* selection */,
                null /* selectionArgs */,
                null /* sortOrder */)) {
            if (cursor != null) {
                cursor.moveToFirst();
                apnData = new ApnData(uri, cursor);
            }
        }

        if (apnData == null) {
            Log.d(TAG, "Can't get apnData from Uri " + uri);
        }

        return apnData;
    }

    @VisibleForTesting
    static class ApnData {
        /**
         * The uri correspond to a database row of the apn data. This should be null if the apn
         * is not in the database.
         */
        Uri mUri;

        /** Each element correspond to a column of the database row. */
        Object[] mData;

        ApnData(int numberOfField) {
            mData = new Object[numberOfField];
        }

        ApnData(Uri uri, Cursor cursor) {
            mUri = uri;
            mData = new Object[cursor.getColumnCount()];
            for (int i = 0; i < mData.length; i++) {
                switch (cursor.getType(i)) {
                    case Cursor.FIELD_TYPE_FLOAT:
                        mData[i] = cursor.getFloat(i);
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        mData[i] = cursor.getInt(i);
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        mData[i] = cursor.getString(i);
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        mData[i] = cursor.getBlob(i);
                        break;
                    default:
                        mData[i] = null;
                }
            }
        }

        Uri getUri() {
            return mUri;
        }

        void setUri(Uri uri) {
            mUri = uri;
        }

        Integer getInteger(int index) {
            return (Integer) mData[index];
        }

        Integer getInteger(int index, Integer defaultValue) {
            Integer val = getInteger(index);
            return val == null ? defaultValue : val;
        }

        String getString(int index) {
            return (String) mData[index];
        }
    }

    ///----------------------------------------MTK------------------------------------------------
    /// M: for [SIM Hot Swap]
    private SimHotSwapHandler mSimHotSwapHandler;

    /// M: [APN Source Type]
    private int mSourceType = ApnUtils.SOURCE_TYPE_DEFAULT;
    private boolean mReadOnlyMode = false;

    /// M: for plug-in
    private IApnSettingsExt mApnExt;

    @Override
    public void onDestroy() {
        /// M: for [SIM Hot Swap]
        if (mSimHotSwapHandler != null) {
            mSimHotSwapHandler.unregisterOnSimHotSwap();
        }
        /// M: unlisten for airplane mode & data state change
        getContext().unregisterReceiver(mReceiver);
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();

        /// M: for plug-in
        mApnExt.onDestroy();
    }

    /// M: add the receiver and observer to update UI {@
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                boolean airplaneModeEnabled = intent.getBooleanExtra("state", false);
                if (airplaneModeEnabled) {
                    Log.d(TAG, "receiver: ACTION_AIRPLANE_MODE_CHANGED in ApnEditor");
                    exitWithoutSave();
                }
            } else if (action.equals(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                Log.d(TAG, "Receiver,send MMS status, get type = " + apnType);
                if (PhoneConstants.APN_TYPE_MMS.equals(apnType)) {
                    updateScreenEnableState();
                }
            } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                Log.d(TAG, "receiver: ACTION_SIM_STATE_CHANGED");
                updateScreenEnableState();
            }
        }
    };

    private void exitWithoutSave() {
        if (mNewApn && (mApnData.getUri() != null)) {
            getContentResolver().delete(mApnData.getUri(), null, null);
        }
        finish();
    }

    /*[n migration TODO]
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        super.onMenuOpened(featureId, menu);
        // if the airplane is enable or call state not idle, then disable the
        // Menu.
        if (menu != null) {
            menu.setGroupEnabled(0, isSimReadyAndRadioOn() && !mReadOnlyMode
                    && mApnExt.getScreenEnableState(mSubId, this));
        }
        return true;
    }
    */

    /**
     * update screen enable state
     */
    private void updateScreenEnableState() {
        boolean enable = isSimReadyAndRadioOn();
        Log.d(TAG, "enable = " + enable + " mReadOnlyMode = " + mReadOnlyMode);
        getPreferenceScreen().setEnabled(enable &&
                !mReadOnlyMode && mApnExt.getScreenEnableState(mSubId, getActivity()));

        /// M: for plug-in @{
        mApnExt.setApnTypePreferenceState(mApnType, mApnType.getText());
        mApnExt.updateFieldsStatus(mSubId, mSourceType, getPreferenceScreen(), mApn.getText());
        mApnExt.setMvnoPreferenceState(mMvnoType, mMvnoMatchData);
        /// @}
    }

    private boolean isSimReadyAndRadioOn() {
        boolean simReady = false;
        simReady = TelephonyManager.SIM_STATE_READY == TelephonyManager.getDefault()
                .getSimState(SubscriptionManager.getSlotIndex(mSubId));
        boolean airplaneModeEnabled = android.provider.Settings.System.getInt(getContentResolver(),
                android.provider.Settings.System.AIRPLANE_MODE_ON, -1) == 1;

        boolean enable = !airplaneModeEnabled && simReady;
        Log.d(TAG, "isSimReadyAndRadioOn(), subId = " + mSubId + " ,airplaneModeEnabled = "
                + airplaneModeEnabled + " ,simReady = " + simReady);
        return enable;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        if (pref != null) {
            /// M: @{
            if (pref.equals(mCarrierEnabled)) {
                pref.setSummary(
                        checkNull(String.valueOf(sharedPreferences.getBoolean(key, true))));
                /// @}
                /// M: check  port value , it must be 1-65535 @{
            } else if (pref.equals(mPort)) {
                String portStr = sharedPreferences.getString(key, "");
                if (!portStr.equals("")) {
                    try {
                        int portNum = Integer.parseInt(portStr);
                        if (portNum > 65535 || portNum <= 0) {
                            Toast.makeText(getContext(), getString(R.string.apn_port_warning),
                                    Toast.LENGTH_LONG).show();
                            ((EditTextPreference) pref).setText("");
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(getContext(), getString(R.string.apn_port_warning),
                                Toast.LENGTH_LONG).show();
                        ((EditTextPreference) pref).setText("");
                    }
                }
                pref.setSummary(checkNull(sharedPreferences.getString(key, "")));
                /// @}
            } else {
                pref.setSummary(checkNull(sharedPreferences.getString(key, "")));
            }
        }
    }
}
