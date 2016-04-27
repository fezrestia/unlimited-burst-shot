package com.fezrestia.android.application.unlimitedburstshot.nativesavingtask;

import com.fezrestia.android.common.log.LogConfig;

public class NativeSavingTask implements Runnable {

    static {
        System.loadLibrary("native_saving_task");
    }

    public interface OnStoreCompletedListener {
        void onStoreCompleted(NativeSavingTask finishedTask);
    }

    private OnStoreCompletedListener mCallback = null;

    public void setOnStoreCompletedListener(OnStoreCompletedListener callback) {
        mCallback = callback;
    }

    // Native saving task data structure memory address.
    private int mNativeDataStructPointerAddress = 0;

    // Target file path.
    private final String TARGET_FILE_PATH;

    // Memory size allocated in native.
    private final int ALLOCATED_MEMORY_SIZE;

    // Already killed.
    private boolean mIsAlreadyKilled = false;

    // CONSTRUCTOR.
    public NativeSavingTask(
            int width,
            int height,
            byte[] yuvPreviewFrame,
            int previewFormat,
            int jpegQuality,
            String filePath) {
        int ret;

        // Initialize in native.
        ret = doInitialize(
                width,
                height,
                yuvPreviewFrame,
                previewFormat,
                jpegQuality,
                filePath);

        // Cache path.
        TARGET_FILE_PATH = filePath;

        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                "] [NATIVE_DATA_STRUCT=" + mNativeDataStructPointerAddress + "]");

        // Calculate estimated allocated memory size in native.
        ALLOCATED_MEMORY_SIZE = yuvPreviewFrame.length + width * height * 3; // YUV and RGB.

        if (ret != 0) {
            throw new RuntimeException(
                    "NativeSavingTask.doInitialize():[ERROR:RET=" + ret + "]");
        }
    }

    public int getNativeSize() {
        return ALLOCATED_MEMORY_SIZE;
    }

    public String getTargetFilePath() {
        return TARGET_FILE_PATH;
    }

    public synchronized void release() {
        mCallback = null;
    }

    public synchronized void kill() {
        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                "] [NativeSavingTask is KILLED !]");

        mIsAlreadyKilled = true;

        // Release native memory.
        doFinalize();
    }

    // Initialize.
    private final native int doInitialize(
            int width,
            int height,
            byte[] yuvData,
            int previewFormat,
            int jpegQuality,
            String filePath);

    // Do store.
    private final native int doStoreYuvByteArrayAsJpegFile();

    // Finalize.
    private final native int doFinalize();

    @Override
    public synchronized void run() {
        if (mIsAlreadyKilled) {
            return;
        }

        // Store.
        doStoreYuvByteArrayAsJpegFile();

        // Finalize in native.
        doFinalize();

        if (mCallback != null) {
            mCallback.onStoreCompleted(this);
        }
    }
}
