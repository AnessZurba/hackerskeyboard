/*
 * Copyright (C) 2008 The Android Open Source Project
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
 * java/src/com/android/inputmethod/latin/BinaryDictionary.java at
 * 127336e9f29d69607eab55982324b210279ae8c5. Only the natives registered by our
 * com_android_inputmethod_latin_BinaryDictionary.cpp; one traverse session; no gesture input,
 * personalization, migration or Android dependencies; results as plain Suggestion objects.
 */

package kr.jcb1ee.hackerskeyboard2.suggest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A dictionary in the AOSP binary format (v2 read-only main dictionaries as made by dicttool's
 * makedict, or v4), searched by the native decoder. Not thread safe: callers serialise access.
 */
final class BinaryDictionary {
    // Must be equal to MAX_WORD_LENGTH in native/jni/src/defines.h
    static final int MAX_WORD_LENGTH = 48;
    // Must be equal to MAX_PREV_WORD_COUNT_FOR_N_GRAM in native/jni/src/defines.h
    static final int MAX_PREV_WORD_COUNT_FOR_N_GRAM = 3;
    static final int NOT_A_PROBABILITY = -1;
    static final int NOT_A_COORDINATE = -1;
    static final float NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL = -1.0f;
    private static final int NOT_A_CODE = -1;

    private static native long openNative(String sourceDir, long dictOffset, long dictSize,
            boolean isUpdatable);
    private static native long createOnMemoryNative(long formatVersion,
            String locale, String[] attributeKeyStringArray, String[] attributeValueStringArray);
    private static native void getHeaderInfoNative(long dict, int[] outHeaderSize,
            int[] outFormatVersion, ArrayList<int[]> outAttributeKeys,
            ArrayList<int[]> outAttributeValues);
    private static native boolean flushNative(long dict, String filePath);
    private static native boolean needsToRunGCNative(long dict, boolean mindsBlockByGC);
    private static native boolean flushWithGCNative(long dict, String filePath);
    private static native void closeNative(long dict);
    private static native int getFormatVersionNative(long dict);
    private static native int getProbabilityNative(long dict, int[] word);
    private static native int getMaxProbabilityOfExactMatchesNative(long dict, int[] word);
    private static native int getNgramProbabilityNative(long dict, int[][] prevWordCodePointArrays,
            boolean[] isBeginningOfSentenceArray, int[] word);
    private static native void getSuggestionsNative(long dict, long proximityInfo,
            long traverseSession, int[] xCoordinates, int[] yCoordinates, int[] times,
            int[] pointerIds, int[] inputCodePoints, int inputSize, int[] suggestOptions,
            int[][] prevWordCodePointArrays, boolean[] isBeginningOfSentenceArray,
            int prevWordCount, int[] outputSuggestionCount, int[] outputCodePoints,
            int[] outputScores, int[] outputIndices, int[] outputTypes,
            int[] outputAutoCommitFirstWordConfidence,
            float[] inOutWeightOfLangModelVsSpatialModel);
    private static native boolean addUnigramEntryNative(long dict, int[] word, int probability,
            int[] shortcutTarget, int shortcutProbability, boolean isBeginningOfSentence,
            boolean isNotAWord, boolean isPossiblyOffensive, int timestamp);
    private static native boolean removeUnigramEntryNative(long dict, int[] word);
    private static native boolean addNgramEntryNative(long dict,
            int[][] prevWordCodePointArrays, boolean[] isBeginningOfSentenceArray,
            int[] word, int probability, int timestamp);
    private static native boolean removeNgramEntryNative(long dict,
            int[][] prevWordCodePointArrays, boolean[] isBeginningOfSentenceArray, int[] word);
    private static native boolean updateEntriesForWordWithNgramContextNative(long dict,
            int[][] prevWordCodePointArrays, boolean[] isBeginningOfSentenceArray,
            int[] word, boolean isValidWord, int count, int timestamp);
    private static native String getPropertyNative(long dict, String query);
    private static native boolean isCorruptedNative(long dict);

    private long mNativeDict;
    private final DicTraverseSession mSession;

    /**
     * Opens {@code length} bytes at {@code offset} of {@code path} (a plain file, or an APK
     * with the dictionary stored uncompressed). Check {@link #isValid()} afterwards.
     */
    BinaryDictionary(String path, long offset, long length, String locale) {
        mNativeDict = openNative(path, offset, length, false /* isUpdatable */);
        mSession = mNativeDict != 0 ? new DicTraverseSession(locale, mNativeDict, length) : null;
    }

    boolean isValid() {
        return mNativeDict != 0;
    }

    int getFormatVersion() {
        return getFormatVersionNative(mNativeDict);
    }

