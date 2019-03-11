package com.mediatek.faceid;

import android.content.Context;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

public class UnlockPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, Preference.OnPreferenceChangeListener {
    private static final String KEY_UNLOCK_SCREEN = "unlock";
    private static final String TAG = "faceid";
    private static final String KEY_FACEID_ENABLE = "faceid_enable";
    private static final String KEY_FACEID_SET = "faceid_set";
    private Preference mPreference;

    private Context mContext;

    public UnlockPreferenceController(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        mPreference = screen.findPreference(getPreferenceKey());
        super.displayPreference(screen);
    }

    @Override
    public String getPreferenceKey() {
        return KEY_UNLOCK_SCREEN;
    }

    @Override
    public void updateState(Preference preference) {
        int setting = Settings.System.getInt(mContext.getContentResolver(),
                KEY_FACEID_SET, 0);
        ((SwitchPreference) preference).setEnabled(setting != 0);
        int enable = Settings.System.getInt(mContext.getContentResolver(),
                KEY_FACEID_ENABLE, 0);
        ((SwitchPreference) preference).setChecked(enable != 0);
    }


    public void refreshPreference() {
        updateState(mPreference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean enable = (Boolean) newValue;
        Settings.System.putInt(mContext.getContentResolver(), KEY_FACEID_ENABLE, enable ? 1 : 0);
        return true;
    }
}
