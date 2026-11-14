/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modified for Hacker's Keyboard 2: from AOSP LatinIME
 * java/src/com/android/inputmethod/latin/utils/BinaryDictionaryUtils.java at
 * 127336e9f29d69607eab55982324b210279ae8c5; only the native entry points are kept.
 */

package kr.jcb1ee.hackerskeyboard2.suggest;

/** Helpers implemented in libjni_latinime. */
final class BinaryDictionaryUtils {
    private BinaryDictionaryUtils() {}

    static native boolean createEmptyDictFileNative(String filePath, long dictVersion,
            String locale, String[] attributeKeyStringArray, String[] attributeValueStringArray);
    private static native float calcNormalizedScoreNative(int[] before, int[] after, int score);
    static native int setCurrentTimeForTestNative(int currentTime);

    /**
     * How sure the decoder is that {@code after} is what was meant by {@code before}, given the
     * suggestion's score: AOSP auto-corrects when this exceeds a threshold (0.185 "modest",
     * 0.067 "aggressive").
     */
    static float calcNormalizedScore(String before, String after, int score) {
        int[] b = before.codePoints().toArray();
        int[] a = after.codePoints().toArray();
        return calcNormalizedScoreNative(b, a, score);
    }
}
