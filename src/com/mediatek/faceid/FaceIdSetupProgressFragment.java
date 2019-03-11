package com.mediatek.faceid;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.SettingsActivity;

public class FaceIdSetupProgressFragment extends Fragment implements OnClickListener{
    private static final String TAG = "FaceIdSetupProgress";
    private static final int MSG_FAIL = 1;
    private static final int TIMEOUT = 180000;

    private View mRootView;
    protected Button mCancelButton;
    protected Button mNextButton;
    protected Button mConfirmButton;
    private TextView mDetectStatusView;

    private CircleProgressView mCircleProgressView = null;
    private Timer mTimer = null;

    private CameraDevice mCameraClient = new CameraDevice(new FaceIdDetectUiHandler());
    private TextureView mPreviewTexture = null;
    private boolean mIsSurfaceCreated = false;

    private FaceIdHelper mFaceIdHelper;

    // required constructor for fragments
    public FaceIdSetupProgressFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.face_id_setup_activity, null);
        mCancelButton = (Button) rootView.findViewById(R.id.faceid_cancel_button);
        mCancelButton.setOnClickListener(this);
        mNextButton = (Button) rootView.findViewById(R.id.faceid_go_button);
        mNextButton.setOnClickListener(this);
        mConfirmButton = (Button) rootView.findViewById(R.id.faceid_confirm_button);
        mConfirmButton.setOnClickListener(this);
        //Window window = rootView.getWindow();
        //window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        //        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mPreviewTexture = (TextureView) rootView.findViewById(R.id.previewFrame);
        mPreviewTexture.setSurfaceTextureListener(new SurfaceChangeCallback());
        mCircleProgressView = (CircleProgressView) rootView.findViewById(R.id.timer_circle);

        mDetectStatusView = (TextView) rootView.findViewById(R.id.status);
        mFaceIdHelper = new FaceIdHelper(getActivity());

        return rootView;
    }

    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_FAIL:
                    handleFail();
                    break;
            }
        }
    };

    public void onClick(View v) {
        switch (v.getId()) {
           case R.id.faceid_go_button:
               handleNext();
               break;
           case R.id.faceid_cancel_button:
               handleCancel();
               break;
           case R.id.faceid_confirm_button:
               handleConfirm();
               break;
           default:
               break;
        }
    }

    private void updateMessage(int resid) {
        mDetectStatusView.setText(resid);
    }

    public void handleNext() {
        getActivity().onBackPressed();
    }

    private void handleCancel() {
        getActivity().finish();
    }

    private void handleConfirm() {
        getActivity().finish();
    }

    public void handleFail() {
        Log.d(TAG, "handleFail");
        updateMessage(R.string.faceid_setup_failed);
        mNextButton.setText(R.string.print_restart);
        mNextButton.setVisibility(View.VISIBLE);
        //todo
    }

    public void handleSuccess() {
        Log.d(TAG, "handleSuccess");
        mCancelButton.setVisibility(View.GONE);
        mConfirmButton.setVisibility(View.VISIBLE);
        updateMessage(R.string.faceid_setup_ready);
        mFaceIdHelper.enableFaceId();
        mFaceIdHelper.setFaceIdForUser();
        //todo
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        mCameraClient.stopPreview();
        mCameraClient.releaseCamera();
        super.onPause();
    }

    /**
     * Surface change call back receiver, it bind a status change listener.
     * When surface status change, use the listener to notify the change.
     */
    private class SurfaceChangeCallback implements TextureView.SurfaceTextureListener {
        SurfaceChangeCallback() {
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mIsSurfaceCreated = true;
            Log.d(TAG, "onSurfaceTextureAvailable " + width + " " + height);
            mCameraClient.openFrontCameraView(surface);
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0: degrees = 0; break;
                case Surface.ROTATION_90: degrees = 90; break;
                case Surface.ROTATION_180: degrees = 180; break;
                case Surface.ROTATION_270: degrees = 270; break;
            }
            mCameraClient.setCameraParameter(width, height, ImageFormat.NV21, 15, degrees);
            mCameraClient.startPreview();
            java.util.TimerTask task = new java.util.TimerTask() {
                public void run() {
                   mTimer = mTimer.reset();
                   mCameraClient.stopPreview();
                   mCircleProgressView.update(mTimer);
                   mCameraClient.releaseCamera();
                   mHandler.sendMessage(mHandler.obtainMessage(MSG_FAIL));
                }
            };
            mTimer = new Timer(Timer.RESET, TIMEOUT, task).start();
            mCircleProgressView.update(mTimer);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            mIsSurfaceCreated = false;
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    }

    class FaceIdDetectUiHandler extends Handler {
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage msg.what="+msg.what+", msg.arg1="+msg.arg1);
            switch(msg.what) {
                case FaceIdManager.CMD_START_REGISTER_FACE:
                    if (msg.arg1 == FaceIdManager.SUCCESS) {
                        release();
                        handleSuccess();
                    } else if(msg.arg1 == FaceIdManager.FAILURE) {
                        //TODO
                        release();
                        handleFail();
                    } else {
                        updateMessage(getStringForError(msg.arg1));
                    }
                break;
            }
        }

        private int getStringForError(int msg) {
            switch(msg) {
                case FaceIdManager.ERROR_NOT_FOUND:
                case FaceIdManager.ERROR_LEFT:
                case FaceIdManager.ERROR_TOP:
                case FaceIdManager.ERROR_RIGHT:
                case FaceIdManager.ERROR_BOTTOM:
                case FaceIdManager.ERROR_INCOMPLETE:
                case FaceIdManager.ERROR_ROTATED_LEFT:
                case FaceIdManager.ERROR_RISE:
                case FaceIdManager.ERROR_ROTATED_RIGHT:
                case FaceIdManager.ERROR_DOWN:
                case FaceIdManager.ERROR_MULTI:
                     return R.string.faceid_error_not_center;
                case FaceIdManager.ERROR_EYE_CLOSED:
                     return R.string.faceid_error_eyes_close;
                case FaceIdManager.ERROR_EYE_CLOSED_UNKNOW:
                     return R.string.faceid_error_eyes_occlusion;
                case FaceIdManager.ERROR_MOUTH_OCCLUSION:
                     return R.string.faceid_error_mouth_occlusion;
                default :
                     return R.string.faceid_error_not_found;
            }
        }

        private void release() {
            mCameraClient.stopPreview();
            mTimer = mTimer.reset();
            mCircleProgressView.update(mTimer);
            mCameraClient.releaseCamera();
        }
    }
}
