#include "com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask.h"

#include "NativeSavingTask.h"

#include <android/log.h>
#include <android/bitmap.h>

#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#include "jpeglib.h"
#include "jerror.h"



#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "TraceLog", __VA_ARGS__)



// Function declaration.
static void YUV420P_2_RGB888(jint jwidth, jint jheight, jbyte *p_yuv, unsigned char *p_rgb);
static int RGB888_2_JPEG(
        const unsigned char* p_input_rgb, int width, int height, FILE* p_output_file);



//// Static constant parameters for all NativeSavingTask instances.

// Image parameters.
int mFrameWidth = 0;
int mFrameHeight = 0;
int mBufferLength = 0;

// Frame image format.
int mImageFormat = 0;

// JPEG parameters.
int mJpegQuality = 100;



//// TIME DEBUG
clock_t mStartClock, mEndClock;



/*
 * Class:     com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask
 * Method:    doInitialize
 */
JNIEXPORT jint JNICALL Java_com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask_doInitialize(
        JNIEnv* jenv, jobject jobj,
        jint width, jint height, jbyteArray yuvData, jint previewFormat, jint jpegQuality,
        jstring filePath) {
    // Work buf.
    unsigned char* yuv = NULL;
    int yuvLen = 0;
    int i = 0;
    jclass jcls;
    jfieldID jfld;

    // Alloc NativeSavingTaskDataStruct space.
    NativeSavingTaskDataStruct* p_data_struct;
    p_data_struct = (NativeSavingTaskDataStruct*) malloc(sizeof(NativeSavingTaskDataStruct));
    if (p_data_struct == NULL) {
        // Failed to allocate memory.
        return -1;
    }

    // Store parameters.
    mFrameWidth = width;
    mFrameHeight = height;
    mBufferLength = 3 * width * height;
    mImageFormat = previewFormat;
    mJpegQuality = jpegQuality;

    // Get target file path.
    p_data_struct->p_TargetFilePath = (*jenv)->GetStringUTFChars(jenv, filePath, NULL);

    // Get input YUV data from JAVA layer.
    yuv = (*jenv)->GetByteArrayElements(jenv, yuvData, NULL);
    yuvLen = (*jenv)->GetArrayLength(jenv, yuvData);
    p_data_struct->p_YUV_frame = (unsigned char*) malloc(yuvLen);
    if (p_data_struct->p_YUV_frame == NULL) {
        return -1;
    }
    for (i = 0; i < yuvLen; ++i) {
        p_data_struct->p_YUV_frame[i] = yuv[i];
    }
    (*jenv)->ReleaseByteArrayElements(jenv, yuvData, yuv, 0); // Release JAVA heap.

    // Allocate RGB frame space.
    p_data_struct->p_RGB_frame = (unsigned char*) malloc(mBufferLength);
    if (p_data_struct->p_RGB_frame == NULL) {
        return -1;
    }

    // Set DataStruct pointer to JAVA layer field.
    jcls = (*jenv)->GetObjectClass(jenv, jobj);
    jfld = (*jenv)->GetFieldID(jenv, jcls, JAVA_LAYER_ID, "I");
    if (jfld == NULL) {
        return -1;
    }

    (*jenv)->SetIntField(jenv, jobj, jfld, (int) p_data_struct);



/*
LOGE("### StoreData Performance Test:[SET UP] [TIME=%d]", (int) clock());

    // Create 3[MB] data.
    unsigned char* buf = NULL;
    int len = 3 * 1024 * 1024;
    int ite = 0;
    buf = (unsigned char*) malloc(len);

    // Initialize.
    for (ite = 0; ite < len; ++ite) {
        if (ite % 2 == 0) {
            buf[ite] = 0;
        } else {
            buf[ite] = 1;
        }
    }

    // File pointer.
    FILE* p_output_file;

LOGE("### StoreData Performance Test:[GO!!] [TIME=%d]", (int) clock());

    // Open, write, flush, and close.
    p_output_file = fopen("/mnt/sdcard/000.bin", "wb");
    fwrite(buf, sizeof(unsigned char*), len, p_output_file);
    fflush(p_output_file);
    fclose(p_output_file);

LOGE("### StoreData Performance Test:[DONE] [TIME=%d]", (int) clock());

    // Open, write, flush, and close.
    p_output_file = fopen("/mnt/sdcard/001.bin", "wb");
    fwrite(buf, sizeof(unsigned char*), len, p_output_file);
    fflush(p_output_file);
    fclose(p_output_file);

LOGE("### StoreData Performance Test:[DONE] [TIME=%d]", (int) clock());

    // Open, write, flush, and close.
    p_output_file = fopen("/mnt/sdcard/002.bin", "wb");
    fwrite(buf, sizeof(unsigned char*), len, p_output_file);
    fflush(p_output_file);
    fclose(p_output_file);

LOGE("### StoreData Performance Test:[DONE] [TIME=%d]", (int) clock());

    // Open, write, flush, and close.
    p_output_file = fopen("/mnt/sdcard/003.bin", "wb");
    fwrite(buf, sizeof(unsigned char*), len, p_output_file);
    fflush(p_output_file);
    fclose(p_output_file);

LOGE("### StoreData Performance Test:[DONE] [TIME=%d]", (int) clock());

    // Open, write, flush, and close.
    p_output_file = fopen("/mnt/sdcard/004.bin", "wb");
    fwrite(buf, sizeof(unsigned char*), len, p_output_file);
    fflush(p_output_file);
    fclose(p_output_file);

LOGE("### StoreData Performance Test:[DONE] [TIME=%d]", (int) clock());

    // Open, write, flush, and close.
    p_output_file = fopen("/mnt/sdcard/005.bin", "wb");
    fwrite(buf, sizeof(unsigned char*), len, p_output_file);
    fflush(p_output_file);
    fclose(p_output_file);

LOGE("### StoreData Performance Test:[DONE] [TIME=%d]", (int) clock());

    // Open, write, flush, and close.
    p_output_file = fopen("/mnt/sdcard/006.bin", "wb");
    fwrite(buf, sizeof(unsigned char*), len, p_output_file);
    fflush(p_output_file);
    fclose(p_output_file);

LOGE("### StoreData Performance Test:[DONE] [TIME=%d]", (int) clock());

    // Open, write, flush, and close.
    p_output_file = fopen("/mnt/sdcard/007.bin", "wb");
    fwrite(buf, sizeof(unsigned char*), len, p_output_file);
    fflush(p_output_file);
    fclose(p_output_file);

LOGE("### StoreData Performance Test:[DONE] [TIME=%d]", (int) clock());

    // Open, write, flush, and close.
    p_output_file = fopen("/mnt/sdcard/008.bin", "wb");
    fwrite(buf, sizeof(unsigned char*), len, p_output_file);
    fflush(p_output_file);
    fclose(p_output_file);

LOGE("### StoreData Performance Test:[DONE] [TIME=%d]", (int) clock());

    // Open, write, flush, and close.
    p_output_file = fopen("/mnt/sdcard/009.bin", "wb");
    fwrite(buf, sizeof(unsigned char*), len, p_output_file);
    fflush(p_output_file);
    fclose(p_output_file);

LOGE("### StoreData Performance Test:[DONE] [TIME=%d]", (int) clock());

    free(buf);
    buf = NULL;

LOGE("### NativeSavingTask.doInitialize():[OUT][TIME=%d]", (int) clock());
*/



    return 0;
}