    /** The header attributes (dictionary, locale, version, description, date...). */
    Map<String, String> getHeaderAttributes() {
        Map<String, String> attributes = new HashMap<>();
        if (!isValid()) return attributes;
        int[] headerSize = new int[1], formatVersion = new int[1];
        ArrayList<int[]> keys = new ArrayList<>(), values = new ArrayList<>();
        getHeaderInfoNative(mNativeDict, headerSize, formatVersion, keys, values);
        for (int i = 0; i < keys.size(); i++) {
            attributes.put(fromNullTerminated(keys.get(i)), fromNullTerminated(values.get(i)));
        }
        return attributes;
    }

    /** The word's unigram probability (0-255 scale), or NOT_A_PROBABILITY if absent. */
    int getProbability(String word) {
        if (!isValid() || word.isEmpty()) return NOT_A_PROBABILITY;
        return getProbabilityNative(mNativeDict, word.codePoints().toArray());
    }

    /** Like getProbability, but also matches words differing only in case and accents. */
    int getMaxProbabilityOfExactMatches(String word) {
        if (!isValid() || word.isEmpty()) return NOT_A_PROBABILITY;
        return getMaxProbabilityOfExactMatchesNative(mNativeDict, word.codePoints().toArray());
    }

    /**
     * Runs the decoder. {@code codePoints} is what was typed; {@code xs}/{@code ys} the touch
     * points (NOT_A_COORDINATE where unknown: the decoder then uses the key's centre).
     * {@code previousWord} is null when unknown, "" at the start of a sentence.
     */
    List<Suggestion> getSuggestions(int[] codePoints, int[] xs, int[] ys,
            ProximityInfo proximityInfo, String previousWord, boolean blockOffensive) {
        List<Suggestion> out = new ArrayList<>();
        if (!isValid() || proximityInfo == null || proximityInfo.getNativeProximityInfo() == 0) {
            return out;
        }
        DicTraverseSession s = mSession;
        int inputSize = Math.min(codePoints.length, MAX_WORD_LENGTH - 1);
        Arrays.fill(s.mInputCodePoints, NOT_A_CODE);
        System.arraycopy(codePoints, 0, s.mInputCodePoints, 0, inputSize);
        int[] x = new int[inputSize], y = new int[inputSize];
        for (int i = 0; i < inputSize; i++) {
            x[i] = xs != null && i < xs.length ? xs[i] : NOT_A_COORDINATE;
            y[i] = ys != null && i < ys.length ? ys[i] : NOT_A_COORDINATE;
        }
        int[] times = new int[inputSize], pointerIds = new int[inputSize];
        int prevWordCount = 0;
        Arrays.fill(s.mPrevWordCodePointArrays, null);
        Arrays.fill(s.mIsBeginningOfSentenceArray, false);
        if (previousWord != null) {
            // An empty previous word is AOSP's NgramContext.BEGINNING_OF_SENTENCE.
            s.mPrevWordCodePointArrays[0] = previousWord.codePoints().toArray();
            s.mIsBeginningOfSentenceArray[0] = previousWord.isEmpty();
            prevWordCount = 1;
        }
        s.setUseFullEditDistance(false);
        s.setBlockOffensiveWords(blockOffensive);
        s.mInputOutputWeightOfLangModelVsSpatialModel[0] =
                NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL;
        getSuggestionsNative(mNativeDict, proximityInfo.getNativeProximityInfo(),
                s.getSession(), x, y, times, pointerIds, s.mInputCodePoints, inputSize,
                s.mOptions, s.mPrevWordCodePointArrays, s.mIsBeginningOfSentenceArray,
                prevWordCount, s.mOutputSuggestionCount, s.mOutputCodePoints, s.mOutputScores,
                s.mSpaceIndices, s.mOutputTypes, s.mOutputAutoCommitFirstWordConfidence,
                s.mInputOutputWeightOfLangModelVsSpatialModel);
        int count = s.mOutputSuggestionCount[0];
        for (int j = 0; j < count; ++j) {
            int start = j * MAX_WORD_LENGTH;
            int len = 0;
            while (len < MAX_WORD_LENGTH && s.mOutputCodePoints[start + len] != 0) ++len;
            if (len > 0) {
                out.add(new Suggestion(new String(s.mOutputCodePoints, start, len),
                        s.mOutputScores[j], s.mOutputTypes[j]));
            }
        }
        return out;
    }

    void close() {
        if (mSession != null) mSession.close();
        if (mNativeDict != 0) {
            closeNative(mNativeDict);
            mNativeDict = 0;
        }
    }

    private static String fromNullTerminated(int[] codePoints) {
        int len = 0;
        while (len < codePoints.length && codePoints[len] != 0) len++;
        return new String(codePoints, 0, len);
    }
}
