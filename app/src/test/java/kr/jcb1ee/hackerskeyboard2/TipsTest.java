package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;
import android.widget.LinearLayout;

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

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/** Tips in the toolbar row: what brings each one up, and what keeps it away. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class TipsTest {
    private EditText mEdit;
    private org.robolectric.android.controller.ServiceController<SelectionPanelTest.TestIME> mController;
    private LatinIME mIme;
    private KeyboardToolbar mToolbar;

    @Before
    public void setUp() {
        Shadows.shadowOf(ApplicationProvider.<android.app.Application>getApplicationContext()
                .getPackageManager()).setSystemFeature(
                        android.content.pm.PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT, true);
        HintManager.resetAll(prefs());
        ClipboardHistory.forget();
    }

    @After
    public void tearDown() {
        if (mToolbar != null && !mToolbar.isToolbarShown()) mToolbar.toggleToolbar();
        if (mController != null) mController.destroy();
        HintManager.resetAll(prefs());
        prefs().edit().remove(HintManager.PREF_SHOW_TIPS).commit();
        SelectionPanelTest.sConnection = null;
        SelectionPanelTest.sInputType = InputType.TYPE_CLASS_TEXT;
        SelectionPanelTest.sEditorInfo = null;
    }

    private static SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
    }

    private static void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    /** A keyboard on a field of {@code inputType} holding some text. */
    private void start(int inputType) {
        SelectionPanelTest.sInputType = inputType;
        mEdit = new EditText(ApplicationProvider.getApplicationContext());
        mEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        mEdit.setText("one two three four five six");
        mEdit.setSelection(mEdit.length());
        SelectionPanelTest.sConnection = new InputConnectionWrapper(
                mEdit.onCreateInputConnection(new EditorInfo()), true) {
            @Override
            public boolean sendKeyEvent(android.view.KeyEvent e) {
                return mEdit.dispatchKeyEvent(e);
            }
        };
        mController = Robolectric.buildService(SelectionPanelTest.TestIME.class).create();
        mIme = mController.get();
        mToolbar = (KeyboardToolbar) mIme.onCreateInputView();
        if (!mToolbar.isToolbarShown()) mToolbar.toggleToolbar();
        idle();
        open(false);
    }

    /** The keyboard opens on the field again ({@code restarting}: the same field). */
    private void open(boolean restarting) {
        mIme.onFinishInputView(true);
        EditorInfo ei = mIme.getCurrentInputEditorInfo();
        mIme.onStartInput(ei, restarting);
        mIme.onStartInputView(ei, restarting);
        idle();
    }

    private void key(int code) {
        mIme.onPress(code);
        mIme.onKey(code, new int[] {code}, 0, 0);
        mIme.onRelease(code);
        idle();
    }

    private void backspaces(int n) {
        for (int i = 0; i < n; i++) key(Keyboard.KEYCODE_DELETE);
    }

    private String tipText() {
        return mToolbar.tipShown() ? mToolbar.tipStrip().text().toString() : null;
    }

    private View firstButtonCell() {
        return ((LinearLayout) mToolbar.getChildAt(0)).getChildAt(0);
    }

    @Test
    public void fiveBackspacesShowTheSwipeTipInTheRow() {
        start(InputType.TYPE_CLASS_TEXT);
        backspaces(4);
        assertNull("not yet", tipText());
        backspaces(1);
        assertEquals("Tip: swipe left from Backspace to delete whole words.", tipText());
        assertEquals("in the buttons' place", View.GONE, firstButtonCell().getVisibility());
        assertEquals(1, mIme.mHints.shownCount(HintManager.Tip.BACKSPACE_SWIPE));
    }

    @Test
    public void tipGoesAfterSixSecondsAndButtonsComeBack() {
        start(InputType.TYPE_CLASS_TEXT);
        backspaces(5);
        assertTrue(mToolbar.tipShown());
        ShadowLooper.idleMainLooper(KeyboardToolbar.TIP_MS + 100, TimeUnit.MILLISECONDS);
        assertFalse(mToolbar.tipShown());
        assertEquals(View.VISIBLE, firstButtonCell().getVisibility());
        assertFalse("timing out is not dismissing", mIme.mHints.isLearned(HintManager.Tip.BACKSPACE_SWIPE));
    }

    @Test
    public void dismissIsForGood() {
        start(InputType.TYPE_CLASS_TEXT);
        backspaces(5);
        mToolbar.tipStrip().closeButton().performClick();
        assertFalse(mToolbar.tipShown());
        assertTrue(mIme.mHints.isLearned(HintManager.Tip.BACKSPACE_SWIPE));
        open(false);
        backspaces(5);
        assertNull(tipText());
    }

    @Test
    public void onlyOneTipPerSession() {
        start(InputType.TYPE_CLASS_TEXT);
        backspaces(5);
        ShadowLooper.idleMainLooper(KeyboardToolbar.TIP_MS + 100, TimeUnit.MILLISECONDS);
        key(LatinKeyboardView.KEYCODE_DPAD_LEFT);
        key(LatinKeyboardView.KEYCODE_DPAD_LEFT);
        assertNull("one per session", tipText());
        open(false);
        key(LatinKeyboardView.KEYCODE_DPAD_LEFT);
        key(LatinKeyboardView.KEYCODE_DPAD_LEFT);
        assertEquals("Tip: hold Space and slide to move the cursor.", tipText());
    }

    @Test
    public void notWhileTheUserHidTheRow() {
        start(InputType.TYPE_CLASS_TEXT);
        mToolbar.toggleToolbar(); // hidden
        backspaces(5);
        assertNull(tipText());
        assertEquals(0, mIme.mHints.shownCount(HintManager.Tip.BACKSPACE_SWIPE));
    }

    @Test
    public void hidingTheRowTakesTheTipAway() {
        start(InputType.TYPE_CLASS_TEXT);
        backspaces(5);
        mToolbar.toggleToolbar();
        assertFalse(mToolbar.tipShown());
    }

    @Test
    public void neverInPasswordTerminalNumberOrPhoneFields() {
        int[] types = {
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_NULL,
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_PHONE,
        };
        for (int type : types) {
            start(type);
            backspaces(5);
            assertNull("type " + type, tipText());
            mController.destroy();
            mController = null;
        }
    }

    @Test
    public void autofillChipsComeFirst() {
        start(InputType.TYPE_CLASS_TEXT);
        backspaces(5);
        assertTrue(mToolbar.tipShown());
        View chip = new View(mIme);
        mToolbar.showChips(Collections.singletonList(chip));
        assertFalse("chips take the row", mToolbar.tipShown());
        open(false);
        mToolbar.showChips(Collections.singletonList(new View(mIme)));
        backspaces(5);
        assertNull("not over chips", tipText());
        mToolbar.showChips(Collections.<View>emptyList());
    }

    @Test
    public void liveSuggestionsComeFirst() {
        start(InputType.TYPE_CLASS_TEXT);
        backspaces(5);
        mToolbar.showSuggestions("a", "b", "c", false);
        assertFalse(mToolbar.tipShown());
        assertTrue(mToolbar.suggestionsShown());
        mToolbar.clearSuggestions();
        open(false);
        mToolbar.showSuggestions("a", "b", "c", false);
        backspaces(5);
        assertNull(tipText());
    }

    @Test
    public void heldBackspaceRepeatsAreNotTaps() {
        start(InputType.TYPE_CLASS_TEXT);
        mIme.mRepeatingKey = true;
        backspaces(8);
        mIme.mRepeatingKey = false;
        assertNull(tipText());
    }

    @Test
    public void usingTheSwipeTakesItsTipAwayForGood() {
        start(InputType.TYPE_CLASS_TEXT);
        backspaces(5);
        assertTrue(mIme.startDeleteSwipe());
        mIme.finishDeleteSwipe(false);
        assertFalse(mToolbar.tipShown());
        assertTrue(mIme.mHints.isLearned(HintManager.Tip.BACKSPACE_SWIPE));
    }

    @Test
    public void showTipsOff() {
        prefs().edit().putBoolean(HintManager.PREF_SHOW_TIPS, false).commit();
        start(InputType.TYPE_CLASS_TEXT);
        backspaces(5);
        assertNull(tipText());
    }

    @Test
    public void thirdOpenShowsTheSwipesTipAndASwipeLearnsIt() {
        start(InputType.TYPE_CLASS_TEXT); // 1
        open(false); // 2
        assertNull(tipText());
        open(true); // a restart is not an open
        assertNull(tipText());
        open(false); // 3
        assertEquals("Tip: swipe up to hide the toolbar, down to close the keyboard.", tipText());
        mIme.swipeUp(); // hides the toolbar (and the tip with it)
        assertTrue(mIme.mHints.isLearned(HintManager.Tip.SWIPE_ACTIONS));
    }

    @Test
    public void languageKeyThreeTimesShowsTheSlideTip() {
        start(InputType.TYPE_CLASS_TEXT);
        for (int i = 0; i < 3; i++) {
            open(false);
            key(LatinKeyboardView.KEYCODE_NEXT_LANGUAGE);
        }
        // The third open showed the swipes tip in its session; reset that for this check.
        mIme.mHints.learned(HintManager.Tip.SWIPE_ACTIONS);
        open(false);
        key(LatinKeyboardView.KEYCODE_NEXT_LANGUAGE);
        assertEquals("Tip: slide across the Space bar to switch language.", tipText());
        mIme.onSpaceLanguageSlide();
        assertFalse(mToolbar.tipShown());
        assertTrue(mIme.mHints.isLearned(HintManager.Tip.LANGUAGE_SLIDE));
    }

    @Test
    public void clipboardOpenedThreeTimesShowsThePinTip() {
        start(InputType.TYPE_CLASS_TEXT);
        mIme.mHints.learned(HintManager.Tip.SWIPE_ACTIONS);
        ClipboardHistory.get(mIme).add("copied text");
        View clipboardButton = ((ViewGroup) firstCell(1)).getChildAt(0);
        for (int i = 0; i < 3; i++) {
            open(false);
            clipboardButton.performClick();
            idle();
            if (i < 2) {
                assertNull(tipText());
                clipboardButton.performClick(); // closes it
            }
        }
        assertEquals("Tip: long-press a clip, then tap the pin to keep it.", tipText());
    }

    private View firstCell(int i) {
        return ((LinearLayout) mToolbar.getChildAt(0)).getChildAt(i);
    }

    @Test
    public void tapsInTheTextShowTheSelectTip() {
        start(InputType.TYPE_CLASS_TEXT);
        mIme.mHints.learned(HintManager.Tip.SWIPE_ACTIONS);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));
        int[] at = {27, 3, 10, 20};
        for (int i = 1; i < at.length; i++) {
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2));
            mIme.onUpdateSelection(at[i - 1], at[i - 1], at[i], at[i], -1, -1);
        }
        assertEquals("Tip: tap Select, then slide on it to extend the selection.", tipText());
    }

    @Test
    public void typingMovesAreNotTapsInTheText() {
        start(InputType.TYPE_CLASS_TEXT);
        mIme.mHints.learned(HintManager.Tip.SWIPE_ACTIONS);
        for (int i = 0; i < 4; i++) {
            key('a'); // the keyboard itself just changed the text
            mIme.onUpdateSelection(10, 10, 20, 20, -1, -1);
        }
        assertNull(tipText());
    }
}
