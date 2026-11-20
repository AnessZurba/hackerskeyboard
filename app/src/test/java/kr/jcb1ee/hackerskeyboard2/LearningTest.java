/*
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.UserDictionary;
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
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import kr.jcb1ee.hackerskeyboard2.suggest.LearnedWords;
import kr.jcb1ee.hackerskeyboard2.suggest.SuggestTestSupport;
import kr.jcb1ee.hackerskeyboard2.suggest.SuggestionEngine;

/**
 * Suggestions stage 4 in the keyboard, with the real decoder: Android's personal dictionary,
 * learning typed words (thresholds, the fields and settings that keep it off), saving and
 * forgetting from the strip, next-word suggestions, and what stays out of backups.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = SuggestTestSupport.QUALIFIERS)
public class LearningTest {
    private static final int MULTI_LINE = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE;

    /** Android's personal dictionary, as the system's provider answers the current keyboard. */
    public static class FakeUserDictionary extends ContentProvider {
        static final List<String[]> sWords = new ArrayList<>(); // word, frequency, locale

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] args,
                String sortOrder) {
            MatrixCursor c = new MatrixCursor(new String[] {UserDictionary.Words.WORD,
                    UserDictionary.Words.FREQUENCY, UserDictionary.Words.LOCALE});
            for (String[] w : sWords) c.addRow(new Object[] {w[0], Integer.valueOf(w[1]), w[2]});
            return c;
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            sWords.add(new String[] {values.getAsString(UserDictionary.Words.WORD), "250",
                    values.getAsString(UserDictionary.Words.LOCALE)});
            getContext().getContentResolver().notifyChange(UserDictionary.Words.CONTENT_URI, null);
            return uri;
        }

        @Override
        public int delete(Uri uri, String selection, String[] args) {
            return 0;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] args) {
            return 0;
        }
    }

    private EditText mEdit;
    private org.robolectric.android.controller.ServiceController<WordStripTest.EngineIME> mController;
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
        FakeUserDictionary.sWords.clear();
        LearnedWords.forgetAll(context());
    }

    @After
    public void tearDown() {
        stop();
        if (mToolbar != null && !mToolbar.isToolbarShown()) mToolbar.toggleToolbar();
        if (mToolbar != null) mToolbar.showChips(Collections.<View>emptyList());
        prefs().edit().clear().commit();
        SelectionPanelTest.sConnection = null;
        SelectionPanelTest.sInputType = InputType.TYPE_CLASS_TEXT;
        SelectionPanelTest.sEditorInfo = null;
        FakeUserDictionary.sWords.clear();
        LearnedWords.forgetAll(context());
    }

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private static SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(context());
    }

    private void start(int inputType) {
        start(inputType, 0);
    }

    /** A keyboard started on an empty field of {@code inputType} with {@code imeOptions}. */
    private void start(int inputType, int imeOptions) {
        SelectionPanelTest.sInputType = inputType;
        mEdit = new EditText(context());
        // A TYPE_NULL view has no input connection; the keyboard is still told TYPE_NULL.
        mEdit.setInputType(inputType == InputType.TYPE_NULL ? InputType.TYPE_CLASS_TEXT : inputType);
        mEdit.setImeOptions(imeOptions);
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
        mController = Robolectric.buildService(WordStripTest.EngineIME.class).create();
        mIme = mController.get();
        mIme.onCreateInputView();
        mToolbar = mIme.mKeyboardSwitcher.getInputContainer();
        if (!mToolbar.isToolbarShown()) mToolbar.toggleToolbar();
        mToolbar.showChips(Collections.<View>emptyList());
        EditorInfo ei = mIme.getCurrentInputEditorInfo();
        mIme.onStartInput(ei, false);
        mIme.onStartInputView(ei, false);
        settle();
    }

    private void startIn(String language, int inputType) {
        prefs().edit().putString(LatinIME.PREF_SELECTED_LANGUAGES, language)
                .putString(LatinIME.PREF_INPUT_LANGUAGE, language).commit();
        start(inputType);
    }

    private void stop() {
        if (mController != null) {
            mIme.onFinishInputView(true);
            mController.destroy();
        }
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
        selectionChanged(before);
    }

    private void selectionChanged(int before) {
        android.text.Editable text = mEdit.getText();
        mIme.onUpdateSelection(before, before, mEdit.getSelectionStart(), mEdit.getSelectionEnd(),
                BaseInputConnection.getComposingSpanStart(text),
                BaseInputConnection.getComposingSpanEnd(text));
        settle();
    }

    private void settle() {
        idle();
        if (mIme.wordQueue() != null) assertTrue(mIme.wordQueue().flush(5000));
        idle();
        if (mIme.wordQueue() != null) assertTrue(mIme.wordQueue().flush(5000));
        idle();
    }

    private boolean shifted() {
        int s = mIme.mKeyboardSwitcher.getInputView().getShiftState();
        return s != Keyboard.SHIFT_OFF;
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

    private List<String> stripWords() {
        SuggestionStrip strip = mToolbar.suggestionStrip();
        return Arrays.asList(strip.wordAt(SuggestionStrip.LEFT), strip.wordAt(SuggestionStrip.MIDDLE),
                strip.wordAt(SuggestionStrip.RIGHT));
    }

    /** Taps the strip's view showing {@code word}. */
    private void tapWord(String word) {
        int slot = stripWords().indexOf(word);
        assertTrue(word + " in " + stripWords(), slot >= 0);
        int before = mEdit.getSelectionStart();
        mToolbar.suggestionStrip().slotView(slot).performClick();
        idle();
        selectionChanged(before);
    }

    private void longPressWord(String word) {
        int slot = stripWords().indexOf(word);
        assertTrue(word + " in " + stripWords(), slot >= 0);
        mToolbar.suggestionStrip().slotView(slot).performLongClick();
        idle();
    }

    private SuggestionEngine.WordSource source(String word) {
        return mIme.wordQueue().source(word);
    }

    private static void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    // ------------------------------------------------------------------ personal dictionary

    private void personalWord(String word, String locale) {
        FakeUserDictionary.sWords.add(new String[] {word, "250", locale});
    }

    @Test
    public void personalDictionaryWordsAreSuggestedAndNotCorrected() {
        Robolectric.setupContentProvider(FakeUserDictionary.class, "user_dictionary");
        personalWord("Zorblax", null);
        personalWord("helo", "en");
        personalWord("Grüßli", "de");
        prefs().edit().putBoolean(LatinIME.PREF_AUTO_CORRECTION, true).commit();
        start(MULTI_LINE);
        type("zorbla");
        assertTrue(stripWords().toString(), stripWords().contains("Zorblax"));
        start(MULTI_LINE);
        // A personal word typed as is stays, even with auto-correction on ("helo" alone
        // would become "hello").
        type("helo ");
        assertEquals("Helo |", field());
        assertEquals(SuggestionEngine.WordSource.PERSONAL_DICTIONARY, source("helo"));
        assertEquals("another language's word", SuggestionEngine.WordSource.NONE,
                source("Grüßli"));
    }

    @Test
    public void personalDictionaryChangesArriveWhileTyping() {
        Robolectric.setupContentProvider(FakeUserDictionary.class, "user_dictionary");
        start(MULTI_LINE);
        assertEquals(SuggestionEngine.WordSource.NONE, source("Quuxly"));
        ContentValues v = new ContentValues();
        v.put(UserDictionary.Words.WORD, "Quuxly");
        v.put(UserDictionary.Words.LOCALE, "en_US");
        context().getContentResolver().insert(UserDictionary.Words.CONTENT_URI, v);
        settle();
        assertEquals(SuggestionEngine.WordSource.PERSONAL_DICTIONARY, source("Quuxly"));
    }

    @Test
    public void withoutTheProviderNothingBreaks() {
        // No personal dictionary (not the current keyboard, or none on the device).
        start(MULTI_LINE);
        type("helo");
        assertTrue(stripWords().contains("Hello"));
    }

    @Test
    public void longPressAPersonalWordSaysWhereItIs() {
        Robolectric.setupContentProvider(FakeUserDictionary.class, "user_dictionary");
        personalWord("Zorblax", null);
        start(MULTI_LINE);
        type("zorbla");
        longPressWord("Zorblax");
        SuggestionStrip strip = mToolbar.suggestionStrip();
        assertTrue(strip.promptShown());
        assertTrue(strip.promptText().getText().toString(),
                strip.promptText().getText().toString().contains("Android's personal dictionary"));
        assertEquals("Open", strip.promptAction().getText().toString());
        // Typing on: the words are back.
        type("x");
        assertFalse(strip.promptShown());
    }

    // ------------------------------------------------------------------ learning

    @Test
    public void aNewWordIsLearnedFromItsSecondUse() {
        prefs().edit().putBoolean(LatinIME.PREF_AUTO_CORRECTION, true).commit();
        start(MULTI_LINE);
        type("zorbex ");
        assertEquals(SuggestionEngine.WordSource.NONE, source("zorbex"));
        type("zorbex ");
        assertEquals(SuggestionEngine.WordSource.LEARNED, source("zorbex"));
        type("zorbe");
        assertTrue(stripWords().toString(), stripWords().contains("zorbex"));
        type("x ");
        assertEquals("Zorbex zorbex zorbex |", field());
    }

    @Test
    public void learnedWordsOutliveTheKeyboard() {
        start(MULTI_LINE);
        type("zorbex zorbex ");
        stop();
        start(MULTI_LINE);
        assertEquals(SuggestionEngine.WordSource.LEARNED, source("zorbex"));
    }

    @Test
    public void nothingIsLearnedWhereTheFieldSaysNo() {
        // Incognito tabs and private chats ask for no personalized learning.
        start(MULTI_LINE, androidx.core.view.inputmethod.EditorInfoCompat
                .IME_FLAG_NO_PERSONALIZED_LEARNING);
        type("zorbex zorbex zorbex ");
        assertEquals("suggestions still shown", "Zorbex zorbex zorbex |", field());
        type("zorbe");
        assertTrue(mToolbar.suggestionsShown());
        assertEquals(SuggestionEngine.WordSource.NONE, source("zorbex"));
        // Nor is tapping the typed word a save there.
        type("x");
        tapWord("zorbex");
        assertNull(mToolbar.gestureLabelStatus());
        assertEquals(SuggestionEngine.WordSource.NONE, source("zorbex"));

        for (int type : new int[] {
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_NULL}) {
            start(type);
            type("zorbex zorbex zorbex ");
            start(MULTI_LINE);
            assertEquals("type " + type, SuggestionEngine.WordSource.NONE, source("zorbex"));
        }
    }

    @Test
    public void fieldsThatAllowLearning() {
        EditorInfo ei = new EditorInfo();
        ei.inputType = MULTI_LINE;
        assertTrue(LearnedWords.allowedIn(ei));
        ei.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE;
        assertTrue(LearnedWords.allowedIn(ei));
        ei.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        assertFalse(LearnedWords.allowedIn(ei));
        ei.imeOptions = 0;
        for (int variation : new int[] {InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_FILTER}) {
            ei.inputType = InputType.TYPE_CLASS_TEXT | variation;
            assertFalse("variation " + variation, LearnedWords.allowedIn(ei));
        }
        ei.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        assertFalse(LearnedWords.allowedIn(ei));
        ei.inputType = InputType.TYPE_NULL;
        assertFalse(LearnedWords.allowedIn(ei));
        ei.inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        assertFalse(LearnedWords.allowedIn(ei));
        assertFalse(LearnedWords.allowedIn(null));
    }

    @Test
    public void nothingIsLearnedWithTheSettingOff() {
        prefs().edit().putBoolean(LatinIME.PREF_LEARN_WORDS, false).commit();
        start(MULTI_LINE);
        type("zorbex zorbex zorbex zorbe");
        tapWord("zorbe"); // not saved either
        assertNull(mToolbar.gestureLabelStatus());
        assertEquals(SuggestionEngine.WordSource.NONE, source("zorbex"));
        assertEquals(SuggestionEngine.WordSource.NONE, source("zorbe"));
    }

    @Test
    public void aRevertedAutoCorrectionIsNotLearnedAsAPair() {
        prefs().edit().putBoolean(LatinIME.PREF_AUTO_CORRECTION, true).commit();
        start(MULTI_LINE);
        // "the helo" -> "the hello", reverted with Backspace to "helo", twice.
        type("the helo \b ");
        type("the helo \b ");
        assertEquals("The helo the helo |", field());
        type("the ");
        assertFalse("no pair the -> hello: " + stripWords(), stripWords().contains("hello"));
    }

    // ------------------------------------------------------------------ save and forget

    @Test
    public void tappingTheTypedWordSavesIt() {
        start(MULTI_LINE);
        type("flobbertap");
        SuggestionStrip strip = mToolbar.suggestionStrip();
        assertEquals("the typed non-word is on the left", "Flobbertap",
                strip.wordAt(SuggestionStrip.LEFT));
        tapWord("Flobbertap");
        assertEquals("Flobbertap |", field());
        assertEquals("Saved", mToolbar.gestureLabelStatus());
        // Saved as typed apart from the sentence's capital, and at once.
        assertEquals(SuggestionEngine.WordSource.LEARNED, source("flobbertap"));
        type("flobberta");
        assertTrue(stripWords().toString(), stripWords().contains("flobbertap"));
        stop();
        start(MULTI_LINE);
        assertEquals(SuggestionEngine.WordSource.LEARNED, source("flobbertap"));
    }

    @Test
    public void aDictionaryWordIsNotSavedByTappingIt() {
        start(MULTI_LINE);
        type("hello");
        tapWord("Hello");
        assertNull(mToolbar.gestureLabelStatus());
    }

    @Test
    public void longPressALearnedWordToForgetIt() {
        start(MULTI_LINE);
        type("zorbex zorbex zorbe");
        longPressWord("zorbex");
        SuggestionStrip strip = mToolbar.suggestionStrip();
        assertTrue(strip.promptShown());
        assertEquals("Remove “zorbex” from suggestions?", strip.promptText().getText().toString());
        assertEquals("Remove", strip.promptAction().getText().toString());
        strip.promptAction().performClick();
        settle();
        assertFalse(strip.promptShown());
        assertEquals("Removed", mToolbar.gestureLabelStatus());
        assertEquals(SuggestionEngine.WordSource.NONE, source("zorbex"));
        assertFalse(stripWords().toString(), stripWords().contains("zorbex"));
        assertEquals("the word typed so far stays", "Zorbex zorbex zorbe|", field());
    }

    @Test
    public void longPressADictionaryWordOnlySaysSo() {
        start(MULTI_LINE);
        type("helo");
        longPressWord("Hello");
        SuggestionStrip strip = mToolbar.suggestionStrip();
        assertTrue(strip.promptShown());
        assertEquals(View.GONE, strip.promptAction().getVisibility());
        strip.promptText().performClick(); // never mind
        assertFalse(strip.promptShown());
    }

    @Test
    public void longPressTheTypedNonWordOffersToSaveIt() {
        start(MULTI_LINE);
        type("the flobbertap");
        longPressWord("flobbertap");
        SuggestionStrip strip = mToolbar.suggestionStrip();
        assertEquals("Save", strip.promptAction().getText().toString());
        strip.promptAction().performClick();
        settle();
        assertEquals("The flobbertap |", field());
        assertEquals(SuggestionEngine.WordSource.LEARNED, source("flobbertap"));
    }

    @Test
    public void forgetLearnedWordsFromTheSettings() {
        start(MULTI_LINE);
        type("zorbex zorbex flobbertap");
        tapWord("flobbertap");
        assertEquals(SuggestionEngine.WordSource.LEARNED, source("zorbex"));
        LearnedWords.forgetAll(context()); // what the confirmed setting does
        assertEquals(SuggestionEngine.WordSource.NONE, source("zorbex"));
        assertEquals(SuggestionEngine.WordSource.NONE, source("flobbertap"));
        assertFalse(new java.io.File(context().getFilesDir(), "learning/en/saved.txt").exists());
    }

    // ------------------------------------------------------------------ next word

    @Test
    public void nextWordsAfterAWordAndASpace() {
        start(MULTI_LINE);
        type("good morning. good morning. good ");
        assertTrue("next words shown", mToolbar.suggestionsShown());
        assertEquals("morning", mToolbar.suggestionStrip().wordAt(SuggestionStrip.MIDDLE));
        tapWord("morning");
        assertEquals("Good morning. Good morning. Good morning |", field());
        // Backspace: gone.
        type("good ");
        assertTrue(mToolbar.suggestionsShown());
        type("\b");
        assertFalse(mToolbar.suggestionsShown());
    }

    @Test
    public void nextWordsGoWithAnyOtherKeyOrACursorMove() {
        start(MULTI_LINE);
        type("good morning good morning good ");
        assertTrue(mToolbar.suggestionsShown());
        type("1");
        assertFalse("a digit ends them", mToolbar.suggestionsShown());
        type(" good ");
        assertTrue(mToolbar.suggestionsShown());
        // A tap elsewhere in the text.
        int before = mEdit.getSelectionStart();
        mEdit.setSelection(0);
        SystemClockAdvance.by(1000);
        selectionChanged(before);
        assertFalse("a cursor move ends them", mToolbar.suggestionsShown());
    }

    @Test
    public void typingAWordAfterOneUsesThePair() {
        start(MULTI_LINE);
        type("good flobbertap good flobbertap good flobb");
        assertTrue(stripWords().toString(), stripWords().contains("flobbertap"));
    }

    @Test
    public void noNextWordsWithTheSettingOff() {
        prefs().edit().putBoolean(LatinIME.PREF_NEXT_WORD, false).commit();
        start(MULTI_LINE);
        type("good morning good morning good ");
        assertFalse(mToolbar.suggestionsShown());
    }

    @Test
    public void noNextWordsWhereNothingIsSuggested() {
        start(MULTI_LINE);
        type("good morning good morning ");
        start(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        type("good ");
        assertFalse(mToolbar.suggestionsShown());
    }

    @Test
    public void hebrewNextWords() {
        startIn("iw", MULTI_LINE);
        type("בוקר טוב בוקר טוב בוקר ");
        assertTrue(mToolbar.suggestionsShown());
        SuggestionStrip strip = mToolbar.suggestionStrip();
        assertEquals("טוב", strip.wordAt(SuggestionStrip.MIDDLE));
        assertTrue(strip.isRightToLeft());
        tapWord("טוב");
        assertEquals("בוקר טוב בוקר טוב בוקר טוב |", field());
    }

    @Test
    public void arabicLearnsAWordMissingFromTheDictionary() {
        startIn("ar", MULTI_LINE);
        // A made-up word typed twice, then offered.
        type("زقطبونة زقطبونة زقطبو");
        assertTrue(stripWords().toString(), stripWords().contains("زقطبونة"));
        assertTrue(mToolbar.suggestionStrip().isRightToLeft());
    }

    // ------------------------------------------------------------------ privacy

    @Test
    public void learnedWordsStayOutOfBackups() throws Exception {
        for (int xml : new int[] {R.xml.data_extraction_rules, R.xml.backup_rules}) {
            List<String> excluded = new ArrayList<>();
            int sections;
            try (XmlResourceParser p = context().getResources().getXml(xml)) {
                for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT; e = p.next()) {
                    if (e != XmlPullParser.START_TAG) continue;
                    if (p.getName().equals("exclude")) {
                        excluded.add(p.getAttributeValue(null, "domain") + ":"
                                + p.getAttributeValue(null, "path"));
                    }
                }
            }
            // Wherever the clipboard history is kept out (every section), so is learning.
            sections = Collections.frequency(excluded, "file:clipboard_history.json");
            assertTrue(excluded.toString(), sections > 0);
            assertEquals(excluded.toString(), sections,
                    Collections.frequency(excluded, "file:learning/"));
            assertEquals(sections, Collections.frequency(excluded, "database:auto_dict.db"));
            assertEquals(sections, Collections.frequency(excluded, "database:userbigram_dict.db"));
        }
        assertEquals("learning",
                LearnedWords.directory(context(), "en_US").getParentFile().getName());
    }

    /** Moves the uptime clock on (so a cursor move counts as the user's, not the keyboard's). */
    private static final class SystemClockAdvance {
        static void by(long ms) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
                    java.time.Duration.ofMillis(ms));
        }
    }
}
