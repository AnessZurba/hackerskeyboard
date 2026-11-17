# The app's native code: libjni_latinime, the AOSP LatinIME decoder behind word suggestions
# (app/src/main/cpp/aosp/README.md). Its own CMakeLists.txt also builds the desktop library
# the JVM unit tests use.

cmake_minimum_required(VERSION 3.22.1)

add_subdirectory(src/main/cpp/aosp)
