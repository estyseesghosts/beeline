cmake_minimum_required(VERSION 3.22.1)
project(beeline_ffmpeg CXX)

set(CMAKE_CXX_STANDARD 17)

add_library(beeline_ffmpeg SHARED ffmpeg_jni.cpp)
target_link_libraries(beeline_ffmpeg log)
# 16 KB page alignment, required for Android 15+ devices with 16 KB pages.
target_link_options(beeline_ffmpeg PRIVATE -Wl,-z,max-page-size=16384)

# FFmpeg exists for arm64-v8a only (tools/ffmpeg/build.sh). Other ABIs get a stub that reports "unavailable".
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(FFMPEG_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../tools/ffmpeg/prebuilt)
    file(STRINGS ${FFMPEG_DIR}/BUILD_ID FFMPEG_BUILD_ID LIMIT_COUNT 1)
    foreach(lib avformat avcodec avfilter swresample swscale avutil)
        add_library(${lib} SHARED IMPORTED)
        set_target_properties(${lib} PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/arm64-v8a/lib${lib}.so)
        target_link_libraries(beeline_ffmpeg ${lib})
    endforeach()
    target_include_directories(beeline_ffmpeg PRIVATE ${FFMPEG_DIR}/include)
    # The trailing number changes with the JNI encoder settings, so the benchmark cache starts fresh.
    target_compile_definitions(beeline_ffmpeg PRIVATE
        BEELINE_FFMPEG
        BEELINE_FFMPEG_BUILD_ID="${FFMPEG_BUILD_ID}+jni2")
endif()
