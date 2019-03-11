package com.mediatek.faceid;

import android.content.Context;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

public class FaceIdHelper {
    private static final String KEY_FACEID_ENABLE = "faceid_enable";
    private static final String KEY_FACEID_SET = "faceid_set";
    public static final String KEY_FOR_FACEID = "for_faceid";
    private Context mContext;
    private int mUserId;

    public FaceIdHelper(Context context) {
        mContext = context;
        mUserId = UserHandle.myUserId();
    }

    public void deleteFaceId() {
        disableFaceId();
        //Todo: after use api, remove this code.
        Settings.System.putInt(mContext.getContentResolver(), KEY_FACEID_SET, 0);
//       TODO: Test for NE
        FaceIdManager.getInstance().deleteFeature(String.valueOf(mUserId));
    }

    public void enableFaceId() {
        Settings.System.putInt(mContext.getContentResolver(), KEY_FACEID_ENABLE, 1);
    }

    public void disableFaceId() {
        Settings.System.putInt(mContext.getContentResolver(), KEY_FACEID_ENABLE, 0);
    }

    public boolean hasSetFaceIdForUser() {
        return Settings.System.getInt(mContext.getContentResolver(), KEY_FACEID_SET, 0) != 0;
        // waiting for manager API and userid.
    }

    //to-do: after api, remove this
    public void setFaceIdForUser() {
         Settings.System.putInt(mContext.getContentResolver(), KEY_FACEID_SET, 1);
        // waiting for manager API and userid.
    }

    public static boolean isFaceIdFeatureEnabled() {
        Log.d("faceid", "isFaceIdFeatureEnabled "+SystemProperties.get("ro.vendor.mtk_cam_security"));
        return SystemProperties.get("ro.vendor.mtk_cam_security").equals("1");
    }
}
