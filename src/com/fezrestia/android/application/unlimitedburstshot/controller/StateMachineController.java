package com.fezrestia.android.application.unlimitedburstshot.controller;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

import com.fezrestia.android.application.unlimitedburstshot.Definition;
import com.fezrestia.android.application.unlimitedburstshot.UnlimitedBurstShotActivity;
import com.fezrestia.android.application.unlimitedburstshot.UnlimitedBurstShotApplication;
import com.fezrestia.android.application.unlimitedburstshot.device.CameraDeviceHandler;
import com.fezrestia.android.application.unlimitedburstshot.nativesavingtask.NativeSavingTaskStackController;
import com.fezrestia.android.application.unlimitedburstshot.storage.RingBuffer;
import com.fezrestia.android.application.unlimitedburstshot.view.ViewFinderVisuals;
import com.fezrestia.android.application.unlimitedburstshot.view.ViewFinderVisuals.ViewUpdateEvent;
import com.fezrestia.android.common.log.LogConfig;
import com.fezrestia.android.utility.java.Utility;

public class StateMachineController
        implements NativeSavingTaskStackController.OnPhotoStoreCompletedListener {
    private static final String TAG = "StateMachine";

    // Master Activity.
    private UnlimitedBurstShotActivity mActivity;

    // Master View.
    private ViewFinderVisuals mViewFinderVisuals;

    // Master Device.
    private CameraDeviceHandler mCameraDeviceHandler;

    // Capture state.
    private CaptureState currentState = CaptureState.STATE_NONE;

    // Preview frame.
    private RingBuffer mFrameBufferRing;

    // Handler for burst shot interval creation.
    private Handler mHandler = new Handler();

    // Burst shot minimum interval.
    private static final int MIN_BURST_SHOT_INTERVAL = 10;

    // Burst shot interval.
    private int mBurstShotInterval = MIN_BURST_SHOT_INTERVAL;

    // Latest stored URI.
    private Uri latestStoredPhotoUri = Uri.parse("content://media/external/images/media");

    public enum CaptureState {
        STATE_NONE,
        STATE_INITIALIZE,
        STATE_RESUME,
        STATE_PHOTO_STANDBY,
        STATE_PHOTO_ZOOMING,
        STATE_PHOTO_AF_SEARCH,
        STATE_PHOTO_CAPTURE_WAIT_FOR_AF_DONE,
        STATE_PHOTO_AF_DONE,
        STATE_PHOTO_CAPTURE,
        STATE_PHOTO_STORE,
        STATE_PAUSE,
        STATE_FINALIZE,
    }

    public enum TransitterEvent {
        // Life cycle.
        EVENT_INITIALIZE,
        EVENT_RESUME,
        EVENT_PAUSE,
        EVENT_FINALIZE,

        // Surface.
        EVENT_ON_SURFACE_CREATED,
        EVENT_ON_SURFACE_CHANGED,
        EVENT_ON_SURFACE_PREPARED,
        EVENT_ON_SURFACE_PREPARED_WITHOUT_RESIZE,

        // Device.
        EVENT_ON_AUTO_FOCUS_DONE,
        EVENT_ON_FRAME_DONE,
        EVENT_CHANGE_CAMERA_DEVICE,

        // Storage.
        EVENT_ON_STORE_DONE,

        // Key.
        EVENT_KEY_FOCUS_DOWN,
        EVENT_KEY_FOCUS_UP,
        EVENT_KEY_CAPTURE_DOWN,
        EVENT_KEY_CAPTURE_UP,
        EVENT_KEY_ZOOM_IN_DOWN,
        EVENT_KEY_ZOOM_IN_UP,
        EVENT_KEY_ZOOM_OUT_DOWN,
        EVENT_KEY_ZOOM_OUT_UP,

        // Touch.
        EVENT_CAPTURE_BUTTON_TOUCH,
        EVENT_CAPTURE_BUTTON_RELEASE,
        EVENT_CAPTURE_BUTTON_CANCEL,

        // Burst.
        EVENT_REQUEST_NEXT_CAPTURE_ON_TIMER,
        EVENT_ON_BURST_INTERVAL_CHANCED,
    }

    // State change listener.
    public interface OnStateChangedListener {
        void onStateChanged(CaptureState currentState, Object object);
    }

    private Set<OnStateChangedListener> mOnStateChangedListenerSet
            = new CopyOnWriteArraySet<OnStateChangedListener>();

    public void addOnStateChangedListener(OnStateChangedListener listener) {
        mOnStateChangedListenerSet.add(listener);
    }

    public void removeOnStateChangedListener(OnStateChangedListener listener) {
        mOnStateChangedListenerSet.remove(listener);
    }

    // CONSTRUCTOR.
    public StateMachineController(UnlimitedBurstShotActivity activity) {
        mActivity = activity;
    }

    public void setViewFinderVisuals(ViewFinderVisuals viewFinderVisuals) {
        mViewFinderVisuals = viewFinderVisuals;
    }

    public void setCameraDeviceHandler(CameraDeviceHandler cameraDeviceHandler) {
        mCameraDeviceHandler = cameraDeviceHandler;
    }

    private NativeSavingTaskStackController getSavingTaskController() {
        return ((UnlimitedBurstShotApplication) mActivity.getApplication())
                .getGlobalNativeSavingTaskStackController();
    }

    private void setSavingTaskController(NativeSavingTaskStackController controller) {
        ((UnlimitedBurstShotApplication) mActivity.getApplication())
                .setGlobalNativeSavingTaskStackController(controller);
    }

    public Camera.Parameters getCurrentCameraParameters(boolean isReFetchRequired) {
        // Fetch.
        if (isReFetchRequired) {
            mCameraDeviceHandler.requestCacheParameters();
        }

        // Get.
        return mCameraDeviceHandler.getLatestCachedParameters();
    }

    public boolean tryToSetCameraParameters(Camera.Parameters newParams) {
        return mCameraDeviceHandler.trySetParametersToDevice(newParams);
    }

    public CaptureState getCurrent() {
        return currentState;
    }

    public synchronized void sendEvent(TransitterEvent transitter, Object object) {
        if(LogConfig.dLog) Log.d("TraceLog",
                TAG + ".sendEvent():[IN][STATE="+currentState+"][EVENT="+transitter+"]");

        // Non-state-related event handler.
        switch (transitter) {
            case EVENT_ON_BURST_INTERVAL_CHANCED:
                // arg : Integer rate. 0-100 %
                int rate = ((Integer) object).intValue();
                mBurstShotInterval = MIN_BURST_SHOT_INTERVAL + (100 - rate) * 3; // +0 - +1[sec].
                return;
        }

        switch (currentState) {
            case STATE_NONE:
                switch (transitter) {
                    case EVENT_INITIALIZE:
                        changeTo(CaptureState.STATE_INITIALIZE, object);
                        break;
                }
                break;

            case STATE_INITIALIZE:
                switch (transitter) {
                    case EVENT_RESUME:
                        changeTo(CaptureState.STATE_RESUME, object);
                        break;

                    case EVENT_PAUSE:
                        changeTo(CaptureState.STATE_PAUSE, object);
                        break;
                }
                break;

            case STATE_RESUME:
                switch (transitter) {
                    case EVENT_ON_SURFACE_CREATED:
                        if (!mCameraDeviceHandler.waitForCameraInitialization()) {
                            if(LogConfig.eLog) Log.e("TraceLog",
                                    TAG + ".sendEvent():[wait task is expired on created.]");
                            // Re-request device open.
                            mCameraDeviceHandler.requestOpenCameraDevice(mActivity);
                        }
                        break;

                    case EVENT_ON_SURFACE_CHANGED:
                        // Prepare device, if device is not open yet, request to start open.
                        // If application is resumed immediately after paused,
                        // onRestart() is not called and surfaceChanged() is called immediately.
                        // So, check device state here, and request re-open device if needed.
                        mCameraDeviceHandler.requestOpenCameraDevice(mActivity);
                        if (!mCameraDeviceHandler.waitForCameraInitialization()) {
                            if(LogConfig.eLog) Log.e("TraceLog",
                                    TAG + ".sendEvent():[wait task is expired on changed.]");
                            // Re-request device open.
                            mCameraDeviceHandler.requestOpenCameraDevice(mActivity);
                        }
                        break;

                    case EVENT_ON_SURFACE_PREPARED:
                        // fall-through
                    case EVENT_ON_SURFACE_PREPARED_WITHOUT_RESIZE:
                        // arg : SurfaceHolder

                        if (!mCameraDeviceHandler.waitForCameraInitialization()) {
                            if(LogConfig.eLog) Log.e("TraceLog",
                                    TAG + ".sendEvent():[wait task is expired on changed.]");
                            // Finish as abnormal end.
                            mActivity.finish();
                        }

                        // Start LiveViewFinder.
                        mCameraDeviceHandler.startLiveViewFinder((SurfaceHolder) object);

                        // Prepare frame buffer callback.
                        mCameraDeviceHandler.preparePreviewCallbackWithBuffer();

                        // Create frame buffer ring.
                        // Calculate bit per pixel size.
                        int imageFormat = mCameraDeviceHandler.getLatestCachedParameters()
                                .getPreviewFormat();
                        int bitPerPixel = ImageFormat.getBitsPerPixel(imageFormat);
                        Rect previewRect = mCameraDeviceHandler.getPreviewRect();
                        int bufLen = previewRect.width() * previewRect.height() * bitPerPixel / 8;
                        mFrameBufferRing = new RingBuffer(2, bufLen);

                        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                                "] [PREVIEW FORMAT = " + imageFormat + "]");

                        // Global saving task stack controller
                        if (getSavingTaskController() == null) {
                            // Set up if not initialized yet.
                            setSavingTaskController(
                                    new NativeSavingTaskStackController(
                                            mActivity,
                                            mCameraDeviceHandler.getPreviewRect().width(),
                                            mCameraDeviceHandler.getPreviewRect().height(),
                                            imageFormat,
                                            100,
                                            this)
                                    );
                        } else {
                            // Already initialized.
                            getSavingTaskController().setPhotoStoreCompletedListener(this);

                            // Update view.
                            if (!getSavingTaskController().isTaskStackEmpty()) {
                                // Now on storing.
                                mViewFinderVisuals.sendViewUpdateEvent(
                                        ViewFinderVisuals.ViewUpdateEvent.ON_STORING_STARTED,
                                        null);
                            }
                        }

                        changeTo(CaptureState.STATE_PHOTO_STANDBY, object);
                        break;

                    case EVENT_PAUSE:
                        changeTo(CaptureState.STATE_PAUSE, object);
                        break;
                }
                break;

            case STATE_PHOTO_STANDBY:
                switch (transitter) {
                    case EVENT_KEY_ZOOM_IN_DOWN:
                        doZoomIn();
                        changeTo(CaptureState.STATE_PHOTO_ZOOMING, object);
                        break;

                    case EVENT_KEY_ZOOM_OUT_DOWN:
                        doZoomOut();
                        changeTo(CaptureState.STATE_PHOTO_ZOOMING, object);
                        break;

                    case EVENT_KEY_FOCUS_DOWN:
                        autoFocus();
                        changeTo(CaptureState.STATE_PHOTO_AF_SEARCH, object);
                        break;

                    case EVENT_CAPTURE_BUTTON_TOUCH:
                        autoFocus();
                        changeTo(CaptureState.STATE_PHOTO_CAPTURE_WAIT_FOR_AF_DONE, object);
                        break;

                    case EVENT_CHANGE_CAMERA_DEVICE:
                        changeCameraDevice();
                        mViewFinderVisuals.sendViewUpdateEvent(
                                ViewUpdateEvent.REQUEST_SURFACE_SIZE_UPDATE,
                                null);
                        break;

                    case EVENT_PAUSE:
                        changeTo(CaptureState.STATE_PAUSE, object);
                        break;
                }
                break;

            case STATE_PHOTO_ZOOMING:
                switch (transitter) {
                    case EVENT_KEY_ZOOM_IN_UP:
                        // fall-though.
                    case EVENT_KEY_ZOOM_OUT_UP:
                        doStopZoom();
                        changeTo(CaptureState.STATE_PHOTO_STANDBY, object);
                        break;

                    case EVENT_PAUSE:
                        doStopZoom();
                        changeTo(CaptureState.STATE_PAUSE, object);
                        break;
                }
                break;

            case STATE_PHOTO_AF_SEARCH:
                switch (transitter) {
                    case EVENT_ON_AUTO_FOCUS_DONE:
                        if (((Boolean) object).booleanValue()) {
                            // Play success sound.
                            mActivity.playAutoFocusSuccessSound();
                        }
                        changeTo(CaptureState.STATE_PHOTO_AF_DONE, object);
                        break;

                    case EVENT_KEY_FOCUS_UP:
                        cancelAutoFocus();
                        changeTo(CaptureState.STATE_PHOTO_STANDBY, object);
                        break;

                    case EVENT_KEY_CAPTURE_DOWN:
                        changeTo(CaptureState.STATE_PHOTO_CAPTURE_WAIT_FOR_AF_DONE, object);
                        break;

                    case EVENT_PAUSE:
                        cancelAutoFocus();
                        changeTo(CaptureState.STATE_PAUSE, object);
                        break;

                }
                break;

            case STATE_PHOTO_AF_DONE:
                switch (transitter) {
                    case EVENT_KEY_CAPTURE_DOWN:
//                        cancelAutoFocus();
                        takeBurstFrame(true);
                        changeTo(CaptureState.STATE_PHOTO_CAPTURE, object);
                        break;

                    case EVENT_KEY_FOCUS_UP:
                        cancelAutoFocus();
                        changeTo(CaptureState.STATE_PHOTO_STANDBY, object);
                        break;

                    case EVENT_PAUSE:
                        cancelAutoFocus();
                        changeTo(CaptureState.STATE_PAUSE, object);
                        break;

                }
                break;

            case STATE_PHOTO_CAPTURE_WAIT_FOR_AF_DONE:
                switch (transitter) {
                    case EVENT_KEY_CAPTURE_UP:
                        // fall-through.
                    case EVENT_CAPTURE_BUTTON_RELEASE:
                        // fall-through.
                    case EVENT_CAPTURE_BUTTON_CANCEL:
                        cancelAutoFocus();
                        changeTo(CaptureState.STATE_PHOTO_STANDBY, object);
                        break;

                    case EVENT_ON_AUTO_FOCUS_DONE:
                        if (((Boolean) object).booleanValue()) {
                            // Play success sound.
                            mActivity.playAutoFocusSuccessSound();
                        }

                        // Capture.
                        changeTo(CaptureState.STATE_PHOTO_AF_DONE, object);
//                        cancelAutoFocus();
                        takeBurstFrame(true);
                        changeTo(CaptureState.STATE_PHOTO_CAPTURE, object);
                        break;

                    case EVENT_PAUSE:
                        cancelAutoFocus();
                        changeTo(CaptureState.STATE_PAUSE, object);
                        break;
                }
                break;

            case STATE_PHOTO_CAPTURE:
                switch (transitter) {
                    case EVENT_KEY_CAPTURE_UP:
                        // fall-through.
                    case EVENT_CAPTURE_BUTTON_RELEASE:
                        // fall-through.
                    case EVENT_CAPTURE_BUTTON_CANCEL:
                        // Stop burst capturing.
                        getSavingTaskController().finishCapturing();

                        changeTo(CaptureState.STATE_PHOTO_STANDBY, object);
                        break;

                    case EVENT_ON_FRAME_DONE:
                        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                                "] [1 SHOT DONE]");

                        // Check null. If data == null, it is 1st time frame request. Ignore it.
                        if (object == null) {
                            // NOP. Only post request next capture event.
                            getSavingTaskController().startCapturing(
                                    Environment.getExternalStorageDirectory().getPath()
                                    + Definition.UNLIMITEDBURSTSHOT_STORAGE_PATH
                                    + System.currentTimeMillis());
                        } else {
                            // Shutter sound.
                            mActivity.playShutterDoneSound();

                            // Store.
                            getSavingTaskController().requestStore((byte[]) object);

                            // Start storing animation.
                            mViewFinderVisuals.sendViewUpdateEvent(
                                    ViewFinderVisuals.ViewUpdateEvent.ON_STORING_STARTED,
                                    null);

                            // Switch buffer.
                            mFrameBufferRing.increment();
                        }

                        mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (currentState == CaptureState.STATE_PHOTO_CAPTURE) {
                                        sendEvent(
                                                TransitterEvent
                                                        .EVENT_REQUEST_NEXT_CAPTURE_ON_TIMER,
                                                (Object[]) null);
                                    }
                                }
                        }, mBurstShotInterval);
                        break;

                    case EVENT_REQUEST_NEXT_CAPTURE_ON_TIMER:
                        if (getSavingTaskController().isTaskStackFull()) {
                            // Postponed to next timer tick.
                            mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        sendEvent(
                                                TransitterEvent
                                                        .EVENT_REQUEST_NEXT_CAPTURE_ON_TIMER,
                                                (Object[]) null);
                                    }
                            }, mBurstShotInterval);
                        } else {
                            // Request next.
                            takeBurstFrame(false);
                        }
                        break;

                    case EVENT_PAUSE:
                        // Stop burst capturing.
                        getSavingTaskController().finishCapturing();

                        changeTo(CaptureState.STATE_PAUSE, object);
                        break;
                }
                break;

            case STATE_PAUSE:
                // Release saving task listener.
                getSavingTaskController().setPhotoStoreCompletedListener(null);

                switch (transitter) {
                    case EVENT_RESUME:
                        changeTo(CaptureState.STATE_RESUME, object);
                        break;

                    case EVENT_ON_SURFACE_PREPARED:
                        // If application is resumed after paused immediately,
                        // surfaceChanged() is called before onRestart().
                        // So, if this occurred in PAUSE state,
                        // escape the situation here.

                        // Force change to RESUME state to reset view, and re-send event.
                        changeTo(CaptureState.STATE_RESUME, object);
                        sendEvent(
                                TransitterEvent.EVENT_ON_SURFACE_PREPARED,
                                object);
                        break;

                    case EVENT_FINALIZE:
                        changeTo(CaptureState.STATE_FINALIZE, object);
                        break;
                }
                break;

            case STATE_FINALIZE:
                // NO EVENT should be handled.
                break;

            default:
                // NOP, UnExpected State.
                break;
        }
    }

    private void changeTo(CaptureState nextState, Object object) {
        if(LogConfig.dLog) Log.d("TraceLog", TAG + ".changeTo():[IN][nextState="+nextState+"]");

        // Update.
        currentState = nextState;

        // Notice listeners.
        for (OnStateChangedListener listener : mOnStateChangedListenerSet) {
            listener.onStateChanged(currentState, object);
        }
    }

    private void autoFocus() {
        mCameraDeviceHandler.autoFocus();
    }

    private void cancelAutoFocus() {
        mCameraDeviceHandler.cancelAutoFocus();
    }

    private void takeBurstFrame(boolean is1stFrame) {
        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                "] [REQUEST NEXT PREVIEW : START]");

        if (is1stFrame) {
            // Do frame capture.
            mCameraDeviceHandler.requestNextPreviewCallbackWithBuffer(
                    mFrameBufferRing.getCurrent());

            mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Send DONE event with null object to cancel 1st time frame.
                        sendEvent(TransitterEvent.EVENT_ON_FRAME_DONE, null);
                    }
            }, 0);
        } else {
            // Request next frame.
            mCameraDeviceHandler.requestNextPreviewCallbackWithBuffer(
                    mFrameBufferRing.getNext());

            mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Send DONE event immediately.
                        sendEvent(
                                TransitterEvent.EVENT_ON_FRAME_DONE,
                                mFrameBufferRing.getCurrent());
                    }
            }, 0);
        }
    }

    public void onAutoFocusDone(boolean success) {
        sendEvent(TransitterEvent.EVENT_ON_AUTO_FOCUS_DONE, new Boolean(success));
    }

    public void onShutterDone() {
        // NOP.
    }

    public void onTakePictureDone(byte[] data) {
        // NOP.
    }

    public void onPreviewFrameUpdated(byte[] data) {
        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                "] [REQUEST NEXT PREVIEW : FINISH]");
        // NOP.

        //TODO:Send event to synchronize frame cycle and store cycle.

    }

    class On1stPhotoScanCompletedCallback
            implements Utility.MediaScannerNotifier.OnScanCompletedCallback {
        @Override
        public void onScanCompleted(Uri uri) {
            latestStoredPhotoUri = uri;
        }
    }

    class OnRepeatedPhotoScanCompletedCallback
            implements Utility.MediaScannerNotifier.OnScanCompletedCallback {
        @Override
        public void onScanCompleted(Uri uri) {
            // NOP.
        }
    }

    public Uri getLatestStoredPhotoUri() {
        return latestStoredPhotoUri;
    }

    private void doZoomIn() {
        // Zoom in.
        mCameraDeviceHandler.startSmoothZoom(mCameraDeviceHandler.getMaxZoom());
    }

    private void doZoomOut() {
        // Zoom out, "0" is default zoom.
        mCameraDeviceHandler.startSmoothZoom(0);
    }

    private void doStopZoom() {
        // Stop zoom.
        mCameraDeviceHandler.stopSmoothZoom();
    }

    public void onZoomChange(int zoomValue, boolean stopped, Camera camera) {
        // NOP.
        //TODO:Update ViewFinderVisuals ?
    }

    private void changeCameraDevice() {
        mCameraDeviceHandler.changeCameraDevice();
    }

    public void onVideoRecordingDone(String filePath) {
        // NOP.
    }

    @Override
    public void onPhotoStoreCompleted(
            String filePath, boolean is1stCapture, int remainItemCount) {
        Utility.MediaScannerNotifier notifier =
                new Utility.MediaScannerNotifier(mActivity, filePath, null);
        if (is1stCapture) {
            // Request update CP for 1st photo. This is the target of viewer icon trigger.
            notifier.setOnScanCompletedCallback(new On1stPhotoScanCompletedCallback());
        } else {
            // Request update CP for after 2nd.
            notifier.setOnScanCompletedCallback(new OnRepeatedPhotoScanCompletedCallback());
        }

        // Stop storing animation.
        if (remainItemCount == 0) {
            mViewFinderVisuals.sendViewUpdateEvent(
                    ViewFinderVisuals.ViewUpdateEvent.ON_STORING_FINISHED,
                    null);
        }
    }
}
