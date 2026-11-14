package kr.jcb1ee.hackerskeyboard2.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The AOSP decoder with the bundled English dictionary: corrections and completions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = SuggestTestSupport.QUALIFIERS)
public class AospSuggestionEngineTest {
    private static AospSuggestionEngine sEngine;
    private static KeyGeometry sKeys;

    @BeforeClass
    public static void open() {
        sEngine = SuggestTestSupport.englishEngine();
    }

    @AfterClass
    public static void close() {
        if (sEngine != null) sEngine.close();
    }

    private KeyGeometry keys() {
        if (sKeys == null) sKeys = SuggestTestSupport.englishFullLayout();
        return sKeys;
    }

    private List<String> words(String typed) {
        List<String> out = new ArrayList<>();
        for (Suggestion s : sEngine.suggestions(typed, null, null, null, keys(), 5)) {
            out.add(s.word);
        }
        return out;
    }

    @Test
    public void headerIsAospEnglish() {
        Map<String, String> header = sEngine.getHeader();
        assertEquals("en_US", header.get("locale"));
        assertEquals("main:en_us", header.get("dictionary"));
    }

    @Test
    public void correctsTypos() {
        assertEquals("helo", "hello", words("helo").get(0));
        assertEquals("teh", "the", words("teh").get(0));
        assertTrue("recieve: " + words("recieve"), words("recieve").contains("receive"));
        assertTrue("keybaord: " + words("keybaord"), words("keybaord").contains("keyboard"));
    }

    @Test
    public void completesWords() {
        assertEquals("th", "the", words("th").get(0));
        assertTrue("keyb: " + words("keyb"), words("keyb").contains("keyboard"));
    }

    @Test
    public void exactWordComesFirst() {
        List<Suggestion> s = sEngine.suggestions("house", null, null, null, keys(), 5);
        assertEquals("house", s.get(0).word);
        assertTrue(s.get(0).isExactMatch());
        // "Creek" (a place name) is more frequent, but "creek" is a word too.
        assertEquals("creek", words("creek").get(0));
        assertEquals("Creek", words("Creek").get(0));
    }

    @Test
    public void keepsTheTypedCase() {
        assertEquals("The", words("Teh").get(0));
        assertEquals("HELLO", words("HELO").get(0));
    }

    @Test
    public void validWords() {
        assertTrue(sEngine.isValidWord("hello"));
        assertTrue(sEngine.isValidWord("Hello"));
        assertFalse(sEngine.isValidWord("helo"));
        assertFalse(sEngine.isValidWord(""));
    }

    @Test
    public void worksWithoutAKeyboard() {
        List<Suggestion> s = sEngine.suggestions("helo", null, null, null, null, 3);
        assertNotNull(s);
        assertEquals("hello", s.get(0).word);
    }

    @Test
    public void touchPointsSteerTheCorrection() {
        // "cat" typed with the middle tap at the right edge of 'a', towards 's'.
        KeyGeometry k = keys();
        KeyGeometry.Key c = k.keyFor('c'), a = k.keyFor('a'), t = k.keyFor('t');
        int[] xs = {c.centerX(), a.x + a.width - 1, t.centerX()};
        int[] ys = {c.centerY(), a.centerY(), t.centerY()};
        List<Suggestion> s = sEngine.suggestions("cst", xs, ys, null, k, 5);
        List<String> w = new ArrayList<>();
        for (Suggestion x : s) w.add(x.word);
        assertTrue("cst near a: " + w, w.indexOf("cat") >= 0 && w.indexOf("cat") < 3);
    }

    @Test
    public void autoCorrectionConfidence() {
        Suggestion hello = sEngine.suggestions("helo", null, null, null, keys(), 1).get(0);
        assertTrue(sEngine.autoCorrectionConfidence("helo", hello) > 0.185f);
    }
}
