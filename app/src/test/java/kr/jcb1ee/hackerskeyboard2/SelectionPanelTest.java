package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** The selection panel's Select mode against editors, and the Select button's trackpad. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SelectionPanelTest {
    /** The keyboard, typing into {@link #sConnection}. */
    public static class TestIME extends LatinIME {
        @Override
        public InputConnection getCurrentInputConnection() {
            return sConnection;
        }

        @Override
        public EditorInfo getCurrentInputEditorInfo() {
            EditorInfo ei = new EditorInfo();
            ei.inputType = sInputType;
            return ei;
        }
    }

    static InputConnection sConnection;
    static int sInputType = InputType.TYPE_CLASS_TEXT;
    private EditText mField;
    private LatinIME mIme;

    @Before
    public void setUp() {
        mField = new EditText(ApplicationProvider.getApplicationContext());
        mField.setText("hello world");
        sInputType = InputType.TYPE_CLASS_TEXT;
        mIme = Robolectric.buildService(TestIME.class).create().get();
    }

    /** An editor that reports its selection as (start, end) whichever way it was set. */
    private InputConnection normalising() {
        return new InputConnectionWrapper(mField.onCreateInputConnection(new EditorInfo()), true) {
            @Override
            public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
                ExtractedText et = super.getExtractedText(request, flags);
                int s = Math.min(et.selectionStart, et.selectionEnd);
                et.selectionEnd = Math.max(et.selectionStart, et.selectionEnd);
                et.selectionStart = s;
                return et;
            }
        };
    }

    private void assertSelected(int from, int to) {
        assertEquals(from, Math.min(mField.getSelectionStart(), mField.getSelectionEnd()));
        assertEquals(to, Math.max(mField.getSelectionStart(), mField.getSelectionEnd()));
    }

    /** Selecting backwards (or forwards in right-to-left text) keeps growing the selection. */
    @Test
    public void selectingBackwardsInANormalisingEditorKeepsTheAnchor() {
        sConnection = normalising();
        sConnection.setSelection(5, 5);
        mIme.moveCursor(-1, true, false);
        assertSelected(4, 5);
        mIme.moveCursor(-1, true, false);
        assertSelected(3, 5);
        mIme.moveCursor(-1, true, false);
        assertSelected(2, 5);
        mIme.moveCursor(1, true, false); // and back
        assertSelected(3, 5);
    }

    /** A selection the user changed in the app is taken from the app, not the memory. */
    @Test
    public void selectionChangedByTheAppIsFollowed() {
        sConnection = normalising();
        sConnection.setSelection(5, 5);
        mIme.moveCursor(-1, true, false);
        sConnection.setSelection(8, 8); // user taps elsewhere
        mIme.moveCursor(1, true, false);
        assertSelected(8, 9);
    }

    /** Holding the Select button and dragging moves the cursor, or the selection's end. */
    @Test
    public void selectButtonIsATrackpad() {
        sConnection = mField.onCreateInputConnection(new EditorInfo());
        sConnection.setSelection(0, 0);
        KeyboardToolbar toolbar = (KeyboardToolbar) mIme.onCreateInputView();
        idle();
        LinearLayout bar = (LinearLayout) toolbar.getChildAt(0);
        ((ViewGroup) bar.getChildAt(0)).getChildAt(0).performClick(); // selection panel
        idle();
        FrameLayout body = (FrameLayout) toolbar.getChildAt(1);
        TextView select = find(body.getChildAt(1), mIme.getString(R.string.toolbar_select));

        drag(select, 60);
        int cursor = mField.getSelectionEnd();
        assertTrue("cursor moved right, at " + cursor, cursor > 0);
        assertEquals("no selection while Select is off", cursor, mField.getSelectionStart());

        tap(select); // Select on
        drag(select, 60);
        assertEquals("anchor stays", cursor, mField.getSelectionStart());
        assertTrue("selection grew", mField.getSelectionEnd() > cursor);
    }

    // ---------------------------------------------------------------- trackpad drags

    private static final String LINES = "first line\nsecond line\nthird";

    /**
     * A multi-line editor like many real ones: it reports selection changes a little later
     * (and until then, reading it back gives the old selection), and an up arrow on the
     * first line (down on the last) jumps to the start (end) of the text.
     */
    private InputConnection laggy() {
        mField.setText(LINES);
        sInputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        final int[] reported = {0, 0};
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        return new InputConnectionWrapper(mField.onCreateInputConnection(new EditorInfo()), true) {
            void select(int anchor, int focus) {
                android.text.Selection.setSelection(mField.getText(), anchor, focus);
                handler.postDelayed(() -> {
                    int oldStart = reported[0], oldEnd = reported[1];
                    reported[0] = anchor;
                    reported[1] = focus;
                    mIme.onUpdateSelection(oldStart, oldEnd, anchor, focus, -1, -1);
                }, 30);
            }

            @Override
            public boolean setSelection(int start, int end) {
                select(start, end);
                return true;
            }

            @Override
            public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
                ExtractedText et = super.getExtractedText(request, flags);
                et.selectionStart = reported[0];
                et.selectionEnd = reported[1];
                return et;
            }

            @Override
            public boolean requestCursorUpdates(int mode) {
                return false;
            }

            @Override
            public boolean sendKeyEvent(android.view.KeyEvent event) {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return true;
                boolean up = event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_UP;
                int anchor = android.text.Selection.getSelectionStart(mField.getText());
                int focus = android.text.Selection.getSelectionEnd(mField.getText());
                int lineStart = LINES.lastIndexOf('\n', focus - 1) + 1;
                int col = focus - lineStart;
                int target;
                if (up) {
                    if (lineStart == 0) {
                        target = 0;
                    } else {
                        int prev = LINES.lastIndexOf('\n', lineStart - 2) + 1;
                        target = Math.min(prev + col, lineStart - 1);
                    }
                } else {
                    int next = LINES.indexOf('\n', focus);
                    if (next < 0) {
                        target = LINES.length();
                    } else {
                        int nextEnd = LINES.indexOf('\n', next + 1);
                        if (nextEnd < 0) nextEnd = LINES.length();
                        target = Math.min(next + 1 + col, nextEnd);
                    }
                }
                select(event.isShiftPressed() ? anchor : target, target);
                return true;
            }
        };
    }

    private static void settle() {
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
    }

    private void assertSelection(int anchor, int focus) {
        assertEquals("anchor", anchor, android.text.Selection.getSelectionStart(mField.getText()));
        assertEquals("moving end", focus, android.text.Selection.getSelectionEnd(mField.getText()));
    }

    /** Lines and characters mix smoothly, and dragging past the top doesn't jump. */
    @Test
    public void spaceDragMovesByLinesWithoutJumping() {
        sConnection = laggy();
        sConnection.setSelection(15, 15); // "second line", after "seco"
        settle();
        assertTrue(mIme.beginCursorDrag(false));
        mIme.mCursorDrag.lines(-1);   // up to "firs|t"
        mIme.mCursorDrag.chars(2);    // while the editor is still busy
        settle();
        assertSelection(6, 6);
        mIme.mCursorDrag.lines(-1);   // first line: would jump to the start
        settle();
        assertSelection(6, 6);
        mIme.mCursorDrag.chars(-1);
        settle();
        assertSelection(5, 5);
        mIme.mCursorDrag.end();
    }

    /** Dragging with Select on keeps the anchor through line moves. */
    @Test
    public void selectDragKeepsTheSelection() {
        sConnection = laggy();
        sConnection.setSelection(15, 15);
        settle();
        assertTrue(mIme.beginCursorDrag(true));
        mIme.mCursorDrag.chars(2);    // 15..17
        mIme.mCursorDrag.lines(1);    // third line, column 6 -> its end (28)
        mIme.mCursorDrag.chars(-1);
        settle();
        assertSelection(15, 27);
        mIme.mCursorDrag.lines(1);    // last line: would jump to the end
        settle();
        assertSelection(15, 27);
        mIme.mCursorDrag.chars(-3);
        settle();
        assertSelection(15, 24);
        mIme.mCursorDrag.end();
    }

    private static void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static void tap(View v) {
        long t = SystemClock.uptimeMillis();
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 10, 10, 0));
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, 10, 10, 0));
        idle();
    }

    /** Hold, then drag right by {@code dp}·density pixels over 0.6s. */
    private static void drag(View v, int dp) {
        float d = v.getResources().getDisplayMetrics().density;
        long t0 = SystemClock.uptimeMillis();
        v.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 10, 10, 0));
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        for (int i = 1; i <= 6; i++) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(100));
            v.dispatchTouchEvent(MotionEvent.obtain(t0, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_MOVE, 10 + i * dp * d / 6, 10, 0));
        }
        v.dispatchTouchEvent(MotionEvent.obtain(t0, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, 10 + dp * d, 10, 0));
        idle();
    }

    private static TextView find(View v, String text) {
        if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return (TextView) v;
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                TextView found = find(((ViewGroup) v).getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
