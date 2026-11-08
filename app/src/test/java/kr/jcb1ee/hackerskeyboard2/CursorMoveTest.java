package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** Cursor movement for the space-bar slider and the toolbar arrows. */
public class CursorMoveTest {
    private static int[] move(String text, int anchor, int end, int steps, boolean select, boolean word) {
        return LatinIME.newSelection(text, anchor, end, steps, select, word);
    }

    @Test
    public void stopsAtTheEndsOfTheText() {
        assertArrayEquals(new int[] {0, 0}, move("hello", 2, 2, -10, false, false));
        assertArrayEquals(new int[] {5, 5}, move("hello", 2, 2, 10, false, false));
        assertArrayEquals(new int[] {0, 0}, move("hello", 0, 0, -1, false, false));
        assertArrayEquals(new int[] {5, 5}, move("hello", 5, 5, 1, false, false));
        assertArrayEquals(new int[] {0, 0}, move("", 0, 0, 3, false, false));
    }

    @Test
    public void movesByWholeCharacters() {
        String emoji = "a😀b"; // a 😀 b: the emoji is two chars
        assertArrayEquals(new int[] {3, 3}, move(emoji, 1, 1, 1, false, false));
        String marked = "بَب"; // beh + fatha + beh: the mark belongs to the first letter
        assertArrayEquals(new int[] {0, 0}, move(marked, 2, 2, 1, false, false)); // RTL: right = back
    }

    @Test
    public void rightToLeftFollowsTheTextVisually() {
        String ar = "مرحبا";
        // In Arabic, dragging left moves forward in the text.
        assertArrayEquals(new int[] {3, 3}, move(ar, 2, 2, -1, false, false));
        assertArrayEquals(new int[] {1, 1}, move(ar, 2, 2, 1, false, false));
    }

    @Test
    public void selectsAndJumpsByWord() {
        assertArrayEquals(new int[] {2, 4}, move("hello", 2, 2, 2, true, false));
        String s = "one two three";
        assertArrayEquals(new int[] {7, 7}, move(s, 4, 4, 1, false, true));   // end of "two"
        assertArrayEquals(new int[] {4, 4}, move(s, 7, 7, -1, false, true));  // start of "two"
        assertArrayEquals(new int[] {0, 0}, move(s, 4, 4, -5, false, true));
        // Moving with a selection first collapses it to the side moved towards.
        assertArrayEquals(new int[] {1, 1}, move("hello", 1, 4, -1, false, false));
        assertArrayEquals(new int[] {4, 4}, move("hello", 1, 4, 1, false, false));
    }

    @Test
    public void wordAndLineAtCursor() {
        assertArrayEquals(new int[] {4, 7}, LatinIME.wordAt("one two three", 5));
        assertArrayEquals(new int[] {4, 7}, LatinIME.wordAt("one two three", 7)); // at its end
        assertArrayEquals(new int[] {0, 5}, LatinIME.wordAt("مرحبا بك", 2));
        assertArrayEquals(new int[] {4, 9}, LatinIME.lineAt("abc\ndefgh\nij", 6));
        assertArrayEquals(new int[] {0, 3}, LatinIME.lineAt("abc\ndef", 0));
        assertArrayEquals(new int[] {4, 7}, LatinIME.lineAt("abc\ndef", 7));
    }

    @Test
    public void movesEitherEndOfASelection() {
        String s = "hello world";
        // Selection 2..8 with the end moving: extend right.
        assertArrayEquals(new int[] {2, 9}, move(s, 2, 8, 1, true, false));
        // Swapped (anchor 8, moving end 2): the start moves, left or right.
        assertArrayEquals(new int[] {8, 1}, move(s, 8, 2, -1, true, false));
        assertArrayEquals(new int[] {8, 3}, move(s, 8, 2, 1, true, false));
        // The start moves by words too.
        assertArrayEquals(new int[] {8, 0}, move(s, 8, 2, -1, true, true));
    }
}
