package com.fezrestia.android.application.unlimitedburstshot.storage;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import com.fezrestia.android.application.unlimitedburstshot.Definition;
import com.fezrestia.android.common.log.LogConfig;

public class AsyncDataStoringTaskHandler {
    private static final String TAG = "AsyncDataStoringTaskHandler";

    // Singleton instance.
    private static AsyncDataStoringTaskHandler instance = new AsyncDataStoringTaskHandler();

    // Task future for open OutputStream.
    private ExecutorService mExecService;
    private Future<Boolean> mAsyncDataStoringTaskFuture;
    private AsyncDataStoringTask mAsyncDataStoringTask;

    // CONSTRUCTOR
    private AsyncDataStoringTaskHandler() {
        // NOP
    }

    public static AsyncDataStoringTaskHandler getInstance() {
        return instance;
    }

    public void setUp() {
        // Create executor.
        mExecService = Executors.newSingleThreadExecutor();
    }

    public void getDown() {
        // Shutdown and delete executor and task.
        if (mExecService != null) {
            mExecService.shutdown();
            mExecService = null;
        }
    }

    public synchronized void rerquestDataStore(
            Activity act,
            FileOutputStream fos,
            byte[] data) {
        // Check previous task.
        Boolean isPreviousTaskSuccess = true;
        if (mAsyncDataStoringTaskFuture != null) {
            // Wait for previous saving task finish.
            try {
                isPreviousTaskSuccess = mAsyncDataStoringTaskFuture.get(
                        Definition.EXECUTOR_TASK_TIMEOUT, TimeUnit.MILLISECONDS);

            } catch (CancellationException e) {
                if(LogConfig.eLog) Log.e("TraceLog",
                        TAG + ".rerquestDataStore():[Task is canceled.]" + e);

                isPreviousTaskSuccess = false;

            } catch (ExecutionException e) {
                if(LogConfig.eLog) Log.e("TraceLog",
                        TAG + ".rerquestDataStore():[Task is excecpted.]" + e);

                isPreviousTaskSuccess = false;

            } catch (InterruptedException e) {
                if(LogConfig.eLog) Log.e("TraceLog",
                        TAG + ".rerquestDataStore():[Task is interrupted.]" + e);

                isPreviousTaskSuccess = false;

            } catch (TimeoutException e) {
                if(LogConfig.eLog) Log.e("TraceLog",
                        TAG + ".rerquestDataStore():[Task is timeouted.]" + e);

                isPreviousTaskSuccess = false;

            }

        }

        // Indicate.
        if (!isPreviousTaskSuccess) {
            Toast.makeText(act, "Image store failed !", Toast.LENGTH_SHORT).show();

        }

        // Create new task.
        mAsyncDataStoringTask = new AsyncDataStoringTask(fos, data);

        // Start task.
        mAsyncDataStoringTaskFuture = mExecService.submit(mAsyncDataStoringTask);

    }

    private class AsyncDataStoringTask
            implements Callable<Boolean> {
        private static final String TAG = "AsyncDataStoringTaskHandler.AsyncDataStoringTask";

        private FileOutputStream mFileOutputStream;
        private byte[] mData;

        // CONSTRUCTOR
        public AsyncDataStoringTask(FileOutputStream fos, byte[] data) {
            mFileOutputStream = fos;
            mData = data;

        }

        @Override
        public Boolean call() {
            if (LogConfig.isTimeDebug) Log.e("TraceLog",
                    "[PERFORMANCE] [TIME = " + System.currentTimeMillis()
                    + "] [UnlimitedBurstShot:SavePicture START]");
            if(LogConfig.dLog) Log.d("TraceLog", TAG + ".call():[Task START]");

            // Return value.
            Boolean ret = true;

            // Write data.
            try {
                mFileOutputStream.write(mData);
                mFileOutputStream.close();

            } catch (IOException e) {
                if(LogConfig.eLog) Log.e("TraceLog",
                        TAG + ".call():[FileNotFoundException]");
                e.printStackTrace();

                try {
                    // Close.
                    mFileOutputStream.close();

                    ret = false;

                } catch (IOException e1) {
                    if(LogConfig.eLog) Log.e("TraceLog",
                            TAG + ".call():[Failed to close()]");
                    e.printStackTrace();

                    ret = false;

                }

            }

            // Request G.C.
            System.gc();

            if (LogConfig.isTimeDebug) Log.e("TraceLog",
                    "[PERFORMANCE] [TIME = " + System.currentTimeMillis()
                    + "] [UnlimitedBurstShot:SavePicture FINISH]");
            if(LogConfig.dLog) Log.d("TraceLog", TAG + ".call():[Task FINISH]");

            return ret;
        }

    }

}
