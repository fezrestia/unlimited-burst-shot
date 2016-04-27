package com.fezrestia.android.application.unlimitedburstshot;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;

import com.fezrestia.android.application.unlimitedburstshot.R;
import com.fezrestia.android.application.unlimitedburstshot.controller.StateMachineController;
import com.fezrestia.android.application.unlimitedburstshot.controller.StateMachineController.TransitterEvent;
import com.fezrestia.android.application.unlimitedburstshot.device.CameraDeviceHandler;
import com.fezrestia.android.application.unlimitedburstshot.view.ViewFinderVisuals;
import com.fezrestia.android.common.log.LogConfig;
import com.fezrestia.android.utility.java.Utility;

public class UnlimitedBurstShotActivity extends Activity {
    private static final String TAG = "UnlimitedBurstShotActivity";

    // State Machine.
    private StateMachineController mStateMachineController;

    // Device Handler.
    private CameraDeviceHandler mCameraDeviceHandler;

    // View Root.
    private ViewFinderVisuals mViewFinderVisuals;

    // Post event handler.
    private Handler mPostEventHandler;

    // MediaPlayer.
    private MediaPlayer mMediaPlayer;
    Uri soundUriAutoFocusSuccess = null;
    Uri soundUriShutterDone = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Create core instances.
        mStateMachineController = new StateMachineController(this);
        mViewFinderVisuals = new ViewFinderVisuals(this);

        // Setup relations.
        mStateMachineController.setViewFinderVisuals(mViewFinderVisuals);
        mViewFinderVisuals.setStateMachine(mStateMachineController);

        mStateMachineController.sendEvent(
                TransitterEvent.EVENT_INITIALIZE,
                null);

        // Setup directory.
        Utility.createDirectoryInExtStorage(Definition.UNLIMITEDBURSTSHOT_STORAGE_PATH);

        // Create post event handler.
        mPostEventHandler = new Handler();

        // Create MediaPlayer.
        createMediaPlayer();

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        // Setup device handler.
        mCameraDeviceHandler = CameraDeviceHandler.getInstance();
        mCameraDeviceHandler.setStateMachine(mStateMachineController);
        mStateMachineController.setCameraDeviceHandler(mCameraDeviceHandler);

        // Request device open.
        mCameraDeviceHandler.requestOpenCameraDevice(this);

        mStateMachineController.sendEvent(
                TransitterEvent.EVENT_RESUME,
                null);

        // Async start lazy initialization.
        requestPostLazyInitializationTaskExecute();

