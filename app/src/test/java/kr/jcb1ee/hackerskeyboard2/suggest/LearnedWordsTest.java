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

package kr.jcb1ee.hackerskeyboard2.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The user's own words with the real decoder: Android's personal dictionary, words learned
 * from typing (AOSP's user history dictionary: thresholds, decay, word pairs), saved and
 * forgotten words, and their files.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = SuggestTestSupport.QUALIFIERS)
public class LearnedWordsTest {
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();

    private AospSuggestionEngine mEngine;
    private File mFiles;

    @Before
    public void setUp() throws Exception {
        mFiles = mFolder.newFolder("files");
        mEngine = open();
    }

    @After
    public void tearDown() {
        if (mEngine != null) mEngine.close();
        BinaryDictionaryUtils.setCurrentTimeForTestNative(-1);
    }

    private AospSuggestionEngine open() {
        AospSuggestionEngine e = SuggestTestSupport.englishEngine();
        e.attachLearnedWords(LearnedWords.directoryFor(mFiles, "en_US"));
        return e;
    }

    private void reopen() {
        mEngine.close();
        mEngine = open();
    }

    private List<String> words(String typed) {
        List<String> out = new ArrayList<>();
        for (Suggestion s : mEngine.suggestions(typed, null, null, null, null, 5)) {
            out.add(s.word);
        }
        return out;
    }

    private List<String> next(String previous) {
        List<String> out = new ArrayList<>();
        for (Suggestion s : mEngine.predictions(WordContext.of(previous, false), 5)) {
            out.add(s.word);
        }
        return out;
    }

    /** Types {@code previous} (if a word) and then {@code word}, as the keyboard learns them. */
    private void type(String previous, String word) {
        if (previous != null && !previous.isEmpty()) {
            mEngine.learn(WordContext.NONE, previous, false);
        }
        mEngine.learn(WordContext.of(previous, false), word, false);
    }

    // ------------------------------------------------------------------ personal dictionary

    @Test
    public void personalDictionaryWordsAreWordsAndSuggested() {
        assertFalse(mEngine.isValidWord("Zorblax"));
        Map<String, Integer> words = new HashMap<>();
        words.put("Zorblax", 160);
        words.put("kubectl", 160);
        mEngine.setPersonalWords(words);
        assertTrue(mEngine.isValidWord("Zorblax"));
        assertTrue(mEngine.isValidWord("kubectl"));
        assertEquals("Zorblax", words("Zorblax").get(0));
        assertTrue(words("zorbla").toString(), words("zorbla").contains("Zorblax"));
        assertTrue(words("kubect").toString(), words("kubect").contains("kubectl"));
        assertEquals(SuggestionEngine.WordSource.PERSONAL_DICTIONARY,
                mEngine.source("kubectl"));
        mEngine.setPersonalWords(Collections.<String, Integer>emptyMap());
        assertFalse(mEngine.isValidWord("kubectl"));
    }

    @Test
    public void aPersonalWordIsNotAutoCorrected() {
        // "helo" alone is corrected to "hello"; as a personal word it is kept.
        SuggestionQueue.Result before = result("helo");
        assertEquals("hello", before.autoCorrection(SuggestionQueue.AUTO_CORRECTION_THRESHOLD));
        mEngine.setPersonalWords(Collections.singletonMap("helo", 160));
        SuggestionQueue.Result after = result("helo");
        assertTrue(after.typedIsWord);
        assertEquals(null, after.autoCorrection(SuggestionQueue.AUTO_CORRECTION_THRESHOLD));
        assertEquals("helo", after.suggestions.get(0).word);
    }

    private SuggestionQueue.Result result(String typed) {
        List<Suggestion> s = mEngine.suggestions(typed, null, null, null, null, 5);
        return new SuggestionQueue.Result(0, typed, s, mEngine.isValidWord(typed),
                s.isEmpty() ? 0f : mEngine.autoCorrectionConfidence(typed, s.get(0)));
    }

    @Test
    public void personalDictionaryLocales() {
        assertTrue(PersonalDictionary.localeMatches(null, "en_US"));
        assertTrue(PersonalDictionary.localeMatches("", "en_US"));
        assertTrue(PersonalDictionary.localeMatches("en", "en_US"));
        assertTrue(PersonalDictionary.localeMatches("en_US", "en_US"));
        assertTrue(PersonalDictionary.localeMatches("en-us", "en_US"));
        assertFalse(PersonalDictionary.localeMatches("en_GB", "en_US"));
        assertFalse(PersonalDictionary.localeMatches("de", "en_US"));
        assertTrue(PersonalDictionary.localeMatches("he", "iw"));
        assertTrue(PersonalDictionary.localeMatches("iw_IL", "he_IL"));
        assertTrue(PersonalDictionary.localeMatches("ar", "ar"));
        assertEquals(160, PersonalDictionary.decoderProbability(250));
        assertEquals(163, PersonalDictionary.decoderProbability(255));
        assertEquals(0, PersonalDictionary.decoderProbability(-3));
    }