/*
 * Class:     com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask
 * Method:    doFinalize
 */
JNIEXPORT jint JNICALL Java_com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask_doFinalize(
        JNIEnv* jenv, jobject jobj) {
    // Data container of this instance.
    NativeSavingTaskDataStruct* p_data_struct;
    GET_NATIVE_SAVING_TASK_DATA_STRUCT(p_data_struct, jenv, jobj);

    if (p_data_struct->p_YUV_frame != NULL) {
        free(p_data_struct->p_YUV_frame);
        p_data_struct->p_YUV_frame = NULL;
    }
    if (p_data_struct->p_RGB_frame != NULL) {
        free(p_data_struct->p_RGB_frame);
        p_data_struct->p_RGB_frame = NULL;
    }
    p_data_struct->p_TargetFilePath = NULL;

    free(p_data_struct);
    p_data_struct = NULL;

    return 0;
}



/*
 * Class:     com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask
 * Method:    doStoreYuvByteArrayAsJpegFile
 */
JNIEXPORT jint JNICALL Java_com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask_doStoreYuvByteArrayAsJpegFile(
        JNIEnv* jenv, jobject jobj) {
    // Data container of this instance.
    NativeSavingTaskDataStruct* p_data_struct;
    GET_NATIVE_SAVING_TASK_DATA_STRUCT(p_data_struct, jenv, jobj);

// TIME DEBUG
    mStartClock = clock();
//LOGE("### NativeSavingTask.doStoreYuvByteArrayAsJpegFile():[IN][TIME=%d]", mStartClock / (CLOCKS_PER_SEC / 1000));

    if (p_data_struct->p_YUV_frame == NULL) {
//LOGE("### NativeSavingTask.doStoreYuvByteArrayAsJpegFile():[p_YuvFrame==NULL]");
        return -1;
    }
    if (p_data_struct->p_RGB_frame == NULL) {
//LOGE("### NativeSavingTask.doStoreYuvByteArrayAsJpegFile():[p_RgbFrame==NULL]");
        return -1;
    }

// TIME DEBUG
    mEndClock = clock();
//LOGE("### NativeSavingTask.doStoreYuvByteArrayAsJpegFile():[YUV to RTB convert : START][TIME=%d]", mEndClock / (CLOCKS_PER_SEC / 1000));

    // Convert YUV420P to RGB888.
    YUV420P_2_RGB888(
            mFrameWidth, mFrameHeight, p_data_struct->p_YUV_frame, p_data_struct->p_RGB_frame);

// TIME DEBUG
    mEndClock = clock();
//LOGE("### NativeSavingTask.doStoreYuvByteArrayAsJpegFile():[YUV to RTB convert : FINISH][TIME=%d]", mEndClock / (CLOCKS_PER_SEC / 1000));

    // Prepare file.
    FILE* p_output_file;
    if ((p_output_file = fopen(p_data_struct->p_TargetFilePath, "wb")) == NULL) {
//LOGE("### NativeSavingTask.doStoreYuvByteArrayAsJpegFile():[FILE OPEN FAILED]");
        return -1;
    }
//LOGE("### NativeSavingTask.doStoreYuvByteArrayAsJpegFile():[fopen : DONE]");

// TIME DEBUG
    mEndClock = clock();
//LOGE("### NativeSavingTask.doStoreYuvByteArrayAsJpegFile():[JPEG : START][TIME=%d]", mEndClock / (CLOCKS_PER_SEC / 1000));

    // Compress RGB888 to JPEG.
    RGB888_2_JPEG(p_data_struct->p_RGB_frame, mFrameWidth, mFrameHeight, p_output_file);

// TIME DEBUG
    mEndClock = clock();
//LOGE("### NativeSavingTask.doStoreYuvByteArrayAsJpegFile():[JPEG : FINISH][TIME=%d]", mEndClock / (CLOCKS_PER_SEC / 1000));

    fflush(p_output_file);

//LOGE("### NativeSavingTask.doStoreYuvByteArrayAsJpegFile():[fflush : DONE]");

    fclose(p_output_file);

//LOGE("### NativeSavingTask.doStoreYuvByteArrayAsJpegFile():[fclose : DONE]");

    return 0;
}




