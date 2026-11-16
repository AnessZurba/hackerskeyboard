package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Swipe left / right across the keys: undo and redo (the defaults), their label and tip. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class UndoSwipeTest {
    private EditText mEdit;
    private org.robolectric.android.controller.ServiceController<SelectionPanelTest.TestIME> mController;
    private LatinIME mIme;
    private KeyboardToolbar mToolbar;
    private LatinKeyboardView mView;
    private final List<String> mSent = new ArrayList<>();

    @Before
    public void setUp() {
        Shadows.shadowOf(ApplicationProvider.<android.app.Application>getApplicationContext()
                .getPackageManager()).setSystemFeature(
                        android.content.pm.PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT, true);
        HintManager.resetAll(prefs());
    }

    @After
    public void tearDown() {
        if (mController != null) mController.destroy();
        HintManager.resetAll(prefs());
        prefs().edit().remove(LatinIME.PREF_SWIPE_LEFT).remove(LatinIME.PREF_SWIPE_RIGHT).commit();
        SelectionPanelTest.sConnection = null;
        SelectionPanelTest.sInputType = InputType.TYPE_CLASS_TEXT;
    }

    private static SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
    }

    private static void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static String describe(KeyEvent e) {
        StringBuilder s = new StringBuilder(e.getAction() == KeyEvent.ACTION_DOWN ? "down " : "up ");
        int code = e.getKeyCode();
        s.append(code == KeyEvent.KEYCODE_CTRL_LEFT ? "CTRL_LEFT"
                : code == KeyEvent.KEYCODE_SHIFT_LEFT ? "SHIFT_LEFT"
                : code == KeyEvent.KEYCODE_Z ? "Z" : Integer.toString(code));
        if ((e.getMetaState() & KeyEvent.META_CTRL_ON) != 0) s.append("+ctrl");
        if ((e.getMetaState() & KeyEvent.META_SHIFT_ON) != 0) s.append("+shift");
        return s.toString();
    }

    private void start() {
        SelectionPanelTest.sInputType = InputType.TYPE_CLASS_TEXT;
        mEdit = new EditText(ApplicationProvider.getApplicationContext());
        mEdit.setText("one two three four five");
        mEdit.setSelection(mEdit.length());
        SelectionPanelTest.sConnection = new InputConnectionWrapper(
                mEdit.onCreateInputConnection(new EditorInfo()), true) {
            @Override
            public boolean sendKeyEvent(KeyEvent e) {
                mSent.add(describe(e));
                return true;
            }
        };
        mController = Robolectric.buildService(SelectionPanelTest.TestIME.class).create();
        mIme = mController.get();
        mToolbar = (KeyboardToolbar) mIme.onCreateInputView();
        if (!mToolbar.isToolbarShown()) mToolbar.toggleToolbar();
        idle();
        mToolbar.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        mToolbar.layout(0, 0, 320, mToolbar.getMeasuredHeight());
        mView = (LatinKeyboardView) ((FrameLayout) mToolbar.getChildAt(1)).getChildAt(0);
        EditorInfo ei = mIme.getCurrentInputEditorInfo();
        mIme.onStartInput(ei, false);
        mIme.onStartInputView(ei, false);
        idle();
        mSent.clear();
    }

    private float[] centre(int code) {
        for (Keyboard.Key k : mView.getKeyboard().getKeys()) {
            if (k.codes != null && k.codes[0] == code) return new float[] {k.x + k.width / 2f, k.y + k.height / 2f};
        }
        throw new AssertionError("no key " + code);
    }

    /** A quick one-finger stroke from {@code from} to {@code to} over 60 ms. */
    private void fling(float[] from, float[] to) {
        long t0 = SystemClock.uptimeMillis();
        mView.onTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, from[0], from[1], 0));
        for (int i = 1; i <= 6; i++) {
            mView.onTouchEvent(MotionEvent.obtain(t0, t0 + i * 10, MotionEvent.ACTION_MOVE,
                    from[0] + (to[0] - from[0]) * i / 6, from[1] + (to[1] - from[1]) * i / 6, 0));
        }
        mView.onTouchEvent(MotionEvent.obtain(t0, t0 + 60, MotionEvent.ACTION_UP, to[0], to[1], 0));
        idle();
    }

    private static final List<String> UNDO = Arrays.asList(
            "down CTRL_LEFT+ctrl", "down Z+ctrl", "up Z+ctrl", "up CTRL_LEFT");
    private static final List<String> REDO = Arrays.asList(
            "down CTRL_LEFT+ctrl", "down SHIFT_LEFT+ctrl+shift", "down Z+ctrl+shift",
            "up Z+ctrl+shift", "up SHIFT_LEFT+ctrl", "up CTRL_LEFT");

    @Test
    public void defaultsAreUndoAndRedo() {
        start();
        assertTrue(mIme.swipeLeft());
        assertEquals(UNDO, mSent);
        mSent.clear();
        assertTrue(mIme.swipeRight());
        assertEquals(REDO, mSent);
        assertTrue(mIme.swipesUndo());
        assertTrue(mIme.mHints.isLearned(HintManager.Tip.UNDO_SWIPE));
        assertTrue("on the practice card", HintManager.isPracticed(prefs(), HintManager.Tip.UNDO_SWIPE));
    }

    @Test
    public void aStoredChoiceStays() {
        prefs().edit().putString(LatinIME.PREF_SWIPE_LEFT, "none")
                .putString(LatinIME.PREF_SWIPE_RIGHT, "lang_next").commit();
        start();
        assertFalse("none: no action", mIme.swipeLeft());
        assertTrue(mSent.isEmpty());
        assertFalse(mIme.swipesUndo());
    }

    @Test
    public void flingAcrossTheKeysUndoesAndTypesNothing() {
        start();
        fling(centre('p'), centre('q'));
        assertEquals(UNDO, mSent);
        mSent.clear();
        fling(centre('a'), centre('l'));
        assertEquals(REDO, mSent);
    }

    @Test
    public void undoSaysWhatHappenedForAMoment() {
        start();
        mIme.swipeLeft();
        assertEquals("Undo", mToolbar.gestureLabelStatus());
        assertEquals("Swipe right to redo", mToolbar.gestureLabelHint());
        mIme.swipeRight();
        assertEquals("Redo", mToolbar.gestureLabelStatus());
        assertEquals("Swipe left to undo again", mToolbar.gestureLabelHint());
        ShadowLooper.idleMainLooper(KeyboardToolbar.FLASH_MS + 50, TimeUnit.MILLISECONDS);
        assertNull(mToolbar.gestureLabelStatus());
    }

    @Test
    public void flickLeftFromBackspaceIsNotAnUndo() throws Exception {
        start();
        java.lang.reflect.Field f = LatinIME.class.getDeclaredField("mDeleteSwipeField");
        f.setAccessible(true);
        f.setBoolean(mIme, true);
        float[] del = centre(Keyboard.KEYCODE_DELETE);
        fling(del, new float[] {del[0] - 200, del[1]});
        for (String e : mSent) assertFalse("no undo: " + mSent, e.contains("Z+ctrl"));
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

    @Test
    public void undoButtonTwiceShowsTheSwipeTip() {
        start();
        mIme.mHints.learned(HintManager.Tip.SWIPE_ACTIONS);
        LinearLayout bar = (LinearLayout) mToolbar.getChildAt(0);
        ((ViewGroup) bar.getChildAt(0)).getChildAt(0).performClick(); // selection panel
        idle();
        TextView undo = find(mToolbar.getChildAt(1), mIme.getString(R.string.toolbar_undo));
        undo.performClick();
        assertFalse(mToolbar.tipShown());
        undo.performClick();
        assertEquals("Tip: swipe left across the keys to undo, right to redo.",
                mToolbar.tipStrip().text().toString());
        assertTrue(mToolbar.tipShown());
        mIme.swipeLeft();
        assertFalse("used: gone", mToolbar.tipShown());
    }

    @Test
    public void noUndoTipWhenTheSwipesDoSomethingElse() {
        prefs().edit().putString(LatinIME.PREF_SWIPE_LEFT, "none").commit();
        start();
        for (int i = 0; i < 3; i++) mIme.mHints.onUndoOtherwise(mIme.swipesUndo());
        assertFalse(mToolbar.tipShown());
    }
}
