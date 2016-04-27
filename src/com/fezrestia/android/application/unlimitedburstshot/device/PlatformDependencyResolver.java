package com.fezrestia.android.application.unlimitedburstshot.device;

import java.util.ArrayList;
import java.util.List;

import com.fezrestia.android.application.unlimitedburstshot.Definition;
import com.fezrestia.android.utility.java.Utility;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;



public class PlatformDependencyResolver {
//    private static final String TAG = "PlatformDependencyResolver";

    // Aspect ratio clearance percentage.
    private static final int ASPECT_RATIO_CLEARANCE_PERCENTAGE = 10;

    // Get optimal preview size.
    public static Camera.Size getOptimalPreviewSize(
            Context context, Camera.Parameters params) {
        // Get display Rectangles.
        Rect displayRect = Utility.getLandscapeDisplayRect(context);

        // Find acceptable preview size list.
        List<Camera.Size> acceptablePreviewSize = new ArrayList<Camera.Size>();
        int displayAspectPercent
                = (int) ((((float) displayRect.width()) / displayRect.height()) * 100);
//android.util.Log.e("TraceLog", TAG + ".getOptimalPreviewSize():[DISP PERCENT = " + displayAspectPercent + "]");
        for (Camera.Size preview : params.getSupportedPreviewSizes()) {
            int previewAspectPercent
                    = (int) ((((float) preview.width) / preview.height) * 100);
//android.util.Log.e("TraceLog", TAG + ".getOptimalPreviewSize():[PREV PERCENT = " + previewAspectPercent + "]");
            // Preview aspect is always wider than display.
            if ((previewAspectPercent - displayAspectPercent)
                    < ASPECT_RATIO_CLEARANCE_PERCENTAGE) {
//android.util.Log.e("TraceLog", TAG + ".getOptimalPreviewSize():[AcceptablePreviewSize][W = "+preview.width+"][H = "+preview.height+"]");
                acceptablePreviewSize.add(preview);

            }

        }

        // Target.
        Camera.Size optimalPreviewSize = null;

        // Search optimal preview size from list.
        int sizeDiff = displayRect.width();
        for (Camera.Size preview : acceptablePreviewSize) {
            if (displayRect.width() <= preview.width) {
                if ((preview.width - displayRect.width()) < sizeDiff) {
                    sizeDiff = preview.width - displayRect.width();
                    optimalPreviewSize = preview;

                }

            }

        }

        // If not available, use 16:9 instead of default.
        int percent16vs9 = (int) (16.0f / 9.0f * 100);
        if (optimalPreviewSize == null) {
            for (Camera.Size preview : params.getSupportedPreviewSizes()) {
                int previewAspectPercent
                        = (int) ((((float) preview.width) / preview.height) * 100);
                if ((previewAspectPercent - percent16vs9)
                        < ASPECT_RATIO_CLEARANCE_PERCENTAGE) {
                    if ((optimalPreviewSize == null)
                            || (optimalPreviewSize.width < preview.width)) {
                        optimalPreviewSize = preview;
                    }
                }
            }
        }

        // If not available, use 4:3 instead of default.
        int percent4vs3 = (int) (4.0f / 3.0f * 100);
        if (optimalPreviewSize == null) {
            for (Camera.Size preview : params.getSupportedPreviewSizes()) {
                int previewAspectPercent
                        = (int) ((((float) preview.width) / preview.height) * 100);
                if ((previewAspectPercent - percent4vs3)
                        < ASPECT_RATIO_CLEARANCE_PERCENTAGE) {
                    if ((optimalPreviewSize == null)
                            || (optimalPreviewSize.width < preview.width)) {
                        optimalPreviewSize = preview;
                    }
                }
            }
        }

        // If still optimal size is not available, use default.
        if (optimalPreviewSize == null) {
            optimalPreviewSize = params.getPreviewSize();
        }

//android.util.Log.e("TraceLog", TAG + ".getOptimalPreviewSize():[W = "+optimalPreviewSize.width+"][H = "+optimalPreviewSize.height+"]");
        return optimalPreviewSize;
    }

    // Get optimal picture size.
    public static Camera.Size getOptimalPictureSize(
            Camera.Size optimalPreviewSize, Camera.Parameters params) {
        // Get preview aspect.
        int previewAspectPercent =
                (int) ((((float) optimalPreviewSize.width) / optimalPreviewSize.height) * 100);
//android.util.Log.e("TraceLog", TAG + ".getOptimalPictureSize():[PREV PERCENT = " + previewAspectPercent + "]");

        // Find acceptable picture size list.
        List<Camera.Size> acceptablePictureSize = new ArrayList<Camera.Size>();
        for (Camera.Size picture : params.getSupportedPictureSizes()) {
            int pictureAspectPercent
                    = (int) ((((float) picture.width) / picture.height) * 100);
//android.util.Log.e("TraceLog", TAG + ".getOptimalPictureSize():[PICT PERCENT = " + pictureAspectPercent + "]");
            // Picture aspect have to be near by preview aspect.
            if (Math.abs(pictureAspectPercent - previewAspectPercent)
                    < ASPECT_RATIO_CLEARANCE_PERCENTAGE / 2) {
//android.util.Log.e("TraceLog", TAG + ".getOptimalPictureSize():[AcceptablePictureSize][W = "+picture.width+"][H = "+picture.height+"]");
                acceptablePictureSize.add(picture);

            }

        }

        // Get optimal picture size, if not available, use default value.
        int optWidth = 0;
        Camera.Size optimalPictureSize = null;
        for (Camera.Size picture : acceptablePictureSize) {
            if (optWidth <= picture.width) {
                optWidth = picture.width;
                optimalPictureSize = picture;

            }

        }

        // If optimal picture size is not available, use default.
        if (optimalPictureSize == null) {
            optimalPictureSize = params.getPictureSize();

        }

//android.util.Log.e("TraceLog", TAG + ".getOptimalPictureSize():[W = "+optimalPictureSize.width+"][H = "+optimalPictureSize.height+"]");
        return optimalPictureSize;
    }

