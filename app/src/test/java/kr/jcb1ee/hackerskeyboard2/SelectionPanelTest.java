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
        InputConnection appConnection() {
            return sConnection;
        }

        @Override
        public EditorInfo getCurrentInputEditorInfo() {
            EditorInfo ei = new EditorInfo();
            ei.inputType = sInputType;
            ei.packageName = "com.example.chat";
            if (sImages) {
                androidx.core.view.inputmethod.EditorInfoCompat.setContentMimeTypes(ei, new String[] {"image/*"});
            }
            return ei;
        }
    }

    static InputConnection sConnection;
    static int sInputType = InputType.TYPE_CLASS_TEXT;
    static boolean sImages;
    private EditText mField;
    private LatinIME mIme;

    @Before
    public void setUp() {
        mField = new EditText(ApplicationProvider.getApplicationContext());
        mField.setText("hello world");
        sInputType = InputType.TYPE_CLASS_TEXT;
        sImages = false;
        ClipboardHistory.forget();
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

    // ---------------------------------------------------------------- emoji panel

    private KeyboardToolbar openEmoji() {
        sConnection = mField.onCreateInputConnection(new EditorInfo());
        mField.setText("");
        KeyboardToolbar toolbar = (KeyboardToolbar) mIme.onCreateInputView();
        idle();
        layout(toolbar);
        LinearLayout bar = (LinearLayout) toolbar.getChildAt(0);
        ((ViewGroup) bar.getChildAt(2)).getChildAt(0).performClick();
        idle();
        layout(toolbar);
        return toolbar;
    }

    private static void layout(View v) {
        for (int i = 0; i < 2; i++) {
            v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            v.layout(0, 0, 1080, v.getMeasuredHeight());
        }
    }

    /** Every category is in the one scrolling grid, not just a page of it. */
    @Test
    public void emojiPanelHoldsEveryEmoji() {
        KeyboardToolbar toolbar = openEmoji();
        FrameLayout body = (FrameLayout) toolbar.getChildAt(1);
        androidx.recyclerview.widget.RecyclerView grid = findGrid(body.getChildAt(1));
        assertTrue("all emoji, got " + grid.getAdapter().getItemCount(),
                grid.getAdapter().getItemCount() > 1500);
        assertTrue("cells laid out", grid.getChildCount() > 20);
        View cell = grid.getChildAt(1); // after the first section header
        cell.performClick();
        assertTrue("typed into the app: " + mField.getText(), mField.getText().length() > 0);
    }

    /** Search: the keys type the query, results come up, a tap types into the app. */
    @Test
    public void emojiSearchTypesQueryAndInsertsResult() {
        KeyboardToolbar toolbar = openEmoji();
        FrameLayout body = (FrameLayout) toolbar.getChildAt(1);
        View panel = body.getChildAt(1);
        View search = findByDescription(panel, mIme.getString(R.string.toolbar_emoji_search));
        search.performClick();
        idle();
        assertTrue(toolbar.isSearching());
        assertEquals("keyboard is back", View.VISIBLE, body.getChildAt(0).getVisibility());
        for (char c : "grin".toCharArray()) mIme.onKey(c, new int[] {c}, 0, 0);
        idle();
        assertEquals("the app got none of the query", "", mField.getText().toString());
        assertTrue(mIme.getCurrentInputConnection().getTextBeforeCursor(10, 0).toString().contains("grin"));
        LinearLayout bar = (LinearLayout) toolbar.getChildAt(0);
        TextView first = firstEmojiIn(bar);
        assertTrue("found a result", first != null);
        String emoji = first.getText().toString();
        first.performClick();
        assertEquals("result typed into the app", emoji, mField.getText().toString());
        assertTrue("still searching", toolbar.isSearching());
        toolbar.endSearchFromBack();
        assertTrue(!toolbar.isSearching());
        assertTrue("keys go to the app again", mIme.getCurrentInputConnection() == sConnection);
    }

    @Test
    public void emojiSearchMatchesArabicAndHebrew() {
        KeyboardToolbar toolbar = openEmoji();
        assertTrue(toolbar.searchEmoji("ضحك", 10).contains("😀"));
        assertTrue(toolbar.searchEmoji("חיוך", 10).contains("😀"));
        assertEquals("😀", toolbar.searchEmoji("grinning face", 10).get(0));
    }

    private static androidx.recyclerview.widget.RecyclerView findGrid(View v) {
        if (v instanceof androidx.recyclerview.widget.RecyclerView) return (androidx.recyclerview.widget.RecyclerView) v;
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                androidx.recyclerview.widget.RecyclerView r = findGrid(((ViewGroup) v).getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    private static View findByDescription(View v, String d) {
        if (d.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())) return v;
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                View r = findByDescription(((ViewGroup) v).getChildAt(i), d);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static TextView firstEmojiIn(View v) {
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE && v.isClickable()
                && ((TextView) v).getText().length() > 0
                && Character.isSurrogate(((TextView) v).getText().charAt(0))) {
            return (TextView) v;
        }
        if (v instanceof ViewGroup && v.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                TextView r = firstEmojiIn(((ViewGroup) v).getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- images

    private java.io.File imageClip() throws Exception {
        java.io.File file = new java.io.File(ClipboardHistory.imageDir(mIme), "abc.png");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
        return file;
    }

    /** Fields that take images (chat apps) get them as content, shared one by one. */
    @Test
    public void imagesGoInAsContentWhereAccepted() throws Exception {
        java.io.File file = imageClip();
        final android.net.Uri[] got = new android.net.Uri[1];
        sConnection = new InputConnectionWrapper(mField.onCreateInputConnection(new EditorInfo()), true) {
            @Override
            public boolean commitContent(android.view.inputmethod.InputContentInfo info, int flags,
                    android.os.Bundle opts) {
                got[0] = info.getContentUri();
                assertTrue("app may read it", (flags & InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0);
                return true;
            }
        };
        sImages = false;
        assertTrue("a plain text field doesn't take it", !mIme.commitImage(file, "image/png"));
        assertEquals(null, got[0]);
        sImages = true;
        assertTrue(mIme.commitImage(file, "image/png"));
        assertEquals("content", got[0].getScheme());
        assertEquals(mIme.getPackageName() + ".images", got[0].getAuthority());
    }

    /** Image clips are kept as files, and removing the clip removes its file. */
    @Test
    public void imageClipsKeepAndDropTheirFiles() throws Exception {
        java.io.File file = imageClip();
        ClipboardHistory history = ClipboardHistory.get(mIme);
        history.addImage(file.getName(), "image/png");
        ClipboardHistory.Clip clip = history.clips().get(0);
        assertTrue(clip.isImage());
        assertEquals(file, history.imageFile(clip));
        history.addImage(file.getName(), "image/png"); // copied again: no duplicate
        int images = 0;
        for (ClipboardHistory.Clip c : history.clips()) if (c.isImage()) images++;
        assertEquals(1, images);
        history.remove(history.clips().get(0));
        assertTrue("file removed", !file.exists());
    }

    // ---------------------------------------------------------------- loupe

    private void attachKeyboard() {
        KeyboardToolbar toolbar = (KeyboardToolbar) mIme.onCreateInputView();
        idle();
        android.app.Activity host = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        if (toolbar.getParent() instanceof ViewGroup) ((ViewGroup) toolbar.getParent()).removeView(toolbar);
        host.setContentView(toolbar);
        idle();
    }

    /** Dragging shows the line around the cursor, follows it, and goes away at the end. */
    @Test
    public void loupeFollowsTheDrag() {
        sConnection = laggy(); // "first line\nsecond line\nthird"
        sConnection.setSelection(15, 15);
        settle();
        attachKeyboard();
        assertTrue(mIme.beginCursorDrag(false));
        assertTrue("loupe up", mIme.mLoupe.isShowing());
        assertEquals("the current line only", "second line", mIme.mLoupe.text().toString());
        mIme.mCursorDrag.lines(-1);
        settle();
        assertEquals("first line", mIme.mLoupe.text().toString());
        mIme.mCursorDrag.end();
        assertTrue("gone after", !mIme.mLoupe.isShowing());
    }

    @Test
    public void loupeCanBeTurnedOff() {
        sConnection = laggy();
        sConnection.setSelection(15, 15);
        settle();
        attachKeyboard();
        android.preference.PreferenceManager.getDefaultSharedPreferences(mIme).edit()
                .putBoolean(CursorLoupe.PREF, false).commit();
        idle();
        assertTrue(mIme.beginCursorDrag(false));
        assertTrue(!mIme.mLoupe.isShowing());
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
