package kr.jcb1ee.hackerskeyboard2.suggest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Imported dictionaries: the catalogue and its language mapping, checking and storing a
 * picked file, and the engine opening an imported dictionary.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = SuggestTestSupport.QUALIFIERS)
public class DictionaryFilesTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @After
    public void cleanUp() {
        for (DictionaryCatalog.Entry e : DictionaryCatalog.all()) {
            DictionaryFiles.remove(mContext, e.name);
        }
    }

    private static DictionaryCatalog.Entry entry(String name) {
        DictionaryCatalog.Entry e = DictionaryCatalog.byName(name);
        assertNotNull(name, e);
        return e;
    }

    private static InputStream bundled(String name) throws IOException {
        return new FileInputStream(new File(SuggestTestSupport.DICT_DIR, "main_" + name + ".dict"));
    }

    // ---------------------------------------------------------------- the catalogue

    @Test
    public void localesMapToTheirLanguagesDictionary() {
        String[][] cases = {
                {"en", "en"}, {"en_US", "en"}, {"en_GB", "en"}, {"en_CX", "en"},
                {"en_DV", "en"}, {"iw", "iw"}, {"he", "iw"}, {"he_IL", "iw"}, {"ar", "ar"},
                {"de", "de"}, {"de_NE", "de"}, {"cs_QY", "cs"}, {"hu_QY", "hu"},
                {"sk_QY", "sk"}, {"bg_ST", "bg"}, {"ru_PH", "ru"}, {"es_LA", "es"},
                {"es_US", "es"}, {"fr_CA", "fr"}, {"pt", "pt"}, {"pt_PT", "pt_pt"},
                {"pt-BR", "pt"}, {"nb", "nb"}, {"sr", "sr"}, {"uk", "uk"}, {"ca", "ca"},
        };
        for (String[] c : cases) {
            DictionaryCatalog.Entry e = DictionaryCatalog.forLocale(c[0]);
            assertNotNull(c[0], e);
            assertEquals(c[0], c[1], e.name);
        }
        for (String none : new String[] {"ko", "th", "ja", "zh_CN", "fa", "hy", "vi", "lo"}) {
            assertNull(none, DictionaryCatalog.forLocale(none));
        }
        // pt_PT falls back to the Brazilian list when only that one is installed.
        assertEquals(Arrays.asList("pt_pt", "pt"), AospSuggestionEngine.dictionaryNames("pt_PT"));
        assertEquals(Arrays.asList("de_ne", "de"), AospSuggestionEngine.dictionaryNames("de_NE"));
    }

    @Test
    public void onlyEnglishHebrewArabicAreBundled() {
        Set<String> bundled = new HashSet<>();
        for (DictionaryCatalog.Entry e : DictionaryCatalog.all()) {
            File asset = new File(SuggestTestSupport.DICT_DIR, e.fileName());
            assertEquals(e.name, e.bundled, asset.isFile());
            if (e.bundled) bundled.add(e.name);
            else assertNotNull("no published checksum for " + e.name, e.sha256);
        }
        assertEquals(new HashSet<>(Arrays.asList("en", "iw", "ar")), bundled);
        File[] assets = SuggestTestSupport.DICT_DIR.listFiles((d, n) -> n.endsWith(".dict"));
        assertEquals(3, assets.length);
        assertEquals("https://github.com/AnessZurba/hackerskeyboard/releases/download/dicts-v1/"
                + "main_pt_pt.dict", entry("pt_pt").downloadUrl());
    }

    /** Every dictionary is for a language the keyboard lists (InputLanguageSelection). */
    @Test
    public void catalogueLanguagesAreKeyboardLanguages() throws Exception {
        java.lang.reflect.Field f = Class.forName(
                "kr.jcb1ee.hackerskeyboard2.InputLanguageSelection$SettingsFragment")
                .getDeclaredField("KBD_LOCALIZATIONS");
        f.setAccessible(true);
        Set<String> languages = new HashSet<>();
        for (String l : (String[]) f.get(null)) languages.add(l.split("_")[0]);
        for (DictionaryCatalog.Entry e : DictionaryCatalog.all()) {
            assertTrue(e.name + " is no keyboard language", languages.contains(e.language()));
        }
    }

    /** The checksums in the catalogue are those of the files tools/mkdicts.sh makes. */
    @Test
    public void publishedChecksumsMatchTheBuild() throws IOException {
        File sums = new File(SuggestTestSupport.OPTIONAL_DICT_DIR, "SHA256SUMS");
        assumeTrue("not built (./gradlew optionalDicts): " + sums, sums.isFile());
        assertEquals(new String(Files.readAllBytes(sums.toPath()), StandardCharsets.UTF_8).trim(),
                DictionaryCatalog.SHA256SUMS.trim());
    }

    // ---------------------------------------------------------------- the header

    @Test
    public void readsTheHeadersOfTheBundledDictionaries() throws Exception {
        String[][] cases = {{"en", "en_US", "main:en_us"}, {"iw", "iw", "main:iw"},
                {"ar", "ar", "main:ar"}};
        for (String[] c : cases) {
            try (InputStream in = bundled(c[0])) {
                Map<String, String> h = DictionaryFiles.readHeader(in);
                assertEquals(c[1], h.get("locale"));
                assertEquals(c[2], h.get("dictionary"));
            }
        }
        try (InputStream in = bundled("iw")) {
            assertEquals("עברית", DictionaryFiles.readHeader(in).get("description"));
        }
    }

    // ---------------------------------------------------------------- importing

    @Test
    public void importsADictionaryAndTheEngineUsesIt() throws Exception {
        assumeTrue(NativeLibrary.load());
        // The English dictionary with another description in its header ("English (UX)"),
        // to tell it from the bundled one.
        byte[] en = Files.readAllBytes(new File(SuggestTestSupport.DICT_DIR, "main_en.dict").toPath());
        byte[] mine = en.clone();
        int at = indexOf(mine, "English (US)".getBytes(StandardCharsets.US_ASCII));
        assertTrue(at > 0 && at < 200);
        mine[at + 10] = 'X';
        AospSuggestionEngine before = AospSuggestionEngine.open(mContext, "en_GB");
        if (before != null) { // the bundled asset, where the test runtime maps assets
            assertEquals("English (US)", before.getHeader().get("description"));
            before.close();
        }

        DictionaryFiles.Imported imported = DictionaryFiles.install(mContext, entry("en"),
                new ByteArrayInputStream(mine));
        assertEquals("en_US", imported.header.get("locale"));
        assertEquals("English (UX)", imported.header.get("description"));
        assertFalse(imported.verified); // not a published file
        assertTrue(DictionaryFiles.isImported(mContext, "en"));
        assertEquals("English (UX)", DictionaryFiles.header(mContext, "en").get("description"));
        File stored = DictionaryFiles.file(mContext, "en");
        assertEquals(new File(mContext.getFilesDir(), "dicts/main_en.dict"), stored);
        assertArrayEquals(mine, Files.readAllBytes(stored.toPath()));
        assertEquals(1, DictionaryFiles.dir(mContext).list().length); // no temporary left

        AospSuggestionEngine engine = AospSuggestionEngine.open(mContext, "en_GB");
        assertNotNull(engine);
        try {
            assertEquals("English (UX)", engine.getHeader().get("description"));
            assertEquals("hello", engine.suggestions("helo", null, null, null, null, 3).get(0).word);
        } finally {
            engine.close();
        }

        assertTrue(DictionaryFiles.remove(mContext, "en"));
        assertFalse(DictionaryFiles.isImported(mContext, "en"));
        assertNull(DictionaryFiles.header(mContext, "en"));
        AospSuggestionEngine after = AospSuggestionEngine.open(mContext, "en_GB");
        if (after != null) {
            assertEquals("English (US)", after.getHeader().get("description"));
            after.close();
        }
        assertFalse(DictionaryFiles.remove(mContext, "en"));
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    @Test
    public void importsAPublishedDictionaryAsVerified() throws Exception {
        File de = SuggestTestSupport.optionalDict("de");
        DictionaryFiles.Imported imported;
        try (InputStream in = new FileInputStream(de)) {
            imported = DictionaryFiles.install(mContext, entry("de"), in);
        }
        assertTrue(imported.verified);
        assertEquals("de", imported.header.get("locale"));
        assumeTrue(NativeLibrary.load());
        AospSuggestionEngine engine = AospSuggestionEngine.open(mContext, "de_NE");
        assertNotNull(engine);
        try {
            List<String> words = new ArrayList<>();
            for (Suggestion s : engine.suggestions("uber", null, null, null, null, 3)) {
                words.add(s.word);
            }
            assertTrue(words.toString(), words.contains("über"));
        } finally {
            engine.close();
        }
    }

    @Test
    public void rejectsADictionaryForAnotherLanguage() throws Exception {
        try (InputStream in = bundled("iw")) {
            DictionaryFiles.install(mContext, entry("de"), in);
            fail();
        } catch (DictionaryFiles.InvalidDictionaryException e) {
            assertEquals(DictionaryFiles.Problem.WRONG_LANGUAGE, e.problem);
        }
        assertFalse(DictionaryFiles.isImported(mContext, "de"));
        assertEquals(0, DictionaryFiles.dir(mContext).list().length);
    }

    @Test
    public void rejectsFilesThatAreNotDictionaries() throws Exception {
        byte[] random = new byte[100_000];
        new Random(1).nextBytes(random);
        expect(DictionaryFiles.Problem.NOT_A_DICTIONARY, random);
        byte[] text = new byte[50_000];
        Arrays.fill(text, (byte) 'a');
        expect(DictionaryFiles.Problem.NOT_A_DICTIONARY, text);
        expect(DictionaryFiles.Problem.BAD_SIZE, new byte[100]);
        expect(DictionaryFiles.Problem.BAD_SIZE, new byte[0]);

        // The right magic number with a wrong version, or a header running past its end.
        byte[] en = Files.readAllBytes(new File(SuggestTestSupport.DICT_DIR, "main_en.dict").toPath());
        byte[] v4 = en.clone();
        v4[5] = 4;
        expect(DictionaryFiles.Problem.NOT_A_DICTIONARY, v4);
        byte[] header = Arrays.copyOf(en, 8192);
        header[8] = 0; header[9] = 0; header[10] = (byte) 0xFF; header[11] = (byte) 0xFF;
        expect(DictionaryFiles.Problem.NOT_A_DICTIONARY, header);
        assertFalse(DictionaryFiles.isImported(mContext, "en"));
    }

    @Test
    public void keepsTheEarlierImportWhenANewOneIsRejected() throws Exception {
        try (InputStream in = bundled("en")) {
            DictionaryFiles.install(mContext, entry("en"), in);
        }
        expect(DictionaryFiles.Problem.NOT_A_DICTIONARY, new byte[10_000]);
        assertEquals("en_US", DictionaryFiles.header(mContext, "en").get("locale"));
    }

    private void expect(DictionaryFiles.Problem problem, byte[] file) {
        try {
            DictionaryFiles.install(mContext, entry("en"), new ByteArrayInputStream(file));
            fail("accepted as " + problem);
        } catch (DictionaryFiles.InvalidDictionaryException e) {
            assertEquals(e.getMessage(), problem, e.problem);
        }
    }
}
