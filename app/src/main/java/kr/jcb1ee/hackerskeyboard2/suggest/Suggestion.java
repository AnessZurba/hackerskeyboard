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

/** One candidate word. Higher scores are better; scores only compare within one query. */
public final class Suggestion {
    // AOSP SuggestedWordInfo kinds (low byte) and flags, as the decoder reports them.
    public static final int KIND_MASK = 0xFF;
    public static final int KIND_CORRECTION = 1;
    public static final int KIND_COMPLETION = 2;
    public static final int KIND_PREDICTION = 8;
    public static final int FLAG_POSSIBLY_OFFENSIVE = 0x80000000;
    public static final int FLAG_EXACT_MATCH = 0x40000000;
    public static final int FLAG_EXACT_MATCH_WITH_INTENTIONAL_OMISSION = 0x20000000;
    public static final int FLAG_APPROPRIATE_FOR_AUTO_CORRECTION = 0x10000000;

    public final String word;
    public final int score;
    /** Kind and flags, see the constants. */
    public final int kind;

    public Suggestion(String word, int score, int kind) {
        this.word = word;
        this.score = score;
        this.kind = kind;
    }

    public boolean isExactMatch() {
        return (kind & (FLAG_EXACT_MATCH | FLAG_EXACT_MATCH_WITH_INTENTIONAL_OMISSION)) != 0;
    }

    public boolean isCompletion() {
        return (kind & KIND_MASK) == KIND_COMPLETION;
    }

    public boolean isAppropriateForAutoCorrection() {
        return (kind & FLAG_APPROPRIATE_FOR_AUTO_CORRECTION) != 0;
    }

    Suggestion withWord(String newWord) {
        return new Suggestion(newWord, score, kind);
    }

    @Override
    public String toString() {
        return word + "(" + score + ")";
    }
}