    // ------------------------------------------------------------------ learning

    @Test
    public void aNewWordIsSuggestedOnceTypedTwice() {
        assertFalse(mEngine.isValidWord("flobbertap"));
        type("", "flobbertap");
        assertFalse("not after once", mEngine.isValidWord("flobbertap"));
        assertFalse(words("flobbertap").contains("flobbertap")
                && mEngine.source("flobbertap") != SuggestionEngine.WordSource.NONE);
        type("the", "flobbertap");
        assertTrue("after twice", mEngine.isValidWord("flobbertap"));
        assertEquals(SuggestionEngine.WordSource.LEARNED, mEngine.source("flobbertap"));
        assertEquals("flobbertap", words("flobbertap").get(0));
        assertTrue(words("flobberta").toString(), words("flobberta").contains("flobbertap"));
        // Typed as is, it is no longer auto-corrected.
        assertEquals(null, result("flobbertap")
                .autoCorrection(SuggestionQueue.AUTO_CORRECTION_THRESHOLD));
    }

    @Test
    public void notLearned() {
        type(null, "abc123");
        type(null, "abc123");
        type(null, "NASAX");
        type(null, "NASAX");
        type(null, "--");
        type(null, "--");
        assertFalse(mEngine.isValidWord("abc123"));
        assertFalse(mEngine.isValidWord("NASAX"));
        assertFalse(mEngine.isValidWord("nasax"));
    }

    @Test
    public void aCapitalForTheSentenceIsLearnedLowerCase() {
        mEngine.learn(WordContext.SENTENCE_START, "Flobbertap", true);
        mEngine.learn(WordContext.SENTENCE_START, "Flobbertap", true);
        assertTrue(mEngine.isValidWord("flobbertap"));
        assertEquals("flobbertap", words("flobbertap").get(0));
        // A name typed mid-sentence keeps its capital.
        type("met", "Zanzibarr");
        type("met", "Zanzibarr");
        assertEquals("Zanzibarr", words("Zanzibarr").get(0));
        assertFalse(mEngine.isValidWord("zanzibarr"));
    }

    @Test
    public void learnedWordsDecay() {
        int now = LearnedWords.now();
        BinaryDictionaryUtils.setCurrentTimeForTestNative(now);
        type("a", "flobbertap");
        type("a", "flobbertap");
        assertTrue(mEngine.isValidWord("flobbertap"));
        // Unused for a year: forgotten (AOSP's forgetting curve, levels drop every 15 days).
        BinaryDictionaryUtils.setCurrentTimeForTestNative(now + 365 * 24 * 3600);
        mEngine.flushLearnedWords();
        assertFalse(mEngine.isValidWord("flobbertap"));
    }

    // ------------------------------------------------------------------ next word

    @Test
    public void nextWordsFromTypedPairs() {
        assertTrue(next("good").isEmpty());
        type(null, "good");
        type("good", "morning");
        type(null, "good");
        type("good", "morning");
        type("good", "night");
        type("good", "night");
        type("good", "night");
        List<String> after = next("good");
        assertTrue(after.toString(), after.contains("morning"));
        assertTrue(after.toString(), after.contains("night"));
        assertEquals("most frequent first: " + after, "night", after.get(0));
        // The capital of a sentence start doesn't matter.
        List<String> capital = new ArrayList<>();
        for (Suggestion s : mEngine.predictions(WordContext.of("Good", true), 5)) {
            capital.add(s.word);
        }
        assertEquals(after, capital);
        assertTrue(next("bad").isEmpty());
    }

    @Test
    public void newWordsPairUpToo() {
        type("my", "flobbertap");
        type("my", "flobbertap");
        type("my", "flobbertap");
        assertTrue(next("my").toString(), next("my").contains("flobbertap"));
    }

    @Test
    public void unlearnRemovesAPair() {
        type("good", "morning");
        type("good", "morning");
        assertTrue(next("good").contains("morning"));
        mEngine.unlearn(WordContext.of("good", false), "morning", false);
        assertFalse(next("good").contains("morning"));
        assertTrue(mEngine.isValidWord("morning"));
    }

    // ------------------------------------------------------------------ save, forget, files

    @Test
    public void savedWordsAtOnceAndForGood() {
        mEngine.save(WordContext.NONE, "Qwertyuiopz", false);
        assertTrue(mEngine.isValidWord("Qwertyuiopz"));
        assertEquals("Qwertyuiopz", words("Qwertyuiopz").get(0));
        assertEquals(SuggestionEngine.WordSource.LEARNED, mEngine.source("Qwertyuiopz"));
        int now = LearnedWords.now();
        BinaryDictionaryUtils.setCurrentTimeForTestNative(now + 365 * 24 * 3600);
        reopen();
        assertTrue("saved words don't decay", mEngine.isValidWord("Qwertyuiopz"));
    }