        super.onResume();
    }

    private void requestPostLazyInitializationTaskExecute() {
        // Post event of lazy initialization.
        mPostEventHandler.postDelayed(
                new LazyInitializationTask(),
                Definition.LAZY_INITIALIZATION_TASK_WAIT);
    }

    class LazyInitializationTask implements Runnable {
        @Override
        public void run() {
            if ((mStateMachineController == null)
                    || (mCameraDeviceHandler == null)
                    || (mViewFinderVisuals == null)) {
                // Application is already destroyed.
                return;
            }

            switch (mStateMachineController.getCurrent()) {
                case STATE_PHOTO_STANDBY:
                    // Prepare zooming function.
                    mCameraDeviceHandler.prepareZoom();
                    break;

                default:
                    // If current state is not stand-by, re-try to setup.
                    retry();
                    break;
            }
        }

        private void retry() {
            if (mPostEventHandler != null) {
                mPostEventHandler.postDelayed(
                        new LazyInitializationTask(),
                        Definition.LAZY_INITIALIZATION_TASK_WAIT);
            }
        }
    }

    @Override
    public void onPause() {
        mStateMachineController.sendEvent(
                TransitterEvent.EVENT_PAUSE,
                null);

        // Release camera instance.
        if (mCameraDeviceHandler != null) {
            mCameraDeviceHandler.setStateMachine(null);
            mCameraDeviceHandler.releaseCameraInstance();
            mCameraDeviceHandler = null;
        } else {
            CameraDeviceHandler.getInstance().releaseCameraInstance();
        }

        // Call super pause.
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mStateMachineController.sendEvent(
                StateMachineController.TransitterEvent.EVENT_FINALIZE,
                null);

        // Get down relations.
        mStateMachineController.setCameraDeviceHandler(null);
        mStateMachineController.setViewFinderVisuals(null);
        mViewFinderVisuals.setStateMachine(null);

        // Release core instances.
        mStateMachineController = null;
        mViewFinderVisuals = null;

        // Release handler.
        mPostEventHandler = null;

        // Release MediaPlayer.
        releaseMediaPlayer();
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (event.getRepeatCount() != 0) {
            // Does not handle long-press.
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_FOCUS:
                mStateMachineController.sendEvent(
                        StateMachineController.TransitterEvent.EVENT_KEY_FOCUS_DOWN,
                        null);
                return true;

            case KeyEvent.KEYCODE_CAMERA:
                mStateMachineController.sendEvent(
                        StateMachineController.TransitterEvent.EVENT_KEY_CAPTURE_DOWN,
                        null);
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
                mStateMachineController.sendEvent(
                        StateMachineController.TransitterEvent.EVENT_KEY_ZOOM_OUT_DOWN,
                        null);
                return true;

            case KeyEvent.KEYCODE_VOLUME_DOWN:
                mStateMachineController.sendEvent(
                        StateMachineController.TransitterEvent.EVENT_KEY_ZOOM_IN_DOWN,
                        null);
                return true;

            case KeyEvent.KEYCODE_BACK:
                // NOP.
                return true;

            default:
                // NOP, transfer event to framework.
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FOCUS:
                mStateMachineController.sendEvent(
                        StateMachineController.TransitterEvent.EVENT_KEY_FOCUS_UP,
                        null);
                return true;

            case KeyEvent.KEYCODE_CAMERA:
                mStateMachineController.sendEvent(
                        StateMachineController.TransitterEvent.EVENT_KEY_CAPTURE_UP,
                        null);
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
                mStateMachineController.sendEvent(
                        StateMachineController.TransitterEvent.EVENT_KEY_ZOOM_OUT_UP,
                        null);
                return true;

            case KeyEvent.KEYCODE_VOLUME_DOWN:
                mStateMachineController.sendEvent(
                        StateMachineController.TransitterEvent.EVENT_KEY_ZOOM_IN_UP,
                        null);
                return true;

            case KeyEvent.KEYCODE_BACK:
                // Terminate application.
                switch (mStateMachineController.getCurrent()) {
                    case STATE_PHOTO_STANDBY:
                        mStateMachineController.sendEvent(
                                TransitterEvent.EVENT_PAUSE,
                                null);
                        finish();
                }
                return true;

            default:
                if(LogConfig.dLog) Log.d("TraceLog", TAG + "onKeyUp():[default]");
                // NOP, transfer event to framework.
                return super.onKeyUp(keyCode, event);
        }
    }

    public void playAutoFocusSuccessSound() {
        playSound(soundUriAutoFocusSuccess);
    }

    public void playShutterDoneSound() {
        if (soundUriShutterDone != null) {
            playSound(soundUriShutterDone);
        }
    }

    private void playSound(Uri soundUri) {
        synchronized(mMediaPlayer) {
            try {
                mMediaPlayer.reset();
                mMediaPlayer.setDataSource(this, soundUri);
                mMediaPlayer.prepare();
            } catch (IOException ex) {
                if(LogConfig.eLog) Log.e("TraceLog", TAG + ".playSound():[fail to play sound.]");
                mMediaPlayer.reset();
            }

            mMediaPlayer.start();
        }
    }

    private void createMediaPlayer() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMediaPlayer = new MediaPlayer();

        // Set parameters.
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
        mMediaPlayer.setVolume(
                am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION),
                am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION));

        // Check URI.
        File semcAfSoundFile = new File("/system/media/audio/camera/common/af_success.m4a");
        if (semcAfSoundFile.canRead()) {
            soundUriAutoFocusSuccess = Uri.fromFile(semcAfSoundFile);
        } else {
            soundUriAutoFocusSuccess = Uri.parse(
                    "android.resource://com.fezrestia.android.application.unlimitedburstshot/"
                    + R.raw.af_success);
        }

//        File semcShutterSoundFile = new File("/system/media/audio/camera/sound1/shutter.m4a");
//        if (semcShutterSoundFile.canRead()) {
//            soundUriShutterDone = Uri.fromFile(semcShutterSoundFile);
//        }
        soundUriShutterDone = Uri.parse(
                "android.resource://com.fezrestia.android.application.unlimitedburstshot/"
                + R.raw.shutter_done);
    }

    private void releaseMediaPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    public void startViewerApplication() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(mStateMachineController.getLatestStoredPhotoUri());
        this.startActivity(intent);
    }
}
