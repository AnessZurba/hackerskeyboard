package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The colour themes: which ids they cover, and how keys look with and without borders. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class KeyboardThemeTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void colourThemesAndImageThemes() {
        for (int id : new int[] {4, 5, 7, 8, 9, 10}) {
            assertNotNull("colour theme " + id, KeyboardTheme.forId(mContext, id));
        }
        for (int id : new int[] {0, 1, 2, 3, 6}) {
            assertNull("image theme " + id, KeyboardTheme.forId(mContext, id));
        }
    }

    /** With borders, a plain key is filled; without, it shows the keyboard behind it. */
    @Test
    public void keyBordersToggle() {
        for (int id : new int[] {4, 5, 7, 8, 9, 10}) {
            KeyboardTheme t = KeyboardTheme.forId(mContext, id);
            int filled = centre(t.keyBackground(mContext, true), new int[0]);
            int bare = centre(t.keyBackground(mContext, false), new int[0]);
            assertEquals("theme " + id + " key", t.key | 0xFF000000, filled);
            assertEquals("theme " + id + " no border", 0, bare);
            // Pressed and active keys still light up without borders.
            assertEquals(t.keyPressed | 0xFF000000, centre(t.keyBackground(mContext, false),
                    new int[] {android.R.attr.state_pressed}));
            assertEquals(t.keyActive | 0xFF000000, centre(t.keyBackground(mContext, false),
                    new int[] {android.R.attr.state_checkable, android.R.attr.state_checked}));
        }
    }

    @Test
    public void highContrastHasOutlinesAndReadableLabels() {
        KeyboardTheme t = KeyboardTheme.forId(mContext, KeyboardTheme.HIGH_CONTRAST);
        assertNotEquals(0, t.keyStroke);
        assertEquals(0xFFFFFFFF, t.text);
        assertEquals(0xFF000000, t.background);
    }

    private static int centre(Drawable d, int[] state) {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        d.setState(state);
        d.setBounds(0, 0, 60, 60);
        d.draw(new Canvas(b));
        return b.getPixel(30, 30);
    }
}
