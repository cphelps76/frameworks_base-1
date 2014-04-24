LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

ifneq ($(PRODUCT_BRAND),TV)
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
    ../../../ex/carousel/java/com/android/ex/carousel/carousel.rs

LOCAL_JAVA_LIBRARIES := services telephony-common

LOCAL_STATIC_JAVA_LIBRARIES := android-common-carousel libGoogleAnalyticsV2

LOCAL_PACKAGE_NAME := SystemUI
LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)
endif

include $(call all-makefiles-under,$(LOCAL_PATH))
