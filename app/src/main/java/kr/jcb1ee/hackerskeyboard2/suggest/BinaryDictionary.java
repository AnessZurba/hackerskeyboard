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
 * personalization input events, migration or Android dependencies; results as plain
 * Suggestion objects. Updatable (v4) dictionaries as AOSP's ExpandableBinaryDictionary uses
 * them: on memory, or a directory reopened after each flush.
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

    /** AOSP's FormatSpec.VERSION403: the updatable (v4) format. */
    static final int FORMAT_VERSION_4 = 403;
    /** AOSP's BinaryDictionary.NOT_A_VALID_TIMESTAMP. */
    static final int NOT_A_VALID_TIMESTAMP = -1;
    // What the traverse session sizes its caches by, for the small updatable dictionaries.
    private static final long DYNAMIC_DICT_SIZE = 64 * 1024;

    private long mNativeDict;
    private DicTraverseSession mSession;
    private final String mLocale;
    /** An updatable dictionary's directory, or null (read-only, or on memory). */
    private final String mDirectory;

    /**
     * Opens {@code length} bytes at {@code offset} of {@code path} (a plain file, or an APK
     * with the dictionary stored uncompressed). Check {@link #isValid()} afterwards.
     */
    BinaryDictionary(String path, long offset, long length, String locale) {
        mLocale = locale;
        mDirectory = null;
        mNativeDict = openNative(path, offset, length, false /* isUpdatable */);
        mSession = mNativeDict != 0 ? new DicTraverseSession(locale, mNativeDict, length) : null;
    }

    private BinaryDictionary(long nativeDict, String locale, String directory) {
        mLocale = locale;
        mDirectory = directory;
        mNativeDict = nativeDict;
        mSession = nativeDict != 0
                ? new DicTraverseSession(locale, nativeDict, DYNAMIC_DICT_SIZE) : null;
    }

    /**
     * A new empty updatable dictionary held in memory only (nothing is written anywhere).
     * Check {@link #isValid()}.
     */
    static BinaryDictionary createInMemory(String locale, Map<String, String> attributes) {
        String[] keys = attributes.keySet().toArray(new String[0]);
        String[] values = new String[keys.length];
        for (int i = 0; i < keys.length; i++) values[i] = attributes.get(keys[i]);
        long dict = createOnMemoryNative(FORMAT_VERSION_4, locale, keys, values);
        return new BinaryDictionary(dict, locale, null);
    }

    /**
     * Opens the updatable dictionary in {@code directory}, creating it empty with
     * {@code attributes} when there is none (or it can't be read). Check {@link #isValid()}.
     */
    static BinaryDictionary openOrCreateUpdatable(java.io.File directory, String locale,
            Map<String, String> attributes) {
        String path = directory.getAbsolutePath();
        long dict = directory.isDirectory() ? openNative(path, 0, 0, true /* isUpdatable */) : 0;
        if (dict != 0 && isCorruptedNative(dict)) {
            closeNative(dict);
            dict = 0;
        }
        if (dict == 0) {
            deleteRecursively(directory);
            java.io.File parent = directory.getParentFile();
            if (parent != null) parent.mkdirs();
            String[] keys = attributes.keySet().toArray(new String[0]);
            String[] values = new String[keys.length];
            for (int i = 0; i < keys.length; i++) values[i] = attributes.get(keys[i]);
            if (BinaryDictionaryUtils.createEmptyDictFileNative(path, FORMAT_VERSION_4, locale,
                    keys, values)) {
                dict = openNative(path, 0, 0, true /* isUpdatable */);
            }
        }
        return new BinaryDictionary(dict, locale, path);
    }

    static void deleteRecursively(java.io.File f) {
        java.io.File[] children = f.listFiles();
        if (children != null) {
            for (java.io.File c : children) deleteRecursively(c);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
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
     * Adds (or updates) a word with {@code probability} (0-255), for dictionaries built from
     * a word list (the personal dictionary): not decaying, no history.
     */
    boolean addUnigram(String word, int probability) {
        if (!isValid() || word.isEmpty()) return false;
        return addUnigramEntryNative(mNativeDict, word.codePoints().toArray(), probability,
                null /* shortcutTarget */, NOT_A_PROBABILITY, false /* isBeginningOfSentence */,
                false /* isNotAWord */, false /* isPossiblyOffensive */, NOT_A_VALID_TIMESTAMP);
    }

    boolean removeUnigram(String word) {
        if (!isValid() || word.isEmpty()) return false;
        return removeUnigramEntryNative(mNativeDict, word.codePoints().toArray());
    }

    /**
     * Records that {@code word} was typed after {@code previousWord} (null: unknown, "": the
     * start of a sentence), as AOSP's user history does: the word's and the pair's counts go
     * up and decay with time. A word that is not {@code isValid} (not in another dictionary)
     * becomes a suggestion only once it has been typed twice.
     */
    boolean updateEntriesForWord(String previousWord, String word, boolean isValid, int count,
            int timestamp) {
        if (!isValid() || word.isEmpty()) return false;
        return updateEntriesForWordWithNgramContextNative(mNativeDict, context(previousWord),
                beginningOfSentence(previousWord), word.codePoints().toArray(), isValid, count,
                timestamp);
    }

    /** Forgets that {@code word} followed {@code previousWord}. */
    boolean removeNgram(String previousWord, String word) {
        if (!isValid() || word.isEmpty() || previousWord == null) return false;
        return removeNgramEntryNative(mNativeDict, context(previousWord),
                beginningOfSentence(previousWord), word.codePoints().toArray());
    }

    /** The probability of {@code word} after {@code previousWord}, or NOT_A_PROBABILITY. */
    int getNgramProbability(String previousWord, String word) {
        if (!isValid() || word.isEmpty()) return NOT_A_PROBABILITY;
        return getNgramProbabilityNative(mNativeDict, context(previousWord),
                beginningOfSentence(previousWord), word.codePoints().toArray());
    }

    private static int[][] context(String previousWord) {
        if (previousWord == null) return new int[0][];
        return new int[][] {previousWord.codePoints().toArray()};
    }

    private static boolean[] beginningOfSentence(String previousWord) {
        if (previousWord == null) return new boolean[0];
        return new boolean[] {previousWord.isEmpty()};
    }

    /**
     * Writes an updatable dictionary to its directory (running AOSP's garbage collection,
     * which also applies the decay, when the dictionary asks for it) and reopens it, as
     * AOSP does. False for one held in memory or when writing failed.
     */
    boolean flush() {
        if (!isValid() || mDirectory == null) return false;
        boolean ok = needsToRunGCNative(mNativeDict, false /* mindsBlockByGC, as AOSP */)
                ? flushWithGCNative(mNativeDict, mDirectory)
                : flushNative(mNativeDict, mDirectory);
        reopen();
        return ok;
    }

    private void reopen() {
        close();
        mNativeDict = openNative(mDirectory, 0, 0, true /* isUpdatable */);
        mSession = mNativeDict != 0
                ? new DicTraverseSession(mLocale, mNativeDict, DYNAMIC_DICT_SIZE) : null;
    }

    /**
     * Runs the decoder. {@code codePoints} is what was typed; {@code xs}/{@code ys} the touch
     * points (NOT_A_COORDINATE where unknown: the decoder then matches only that letter, not its
     * neighbours).
     * {@code previousWord} is null when unknown, "" at the start of a sentence.
     */
    List<Suggestion> getSuggestions(int[] codePoints, int[] xs, int[] ys,
            ProximityInfo proximityInfo, String previousWord, boolean blockOffensive) {
        List<Suggestion> out = new ArrayList<>();
        if (!isValid() || proximityInfo == null || proximityInfo.getNativeProximityInfo() == 0) {
            return out;
        }
        DicTraverseSession s = mSession;
        // No input with a previous word: the words that followed it (AOSP's predictions).
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
        mSession = null;
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
