package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
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
