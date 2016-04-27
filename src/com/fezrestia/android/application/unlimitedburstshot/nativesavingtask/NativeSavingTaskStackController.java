package com.fezrestia.android.application.unlimitedburstshot.nativesavingtask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;

import com.fezrestia.android.common.log.LogConfig;

import com.fezrestia.android.application.unlimitedburstshot.Definition;
import com.fezrestia.android.application.unlimitedburstshot.UnlimitedBurstShotApplication;

public class NativeSavingTaskStackController
        implements NativeSavingTask.OnStoreCompletedListener {
    // Master activity.
    private final Activity mActivity;

    // Activity manager for memory info.
    private final ActivityManager mActMan;

    // Parameters.
    private final int FRAME_WIDTH;
    private final int FRAME_HEIGHT;
    private final int PREVIEW_FORMAT;
    private final int JPEG_QUALITY;

    // Max task size
//    private final int MAX_TASK_STACK_SIZE = 24;

    // Memory size threshold of low memory killer trigger.
    private final long LOW_MEMORY_KILLER_TRIGGER_THRESHOLD;

    // Current available memory size.
    private long mCurrentAvailableMemSize = 0;

    // Current estimated allocated memory size in native.
    private int mNativeMemorySize = 0;

    // Task list. This is got from UnlimitedBurstShotApplication.
    private final List<NativeSavingTask> mSavingTaskList = new ArrayList<NativeSavingTask>();

    // Executor. This is got from UnlimitedBurstShotApplication.
    private final ExecutorService mExecService;

    // File path pre-fix.
    private String mFilePathPreFix = null;

    // File path post-fix.
    private static final String FILE_PATH_POST_FIX = Definition.PHOTO_FILE_EXTENSION;

    // Capture count.
    private int mCaptureCount = 0;

    // 1st photo flag.
    private boolean mIs1stPhotoAlreadyCallbacked = false;

    public interface OnPhotoStoreCompletedListener {
        void onPhotoStoreCompleted(String path, boolean is1stCapture, int remainItemCount);
    }

    private OnPhotoStoreCompletedListener mPhotoStoreListener = null;

    public void setPhotoStoreCompletedListener(OnPhotoStoreCompletedListener listener) {
        mPhotoStoreListener = listener;
    }

    // CONSTRUCTOR.
    public NativeSavingTaskStackController(
            Activity act,
            int width,
            int height,
            int previewFormat,
            int jpegQuality,
            OnPhotoStoreCompletedListener listener) {
        // Master.
        mActivity = act;

        // Store parameters.
        FRAME_WIDTH = width;
        FRAME_HEIGHT = height;
        PREVIEW_FORMAT = previewFormat;
        JPEG_QUALITY = jpegQuality;

        // Callback listener.
        mPhotoStoreListener = listener;

        // Get executor.
        mExecService = ((UnlimitedBurstShotApplication) mActivity.getApplication())
                .getNativeSavingExecutorService();

        // Memory info.
        mActMan = ((ActivityManager) mActivity.getSystemService(Context.ACTIVITY_SERVICE));
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        mActMan.getMemoryInfo(memInfo);
        LOW_MEMORY_KILLER_TRIGGER_THRESHOLD = memInfo.threshold + 24 * 1024 * 1024; // [+24MB]
    }

    public final synchronized void release() {
        // Release callback.
        mPhotoStoreListener = null;

        // Executor is shut downed in UnlimitedBurstShotApplication.onTerminate().

        // Release callback reference from all tasks. And clear list.
        for (NativeSavingTask task : mSavingTaskList) {
            task.setOnStoreCompletedListener(null);
        }
        mSavingTaskList.clear();
    }

    public final synchronized boolean isTaskStackEmpty() {
        return mSavingTaskList.isEmpty();
    }

    public final synchronized boolean isTaskStackFull() {
        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                "] [CURRENT TASK STACK : " + mSavingTaskList.size()
                + "] [CURRENT MEM SIZE : " + mNativeMemorySize + "]");

        // Get memory info.
        Runtime runtime = Runtime.getRuntime();
        int totalMemory = (int) (runtime.totalMemory() / 1024 / 1024);
        int freeMemory = (int) (runtime.freeMemory() / 1024 / 1024);
        int usedMemory = totalMemory - freeMemory;
        int dalvikMaxMemory = (int) (runtime.maxMemory() / 1024 / 1024);

// DEBUG INFO.
        if (LogConfig.isTimeDebug) {
            android.util.Log.e("TraceLog",
                      "-- RUNTIME MEMORY INFO ----------------------------------------\n"
                    + " TOTAL:" + totalMemory + "\n"
                    + " FREE :" + freeMemory + "\n"
                    + " USED :" + usedMemory + "\n"
                    + " DALVK:" + dalvikMaxMemory + "\n"
                    + "---------------------------------------- RUNTIME MEMORY INFO --\n");

            ActivityManager actMan = ((ActivityManager)
                    mActivity.getSystemService(Context.ACTIVITY_SERVICE));
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            actMan.getMemoryInfo(memInfo);
            android.util.Log.e("TraceLog",
                      "-- ACTIVITY MANAGER MEMORY INFO -------------------------------\n"
                    + " SYSTEM AVAILABLE MEMORY : " + memInfo.availMem / 1024 / 1024 + "\n"
                    + " LOW MEMORY THRESHOLD    : " + memInfo.threshold / 1024 / 1024 + "\n"
                    + "------------------------------- ACTIVITY MANAGER MEMORY INFO --\n");
        }
// DEBUG INFO.

        updateMemoryInfo();

        if (mCurrentAvailableMemSize < LOW_MEMORY_KILLER_TRIGGER_THRESHOLD) {
            if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                    "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                    "] [LOW MEMORY, CAPTURE IS SUSPENDED !]" + " "
                    + "[CUR=" + mCurrentAvailableMemSize + "]" + " "
                    + "[THRESHOLD=" + LOW_MEMORY_KILLER_TRIGGER_THRESHOLD + "]");

            // Memory low.
            return true;
        }

        // Memory is available.
        return false;
    }

    private void updateMemoryInfo() {
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        mActMan.getMemoryInfo(memInfo);

        mCurrentAvailableMemSize = memInfo.availMem;
    }

    public final synchronized void startCapturing(String filePathPreFix) {
        mFilePathPreFix = filePathPreFix;
        mCaptureCount = 0;
        mIs1stPhotoAlreadyCallbacked = false;
    }

    public final synchronized void requestStore(byte[] yuvData) {
        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                "] [REQUEST STORE : START]");

        if (mExecService == null) {
            // TaskStackController is already released.
            throw new RuntimeException("NativeSavingTaskStackController is already released.");
        }

        // Create new saving task.
        NativeSavingTask newTask = new NativeSavingTask(
                FRAME_WIDTH,
                FRAME_HEIGHT,
                yuvData,
                PREVIEW_FORMAT,
                JPEG_QUALITY,
                getNextFilePath());

        // Set callback.
        newTask.setOnStoreCompletedListener(this);

        // Submit task to executor.
        mExecService.submit(newTask);

        // Add list.
        mSavingTaskList.add(newTask);

        // Memory size.
        mNativeMemorySize += newTask.getNativeSize();

        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                "] [REQUEST STORE : FINISH]");
    }

    private String getCurrentFilePath() {
        return mFilePathPreFix + "_" + getCountString(mCaptureCount) + FILE_PATH_POST_FIX;
    }

    private String getNextFilePath() {
        ++mCaptureCount;
        return getCurrentFilePath();
    }

    private String getCountString(int count) {
        String str = Integer.toString(count);

        while (str.length() < 4) {  // Max character number.
            str = "0" + str;
        }

        return str;
    }

    public final synchronized void finishCapturing() {
        // Reset.
        mFilePathPreFix = null;
        mCaptureCount = 0;
    }

    @Override
    public synchronized void onStoreCompleted(NativeSavingTask finishedTask) {
        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                "] [ON STORE COMPLETED]");

        finishedTask.setOnStoreCompletedListener(null);

        // Remove from list.
        mSavingTaskList.remove(finishedTask);

        // Memory size.
        mNativeMemorySize -= finishedTask.getNativeSize();

        // Notify.
        if (mPhotoStoreListener != null) {
            if (!mIs1stPhotoAlreadyCallbacked) {
                mIs1stPhotoAlreadyCallbacked = true;

                // 1st photo.
                mPhotoStoreListener.onPhotoStoreCompleted(
                        finishedTask.getTargetFilePath(),
                        true,
                        mSavingTaskList.size());
            } else {
                // After 2nd photo.
                mPhotoStoreListener.onPhotoStoreCompleted(
                        finishedTask.getTargetFilePath(),
                        false,
                        mSavingTaskList.size());
            }
        }

        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                "] [ON STORE COMPLETED] [CURRENT TASK STACK : " + mSavingTaskList.size()
                + "] [CURRENT MEM SIZE : " + mNativeMemorySize + "]");
    }
}
