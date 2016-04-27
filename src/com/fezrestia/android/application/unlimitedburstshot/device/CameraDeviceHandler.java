package com.fezrestia.android.application.unlimitedburstshot.device;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fezrestia.android.application.unlimitedburstshot.Definition;
import com.fezrestia.android.application.unlimitedburstshot.controller.StateMachineController;
import com.fezrestia.android.common.log.LogConfig;
import com.fezrestia.android.utility.java.Utility;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.SurfaceHolder;

public class CameraDeviceHandler {
    private static final String TAG = "CameraDeviceHandler";

    // Singleton.
    private static CameraDeviceHandler instance = new CameraDeviceHandler();

    // Task and future for executor.
    private OpenDeviceTask mOpenDeviceTask = new OpenDeviceTask();
    private Future<?> mOpenDeviceFuture;

    // Device resource.
    private Camera mCamera;
    private Camera.Parameters mLatestCachedParameters;

    // Status.
    private int mCurrentDeviceState = STATUS_RELEASED;
    public static final int STATUS_RELEASED = 0;
    public static final int STATUS_OPENING = 1;
    public static final int STATUS_OPENED = 2;

    // Picture and Preview size.
    private Camera.Size mPreviewSize;
    private Camera.Size mPictureSize;

    // Master context.
    private Context mContext;

    // State machine.
    private StateMachineController mStateMachine;

    // Preview frame target surface.
    private SurfaceHolder mSurfaceHolder;

    // Camera ID.
    private int mCameraDeviceId;

    // Callback.
    private OnAutoFocusCallback mOnAutoFocusCallback;
    private OnPreviewFrameCallback mOnPreviewFrameCallback;
    private OnShutterCallback mOnShutterCallback;
    private OnPictureTakenCallback mOnPictureTakenCallback;
    private OnZoomChangedCallback mOnZoomChangedCallback;

    // Video recording.
    private MediaRecorder recorder;
    private String latestStoredVideoFilePath;

    public CameraDeviceHandler() {
        // Create callback.
        mOnAutoFocusCallback = new OnAutoFocusCallback();
        mOnPreviewFrameCallback = new OnPreviewFrameCallback();
        mOnShutterCallback = new OnShutterCallback();
        mOnPictureTakenCallback = new OnPictureTakenCallback();
    }

    public static CameraDeviceHandler getInstance() {
        return instance;
    }

    public void requestOpenCameraDevice(Context context) {
        // Start camera open thread if not open yet.
        switch (getCameraDeviceStatus()) {
            case STATUS_RELEASED:
                // Re-Open camera.
                startCameraOpen(context, Camera.CameraInfo.CAMERA_FACING_BACK);
                break;

            default:
                // NOP, device is already opened or opening.
                break;
        }
    }

    public synchronized void startCameraOpen(Context context, int cameraId) {
        if(LogConfig.dLog) Log.d("TraceLog", TAG +".startCameraOpen():[IN]");

        if (mCurrentDeviceState != STATUS_RELEASED) {
            if(LogConfig.dLog) Log.d("TraceLog", TAG +".startCameraOpen():[STATUS_RELEASED]");

            // If already camera is handled, release camera before open.
            releaseCameraInstance();
        }

        mContext = context;
        mCameraDeviceId = cameraId;

        // Create task and request execution.
        ExecutorService exec = Executors.newSingleThreadExecutor();
        mOpenDeviceFuture = exec.submit(mOpenDeviceTask);

        // Shutdown executor, this executor is used for OpenDeviceTask only.
        exec.shutdown();

        mCurrentDeviceState = STATUS_OPENING;
    }

