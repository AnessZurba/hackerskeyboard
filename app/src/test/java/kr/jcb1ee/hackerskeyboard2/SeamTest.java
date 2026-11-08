package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertTrue;

import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Plain theme backgrounds lose their top highlight line under the toolbar; others stay. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SeamTest {
    private static View keyboardBackgroundAfterToolbar(int layout) {
        LatinIME ime = Robolectric.buildService(LatinIME.class).create().get();
        KeyboardToolbar toolbar = new KeyboardToolbar(ime);
        LatinKeyboardView kv = (LatinKeyboardView) LayoutInflater.from(ime).inflate(layout, null);
        toolbar.setKeyboardView(kv);
        return kv;
    }

    @Test
    public void plainThemesBecomeFlat() {
        for (int layout : new int[] {R.layout.input_ics, R.layout.input_material_dark,
                R.layout.input_material_black, R.layout.input_material_light}) {
            assertTrue("flat background for " + layout,
                    keyboardBackgroundAfterToolbar(layout).getBackground() instanceof ColorDrawable);
        }
    }

    @Test
    public void translucentThemeKeepsItsBackground() {
        // Its background is already a colour, #c0000000; it must stay translucent.
        ColorDrawable bg = (ColorDrawable)
                keyboardBackgroundAfterToolbar(R.layout.input_trans_neon).getBackground();
        org.junit.Assert.assertEquals(0xC0000000, bg.getColor());
    }
}
