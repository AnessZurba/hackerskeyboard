# AOSP LatinIME native suggestion engine (vendored)

This directory is a copy of the native decoder of the Android Open Source
Project's LatinIME keyboard:

- Source: https://android.googlesource.com/platform/packages/inputmethods/LatinIME
- Commit: `127336e9f29d69607eab55982324b210279ae8c5` (main, 2025-02-26)
- Path upstream: `native/jni/` (`src/` and the four JNI glue files plus `jni_common.*`)
- Licence: Apache License 2.0 (see `NOTICE`, copied from the upstream repository,
  and the header of every file). Nothing here comes from HeliBoard, OpenBoard,
  FUTO or any other fork.

`src/` is unmodified. Files changed for this app carry a "Modified for Hacker's
Keyboard 2" note at the top and are listed here:

- `jni_common.h`: the Java class names the natives are registered against
  (`kr/jcb1ee/hackerskeyboard2/suggest/...` instead of `com/android/inputmethod/...`).
- `com_android_inputmethod_*.cpp`: use those class names; the BinaryDictionary
  method table only registers the methods our Java side declares (no
  personalization input events, word properties, migration).

The Java side lives in `app/src/main/java/kr/jcb1ee/hackerskeyboard2/suggest/`
(classes derived from AOSP carry the AOSP header and a modification note).

Build: `CMakeLists.txt` here builds `libjni_latinime`. The app's
`app/CMakeLists.txt` adds it for the APK; the unit tests build a host (desktop)
copy with `-DHOST_TOOL` (see the `hostSuggestJni` task in `app/build.gradle`).

To update: copy `native/jni/src` and the glue files from a newer upstream
commit, re-apply the notes above (the diffs are small), update the commit here.