// JPEC Compression.

static void jpegErrorHandler(j_common_ptr cinfo) {
    char pszMessage[JMSG_LENGTH_MAX];
    (*cinfo->err->format_message) (cinfo, pszMessage);
    LOGE("LIB_JPEG ERROR:[%s]", pszMessage);
}

static int RGB888_2_JPEG(
        const unsigned char* p_input_rgb, int width, int height, FILE* p_output_file) {
    // Return code.
    int ret = 0;

    // Iterator.
    int i = 0;

    // Temp pointer.
    const unsigned char* p_current_line_top = NULL;

    // JPEG objects.
    struct jpeg_compress_struct cInfo;
    struct jpeg_error_mgr jError;

    // Initialize.

//LOGE("### RGB888_2_JPEG:[INITIALIZE : START]");

    cInfo.err = jpeg_std_error(&jError);
    jError.error_exit = jpegErrorHandler;
    jpeg_create_compress(&cInfo);

//LOGE("### RGB888_2_JPEG:[INITIALIZE : FINISH]");

    // Set target output stream.
    jpeg_stdio_dest(&cInfo, p_output_file);

//LOGE("### RGB888_2_JPEG:[SET DEST FILE : DONE]");

    // Set parameters.
    cInfo.image_width = width;
    cInfo.image_height = height;
    cInfo.input_components = 3; // RGB color. (1=GrayScale)
    cInfo.in_color_space = JCS_RGB; // RGB color. (JCS_GRAYSCALE=GrayScale)
    jpeg_set_defaults(&cInfo);

//LOGE("### RGB888_2_JPEG:[SET PARAMETERS : DONE]");

    // Quality.
    jpeg_set_quality(&cInfo, mJpegQuality, TRUE);

//LOGE("### RGB888_2_JPEG:[SET QUALITY : DONE]");

    // Prepare.
    jpeg_start_compress(&cInfo, TRUE);

//LOGE("### RGB888_2_JPEG:[PREPARE : DONE]");

    // Compress.
    ret = 0;
    p_current_line_top = p_input_rgb;
    for (i = 0; i < height; ++i) {
        jpeg_write_scanlines(&cInfo, (JSAMPARRAY) &p_current_line_top, 1);

        p_current_line_top += width * 3;

        ++ret;

//LOGE("### RGB888_2_JPEG:[COMPRESS 1 LINE : DONE]");

    }

//LOGE("### RGB888_2_JPEG:[COMPRESS ALL LINES : DONE]");


//LOGE("### RGB888_2_JPEG:[jpeg_write_scanlines = %d]", ret);

    if (ret != height) {
//LOGE("### RGB888_2_JPEG:[jpeg_write_scanlines = FAIL]");
    }

    // Finalize.
    jpeg_finish_compress(&cInfo);
    jpeg_destroy_compress(&cInfo);

    return 0;
}



