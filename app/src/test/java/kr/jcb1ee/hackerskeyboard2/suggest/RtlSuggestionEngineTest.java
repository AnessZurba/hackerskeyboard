package kr.jcb1ee.hackerskeyboard2.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The Hebrew and Arabic dictionaries on their own 5-row layouts. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = SuggestTestSupport.QUALIFIERS)
public class RtlSuggestionEngineTest {
    private static AospSuggestionEngine sHebrew, sArabic;
    private static KeyGeometry sHebrewKeys, sArabicKeys;

    @AfterClass
    public static void close() {
        if (sHebrew != null) sHebrew.close();
        if (sArabic != null) sArabic.close();
        sHebrew = sArabic = null;
        sHebrewKeys = sArabicKeys = null;
    }

    private static List<String> hebrew(String typed) {
        if (sHebrew == null) {
            sHebrew = SuggestTestSupport.engine("iw");
            sHebrewKeys = SuggestTestSupport.fullLayout("iw");
        }
        return words(sHebrew.suggestions(typed, null, null, null, sHebrewKeys, 5));
    }

    private static List<String> arabic(String typed) {
        if (sArabic == null) {
            sArabic = SuggestTestSupport.engine("ar");
            sArabicKeys = SuggestTestSupport.fullLayout("ar");
        }
        return words(sArabic.suggestions(typed, null, null, null, sArabicKeys, 5));
    }

    private static List<String> words(List<Suggestion> s) {
        List<String> out = new ArrayList<>();
        for (Suggestion x : s) out.add(x.word);
        return out;
    }

    @Test
    public void dictionaryNamesForTheAppsLanguageCodes() {
        assertEquals(Arrays.asList("iw"), AospSuggestionEngine.dictionaryNames("iw"));
        assertEquals(Arrays.asList("iw"), AospSuggestionEngine.dictionaryNames("he"));
        assertEquals(Arrays.asList("iw_il", "iw"), AospSuggestionEngine.dictionaryNames("he_IL"));
        assertEquals(Arrays.asList("ar"), AospSuggestionEngine.dictionaryNames("ar"));
        assertEquals(Arrays.asList("en_us", "en"), AospSuggestionEngine.dictionaryNames("en_US"));
        assertEquals(Arrays.asList("de_ne", "de"), AospSuggestionEngine.dictionaryNames("de_NE"));
    }

    @Test
    public void headers() {
        hebrew("של");
        arabic("في");
        assertEquals("iw", sHebrew.getHeader().get("locale"));
        assertEquals("main:iw", sHebrew.getHeader().get("dictionary"));
        assertEquals("ar", sArabic.getHeader().get("locale"));
        assertEquals("main:ar", sArabic.getHeader().get("dictionary"));
    }

    @Test
    public void layoutsHaveTheirLetters() {
        hebrew("של");
        arabic("في");
        for (char c : "קראטוןםפשדגכעיחלךףזסבהנמצתץ".toCharArray()) {
            assertNotNull("Hebrew " + c, sHebrewKeys.keyFor(c));
        }
        for (char c : "ضصثقفغعهخحجدشسيبلاتنمكطئءؤرىةوزظذ".toCharArray()) {
            assertNotNull("Arabic " + c, sArabicKeys.keyFor(c));
        }
        // Shifted letters are not keys of their own.
        assertNull(sArabicKeys.keyFor('أ'));
    }

    @Test
    public void hebrewFinalLetters() {
        // The ordinary form typed at the end finds the word with the final form...
        assertEquals("שלום", hebrew("שלומ").get(0));
        assertEquals("העולם", hebrew("העולמ").get(0));
        assertEquals("איך", hebrew("איכ").get(0));
        assertEquals("ניתן", hebrew("ניתנ").get(0));
        // ...and a final form typed inside a word finds the ordinary one.
        assertTrue(hebrew("שלוםי").toString(), hebrew("שלוםי").contains("שלומי"));
        // The words themselves come first.
        assertEquals("שלום", hebrew("שלום").get(0));
        assertTrue(sHebrew.isValidWord("שלום"));
        assertFalse(sHebrew.isValidWord("שלומ"));
    }

    @Test
    public void hebrewNiqqudIsIgnored() {
        // שָׁלוֹם with qamats, shin dot and holam.
        assertEquals("שלום", hebrew("שָׁלוֹם").get(0));
        assertTrue(sHebrew.isValidWord("שָׁלוֹם"));
    }

