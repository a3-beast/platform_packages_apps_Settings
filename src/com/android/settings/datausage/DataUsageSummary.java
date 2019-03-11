/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.settings.datausage;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.NetworkTemplate;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.support.annotation.VisibleForTesting;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionPlan;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.dashboard.SummaryLoader;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settingslib.NetworkPolicyEditor;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.net.DataUsageController;

import com.mediatek.cta.CtaManager;
import com.mediatek.cta.CtaManagerFactory;
import com.mediatek.provider.MtkSettingsExt;
import com.mediatek.settings.UtilsExt;
import com.mediatek.settings.datausage.TempDataServiceDialogActivity;
import com.mediatek.settings.ext.IDataUsageSummaryExt;
import com.mediatek.settings.sim.TelephonyUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings preference fragment that displays data usage summary.
 */
public class DataUsageSummary extends DataUsageBaseFragment implements Indexable,
        DataUsageEditController {

    private static final String TAG = "DataUsageSummary";

    static final boolean LOGD = false;

    public static final String KEY_RESTRICT_BACKGROUND = "restrict_background";

    private static final String KEY_STATUS_HEADER = "status_header";

    // Mobile data keys
    public static final String KEY_MOBILE_USAGE_TITLE = "mobile_category";
    public static final String KEY_MOBILE_DATA_USAGE_TOGGLE = "data_usage_enable";
    public static final String KEY_MOBILE_DATA_USAGE = "cellular_data_usage";
    public static final String KEY_MOBILE_BILLING_CYCLE = "billing_preference";
    private static final int TYPE_TEMP_DATA_SERVICE_SUMMARY = 0;

    // Wifi keys
    public static final String KEY_WIFI_USAGE_TITLE = "wifi_category";
    public static final String KEY_WIFI_DATA_USAGE = "wifi_data_usage";

    private DataUsageSummaryPreference mSummaryPreference;
    private DataUsageSummaryPreferenceController mSummaryController;
    private NetworkTemplate mDefaultTemplate;

    /// M: for phonestate listener, when calling ,can not edit mEnableDataService.
    private int mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
    private PhoneStateListener mPhoneStateListener;
    int mTempPhoneid = 0;
    private IDataUsageSummaryExt mDataUsageSummaryExt;
    private Context mContext;
    private int mDefaultSubId;
    @Override
    public int getHelpResource() {
        return R.string.help_url_data_usage;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Log.d(TAG, "onCreate");

        mContext = getContext();
        mDataUsageSummaryExt = UtilsExt.getDataUsageSummaryExt(mContext
                .getApplicationContext());

        boolean hasMobileData = DataUsageUtils.hasMobileData(mContext);

        mDefaultSubId = DataUsageUtils.getDefaultSubscriptionId(mContext);
        if (mDefaultSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.d(TAG, "onCreate INVALID_SUBSCRIPTION_ID Mobile data false");
            hasMobileData = false;
        }
        mDefaultTemplate = DataUsageUtils.getDefaultTemplate(mContext, mDefaultSubId);
        mSummaryPreference = (DataUsageSummaryPreference) findPreference(KEY_STATUS_HEADER);

        if (!hasMobileData || !isAdmin()) {
            removePreference(KEY_RESTRICT_BACKGROUND);
        }
        boolean hasWifiRadio = DataUsageUtils.hasWifiRadio(mContext);
        if (hasMobileData) {
            addMobileSection(mDefaultSubId);
            List<SubscriptionInfo> subscriptions =
                    services.mSubscriptionManager.getActiveSubscriptionInfoList();
            if ((subscriptions != null) && (subscriptions.size() == 2)) {
                addDataServiceSection(subscriptions);
            }
            if (DataUsageUtils.hasSim(mContext) && hasWifiRadio) {
                // If the device has a SIM installed, the data usage section shows usage for mobile,
                // and the WiFi section is added if there is a WiFi radio - legacy behavior.
                addWifiSection();
            }
            // Do not add the WiFi section if either there is no WiFi radio (obviously) or if no
            // SIM is installed. In the latter case the data usage section will show WiFi usage and
            // there should be no explicit WiFi section added.
        } else if (hasWifiRadio) {
            addWifiSection();
        }
        if (DataUsageUtils.hasEthernet(mContext)) {
            addEthernetSection();
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (UserManager.get(getContext()).isAdminUser()) {
            inflater.inflate(R.menu.data_usage, menu);
        }
        /// M: Remove Cellular networks menu item on wifi-only device @{
        if (Utils.isWifiOnly(getActivity())) {
            menu.removeItem(R.id.data_usage_menu_cellular_networks);
        }
        /// @}
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.data_usage_menu_cellular_networks: {
                Log.d(TAG, "select CELLULAR_NETWORKDATA");
                final Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new ComponentName("com.android.phone",
                        "com.android.phone.MobileNetworkSettings"));
                startActivity(intent);
                return true;
            }
            /// M: for [CTA2016 requirement] @{
            // start a cellular data control page
            case R.id.data_usage_menu_cellular_data_control: {
                Log.d(TAG, "select CELLULAR_DATA");
                Intent intent = new Intent("com.mediatek.security.CELLULAR_DATA");
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "cellular data control activity not found!!!");
                }
                return true;
            }
            /// @}
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == findPreference(KEY_STATUS_HEADER)) {
            BillingCycleSettings.BytesEditorFragment.show(this, false);
            return false;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.data_usage;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        final Activity activity = getActivity();
        final ArrayList<AbstractPreferenceController> controllers = new ArrayList<>();
        mSummaryController =
                new DataUsageSummaryPreferenceController(activity, getLifecycle(), this);
        controllers.add(mSummaryController);
        getLifecycle().addObserver(mSummaryController);
        return controllers;
    }

    @VisibleForTesting
    void addMobileSection(int subId) {
        addMobileSection(subId, null);
    }

    void addMobileSection(int subId, int order) {
        addMobileSection(subId, null, order);
    }

    private void addMobileSection(int subId, SubscriptionInfo subInfo, int order) {
        TemplatePreferenceCategory category = (TemplatePreferenceCategory)
                inflatePreferences(R.xml.data_usage_cellular, order);
        Log.d(TAG, "addMobileSection with subID: " + subId
                + " orderd = " + order);
        category.setTemplate(getNetworkTemplate(subId), subId, services);
        category.pushTemplates(services);
        if (subInfo != null && !TextUtils.isEmpty(subInfo.getDisplayName())) {
            Preference title  = category.findPreference(KEY_MOBILE_USAGE_TITLE);
            title.setTitle(subInfo.getDisplayName());
        }
    }

    private Preference inflatePreferences(int resId, int order) {
        PreferenceScreen rootPreferences = getPreferenceManager().inflateFromResource(
                getPrefContext(), resId, null);
        Preference pref = rootPreferences.getPreference(0);
        rootPreferences.removeAll();

        PreferenceScreen screen = getPreferenceScreen();
        pref.setOrder(order);
        screen.addPreference(pref);

        return pref;
    }

    private void addMobileSection(int subId, SubscriptionInfo subInfo) {
        TemplatePreferenceCategory category = (TemplatePreferenceCategory)
                inflatePreferences(R.xml.data_usage_cellular);
        Log.d(TAG, "addMobileSection with subID: " + subId);
        category.setTemplate(getNetworkTemplate(subId), subId, services);
        category.pushTemplates(services);
        if (subInfo != null && !TextUtils.isEmpty(subInfo.getDisplayName())) {
            Preference title  = category.findPreference(KEY_MOBILE_USAGE_TITLE);
            title.setTitle(subInfo.getDisplayName());
        }
    }

    @VisibleForTesting
    void addWifiSection() {
        TemplatePreferenceCategory category = (TemplatePreferenceCategory)
                inflatePreferences(R.xml.data_usage_wifi);
        category.setTemplate(NetworkTemplate.buildTemplateWifiWildcard(), 0, services);
    }

    private void addEthernetSection() {
        TemplatePreferenceCategory category = (TemplatePreferenceCategory)
                inflatePreferences(R.xml.data_usage_ethernet);
        category.setTemplate(NetworkTemplate.buildTemplateEthernet(), 0, services);
    }

    private Preference inflatePreferences(int resId) {
        PreferenceScreen rootPreferences = getPreferenceManager().inflateFromResource(
                getPrefContext(), resId, null);
        Preference pref = rootPreferences.getPreference(0);
        rootPreferences.removeAll();

        PreferenceScreen screen = getPreferenceScreen();
        pref.setOrder(screen.getPreferenceCount());
        screen.addPreference(pref);

        return pref;
    }

    private NetworkTemplate getNetworkTemplate(int subscriptionId) {
        NetworkTemplate mobileAll = NetworkTemplate.buildTemplateMobileAll(
                services.mTelephonyManager.getSubscriberId(subscriptionId));
        Log.d(TAG, "getNetworkTemplate with subID: " + subscriptionId);
        return NetworkTemplate.normalize(mobileAll,
                services.mTelephonyManager.getMergedSubscriberIds());
    }

    @Override
    public void onResume() {
        super.onResume();
        ///reCreate UI if default data id changed.
        int newDefaultSubId = DataUsageUtils.getDefaultSubscriptionId(mContext);
        Log.d(TAG, "onResumed mDefaultSubId = " + mDefaultSubId
                + " newDefaultSubId = " + newDefaultSubId);
        boolean hasMobileData = DataUsageUtils.hasMobileData(mContext);
        if (mDefaultSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.d(TAG, "onResume INVALID_SUBSCRIPTION_ID Mobile data false");
            hasMobileData = false;
        }
        if (hasMobileData && mDefaultSubId != newDefaultSubId) {
            TemplatePreferenceCategory dataUsageCellularScreen =
                    (TemplatePreferenceCategory)getPreferenceScreen()
                    .findPreference("mobile_category");
            if (dataUsageCellularScreen != null) {
                int order = dataUsageCellularScreen.getOrder();
                getPreferenceScreen().removePreference(dataUsageCellularScreen);
                Log.d(TAG, "removePreferencedd and add" +
                        " (data_usage_cellular_screen) order = " + order);
                /// M: Add the mobile section when the new subscription is valid only. @{
                if (newDefaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    addMobileSection(newDefaultSubId, order);
                }
                /// @}
            }
        }
        int mainPhoneid = TelephonyUtils.getMainCapabilityPhoneId();
        if (mainPhoneid == 0) {
            mTempPhoneid = 1;
        } else {
            mTempPhoneid = 0;
        }
        updateScreenEnabled();
        updateState();
    }

    @VisibleForTesting
    static CharSequence formatUsage(Context context, String template, long usageLevel) {
        final float LARGER_SIZE = 1.25f * 1.25f;  // (1/0.8)^2
        final float SMALLER_SIZE = 1.0f / LARGER_SIZE;  // 0.8^2
        return formatUsage(context, template, usageLevel, LARGER_SIZE, SMALLER_SIZE);
    }

    static CharSequence formatUsage(Context context, String template, long usageLevel,
                                    float larger, float smaller) {
        final int FLAGS = Spannable.SPAN_INCLUSIVE_INCLUSIVE;

        final Formatter.BytesResult usedResult = Formatter.formatBytes(context.getResources(),
                usageLevel, Formatter.FLAG_CALCULATE_ROUNDED | Formatter.FLAG_IEC_UNITS);
        final SpannableString enlargedValue = new SpannableString(usedResult.value);
        enlargedValue.setSpan(new RelativeSizeSpan(larger), 0, enlargedValue.length(), FLAGS);

        final SpannableString amountTemplate = new SpannableString(
                context.getString(com.android.internal.R.string.fileSizeSuffix)
                .replace("%1$s", "^1").replace("%2$s", "^2"));
        final CharSequence formattedUsage = TextUtils.expandTemplate(amountTemplate,
                enlargedValue, usedResult.units);

        final SpannableString fullTemplate = new SpannableString(template);
        fullTemplate.setSpan(new RelativeSizeSpan(smaller), 0, fullTemplate.length(), FLAGS);
        return TextUtils.expandTemplate(fullTemplate,
                BidiFormatter.getInstance().unicodeWrap(formattedUsage.toString()));
    }

    private void updateState() {
        PreferenceScreen screen = getPreferenceScreen();
        for (int i = 1; i < screen.getPreferenceCount(); i++) {
            Preference currentPreference = screen.getPreference(i);
            /// M: Add for support temporary data service. @{
            if (currentPreference instanceof PreferenceCategory) {
                if (((PreferenceCategory) currentPreference)
                        .getKey().equals(KEY_SERVICE_CATEGORY)) {
                    if (mEnableDataService != null) {
                        boolean dataService = getDataService();
                        mEnableDataService.setChecked(dataService);
                        Log.d(TAG, "updateState, dataService=" + dataService);
                    }
                    continue;
                }
            }
            /// @}
            if (currentPreference instanceof TemplatePreferenceCategory) {
                ((TemplatePreferenceCategory) currentPreference).pushTemplates(services);
            }
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.DATA_USAGE_SUMMARY;
    }

    @Override
    public NetworkPolicyEditor getNetworkPolicyEditor() {
        return services.mPolicyEditor;
    }

    @Override
    public NetworkTemplate getNetworkTemplate() {
        Log.d(TAG, "getNetworkTemplate without subID: DefaultTemplate");
        return mDefaultTemplate;
    }

    @Override
    public void updateDataUsage() {
        updateState();
        mSummaryController.updateState(mSummaryPreference);
    }

    private static class SummaryProvider
            implements SummaryLoader.SummaryProvider {

        private final Activity mActivity;
        private final SummaryLoader mSummaryLoader;
        private final DataUsageController mDataController;

        public SummaryProvider(Activity activity, SummaryLoader summaryLoader) {
            mActivity = activity;
            mSummaryLoader = summaryLoader;
            mDataController = new DataUsageController(activity);
        }

        @Override
        public void setListening(boolean listening) {
            if (listening) {
                if (DataUsageUtils.hasSim(mActivity)) {
                    mSummaryLoader.setSummary(this,
                            mActivity.getString(R.string.data_usage_summary_format,
                                    formatUsedData()));
                } else {
                    final DataUsageController.DataUsageInfo info =
                            mDataController
                                    .getDataUsageInfo(NetworkTemplate.buildTemplateWifiWildcard());

                    if (info == null) {
                        mSummaryLoader.setSummary(this, null);
                    } else {
                        final CharSequence wifiFormat = mActivity
                                .getText(R.string.data_usage_wifi_format);
                        final CharSequence sizeText =
                                DataUsageUtils.formatDataUsage(mActivity, info.usageLevel);
                        mSummaryLoader.setSummary(this,
                                TextUtils.expandTemplate(wifiFormat, sizeText));
                    }
                }
            }
        }

        private CharSequence formatUsedData() {
            SubscriptionManager subscriptionManager = (SubscriptionManager) mActivity
                .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            int defaultSubId = subscriptionManager.getDefaultSubscriptionId();
            if (defaultSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return formatFallbackData();
            }
            SubscriptionPlan dfltPlan = DataUsageSummaryPreferenceController
                    .getPrimaryPlan(subscriptionManager, defaultSubId);
            if (dfltPlan == null) {
                return formatFallbackData();
            }
            if (DataUsageSummaryPreferenceController.unlimited(dfltPlan.getDataLimitBytes())) {
                return DataUsageUtils.formatDataUsage(mActivity, dfltPlan.getDataUsageBytes());
            } else {
                return Utils.formatPercentage(dfltPlan.getDataUsageBytes(),
                    dfltPlan.getDataLimitBytes());
            }
        }

        private CharSequence formatFallbackData() {
            DataUsageController.DataUsageInfo info = mDataController.getDataUsageInfo();
            if (info == null) {
                return DataUsageUtils.formatDataUsage(mActivity, 0);
            } else if (info.limitLevel <= 0) {
                return DataUsageUtils.formatDataUsage(mActivity, info.usageLevel);
            } else {
                return Utils.formatPercentage(info.usageLevel, info.limitLevel);
            }
        }
    }

    public static final SummaryLoader.SummaryProviderFactory SUMMARY_PROVIDER_FACTORY
        = SummaryProvider::new;

    /**
     * For search
     */
    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider() {

            @Override
            public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                    boolean enabled) {
                List<SearchIndexableResource> resources = new ArrayList<>();
                SearchIndexableResource resource = new SearchIndexableResource(context);
                resource.xmlResId = R.xml.data_usage;
                resources.add(resource);

                resource = new SearchIndexableResource(context);
                resource.xmlResId = R.xml.data_usage_cellular;
                resources.add(resource);

                resource = new SearchIndexableResource(context);
                resource.xmlResId = R.xml.data_usage_wifi;
                resources.add(resource);

                return resources;
            }

            @Override
            public List<String> getNonIndexableKeys(Context context) {
                List<String> keys = super.getNonIndexableKeys(context);

                if (!DataUsageUtils.hasMobileData(context)) {
                    keys.add(KEY_MOBILE_USAGE_TITLE);
                    keys.add(KEY_MOBILE_DATA_USAGE_TOGGLE);
                    keys.add(KEY_MOBILE_DATA_USAGE);
                    keys.add(KEY_MOBILE_BILLING_CYCLE);
                }

                if (!DataUsageUtils.hasWifiRadio(context)) {
                    keys.add(KEY_WIFI_DATA_USAGE);
                }

                // This title is named Wifi, and will confuse users.
                keys.add(KEY_WIFI_USAGE_TITLE);

                return keys;
            }
        };

    ///------------------------------------MTK------------------------------------------------
    // Data service keys
    public static final String KEY_DATA_SERVICE_ENABLE = "data_service_enable";
    public static final String KEY_SERVICE_CATEGORY = "service_category";
    private final static String ONE = "1";
    private static final String DATA_SERVICE_ENABLED = MtkSettingsExt.Global.DATA_SERVICE_ENABLED;

    private boolean mIsAirplaneModeOn;
    private SwitchPreference mEnableDataService;

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        /// M: for [CTA2016 requirement] @{
        MenuItem cellularDataControl = menu.findItem(R.id.data_usage_menu_cellular_data_control);
        CtaManager manager = CtaManagerFactory.getInstance().makeCtaManager();
        boolean isCtaSupported = manager.isCtaSupported();
        Log.d(TAG, "isCtaSupported = " + isCtaSupported);
        if (isCtaSupported) {
            if (cellularDataControl != null) {
                cellularDataControl.setVisible(true);
            }
        } else {
            if (cellularDataControl != null) {
                cellularDataControl.setVisible(false);
            }
        }
        /// @}
    }

    /// M: [CMCC VOLTE] @{
    private void addDataServiceSection(List<SubscriptionInfo> subscriptions) {
        if (!isDataServiceSupport()) {
            return;
        }

        Log.d(TAG, "addDataServiceSection..");
        if ((subscriptions == null) || (subscriptions.size() != 2)) {
            Log.d(TAG, "subscriptions size != 2");
            return;
        }

        PreferenceCategory category = (PreferenceCategory)
                inflatePreferences(R.xml.data_service_cellular);
        mEnableDataService = (SwitchPreference) findPreference(KEY_DATA_SERVICE_ENABLE);
        /// set summary for opeartor.
        String customerString = mDataUsageSummaryExt.customTempDataSummary(
                mEnableDataService.getSummary().toString(),
                TYPE_TEMP_DATA_SERVICE_SUMMARY);
        mEnableDataService.setSummary(customerString);
        mEnableDataService.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Log.d(TAG, "onPreferenceChange, preference=" + preference.getTitle());
                if (preference == mEnableDataService) {
                    if (!mEnableDataService.isChecked()) {
                        showDataServiceDialog();
                        mEnableDataService.setEnabled(false);
                        return false;
                    }
                    setDataService(0);
                }
                return true;
            }
        });

        mIsAirplaneModeOn = TelephonyUtils.isAirplaneModeOn(getContext());
        int mainPhoneid = TelephonyUtils.getMainCapabilityPhoneId();
        if (mainPhoneid == 0) {
            mTempPhoneid = 1;
        } else {
            mTempPhoneid = 0;
        }
        updateScreenEnabled();
        boolean dataServiceMode = getDataService();
        mEnableDataService.setChecked(dataServiceMode);
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(DATA_SERVICE_ENABLED), true, mContentObserver);

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        // For radio on/off
        intentFilter.addAction(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE);
        intentFilter.addAction(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_FAILED);
        mDataUsageSummaryExt.customReceiver(intentFilter);
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        int subid = SubscriptionManager.getSubId(mTempPhoneid)[0];
        tm.listen(getPhoneStateListener(mTempPhoneid, subid), PhoneStateListener.LISTEN_CALL_STATE);

        getContext().registerReceiver(mReceiver, intentFilter);
    }

    private PhoneStateListener getPhoneStateListener(int phoneId, int subId) {
        mPhoneStateListener = new PhoneStateListener(subId) {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                Log.d(TAG, "onCallStateChanged state = " + state);
                updateScreenEnabled();
            }
        };
        return mPhoneStateListener;
    }

    private void showDataServiceDialog() {
        Log.d(TAG, "showDataServiceDialog");
        Intent newIntent = new Intent(getContext(), TempDataServiceDialogActivity.class);
        startActivity(newIntent);
    }

    private void updateScreenEnabled() {
        boolean isSwitching = TelephonyUtils.isCapabilitySwitching();
        Log.d(TAG, "updateScreenEnabled, mIsAirplaneModeOn = " + mIsAirplaneModeOn
                + ", isSwitching = " + isSwitching
                + ", mTempPhoneid = " + mTempPhoneid);
        if (mEnableDataService != null) {
            mEnableDataService.setEnabled(!mIsAirplaneModeOn && !isSwitching
                && !mDataUsageSummaryExt.customTempdata(mTempPhoneid));
            mDataUsageSummaryExt.customTempdataHide(mEnableDataService);
        } else {
            Log.d(TAG, "mEnableDataService == null");
        }
    }

    private boolean getDataService() {
        int dataServie = 0;
        Context context = getContext();
        if (context != null) {
            dataServie = Settings.Global.getInt(context.getContentResolver(),
                    DATA_SERVICE_ENABLED, 0);
        }
        Log.d(TAG, "getDataService =" + dataServie);
        return dataServie == 0 ? false : true;
    }

    private void setDataService(int value) {
        Log.d(TAG, "setDataService =" + value);
        Settings.Global.putInt(getContext().getContentResolver(),
                DATA_SERVICE_ENABLED, value);
    }

    private ContentObserver mContentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (mEnableDataService == null) {
                Log.d(TAG, "onChange mEnableDataService == null");
                return;
            }

            boolean dataService = getDataService();
            Log.d(TAG, "onChange dataService = " + dataService
                    + ", isChecked = " + mEnableDataService.isChecked());
            if (dataService != mEnableDataService.isChecked()) {
                mEnableDataService.setChecked(dataService);
            }
        }
    };

    // Receiver to handle different actions
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "mReceiver action = " + action);
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                mIsAirplaneModeOn = intent.getBooleanExtra("state", false);
                updateScreenEnabled();
            } else if (action.equals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)
                    || mDataUsageSummaryExt.customDualReceiver(action)) {
                updateScreenEnabled();
            } else if (action.equals(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_DONE)
                    || action.equals(TelephonyIntents.ACTION_SET_RADIO_CAPABILITY_FAILED)) {
                updateScreenEnabled();
            }
        }
    };

    private static boolean isDataServiceSupport() {
        boolean isSupport = ONE.equals(
                SystemProperties.get("persist.vendor.radio.smart.data.switch")) ? true : false;
        return isSupport;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        if (!isDataServiceSupport()) {
            return;
        }

        if (mEnableDataService != null) {
            getContentResolver().unregisterContentObserver(mContentObserver);
            getContext().unregisterReceiver(mReceiver);
            mEnableDataService = null;
        }
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (null != mPhoneStateListener) {
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }
    /// @}

}
