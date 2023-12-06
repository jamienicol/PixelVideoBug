#ifndef PIXELVIDEO_PIXELVIDEO_H
#define PIXELVIDEO_PIXELVIDEO_H

#include <android/log.h>

#define DEBUG 1

#define LOG_TAG "Renderer"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#if DEBUG
#define ALOGV(...) \
  __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#else
#define ALOGV(...)
#endif

#endif //PIXELVIDEO_PIXELVIDEO_H
