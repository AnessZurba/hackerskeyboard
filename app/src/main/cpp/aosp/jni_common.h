/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modified for Hacker's Keyboard 2: adds HK_JNI_CLASS() for the Java package the natives are registered in.
 */

#ifndef LATINIME_JNI_COMMON_H
#define LATINIME_JNI_COMMON_H

#include "jni.h"

// The Java classes live in kr.jcb1ee.hackerskeyboard2.suggest instead of
// com.android.inputmethod.latin / com.android.inputmethod.keyboard.
#define HK_JNI_CLASS(name) "kr/jcb1ee/hackerskeyboard2/suggest/" name

namespace latinime {
int registerNativeMethods(JNIEnv *env, const char *const className, const JNINativeMethod *methods,
        const int numMethods);
} // namespace latinime
#endif // LATINIME_JNI_COMMON_H
