LOCAL_PATH := $(call my-dir)
LOCAL_SRC_FILES := medialibrary.cpp AndroidMediaLibrary.cpp
LOCAL_MODULE    := mla
LOCAL_MODULE_FILENAME := libmla
LOCAL_LDLIBS    += -L$(OUT_LIB_DIR) -lmedialibrary -L$(SYSROOT)/usr/lib -llog
LOCAL_C_INCLUDES := $(MEDIALIBRARY_INCLUDE_DIR)
include $(BUILD_SHARED_LIBRARY)