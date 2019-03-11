package com.mediatek.faceid;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.util.Log;

import java.io.IOException;

public class CameraDevice {

    private static final String TAG = "FaceIdCameraDevice";

    private static final int OPEN = 1;
    private static final int SET_SURFACE = 2;
    private static final int START_PREVIEW = 3;
    private static final int STOP_PREVIEW = 4;
    private static final int RELEASE = 5;
    private static final int SET_PARAMETER = 6;
    private static final String HANDLER_THREAD_NAME = "FaceIdSetupCameraOperation";

    private Camera mCamera = null;
    private HandlerThread mHandlerThread = null;
    private CameraPreviewHandler mServiceHandler = null;
    private Handler mUiHandler;
    private Camera.Parameters mCameraParams;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private int mImageFormat;
    private int mFrameRate;
    private int mRotateDegree;
    private boolean mCameraInited = false;
    private FaceIdCallback mFRHandlerCallback = new FaceIdCallback();

    public CameraDevice(Handler uiHandler) {
        mUiHandler = uiHandler;
    }

    public void openFrontCameraView(SurfaceTexture texture) {
         if (mHandlerThread == null) {
             mHandlerThread = new HandlerThread(HANDLER_THREAD_NAME);
             mHandlerThread.start();
             mServiceHandler = new CameraPreviewHandler(
                     mHandlerThread.getLooper());
         }
         Message msg = mServiceHandler.obtainMessage(OPEN);
         msg.obj = texture;
         mServiceHandler.sendMessage(msg);
    }

    public void setCameraParameter(int width, int height, int format, int frameRate, int rotate) {
        mPreviewWidth = width;
        mPreviewHeight = height;
        mImageFormat = format;
        mFrameRate = frameRate;

        int numCam = Camera.getNumberOfCameras();
        for (int i = 0; i < numCam; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            //if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mRotateDegree = (info.orientation + rotate) % 360;
                mRotateDegree = (360 - mRotateDegree) % 360;
                break;
            }
        }
        Message msg = mServiceHandler.obtainMessage(SET_PARAMETER);
        mServiceHandler.sendMessage(msg);
    }

    public void setPreviewSurface(SurfaceTexture texture) {
         Message msg = mServiceHandler.obtainMessage(SET_SURFACE);
         msg.obj = texture;
         mServiceHandler.sendMessage(msg);
    }

    public void startPreview() {
         Message msg = mServiceHandler.obtainMessage(START_PREVIEW);
         mServiceHandler.sendMessage(msg);
    }

    public void stopPreview() {
         Message msg = mServiceHandler.obtainMessage(STOP_PREVIEW);
         mServiceHandler.sendMessage(msg);
    }

    public void releaseCamera() {
        Message msg = mServiceHandler.obtainMessage(RELEASE);
        mServiceHandler.sendMessage(msg);
    }

    public class CameraPreviewHandler extends Handler {
        public CameraPreviewHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what != OPEN && !mCameraInited) {
                Log.e(TAG, "camera is not init, return...");
                return;
            } else if (msg.what == OPEN && mCameraInited) {
                Log.e(TAG, "camera is already inited, return...");
                return;
            }
            switch (msg.what) {
            case OPEN:
                int numCam = Camera.getNumberOfCameras();
                int frontCamId = -1;
                int intError = FaceIdManager.getInstance().init(102, null);
                Log.d(TAG, "intError = " + intError);
                if(intError != 0) {
                    mServiceHandler.obtainMessage(RELEASE);
                    break;
                }
                for (int i = 0; i < numCam; i++) {
                    Camera.CameraInfo info = new Camera.CameraInfo();
                    Camera.getCameraInfo(i, info);
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    //if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        frontCamId = i;
                        Log.e(TAG, "front camera id: " + i);
                        break;
                    }
                }
                if (frontCamId != -1) {
                    //TODO: if camera device is occupied by others, open will throw RuntimeException.
                    //mCamera = Camera.open(frontCamId);
                    //mCameraParams = mCamera.getParameters();
                    Log.e(TAG, "front camera " + mCamera);
                    mCameraInited = true;
                    if (msg.obj != null) {
                        /*try {
                            mCamera.setPreviewTexture((SurfaceTexture) msg.obj);
                        } catch (IOException e) {
                            Log.e(TAG, "set previewTexture failed " + e.getMessage());
                            mCameraInited = false;
                        }*/
                    }
                }
                break;
            case SET_SURFACE:
                /*
                try {
                    mCamera.setPreviewTexture((SurfaceTexture) msg.obj);
                } catch (IOException e) {
                    Log.e(TAG, "set previewTexture failed " + e.getMessage());
                }*/
                break;
            case START_PREVIEW:
                //mCamera.startPreview();
                FaceIdManager.getInstance().registerCallback(UserHandle.myUserId(), mFRHandlerCallback);
                int detectError = FaceIdManager.getInstance().detectAndSaveFeature(String.format("%d", UserHandle.myUserId()));
                Log.d(TAG, "detectError = " + detectError);
                break;
            case SET_PARAMETER:
                //mCameraParams.setPreviewSize(mPreviewWidth, mPreviewHeight);
                //mCameraParams.setPreviewFormat(mImageFormat);
                //mCameraParams.setPreviewFrameRate(mFrameRate);
                //mCameraParams.set("mtk-cam-mode",1);
                //mCamera.setParameters(mCameraParams);
                //mCamera.setDisplayOrientation(mRotateDegree);
                break;
            case STOP_PREVIEW:
                Log.e(TAG, "stop previewTexture");
                //mCamera.stopPreview();
                FaceIdManager.getInstance().unregisterCallback(UserHandle.myUserId(), mFRHandlerCallback);
                FaceIdManager.getInstance().release();
                break;
            case RELEASE:
                //mCamera.release();
                mCameraInited = false;
                mCamera = null;
                break;
            default:
                break;
            }
        }
    } // CameraPreviewHandler END

    private class FaceIdCallback implements FaceIdManager.InfoListener {
        public void onInfo(int cmd, int code) {
            Log.d(TAG, " receive FR handler message cmd: " + cmd + " " + code);
            Message msg = mUiHandler.obtainMessage(cmd);
            msg.arg1 = code;
            mUiHandler.sendMessage(msg);
        }
    }
}
