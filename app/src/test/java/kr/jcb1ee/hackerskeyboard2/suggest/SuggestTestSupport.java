package kr.jcb1ee.hackerskeyboard2.suggest;

import static org.junit.Assume.assumeTrue;


import java.io.File;

import kr.jcb1ee.hackerskeyboard2.LocaleLayouts;


/**
 * Shared setup for the tests that run the AOSP decoder on the JVM. The native library is the
 * desktop build from the hostSuggestJni Gradle task; without it (no CMake or C++ compiler on
 * the machine) the tests are skipped rather than failed.
 *
 * <p>All such tests must use the same Robolectric config (sdk 36, same qualifiers): a native
 * library can only be bound to one class loader per JVM, and each config gets its own.
 */
public final class SuggestTestSupport {
    public static final String QUALIFIERS = "w411dp-h891dp-xxhdpi";
    static final File DICT_DIR = new File("src/main/assets/dicts");
    /**
     * The optional dictionaries (tools/mkdicts.sh, ./gradlew optionalDicts): root build/dicts,
     * as the Gradle test task says; tests of a language without one there are skipped.
     */
    static final File OPTIONAL_DICT_DIR = new File(System.getProperty(
            "hk.suggest.optionalDicts", "../build/dicts"));

    private SuggestTestSupport() {}

    /** The English engine, or the test is skipped when the native library isn't available. */
    public static AospSuggestionEngine englishEngine() {
        return engine("en_US");
    }

    /**
     * The engine for {@code locale} with the bundled dictionary from the source tree (found as
     * the app finds it), or the test is skipped when the native library isn't available.
     */
    public static AospSuggestionEngine engine(String locale) {
        assumeTrue("host libjni_latinime not built: " + NativeLibrary.loadError(),
                NativeLibrary.load());
        for (String name : AospSuggestionEngine.dictionaryNames(locale)) {
            File dict = new File(DICT_DIR, "main_" + name + ".dict");
            if (!dict.isFile()) dict = new File(OPTIONAL_DICT_DIR, "main_" + name + ".dict");
            if (!dict.isFile()) continue;
            AospSuggestionEngine engine = AospSuggestionEngine.open(dict, locale);
            if (engine == null) throw new AssertionError("could not open " + dict);
            return engine;
        }
        throw new AssertionError("no dictionary for " + locale + " in " + DICT_DIR);
    }

    /**
     * The optional dictionary file {@code name} ("de") from build/dicts, or the test is
     * skipped when it has not been built.
     */
    public static File optionalDict(String name) {
        File f = new File(OPTIONAL_DICT_DIR, "main_" + name + ".dict");
        assumeTrue("not built (./gradlew optionalDicts): " + f, f.isFile());
        return f;
    }

    /** The key geometry of the English 5-row PC layout at the test screen size. */
    public static KeyGeometry englishFullLayout() {
        return fullLayout("en");
    }

    /** The key geometry of {@code locale}'s 5-row PC layout at the test screen size. */
    public static KeyGeometry fullLayout(String locale) {
        return KeyGeometry.fromKeyboard(LocaleLayouts.fullKeyboard(locale));
    }
}
