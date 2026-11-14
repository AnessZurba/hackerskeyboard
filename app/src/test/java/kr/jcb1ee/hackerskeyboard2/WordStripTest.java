package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import kr.jcb1ee.hackerskeyboard2.suggest.KeyGeometry;
import kr.jcb1ee.hackerskeyboard2.suggest.Suggestion;
import kr.jcb1ee.hackerskeyboard2.suggest.SuggestionEngine;
import kr.jcb1ee.hackerskeyboard2.suggest.SuggestTestSupport;

/**
 * Word suggestions in the toolbar row, with the real decoder and English dictionary: the strip
 * appearing and going, picking, auto-correction and its undo, and the fields that never get
 * suggestions.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = SuggestTestSupport.QUALIFIERS)
public class WordStripTest {
    /** The test keyboard, with the English engine from the source tree. */
    public static class EngineIME extends SelectionPanelTest.TestIME {
        static int sEnginesOpened;

        @Override
        SuggestionEngine openSuggestionEngine(String locale) {
            if (!locale.startsWith("en")) return null;
            sEnginesOpened++;
            return SuggestTestSupport.englishEngine();
        }
    }

    private static final int MULTI_LINE = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
    private static final int AUTO_CORRECT = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;

    private EditText mEdit;
    private org.robolectric.android.controller.ServiceController<EngineIME> mController;
    private LatinIME mIme;
    private KeyboardToolbar mToolbar;

    @Before
    public void setUp() {
        SuggestTestSupport.englishEngine().close(); // skips without the host library
        Shadows.shadowOf(ApplicationProvider.<android.app.Application>getApplicationContext()
                .getPackageManager()).setSystemFeature(
                        android.content.pm.PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT, true);
        prefs().edit().putBoolean(LatinIME.PREF_WORD_SUGGESTIONS, true)
                .putBoolean(LatinIME.PREF_AUTO_CORRECTION, false).commit();
    }

    @After
    public void tearDown() {
        stop();
        if (mToolbar != null && !mToolbar.isToolbarShown()) mToolbar.toggleToolbar();
        if (mToolbar != null) mToolbar.showChips(Collections.<View>emptyList());
        prefs().edit().remove(LatinIME.PREF_WORD_SUGGESTIONS)
                .remove(LatinIME.PREF_AUTO_CORRECTION).commit();
        SelectionPanelTest.sConnection = null;
        SelectionPanelTest.sInputType = InputType.TYPE_CLASS_TEXT;
        SelectionPanelTest.sEditorInfo = null;
    }

    private static SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(
                ApplicationProvider.getApplicationContext());
    }

    /** A keyboard started on an empty field of {@code inputType}. */
    private void start(int inputType) {
        SelectionPanelTest.sInputType = inputType;
        mEdit = new EditText(ApplicationProvider.getApplicationContext());
        // A TYPE_NULL view has no input connection; the keyboard is still told TYPE_NULL.
        mEdit.setInputType(inputType == InputType.TYPE_NULL ? InputType.TYPE_CLASS_TEXT : inputType);
        EditorInfo appInfo = new EditorInfo();
        SelectionPanelTest.sConnection = new InputConnectionWrapper(
                mEdit.onCreateInputConnection(appInfo), true) {
            @Override
            public boolean sendKeyEvent(android.view.KeyEvent e) {
                return mEdit.dispatchKeyEvent(e);
            }
        };
        SelectionPanelTest.sEditorInfo = appInfo;
        stop();
        mController = Robolectric.buildService(EngineIME.class).create();
        mIme = mController.get();
        mIme.onCreateInputView();
        mToolbar = mIme.mKeyboardSwitcher.getInputContainer();
        if (!mToolbar.isToolbarShown()) mToolbar.toggleToolbar();
        mToolbar.showChips(Collections.<View>emptyList());
        EditorInfo ei = mIme.getCurrentInputEditorInfo();
        mIme.onStartInput(ei, false);
        mIme.onStartInputView(ei, false);
        idle();
    }

    private void stop() {
        if (mController != null) mController.destroy();
        mController = null;
    }

    /** Types {@code text} by tapping each key's centre; '\b' is Backspace. */
    private void type(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int code = c == '\b' ? Keyboard.KEYCODE_DELETE : c;
            int[] xy = keyCentre(Character.toLowerCase(c));
            if (Character.isLetter(c) && shifted()) code = Character.toUpperCase(c);
            tap(code, xy[0], xy[1]);
        }
    }

    private void tap(int code, int x, int y) {
        int before = mEdit.getSelectionStart();
        mIme.onPress(code);
        mIme.onKey(code, new int[] {code}, x, y);
        mIme.onRelease(code);
        idle();
        android.text.Editable text = mEdit.getText();
        mIme.onUpdateSelection(before, before, mEdit.getSelectionStart(), mEdit.getSelectionEnd(),
                BaseInputConnection.getComposingSpanStart(text),
                BaseInputConnection.getComposingSpanEnd(text));
        settle();
    }

    /** Lets the background query finish and its result reach the main thread. */
    private void settle() {
        idle();
        if (mIme.wordQueue() != null) assertTrue(mIme.wordQueue().flush(5000));
        idle();
    }

    private boolean shifted() {
        return mIme.mKeyboardSwitcher.getInputView().getShiftState() != Keyboard.SHIFT_OFF;
    }

    private int[] keyCentre(int code) {
        for (Keyboard.Key k : mIme.mKeyboardSwitcher.getInputView().getKeyboard().getKeys()) {
            if (k.codes != null && k.codes.length > 0 && Character.toLowerCase(k.codes[0]) == code) {
                return new int[] {k.x + k.width / 2, k.y + k.height / 2};
            }
        }
        return new int[] {LatinKeyboardBaseView.NOT_A_TOUCH_COORDINATE,
                LatinKeyboardBaseView.NOT_A_TOUCH_COORDINATE};
    }

    private String field() {
        String t = mEdit.getText().toString();
        int cursor = mEdit.getSelectionStart();
        return t.substring(0, cursor) + "|" + t.substring(cursor);
    }

    private boolean composing() {
        return BaseInputConnection.getComposingSpanStart(mEdit.getText()) >= 0;
    }

    private List<String> stripWords() {
        SuggestionStrip strip = mToolbar.suggestionStrip();
        return Arrays.asList(strip.wordAt(SuggestionStrip.LEFT), strip.wordAt(SuggestionStrip.MIDDLE),
                strip.wordAt(SuggestionStrip.RIGHT));
    }

    private static void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    // ------------------------------------------------------------------ strip

    @Test
    public void stripReplacesTheButtonsWhileAWordIsTyped() {
        start(MULTI_LINE);
        assertNotNull(mIme.wordQueue());
        assertFalse(mToolbar.suggestionsShown());
        type("hel");
        assertTrue("suggestions shown while typing", mToolbar.suggestionsShown());
        assertTrue(composing());
        assertTrue(stripWords().toString(), stripWords().contains("Help")
                || stripWords().contains("Hello"));
        type(" ");
        assertEquals("Hel |", field());
        assertFalse("buttons back after the word", mToolbar.suggestionsShown());
        type("wo");
        assertTrue(mToolbar.suggestionsShown());
        type("\b\b");
        assertEquals("Hel |", field());
        assertFalse("no word, no suggestions", mToolbar.suggestionsShown());
    }

    @Test
    public void bestSuggestionIsInTheMiddle() {
        start(MULTI_LINE);
        type("the");
        assertEquals("the", mToolbar.suggestionStrip().wordAt(SuggestionStrip.MIDDLE).toLowerCase());
    }

    @Test
    public void tappingASuggestionReplacesTheWordAndAddsASpace() {
        start(MULTI_LINE);
        type("helo");
        int slot = stripWords().indexOf("Hello");
        assertTrue(stripWords().toString(), slot >= 0);
        mToolbar.suggestionStrip().slotView(slot).performClick();
        settle();
        assertEquals("Hello |", field());
        assertFalse(composing());
        assertFalse(mToolbar.suggestionsShown());
        // Punctuation after a picked word goes before the space, as with the old strip.
        type(".");
        assertEquals("Hello. |", field());
    }

    @Test
    public void suggestionsKeepTheTypedCapitals() {
        start(InputType.TYPE_CLASS_TEXT);
        type("HELO");
        assertTrue(stripWords().toString(), stripWords().contains("HELLO"));
    }

    // ------------------------------------------------------------------ auto-correction

    @Test
    public void autoCorrectionIsOffByDefault() {
        start(MULTI_LINE);
        type("helo ");
        assertEquals("Helo |", field());
    }

    @Test
    public void autoCorrectionOnSpaceAndBackspaceUndoesIt() {
        prefs().edit().putBoolean(LatinIME.PREF_AUTO_CORRECTION, true).commit();
        start(MULTI_LINE);
        type("becuase");
        assertTrue("pending correction highlighted", mToolbar.suggestionStrip().middleIsCorrection());
        assertEquals("Because", mToolbar.suggestionStrip().wordAt(SuggestionStrip.MIDDLE));
        assertEquals("typed word on the left", "Becuase",
                mToolbar.suggestionStrip().wordAt(SuggestionStrip.LEFT));
        type(" ");
        assertEquals("Because |", field());
        type("\b");
        assertEquals("Becuase|", field());
        assertTrue("back to the typed word, composing", composing());
        type(" ");
        assertEquals("not corrected again", "Becuase |", field());
    }

    @Test
    public void autoCorrectionOnPunctuation() {
        prefs().edit().putBoolean(LatinIME.PREF_AUTO_CORRECTION, true).commit();
        start(MULTI_LINE);
        type("becuase,");
        assertEquals("Because,|", field());
    }

    @Test
    public void validWordsAreNotCorrected() {
        prefs().edit().putBoolean(LatinIME.PREF_AUTO_CORRECTION, true).commit();
        start(MULTI_LINE);
        type("hell ");
        assertEquals("Hell |", field());
        assertFalse(composing());
    }

    @Test
    public void tappingTheTypedWordKeepsIt() {
        prefs().edit().putBoolean(LatinIME.PREF_AUTO_CORRECTION, true).commit();
        start(MULTI_LINE);
        type("helo");
        assertTrue(mToolbar.suggestionStrip().middleIsCorrection());
        mToolbar.suggestionStrip().slotView(SuggestionStrip.LEFT).performClick();
        settle();
        assertEquals("Helo |", field());
    }

    @Test
    public void noAutoCorrectionWhereTheFieldDoesNotAllowIt() {
        prefs().edit().putBoolean(LatinIME.PREF_AUTO_CORRECTION, true).commit();
        // Single line without TYPE_TEXT_FLAG_AUTO_CORRECT: suggestions yes, corrections no.
        start(InputType.TYPE_CLASS_TEXT);
        type("becuase");
        assertTrue(mToolbar.suggestionsShown());
        assertFalse(mToolbar.suggestionStrip().middleIsCorrection());
        type(" ");
        assertEquals("becuase |", field());
        // With the flag it does.
        start(AUTO_CORRECT);
        type("becuase ");
        assertEquals("because |", field());
    }

    @Test
    public void doubleSpacePeriodWithSuggestionsOn() {
        start(MULTI_LINE);
        type("hi  there");
        assertEquals("Hi. There|", field());
    }

    // ------------------------------------------------------------------ fields

    @Test
    public void neverInFieldsWithoutSuggestions() {
        int[] types = {
                InputType.TYPE_NULL,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_PHONE,
                InputType.TYPE_CLASS_DATETIME,
        };
        prefs().edit().putBoolean(LatinIME.PREF_AUTO_CORRECTION, true).commit();
        for (int t : types) {
            start(t);
            type("helo");
            String what = "input type 0x" + Integer.toHexString(t);
            assertFalse(what + ": no suggestions", mToolbar.suggestionsShown());
            assertFalse(what + ": nothing in the strip", mToolbar.suggestionStrip().hasWords());
            assertFalse(what + ": no composing", composing());
            type(" ");
            String text = mEdit.getText().toString().toLowerCase();
            assertFalse(what + ": not corrected", text.contains("ello") || text.contains("elp"));
        }
    }

    @Test
    public void nothingWhenTheSettingIsOff() {
        prefs().edit().putBoolean(LatinIME.PREF_WORD_SUGGESTIONS, false).commit();
        start(MULTI_LINE);
        assertNull(mIme.wordQueue());
        type("helo");
        assertFalse(mToolbar.suggestionsShown());
        assertFalse(composing());
    }

    @Test
    public void turningTheSettingOnAndOff() {
        prefs().edit().putBoolean(LatinIME.PREF_WORD_SUGGESTIONS, false).commit();
        start(MULTI_LINE);
        assertNull(mIme.wordQueue());
        prefs().edit().putBoolean(LatinIME.PREF_WORD_SUGGESTIONS, true).commit();
        assertNotNull(mIme.wordQueue());
        type("helo");
        assertTrue(mToolbar.suggestionsShown());
        prefs().edit().putBoolean(LatinIME.PREF_WORD_SUGGESTIONS, false).commit();
        assertNull(mIme.wordQueue());
        assertEquals("the word is kept", "Helo|", field());
        assertFalse(mToolbar.suggestionsShown());
    }

    @Test
    public void aHiddenToolbarRowIsNotShownForSuggestions() {
        prefs().edit().putBoolean(LatinIME.PREF_AUTO_CORRECTION, true).commit();
        start(MULTI_LINE);
        mToolbar.toggleToolbar(); // the user hides the row
        type("helo");
        assertFalse(mToolbar.isToolbarShown());
        assertFalse(mToolbar.suggestionsShown());
        assertFalse(composing());
        type(" ");
        assertEquals("no invisible corrections", "Helo |", field());
        mToolbar.toggleToolbar(); // shown again: suggestions come back with the next word
        type("wor");
        assertTrue(mToolbar.suggestionsShown());
    }

    @Test
    public void autofillChipsKeepTheRow() {
        start(MULTI_LINE);
        View chip = new View(ApplicationProvider.getApplicationContext());
        mToolbar.showChips(Collections.singletonList(chip));
        type("hel");
        assertFalse("chips first", mToolbar.suggestionsShown());
        assertTrue(mToolbar.suggestionStrip().hasWords());
        mToolbar.showChips(Collections.<View>emptyList());
        assertTrue("suggestions once the chips go", mToolbar.suggestionsShown());
    }

    // ------------------------------------------------------------------ touches

    @Test
    public void tapPositionsAreRecordedInKeyboardCoordinates() {
        start(MULTI_LINE);
        int[] h = keyCentre('h'), i = keyCentre('i');
        tap('h', h[0] + 3, h[1] - 2);
        tap('i', i[0], i[1]);
        WordComposer word = mIme.getCurrentWord();
        assertEquals(h[0] + 3, word.getXs()[0]);
        assertEquals(h[1] - 2, word.getYs()[0]);
        assertEquals(i[0], word.getXs()[1]);
        // A typed key with no touch (e.g. from a popup) has none.
        mIme.onText("s");
        settle();
        assertEquals(LatinKeyboardBaseView.NOT_A_TOUCH_COORDINATE, word.getXs()[2]);
    }

    @Test
    public void geometryFollowsAScaledKeyboard() {
        start(MULTI_LINE);
        LatinKeyboardView view = mIme.mKeyboardSwitcher.getInputView();
        Keyboard kb = view.getKeyboard();
        KeyGeometry before = mIme.keyGeometry();
        view.setAvailableWidth(kb.getMinWidth() * 3 / 4); // landscape cutout
        KeyGeometry after = mIme.keyGeometry();
        KeyGeometry.Key p = after.keyFor('p');
        Keyboard.Key key = null;
        for (Keyboard.Key k : view.getKeyboard().getKeys()) {
            if (k.codes != null && k.codes.length > 0 && k.codes[0] == 'p') key = k;
        }
        assertNotNull(key);
        assertEquals(key.x, p.x);
        assertEquals(key.width, p.width);
        assertTrue(p.x < before.keyFor('p').x);
        // Typing on the scaled keys still finds the word.
        type("helo");
        assertTrue(stripWords().toString(), stripWords().contains("Hello"));
    }

    @Test
    public void wherePressedMatters() {
        // The same keys, touched near different edges, give different suggestions.
        List<String> towardsA = typedWithTouch("cst", 's', 'a');
        List<String> towardsD = typedWithTouch("cst", 's', 'd');
        assertTrue(towardsA + " / " + towardsD,
                towardsD.contains("cost") && !towardsA.contains("cost"));
        List<String> towardsE = typedWithTouch("thw", 'w', 'e');
        List<String> towardsQ = typedWithTouch("thw", 'w', 'q');
        assertTrue(towardsE + " / " + towardsQ,
                towardsE.contains("they") && !towardsQ.contains("they"));
    }

    /** Types {@code word}, tapping {@code key} near its edge towards {@code towards} (still on the key). */
    private List<String> typedWithTouch(String word, char key, char towards) {
        start(InputType.TYPE_CLASS_TEXT);
        int[] a = keyCentre(key), b = keyCentre(towards);
        for (char c : word.toCharArray()) {
            if (c == key) {
                tap(c, (a[0] * 11 + b[0] * 9) / 20, (a[1] * 11 + b[1] * 9) / 20);
            } else {
                type(String.valueOf(c));
            }
        }
        return stripWords();
    }

    // ------------------------------------------------------------------ look

    @Test
    public void stripUsesTheToolbarColours() {
        prefs().edit().putBoolean(LatinIME.PREF_AUTO_CORRECTION, true).commit();
        start(MULTI_LINE);
        type("hel");
        SuggestionStrip strip = mToolbar.suggestionStrip();
        assertEquals(mToolbar.foregroundColour(),
                strip.slotView(SuggestionStrip.LEFT).getCurrentTextColor());
        type("o");
        assertTrue(strip.middleIsCorrection());
        assertEquals(mToolbar.accentColour(),
                strip.slotView(SuggestionStrip.MIDDLE).getCurrentTextColor());
    }

    @Test
    public void rightToLeftWords() {
        start(MULTI_LINE);
        SuggestionStrip strip = mToolbar.suggestionStrip();
        strip.setWords("שלום", "שלומי", "של", false);
        assertTrue(strip.isRightToLeft());
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG,
                strip.slotView(SuggestionStrip.MIDDLE).getTextDirection());
        strip.setWords("مرحبا", null, null, false);
        assertTrue(strip.isRightToLeft());
        strip.setWords("hello", "help", null, false);
        assertFalse(strip.isRightToLeft());
    }

    @Test
    public void stripSlots() {
        List<Suggestion> s = Arrays.asList(new Suggestion("hello", 3, 0),
                new Suggestion("help", 2, 0), new Suggestion("helot", 1, 0));
        assertEquals(Arrays.asList("help", "hello", "helot"),
                Arrays.asList(LatinIME.stripSlots("helo", s, null)));
        assertEquals(Arrays.asList("helo", "hello", "help"),
                Arrays.asList(LatinIME.stripSlots("helo", s, "hello")));
        assertEquals(Arrays.asList(null, "qzx", null),
                Arrays.asList(LatinIME.stripSlots("qzx", Collections.<Suggestion>emptyList(), null)));
    }
}