    private synchronized void reOpenCamera(int cameraId) {
        // Release.
        if (mCamera != null) {
            mCamera.setZoomChangeListener(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }

        // ReOpen start.
        startCameraOpen(mContext, cameraId);

        // Wait.
        waitForCameraInitialization();

        // Prepare
        prepareZoom();

        // Start.
        startLiveViewFinder(mSurfaceHolder);
    }

    public int getCurrentCameraDeviceId() {
        return mCameraDeviceId;
    }

    public int getCameraDeviceStatus() {
        return mCurrentDeviceState;
    }

    // Call this method after startCameraOpen().
    public synchronized Camera getCameraInstance() {
        if(LogConfig.dLog) Log.d("TraceLog", TAG + ".getCameraInstance():[IN]");

        // Wait for camera open task finish.
        boolean expired = isOpenDeviceTaskFinishedSuccessfully();
        mOpenDeviceFuture = null;

        if (expired) {
            if(LogConfig.eLog) Log.e("TraceLog", TAG + ".getCameraInstance():[Task is expired]");

            return null;
        }

        return mCamera;
    }

    public synchronized void releaseCameraInstance() {
        if(LogConfig.dLog) Log.d("TraceLog", TAG + ".releaseCameraInstance():[IN]");

        switch (mCurrentDeviceState) {
            case STATUS_RELEASED:
                // NOP, device is already released.
                return;

            default:
                // NOP
                break;
        }

        // Wait for camera open task finish.
        isOpenDeviceTaskFinishedSuccessfully();
        mOpenDeviceFuture = null;

        // Reset caller context.
        mContext = null;

        // Reset cached parameters.
        mLatestCachedParameters = null;

        if (mCamera != null) {
            // Remove callback.
            mCamera.setZoomChangeListener(null);
            mCamera.setPreviewCallback(null);
            mCamera.setPreviewCallbackWithBuffer(null);

            // Release hardware resource.
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;

        }

        mCurrentDeviceState = STATUS_RELEASED;
    }

    private class OpenDeviceTask implements Runnable {
        private static final String TAG = "CameraDeviceHandler.OpenDeviceTask";

        private boolean mIsExpired = false;

        // CONSTRUCTOR
        public OpenDeviceTask() {
            mIsExpired = true;
        }

        public boolean isExpired() {
            return mIsExpired;
        }

        @Override
        public void run() {
            if(LogConfig.dLog) Log.d("TraceLog", TAG + ".run():[OpenDeviceTask START]");

            mCurrentDeviceState = STATUS_OPENING;

            // Try to get hardware resource.
            if (LogConfig.isTimeDebug) Log.e("TraceLog",
                    "[PERFORMANCE] [TIME = " + System.currentTimeMillis()
                    + "] [CameraDeviceHandler OPEN START]");

            try{
                mCamera = Camera.open(mCameraDeviceId);

            } catch(Exception e) {
                if(LogConfig.eLog) Log.e("TraceLog", TAG + ".run():[Failed to Camera.open()]");

                mCamera = null;
                mCurrentDeviceState = STATUS_RELEASED;
                return;
            }
            if (mCamera == null) {
                if(LogConfig.eLog) Log.e("TraceLog", TAG + ".run():[mCamera is null after open()]");

                mCurrentDeviceState = STATUS_RELEASED;
                return;
            }

            if (LogConfig.isTimeDebug) Log.e("TraceLog",
                    "[PERFORMANCE] [TIME = " + System.currentTimeMillis()
                    + "] [CameraDeviceHandler OPEN FINISH]");

            // Fetch initial Camera parameters.
            mLatestCachedParameters = mCamera.getParameters();

            // Set preview and picture size.
            if (LogConfig.isTimeDebug) Log.e("TraceLog",
                    "[PERFORMANCE] [TIME = " + System.currentTimeMillis()
                    + "] [CameraDeviceHandler Settings START]");



            Camera.Size optimalPreviewSize;
            Camera.Size optimalRecordingSize = PlatformDependencyResolver.getOptimalRecordingSize(
                    mContext, mLatestCachedParameters);
            if (Utility.getLandscapeDisplayRect(mContext).width() < optimalRecordingSize.width) {
                // Recording size is larger than display size.
                optimalPreviewSize = optimalRecordingSize;
            } else {
                // Use optimal preview size for recording.
                optimalPreviewSize = PlatformDependencyResolver.getOptimalPreviewSize(
                        mContext, mLatestCachedParameters);
            }
            mLatestCachedParameters.setPreviewSize(
                    optimalPreviewSize.width, optimalPreviewSize.height);
            mPreviewSize = optimalPreviewSize;
            Camera.Size optimalPictureSize = PlatformDependencyResolver.getOptimalPictureSize(
                    optimalPreviewSize, mLatestCachedParameters);
            mLatestCachedParameters.setPictureSize(
                    optimalPictureSize.width, optimalPictureSize.height);

            mPictureSize = optimalPictureSize;

            // Set focus setting.
            if (mCameraDeviceId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mLatestCachedParameters.setFocusMode(
                        PlatformDependencyResolver.getOptimalFocusMode(mLatestCachedParameters));
            }

            // Commit parameters.
            mCamera.setParameters(mLatestCachedParameters);

            if (LogConfig.isTimeDebug) Log.e("TraceLog",
                    "[PERFORMANCE] [TIME = " + System.currentTimeMillis()
                    + "] [CameraDeviceHandler Settings FINISH]");

            // Start live view finder.
            if (LogConfig.isTimeDebug) Log.e("TraceLog",
                    "[PERFORMANCE] [TIME = " + System.currentTimeMillis()
                    + "] [CameraDeviceHandler StartPreview START]");

            mCamera.startPreview();

            if (LogConfig.isTimeDebug) Log.e("TraceLog",
                    "[PERFORMANCE] [TIME = " + System.currentTimeMillis()
                    + "] [CameraDeviceHandler StartPreview FINISH]");

            // Update flag.
            mIsExpired = false;

            mCurrentDeviceState = STATUS_OPENED;

// DEBUG INFORMATION
//String[] splitParams = mLatestCachedParameters.flatten().split(";");
//for (String str : splitParams) {
//    android.util.Log.e("TraceLog", "###### CameraParameters : " + str);
//}
// DEBUG INFORMATION

            if(LogConfig.dLog) Log.d("TraceLog", TAG + ".run():[OpenDeviceTask FINISH]");
        }
    }

    private synchronized boolean isOpenDeviceTaskFinishedSuccessfully() {
        if (mOpenDeviceFuture == null) {
            return false;
        }

        try {
            // Wait for task finish.
            mOpenDeviceFuture.get(Definition.EXECUTOR_TASK_TIMEOUT, TimeUnit.MILLISECONDS);

        } catch (CancellationException e) {
            if(LogConfig.dLog) Log.d("TraceLog",
                    TAG + ".isOpenDeviceTaskFinished():[OpenDeviceTask is canceled.]");
            // NOP.
        } catch (ExecutionException e) {
            if(LogConfig.dLog) Log.d("TraceLog",
                    TAG + ".isOpenDeviceTaskFinished():[OpenDeviceTask is excecpted.]");
            // NOP.
        } catch (InterruptedException e) {
            if(LogConfig.dLog) Log.d("TraceLog",
                    TAG + ".isOpenDeviceTaskFinished():[OpenDeviceTask is interrupted.]");
            // NOP.
        } catch (TimeoutException e) {
            if(LogConfig.dLog) Log.d("TraceLog",
                    TAG + ".isOpenDeviceTaskFinished():[OpenDeviceTask is timeouted.]");
            // NOP.
        }

        return mOpenDeviceTask.isExpired();
    }

    public boolean waitForCameraInitialization() {
        return getCameraInstance() != null;
    }

    public void prepareZoom() {
        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            if(LogConfig.dLog) Log.d("TraceLog", TAG + ".prepareZoom():[camera is null]");
            return;
        }

        // Check smooth zoom is supported or not.
        if (getLatestCachedParameters().isSmoothZoomSupported()) {
            // Create callback here.
            // User can not use zooming function while this callback is null.
            mOnZoomChangedCallback = new OnZoomChangedCallback();

            // Set callback.
            camera.setZoomChangeListener(mOnZoomChangedCallback);

        } else {
            if(LogConfig.dLog) Log.d("TraceLog",
                    TAG + ".prepareZoom():[smoothZoom is not supported]");
            // NOP.
        }
    }

