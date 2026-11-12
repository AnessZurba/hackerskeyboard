package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Loads every keyboard layout for every language the language list offers (5-row and 4-row),
 * the way KeyboardSwitcher does, and records each
 * keyboard's rows of key labels. Guards the layout XML, the per-language key maps and the
 * pseudo-region locales (de_NE, cs_QY, ...) against refactoring (plan Phase 1.6 and 4.5).
 * The golden snapshot is src/test/resources/layout_golden.txt.
 *
 * <p>Regenerate after a deliberate change with
 * {@code ./gradlew testDebugUnitTest --tests '*LayoutLoadTest*' -PupdateGolden} and review the diff.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LayoutLoadTest {
    private static final String GOLDEN = "src/test/resources/layout_golden.txt";

    // The same lists as InputLanguageSelection (KBD_5_ROW and KBD_4_ROW).
    private static final String[] FIVE_ROW = {
            "ar", "bg", "bg_ST", "cs", "cs_QY", "da", "de", "de_NE", "el",
            "en", "en_CX", "en_DV", "en_GB", "es", "es_LA", "fa", "fi", "fr",
            "fr_CA", "hr", "hu", "hu_QY", "hy", "it", "iw", "ko", "lo", "lt",
            "nb", "pt_PT", "ro", "ru", "ru_PH", "si", "sk", "sk_QY", "sl",
            "sr", "sv", "ta", "th", "tr", "uk"
    };
    private static final String[] FOUR_ROW = {
            "ar", "bg", "bg_ST", "cs", "cs_QY", "da", "de", "de_NE", "el",
            "en", "en_CX", "en_DV", "es", "es_LA", "es_US", "fa", "fr", "fr_CA",
            "hr", "hu", "hu_QY", "iw", "nb", "ru", "ru_PH", "sk", "sk_QY",
            "sl", "sr", "sv", "tr", "uk"
    };

    @Test
    public void everyLayoutLoadsAsBefore() throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("# Key labels per row for every layout and language (LayoutLoadTest).\n");
        out.append("# Regenerate with -PupdateGolden.\n");
        out.append("\n## Shared layouts (en)\n");
        Object[][] shared = {
                {"fn (5-row)", R.xml.kbd_full_fn}, {"fn, swapped", R.xml.kbd_full_swap_fn},
                {"5-row, swapped Ctrl/Esc", R.xml.kbd_full_swap},
                {"compact", R.xml.kbd_compact}, {"compact fn", R.xml.kbd_compact_fn},
                {"symbols", R.xml.kbd_symbols}, {"symbols shift", R.xml.kbd_symbols_shift},
                {"phone", R.xml.kbd_phone}, {"phone symbols", R.xml.kbd_phone_symbols},
        };
        for (Object[] s : shared) {
            out.append(describe("en", (String) s[0], (Integer) s[1], 0));
        }
        TreeSet<String> languages = new TreeSet<>(java.util.Arrays.asList(FIVE_ROW));
        languages.addAll(java.util.Arrays.asList(FOUR_ROW));
        for (String lang : languages) {
            out.append("\n## ").append(lang).append('\n');
            if (contains(FIVE_ROW, lang)) {
                out.append(describe(lang, "5-row", R.xml.kbd_full, 0));
            }
            if (contains(FOUR_ROW, lang)) {
                out.append(describe(lang, "4-row", R.xml.kbd_qwerty, KeyboardSwitcher.KEYBOARDMODE_NORMAL));
            }
        }
        File golden = new File(GOLDEN);
        if (System.getProperty("updateGolden") != null) {
            golden.getParentFile().mkdirs();
            Files.write(golden.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }
        String expected = new String(Files.readAllBytes(golden.toPath()), StandardCharsets.UTF_8);
        assertEquals("layouts changed; if deliberate, regenerate with -PupdateGolden",
                expected, out.toString());
    }

    /** Loads one layout in one language, as KeyboardSwitcher.getKeyboard does. */
    private static String describe(String lang, String name, int xml, int mode) {
        Context c = ApplicationProvider.getApplicationContext();
        Resources res = c.getResources();
        Configuration conf = res.getConfiguration();
        Locale saved = conf.locale;
        String[] parts = lang.split("_");
        conf.locale = parts.length > 1 ? new Locale(parts[0], parts[1]) : new Locale(parts[0]);
        res.updateConfiguration(conf, null);
        LatinKeyboard kb;
        try {
            kb = new LatinKeyboard(c, xml, mode, LatinIME.sKeyboardSettings.keyboardHeightPercent);
        } finally {
            conf.locale = saved;
            res.updateConfiguration(conf, null);
        }
        TreeMap<Integer, List<String>> rows = new TreeMap<>();
        for (Keyboard.Key k : kb.getKeys()) {
            rows.computeIfAbsent(k.y, y -> new ArrayList<>()).add(label(k));
        }
        assertTrue(lang + " " + name + " has keys", !rows.isEmpty());
        StringBuilder s = new StringBuilder(name).append(":\n");
        for (List<String> row : rows.values()) {
            s.append("  ").append(String.join(" ", row)).append('\n');
        }
        return s.toString();
    }

    private static String label(Keyboard.Key k) {
        if (k.label != null && k.label.length() > 0) {
            return k.label.toString().replace(' ', '_').replace("\n", "\\n");
        }
        int code = k.codes != null && k.codes.length > 0 ? k.codes[0] : 0;
        return code > 32 ? new String(Character.toChars(code)) : "[" + code + "]";
    }

    private static boolean contains(String[] list, String s) {
        for (String x : list) if (x.equals(s)) return true;
        return false;
    }
}
