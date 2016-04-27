package com.fezrestia.android.application.unlimitedburstshot.storage;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.util.Log;

import com.fezrestia.android.application.unlimitedburstshot.Definition;
import com.fezrestia.android.common.log.LogConfig;



public class OutputStreamMultiplexer {
    private static final String TAG = "OutputStreamMultiplexer";

    // Singleton instance.
    private static OutputStreamMultiplexer instance = new OutputStreamMultiplexer();

    // Task future for open OutputStream.
    private ExecutorService mExecService;
    private List<Future<FileOutputStream>> mOpenOutputStreamFutureList
            = new ArrayList<Future<FileOutputStream>>();

    // Count of OutputStream cache.
    private static final int OUTPUT_STREAM_CACHE_COUNT = 1;

    // File path name parts.
    private String mFilePathPreFix;
    private String mFilePathPostFix;

    // File count.
    private int mCount;



    private OutputStreamMultiplexer() {
        // NOP
    }

    public static OutputStreamMultiplexer getInstance() {
        return instance;
    }

    public void setUpMultiplexer(String filePathPreFix, String filePathPostFix) {
        // Create executor and start task.
        mExecService = Executors.newSingleThreadExecutor();

        // Reset count.
        mCount = 0;

        // Copy file path parts.
        mFilePathPreFix = filePathPreFix;
        mFilePathPostFix = filePathPostFix;

        // Create callable task.
        for (int i = 0; i < OUTPUT_STREAM_CACHE_COUNT; ++i) {
            // Submit task and store future.
            mOpenOutputStreamFutureList.add(
                    mExecService.submit(new OpenOutputStreamTask(getNextFilePath())));

            // Count up.
            ++mCount;

        }

    }

    private String getNextFilePath() {
        return mFilePathPreFix + "_" + mCount + mFilePathPostFix;
    }

    public void getDownMultiplexer() {
        // Shutdown and delete executor and task.
        mExecService.shutdown();
        mExecService = null;

        for (Future<FileOutputStream> future : mOpenOutputStreamFutureList) {
            try {
                future.get(Definition.EXECUTOR_TASK_TIMEOUT, TimeUnit.MILLISECONDS).close();

            } catch (IOException e) {
                if(LogConfig.eLog) Log.e("TraceLog",
                        TAG + ".getDownMultiplexer():[IOException]" + e);
                // NOP
            } catch (InterruptedException e) {
                if(LogConfig.eLog) Log.e("TraceLog",
                        TAG + ".getDownMultiplexer():[Task is interrupted.]" + e);
                // NOP
            } catch (ExecutionException e) {
                if(LogConfig.eLog) Log.e("TraceLog",
                        TAG + ".getDownMultiplexer():[Task is excepted.]" + e);
                // NOP
            } catch (TimeoutException e) {
                if(LogConfig.eLog) Log.e("TraceLog",
                        TAG + ".getDownMultiplexer():[Task is timeouted.]" + e);
                // NOP
            }

        }
        mOpenOutputStreamFutureList.clear();

    }

    public synchronized FileOutputStream getNextOutputStream() {
        // Return target.
        FileOutputStream ret = null;

        try {
            // Wait for task finish.
            ret = mOpenOutputStreamFutureList.get(0)
                    .get(Definition.EXECUTOR_TASK_TIMEOUT, TimeUnit.MILLISECONDS);

        } catch (CancellationException e) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".getNextOutputStream():[Task is canceled.]" + e);
            // NOP
        } catch (ExecutionException e) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".getNextOutputStream():[Task is excecpted.]" + e);
            // NOP
        } catch (InterruptedException e) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".getNextOutputStream():[Task is interrupted.]" + e);
            // NOP
        } catch (TimeoutException e) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".getNextOutputStream():[Task is timeouted.]" + e);
            // NOP
        }

        // Delete old OutputStream.
        mOpenOutputStreamFutureList.remove(0);

        // Request new OutputStream.
        mOpenOutputStreamFutureList.add(
                mExecService.submit(new OpenOutputStreamTask(getNextFilePath())));

        // Count up.
        ++mCount;

        return ret;
    }

    private class OpenOutputStreamTask
            implements Callable<FileOutputStream> {
        private static final String TAG = "OutputStreamMultiplexer.OpenOutputStreamTask";

        private String mTargetFilePath;

        // CONSTRUCTOR
        public OpenOutputStreamTask(String filePath) {
            mTargetFilePath = filePath;

        }

        @Override
        public FileOutputStream call() {
            if (LogConfig.isTimeDebug) Log.e("TraceLog",
                    "[PERFORMANCE] [TIME = " + System.currentTimeMillis()
                    + "] [UnlimitedBurstShot:Get FileOutputStream START] [PATH="
                    + mTargetFilePath + "]");
            if(LogConfig.dLog) Log.d("TraceLog", TAG + ".call():[Task START]");

            FileOutputStream fos;

            // Open stream.
            try {
                fos = new FileOutputStream(mTargetFilePath);

            } catch (FileNotFoundException e) {
                if(LogConfig.eLog) Log.e("TraceLog",
                        TAG + ".call():[FileNotFoundException]");
                e.printStackTrace();

                fos = null;

            }

            if (LogConfig.isTimeDebug) Log.e("TraceLog",
                    "[PERFORMANCE] [TIME = " + System.currentTimeMillis()
                    + "] [UnlimitedBurstShot:Get FileOutputStream FINISH]");
            if(LogConfig.dLog) Log.d("TraceLog", TAG + ".call():[Task FINISH]");

            return fos;
        }

    }

}
