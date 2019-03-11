/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.content.Intent;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settings.R;
import com.android.settings.password.ChooseLockSettingsHelper;

import java.util.ArrayList;
import java.util.List;

public class FaceIdSettings extends DashboardFragment {
    private static final String TAG = "FaceIdSettings";
    private static final int FACEID_REQUEST = 150;
    public static final String KEY_CONFIRMATION_LAUNCHED = "confirmation_launched";
    private Context mContext;
    private ChooseLockSettingsHelper mHelper;
    private FaceIdSetupPreferenceController mFaceidSetupPreferenceController;
    private FaceIdDeletePreferenceController mFaceIdDeletePreferenceController;
    private UnlockPreferenceController mUnlockPreferenceController;
    private boolean mConfirmationLaunched;
    private static final String KEY_FACEID_SET = "faceid_set";
    private SettingsObserver mSettingsObserver;

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.SECURITY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mHelper = new ChooseLockSettingsHelper(this.getActivity(), this);
        if (icicle != null) {
            mConfirmationLaunched = icicle.getBoolean(KEY_CONFIRMATION_LAUNCHED);
        }
        Log.d(TAG, "onCreate, mConfirmationLaunched = " + mConfirmationLaunched);
        if(!mConfirmationLaunched) {
            mConfirmationLaunched = mHelper.launchConfirmationActivity(
                    FACEID_REQUEST, "faceid");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_CONFIRMATION_LAUNCHED, mConfirmationLaunched);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (mSettingsObserver == null) {
            mSettingsObserver = new SettingsObserver();
        }
        mSettingsObserver.register(true /* register */);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if (mSettingsObserver != null) {
            mSettingsObserver.register(false /* unregister */);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult resultCode = " + resultCode);
        mFaceidSetupPreferenceController.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FACEID_REQUEST && resultCode != Activity.RESULT_OK) {
            finish();
            return;
        }
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.faceid_settings;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, getLifecycle());
    }

    private  List<AbstractPreferenceController> buildPreferenceControllers(
            Context context, Lifecycle lifecycle) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        mUnlockPreferenceController = new UnlockPreferenceController(context);
        controllers.add(mUnlockPreferenceController);
        mFaceidSetupPreferenceController =
                new FaceIdSetupPreferenceController(context, lifecycle, this);
        controllers.add(mFaceidSetupPreferenceController);
        mFaceIdDeletePreferenceController =
                new FaceIdDeletePreferenceController(context, lifecycle);
        controllers.add(mFaceIdDeletePreferenceController);

        return controllers;
    }

    class SettingsObserver extends ContentObserver {
        private final Uri mFaceIdUri = Settings.System.getUriFor(KEY_FACEID_SET);

        public SettingsObserver() {
            super(new Handler());
        }

        public void register(boolean register) {
            final ContentResolver cr = mContext.getContentResolver();
            if (register) {
                cr.registerContentObserver(mFaceIdUri, false, this);
            } else {
                cr.unregisterContentObserver(this);
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (mFaceIdUri.equals(uri)) {
                Log.d(TAG, "onChange");
                mFaceidSetupPreferenceController.refreshPreference();
                mFaceIdDeletePreferenceController.refreshPreference();
                mUnlockPreferenceController.refreshPreference();
            }
        }
    }
}
