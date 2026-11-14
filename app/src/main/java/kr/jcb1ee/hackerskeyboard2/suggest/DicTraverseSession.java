/*
 * Copyright (C) 2012 The Android Open Source Project
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
 * java/src/com/android/inputmethod/latin/DicTraverseSession.java at
 * 127336e9f29d69607eab55982324b210279ae8c5; moved package, explicit close instead of
 * finalize(), suggest options inlined.
 */

package kr.jcb1ee.hackerskeyboard2.suggest;

/** Native per-dictionary search state, plus the arrays getSuggestions fills. Not thread safe. */
final class DicTraverseSession {
    // Must be equal to MAX_RESULTS in native/jni/src/defines.h
    static final int MAX_RESULTS = 18;

    // Must match suggest_options.h.
    private static final int OPTION_IS_GESTURE = 0;
    private static final int OPTION_USE_FULL_EDIT_DISTANCE = 1;
    private static final int OPTION_BLOCK_OFFENSIVE_WORDS = 2;
    private static final int OPTION_WEIGHT_FOR_LOCALE_IN_THOUSANDS = 4;
    private static final int OPTIONS_SIZE = 5;

    final int[] mInputCodePoints = new int[BinaryDictionary.MAX_WORD_LENGTH];
    final int[][] mPrevWordCodePointArrays = new int[BinaryDictionary.MAX_PREV_WORD_COUNT_FOR_N_GRAM][];
    final boolean[] mIsBeginningOfSentenceArray =
            new boolean[BinaryDictionary.MAX_PREV_WORD_COUNT_FOR_N_GRAM];
    final int[] mOutputSuggestionCount = new int[1];
    final int[] mOutputCodePoints = new int[BinaryDictionary.MAX_WORD_LENGTH * MAX_RESULTS];
    final int[] mSpaceIndices = new int[MAX_RESULTS];
    final int[] mOutputScores = new int[MAX_RESULTS];
    final int[] mOutputTypes = new int[MAX_RESULTS];
    // Only one result is ever used
    final int[] mOutputAutoCommitFirstWordConfidence = new int[1];
    final float[] mInputOutputWeightOfLangModelVsSpatialModel = new float[1];
    final int[] mOptions = new int[OPTIONS_SIZE];

    private static native long setDicTraverseSessionNative(String locale, long dictSize);
    private static native void initDicTraverseSessionNative(long nativeDicTraverseSession,
            long dictionary, int[] previousWord, int previousWordLength);
    private static native void releaseDicTraverseSessionNative(long nativeDicTraverseSession);

    private long mNativeDicTraverseSession;

    DicTraverseSession(String locale, long dictionary, long dictSize) {
        mNativeDicTraverseSession = setDicTraverseSessionNative(locale != null ? locale : "",
                dictSize);
        initDicTraverseSessionNative(mNativeDicTraverseSession, dictionary, null, 0);
        mOptions[OPTION_IS_GESTURE] = 0; // no gesture typing in this keyboard
        mOptions[OPTION_WEIGHT_FOR_LOCALE_IN_THOUSANDS] = 1000;
    }

    long getSession() {
        return mNativeDicTraverseSession;
    }

    void setUseFullEditDistance(boolean value) {
        mOptions[OPTION_USE_FULL_EDIT_DISTANCE] = value ? 1 : 0;
    }

    void setBlockOffensiveWords(boolean value) {
        mOptions[OPTION_BLOCK_OFFENSIVE_WORDS] = value ? 1 : 0;
    }

    void close() {
        if (mNativeDicTraverseSession != 0) {
            releaseDicTraverseSessionNative(mNativeDicTraverseSession);
            mNativeDicTraverseSession = 0;
        }
    }
}
