package com.mediatek.faceid;

import android.content.Context;
import android.util.Log;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import com.android.settings.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.provider.Settings;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnResume;

public class FaceIdDeletePreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, LifecycleObserver, OnResume {
    /** Key of the "Drm reset" preference in {@link R.xml.system_dashboard_fragment}.*/
    private static final String KEY_DELETE= "delete";
    private static final String TAG = "faceid";
    private Preference mPreference;
    private PreferenceScreen mScreen;
    private Context mContext;
    private Dialog mDialog;
    private FaceIdHelper mFaceIdHelper;

    public FaceIdDeletePreferenceController(Context context, Lifecycle lifecycle) {
        super(context);
        mContext = context;
        mFaceIdHelper = new FaceIdHelper(context);
        if (lifecycle != null) {
            lifecycle.addObserver(this);
        }
    }

    /** Hide "Factory reset" settings for secondary users. */
    @Override
    public boolean isAvailable() {
        return mFaceIdHelper.hasSetFaceIdForUser();
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
    public String getPreferenceKey() {
        return KEY_DELETE;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (KEY_DELETE.equals(preference.getKey())) {
            Log.d(TAG, "handlePreferenceTreeClick delete");
            launchConfirmDialog();
            return true;
        }
        return false;
    }

    private void launchConfirmDialog() {
        if (mDialog == null) {
            mDialog = new AlertDialog.Builder(mContext)
                .setTitle(R.string.security_settings_fingerprint_enroll_dialog_delete)
                .setIcon(R.drawable.ic_warning_24dp)
                .setMessage(R.string.faceid_delete_message)
                .setPositiveButton(R.string.delete,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        Log.d(TAG, "onClick delete");
                        mFaceIdHelper.deleteFaceId();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        }
        mDialog.show();
    }

}
