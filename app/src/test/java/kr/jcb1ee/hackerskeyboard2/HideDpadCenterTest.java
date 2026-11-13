package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** "Hide the ○ key" drops D-pad Center; its room goes to the space bar (centred) and Fn. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class HideDpadCenterTest {
    @After
    public void reset() {
        LatinIME.sKeyboardSettings.hideDpadCenter = false;
    }

    private static Keyboard.Key find(Keyboard kb, int code) {
        for (Keyboard.Key k : kb.getKeys()) if (k.codes != null && k.codes[0] == code) return k;
        return null;
    }

    @Test
    public void spaceTakesTheRoom() {
        Context c = ApplicationProvider.getApplicationContext();
        int[] layouts = {R.xml.kbd_full, R.xml.kbd_full_swap};
        for (int layout : layouts) {
            LatinIME.sKeyboardSettings.hideDpadCenter = false;
            Keyboard normal = new LatinKeyboard(c, layout);
            Keyboard.Key center = find(normal, LatinKeyboardView.KEYCODE_DPAD_CENTER);
            Keyboard.Key space = find(normal, ' ');
            Keyboard.Key left = find(normal, LatinKeyboardView.KEYCODE_DPAD_LEFT);
            assertNotNull(center);

            LatinIME.sKeyboardSettings.hideDpadCenter = true;
            Keyboard hidden = new LatinKeyboard(c, layout);
            assertNull(find(hidden, LatinKeyboardView.KEYCODE_DPAD_CENTER));
            Keyboard.Key space2 = find(hidden, ' ');
            Keyboard.Key left2 = find(hidden, LatinKeyboardView.KEYCODE_DPAD_LEFT);
            Keyboard.Key fn = find(normal, LatinKeyboardView.KEYCODE_FN);
            Keyboard.Key fn2 = find(hidden, LatinKeyboardView.KEYCODE_FN);
            assertEquals("space x unchanged", space.x, space2.x);
            int grown = (space2.width - space.width) + (fn2.width - fn.width);
            assertEquals("space and Fn share the key and its gap", center.width + center.gap, grown, 2);
            assertEquals("space centred", hidden.getMinWidth() / 2f, space2.x + space2.width / 2f, 3);
            assertEquals("Fn right after space", space2.x + space2.width, fn2.x - fn2.gap, 3);
            assertEquals("arrows stay put", left.x, left2.x);
            assertEquals(normal.getKeys().size() - 1, hidden.getKeys().size());
        }
    }
}
