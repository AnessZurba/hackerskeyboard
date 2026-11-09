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
        return laggy(false);
    }

    /** {@code dropsSelection}: like some editors, Shift+arrow collapses the selection. */
    private InputConnection laggy(final boolean dropsSelection) {
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
                if (event.getKeyCode() != android.view.KeyEvent.KEYCODE_DPAD_UP
                        && event.getKeyCode() != android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    return true; // Shift/Ctrl going down around the arrow
                }
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
                select(event.isShiftPressed() && !dropsSelection ? anchor : target, target);
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
        assertTrue("not at once", !mIme.mLoupe.isShowing());
        ShadowLooper.idleMainLooper(CursorLoupe.START_DELAY_MS + 100, TimeUnit.MILLISECONDS);
        assertTrue("loupe up after a moment", mIme.mLoupe.isShowing());
        assertEquals("the current line only", "second line", mIme.mLoupe.text().toString());
        mIme.mCursorDrag.lines(-1);
        settle();
        assertEquals("first line", mIme.mLoupe.text().toString());
        mIme.mCursorDrag.end();
        assertTrue("gone after", !mIme.mLoupe.isShowing());
    }

    /** The bubble stays still while the caret wobbles, and only follows when it really moves. */
    @Test
    public void loupePlacementIsSteady() {
        CursorLoupe.Placer p = new CursorLoupe.Placer(1f);
        int w = 117, h = 23, kb = 1000, screen = 1000;
        float[] first = p.place(500, 300, 320, w, h, kb, screen);
        assertEquals(500 - w / 2f, first[0], 0.01f);
        assertEquals(300 - h - 10, first[1], 0.01f);
        // Caret steps a character at a time within the dead zone, top jitters by a pixel or two.
        float[] xs = {505, 498, 512, 490, 503, 497};
        float[] tops = {301, 299, 302, 300, 298, 301};
        for (int i = 0; i < xs.length; i++) {
            float[] r = p.place(xs[i], tops[i], tops[i] + 20, w, h, kb, screen);
            assertEquals("x steady " + i, first[0], r[0], 0.01f);
            assertEquals("y steady " + i, first[1], r[1], 0.01f);
        }
        // Moving right past the dead zone drags the bubble along, caret at the zone's edge.
        float[] r = p.place(600, 300, 320, w, h, kb, screen);
        assertEquals(600 - w * 0.2f - w / 2f, r[0], 0.01f);
        assertEquals(first[1], r[1], 0.01f);
        // Back left a little: no move.
        assertEquals(r[0], p.place(590, 300, 320, w, h, kb, screen)[0], 0.01f);
        // A new line moves it vertically.
        assertEquals(330 - h - 10, p.place(590, 330, 350, w, h, kb, screen)[1], 0.01f);
        // Near the top: below the line, and it stays below with jitter around the threshold.
        p.reset();
        float y1 = p.place(500, 20, 40, w, h, kb, screen)[1];
        assertEquals(40 + 10, y1, 0.01f);
        assertEquals(y1, p.place(500, 25, 45, w, h, kb, screen)[1], 0.01f);
        // Never off the screen sideways.
        p.reset();
        assertEquals(4f, p.place(2, 300, 320, w, h, kb, screen)[0], 0.01f);
        assertEquals(screen - 4f - w, p.place(999, 300, 320, w, h, kb, screen)[0], 0.01f);
    }

    /** The bubble is 60% of the old size and its line of text fits inside. */
    @Test
    public void loupeIsSmallAndTheTextFits() {
        sConnection = laggy();
        sConnection.setSelection(15, 15);
        settle();
        attachKeyboard();
        assertTrue(mIme.beginCursorDrag(false));
        ShadowLooper.idleMainLooper(CursorLoupe.START_DELAY_MS + 100, TimeUnit.MILLISECONDS);
        assertTrue(mIme.mLoupe.isShowing());
        float density = mIme.getResources().getDisplayMetrics().density;
        android.widget.PopupWindow popup = mIme.mLoupe.popup();
        assertEquals(Math.round(117 * density), popup.getWidth());
        assertTrue(popup.getHeight() >= Math.round(23 * density));
        CursorLoupe.LoupeText view = (CursorLoupe.LoupeText) popup.getContentView();
        assertTrue("line fits between the paddings",
                view.lineHeight() <= popup.getHeight() - view.getPaddingTop() - view.getPaddingBottom());
        mIme.mCursorDrag.end();
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
        ShadowLooper.idleMainLooper(CursorLoupe.START_DELAY_MS + 100, TimeUnit.MILLISECONDS);
        assertTrue(!mIme.mLoupe.isShowing());
        mIme.mCursorDrag.end();
    }

    /** An editor that drops the selection on Shift+arrow: the drag puts it back. */
    @Test
    public void selectDragKeepsSelectionWhereShiftArrowDropsIt() {
        sConnection = laggy(true);
        sConnection.setSelection(15, 15);
        settle();
        assertTrue(mIme.beginCursorDrag(true));
        mIme.mCursorDrag.chars(2);   // 15..17
        mIme.mCursorDrag.lines(-1);  // up: the editor collapses at 6
        settle();
        assertSelection(15, 6);
        mIme.mCursorDrag.chars(1);
        settle();
        assertSelection(15, 7);
        mIme.mCursorDrag.end();
    }

    /** The toolbar's up arrow with Select on, pressed twice quickly, keeps the selection. */
    @Test
    public void toolbarUpArrowKeepsSelection() {
        sConnection = laggy(true);
        sConnection.setSelection(26, 26); // "third", after "thi"
        settle();
        for (int i = 0; i < 2; i++) {     // second press while the first is in flight
            assertTrue(mIme.beginQuietCursorDrag(true));
            assertTrue(mIme.mCursorDrag.lines(-1));
            mIme.mCursorDrag.end();
        }
        settle();
        assertSelection(26, 3);           // two lines up, column 3
        assertTrue("drag over", !mIme.mCursorDrag.isActive());
    }

    /** The loupe steps aside while the cursor moves fast, and is back once it slows. */
    @Test
    public void loupeHidesWhileMovingFast() {
        sConnection = mField.onCreateInputConnection(new EditorInfo());
        mField.setText("the quick brown fox jumps over the lazy dog, again and again");
        sConnection.setSelection(0, 0);
        attachKeyboard();
        assertTrue(mIme.beginCursorDrag(false));
        ShadowLooper.idleMainLooper(CursorLoupe.START_DELAY_MS + 100, TimeUnit.MILLISECONDS);
        assertTrue(mIme.mLoupe.isShowing());
        mIme.mCursorDrag.chars(1);
        assertTrue("slow: stays", mIme.mLoupe.isShowing());
        for (int i = 0; i < 5; i++) mIme.mCursorDrag.chars(4); // 20 characters at once
        assertTrue("fast: hidden", !mIme.mLoupe.isShowing());
        ShadowLooper.idleMainLooper(CursorLoupe.RESUME_DELAY_MS - 100, TimeUnit.MILLISECONDS);
        assertTrue("not straight back", !mIme.mLoupe.isShowing());
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        assertTrue("calm for a while: back", mIme.mLoupe.isShowing());
        mIme.mCursorDrag.end();
    }

    // ---------------------------------------------------------------- backspace swipe

    /** Characters selected after dragging left from backspace by {@code dp} over {@code ms}. */
    private int backspaceSwipe(float dp, long ms) throws Exception {
        sConnection = mField.onCreateInputConnection(new EditorInfo());
        mField.setText("aa bb cc dd ee ff gg hh ii jj kk ll mm nn oo pp qq rr ss tt");
        sConnection.setSelection(mField.length(), mField.length());
        attachKeyboard();
        java.lang.reflect.Field f = LatinIME.class.getDeclaredField("mDeleteSwipeField");
        f.setAccessible(true);
        f.setBoolean(mIme, true);
        LatinKeyboardView view = mIme.mKeyboardSwitcher.getInputView();
        Keyboard.Key del = null;
        for (Keyboard.Key k : view.getKeyboard().getKeys()) {
            if (k.codes != null && k.codes[0] == Keyboard.KEYCODE_DELETE) del = k;
        }
        float x0 = del.x + del.width / 2f, y = del.y + del.height / 2f;
        float px = dp * view.getResources().getDisplayMetrics().density;
        long t0 = SystemClock.uptimeMillis();
        view.onTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x0, y, 0));
        int steps = 8;
        for (int i = 1; i <= steps; i++) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(ms / steps));
            view.onTouchEvent(MotionEvent.obtain(t0, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_MOVE, x0 - px * i / steps, y, 0));
        }
        int selected = Math.abs(mField.getSelectionEnd() - mField.getSelectionStart());
        mLastOverlay = mIme.mKeyboardSwitcher.getInputContainer().gestureLabelStatus();
        view.onTouchEvent(MotionEvent.obtain(t0, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, x0 - px, y, 0));
        idle();
        assertEquals("label gone after", null, mIme.mKeyboardSwitcher.getInputContainer().gestureLabelStatus());
        return selected;
    }

    private String mLastOverlay;

    @Test
    public void backspaceSwipeSlowIsOneWordPerStep() throws Exception {
        assertEquals("4 words (14dp each): \"qq rr ss tt\"", 11, backspaceSwipe(64, 800));
        assertEquals("the keyboard says how many", "4 words", mLastOverlay);
        assertEquals("deleted on release", "aa bb cc dd ee ff gg hh ii jj kk ll mm nn oo pp ",
                mField.getText().toString());
    }

    @Test
    public void backspaceSwipeFastGoesFurther() throws Exception {
        int fast = backspaceSwipe(64, 40);
        assertTrue("fast flick selects more than 4 words: " + fast, fast > 11);
    }

    /**
     * A real text field extends the selection on an arrow only while Shift is held (its own
     * key events), not for an arrow merely flagged with Shift: up/down keep the selection.
     */
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void upDownExtendTheSelectionInARealTextField() {
        sInputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        android.app.Activity a = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        final EditText f = new EditText(a);
        f.setInputType(sInputType);
        a.setContentView(f);
        f.setText("first line\nsecond line\nthird");
        f.requestFocus();
        f.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        f.layout(0, 0, 1000, f.getMeasuredHeight());
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final int[] last = {0, 0};
        final Runnable report = () -> {
            int s = f.getSelectionStart(), e = f.getSelectionEnd();
            if (s != last[0] || e != last[1]) {
                mIme.onUpdateSelection(last[0], last[1], s, e, -1, -1);
                last[0] = s;
                last[1] = e;
            }
        };
        // Keys go to the field as the app's window would deliver them; reports come later.
        sConnection = new InputConnectionWrapper(f.onCreateInputConnection(new EditorInfo()), true) {
            @Override
            public boolean setSelection(int s, int e) {
                boolean r = super.setSelection(s, e);
                h.postDelayed(report, 20);
                return r;
            }

            @Override
            public boolean sendKeyEvent(android.view.KeyEvent ev) {
                boolean r = f.dispatchKeyEvent(ev);
                h.postDelayed(report, 20);
                return r;
            }
        };
        sConnection.setSelection(15, 15);
        settle();
        mIme.moveCursor(2, true, false); // Select on, right twice: 15..17
        settle();
        assertTrue(mIme.beginQuietCursorDrag(true));
        mIme.mCursorDrag.lines(-1);      // the toolbar's up arrow
        mIme.mCursorDrag.end();
        settle();
        assertEquals("anchor kept", 15, f.getSelectionStart());
        assertTrue("moved up a line: " + f.getSelectionEnd(), f.getSelectionEnd() < 11);
        assertTrue(mIme.beginQuietCursorDrag(true));
        mIme.mCursorDrag.lines(1);
        mIme.mCursorDrag.end();
        assertTrue(mIme.beginQuietCursorDrag(true));
        mIme.mCursorDrag.lines(1);
        mIme.mCursorDrag.end();
        settle();
        assertEquals("anchor kept", 15, f.getSelectionStart());
        assertTrue("two lines down: " + f.getSelectionEnd(), f.getSelectionEnd() > 23);
    }

    // ---------------------------------------------------------------- panel keys

    private KeyboardToolbar openPanel(int button) throws Exception {
        // Keys reach the field as the app's window would deliver them.
        sConnection = new InputConnectionWrapper(mField.onCreateInputConnection(new EditorInfo()), true) {
            @Override
            public boolean sendKeyEvent(android.view.KeyEvent event) {
                return mField.dispatchKeyEvent(event);
            }
        };
        KeyboardToolbar toolbar = (KeyboardToolbar) mIme.onCreateInputView();
        idle();
        layout(toolbar);
        java.lang.reflect.Field f = LatinIME.class.getDeclaredField("mDeleteSwipeField");
        f.setAccessible(true);
        f.setBoolean(mIme, true);
        LinearLayout bar = (LinearLayout) toolbar.getChildAt(0);
        ((ViewGroup) bar.getChildAt(button)).getChildAt(0).performClick();
        idle();
        layout(toolbar);
        return toolbar;
    }

    private static void drag(View v, float dx, long ms, boolean hold) {
        long t0 = SystemClock.uptimeMillis();
        v.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 100, 10, 0));
        if (hold) ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        for (int i = 1; i <= 6; i++) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(ms / 6));
            v.dispatchTouchEvent(MotionEvent.obtain(t0, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_MOVE, 100 + dx * i / 6, 10, 0));
        }
        v.dispatchTouchEvent(MotionEvent.obtain(t0, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, 100 + dx, 10, 0));
        idle();
    }

    /** The label on the panel mid-gesture (before the finger lifts). */
    private static String labelMidDrag(View v, float dx, boolean hold, KeyboardToolbar t) {
        long t0 = SystemClock.uptimeMillis();
        v.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 100, 10, 0));
        if (hold) ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        for (int i = 1; i <= 6; i++) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(100));
            v.dispatchTouchEvent(MotionEvent.obtain(t0, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_MOVE, 100 + dx * i / 6, 10, 0));
        }
        String label = t.gestureLabelStatus();
        v.dispatchTouchEvent(MotionEvent.obtain(t0, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, 100 + dx, 10, 0));
        idle();
        assertEquals("label gone after", null, t.gestureLabelStatus());
        return label;
    }

    /** Panel keys label their gestures too: backspace, space, and the Select button. */
    @Test
    public void panelGesturesShowTheLabel() throws Exception {
        float d = mIme.getResources().getDisplayMetrics().density;
        KeyboardToolbar emoji = openPanel(2);
        mField.setText("one two three four");
        sConnection.setSelection(mField.length(), mField.length());
        View panel = ((FrameLayout) emoji.getChildAt(1)).getChildAt(1);
        assertEquals("2 words", labelMidDrag(find(panel, "⌫"), -40 * d, false, emoji));
        View space = findByDescription(panel, mIme.getString(R.string.toolbar_emoji_space));
        assertEquals("Moving the cursor", labelMidDrag(space, 40 * d, true, emoji));

        emoji.closePanel();
        LinearLayout bar = (LinearLayout) emoji.getChildAt(0);
        ((ViewGroup) bar.getChildAt(0)).getChildAt(0).performClick(); // selection panel
        idle();
        layout(emoji);
        TextView select = find(((FrameLayout) emoji.getChildAt(1)).getChildAt(1),
                mIme.getString(R.string.toolbar_select));
        tap(select); // Select on
        assertEquals("Selecting", labelMidDrag(select, -30 * d, true, emoji));
    }

    /** The selection panel's backspace swipes words away like the keyboard's. */
    @Test
    public void selectionPanelBackspaceSwipes() throws Exception {
        KeyboardToolbar toolbar = openPanel(0);
        mField.setText("one two three four");
        sConnection.setSelection(mField.length(), mField.length());
        TextView del = find(((FrameLayout) toolbar.getChildAt(1)).getChildAt(1), "⌫");
        float d = mIme.getResources().getDisplayMetrics().density;
        drag(del, -40 * d, 600, false); // two words, slowly
        assertEquals("one two ", mField.getText().toString());
        assertEquals("label gone after the panel swipe", null, toolbar.gestureLabelStatus());
        del.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 10, 0));
        del.dispatchTouchEvent(MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 100, 10, 0));
        idle();
        assertEquals("a tap deletes one character", "one two", mField.getText().toString());
    }

    /** Holding backspace a moment first (repeat starts), then sliding, still swipes words. */
    @Test
    public void panelBackspaceSwipesAfterAHold() throws Exception {
        KeyboardToolbar toolbar = openPanel(0);
        mField.setText("one two three four five six seven");
        sConnection.setSelection(mField.length(), mField.length());
        TextView del = find(((FrameLayout) toolbar.getChildAt(1)).getChildAt(1), "⌫");
        float d = mIme.getResources().getDisplayMetrics().density;
        drag(del, -48 * d, 600, true);
        String text = mField.getText().toString();
        assertTrue("whole words went, not just characters: \"" + text + "\"", text.endsWith(" "));
        assertTrue(text.length() < "one two three four five six ".length());
    }

    /** The emoji panel's space bar types a space, and is a trackpad when held. */
    @Test
    public void emojiPanelSpaceIsATrackpad() throws Exception {
        KeyboardToolbar toolbar = openPanel(2);
        mField.setText("hello world");
        sConnection.setSelection(0, 0);
        View space = findByDescription(((FrameLayout) toolbar.getChildAt(1)).getChildAt(1),
                mIme.getString(R.string.toolbar_emoji_space));
        float d = mIme.getResources().getDisplayMetrics().density;
        drag(space, 40 * d, 600, true);
        assertTrue("cursor moved right: " + mField.getSelectionEnd(), mField.getSelectionEnd() > 2);
        assertEquals("hello world", mField.getText().toString());
        space.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 10, 0));
        space.dispatchTouchEvent(MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 100, 10, 0));
        idle();
        assertTrue("a tap types a space", mField.getText().toString().contains("  ")
                || mField.getText().toString().length() == 12);
    }

    /** A copy made while the keyboard wasn't running is picked up once typing starts. */
    @Test
    public void clipboardCatchesUpWithCopiesMadeMeanwhile() {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                mIme.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        ClipboardHistory.forget();
        cm.setPrimaryClip(android.content.ClipData.newPlainText("", "copied while away"));
        ClipboardHistory history = ClipboardHistory.get(mIme); // not listening yet
        history.catchUp();
        assertEquals("copied while away", history.clips().get(0).text);
        history.add("later");
        history.catchUp(); // nothing new: no duplicate, no reordering
        assertEquals("later", history.clips().get(0).text);
        assertEquals(2, history.clips().size());
    }

    /** The history keeps 50 unpinned clips, of which at most 20 images. */
    @Test
    public void clipboardKeeps50ClipsAndFewerImages() throws Exception {
        ClipboardHistory history = ClipboardHistory.get(mIme);
        for (int i = 0; i < 60; i++) history.add("text " + i);
        assertEquals(50, history.clips().size());
        assertEquals("newest first", "text 59", history.clips().get(0).text);
        for (int i = 0; i < 25; i++) {
            java.io.File f = new java.io.File(ClipboardHistory.imageDir(mIme), "img" + i + ".png");
            new java.io.FileOutputStream(f).close();
            history.addImage(f.getName(), "image/png");
        }
        int images = 0;
        for (ClipboardHistory.Clip c : history.clips()) if (c.isImage()) images++;
        assertEquals("20 images at most", 20, images);
        assertEquals(50, history.clips().size());
        assertTrue("oldest image files removed",
                !new java.io.File(ClipboardHistory.imageDir(mIme), "img0.png").exists());
    }

    /** Unpinned clips go after a day; pinned ones stay, and unpinning starts a fresh day. */
    @Test
    public void clipboardClipsExpireAfterADay() {
        ClipboardHistory history = ClipboardHistory.get(mIme);
        history.add("old");
        history.add("kept");
        history.add("fresh");
        long dayAgo = System.currentTimeMillis() - ClipboardHistory.MAX_AGE_MS - 1000;
        for (ClipboardHistory.Clip c : history.clips()) {
            if (!c.text.equals("fresh")) c.time = dayAgo;
            if (c.text.equals("kept")) c.pinned = true;
        }
        java.util.List<String> left = new java.util.ArrayList<>();
        for (ClipboardHistory.Clip c : history.clips()) left.add(c.text);
        assertEquals(java.util.Arrays.asList("fresh", "kept"), left);
        ClipboardHistory.Clip kept = history.clips().get(1);
        history.togglePin(kept); // unpinned: a fresh day, not gone at once
        assertEquals(2, history.clips().size());
    }

    /** No password-manager chips in terminals (Termux); text fields still get them. */
    @Test
    public void noAutofillChipsInTerminals() {
        mIme.onCreateInputView();
        idle();
        sInputType = InputType.TYPE_NULL; // a terminal: hides its text from the keyboard
        assertEquals(null, mIme.onCreateInlineSuggestionsRequest(new android.os.Bundle()));
        sInputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        assertTrue(mIme.onCreateInlineSuggestionsRequest(new android.os.Bundle()) != null);
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
