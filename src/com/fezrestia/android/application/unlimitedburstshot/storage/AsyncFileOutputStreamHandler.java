package com.fezrestia.android.application.unlimitedburstshot.storage;

import java.io.FileNotFoundException;
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

import android.util.Log;

import com.fezrestia.android.application.unlimitedburstshot.Definition;
import com.fezrestia.android.common.log.LogConfig;



public class AsyncFileOutputStreamHandler {
    private static final String TAG = "AsyncFileOutputStreamHandler";

    // Singleton instance.
    private static AsyncFileOutputStreamHandler instance = new AsyncFileOutputStreamHandler();

    // Task future for open OutputStream.
    private ExecutorService mExecService;
    private Future<FosAndPathContainer> mOutputStreamFuture;

    // File path name parts.
    private String mFilePathPreFix;
    private String mFilePathPostFix;

    // File count.
    private int mCount;

    // CONSTRUCTOR
    private AsyncFileOutputStreamHandler() {
        // NOP
    }

    public static AsyncFileOutputStreamHandler getInstance() {
        return instance;
    }

    public void setUp(String filePathPreFix, String filePathPostFix) {
        // Create executor and start task.
        mExecService = Executors.newSingleThreadExecutor();

        // Reset.
        reset(filePathPreFix, filePathPostFix);
    }

    public void reset(String filePathPreFix, String filePathPostFix) {
        // Reset count.
        mCount = 0;

        // Copy file path parts.
        mFilePathPreFix = filePathPreFix;
        mFilePathPostFix = filePathPostFix;
    }

    public void prepareNextFileOutputStream() {
        // Submit open FOS task.
        mOutputStreamFuture = mExecService.submit(new OpenOutputStreamTask(getNextFilePath()));

    }

    private String getNextFilePath() {
        return mFilePathPreFix + "_" + getCountString(mCount) + mFilePathPostFix;
    }

    private String getCountString(int count) {
        String str = Integer.toString(count);

        while (str.length() < 4) {  // Max character number.
            str = "0" + str;

        }

        return str;
    }

    public FosAndPathContainer getNextFosAndPathContainer() {
        // Return target.
        FosAndPathContainer ret = null;

        try {
            // Wait for task finish.
            ret = mOutputStreamFuture.get(
                    Definition.EXECUTOR_TASK_TIMEOUT, TimeUnit.MILLISECONDS);

        } catch (CancellationException e) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".getNextFileOutputStream():[Task is canceled.]" + e);
            // NOP
        } catch (ExecutionException e) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".getNextFileOutputStream():[Task is excecpted.]" + e);
            // NOP
        } catch (InterruptedException e) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".getNextFileOutputStream():[Task is interrupted.]" + e);
            // NOP
        } catch (TimeoutException e) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".getNextFileOutputStream():[Task is timeouted.]" + e);
            // NOP
        }

        // Count up.
        ++mCount;

        return ret;
    }

    public void getDown() {
        // Shutdown and delete executor and task.
        if (mExecService != null) {
            mExecService.shutdown();

        }

        try {
            if (mOutputStreamFuture != null) {
                FosAndPathContainer container = mOutputStreamFuture.get(
                        Definition.EXECUTOR_TASK_TIMEOUT, TimeUnit.MILLISECONDS);
                if (container.fos != null) {
                    container.fos.close();

                }

            }

        } catch (IOException e) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".getDown():[IOException]" + e);
            // NOP
        } catch (InterruptedException e) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".getDown():[Task is interrupted.]" + e);
            // NOP
        } catch (ExecutionException e) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".getDown():[Task is excepted.]" + e);
            // NOP
        } catch (TimeoutException e) {
            if(LogConfig.eLog) Log.e("TraceLog",
                    TAG + ".getDown():[Task is timeouted.]" + e);
            // NOP
        }

        mExecService = null;
        mOutputStreamFuture = null;

    }

    private class OpenOutputStreamTask
            implements Callable<FosAndPathContainer> {
        private static final String TAG
                = AsyncFileOutputStreamHandler.TAG + ".OpenOutputStreamTask";

        private String mTargetFilePath;

        // CONSTRUCTOR
        public OpenOutputStreamTask(String filePath) {
            mTargetFilePath = filePath;

        }

        @Override
        public FosAndPathContainer call() {
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

            return new FosAndPathContainer(fos, mTargetFilePath);
        }

    }

    public class FosAndPathContainer {
        public final FileOutputStream fos;
        public final String path;

        // CONSTRUCTOR
        public FosAndPathContainer(FileOutputStream f, String p) {
            fos = f;
            path = p;

        }

    }

}
