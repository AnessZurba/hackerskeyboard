package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class LayoutOutputTest {
    private static Keyboard.Key withLabel(Keyboard kb, String label) {
        for (Keyboard.Key k : kb.getKeys()) if (k.label != null && label.equals(k.label.toString())) return k;
        return null;
    }

    /** Shift on the Fn layer's numpad types Arabic-Indic digits. */
    @Test
    public void fnDigitsShiftToArabicIndic() {
        Context c = ApplicationProvider.getApplicationContext();
        for (int layout : new int[] {R.xml.kbd_full_fn, R.xml.kbd_full_swap_fn}) {
            Keyboard kb = new LatinKeyboard(c, layout);
            for (int d = 0; d <= 9; d++) {
                Keyboard.Key k = withLabel(kb, Integer.toString(d));
                assertNotNull("digit " + d, k);
                assertEquals('0' + d, k.getPrimaryCode(false, false));
                assertEquals(0x0660 + d, k.getPrimaryCode(false, true));
            }
        }
    }

    /** The Fn numpad: comma under 1, 0 under 2; Num Lock and Scroll Lock keys of their own. */
    @Test
    public void fnNumpadBottomRow() {
        Context c = ApplicationProvider.getApplicationContext();
        for (int layout : new int[] {R.xml.kbd_full_fn, R.xml.kbd_full_swap_fn}) {
            Keyboard kb = new LatinKeyboard(c, layout);
            Keyboard.Key zero = withLabel(kb, "0"), one = withLabel(kb, "1"), two = withLabel(kb, "2"),
                    comma = withLabel(kb, ",");
            assertEquals("0 under 2", two.x, zero.x);
            assertEquals(two.width, zero.width);
            assertEquals("comma under 1", one.x, comma.x);
            assertEquals(zero.y, comma.y);
            assertNotNull(withLabel(kb, "NumL"));
            assertNotNull(withLabel(kb, "ScrL"));
        }
    }

    /**
     * Fitting the keyboard into a narrower width (landscape beside a cutout) scales every key
     * as drawn, so no gap opens (the left Shift once kept a short stored width).
     */
    @Test
    public void narrowerKeyboardKeepsProportions() {
        Context c = ApplicationProvider.getApplicationContext();
        for (int layout : new int[] {R.xml.kbd_full, R.xml.kbd_full_fn, R.xml.kbd_full_swap}) {
            Keyboard kb = new LatinKeyboard(c, layout, 0, LatinIME.sKeyboardSettings.keyboardHeightPercent);
            java.util.List<Keyboard.Key> keys = kb.getKeys();
            int[] x = new int[keys.size()], w = new int[keys.size()];
            for (int i = 0; i < keys.size(); i++) {
                x[i] = keys.get(i).x;
                w[i] = keys.get(i).width;
            }
            float scale = 0.9f;
            kb.setKeyboardWidth(Math.round(kb.getMinWidth() * scale));
            for (int i = 0; i < keys.size(); i++) {
                Keyboard.Key k = keys.get(i);
                assertTrue("x " + k.x + " vs " + x[i] * scale, Math.abs(k.x - x[i] * scale) <= 1);
                assertTrue("width " + k.width + " vs " + w[i] * scale, Math.abs(k.width - w[i] * scale) <= 1);
            }
        }
    }

    /** Hebrew resources are found whether the locale says "he" or the legacy "iw". */
    @Test
    public void hebrewLayoutForHeAndIw() {
        Context c = ApplicationProvider.getApplicationContext();
        for (String lang : new String[] {"he", "iw"}) {
            android.content.res.Configuration conf =
                    new android.content.res.Configuration(c.getResources().getConfiguration());
            conf.setLocale(new java.util.Locale(lang));
            Context hc = c.createConfigurationContext(conf);
            Keyboard kb = new LatinKeyboard(hc, R.xml.kbd_qwerty);
            assertNotNull(lang + ": Hebrew letter qof", withLabel(kb, "\u05e7"));
        }
    }

    /** The numpad (number and phone fields) has backspace at the top right. */
    @Test
    public void phoneBackspaceTopRight() {
        Context c = ApplicationProvider.getApplicationContext();
        for (int layout : new int[] {R.xml.kbd_phone, R.xml.kbd_phone_symbols}) {
            Keyboard kb = new LatinKeyboard(c, layout);
            Keyboard.Key del = null;
            int right = 0, top = Integer.MAX_VALUE;
            for (Keyboard.Key k : kb.getKeys()) {
                right = Math.max(right, k.x + k.width);
                top = Math.min(top, k.y);
                if (k.codes != null && k.codes[0] == Keyboard.KEYCODE_DELETE) del = k;
            }
            assertNotNull(del);
            assertEquals("top row", top, del.y);
            assertEquals("right edge", right, del.x + del.width);
        }
    }

    /** The Arabic lam-alef keys type two letters, not presentation-form ligatures. */
    @Test
    @Config(qualifiers = "ar")
    public void arabicLamAlefIsTwoLetters() {
        Context c = ApplicationProvider.getApplicationContext();
        Keyboard kb = new LatinKeyboard(c, R.xml.kbd_full);
        Keyboard.Key lamAlef = withLabel(kb, "لا");
        assertNotNull(lamAlef);
        assertEquals("لا", lamAlef.getOutputText().toString());
        kb.setShiftState(Keyboard.SHIFT_ON);
        assertEquals("لآ", lamAlef.getOutputText().toString());
        // Keys whose shifted form is one character still type through their code.
        Keyboard.Key fa = withLabel(kb, "ف");
        assertNotNull(fa);
        assertEquals("لإ", fa.getOutputText().toString());
        kb.setShiftState(Keyboard.SHIFT_OFF);
        assertNull(fa.getOutputText());
    }
}