    // Get smallest but larger than preview Picture size.
    public static Camera.Size getSmallestButLargerThanPreviewPictureSize(
            Context ctx, Camera.Parameters params) {
//android.util.Log.e("TraceLog", TAG + ".getSmallestButLargerThanPreviewPictureSize():[IN]");
        // Get optimal preview size.
        Camera.Size previewSize = getOptimalPreviewSize(ctx, params);

        // Get preview aspect.
        int previewAspectPercent =
                (int) ((((float) previewSize.width) / previewSize.height) * 100);

        // Find acceptable picture size list.
        List<Camera.Size> acceptablePictureSize = new ArrayList<Camera.Size>();
        for (Camera.Size picture : params.getSupportedPictureSizes()) {
            // Calculate picture aspect.
            int pictureAspectPercent
                    = (int) ((((float) picture.width) / picture.height) * 100);

            // Compare aspect ratio.
            if (Math.abs(pictureAspectPercent - previewAspectPercent)
                    < ASPECT_RATIO_CLEARANCE_PERCENTAGE / 2) {
//android.util.Log.e("TraceLog", TAG + ".getSmallestButLargerThanPreviewPictureSize():[AcceptablePictureSize] [W="+picture.width+"] [H="+picture.height+"]");
                acceptablePictureSize.add(picture);
            }
        }

        // Get smallest but larger than preview size.
        int targetWidth = Integer.MAX_VALUE;
        Camera.Size targetSize = null;
        for (Camera.Size picture : acceptablePictureSize) {
            // Compare with preview.
            if (previewSize.width <= picture.width) {
                // Compare with targetWidth.
                if (picture.width <= targetWidth) {
                    targetWidth = picture.width;
                    targetSize = picture;
                }
            }
        }

        // If target size is not available, use default.
        if (targetSize == null) {
//android.util.Log.e("TraceLog", TAG + ".getSmallestButLargerThanPreviewPictureSize():[Use default picture size]");
            targetSize = params.getPictureSize();
        }

//android.util.Log.e("TraceLog", TAG + ".getSmallestButLargerThanPreviewPictureSize():[OUT] [W="+targetSize.width+"] [H="+targetSize.height+"]");
        return targetSize;
    }

    // Get optimal recording size. If not available, return optimal preview size.
    public static Camera.Size getOptimalRecordingSize(
            Context context, Camera.Parameters params) {
        // FullHD
        for (Camera.Size recording : params.getSupportedPreviewSizes()) {
            if ((recording.width == Definition.FULL_HD_WIDTH)
                    && (recording.height == Definition.FULL_HD_HEIGHT)) {
                return recording;
            }
        }
        // HD
        for (Camera.Size recording : params.getSupportedPreviewSizes()) {
            if ((recording.width == Definition.HD_WIDTH)
                    && (recording.height == Definition.HD_HEIGHT)) {
                return recording;
            }
        }
        // DVD
        for (Camera.Size recording : params.getSupportedPreviewSizes()) {
            if ((recording.width == Definition.DVD_WIDTH)
                    && (recording.height == Definition.DVD_HEIGHT)) {
                return recording;
            }
        }
        // OneSeg
        for (Camera.Size recording : params.getSupportedPreviewSizes()) {
            if ((recording.width == Definition.ONESEG_WIDTH)
                    && (recording.height == Definition.ONESEG_HEIGHT)) {
                return recording;
            }
        }

        return getOptimalPreviewSize(context, params);
    }

    public static String getOptimalFocusMode(Camera.Parameters params) {
        List<String> supportedModes = params.getSupportedFocusModes();

        // 1st priority.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
//android.util.Log.e("TraceLog", TAG + ".getOptimalFocusMode():[SDK_VERSION<=GINGERBREAD_MR1]");
            if (supportedModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                return Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
            }
        } else if (Build.VERSION_CODES.ICE_CREAM_SANDWICH <= Build.VERSION.SDK_INT) {
//android.util.Log.e("TraceLog", TAG + ".getOptimalFocusMode():[ICE_CREAM_SANDWICH<=SDK_VERSION]");
            if (supportedModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                return Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
            }
        }

        // Use default setting.
        return params.getFocusMode();
    }
}
