/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package kr.jcb1ee.hackerskeyboard2.suggest;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Word suggestions for one language: corrections and completions of the word being typed.
 *
 * <p>Implementations are thread safe but may block for a few milliseconds; call them off the
 * touch path where possible. Callers must not ask for suggestions in fields where they never
 * belong (terminals with {@code TYPE_NULL}, passwords, {@code TYPE_TEXT_FLAG_NO_SUGGESTIONS}).
 */
public interface SuggestionEngine {
    /**
     * Ranked candidates for {@code typed}, best first, at most {@code max}.
     *
     * @param typed the word so far, as typed (its case is applied to the results)
     * @param xs touch x per code point of {@code typed}, in {@code keys}' pixels, or null
     *     (then the decoder considers only the letter itself, not its neighbours); entries
     *     may be -1 for unknown
     * @param ys touch y, likewise
     * @param previousWord the word before, null if unknown, "" at the start of a sentence
     * @param keys the keyboard the word was typed on, or null for a plain QWERTY
     */
    List<Suggestion> suggestions(String typed, int[] xs, int[] ys, String previousWord,
            KeyGeometry keys, int max);

    /**
     * Like {@link #suggestions(String, int[], int[], String, KeyGeometry, int)}, with the
     * word before as {@link WordContext} (it steers the learned words).
     */
    default List<Suggestion> suggestionsAfter(String typed, int[] xs, int[] ys,
            WordContext previous, KeyGeometry keys, int max) {
        return suggestions(typed, xs, ys, previous != null ? previous.word : null, keys, max);
    }

    /** Whether {@code word} is in the dictionary (as is, or lower-cased). */
    boolean isValidWord(String word);

    /**
     * How confident an auto-correction from {@code typed} to {@code suggestion} would be (AOSP's
     * normalized score: auto-correct above about 0.185).
     */
    float autoCorrectionConfidence(String typed, Suggestion suggestion);

    /** Frees the dictionary. Later calls return nothing. */
    void close();

    // ------------------------------------------------------------ the user's own words
    // Optional: an engine without them suggests from its dictionary only.

    /** Where a suggested word comes from, for the strip's long press. */
    enum WordSource {
        /** Not a word anywhere (as typed). */
        NONE,
        /** The main dictionary (perhaps also learned). */
        DICTIONARY,
        /** Learned from typing, or saved from the strip, only. */
        LEARNED,
        /** Android's personal dictionary (perhaps also learned). */
        PERSONAL_DICTIONARY
    }

    /**
     * Uses (and from now on learns into) the learned words in {@code dir}, the language's
     * directory in the app's private files. Does IO: call it off the main thread.
     */
    default void attachLearnedWords(File dir) {
    }

    /** The words of Android's personal dictionary for this language, word to probability. */
    default void setPersonalWords(Map<String, Integer> words) {
    }

    /**
     * The words that followed {@code previous} in the user's typing, most likely first
     * (next-word suggestions), at most {@code max}.
     */
    default List<Suggestion> predictions(WordContext previous, int max) {
        return Collections.emptyList();
    }

    /**
     * Learns that {@code word} was typed (committed) after {@code previous}.
     * {@code capitalizedForSentence}: its capital first letter may be the automatic one at
     * the start of a sentence.
     */
    default void learn(WordContext previous, String word, boolean capitalizedForSentence) {
    }

    /** Forgets that {@code word} followed {@code previous} (a reverted auto-correction). */
    default void unlearn(WordContext previous, String word, boolean capitalizedForSentence) {
    }

    /** Saves {@code word} for good (tapped in the strip as typed). */
    default void save(WordContext previous, String word, boolean capitalizedForSentence) {
    }

    /** Forgets a learned or saved word; false if it was neither. */
    default boolean forget(String word) {
        return false;
    }

    default WordSource source(String word) {
        return isValidWord(word) ? WordSource.DICTIONARY : WordSource.NONE;
    }

    /** Writes what was learned to its files. */
    default void flushLearnedWords() {
    }
}