    @Test
    public void arabicAlefForms() {
        // Hamza-less spellings are in the corpus too: the far more frequent usual spelling
        // goes first, the typed word next to it.
        assertEquals("أهمية", arabic("اهمية").get(0));
        assertTrue(arabic("اهمية").contains("اهمية"));
        assertEquals("أكثر", arabic("اكثر").get(0));
        // Typed with the wrong hamza.
        assertTrue(arabic("إهمية").toString(), arabic("إهمية").contains("أهمية"));
        // A word that is itself the more frequent one stays first (كأن "as if" is a word).
        assertEquals("كان", arabic("كان").get(0));
        assertEquals("أن", arabic("أن").get(0));
        assertEquals("إن", arabic("إن").get(0));
    }

    @Test
    public void arabicTaMarbutaAndAlefMaqsura() {
        assertEquals("حكومة", arabic("حكومه").get(0));
        assertEquals("الحكومة", arabic("الحكومه").get(0));
        assertTrue(arabic("مستوي").toString(), arabic("مستوي").contains("مستوى"));
        assertEquals("على", arabic("علي").get(0));
        assertTrue(arabic("علي").contains("علي"));
        assertEquals("الأقصى", arabic("الاقصي").get(0));
    }

    @Test
    public void arabicMarksAndTatweelAreIgnored() {
        // كَتَبَ with fathas, and مـرحبا with a tatweel.
        assertEquals(arabic("كتب").get(0), arabic("كَتَبَ").get(0));
        assertTrue(sArabic.isValidWord("كَتَبَ"));
        assertEquals("مرحبا", arabic("مـرحبا").get(0));
        assertTrue(arabic("َ").isEmpty());
    }

    @Test
    public void wordsComeOutInTheDictionarySpelling() {
        for (String w : arabic("الى")) {
            for (int i = 0; i < w.length(); i++) {
                char c = w.charAt(i);
                assertFalse(w, c >= 0x064B && c <= 0x065F);
            }
        }
        assertTrue(arabic("الى").contains("إلى"));
    }

    @Test
    public void adaptedGeometryPutsVariantsOnTheirBaseKey() {
        arabic("في");
        KeyGeometry a = LetterVariants.ARABIC.adapt(sArabicKeys);
        KeyGeometry.Key alef = a.keyFor('ا');
        for (char v : "أإآ".toCharArray()) {
            KeyGeometry.Key k = a.keyFor(v);
            assertEquals(alef.x, k.x);
            assertEquals(alef.y, k.y);
        }
        assertEquals(a.keyFor('ي').x, a.keyFor('ى').x);
        assertEquals(a.keyFor('ه').y, a.keyFor('ة').y);
        assertTrue(a.keys().size() <= 64); // the decoder's MAX_KEY_COUNT_IN_A_KEYBOARD
        hebrew("של");
        KeyGeometry h = LetterVariants.HEBREW.adapt(sHebrewKeys);
        assertEquals(h.keyFor('מ').x, h.keyFor('ם').x);
        assertEquals(sHebrewKeys.keys().size(), h.keys().size());
        assertTrue(LetterVariants.NONE.adapt(sHebrewKeys) == sHebrewKeys);
    }

    @Test
    public void variantTappedOnItsOwnKeyIsPlacedOnItsBaseKey() {
        hebrew("של");
        KeyGeometry h = LetterVariants.HEBREW.adapt(sHebrewKeys);
        KeyGeometry.Key finalMem = sHebrewKeys.keyFor('ם'), mem = h.keyFor('ם');
        LetterVariants.Input in = LetterVariants.HEBREW.input("שם",
                new int[] {5, finalMem.centerX()}, new int[] {6, finalMem.centerY()}, h);
        assertEquals("שם", in.text);
        assertEquals(5, in.xs[0]);
        assertEquals(mem.centerX(), in.xs[1]);
        assertEquals(mem.centerY(), in.ys[1]);
        in = LetterVariants.HEBREW.input("שם", new int[] {5, mem.x + 1},
                new int[] {6, mem.y + 1}, h);
        assertEquals(mem.x + 1, in.xs[1]);
        // Without positions (a hardware keyboard): only the variant groups' letters get one.
        in = LetterVariants.HEBREW.input("שם", null, null, h);
        assertEquals(-1, in.xs[0]);
        assertEquals(mem.centerX(), in.xs[1]);
    }
}
