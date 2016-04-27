package com.fezrestia.android.utility.java;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.fezrestia.android.common.log.LogConfig;

import android.content.Context;
import android.graphics.Rect;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class Utility {
//    private static final String TAG = "Utility";

// MEDIA SCANNER
    //request MediaScanner to scan new file.
    public static class MediaScannerNotifier implements MediaScannerConnectionClient {
        private static final String TAG = "MediaScannerNotifier:";

        private android.media.MediaScannerConnection mConnection;
        private String mPath;
        private String mMimeType;

        //callback
        private OnScanCompletedCallback callback;

        public MediaScannerNotifier(Context context, String path, String mimeType) {
            if(LogConfig.dLog) Log.d("TraceLog", TAG + "CONSTRUCTOR:[IN]");

            mPath = path;
            mMimeType = mimeType;

            mConnection = new MediaScannerConnection(context, this);
            mConnection.connect();

        }

        @Override
        public void onMediaScannerConnected() {
            if(LogConfig.dLog) Log.d("TraceLog", TAG + "onMediaScannerConnected():[IN]");

            mConnection.scanFile(mPath, mMimeType);

        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            if(LogConfig.dLog) Log.d("TraceLog", TAG + "onScanCompleted():[IN]");

            //call back
            if (callback != null) {
                callback.onScanCompleted(uri);
            }

            //disconnect
            mConnection.disconnect();
            mPath = null;
            mMimeType = null;

        }

        public interface OnScanCompletedCallback {
            abstract public void onScanCompleted(Uri uri);

        }

        public void setOnScanCompletedCallback(OnScanCompletedCallback cb) {
            callback = cb;

        }

    }

    // Request MediaScanner to scan new files for repeated captured photos.
    public static class MediaScannerNotifierForFileSequence
            implements
                    MediaScannerConnection.OnScanCompletedListener
    {
//        private static final String TAG = "MediaScannerNotifierForFileArray";

        private final Context mContext;
        private final OnScanCompletedCallback mCallback;
        private String mTopFilePath;
        private List<String> mTargetFilePathArray = new ArrayList<String>();
        private List<String> mMimeTypeArray = new ArrayList<String>();

        // CONSTRUCTOR
        public MediaScannerNotifierForFileSequence(
                Context context, OnScanCompletedCallback callback) {
            mContext = context;
            mCallback = callback;

        }

        public void pushTargetItem(String filePath, String mimeType) {
            mTargetFilePathArray.add(filePath);
            mMimeTypeArray.add(mimeType);

        }

        public void requestScan() {
            if (mTargetFilePathArray.isEmpty()) {
                // NOP.
                return;
            }

            // Request scan all files.
            MediaScannerConnection.scanFile(
                    mContext,
                    (String[]) mTargetFilePathArray.toArray(new String[0]),
                    (String[]) mMimeTypeArray.toArray(new String[0]),
                    this);

            // Store top file path. This is target of callback URI.
            mTopFilePath = mTargetFilePathArray.get(0);

        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            if (path.equals(mTopFilePath)) {
                // This URI is the 1st captured one.
                if (mCallback != null) {
                    mCallback.onScanCompleted(uri);

                    // Reset.
                    mTopFilePath = null;

                }

            }

        }

        public interface OnScanCompletedCallback {
            abstract public void onScanCompleted(Uri uri);

        }

        public void clear() {
            mTargetFilePathArray.clear();
            mMimeTypeArray.clear();

        }

    }

// CREATE LOCAL DIRECTORY
    //create a new directory if it is not exist
    //dirPath:without root directory path
    public static boolean createDirectoryInExtStorage(String dirPath) {
        String TAG = "Utility.createDirectoryInExtStorage():";

        String targetDirPath = Environment.getExternalStorageDirectory().getPath() + dirPath;

        File file = new File(targetDirPath);

        try {
            if (!file.exists()) {
                //if directory is not exist, create a new directory
                file.mkdirs();
            } else {
                if(LogConfig.dLog) Log.d("TraceLog", TAG
                        + "[Target directory is already exist]");
            }

            return true;

        } catch (SecurityException e) {
            if(LogConfig.eLog) Log.e("TraceLog", TAG
                    + "[SecurityExteption][Failed to create a new directory.]");

            return false;
        }

    }

// VIEW HIT TEST
    //MotionEvent point is contained in target view or not
    public static boolean hitTest(View targetView, MotionEvent motion) {
        String TAG = "hitTest:";

        boolean ret = false;

        //get view location
        int[] locationOfView = new int[2]; //x,y
        targetView.getLocationOnScreen(locationOfView);

        //hit test
        Rect rect = new Rect(locationOfView[0], locationOfView[1],
                locationOfView[0] + targetView.getWidth(),
                locationOfView[1] + targetView.getHeight());

        ret = rect.contains((int) motion.getRawX(), (int)motion.getRawY());

        if(LogConfig.dLog) Log.d("TraceLog", TAG + "hitTest():[ret = " + ret +"]");
        return ret;
    }

// DISPLAY RECT
    public static Rect getDisplayRect(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();

        return new Rect(0, 0, disp.getWidth(), disp.getHeight());
    }

    public static Rect getLandscapeDisplayRect(Context context) {
        Rect disp = getDisplayRect(context);

        Rect ret;
        if (disp.height() < disp.width()) {
            ret = new Rect(0, 0, disp.width(), disp.height());
        } else {
            ret = new Rect(0, 0, disp.height(), disp.width());
        }

        return ret;
    }

    public static Rect getPortraitDisplayRect(Context context) {
        Rect disp = getDisplayRect(context);

        Rect ret;
        if (disp.width() < disp.height()) {
            ret = new Rect(0, 0, disp.width(), disp.height());
        } else {
            ret = new Rect(0, 0, disp.height(), disp.width());
        }

        return ret;
    }

// INT TO HEX-STRING CONVERTER
    public static String convertIntToHexString(int i) {
        // Hex string.
        String hexString = Integer.toHexString(i);

        // Construct.
        while (hexString.length() < 8) {    // 32bit system.
            hexString = "0" + hexString;

        }

        // Add Pre-Fix.
        hexString = "0x" + hexString;

        return hexString;
    }

}
