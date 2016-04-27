#ifndef _NativeSavingTaskH
#define _NativeSavingTaskH

// JAVA layer field ID.
#define JAVA_LAYER_ID "mNativeDataStructPointerAddress"

// Fetch NativeSavingTaskDataStruct pointer.
#define GET_NATIVE_SAVING_TASK_DATA_STRUCT(STRUCT, JENV, JOBJ) \
        { \
            jclass jcls = (*JENV)->GetObjectClass(JENV, JOBJ); \
            jfieldID jfld = (*JENV)->GetFieldID( \
                    JENV, jcls, JAVA_LAYER_ID, "I"); \
            if (jfld == NULL) { \
                return -1; \
            } \
            STRUCT = (NativeSavingTaskDataStruct*) (*JENV)->GetIntField(JENV, JOBJ, jfld); \
        }

// NativeSavingTaskDataStruct define.
typedef struct {
    // Work buffer.
    unsigned char* p_YUV_frame;
    unsigned char* p_RGB_frame;

    // File path.
    const char* p_TargetFilePath;

} NativeSavingTaskDataStruct;

#endif
