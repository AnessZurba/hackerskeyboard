package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
@Config(sdk = 36)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class KeyboardThemeTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void colourThemesAndImageThemes() {
        for (int id : new int[] {4, 5, 7, 8, 9, 10, 11}) {
            assertNotNull("colour theme " + id, KeyboardTheme.forId(mContext, id));
        }
        assertNull("no theme 2 (Stone) any more", KeyboardTheme.forId(mContext, 2));
    }

    /** Removed themes (ICS, Gingerbread, Transparent, ICS dark) become Material Black. */
    @Test
    public void removedThemesBecomeMaterialBlack() {
        for (int id : new int[] {0, 1, 3, 6}) {
            assertEquals(KeyboardTheme.MATERIAL_BLACK, KeyboardTheme.current(id));
        }
        assertEquals(KeyboardTheme.HIGH_CONTRAST, KeyboardTheme.current(KeyboardTheme.HIGH_CONTRAST));
        assertEquals("Stone is replaced by High contrast (light)",
                KeyboardTheme.HIGH_CONTRAST_LIGHT, KeyboardTheme.current(2));
        KeyboardTheme light = KeyboardTheme.forId(mContext, KeyboardTheme.HIGH_CONTRAST_LIGHT);
        assertTrue(light.light);
        assertEquals(0xFF000000, light.keyStroke);
        assertEquals(0xFF000000, light.text);
        android.content.SharedPreferences prefs =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(mContext);
        prefs.edit().putString(KeyboardSwitcher.PREF_KEYBOARD_LAYOUT, "6").commit();
        assertEquals(KeyboardTheme.MATERIAL_BLACK, KeyboardTheme.migrate(prefs));
        assertEquals("7", prefs.getString(KeyboardSwitcher.PREF_KEYBOARD_LAYOUT, null));
        prefs.edit().clear().commit();
        assertEquals("default", KeyboardTheme.MATERIAL_BLACK, KeyboardTheme.migrate(prefs));
    }

    /** With borders, a plain key is filled; without, it shows the keyboard behind it. */
    @Test
    public void keyBordersToggle() {
        for (int id : new int[] {4, 5, 7, 8, 9, 10, 11}) {
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

    private static final int[] STYLED = {KeyboardTheme.TYPEWRITER, KeyboardTheme.KEYCAPS_RETRO,
            KeyboardTheme.KEYCAPS_MIDNIGHT, KeyboardTheme.TERMINAL};

    /** The styled themes have new ids (after 11), never a removed theme's. */
    @Test
    public void styledThemeIds() {
        assertEquals(12, KeyboardTheme.TYPEWRITER);
        assertEquals(13, KeyboardTheme.KEYCAPS_RETRO);
        assertEquals(14, KeyboardTheme.KEYCAPS_MIDNIGHT);
        assertEquals(15, KeyboardTheme.TERMINAL);
        String[] values = mContext.getResources().getStringArray(R.array.keyboard_layout_modes_values);
        String[] names = mContext.getResources().getStringArray(R.array.keyboard_layout_modes);
        assertEquals(values.length, names.length);
        java.util.List<String> listed = java.util.Arrays.asList(values);
        for (int id : STYLED) {
            assertEquals(id, KeyboardTheme.current(id));
            assertTrue(KeyboardTheme.isColourTheme(id));
            KeyboardTheme t = KeyboardTheme.forId(mContext, id);
            assertNotNull("styled theme " + id, t.style);
            assertTrue("listed in settings: " + id, listed.contains(String.valueOf(id)));
            assertEquals(R.layout.input_material_dark, KeyboardSwitcher.layoutFor(id, t));
        }
        for (int id : new int[] {4, 5, 7, 8, 9, 10, 11}) {
            assertNull("flat theme " + id, KeyboardTheme.forId(mContext, id).style);
        }
    }

    /** Every class of styled key draws in every state; the borders setting does not hide it. */
    @Test
    public void styledKeysDrawEveryState() {
        int P = android.R.attr.state_pressed, K = android.R.attr.state_checkable,
                C = android.R.attr.state_checked, E = android.R.attr.state_empty;
        for (int id : STYLED) {
            KeyboardTheme t = KeyboardTheme.forId(mContext, id);
            for (int cls = 0; cls < 3; cls++) {
                for (boolean borders : new boolean[] {true, false}) {
                    String what = "theme " + id + " class " + cls + " borders " + borders;
                    Drawable d = t.keyBackground(mContext, borders, cls);
                    int normal = centre(d, new int[0], KEY_W, KEY_H);
                    int pressed = centre(d, new int[] {P}, KEY_W, KEY_H);
                    int on = centre(d, new int[] {K, C}, KEY_W, KEY_H);
                    assertEquals(what + " opaque face", 0xFF, android.graphics.Color.alpha(normal));
                    assertNear(what + " face colour", t.style.caps[cls].face, normal, 0x30);
                    assertNotEquals(what + " pressed", normal, pressed);
                    assertNotEquals(what + " on", normal, on);
                    assertEquals(what + " empty", 0, centre(d, new int[] {E}, KEY_W, KEY_H));
                    // The label sits on the face: the padding is inside the key.
                    android.graphics.Rect pad = new android.graphics.Rect();
                    d.setState(new int[0]);
                    d.getCurrent().getPadding(pad);
                    assertTrue(what + " padding " + pad, pad.left > 0 && pad.top > 0
                            && pad.left + pad.right < KEY_W && pad.top + pad.bottom < KEY_H);
                }
            }
        }
    }

    /** Keycap colours: plain keys, modifiers, and Esc/Enter. */
    @Test
    public void keyClasses() {
        LatinKeyboard kbd = new LatinKeyboard(mContext, R.xml.kbd_full, 0, 30f);
        java.util.Map<String, Integer> found = new java.util.HashMap<>();
        for (Keyboard.Key k : kbd.getKeys()) {
            int code = k.codes != null && k.codes.length > 0 ? k.codes[0] : 0;
            String name = k.label != null ? k.label.toString() : String.valueOf(code);
            found.put(name, KeyboardTheme.keyClass(k));
        }
        assertEquals(Integer.valueOf(KeyboardTheme.CLASS_NORMAL), found.get("a"));
        assertEquals(Integer.valueOf(KeyboardTheme.CLASS_NORMAL), found.get("1"));
        assertEquals("space", Integer.valueOf(KeyboardTheme.CLASS_NORMAL), found.get("32"));
        assertEquals(Integer.valueOf(KeyboardTheme.CLASS_MODIFIER), found.get("Ctrl"));
        assertEquals(Integer.valueOf(KeyboardTheme.CLASS_MODIFIER), found.get("Alt"));
        assertEquals("shift", Integer.valueOf(KeyboardTheme.CLASS_MODIFIER), found.get("-1"));
        assertEquals("backspace", Integer.valueOf(KeyboardTheme.CLASS_MODIFIER), found.get("-5"));
        assertEquals(Integer.valueOf(KeyboardTheme.CLASS_ACCENT), found.get("Esc"));
        assertEquals("enter", Integer.valueOf(KeyboardTheme.CLASS_ACCENT), found.get("10"));
    }

    private static void assertNear(String what, int expected, int actual, int tolerance) {
        int[] shifts = {16, 8, 0};
        for (int s : shifts) {
            int e = (expected >> s) & 0xFF, a = (actual >> s) & 0xFF;
            assertTrue(what + String.format(": #%08X vs #%08X", expected, actual),
                    Math.abs(e - a) <= tolerance);
        }
    }

    private static final int KEY_W = 90, KEY_H = 130;

    private static int centre(Drawable d, int[] state) {
        return centre(d, state, 60, 60);
    }

    private static int centre(Drawable d, int[] state, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        d.setState(state);
        d.setBounds(0, 0, w, h);
        d.draw(new Canvas(b));
        return b.getPixel(w / 2, h / 2);
    }
}