    @Test
    public void learnedWordsPersistAcrossReopen() {
        type("good", "flobbertap");
        type("good", "flobbertap");
        type(null, "good");
        type(null, "good");
        reopen();
        assertTrue(mEngine.isValidWord("flobbertap"));
        assertTrue(next("good").toString(), next("good").contains("flobbertap"));
        File dir = LearnedWords.directoryFor(mFiles, "en_US");
        assertTrue(new File(dir, "history").isDirectory());
        assertEquals("en", dir.getName());
        assertEquals("learning", dir.getParentFile().getName());
    }

    @Test
    public void twoEnginesShareTheLearnedWords() {
        AospSuggestionEngine other = open();
        try {
            type(null, "flobbertap");
            type(null, "flobbertap");
            assertTrue(other.isValidWord("flobbertap"));
            other.close();
            other = null;
            assertTrue(mEngine.isValidWord("flobbertap"));
        } finally {
            if (other != null) other.close();
        }
    }

    @Test
    public void forgetALearnedOrSavedWord() {
        type(null, "flobbertap");
        type(null, "flobbertap");
        type("my", "flobbertap");
        mEngine.save(WordContext.NONE, "Qwertyuiopz", false);
        assertTrue(mEngine.forget("flobbertap"));
        assertTrue(mEngine.forget("qwertyuiopz")); // as the strip may show it
        assertFalse(mEngine.isValidWord("flobbertap"));
        assertFalse(mEngine.isValidWord("Qwertyuiopz"));
        assertFalse(next("my").toString(), next("my").contains("flobbertap"));
        assertFalse(mEngine.forget("the")); // a dictionary word: nothing to forget
        assertTrue(mEngine.isValidWord("the"));
        reopen();
        assertFalse(mEngine.isValidWord("flobbertap"));
        assertFalse(mEngine.isValidWord("Qwertyuiopz"));
    }

    @Test
    public void clearForgetsEverything() {
        type(null, "flobbertap");
        type(null, "flobbertap");
        mEngine.save(WordContext.NONE, "Qwertyuiopz", false);
        type("good", "morning");
        type("good", "morning");
        LearnedWords.clearAll(mFiles);
        assertFalse(mEngine.isValidWord("flobbertap"));
        assertFalse(mEngine.isValidWord("Qwertyuiopz"));
        assertTrue(next("good").isEmpty());
        // And it keeps learning afterwards.
        type(null, "flobbertap");
        type(null, "flobbertap");
        assertTrue(mEngine.isValidWord("flobbertap"));
        mEngine.close();
        mEngine = null;
        LearnedWords.clearAll(mFiles);
        assertFalse(new File(mFiles, LearnedWords.DIR + "/en").exists());
    }

    @Test
    public void withoutLearnedWordsNothingIsLearned() {
        AospSuggestionEngine plain = SuggestTestSupport.englishEngine();
        try {
            plain.learn(WordContext.NONE, "flobbertap", false);
            plain.learn(WordContext.NONE, "flobbertap", false);
            assertFalse(plain.isValidWord("flobbertap"));
            assertTrue(plain.predictions(WordContext.of("good", false), 3).isEmpty());
        } finally {
            plain.close();
        }
    }

    // ------------------------------------------------------------------ context

    @Test
    public void contextFromTheTextBefore() {
        assertEquals(WordContext.SENTENCE_START, WordContext.fromTextBefore(""));
        assertEquals(WordContext.SENTENCE_START, WordContext.fromTextBefore("   "));
        assertEquals(WordContext.SENTENCE_START, WordContext.fromTextBefore("Hi there. "));
        assertEquals(WordContext.SENTENCE_START, WordContext.fromTextBefore("Really? "));
        assertEquals(WordContext.SENTENCE_START, WordContext.fromTextBefore("one\n"));
        assertEquals(WordContext.of("there", false), WordContext.fromTextBefore("Hi there "));
        assertEquals(WordContext.of("Hi", true), WordContext.fromTextBefore("Hi "));
        assertEquals(WordContext.of("Hi", true), WordContext.fromTextBefore("Ok. Hi "));
        assertEquals(WordContext.of("don't", false), WordContext.fromTextBefore("I don't "));
        assertEquals(WordContext.NONE, WordContext.fromTextBefore("Hi, "));
        assertEquals(WordContext.NONE, WordContext.fromTextBefore("at 10 "));
        assertEquals(WordContext.NONE, WordContext.fromTextBefore("a (b) "));
        // Hebrew and Arabic.
        assertEquals(WordContext.of("שלום", true),
                WordContext.fromTextBefore("שלום "));
        assertEquals(WordContext.SENTENCE_START,
                WordContext.fromTextBefore("ماذا؟ "));
        assertEquals(WordContext.NONE, WordContext.fromTextBefore("نعم، "));
        assertEquals(WordContext.of("بِسْم", true),
                WordContext.fromTextBefore("بِسْم "));
    }
}