// UV420Planar to RGB888.

static void YUV420P_2_RGB888
(jint jwidth, jint jheight, jbyte *p_yuv, unsigned char *p_rgb) {
    jint frameSize = jwidth * jheight;
    jint i, j, yp, y;
    jint uvp, u = 0, v = 0;
    jint y1192;
    jint r, g, b;
    unsigned char *p_w_rgb = p_rgb;

    for(j = 0, yp = 0; j < jheight; j++){

        uvp = frameSize + (j >> 1) * jwidth;

        for(i = 0; i < jwidth; i++, yp++){

            y = (0xff & ((int) p_yuv[yp])) - 16;
            if(y < 0){
                y = 0;
            }
            if((i & 1) == 0){
                v = (0xff & p_yuv[uvp++]) - 128;
                u = (0xff & p_yuv[uvp++]) - 128;
            }

            y1192 = 1192 * y;
            r = (y1192 + 1634 * v);
            g = (y1192 - 833 * v - 400 * u);
            b = (y1192 + 2066 * u);

            if(r < 0){
                r = 0;
            }
            else if(r > 0x0003FFFF){
                r = 0x0003FFFF;
            }
            if(g < 0){
                g = 0;
            }
            else if(g > 0x0003FFFF){
                g = 0x0003FFFF;
            }
            if(b < 0){
                b = 0;
            }
            else if(b > 0x0003FFFF){
                b = 0x0003FFFF;
            }
            // To RGB888 buffer.
            *p_w_rgb = (unsigned char)(((r << 6) & 0xFF0000) >> 16);
            p_w_rgb++;
            *p_w_rgb = (unsigned char)(((g >> 2) & 0xFF00) >> 8);
            p_w_rgb++;
            *p_w_rgb = (unsigned char)((b >> 10) & 0xFF);
            p_w_rgb++;
        }
    }
}
