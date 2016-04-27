package com.fezrestia.android.application.unlimitedburstshot;

import com.fezrestia.android.common.log.LogConfig;

public class Definition {

    // Time.
    public static final int EXECUTOR_TASK_TIMEOUT = 5000;
    public static final int LAZY_INITIALIZATION_TASK_WAIT = 200;

    // Frame size definitions.
    public static final int FULL_HD_WIDTH = 1920;
    public static final int FULL_HD_HEIGHT = 1080;
    public static final int HD_WIDTH = 1280;
    public static final int HD_HEIGHT = 720;
    public static final int DVD_WIDTH = 720;
    public static final int DVD_HEIGHT = 480;
    public static final int ONESEG_WIDTH = 320;
    public static final int ONESEG_HEIGHT = 240;

    // Directory paths.
    public static final String UNLIMITEDBURSTSHOT_STORAGE_PATH
            = LogConfig.isDebugStorage
            ? "/a_STORAGE/TEST/.UnlimitedBurstShot/"
            : "/a_STORAGE/UnlimitedBurstShot/";

    // File extensions.
    public static final String PHOTO_FILE_EXTENSION = ".JPG";
}
