package kr.jcb1ee.hackerskeyboard2.suggest;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;

import java.io.File;
import java.util.Locale;

import kr.jcb1ee.hackerskeyboard2.LatinKeyboard;
import kr.jcb1ee.hackerskeyboard2.R;

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
    static final File EN_DICT = new File("src/main/assets/dicts/main_en.dict");

    private SuggestTestSupport() {}

    /** The English engine, or the test is skipped when the native library isn't available. */
    public static AospSuggestionEngine englishEngine() {
        assumeTrue("host libjni_latinime not built: " + NativeLibrary.loadError(),
                NativeLibrary.load());
        AospSuggestionEngine engine = AospSuggestionEngine.open(EN_DICT, "en_US");
        if (engine == null) throw new AssertionError("could not open " + EN_DICT);
        return engine;
    }

    /** The key geometry of the English 5-row PC layout at the test screen size. */
    public static KeyGeometry englishFullLayout() {
        Context c = ApplicationProvider.getApplicationContext();
        Resources res = c.getResources();
        Configuration conf = res.getConfiguration();
        Locale saved = conf.locale;
        conf.locale = new Locale("en");
        res.updateConfiguration(conf, null);
        try {
            // The default height (40%), not the shared setting other tests may have changed.
            LatinKeyboard kb = new LatinKeyboard(c, R.xml.kbd_full, 0, 40.0f);
            return KeyGeometry.fromKeyboard(kb);
        } finally {
            conf.locale = saved;
            res.updateConfiguration(conf, null);
        }
    }
}
