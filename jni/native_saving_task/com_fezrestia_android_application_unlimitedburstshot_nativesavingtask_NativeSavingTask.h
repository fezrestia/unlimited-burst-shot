﻿/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask */

#ifndef _Included_com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask
#define _Included_com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask

#ifdef __cplusplus
extern "C" {
#endif



/*
 * Class:     com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask
 * Method:    doInitialize
 */
JNIEXPORT jint JNICALL Java_com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask_doInitialize(
        JNIEnv* jenv, jobject jobj,
        jint width, jint height, jbyteArray yuvData, jint previewFormat, jint jpegQuality,
        jstring filePath);



/*
 * Class:     com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask
 * Method:    doStoreYuvByteArrayAsJpegFile
 */
JNIEXPORT jint JNICALL Java_com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask_doStoreYuvByteArrayAsJpegFile(
        JNIEnv* , jobject);



/*
 * Class:     com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask
 * Method:    doFinalize
 */
JNIEXPORT jint JNICALL Java_com_fezrestia_android_application_unlimitedburstshot_nativesavingtask_NativeSavingTask_doFinalize(
        JNIEnv* , jobject);



#ifdef __cplusplus
}
#endif

#endif
