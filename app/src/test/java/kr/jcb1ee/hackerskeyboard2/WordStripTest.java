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
    /** The test keyboard, with the English, Hebrew and Arabic engines from the source tree. */
    public static class EngineIME extends SelectionPanelTest.TestIME {
        static int sEnginesOpened;
        static String sLastLocale;

        @Override
        SuggestionEngine openSuggestionEngine(String locale) {
            if (!locale.startsWith("en") && !locale.startsWith("iw") && !locale.startsWith("he")
                    && !locale.startsWith("ar")) {
                return null;
            }
            sEnginesOpened++;
            sLastLocale = locale;
            return SuggestTestSupport.engine(locale);
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
        // What the tests typed is learned (in the test app's files): none of it carries over.
        kr.jcb1ee.hackerskeyboard2.suggest.LearnedWords.forgetAll(
                ApplicationProvider.getApplicationContext());
        if (mToolbar != null && !mToolbar.isToolbarShown()) mToolbar.toggleToolbar();
        if (mToolbar != null) mToolbar.showChips(Collections.<View>emptyList());
        prefs().edit().remove(LatinIME.PREF_WORD_SUGGESTIONS)
                .remove(LatinIME.PREF_AUTO_CORRECTION)
                .remove(LatinIME.PREF_SELECTED_LANGUAGES)
                .remove(LatinIME.PREF_INPUT_LANGUAGE).commit();
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

    /** Like {@link #start}, with {@code language} as the only input language. */
    private void startIn(String language, int inputType) {
        prefs().edit().putString(LatinIME.PREF_SELECTED_LANGUAGES, language)
                .putString(LatinIME.PREF_INPUT_LANGUAGE, language).commit();
        start(inputType);
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
        // The decoder's best three (the strip shows the typed non-word on the left).
        List<String> best = mToolbar.allSuggestions();
        best.remove(word);
        return best.subList(0, Math.min(3, best.size()));
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
        // A typed word that is no word goes on the left (tap it to keep and save it).
        assertEquals(Arrays.asList("helo", "hello", "help"),
                Arrays.asList(LatinIME.stripSlots("helo", s, null, false)));
        assertEquals(Arrays.asList("help", "hello", "helot"),
                Arrays.asList(LatinIME.stripSlots("helo", s, null, true)));
        assertEquals(Arrays.asList(null, "qzx", null), Arrays.asList(LatinIME.stripSlots("qzx",
                Collections.<Suggestion>emptyList(), null, false)));
    }

    // ------------------------------------------------------------------ Hebrew and Arabic

    @Test
    public void hebrewSuggestions() {
        startIn("iw", MULTI_LINE);
        assertNotNull("Hebrew has a dictionary", mIme.wordQueue());
        assertEquals("iw", EngineIME.sLastLocale);
        type("של");
        assertTrue(mToolbar.suggestionsShown());
        SuggestionStrip strip = mToolbar.suggestionStrip();
        assertTrue(strip.isRightToLeft());
        // The ordinary mem typed at the end: the word with the final form is offered.
        type("ומ");
        assertEquals("שלום", strip.wordAt(SuggestionStrip.MIDDLE));
        strip.slotView(SuggestionStrip.MIDDLE).performClick();
        settle();
        assertEquals("שלום |", field());
    }

    @Test
    public void hebrewKeyGeometry() {
        startIn("iw", MULTI_LINE);
        KeyGeometry keys = mIme.keyGeometry();
        for (char c : "אבגדהוזחטיכךלמםנןסעפףצץקרשת".toCharArray()) {
            assertNotNull("key for " + c, keys.keyFor(c));
        }
    }

    @Test
    public void hebrewAutoCorrectionRestoresTheFinalLetter() {
        prefs().edit().putBoolean(LatinIME.PREF_AUTO_CORRECTION, true).commit();
        startIn("iw", MULTI_LINE);
        type("העולמ ");
        assertEquals("העולם |", field());
        // And a final form typed inside a word.
        type("לםה ");
        assertEquals("העולם למה |", field());
    }

    @Test
    public void heIsHebrewToo() {
        startIn("he", MULTI_LINE);
        assertNotNull(mIme.wordQueue());
        type("שלומ");
        assertEquals("שלום", mToolbar.suggestionStrip().wordAt(SuggestionStrip.MIDDLE));
    }

    @Test
    public void arabicSuggestions() {
        startIn("ar", MULTI_LINE);
        assertNotNull("Arabic has a dictionary", mIme.wordQueue());
        KeyGeometry keys = mIme.keyGeometry();
        for (char c : "ابتثجحخدذرزسشصضطظعغفقكلمنهوي".toCharArray()) {
            assertNotNull("key for " + c, keys.keyFor(c));
        }
        type("حكوم");
        assertTrue(mToolbar.suggestionsShown());
        assertTrue(mToolbar.suggestionStrip().isRightToLeft());
        // Ha typed for ta marbuta: the dictionary's spelling is offered.
        type("ه");
        assertEquals("حكومة", mToolbar.suggestionStrip().wordAt(SuggestionStrip.MIDDLE));
    }

    @Test
    public void arabicAlefWithAndWithoutHamza() {
        startIn("ar", MULTI_LINE);
        // Alef without hamza: the usual spelling goes first, the typed word beside it.
        type("اكثر");
        assertEquals("أكثر", mToolbar.suggestionStrip().wordAt(SuggestionStrip.MIDDLE));
        assertTrue(stripWords().toString(), stripWords().contains("اكثر"));
        type(" ");
        assertEquals("kept as typed (a word, never auto-corrected)", "اكثر |", field());
        // The hamza (shift on the alef key) typed: that word.
        type("أكثر");
        assertEquals("أكثر", mToolbar.suggestionStrip().wordAt(SuggestionStrip.MIDDLE));
    }

    @Test
    public void arabicHarakatAreIgnored() {
        startIn("ar", MULTI_LINE);
        type("حُكومه");
        assertEquals("حكومة", mToolbar.suggestionStrip().wordAt(SuggestionStrip.MIDDLE));
    }

    @Test
    public void arabicPunctuationEndsTheWord() {
        prefs().edit().putBoolean(LatinIME.PREF_AUTO_CORRECTION, true).commit();
        startIn("ar", MULTI_LINE);
        type("حكومه،");
        assertEquals("حكومة،|", field());
        assertFalse(composing());
        assertFalse(mToolbar.suggestionsShown());
        type(" حكومه؟");
        assertEquals("حكومة، حكومة؟|", field());
        type(" حكومه؛");
        assertEquals("حكومة، حكومة؟ حكومة؛|", field());
        // The Latin comma and question mark (shifted keys of the Arabic layout) too.
        type(" حكومه,");
        assertEquals("حكومة، حكومة؟ حكومة؛ حكومة,|", field());
        type(" حكومه?");
        assertEquals("حكومة، حكومة؟ حكومة؛ حكومة, حكومة?|", field());
        // A word that is a word (الحكومه, in the corpus too) is kept.
        type(" الحكومه؟");
        assertEquals("حكومة، حكومة؟ حكومة؛ حكومة, حكومة? الحكومه؟|", field());
    }

    @Test
    public void rightToLeftStripSwapsTheSideSlots() {
        start(MULTI_LINE);
        SuggestionStrip strip = mToolbar.suggestionStrip();
        strip.setWords("hello", "help", "held", false);
        layOut(strip);
        assertTrue("left slot on the left",
                strip.slotView(SuggestionStrip.LEFT).getLeft()
                        < strip.slotView(SuggestionStrip.RIGHT).getLeft());
        strip.setWords("שלומי", "שלום", "של", false);
        layOut(strip);
        assertTrue("second suggestion on the right in RTL",
                strip.slotView(SuggestionStrip.LEFT).getLeft()
                        > strip.slotView(SuggestionStrip.RIGHT).getLeft());
        int middle = strip.slotView(SuggestionStrip.MIDDLE).getLeft();
        assertTrue(middle < strip.slotView(SuggestionStrip.LEFT).getLeft());
        assertTrue(middle > strip.slotView(SuggestionStrip.RIGHT).getLeft());
    }

    private static void layOut(View v) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 1080, 120);
    }

    // ------------------------------------------------------------------ more, and the arrow

    @Test
    public void stripHasMoreAtTheStartAndTheToolbarArrowAtTheEnd() {
        start(MULTI_LINE);
        type("hel");
        SuggestionStrip strip = mToolbar.suggestionStrip();
        assertEquals(strip.moreButton(), strip.getChildAt(0));
        assertEquals(strip.toolbarButton(), strip.getChildAt(strip.getChildCount() - 1));
        assertEquals("More suggestions", strip.moreButton().getContentDescription());
        assertEquals("Show toolbar", strip.toolbarButton().getContentDescription());
        assertTrue(strip.moreButton().isFocusable());
        assertTrue(strip.toolbarButton().isFocusable());
        assertTrue(strip.slotView(SuggestionStrip.MIDDLE).isFocusable());
        assertFalse(mToolbar.moreSuggestionsOpen());
    }

    @Test
    public void moreOpensAPanelOverTheKeysWithMoreCandidates() {
        start(MULTI_LINE);
        type("hel");
        SuggestionStrip strip = mToolbar.suggestionStrip();
        List<String> shown = stripWords();
        assertEquals("few candidates while closed", 6, mIme.wordQuerySize());
        strip.moreButton().performClick();
        assertTrue(mToolbar.moreSuggestionsOpen());
        assertTrue(strip.isExpanded());
        assertEquals("Fewer suggestions", strip.moreButton().getContentDescription());
        assertEquals("more asked for only while open", LatinIME.MORE_QUERY_SIZE,
                mIme.wordQuerySize());
        MoreSuggestionsPanel panel = mToolbar.moreSuggestionsPanel();
        assertNotNull(panel.getParent());
        settle();
        List<String> words = panel.words();
        assertTrue(words.toString(), words.size() > 6);
        // As Samsung: the strip's words stay up there; the panel continues after them.
        for (String w : shown) if (w != null) assertFalse(w, words.contains(w));
        assertTrue("the strip stays", mToolbar.suggestionsShown());
        // The dots are the collapse control now.
        strip.moreButton().performClick();
        assertFalse(mToolbar.moreSuggestionsOpen());
        assertFalse(strip.isExpanded());
        assertEquals(6, mIme.wordQuerySize());
        assertTrue(mToolbar.suggestionsShown());
    }

    @Test
    public void pickingFromThePanelCommitsTheWord() {
        start(MULTI_LINE);
        type("hel");
        mToolbar.suggestionStrip().moreButton().performClick();
        settle();
        MoreSuggestionsPanel panel = mToolbar.moreSuggestionsPanel();
        List<String> words = panel.words();
        String word = words.get(4); // the panel has only words the strip doesn't show
        assertFalse(stripWords().contains(word));
        panel.chip(word).performClick();
        settle();
        assertEquals(word + " |", field());
        assertFalse(composing());
        assertFalse(mToolbar.moreSuggestionsOpen());
        assertEquals(View.VISIBLE, mIme.mKeyboardSwitcher.getInputView().getVisibility());
        assertEquals(6, mIme.wordQuerySize());
    }

    @Test
    public void thePanelClosesWhenTheWordEndsOrTheKeyboardGoes() {
        start(MULTI_LINE);
        type("hel");
        mToolbar.suggestionStrip().moreButton().performClick();
        type(" ");
        assertFalse("a new word", mToolbar.moreSuggestionsOpen());
        type("wor");
        mToolbar.suggestionStrip().moreButton().performClick();
        type("\b\b\b");
        assertFalse("suggestions cleared", mToolbar.moreSuggestionsOpen());
        type("wor");
        mToolbar.suggestionStrip().moreButton().performClick();
        assertTrue(mIme.onKeyDown(android.view.KeyEvent.KEYCODE_BACK,
                new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                        android.view.KeyEvent.KEYCODE_BACK)));
        assertFalse("Back collapses it", mToolbar.moreSuggestionsOpen());
        assertTrue("and keeps the keyboard", composing());
        mToolbar.suggestionStrip().moreButton().performClick();
        assertTrue(mToolbar.moreSuggestionsOpen());
        mIme.onFinishInputView(true);
        assertFalse("keyboard hidden", mToolbar.moreSuggestionsOpen());
        assertNull(mToolbar.moreSuggestionsPanel().getParent());
    }

    @Test
    public void theArrowSwapsInTheToolbarUntilTheNextWord() {
        start(MULTI_LINE);
        type("hel");
        SuggestionStrip strip = mToolbar.suggestionStrip();
        strip.toolbarButton().performClick();
        assertFalse(mToolbar.suggestionsShown());
        assertTrue(mToolbar.wordsDismissed());
        View back = mToolbar.wordsBackButton();
        assertEquals(View.VISIBLE, back.getVisibility());
        assertEquals("Show suggestions", back.getContentDescription());
        assertTrue(back.isFocusable());
        type("p");
        assertFalse("still this word", mToolbar.suggestionsShown());
        assertEquals(View.VISIBLE, back.getVisibility());
        back.performClick();
        assertTrue("words back", mToolbar.suggestionsShown());
        assertEquals(View.GONE, back.getVisibility());
        strip.toolbarButton().performClick();
        assertFalse(mToolbar.suggestionsShown());
        type(" wo");
        assertTrue("the next word has them again", mToolbar.suggestionsShown());
        assertFalse(mToolbar.wordsDismissed());
        assertEquals(View.GONE, back.getVisibility());
    }

    @Test
    public void theArrowClosesThePanel() {
        start(MULTI_LINE);
        type("hel");
        mToolbar.suggestionStrip().moreButton().performClick();
        mToolbar.suggestionStrip().toolbarButton().performClick();
        assertFalse(mToolbar.moreSuggestionsOpen());
        assertEquals(View.VISIBLE, mIme.mKeyboardSwitcher.getInputView().getVisibility());
    }

    @Test
    public void rightToLeftMirrorsTheButtonsAndThePanel() {
        start(MULTI_LINE);
        SuggestionStrip strip = mToolbar.suggestionStrip();
        mToolbar.showSuggestions("שלום", "שלומי", "של",
                false, Arrays.asList("שלומי", "שלום", "של", "שלומות", "שלוש", "שלושה", "שלי"));
        assertEquals("the arrow at the end: on the left", strip.toolbarButton(), strip.getChildAt(0));
        assertEquals(strip.moreButton(), strip.getChildAt(strip.getChildCount() - 1));
        assertEquals(-1f, strip.toolbarButton().getScaleX(), 0f);
        mToolbar.openMoreSuggestions();
        MoreSuggestionsPanel panel = mToolbar.moreSuggestionsPanel();
        assertTrue(panel.isRightToLeft());
        layOutPanel(panel);
        assertTrue("best on the right", panel.chipLeft(0) > panel.chipLeft(1));
        mToolbar.closeMoreSuggestions(false);
        mToolbar.showSuggestions("help", "hello", "held", false,
                Arrays.asList("hello", "help", "held", "helm", "hell", "helps", "helped"));
        assertEquals(strip.moreButton(), strip.getChildAt(0));
        assertEquals(strip.toolbarButton(), strip.getChildAt(strip.getChildCount() - 1));
        assertEquals(1f, strip.toolbarButton().getScaleX(), 0f);
        mToolbar.openMoreSuggestions();
        assertFalse(panel.isRightToLeft());
        layOutPanel(panel);
        assertTrue(panel.chipLeft(0) < panel.chipLeft(1));
    }

    private static void layOutPanel(View v) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 1080, 600);
    }

    @Test
    public void changedWordsAnimateUnlessAnimationsAreOff() {
        start(MULTI_LINE);
        android.content.ContentResolver cr = mIme.getContentResolver();
        try {
            mToolbar.showSuggestions("help", "hello", "held", false);
            SuggestionStrip strip = mToolbar.suggestionStrip();
            assertTrue(SuggestionStrip.animationsEnabled(mIme));
            mToolbar.showSuggestions("help", "hello", "helm", false);
            assertEquals("only the changed word", 1, strip.animatedSlots());
            mToolbar.showSuggestions("help", "hello", "helm", false);
            assertEquals("nothing changed", 0, strip.animatedSlots());
            android.provider.Settings.Global.putFloat(cr,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
            assertFalse(SuggestionStrip.animationsEnabled(mIme));
            mToolbar.showSuggestions("hell", "hello", "help", false);
            assertEquals(0, strip.animatedSlots());
            assertEquals(1f, strip.slotView(SuggestionStrip.RIGHT).getAlpha(), 0f);
            // The panel covers the keys at once, with no reveal.
            strip.moreButton().performClick();
            assertEquals(View.INVISIBLE, mIme.mKeyboardSwitcher.getInputView().getVisibility());
            assertNull(mToolbar.moreSuggestionsPanel().getClipBounds());
            strip.moreButton().performClick();
            assertNull(mToolbar.moreSuggestionsPanel().getParent());
            // The swap to the toolbar, likewise.
            strip.toolbarButton().performClick();
            assertEquals(1f, mToolbar.wordsBackButton().getAlpha(), 0f);
        } finally {
            android.provider.Settings.Global.putFloat(cr,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        }
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    public void landscapeKeepsTheSystemButtonsAfterTheArrow() {
        start(MULTI_LINE);
        type("hel");
        assertTrue(mToolbar.systemButtonsShown());
        int w = mIme.getResources().getDisplayMetrics().widthPixels;
        mToolbar.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        mToolbar.layout(0, 0, w, mToolbar.getMeasuredHeight());
        SuggestionStrip strip = mToolbar.suggestionStrip();
        assertTrue(strip.toolbarButton().getWidth() > 0);
        int arrowRight = strip.getLeft() + strip.toolbarButton().getRight();
        int buttons = 2 * Math.round(44 * mIme.getResources().getDisplayMetrics().density);
        assertTrue("arrow before hide / switch: " + arrowRight + " of " + w,
                arrowRight <= w - buttons);
    }
}
