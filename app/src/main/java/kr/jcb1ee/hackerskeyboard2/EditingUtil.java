/*
 * Copyright (C) 2009 Google Inc.
 * 
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

package kr.jcb1ee.hackerskeyboard2;

import android.text.TextUtils;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import java.util.regex.Pattern;

/**
 * Utility methods to deal with editing text through an InputConnection.
 */
public class EditingUtil {
    /**
     * Number of characters we want to look back in order to identify the previous word
     */
    private static final int LOOKBACK_CHARACTER_NUM = 15;


    private EditingUtil() {};

    /**
     * Append newText to the text field represented by connection.
     * The new text becomes selected.
     */
    public static void appendText(InputConnection connection, String newText) {
        if (connection == null) {
            return;
        }

        // Commit the composing text
        connection.finishComposingText();

        // Add a space if the field already has text.
        CharSequence charBeforeCursor = connection.getTextBeforeCursor(1, 0);
        if (charBeforeCursor != null
                && !charBeforeCursor.equals(" ")
                && (charBeforeCursor.length() > 0)) {
            newText = " " + newText;
        }

        connection.setComposingText(newText, 1);
    }

    private static int getCursorPosition(InputConnection connection) {
        ExtractedText extracted = connection.getExtractedText(
            new ExtractedTextRequest(), 0);
        if (extracted == null) {
          return -1;
        }
        return extracted.startOffset + extracted.selectionStart;
    }

    /**
     * @param connection connection to the current text field.
     * @param separators characters which may separate words
     * @param range the range object to store the result into
     * @return the word that surrounds the cursor, including up to one trailing
     *   separator. For example, if the field contains "he|llo world", where |
     *   represents the cursor, then "hello " will be returned.
     */
    public static String getWordAtCursor(
            InputConnection connection, String separators, Range range) {
        Range r = getWordRangeAtCursor(connection, separators, range);
        return (r == null) ? null : r.word;
    }

    /**
     * Removes the word surrounding the cursor. Parameters are identical to
     * getWordAtCursor.
     */
    public static void deleteWordAtCursor(
        InputConnection connection, String separators) {

        Range range = getWordRangeAtCursor(connection, separators, null);
        if (range == null) return;

        connection.finishComposingText();
        // Move cursor to beginning of word, to avoid crash when cursor is outside
        // of valid range after deleting text.
        int newCursor = getCursorPosition(connection) - range.charsBefore;
        connection.setSelection(newCursor, newCursor);
        connection.deleteSurroundingText(0, range.charsBefore + range.charsAfter);
    }

    /**
     * Represents a range of text, relative to the current cursor position.
     */
    public static class Range {
        /** Characters before selection start */
        public int charsBefore;

        /**
         * Characters after selection start, including one trailing word
         * separator.
         */
        public int charsAfter;

        /** The actual characters that make up a word */
        public String word;

        public Range() {}

        public Range(int charsBefore, int charsAfter, String word) {
            if (charsBefore < 0 || charsAfter < 0) {
                throw new IndexOutOfBoundsException();
            }
            this.charsBefore = charsBefore;
            this.charsAfter = charsAfter;
            this.word = word;
        }
    }

