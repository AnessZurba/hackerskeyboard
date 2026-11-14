# libjni_latinime: the AOSP LatinIME native decoder (see README.md).
#
# Used two ways:
# - from app/CMakeLists.txt (add_subdirectory) for the APK, with the NDK;
# - standalone on the build machine for the JVM unit tests:
#     cmake -S app/src/main/cpp/aosp -B <dir> -DHK_HOST_JNI=ON -DJNI_INCLUDE_DIRS="<jdk>/include;<jdk>/include/linux"
#   (the hostSuggestJni task in app/build.gradle does this).

cmake_minimum_required(VERSION 3.22.1)
project(hk_latinime CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

file(GLOB_RECURSE LATINIME_CORE_SRC CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp)

add_library(jni_latinime SHARED
        com_android_inputmethod_keyboard_ProximityInfo.cpp
        com_android_inputmethod_latin_BinaryDictionary.cpp
        com_android_inputmethod_latin_BinaryDictionaryUtils.cpp
        com_android_inputmethod_latin_DicTraverseSession.cpp
        jni_common.cpp
        ${LATINIME_CORE_SRC})

target_include_directories(jni_latinime PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)

# The upstream warning set (Android.bp) minus -Werror, so a newer compiler's new warnings
# don't break the build of code we don't maintain.
target_compile_options(jni_latinime PRIVATE
        -Wall -Wextra -Wformat=2 -Wcast-qual -Wwrite-strings -Wpointer-arith -Winit-self
        -Woverloaded-virtual -Wno-unused-parameter -Wno-unused-function
        -Wno-vla-cxx-extension -Wno-deprecated-copy -Wno-unknown-warning-option
        -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections
        -O2)

if(HK_HOST_JNI)
    # Desktop build for unit tests: JNI headers from the JDK, no Android log library.
    target_include_directories(jni_latinime PRIVATE ${JNI_INCLUDE_DIRS})
    target_compile_definitions(jni_latinime PRIVATE HOST_TOOL)
else()
    find_library(log-lib log)
    target_link_libraries(jni_latinime ${log-lib})
    # Android 15+ devices may use 16 KB pages: align LOAD segments to 16 KB.
    target_link_options(jni_latinime PRIVATE "-Wl,-z,max-page-size=16384")
    # Only JNI_OnLoad needs exporting: keep the static libc++ private and drop unused code.
    target_link_options(jni_latinime PRIVATE "-Wl,--exclude-libs,ALL" "-Wl,--gc-sections")
endif()
