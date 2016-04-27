package com.fezrestia.android.application.unlimitedburstshot.view;

//import java.util.List;

import android.content.Context;
//import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Camera;
//import android.provider.MediaStore;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.fezrestia.android.application.unlimitedburstshot.R;
import com.fezrestia.android.application.unlimitedburstshot.controller.StateMachineController;
import com.fezrestia.android.application.unlimitedburstshot.controller.StateMachineController.CaptureState;
import com.fezrestia.android.application.unlimitedburstshot.controller.StateMachineController.TransitterEvent;
import com.fezrestia.android.application.unlimitedburstshot.UnlimitedBurstShotActivity;
import com.fezrestia.android.common.log.LogConfig;
import com.fezrestia.android.utility.java.Utility;

public class ViewFinderVisuals
        implements
                SurfaceHolder.Callback,
                InteractiveSliderIndicator.OnInteractiveSliderRangeChangedListener {
    private static final String TAG = "ViewFinderVisuals";

    // Master Activity.
    private UnlimitedBurstShotActivity mActivity;

    // State Machine.
    private StateMachineController mStateMachine;

    // Handler for view update async task.
    private Handler mHandler = new Handler();

    // Surface.
    private SurfaceView mViewFinder;
    private SurfaceHolder mSurfaceHolder;
    private int mTargetSurfaceWidth = -1;
    private int mTargetSurfaceHeight = -1;

    // View finder items.
    private RelativeLayout mHeadUpDisplay;
    // UI components.
    private ImageView mCaptureIcon;
    private ImageView mChangeDeviceIcon;
    private ImageView mGoToViewerIcon;
    private boolean mIsNowStoring = false;
    private InteractiveSliderIndicator mIntervalSlider = null;
    private ImageView autoFocusVisualFeedbackIcon;

    // Event listener.
    private OnStateChangedListener mOnStateChangedListener;
    private TouchEventListener mTouchEventListener;

    // Time wait definition.
    private static final int VIEW_LAZY_INFLATION_WAIT_TIME = 200;

    // View update event.
    public enum ViewUpdateEvent {
        REQUEST_SURFACE_SIZE_UPDATE,
        ON_STORING_STARTED,
        ON_STORING_FINISHED,
    }

    // CONSTRUCTOR
    public ViewFinderVisuals(Context context) {
        if(LogConfig.dLog) Log.d("TraceLog", TAG + ".CONSTRUCTOR:[IN]");

        // Store master activity.
        mActivity = (UnlimitedBurstShotActivity) context;

        // Setup window.
        mActivity.getWindow().setFlags(
                            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Create SurfaceView.
        mViewFinder = new SurfaceView(mActivity);
        mViewFinder.getHolder().addCallback(this);
        mViewFinder.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // Add surface to window.
        WindowManager.LayoutParams params = mActivity.getWindow().getAttributes();
        mActivity.getWindow().addContentView(mViewFinder, params);
    }

    public void setStateMachine(StateMachineController stateMachine) {
        if (stateMachine != null) {
            // Create and add listener.
            mOnStateChangedListener = new OnStateChangedListener();
            stateMachine.addOnStateChangedListener(mOnStateChangedListener);
        } else {
            // Remove and destroy listener.
            if (mStateMachine != null) {
                mStateMachine.removeOnStateChangedListener(mOnStateChangedListener);
            }

            mOnStateChangedListener = null;
        }

        mStateMachine = stateMachine;
    }

    class OnStateChangedListener implements StateMachineController.OnStateChangedListener {
        @Override
        public void onStateChanged(CaptureState currentState, Object object) {
            onViewFinderVisualsStateChanged(currentState, object);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if(LogConfig.dLog) Log.d("TraceLog", TAG + ".surfaceCreated():[IN]");

        // Store.
        mSurfaceHolder = holder;

        // Notify surface created to StateMachine. and wait for Camera device open.
        mStateMachine.sendEvent(
                TransitterEvent.EVENT_ON_SURFACE_CREATED,
                mSurfaceHolder);

        // Check aspect ratio difference between surface and preview.
        Camera.Parameters params = mStateMachine.getCurrentCameraParameters(false);
        if (params == null) {
            // NOP. Delegate surface resize process to onSurfaceChanged.
            return;
        }

        float previewAspect
                = (float) params.getPreviewSize().width / (float) params.getPreviewSize().height;
        Rect displayRect = getDisplayRect(mActivity);
        float displayAspect = (float) displayRect.width() / displayRect.height();

        if (((int) (previewAspect * 100)) == ((int) (displayAspect * 100))) {
            // Surface resize is not necessary. Skipping resize process.
            mStateMachine.sendEvent(
                    TransitterEvent.EVENT_ON_SURFACE_PREPARED_WITHOUT_RESIZE,
                    mSurfaceHolder);

            return;
        }

        // Request surface resize.
        updateSurfaceSize();
    }

    private void updateSurfaceSize() {
        Camera.Parameters params = mStateMachine.getCurrentCameraParameters(false);
        Rect finderRect = getFinderRectFromPreviewSize(
                mActivity,
                params.getPreviewSize().width,
                params.getPreviewSize().height);
        resizeSurfaceView(finderRect.width(), finderRect.height());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if(LogConfig.dLog) Log.d("TraceLog", TAG + ".surfaceChanged():[IN]");

        // Store.
        mSurfaceHolder = holder;

        if ((mTargetSurfaceWidth == -1) && (mTargetSurfaceHeight == -1)) {
            if (LogConfig.dLog) Log.d("TraceLog", TAG + ".surfaceChanged():[not in resizing]");

            // Surface is not in resizing process.
            // If program counter entered in this block,
            // it means application is resumed or NG call
            // because resizeSurfaceView() is called in surfaceCreated(),
            // so, target width/height is not "-1".

            // Surface size is changed.
            mStateMachine.sendEvent(
                    TransitterEvent.EVENT_ON_SURFACE_CHANGED,
                    mSurfaceHolder);
        } else {
            if (LogConfig.dLog) Log.d("TraceLog", TAG + ".surfaceChanged():[in resizing]");

            // Surface is in resizing process.
            if ((mViewFinder.getWidth() == mTargetSurfaceWidth)
                    && (mViewFinder.getHeight() == mTargetSurfaceHeight)) {

                // Surface size is changed successfully.
                mStateMachine.sendEvent(
                        TransitterEvent.EVENT_ON_SURFACE_PREPARED,
                        mSurfaceHolder);

                // Reset.
                mTargetSurfaceWidth = -1;
                mTargetSurfaceHeight = -1;

            } else {
                if (LogConfig.dLog) Log.d("TraceLog",
                        TAG + ".surfaceChanged():[surface size is not fixed yet]");
                // Surface size is not changed yet.
                // NOP
            }
        }
    }

    private void resizeSurfaceView(int width, int height) {
        // Check.
        if ((mTargetSurfaceWidth == width) && (mTargetSurfaceHeight == height)) {
            // Resize request is already done.
            return;
        }

        // Store target.
        mTargetSurfaceWidth = width;
        mTargetSurfaceHeight = height;

        FrameLayout.LayoutParams params
                = (FrameLayout.LayoutParams) mViewFinder.getLayoutParams();

        // Size.
        params.width = width;
        params.height = height;
        // Position.
        params.gravity = Gravity.CENTER;

        mViewFinder.setLayoutParams(params);
    }

    public static Rect getFinderRectFromPreviewSize(
            Context context, int previewWidth, int previewHeight) {

        // Get Display size.
        // Camera application is always landscape. (always height < width)
        Rect displayRect = getDisplayRect(context);
        int displayWidth;
        int displayHeight;
        if (displayRect.height() < displayRect.width()) {
            displayWidth = displayRect.width();
            displayHeight = displayRect.height();
        } else {
            displayWidth = displayRect.height();
            displayHeight = displayRect.width();
        }

        // Calculate aspect ratio.
        float previewRatio = (float) previewWidth / previewHeight;
        float displayRatio = (float) displayWidth / displayHeight;

        // Get finder rectangle size.
        int finderWidth;
        int finderHeight;
        if (previewRatio > displayRatio) {
            finderWidth = displayWidth;
            finderHeight = (int) Math.ceil(finderWidth / previewRatio);
        } else {
            finderHeight = displayHeight;
            finderWidth = (int) Math.ceil(finderHeight * previewRatio);
        }

        // Get finder position.
        int finderX = (displayWidth - finderWidth) / 2;
        int finderY = (displayHeight - finderHeight) / 2;

        // Finder Rectangle.
        Rect result = new Rect(finderX, finderY, finderX + finderWidth, finderY + finderHeight);

        return result;
    }

    public static Rect getDisplayRect(Context context) {
        return Utility.getLandscapeDisplayRect(context);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceHolder = null;
    }

    public class TouchEventListener implements View.OnTouchListener {
        // View finder is inflated in standby state, so initialized in true.
        private boolean isTouchEventEnabled = true;

        public void enableTouchEvent() {
            isTouchEventEnabled = true;
        }

        public void disableTouchEvent() {
            isTouchEventEnabled = false;
        }

        @Override
        public synchronized boolean onTouch(View view, MotionEvent motion) {
            switch (view.getId()) {
                case R.id.capture_icon:
                    if(LogConfig.dLog) Log.d("TraceLog",
                            TAG + ".onTouch():[capture_icon]");

                    switch (motion.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            mStateMachine.sendEvent(
                                    TransitterEvent.EVENT_CAPTURE_BUTTON_TOUCH,
                                    null);
                            break;

                        case MotionEvent.ACTION_MOVE:
                            if (!Utility.hitTest(mCaptureIcon, motion)) {
                                mStateMachine.sendEvent(
                                        TransitterEvent.EVENT_CAPTURE_BUTTON_CANCEL,
                                        null);
                            }
                            break;

                        case MotionEvent.ACTION_UP:
                            if (Utility.hitTest(mCaptureIcon, motion)) {
                                mStateMachine.sendEvent(
                                        TransitterEvent.EVENT_CAPTURE_BUTTON_RELEASE,
                                        null);
                            }
                            break;

                        case MotionEvent.ACTION_CANCEL:
                            mStateMachine.sendEvent(
                                    TransitterEvent.EVENT_CAPTURE_BUTTON_CANCEL,
                                    null);
                            break;
                    }
                    break;

                case R.id.change_device_icon:
                    if(LogConfig.dLog) Log.d("TraceLog",
                            TAG + ".onTouch():[change_device_icon]");

                    if (!isTouchEventEnabled) {
                        return true;
                    }

                    if (motion.getAction() != MotionEvent.ACTION_UP) {
                        break;
                    }

                    if (Utility.hitTest(mChangeDeviceIcon, motion)) {
                        // Change camera device. BACK/FRONT
                        mStateMachine.sendEvent(
                                TransitterEvent.EVENT_CHANGE_CAMERA_DEVICE,
                                null);
                    }
                    break;

                case R.id.go_to_viewer_icon:
                    if(LogConfig.dLog) Log.d("TraceLog",
                            TAG + ".onTouch():[go_to_viewer_icon]");

                    if (!isTouchEventEnabled) {
                        return true;
                    }

                    if (motion.getAction() != MotionEvent.ACTION_UP) {
                        break;
                    }

                    if (Utility.hitTest(mGoToViewerIcon, motion)) {
                        // Launch viewer application.
                        mActivity.startViewerApplication();
                    }
                    break;

                default:
                    if(LogConfig.dLog) Log.d("TraceLog",
                            TAG + ".onTouch():[default]");
                    // UnExpected View.
                    break;
            }

            return true;
        }
    }

    private void setUpHeadUpDisplay() {
        if (mHeadUpDisplay != null) {
            // Already created.

            // Set visibility.
            mHeadUpDisplay.setVisibility(View.VISIBLE);
            mHeadUpDisplay.requestLayout();
            mHeadUpDisplay.invalidate();

            return;
        }

        // Inflate.
        LayoutInflater inflater = android.view.LayoutInflater.from(mActivity);
        mHeadUpDisplay = (RelativeLayout) inflater.inflate(
                R.layout.unlimited_burst_shot, null);

        // Add view.
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        mActivity.getWindow().addContentView(mHeadUpDisplay, params);

        // UI component.
        mCaptureIcon = (ImageView) mActivity.findViewById(R.id.capture_icon);
        mChangeDeviceIcon = (ImageView) mActivity.findViewById(R.id.change_device_icon);
        mGoToViewerIcon = (ImageView) mActivity.findViewById(R.id.go_to_viewer_icon);
        updateViewerIcon();
        mIntervalSlider = new InteractiveSliderIndicator(mActivity);
        mIntervalSlider.setOnInteractiveSliderRangeChangedListener(this);
        onInteractiveSliderRangeChanged(50); // Default ratio.
        autoFocusVisualFeedbackIcon = (ImageView) mActivity.findViewById(
                R.id.auto_focus_visual_feedback_icon);

        // Set event listener.
        mTouchEventListener = new TouchEventListener();
        mCaptureIcon.setOnTouchListener(mTouchEventListener);
        mChangeDeviceIcon.setOnTouchListener(mTouchEventListener);
        mGoToViewerIcon.setOnTouchListener(mTouchEventListener);

        // Refresh.
        mHeadUpDisplay.requestLayout();
        mHeadUpDisplay.invalidate();
    }

    private void updateViewerIcon() {
        if (mGoToViewerIcon == null) {
            // NOP.
            return;
        }

        if (mIsNowStoring) {
            mGoToViewerIcon.setImageResource(R.drawable.now_storing_animation);
            // Start.
            AnimationDrawable storingAnim = (AnimationDrawable)
                    mGoToViewerIcon.getDrawable();
            storingAnim.start();
        } else {
            // Reset.
            mGoToViewerIcon.setImageResource(
                    R.drawable.unlimitedburstshot_viewer_icon);
            // Refresh.
            mGoToViewerIcon.invalidate();
        }
    }

    private void getDownHeadUpDisplay() {
        if (mHeadUpDisplay != null) {
            mCaptureIcon.setOnTouchListener(null);
            mChangeDeviceIcon.setOnTouchListener(null);
            mGoToViewerIcon.setOnTouchListener(null);
            mTouchEventListener = null;

            mCaptureIcon = null;
            mChangeDeviceIcon = null;
            mGoToViewerIcon = null;
            mIntervalSlider.setOnInteractiveSliderRangeChangedListener(null);
            mIntervalSlider = null;
            autoFocusVisualFeedbackIcon = null;

            mHeadUpDisplay = null;
        }
    }

    class SetUpHeadUpDisplayThread implements Runnable {
        @Override
        public void run() {
            if (mStateMachine == null) {
                // Application is already finalized.
                return;
            }

            switch (mStateMachine.getCurrent()) {
                case STATE_PHOTO_STANDBY:
                    setUpHeadUpDisplay();
                    changeToPhotoIdleView();
                    updateAutoFocusVisualFeedback(mStateMachine.getCurrent(), null);
                    break;

                default:
                    mHandler.postDelayed(
                            new SetUpHeadUpDisplayThread(),
                            VIEW_LAZY_INFLATION_WAIT_TIME);
                    break;
            }
        }
    }

    public void sendViewUpdateEvent(ViewUpdateEvent updateEvent, Object object) {
        switch (updateEvent) {
            case REQUEST_SURFACE_SIZE_UPDATE:
                updateSurfaceSize();
                break;

            case ON_STORING_STARTED:
                mIsNowStoring = true;
                updateViewerIcon();
                break;

            case ON_STORING_FINISHED:
                mIsNowStoring = false;

                // Request stop.
                mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateViewerIcon();
                        }
                });
                break;

            default:
                // NOP
                break;
        }
    }

    private void onViewFinderVisualsStateChanged(CaptureState currentState, Object object) {
        switch (currentState) {
            case STATE_NONE:
                // NOP.
                break;

            case STATE_INITIALIZE:
                // NOP.
                break;

            case STATE_RESUME:
                // Show surface.
                mViewFinder.setVisibility(View.VISIBLE);

                // Post lazy inflation event.
                mHandler.postDelayed(
                        new SetUpHeadUpDisplayThread(),
                        VIEW_LAZY_INFLATION_WAIT_TIME);
                break;

            case STATE_PHOTO_STANDBY:
                changeToPhotoIdleView();
                enableTouchEventOnCommonView();
                updateAutoFocusVisualFeedback(currentState, object);
                break;

            case STATE_PAUSE:
                // Hide surface.
                mViewFinder.setVisibility(View.GONE);

                changeToPauseView();
                enableTouchEventOnCommonView();
                updateAutoFocusVisualFeedback(currentState, object);
                break;

            case STATE_PHOTO_ZOOMING:
                changeToCaptureView();
                disableTouchEventOnCommonView();
                break;

            case STATE_PHOTO_AF_SEARCH:
                // fall-through.
            case STATE_PHOTO_AF_DONE:
                changeToCaptureView();
                enableTouchEventOnCommonView();
                updateAutoFocusVisualFeedback(currentState, object);
                break;

            case STATE_PHOTO_CAPTURE:
                // fall-through.
            case STATE_PHOTO_CAPTURE_WAIT_FOR_AF_DONE:
                // fall-through.
            case STATE_PHOTO_STORE:
                changeToCaptureView();
                disableTouchEventOnCommonView();
                updateAutoFocusVisualFeedback(currentState, object);
                break;

            case STATE_FINALIZE:
                // Get down all view components.
                getDownHeadUpDisplay();
                break;
        }
    }

    private void changeToIdleView() {
        if (mHeadUpDisplay == null) {
            return;
        }

        // Common UI.
        mCaptureIcon.setVisibility(View.VISIBLE);
        mChangeDeviceIcon.setVisibility(View.VISIBLE);
        mGoToViewerIcon.setVisibility(View.VISIBLE);
        mIntervalSlider.setVisibility(View.VISIBLE);

        // Refresh.
        mHeadUpDisplay.requestLayout();
        mHeadUpDisplay.invalidate();
    }

    private void changeToPhotoIdleView() {
        if (mHeadUpDisplay == null) {
            return;
        }

        // Call base method.
        changeToIdleView();
    }

    private void changeToPauseView() {
        if (mHeadUpDisplay == null) {
            return;
        }

        // Common UI.
        mCaptureIcon.setVisibility(View.INVISIBLE);
        mChangeDeviceIcon.setVisibility(View.INVISIBLE);
        mGoToViewerIcon.setVisibility(View.INVISIBLE);
        mIntervalSlider.setVisibility(View.INVISIBLE);

        // Refresh.
        mHeadUpDisplay.requestLayout();
        mHeadUpDisplay.invalidate();
    }

    private void changeToCaptureView() {
        if (mHeadUpDisplay == null) {
            return;
        }

        // Common UI.
        mCaptureIcon.setVisibility(View.VISIBLE);
        mChangeDeviceIcon.setVisibility(View.INVISIBLE);
        mGoToViewerIcon.setVisibility(View.INVISIBLE);
        mIntervalSlider.setVisibility(View.VISIBLE);

        // Refresh.
        mHeadUpDisplay.requestLayout();
        mHeadUpDisplay.invalidate();
    }

    private void enableTouchEventOnCommonView() {
        if (mTouchEventListener != null) {
            mTouchEventListener.enableTouchEvent();
        }
    }

    private void disableTouchEventOnCommonView() {
        if (mTouchEventListener != null) {
            mTouchEventListener.disableTouchEvent();
        }
    }

    private void updateAutoFocusVisualFeedback(CaptureState currentState, Object object) {
        if (autoFocusVisualFeedbackIcon == null) {
            return;
        }

        int iconId;

        switch (currentState) {
            case STATE_PHOTO_STANDBY:
                iconId = R.drawable.supercleancamera_auto_focus_idle;
                break;

            case STATE_PHOTO_AF_SEARCH:
                // fall-through.
            case STATE_PHOTO_CAPTURE_WAIT_FOR_AF_DONE:
                iconId = R.drawable.supercleancamera_auto_focus_try;
                break;

            case STATE_PHOTO_AF_DONE:
                if (((Boolean)object).booleanValue()) {
                    iconId = R.drawable.supercleancamera_auto_focus_success;
                } else {
                    iconId = R.drawable.supercleancamera_auto_focus_failure;
                }
                break;

            case STATE_PHOTO_CAPTURE:
                // NOP.
                return;

            default:
                // Other states.
                iconId = 0;
                break;
        }

        if (iconId != 0) {
            autoFocusVisualFeedbackIcon.setBackgroundResource(iconId);
            autoFocusVisualFeedbackIcon.setVisibility(View.VISIBLE);
        } else {
            autoFocusVisualFeedbackIcon.setVisibility(View.INVISIBLE);
        }

        // Refresh.
        autoFocusVisualFeedbackIcon.requestLayout();
        autoFocusVisualFeedbackIcon.invalidate();
    }

    @Override
    public void onInteractiveSliderRangeChanged(int range) {
        // Notify to StateMachine.
        mStateMachine.sendEvent(
                StateMachineController.TransitterEvent.EVENT_ON_BURST_INTERVAL_CHANCED,
                new Integer(range));
    }
}