    /**
     * The text before the selection (or cursor) and after it: one round trip to the app on
     * Android 12+ (getSurroundingText) instead of two, and a consistent snapshot.
     */
    static CharSequence[] textAround(InputConnection ic, int before, int after) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                android.view.inputmethod.SurroundingText st = ic.getSurroundingText(before, after, 0);
                if (st != null) {
                    CharSequence text = st.getText();
                    int s = Math.min(st.getSelectionStart(), st.getSelectionEnd());
                    int e = Math.max(st.getSelectionStart(), st.getSelectionEnd());
                    if (text != null && s >= 0 && e <= text.length()) {
                        return new CharSequence[] {text.subSequence(0, s), text.subSequence(e, text.length())};
                    }
                }
            } catch (RuntimeException ignored) {
                // An editor that doesn't implement it well: ask twice instead.
            }
        }
        return new CharSequence[] {ic.getTextBeforeCursor(before, 0), ic.getTextAfterCursor(after, 0)};
    }

    private static Range getWordRangeAtCursor(
            InputConnection connection, String sep, Range range) {
        if (connection == null || sep == null) {
            return null;
        }
        CharSequence[] around = textAround(connection, 1000, 1000);
        CharSequence before = around[0];
        CharSequence after = around[1];
        if (before == null || after == null) {
            return null;
        }

        // Find first word separator before the cursor
        int start = before.length();
        while (start > 0 && !isWhitespace(before.charAt(start - 1), sep)) start--;

        // Find last word separator after the cursor
        int end = -1;
        while (++end < after.length() && !isWhitespace(after.charAt(end), sep));

        int cursor = getCursorPosition(connection);
        if (start >= 0 && cursor + end <= after.length() + before.length()) {
            String word = before.toString().substring(start, before.length())
                    + after.toString().substring(0, end);

            Range returnRange = range != null? range : new Range();
            returnRange.charsBefore = before.length() - start;
            returnRange.charsAfter = end;
            returnRange.word = word;
            return returnRange;
        }

        return null;
    }

    private static boolean isWhitespace(int code, String whitespace) {
        return whitespace.contains(String.valueOf((char) code));
    }

    private static final Pattern spaceRegex = Pattern.compile("\\s+");

    public static CharSequence getPreviousWord(InputConnection connection,
            String sentenceSeperators) {
        //TODO: Should fix this. This could be slow!
        CharSequence prev = connection.getTextBeforeCursor(LOOKBACK_CHARACTER_NUM, 0);
        if (prev == null) {
            return null;
        }
        String[] w = spaceRegex.split(prev);
        if (w.length >= 2 && w[w.length-2].length() > 0) {
            char lastChar = w[w.length-2].charAt(w[w.length-2].length() -1);
            if (sentenceSeperators.contains(String.valueOf(lastChar))) {
                return null;
            }
            return w[w.length-2];
        } else {
            return null;
        }
    }

    public static class SelectedWord {
        public int start;
        public int end;
        public CharSequence word;
    }

    /**
     * Takes a character sequence with a single character and checks if the character occurs
     * in a list of word separators or is empty.
     * @param singleChar A CharSequence with null, zero or one character
     * @param wordSeparators A String containing the word separators
     * @return true if the character is at a word boundary, false otherwise
     */
    private static boolean isWordBoundary(CharSequence singleChar, String wordSeparators) {
        return TextUtils.isEmpty(singleChar) || wordSeparators.contains(singleChar);
    }

    /**
     * Checks if the cursor is inside a word or the current selection is a whole word.
     * @param ic the InputConnection for accessing the text field
     * @param selStart the start position of the selection within the text field
     * @param selEnd the end position of the selection within the text field. This could be
     *               the same as selStart, if there's no selection.
     * @param wordSeparators the word separator characters for the current language
     * @return an object containing the text and coordinates of the selected/touching word,
     *         null if the selection/cursor is not marking a whole word.
     */
    public static SelectedWord getWordAtCursorOrSelection(final InputConnection ic,
            int selStart, int selEnd, String wordSeparators) {
        if (selStart == selEnd) {
            // There is just a cursor, so get the word at the cursor
            EditingUtil.Range range = new EditingUtil.Range();
            CharSequence touching = getWordAtCursor(ic, wordSeparators, range);
            if (!TextUtils.isEmpty(touching)) {
                SelectedWord selWord = new SelectedWord();
                selWord.word = touching;
                selWord.start = selStart - range.charsBefore;
                selWord.end = selEnd + range.charsAfter;
                return selWord;
            }
        } else {
            // Is the previous character empty or a word separator? If not, return null.
            CharSequence[] around = textAround(ic, 1, 1);
            CharSequence charsBefore = around[0];
            if (!isWordBoundary(charsBefore, wordSeparators)) {
                return null;
            }

            // Is the next character empty or a word separator? If not, return null.
            CharSequence charsAfter = around[1];
            if (!isWordBoundary(charsAfter, wordSeparators)) {
                return null;
            }

            // Extract the selection alone
            CharSequence touching = getSelectedText(ic, selStart, selEnd);
            if (TextUtils.isEmpty(touching)) return null;
            // Is any part of the selection a separator? If so, return null.
            final int length = touching.length();
            for (int i = 0; i < length; i++) {
                if (wordSeparators.contains(touching.subSequence(i, i + 1))) {
                    return null;
                }
            }
            // Prepare the selected word
            SelectedWord selWord = new SelectedWord();
            selWord.start = selStart;
            selWord.end = selEnd;
            selWord.word = touching;
            return selWord;
        }
        return null;
    }

    /** Returns the selected text between the selStart and selEnd positions. */
    private static CharSequence getSelectedText(InputConnection ic, int selStart, int selEnd) {
        return ic.getSelectedText(0);
    }

    /** Marks the word as composing text, which underlines it. */
    public static void underlineWord(InputConnection ic, SelectedWord word) {
        ic.setComposingRegion(word.start, word.end);
    }
}