    public Rect getPreviewRect() {
        if (mPreviewSize == null) {
            return null;
        }

        return new Rect(0, 0, mPreviewSize.width, mPreviewSize.height);
    }

    public Rect getPictureRect() {
        if (mPictureSize == null) {
            return null;
        }

        return new Rect(0, 0, mPictureSize.width, mPictureSize.height);
    }



//// PROCESS FOR PRE- LAUNCH IS ABOVE FROM HERE

//// PROCESS FOR POST-LAUNCH IS BELOW FROM HERE



    public void setStateMachine(StateMachineController stateMachine) {
        mStateMachine = stateMachine;
    }

    public Camera.Parameters getLatestCachedParameters() {
        if (mCurrentDeviceState != STATUS_OPENED) {
            // Device is not opened yet.
            return null;
        }

        if (mLatestCachedParameters == null) {
            // Get camera instance.
            Camera camera = getCameraInstance();

            if (camera == null) {
                if(LogConfig.eLog) Log.e("TraceLog",
                        TAG + ".getLatestCachedParameters():[camera == null]");

                return null;
            }

            // Cache.
            mLatestCachedParameters = camera.getParameters();
        }

        return mLatestCachedParameters;
    }

    public void requestCacheParameters() {
        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".requestCacheParameters():[camera == null]");

