package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The keyboard backgrounds used to carry a highlight line along their top edge (meant to
 * divide the keys from the suggestion strip), which showed as a seam under the toolbar.
 * Drawn at keyboard size, their top row must be the same colour as their body.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SeamTest {
    @Test
    public void backgroundsHaveNoTopLine() {
        int[] backgrounds = {R.drawable.keyboard_dark_background, R.drawable.keyboard_background,
                R.drawable.keyboard_background_material_dark,
                R.drawable.keyboard_background_material_black,
                R.drawable.keyboard_background_material_light};
        for (int id : backgrounds) {
            Drawable d = ApplicationProvider.getApplicationContext().getDrawable(id);
            Bitmap bmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 400, 200);
            d.draw(new Canvas(bmp));
            int body = bmp.getPixel(200, 100);
            for (int y = 0; y < 8; y++) {
                assertEquals("row " + y + " of " + id, Integer.toHexString(body),
                        Integer.toHexString(bmp.getPixel(200, y)));
            }
        }
    }
}
