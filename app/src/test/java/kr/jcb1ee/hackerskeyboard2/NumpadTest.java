package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Number keypad: long press on the symbols key flips phone order (1-2-3 on top) and numpad (7-8-9). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class NumpadTest {
    private ServiceController<SelectionPanelTest.TestIME> mController;
    private LatinIME mIme;

    private static SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
    }

    @After
    public void tearDown() {
        if (mController != null) mController.destroy();
        prefs().edit().remove(KeyboardSwitcher.PREF_NUMPAD).commit();
        SelectionPanelTest.sConnection = null;
        SelectionPanelTest.sInputType = InputType.TYPE_CLASS_TEXT;
    }

    private void start(int inputType) {
        SelectionPanelTest.sInputType = inputType;
        EditText edit = new EditText(ApplicationProvider.getApplicationContext());
        SelectionPanelTest.sConnection = new InputConnectionWrapper(
                edit.onCreateInputConnection(new EditorInfo()), true);
        mController = Robolectric.buildService(SelectionPanelTest.TestIME.class).create();
        mIme = mController.get();
        mIme.onCreateInputView();
        EditorInfo ei = mIme.getCurrentInputEditorInfo();
        mIme.onStartInput(ei, false);
        mIme.onStartInputView(ei, false);
        ShadowLooper.idleMainLooper();
    }

    /** The label of the top-left key. */
    private String topLeft() {
        Keyboard kb = mIme.mKeyboardSwitcher.getInputView().getKeyboard();
        Keyboard.Key best = null;
        for (Keyboard.Key k : kb.getKeys()) {
            if (best == null || k.y < best.y || (k.y == best.y && k.x < best.x)) best = k;
        }
        return String.valueOf((char) best.codes[0]);
    }

    private Keyboard.Key symbolsKey() {
        for (Keyboard.Key k : mIme.mKeyboardSwitcher.getInputView().getKeyboard().getKeys()) {
            if (k.codes[0] == Keyboard.KEYCODE_MODE_CHANGE) return k;
        }
        return null;
    }

    @Test
    public void longPressOnSymbolsFlipsAndIsRemembered() {
        for (int type : new int[] {InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_NUMBER}) {
            start(type);
            assertEquals("phone order first", "1", topLeft());
            LatinKeyboardView view = mIme.mKeyboardSwitcher.getInputView();
            Keyboard.Key sym = symbolsKey();
            assertNotNull(sym);
            assertTrue(view.onLongPress(sym));
            assertEquals("numpad order", "7", topLeft());
            // Remembered the next time a number field opens.
            mController.destroy();
            start(type);
            assertEquals("7", topLeft());
            assertTrue(mIme.mKeyboardSwitcher.getInputView().onLongPress(symbolsKey()));
            assertEquals("back to phone order", "1", topLeft());
            mController.destroy();
            mController = null;
        }
    }

    /** On the keypad the symbols key switches on release, so a long press can flip first. */
    @Test
    public void symbolsKeySwitchesOnRelease() {
        start(InputType.TYPE_CLASS_PHONE);
        int code = Keyboard.KEYCODE_MODE_CHANGE;
        mIme.onPress(code);
        ShadowLooper.idleMainLooper();
        assertEquals("not on press", "1", topLeft());
        mIme.onKey(code, new int[] {code}, 0, 0);
        mIme.onRelease(code);
        ShadowLooper.idleMainLooper();
        assertTrue("symbols after release", !"1".equals(topLeft()));
        // And back to the digits the same way.
        mIme.onPress(code);
        mIme.onKey(code, new int[] {code}, 0, 0);
        mIme.onRelease(code);
        ShadowLooper.idleMainLooper();
        assertEquals("1", topLeft());
    }

    @Test
    public void numpadKeepsBackspaceTopRightAndPlusOnZero() {
        prefs().edit().putBoolean(KeyboardSwitcher.PREF_NUMPAD, true).commit();
        start(InputType.TYPE_CLASS_PHONE);
        Keyboard kb = mIme.mKeyboardSwitcher.getInputView().getKeyboard();
        Keyboard.Key del = null, zero = null;
        int top = Integer.MAX_VALUE, right = 0;
        for (Keyboard.Key k : kb.getKeys()) {
            top = Math.min(top, k.y);
            right = Math.max(right, k.x + k.width);
            if (k.codes[0] == Keyboard.KEYCODE_DELETE) del = k;
            if (k.codes[0] == '0') zero = k;
        }
        assertEquals(top, del.y);
        assertEquals(right, del.x + del.width);
        assertNotNull(zero);
    }
}