            return;
        }

        // Cache.
        mLatestCachedParameters = camera.getParameters();
    }

    public boolean trySetParametersToDevice(Camera.Parameters params) {
        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            if(LogConfig.dLog) Log.d("TraceLog",
                    TAG + ".trySetParametersToDevice():[camera == null]");

            return false;
        }

        try {
            camera.setParameters(params);
        } catch (RuntimeException e) {
            if(LogConfig.dLog) Log.d("TraceLog",
                    TAG + ".trySetParametersToDevice():[Failed to setParameters()]");

            // Reset cached parameters.
            mLatestCachedParameters = null;

            return false;
        }

        return true;
    }

    public void startLiveViewFinder(SurfaceHolder holder) {
        if(LogConfig.dLog) Log.d("TraceLog", TAG + ".startLiveViewFinder():[IN]");

        mSurfaceHolder = holder;

        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            if(LogConfig.eLog) Log.e("TraceLog", TAG + ".startLiveViewFinder():[camera is NULL]");
            // NOP.
            return;
        }

        // Set preview display.
        try {
            camera.setPreviewDisplay(holder);

        } catch (Exception e) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".startLiveViewFinder():[failed to setPreviewDisplay]");
            //NOP.
        }
    }

    public void startPreview() {
        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            if(LogConfig.eLog) Log.e("TraceLog", TAG + ".startPreview():[camera is NULL]");
            // NOP.
            return;
        }

        camera.startPreview();
    }

    public void stopPreview() {
        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            if(LogConfig.eLog) Log.e("TraceLog", TAG + ".stopPreview():[camera is NULL]");
            // NOP.
            return;
        }

        camera.stopPreview();
    }

    public void autoFocus() {
        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            if(LogConfig.eLog) Log.e("TraceLog", TAG + ".autoFocus():[camera is NULL]");
            // NOP.
            return;
        }

        // Start AuroFocus.
        camera.autoFocus(mOnAutoFocusCallback);
    }

    class OnAutoFocusCallback implements Camera.AutoFocusCallback {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if (mStateMachine != null) {
                mStateMachine.onAutoFocusDone(success);
            }
        }
    }

    public void cancelAutoFocus() {
        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            if(LogConfig.eLog) Log.e("TraceLog", TAG + ".cancelAutoFocus():[camera is NULL]");
            // NOP.
            return;
        }

        // Cancel AutoFocus.
        camera.cancelAutoFocus();
    }

    public void takePicture() {
        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            if(LogConfig.eLog) Log.e("TraceLog", TAG + ".takePicture():[camera is NULL]");
            // NOP.
            return;
        }

        // Do capture.
        camera.takePicture(mOnShutterCallback, null, mOnPictureTakenCallback);
    }

    class OnShutterCallback implements Camera.ShutterCallback {
        @Override
        public void onShutter() {
            if (mStateMachine != null) {
                mStateMachine.onShutterDone();
            }
        }
    }

    class OnPictureTakenCallback implements Camera.PictureCallback {
        @Override
        public void onPictureTaken(byte[] data,Camera camera) {
            if (mStateMachine != null) {
                mStateMachine.onTakePictureDone(data);
            }
        }
    }

    public class OnZoomChangedCallback implements Camera.OnZoomChangeListener {
        @Override
        public void onZoomChange(int zoomValue, boolean stopped, Camera camera) {
            if (mStateMachine != null) {
                mStateMachine.onZoomChange(zoomValue, stopped, camera);
            }
        }
    }

    public void startSmoothZoom(int zoomStep) {
        if (!getLatestCachedParameters().isSmoothZoomSupported()) {
            return;
        }

        if (mOnZoomChangedCallback == null) {
            if (LogConfig.wLog) Log.w("TraceLog",
                    TAG + ".startSmoothZoom():[zoom is uninitialized]");
            // NOP.
            return;
        }

        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            if(LogConfig.eLog) Log.e("TraceLog", TAG + ".startSmoothZoom():[camera is NULL]");
            // NOP.
            return;
        }

        // Start zoom.
        camera.startSmoothZoom(zoomStep);
    }

    public void stopSmoothZoom() {
        if (!getLatestCachedParameters().isSmoothZoomSupported()) {
            return;
        }

        if (mOnZoomChangedCallback == null) {
            if (LogConfig.wLog) Log.w("TraceLog",
                    TAG + ".stopSmoothZoom():[zoom is uninitialized]");
            // NOP.
            return;
        }

        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            if(LogConfig.eLog) Log.e("TraceLog", TAG + ".stopSmoothZoom():[camera is NULL]");
            // NOP.
            return;
        }

        // Stop zoom.
        camera.stopSmoothZoom();
    }

    public int getMaxZoom() {
        // Max zoom step.
        int max = 0;

        if (getLatestCachedParameters() == null) {
            if (LogConfig.dLog) Log.d("TraceLog",
                    TAG + ".getMaxZoom():[mLatestCachedParams == null]");
            return max;
        }

        max = getLatestCachedParameters().getMaxZoom();

        return max;
    }

    private void setupMediaRecorder(String filePath) {
        // Get camera instance.
        Camera camera = getCameraInstance();
        if (camera == null) {
            if(LogConfig.dLog) Log.d("TraceLog", TAG + ".setupMediaRecorder():[camera is NULL]");
            // NOP.
            return;
        }
        camera.unlock();

        // Setup MediaRecorder.
        if (recorder == null) {
            recorder = new MediaRecorder();
        }

        recorder.setCamera(camera);
        recorder.setPreviewDisplay(mSurfaceHolder.getSurface());

        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        recorder.setVideoSize(getPreviewRect().width(), getPreviewRect().height());

        recorder.setVideoEncodingBitRate(6 * 1000 * 1000); //6M
        recorder.setVideoFrameRate(24);

        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        recorder.setOutputFile(filePath);

        // Try to prepare MediaRecorder.
        try {
            recorder.prepare();
        } catch (IllegalStateException e) {
            if(LogConfig.dLog) Log.d("TraceLog",
                    TAG + ".setupMediaRecorder():[IllegalStateException]");

            releaseMediaRecorder();
        } catch (IOException e) {
            if(LogConfig.dLog) Log.d("TraceLog",
                    TAG + ".setupMediaRecorder():[IOException]");

            releaseMediaRecorder();
        }
    }

    private void releaseMediaRecorder() {
        if (recorder != null) {
            recorder.release();
            recorder = null;

            try {
                // Get camera instance.
                Camera camera = getCameraInstance();
                if (camera == null) {
                    if(LogConfig.dLog) Log.d("TraceLog",
                            TAG + ".releaseMediaRecorder():[camera is NULL]");
                    // NOP.
                    return;
                }

                camera.reconnect();
            } catch (IOException e) {
                if(LogConfig.dLog) Log.d("TraceLog",
                        TAG + ".releaseMediaRecorder():[IOException]");
                //NOP.
            }
        }
    }

    public void startRecording(String filePath) {
        // Setup recorder.
        latestStoredVideoFilePath = filePath;
        setupMediaRecorder(latestStoredVideoFilePath);

        if (recorder == null) {
            if(LogConfig.eLog) Log.e("TraceLog", TAG + ".startRecording():[recorder is null]");
            // NOP.
            return;
        }

        // Start recording.
        recorder.start();
    }

    public void stopRecording() {
        if (recorder == null) {
            if(LogConfig.eLog) Log.e("TraceLog", TAG + ".stopRecording():[recorder is null]");
            // NOP.
            return;
        }

        // Stop recording.
        try {
            recorder.stop();
        } catch(RuntimeException e) {
            if(LogConfig.eLog) Log.e("TraceLog", TAG + ".stopRecording():[RuntimeException]");
            //NOP.
        }

        // Release recorder.
        releaseMediaRecorder();

        if (mStateMachine != null) {
            mStateMachine.onVideoRecordingDone(latestStoredVideoFilePath);
        }
    }

    public boolean isFrontCameraSupported() {
        return Camera.getNumberOfCameras() != 1;
    }

    public void changeCameraDevice() {
        if (!isFrontCameraSupported()) {
            // Camera device can not be switched.
            // NOP.
            return;
        }

        switch (mCameraDeviceId) {
            case Camera.CameraInfo.CAMERA_FACING_BACK:
                reOpenCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
                break;

            case Camera.CameraInfo.CAMERA_FACING_FRONT:
                reOpenCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
                break;

            default:
                // NOP.
                break;
        }
    }

    public void preparePreviewCallbackWithBuffer() {
        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            return;
        }

        // Add callback.
        camera.setPreviewCallbackWithBuffer(mOnPreviewFrameCallback);
    }

    public synchronized void requestNextPreviewCallbackWithBuffer(byte[] frameBuffer) {
        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            return;
        }

        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                "] [PREVIEW BUFFER REQUEST : START]");

        // Add buffer.
        camera.addCallbackBuffer(frameBuffer);

        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                "] [PREVIEW BUFFER REQUEST : FINISH]");
    }

    public synchronized void stopPreviewCallback() {
        // Get camera instance.
        Camera camera = getCameraInstance();

        if (camera == null) {
            return;
        }

        // Release callback.
        camera.setPreviewCallbackWithBuffer(null);
        camera.setPreviewCallback(null);
    }

    class OnPreviewFrameCallback implements Camera.PreviewCallback {
        @Override
        public void onPreviewFrame(byte[] frame, Camera camera) {

            if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                    "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                    "] [ON PREVIEW FRAME : DONE][LEN = " + frame.length + "]");

            if (mStateMachine != null) {
                mStateMachine.onPreviewFrameUpdated(frame);
            }
        }
    }
}
