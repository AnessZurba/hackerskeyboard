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

import java.util.List;

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
     *     (then each key's centre is assumed); entries may be -1 for unknown
     * @param ys touch y, likewise
     * @param previousWord the word before, null if unknown, "" at the start of a sentence
     * @param keys the keyboard the word was typed on, or null for a plain QWERTY
     */
    List<Suggestion> suggestions(String typed, int[] xs, int[] ys, String previousWord,
            KeyGeometry keys, int max);

    /** Whether {@code word} is in the dictionary (as is, or lower-cased). */
    boolean isValidWord(String word);

    /**
     * How confident an auto-correction from {@code typed} to {@code suggestion} would be (AOSP's
     * normalized score: auto-correct above about 0.185).
     */
    float autoCorrectionConfidence(String typed, Suggestion suggestion);

    /** Frees the dictionary. Later calls return nothing. */
    void close();
}
