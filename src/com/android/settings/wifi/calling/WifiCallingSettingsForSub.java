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

package com.android.settings.wifi.calling;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
/// M: Add for checking if package exists or not.
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceClickListener;
import android.support.v7.preference.PreferenceScreen;
/// M: Add for checking if phone is in call.
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
/// M: Add for checking if IMS is switching or not.
import android.widget.Toast;

import com.android.ims.ImsConfig;
/// M: Add for checking if IMS is switching or not.
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.Phone;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.widget.SwitchBar;

/// M: Add for checking IMS state. @{
import com.mediatek.ims.internal.MtkImsManagerEx;
import com.mediatek.internal.telephony.MtkPhoneConstants;
/// @}
/// M: Add for WFC plugin. @{
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.ext.DefaultWfcSettingsExt;
import com.mediatek.settings.ext.IWfcSettingsExt;
/// @}
/// M: Add for supporting SIM hot swap. @{
import com.mediatek.settings.sim.SimHotSwapHandler;
import com.mediatek.settings.sim.SimHotSwapHandler.OnSimHotSwapListener;
/// @}

/// M: Add for checking carrier config.
import mediatek.telephony.MtkCarrierConfigManager;

/**
 * This is the inner class of {@link WifiCallingSettings} fragment.
 * The preference screen lets you enable/disable Wi-Fi Calling and change Wi-Fi Calling mode.
 */
