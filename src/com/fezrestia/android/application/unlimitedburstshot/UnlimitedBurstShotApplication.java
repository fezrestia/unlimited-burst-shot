package com.fezrestia.android.application.unlimitedburstshot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fezrestia.android.application.unlimitedburstshot.nativesavingtask.NativeSavingTaskStackController;
import com.fezrestia.android.common.log.LogConfig;

import android.app.Application;
import android.util.Log;

public class UnlimitedBurstShotApplication extends Application {
    // Saving single worker thread.
    private static ExecutorService mExecService = null;

    // Global saving task stack controller.
    NativeSavingTaskStackController mSavingTaskController = null;

    @Override
    public void onCreate() {
        super.onCreate();

        // Saving single worker thread.
        mExecService = Executors.newSingleThreadExecutor();
    }

    public ExecutorService getNativeSavingExecutorService() {
        return mExecService;
    }

    public synchronized void setGlobalNativeSavingTaskStackController(
            NativeSavingTaskStackController controller) {
        // Cache.
        mSavingTaskController = controller;
    }

    public synchronized NativeSavingTaskStackController
            getGlobalNativeSavingTaskStackController() {
        return mSavingTaskController;
    }

    @Override
    public void onLowMemory() {
        if (LogConfig.eLog) Log.e("TraceLog", "UnlimitedBurstShotApplication.onLowMemory():[IN]"
                + "[LOW MEMORY][LOW MEMORY][LOW MEMORY]");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        if (LogConfig.isTimeDebug) android.util.Log.e("TraceLog",
                "[PERFORMANCE] [TIME = "+java.lang.System.currentTimeMillis()+
                "] [APPLICATION ON TERMINATED]");

        // Release saving task stack controller.
        mSavingTaskController.release();
        mSavingTaskController = null;

        // Shutdown executor.
        mExecService.shutdown();
        mExecService = null;
    }
}
