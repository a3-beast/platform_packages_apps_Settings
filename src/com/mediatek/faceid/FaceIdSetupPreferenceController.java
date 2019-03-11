package com.mediatek.faceid;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.password.ChooseLockGeneric;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnResume;

public class FaceIdSetupPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, LifecycleObserver, OnResume {
    private static final String KEY_SETUP = "setup";
    private static final int LOCK_REQUEST = 1;
    private Preference mPreference;
    private PreferenceScreen mScreen;
    private Fragment mFragment;
    private FaceIdHelper mFaceIdHelper;
    private Context mContext;

    public FaceIdSetupPreferenceController(Context context, Lifecycle lifecycle,
            Fragment fragment) {
        super(context);
        mContext = context;
        mFragment = fragment;
        mFaceIdHelper = new FaceIdHelper(context);
        if (lifecycle != null) {
            lifecycle.addObserver(this);
        }
    }

    @Override
    public boolean isAvailable() {
        return !mFaceIdHelper.hasSetFaceIdForUser();
    }

    @Override
    public String getPreferenceKey() {
        return KEY_SETUP;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        mScreen = screen;
        mPreference = screen.findPreference(getPreferenceKey());
        super.displayPreference(screen);
    }

    @Override
    public void onResume() {
        refreshPreference();
    }

    public void refreshPreference() {
        if (!isAvailable()) {
            mScreen.removePreference(mPreference);
            return;
        }
        if (mScreen.findPreference(getPreferenceKey()) == null) {
            mScreen.addPreference(mPreference);
        }
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (KEY_SETUP.equals(preference.getKey())) {
            boolean secure = new LockPatternUtils(mContext).isSecure(UserHandle.myUserId());
            if (!secure) {
                startPasswordSetup();
            } else {
                startSetupFaceId();
            }
            return true;
        }
        return false;
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == LOCK_REQUEST && resultCode == Activity.RESULT_FIRST_USER) {
            startSetupFaceId();
            return true;
        }
        return false;
    }

    private void startPasswordSetup() {
        Intent intent = new Intent();
        intent.setClass(mContext, ChooseLockGeneric.class);
        intent.putExtra(mFaceIdHelper.KEY_FOR_FACEID, true);
        mFragment.startActivityForResult(intent, LOCK_REQUEST);
    }

    private void startSetupFaceId() {
        Intent intent = new Intent();
        intent.setClass(mContext, FaceIdSetupGuide.class);
        mContext.startActivity(intent);
    }
}