public class WifiCallingSettingsForSub extends SettingsPreferenceFragment
        implements SwitchBar.OnSwitchChangeListener,
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "WifiCallingForSub";

    //String keys for preference lookup
    private static final String BUTTON_WFC_MODE = "wifi_calling_mode";
    private static final String BUTTON_WFC_ROAMING_MODE = "wifi_calling_roaming_mode";
    private static final String PREFERENCE_EMERGENCY_ADDRESS = "emergency_address_key";

    private static final int REQUEST_CHECK_WFC_EMERGENCY_ADDRESS = 1;

    public static final String EXTRA_LAUNCH_CARRIER_APP = "EXTRA_LAUNCH_CARRIER_APP";

    protected static final String FRAGMENT_BUNDLE_SUBID = "subId";

    public static final int LAUCH_APP_ACTIVATE = 0;
    public static final int LAUCH_APP_UPDATE = 1;

    //UI objects
    private SwitchBar mSwitchBar;
    private Switch mSwitch;
    private ListPreference mButtonWfcMode;
    private ListPreference mButtonWfcRoamingMode;
    private Preference mUpdateAddress;
    private TextView mEmptyView;

    private boolean mValidListener = false;
    private boolean mEditableWfcMode = true;
    private boolean mEditableWfcRoamingMode = true;

    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private ImsManager mImsManager;

    /// M: Add for checking carrier config.
    private boolean mRemoveWfcPreferenceMode;
    /// M: Add for WFC plugin.
    IWfcSettingsExt mWfcExt;
    /// M: Add for ALPS03044866, should not show the dialog every time Activity resumed
    private boolean mAlertAlreadyShowed = false;
    /// M: Add for supporting SIM hot swap.
    private SimHotSwapHandler mSimHotSwapHandler;
    /// M: Create TelephonyManager for the specified subId.
    private TelephonyManager mTelephonyManager;

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        /*
         * Enable/disable controls when in/out of a call and depending on
         * TTY mode and TTY support over VoLTE.
         * @see android.telephony.PhoneStateListener#onCallStateChanged(int,
         * java.lang.String)
         */
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            final SettingsActivity activity = (SettingsActivity) getActivity();
            boolean isNonTtyOrTtyOnVolteEnabled = mImsManager.isNonTtyOrTtyOnVolteEnabled();
            boolean isWfcEnabled = mSwitchBar.isChecked()
                    && isNonTtyOrTtyOnVolteEnabled;

            mSwitchBar.setEnabled((state == TelephonyManager.CALL_STATE_IDLE)
                    && isNonTtyOrTtyOnVolteEnabled);

            boolean isWfcModeEditable = true;
            boolean isWfcRoamingModeEditable = false;
            final CarrierConfigManager configManager = (CarrierConfigManager)
                    activity.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            if (configManager != null) {
                PersistableBundle b = configManager.getConfigForSubId(mSubId);
                if (b != null) {
                    isWfcModeEditable = b.getBoolean(
                            CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL);
                    isWfcRoamingModeEditable = b.getBoolean(
                            CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL);
                }
            }

            Preference pref = getPreferenceScreen().findPreference(BUTTON_WFC_MODE);
            if (pref != null) {
                pref.setEnabled(isWfcEnabled && isWfcModeEditable
                        && (state == TelephonyManager.CALL_STATE_IDLE));
            }
            Preference pref_roam =
                    getPreferenceScreen().findPreference(BUTTON_WFC_ROAMING_MODE);
            if (pref_roam != null) {
                pref_roam.setEnabled(isWfcEnabled && isWfcRoamingModeEditable
                        && (state == TelephonyManager.CALL_STATE_IDLE));
            }
        }
    };

    private final OnPreferenceClickListener mUpdateAddressListener =
            new OnPreferenceClickListener() {
                /*
                 * Launch carrier emergency address managemnent activity
                 */
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent carrierAppIntent = getCarrierActivityIntent();
                    if (carrierAppIntent != null) {
                        carrierAppIntent.putExtra(EXTRA_LAUNCH_CARRIER_APP, LAUCH_APP_UPDATE);
                        startActivity(carrierAppIntent);
                    }
                    return true;
                }
            };

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final SettingsActivity activity = (SettingsActivity) getActivity();

        mEmptyView = getView().findViewById(android.R.id.empty);
        setEmptyView(mEmptyView);
        String emptyViewText = activity.getString(R.string.wifi_calling_off_explanation)
                + activity.getString(R.string.wifi_calling_off_explanation_2);
        mEmptyView.setText(emptyViewText);

        mSwitchBar = getView().findViewById(R.id.switch_bar);
        mSwitchBar.show();
        mSwitch = mSwitchBar.getSwitch();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mSwitchBar.hide();
    }

    private void showAlert(Intent intent) {
        Context context = getActivity();

        CharSequence title = intent.getCharSequenceExtra(Phone.EXTRA_KEY_ALERT_TITLE);
        CharSequence message = intent.getCharSequenceExtra(Phone.EXTRA_KEY_ALERT_MESSAGE);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message)
                .setTitle(title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private IntentFilter mIntentFilter;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive, action=" + action);
            if (action.equals(ImsManager.ACTION_IMS_REGISTRATION_ERROR)) {
                // If this fragment is active then we are immediately
                // showing alert on screen. There is no need to add
                // notification in this case.
                //
                // In order to communicate to ImsPhone that it should
                // not show notification, we are changing result code here.
                setResultCode(Activity.RESULT_CANCELED);

                showAlert(intent);
            /// M: Listen to WFC config changes and update the screen. @{
            } else if (action.equals(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)) {
                int phoneId = intent.getIntExtra(CarrierConfigManager.EXTRA_SLOT_INDEX,
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                int currentPhoneId = SubscriptionManager.getPhoneId(mSubId);
                if (phoneId != SubscriptionManager.INVALID_SIM_SLOT_INDEX
                        && phoneId == currentPhoneId
                        && !mImsManager.isWfcEnabledByPlatform()) {
                    Log.d(TAG, "Carrier config changed, finish WFC activity");
                    getActivity().finish();
                }
            }
            /// @}
        }
    };

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.WIFI_CALLING_FOR_SUB;
    }

    @Override
    public int getHelpResource() {
        // Return 0 to suppress help icon. The help will be populated by parent page.
        return 0;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.wifi_calling_settings);

        /// M: Add for WFC plugin. @{
        mWfcExt = UtilsExt.getWfcSettingsExt(getActivity());
        mWfcExt.initPlugin(this);
        /// @}

        // SubId should always be specified when creating this fragment. Either through
        // fragment.setArguments() or through savedInstanceState.
        if (getArguments() != null && getArguments().containsKey(FRAGMENT_BUNDLE_SUBID)) {
            mSubId = getArguments().getInt(FRAGMENT_BUNDLE_SUBID);
        } else if (savedInstanceState != null) {
            mSubId = savedInstanceState.getInt(
                    FRAGMENT_BUNDLE_SUBID, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        }

        mImsManager = ImsManager.getInstance(
                getActivity(), SubscriptionManager.getPhoneId(mSubId));

        mButtonWfcMode = (ListPreference) findPreference(BUTTON_WFC_MODE);
        mButtonWfcMode.setOnPreferenceChangeListener(this);

        mButtonWfcRoamingMode = (ListPreference) findPreference(BUTTON_WFC_ROAMING_MODE);
        mButtonWfcRoamingMode.setOnPreferenceChangeListener(this);

        mUpdateAddress = (Preference) findPreference(PREFERENCE_EMERGENCY_ADDRESS);
        mUpdateAddress.setOnPreferenceClickListener(mUpdateAddressListener);

        /// M: Add for WFC plugin.
        mWfcExt.addOtherCustomPreference();

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ImsManager.ACTION_IMS_REGISTRATION_ERROR);
        /// M: Listen to Carrier config changes
        mIntentFilter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);

        /// M: Create TelephonyManager for the specified subId. @{
        mTelephonyManager = TelephonyManager.from(getActivity())
                .createForSubscriptionId(mSubId);
        /// @}

        /// M: Add for supporting SIM hot swap. @{
        mSimHotSwapHandler = new SimHotSwapHandler(getActivity());
        mSimHotSwapHandler.registerOnSimHotSwap(new OnSimHotSwapListener() {
            @Override
            public void onSimHotSwap() {
                Log.d(TAG, "onSimHotSwap, finish Activity.");
                getActivity().finish();
            }
        });

        /// M: Add for WFC plugin.
        mWfcExt.onWfcSettingsEvent(DefaultWfcSettingsExt.CREATE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(FRAGMENT_BUNDLE_SUBID, mSubId);
        super.onSaveInstanceState(outState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.wifi_calling_settings_preferences, container, false);

        final ViewGroup prefs_container = view.findViewById(R.id.prefs_container);
        Utils.prepareCustomPreferencesList(container, view, prefs_container, false);
        View prefs = super.onCreateView(inflater, prefs_container, savedInstanceState);
        prefs_container.addView(prefs);

        return view;
    }

    private void updateBody() {
        CarrierConfigManager configManager = (CarrierConfigManager)
                getSystemService(Context.CARRIER_CONFIG_SERVICE);
        boolean isWifiOnlySupported = true;

        /// M: WFC TTY feature for operator
        boolean disableWifiCalling = false;

        if (configManager != null) {
            PersistableBundle b = configManager.getConfigForSubId(mSubId);
            if (b != null) {
                mEditableWfcMode = b.getBoolean(
                        CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL);
                mEditableWfcRoamingMode = b.getBoolean(
                        CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL);
                isWifiOnlySupported = b.getBoolean(
                        CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, true);
                /// M: Add for checking carrier config. @{
                mRemoveWfcPreferenceMode = b.getBoolean(
                        MtkCarrierConfigManager.MTK_KEY_WFC_REMOVE_PREFERENCE_MODE_BOOL, false);
                Log.d(TAG, "updateBody, removeWfcPreferenceMode=" + mRemoveWfcPreferenceMode);
                disableWifiCalling = b.getBoolean(
                        MtkCarrierConfigManager.MTK_KEY_UPDATE_WIFICALLING_BY_TTY, false);
                Log.d(TAG, "[TTY]WFC disableWifiCalling" + disableWifiCalling);
                /// @}
            }
        }

        if (!isWifiOnlySupported) {
            mButtonWfcMode.setEntries(R.array.wifi_calling_mode_choices_without_wifi_only);
            mButtonWfcMode.setEntryValues(R.array.wifi_calling_mode_values_without_wifi_only);
            mButtonWfcRoamingMode.setEntries(
                    R.array.wifi_calling_mode_choices_v2_without_wifi_only);
            mButtonWfcRoamingMode.setEntryValues(
                    R.array.wifi_calling_mode_values_without_wifi_only);
        }


        // NOTE: Buttons will be enabled/disabled in mPhoneStateListener
        boolean wfcEnabled = mImsManager.isWfcEnabledByUser()
                && mImsManager.isNonTtyOrTtyOnVolteEnabled();
        mSwitch.setChecked(wfcEnabled);
        int wfcMode = mImsManager.getWfcMode(false);
        int wfcRoamingMode = mImsManager.getWfcMode(true);
        mButtonWfcMode.setValue(Integer.toString(wfcMode));
        mButtonWfcRoamingMode.setValue(Integer.toString(wfcRoamingMode));
        updateButtonWfcMode(wfcEnabled, wfcMode, wfcRoamingMode);

        /// M: Update enable state.
        updateEnabledState();

       /// M: WFC TTY feature for operator @{
        boolean isNonTtyOrTtyOnVolteEnabled = mImsManager.isNonTtyOrTtyOnVolteEnabled();
        Log.d(TAG, "[TTY]isNonTtyOrTtyOnVolteEnabled :" + isNonTtyOrTtyOnVolteEnabled
                    + " wfcEnabled :" + wfcEnabled);
        if (!isNonTtyOrTtyOnVolteEnabled && disableWifiCalling && !wfcEnabled) {
            Context context = getActivity();
            String disableWifiCallingMessage = context.getString(
                        R.string.tty_wfc_disable_wfc_setting_message);
            Toast.makeText(context, disableWifiCallingMessage, Toast.LENGTH_LONG).show();
        }
        /// @}

        /// M: Add for WFC plugin. @{
        mWfcExt.updateWfcModePreference(
                getPreferenceScreen(), mButtonWfcMode, wfcEnabled, wfcMode);
        /// @}
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        final Context context = getActivity();

        updateBody();

        if (mImsManager.isWfcEnabledByPlatform()) {
            /// M: Listen phone state with specified TelephonyManager.
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

            mSwitchBar.addOnSwitchChangeListener(this);

            mValidListener = true;
        }

        context.registerReceiver(mIntentReceiver, mIntentFilter);

        Intent intent = getActivity().getIntent();
        /// M: Add for ALPS03044866, should not show the dialog every time Activity resumed @{
        if (intent.getBooleanExtra(Phone.EXTRA_KEY_ALERT_SHOW, false) && !mAlertAlreadyShowed) {
            showAlert(intent);
            mAlertAlreadyShowed = true;
        }
        /// @}

        /// M: Add for WFC plugin.
        mWfcExt.onWfcSettingsEvent(DefaultWfcSettingsExt.RESUME);
    }

    @Override
    public void onPause() {
        super.onPause();

        final Context context = getActivity();

        if (mValidListener) {
            mValidListener = false;

            /// M: Listen phone state with specified TelephonyManager.
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

            mSwitchBar.removeOnSwitchChangeListener(this);
        }

        context.unregisterReceiver(mIntentReceiver);

        /// M: Add for WFC plugin.
        mWfcExt.onWfcSettingsEvent(DefaultWfcSettingsExt.PAUSE);
    }

    @Override
    public void onDestroy() {
        /// M: Add for supporting SIM hot swap.
        mSimHotSwapHandler.unregisterOnSimHotSwap();
        /// M: Add for WFC plugin.
        mWfcExt.onWfcSettingsEvent(DefaultWfcSettingsExt.DESTROY);

        super.onDestroy();
    }

    /**
     * Listens to the state change of the switch.
     */
    @Override
    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        Log.d(TAG, "onSwitchChanged(" + isChecked + ")");

        /// M: Check whether wfc switch is to be toggled or not. @{
        // Revert user action with toast when IMS is enabling or disabling.
        if (isInSwitchProcess()) {
            Log.d(TAG, "onSwitchChanged, switching process is ongoing.");
            Toast.makeText(getActivity(), R.string.Switch_not_in_use_string,
                    Toast.LENGTH_SHORT).show();
            mSwitchBar.setChecked(!isChecked);
            return;
        }
        /// @}

        if (!isChecked) {
            updateWfcMode(false);
            return;
        }

        // Call address management activity before turning on WFC
        Intent carrierAppIntent = getCarrierActivityIntent();
        if (carrierAppIntent != null) {
            carrierAppIntent.putExtra(EXTRA_LAUNCH_CARRIER_APP, LAUCH_APP_ACTIVATE);
            startActivityForResult(carrierAppIntent, REQUEST_CHECK_WFC_EMERGENCY_ADDRESS);
        } else {
            updateWfcMode(true);
        }
    }

    /*
     * Get the Intent to launch carrier emergency address management activity.
     * Return null when no activity found.
     */
    private Intent getCarrierActivityIntent() {
        // Retrive component name from carrier config
        CarrierConfigManager configManager =
                getActivity().getSystemService(CarrierConfigManager.class);
        if (configManager == null) return null;

        PersistableBundle bundle = configManager.getConfigForSubId(mSubId);
        if (bundle == null) return null;

        String carrierApp = bundle.getString(
                CarrierConfigManager.KEY_WFC_EMERGENCY_ADDRESS_CARRIER_APP_STRING);
        if (TextUtils.isEmpty(carrierApp)) return null;

        ComponentName componentName = ComponentName.unflattenFromString(carrierApp);
        if (componentName == null) return null;

        /// M: Check if component is registered with Package Manager or not. @{
        if (!isPackageExist(getActivity(), componentName)) {
            return null;
        }
        /// @}

        // Build and return intent
        Intent intent = new Intent();
        intent.setComponent(componentName);
        return intent;
    }

    /*
     * Turn on/off WFC mode with ImsManager and update UI accordingly
     */
    private void updateWfcMode(boolean wfcEnabled) {
        Log.i(TAG, "updateWfcMode(" + wfcEnabled + ")");
        mImsManager.setWfcSetting(wfcEnabled);

        int wfcMode = mImsManager.getWfcMode(false);
        int wfcRoamingMode = mImsManager.getWfcMode(true);
        updateButtonWfcMode(wfcEnabled, wfcMode, wfcRoamingMode);

        /// M: Add for WFC plugin. @{
        mWfcExt.updateWfcModePreference(
                getPreferenceScreen(), mButtonWfcMode, wfcEnabled, wfcMode);
        /// @}

        if (wfcEnabled) {
            mMetricsFeatureProvider.action(getActivity(), getMetricsCategory(), wfcMode);
        } else {
            mMetricsFeatureProvider.action(getActivity(), getMetricsCategory(), -1);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        final Context context = getActivity();

        if (requestCode == REQUEST_CHECK_WFC_EMERGENCY_ADDRESS) {
            Log.d(TAG, "WFC emergency address activity result = " + resultCode);

            if (resultCode == Activity.RESULT_OK) {
                updateWfcMode(true);
            }
        }
    }

    private void updateButtonWfcMode(boolean wfcEnabled,
            int wfcMode, int wfcRoamingMode) {
        Log.d(TAG, "updateButtonWfcMode, wfcEnabled=" + wfcEnabled);
        mButtonWfcMode.setSummary(getWfcModeSummary(wfcMode));
        mButtonWfcMode.setEnabled(wfcEnabled && mEditableWfcMode);
        // mButtonWfcRoamingMode.setSummary is not needed; summary is just selected value.
        mButtonWfcRoamingMode.setEnabled(wfcEnabled && mEditableWfcRoamingMode);

        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        boolean updateAddressEnabled = (getCarrierActivityIntent() != null);
        if (wfcEnabled) {
            if (mEditableWfcMode) {
                preferenceScreen.addPreference(mButtonWfcMode);
            } else {
                // Don't show WFC (home) preference if it's not editable.
                preferenceScreen.removePreference(mButtonWfcMode);
            }
            if (mEditableWfcRoamingMode) {
                preferenceScreen.addPreference(mButtonWfcRoamingMode);
            } else {
                // Don't show WFC roaming preference if it's not editable.
                preferenceScreen.removePreference(mButtonWfcRoamingMode);
            }
            if (updateAddressEnabled) {
                preferenceScreen.addPreference(mUpdateAddress);
            } else {
                preferenceScreen.removePreference(mUpdateAddress);
            }
            /// M: Add for checking carrier config. @{
            if (mRemoveWfcPreferenceMode) {
                preferenceScreen.removePreference(mButtonWfcMode);
            }
            /// @}
        } else {
            preferenceScreen.removePreference(mButtonWfcMode);
            preferenceScreen.removePreference(mButtonWfcRoamingMode);
            preferenceScreen.removePreference(mUpdateAddress);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mButtonWfcMode) {
            Log.d(TAG, "onPreferenceChange mButtonWfcMode " + newValue);
            mButtonWfcMode.setValue((String) newValue);
            int buttonMode = Integer.valueOf((String) newValue);
            int currentWfcMode = mImsManager.getWfcMode(false);
            if (buttonMode != currentWfcMode) {
                mImsManager.setWfcMode(buttonMode, false);
                mButtonWfcMode.setSummary(getWfcModeSummary(buttonMode));
                mMetricsFeatureProvider.action(getActivity(), getMetricsCategory(), buttonMode);
            }
            if (!mEditableWfcRoamingMode) {
                int currentWfcRoamingMode = mImsManager.getWfcMode(true);
                if (buttonMode != currentWfcRoamingMode) {
                    mImsManager.setWfcMode(buttonMode, true);
                    // mButtonWfcRoamingMode.setSummary is not needed; summary is selected value
                }
            }
        } else if (preference == mButtonWfcRoamingMode) {
            mButtonWfcRoamingMode.setValue((String) newValue);
            int buttonMode = Integer.valueOf((String) newValue);
            int currentMode = mImsManager.getWfcMode(true);
            if (buttonMode != currentMode) {
                mImsManager.setWfcMode(buttonMode, true);
                // mButtonWfcRoamingMode.setSummary is not needed; summary is just selected value.
                mMetricsFeatureProvider.action(getActivity(), getMetricsCategory(), buttonMode);
            }
        }
        return true;
    }

    private int getWfcModeSummary(int wfcMode) {
        Log.d(TAG, "getWfcModeSummary, wfcMode=" + wfcMode);
        int resId = com.android.internal.R.string.wifi_calling_off_summary;
        if (mImsManager.isWfcEnabledByUser()) {
            switch (wfcMode) {
                case ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY:
                    resId = com.android.internal.R.string.wfc_mode_wifi_only_summary;
                    break;
                case ImsConfig.WfcModeFeatureValueConstants.CELLULAR_PREFERRED:
                    resId = com.android.internal.R.string.wfc_mode_cellular_preferred_summary;
                    break;
                case ImsConfig.WfcModeFeatureValueConstants.WIFI_PREFERRED:
                    resId = com.android.internal.R.string.wfc_mode_wifi_preferred_summary;
                    break;
                default:
                    Log.e(TAG, "Unexpected WFC mode value: " + wfcMode);
            }
        }
        return resId;
    }

    /// M: Check if the IMS is enabling or disabling. @{
    private boolean isInSwitchProcess() {
        int imsState = MtkPhoneConstants.IMS_STATE_DISABLED;
        try {
            imsState = MtkImsManagerEx.getInstance().getImsState(
                    SubscriptionManager.getPhoneId(mSubId));
        } catch (ImsException e) {
            return false;
        }
        Log.d(TAG, "isInSwitchProcess, imsState=" + imsState);
        return imsState == MtkPhoneConstants.IMS_STATE_DISABLING
                || imsState == MtkPhoneConstants.IMS_STATE_ENABLING;
    }
    /// @}

    /// M: Update enable state. @{
    private void updateEnabledState() {
        boolean isInCall = TelecomManager.from(getActivity()).isInCall();
        Log.d(TAG, "updateEnabledState, inCall=" + isInCall);
        if (isInCall) {
            mSwitchBar.setEnabled(false);
            mButtonWfcMode.setEnabled(false);
            mButtonWfcRoamingMode.setEnabled(false);
        }
    }
    /// @}

    /// M: check if component registered with Package Manager.
    private static boolean isPackageExist(Context context, ComponentName componentName) {
        try {
            context.getPackageManager().getActivityInfo(componentName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "package does not exist.");
            return false;
        }
        Log.d(TAG, "package exists.");
        return true;
    }
    /// @}
}
