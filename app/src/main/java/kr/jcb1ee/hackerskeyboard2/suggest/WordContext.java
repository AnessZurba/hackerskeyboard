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

/**
 * The word before the one being typed (AOSP's NgramContext, one word deep): what learning
 * pairs words with and what next-word suggestions follow. Immutable.
 */
public final class WordContext {
    /** Nothing usable before (after a comma, a number, a symbol; or unknown). */
    public static final WordContext NONE = new WordContext(null, false);
    /** At the start of the text or of a sentence. */
    public static final WordContext SENTENCE_START = new WordContext("", false);

    /** The previous word; null for {@link #NONE}, "" for {@link #SENTENCE_START}. */
    public final String word;
    /**
     * Whether {@link #word} began a sentence, so a capital first letter may only be the
     * keyboard's automatic one (it is then learned and looked up in lower case, as AOSP does,
     * unless only the capitalised form is a word).
     */
    public final boolean startedSentence;

    private WordContext(String word, boolean startedSentence) {
        this.word = word;
        this.startedSentence = startedSentence;
    }

    /** The context {@code word} makes for the word after it. */
    public static WordContext of(String word, boolean startedSentence) {
        if (word == null) return NONE;
        if (word.isEmpty()) return SENTENCE_START;
        return new WordContext(word, startedSentence);
    }

    /** Whether there is a previous word (not NONE, not the start of a sentence). */
    public boolean hasWord() {
        return word != null && !word.isEmpty();
    }

    /**
     * The context of a word typed at the end of {@code before} (the text before it, as much
     * as the editor gave; a word is at most {@link BinaryDictionary#MAX_WORD_LENGTH} long):
     * the last word if only spaces follow it, the start of a sentence after . ! ? (and their
     * Arabic and Urdu forms), a line break or nothing; otherwise {@link #NONE}.
     */
    public static WordContext fromTextBefore(CharSequence before) {
        if (before == null) return NONE;
        int end = before.length();
        while (end > 0 && isSpace(before.charAt(end - 1))) end--;
        if (end == 0) return SENTENCE_START;
        char last = before.charAt(end - 1);
        if (isSentenceEnd(last) || isLineBreak(last)) return SENTENCE_START;
        int start = end;
        while (start > 0 && isWordChar(before, start - 1)) start--;
        if (start == end) return NONE; // a comma, a symbol, a quote...
        // Apostrophes and hyphens at the ends are punctuation, not the word.
        while (start < end && !Character.isLetterOrDigit(before.charAt(start))) start++;
        int wordEnd = end;
        while (wordEnd > start && isEdgePunctuation(before.charAt(wordEnd - 1))) wordEnd--;
        String word = before.subSequence(start, wordEnd).toString();
        if (word.isEmpty() || word.length() > BinaryDictionary.MAX_WORD_LENGTH
                || !hasLetter(word)) {
            return NONE;
        }
        if (start == 0 && before.length() >= BinaryDictionary.MAX_WORD_LENGTH) {
            return NONE; // may be the end of a longer token
        }
        int i = start;
        while (i > 0 && isSpace(before.charAt(i - 1))) i--;
        boolean startedSentence = i == 0 || isSentenceEnd(before.charAt(i - 1))
                || isLineBreak(before.charAt(i - 1));
        return new WordContext(word, startedSentence);
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\u00A0' || c == '\u202F';
    }

    private static boolean isLineBreak(char c) {
        return c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029';
    }

    private static boolean isSentenceEnd(char c) {
        switch (c) {
            case '.': case '!': case '?': case '\u2026': // ellipsis
            case '\u061F': // Arabic question mark
            case '\u06D4': // Arabic full stop (Urdu)
            case '\u3002': // ideographic full stop
                return true;
            default:
                return false;
        }
    }

    private static boolean isWordChar(CharSequence s, int i) {
        char c = s.charAt(i);
        if (Character.isLetterOrDigit(c)) return true;
        int type = Character.getType(c);
        // Niqqud, harakat and other combining marks belong to the letter before them.
        if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK) {
            return true;
        }
        // Within a word: don't, well-known, Hebrew geresh and gershayim.
        return (c == '\'' || c == '\u2019' || c == '-' || c == '\u05F3' || c == '\u05F4')
                && i > 0 && Character.isLetterOrDigit(s.charAt(i - 1));
    }

    private static boolean isEdgePunctuation(char c) {
        return c == '\'' || c == '\u2019' || c == '-';
    }

    private static boolean hasLetter(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (Character.isLetter(word.charAt(i))) return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WordContext)) return false;
        WordContext c = (WordContext) o;
        return java.util.Objects.equals(word, c.word) && startedSentence == c.startedSentence;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(word, startedSentence);
    }

    @Override
    public String toString() {
        if (word == null) return "NONE";
        if (word.isEmpty()) return "SENTENCE_START";
        return word + (startedSentence ? " (sentence start)" : "");
    }
}
