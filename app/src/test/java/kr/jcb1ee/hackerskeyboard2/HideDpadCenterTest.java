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

/** "Hide the ○ key" drops D-pad Center and gives its room to the space bar. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
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
            assertEquals("space x unchanged", space.x, space2.x);
            int grown = space2.width - space.width;
            assertEquals("space grows by the key and its gap", center.width + center.gap, grown, 2);
            assertEquals("arrows stay put", left.x, left2.x);
            assertEquals(normal.getKeys().size() - 1, hidden.getKeys().size());
        }
    }
}
