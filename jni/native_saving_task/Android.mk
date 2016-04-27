# Android.mk
# Build NativeSavingTask

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES = \
    $../jpegsr8d \
    $(JNI_H_INCLUDE) \

LOCAL_MODULE := native_saving_task

LOCAL_SRC_FILES := NativeSavingTask.c

LOCAL_STATIC_LIBRARIES += \
    local_libjpeg \

LOCAL_LDLIBS := \
    -L./lib \
    -landroid \
    -llog \
    -ljnigraphics \

include $(BUILD_SHARED_LIBRARY)
