package kr.jcb1ee.hackerskeyboard2.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The themes: stored ids and their migration, light/dark families and which variant draws,
 * the custom theme, and how keys and popups look.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class KeyboardThemeTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private SharedPreferences mPrefs;

    @Before
    public void setUp() {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mPrefs.edit().clear().commit();
    }

    @After
    public void tearDown() {
        mPrefs.edit().clear().commit();
    }

    // ------------------------------------------------------------------ ids

    /** Variant ids keep their numbers; 16 (terminal on paper) and 17 (custom) are new. */
    @Test
    public void ids() {
        assertEquals(5, Palettes.MATERIAL_LIGHT);
        assertEquals(7, Palettes.MATERIAL_DARK);
        assertEquals(8, Palettes.MATERIAL_YOU);
        assertEquals(10, Palettes.HIGH_CONTRAST_DARK);
        assertEquals(11, Palettes.HIGH_CONTRAST_LIGHT);
        assertEquals(12, Palettes.TYPEWRITER);
        assertEquals(13, Palettes.KEYCAPS_RETRO);
        assertEquals(14, Palettes.KEYCAPS_MIDNIGHT);
        assertEquals(15, Palettes.TERMINAL);
        assertEquals(16, Palettes.TERMINAL_LIGHT);
        assertEquals(17, Palettes.CUSTOM);
        for (int id : new int[] {5, 7, 8, 10, 11, 12, 13, 14, 15, 16}) {
            assertNotNull("variant " + id, Palettes.forId(mContext, id, false));
            assertEquals(id, Themes.current(id));
        }
        for (int id : new int[] {0, 1, 2, 3, 4, 6, 9}) {
            assertNull("removed " + id, Palettes.forId(mContext, id, false));
        }
    }

    /** The settings list shows one entry per family, by the family's id. */
    @Test
    public void settingsListFamilies() {
        String[] values = mContext.getResources().getStringArray(
                kr.jcb1ee.hackerskeyboard2.R.array.keyboard_layout_modes_values);
        String[] names = mContext.getResources().getStringArray(
                kr.jcb1ee.hackerskeyboard2.R.array.keyboard_layout_modes);
        assertEquals(values.length, names.length);
        assertEquals(ThemeFamily.ALL.length, values.length);
        for (int i = 0; i < values.length; i++) {
            assertEquals(String.valueOf(ThemeFamily.ALL[i].id), values[i]);
        }
        String[] bases = mContext.getResources().getStringArray(
                kr.jcb1ee.hackerskeyboard2.R.array.custom_theme_base_values);
        assertEquals("every family but Custom", values.length - 1, bases.length);
        assertFalse(java.util.Arrays.asList(bases).contains("17"));
    }

    @Test
    public void familiesPairLightAndDark() {
        assertPair(7, 5, 7);
        assertPair(10, 11, 10);
        assertPair(13, 13, 14);
        assertPair(15, 16, 15);
        assertPair(12, 12, 12);
        assertPair(8, 8, 8);
        for (int id : new int[] {5, 11, 16}) {
            assertTrue("light " + id, Palettes.forId(mContext, id, false).isLight());
        }
        // Retro beige keeps its dark plate; its keys are the light ones.
        assertTrue(ThemeSpec.luminance(spec(13).face(KeyKind.NORMAL)) > 0.7f);
        assertTrue(ThemeSpec.luminance(spec(14).face(KeyKind.NORMAL)) < 0.3f);
        for (int id : new int[] {7, 10, 14, 15, 12}) {
            assertFalse("dark " + id, Palettes.forId(mContext, id, true).isLight());
        }
    }

    private static void assertPair(int family, int light, int dark) {
        for (int id : new int[] {family, light, dark}) {
            ThemeFamily f = ThemeFamily.of(id);
            assertEquals(family, f.id);
            assertEquals(light, f.variant(false));
            assertEquals(dark, f.variant(true));
        }
    }

    // ------------------------------------------------------------------ migration

    /** Old Material Dark (4), AMOLED (9) and older removed themes become Material Dark. */
    @Test
    public void removedThemesMigrateToMaterialDark() {
        for (int id : new int[] {0, 1, 3, 4, 6, 9}) {
            assertMigrates(id, 7, Themes.MODE_DARK);
        }
        assertMigrates(2, 10, Themes.MODE_LIGHT); // Stone: High contrast (light)
    }

    /** Someone who picked one half of a pair keeps seeing it (not the system's choice). */
    @Test
    public void pickedVariantIsKept() {
        assertMigrates(5, 7, Themes.MODE_LIGHT);
        assertMigrates(7, 7, Themes.MODE_DARK);
        assertMigrates(10, 10, Themes.MODE_DARK);
        assertMigrates(11, 10, Themes.MODE_LIGHT);
        assertMigrates(13, 13, Themes.MODE_LIGHT);
        assertMigrates(14, 13, Themes.MODE_DARK);
        assertMigrates(15, 15, Themes.MODE_DARK);
        assertMigrates(8, 8, Themes.MODE_SYSTEM); // Material You followed the system already
        assertMigrates(12, 12, Themes.MODE_SYSTEM);
        assertMigrates(99, 7, Themes.MODE_SYSTEM); // unknown (newer version): the default
    }

    @Test
    public void newInstallFollowsTheSystem() {
        assertEquals(7, Themes.migrate(mPrefs));
        assertEquals(Themes.MODE_SYSTEM, mPrefs.getString(Themes.PREF_MODE, null));
    }

    /** Once migrated, a family id leaves the light/dark setting alone. */
    @Test
    public void migrationRunsOnce() {
        mPrefs.edit().putString(Themes.PREF_THEME, "5").commit();
        Themes.migrate(mPrefs);
        mPrefs.edit().putString(Themes.PREF_MODE, Themes.MODE_SYSTEM).commit();
        assertEquals(7, Themes.migrate(mPrefs));
        assertEquals(Themes.MODE_SYSTEM, mPrefs.getString(Themes.PREF_MODE, null));
        // A variant id (say, from an old backup) still says which variant was picked.
        mPrefs.edit().putString(Themes.PREF_THEME, "14").commit();
        assertEquals(13, Themes.migrate(mPrefs));
        assertEquals(Themes.MODE_DARK, mPrefs.getString(Themes.PREF_MODE, null));
    }

    private void assertMigrates(int stored, int family, String mode) {
        mPrefs.edit().clear().putString(Themes.PREF_THEME, String.valueOf(stored)).commit();
        assertEquals("family of " + stored, family, Themes.migrate(mPrefs));
        assertEquals("stored " + stored, String.valueOf(family),
                mPrefs.getString(Themes.PREF_THEME, null));
        assertEquals("mode of " + stored, mode, mPrefs.getString(Themes.PREF_MODE, null));
        // What is drawn is what that id drew.
        int variant = Themes.current(stored);
        boolean dark = Themes.wantsDark(mContext, mPrefs);
        if (!Themes.MODE_SYSTEM.equals(mode)) {
            assertEquals(Palettes.forId(mContext, variant, dark),
                    Themes.forKeyboard(mContext).spec);
        }
    }

    // ------------------------------------------------------------------ light or dark

    @Test
    public void variantFollowsTheSetting() {
        mPrefs.edit().putString(Themes.PREF_THEME, "13").putString(Themes.PREF_MODE,
                Themes.MODE_SYSTEM).commit();
        RuntimeEnvironment.setQualifiers("+notnight");
        assertEquals(spec(13), Themes.forKeyboard(mContext).spec);
        RuntimeEnvironment.setQualifiers("+night");
        assertEquals("system went dark", spec(14), Themes.forKeyboard(mContext).spec);
        mPrefs.edit().putString(Themes.PREF_MODE, Themes.MODE_LIGHT).commit();
        assertEquals("light, whatever the system", spec(13), Themes.forKeyboard(mContext).spec);
        RuntimeEnvironment.setQualifiers("+notnight");
        mPrefs.edit().putString(Themes.PREF_MODE, Themes.MODE_DARK).commit();
        assertEquals("dark, whatever the system", spec(14), Themes.forKeyboard(mContext).spec);
        // Material You honours the setting too.
        mPrefs.edit().putString(Themes.PREF_THEME, "8").commit();
        assertFalse(Themes.forKeyboard(mContext).isLight());
        mPrefs.edit().putString(Themes.PREF_MODE, Themes.MODE_LIGHT).commit();
        assertTrue(Themes.forKeyboard(mContext).isLight());
    }

    /** The keyboard compares looks to know when to redraw (e.g. the system went dark). */
    @Test
    public void sameAs() {
        mPrefs.edit().putString(Themes.PREF_THEME, "7").commit();
        RuntimeEnvironment.setQualifiers("+notnight");
        KeyboardLook before = Themes.forKeyboard(mContext);
        assertTrue(before.sameAs(Themes.forKeyboard(mContext)));
        mPrefs.edit().putString(Themes.PREF_MODE, Themes.MODE_SYSTEM).commit();
        KeyboardLook day = Themes.forKeyboard(mContext);
        RuntimeEnvironment.setQualifiers("+night");
        assertFalse("day and night differ", day.sameAs(Themes.forKeyboard(mContext)));
        mPrefs.edit().putBoolean(Themes.PREF_KEY_BORDERS, false).commit();
        KeyboardLook bare = Themes.forKeyboard(mContext);
        mPrefs.edit().putBoolean(Themes.PREF_KEY_BORDERS, true).commit();
        assertFalse("borders", bare.sameAs(Themes.forKeyboard(mContext)));
        RuntimeEnvironment.setQualifiers("+notnight");
    }

    @Test
    public void settingsThatChangeTheLook() {
        for (String key : new String[] {"pref_keyboard_layout", "pref_theme_mode",
                "pref_key_borders", "pref_custom_theme_base", "pref_custom_theme_dark_key"}) {
            assertTrue(key, Themes.affectsLook(key));
        }
        assertFalse(Themes.affectsLook("pref_hint_mode"));
        assertFalse("the screen's tab", Themes.affectsLook(CustomTheme.PREF_EDITING));
        assertTrue(Themes.affectsPreview(CustomTheme.PREF_EDITING));
    }

    private ThemeSpec spec(int variant) {
        return Palettes.forId(mContext, variant, false);
    }

    // ------------------------------------------------------------------ custom

    @Test
    public void customThemeStorage() {
        assertEquals("default base", 7, CustomTheme.baseFamily(mPrefs));
        mPrefs.edit().putString(CustomTheme.PREF_BASE, "17").commit();
        assertEquals("never itself", 7, CustomTheme.baseFamily(mPrefs));
        mPrefs.edit().putString(CustomTheme.PREF_BASE, "14").commit();
        assertEquals("a variant id means its family", 13, CustomTheme.baseFamily(mPrefs));
        ThemeSpec base = spec(14);
        assertEquals(base, CustomTheme.spec(mContext, mPrefs, true));

        CustomTheme.set(mPrefs, true, ThemeSpec.KEY, 0xFF123456);
        CustomTheme.set(mPrefs, true, ThemeSpec.HINT, 0x80FFFFFF);
        assertEquals("pref_custom_theme_dark_key", CustomTheme.key(true, ThemeSpec.KEY));
        ThemeSpec dark = CustomTheme.spec(mContext, mPrefs, true);
        assertEquals(0xFF123456, dark.c[ThemeSpec.KEY]);
        assertEquals(0x80FFFFFF, dark.c[ThemeSpec.HINT]);
        assertEquals(base.c[ThemeSpec.BACKGROUND], dark.c[ThemeSpec.BACKGROUND]);
        assertEquals(base.shape, dark.shape);
        assertEquals("light untouched", spec(13), CustomTheme.spec(mContext, mPrefs, false));
        assertTrue(CustomTheme.isSet(mPrefs, true, ThemeSpec.KEY));
        assertFalse(CustomTheme.isSet(mPrefs, false, ThemeSpec.KEY));

        CustomTheme.clear(mPrefs, true, ThemeSpec.KEY);
        assertEquals(base.c[ThemeSpec.KEY], CustomTheme.spec(mContext, mPrefs, true).c[ThemeSpec.KEY]);
        CustomTheme.set(mPrefs, false, ThemeSpec.BACKGROUND, 0xFFABCDEF);
        CustomTheme.reset(mPrefs, true);
        assertEquals(base, CustomTheme.spec(mContext, mPrefs, true));
        assertEquals("the other variant keeps its colours", 0xFFABCDEF,
                CustomTheme.spec(mContext, mPrefs, false).c[ThemeSpec.BACKGROUND]);

        // Picked as the theme, the keyboard draws it.
        mPrefs.edit().putString(Themes.PREF_THEME, "17")
                .putString(Themes.PREF_MODE, Themes.MODE_LIGHT).commit();
        assertEquals(17, Themes.migrate(mPrefs));
        assertEquals(0xFFABCDEF, Themes.forKeyboard(mContext).spec.c[ThemeSpec.BACKGROUND]);
    }

    /** The custom screen's preview shows the variant being edited, whatever is drawn now. */
    @Test
    public void customPreviewShowsTheEditedVariant() {
        mPrefs.edit().putString(Themes.PREF_THEME, "7")
                .putString(Themes.PREF_MODE, Themes.MODE_LIGHT)
                .putString(CustomTheme.PREF_BASE, "15").commit();
        CustomTheme.set(mPrefs, true, ThemeSpec.LABEL, 0xFFFF00FF);
        assertEquals("editing what is drawn: light", spec(16),
                Themes.forPreview(mContext, Themes.CUSTOM_PREVIEW_KEY).spec);
        mPrefs.edit().putString(CustomTheme.PREF_EDITING, Themes.MODE_DARK).commit();
        ThemeSpec shown = Themes.forPreview(mContext, Themes.CUSTOM_PREVIEW_KEY).spec;
        assertEquals(ThemeSpec.TERMINAL, shown.shape);
        assertEquals(0xFFFF00FF, shown.c[ThemeSpec.LABEL]);
        assertEquals("the theme screen's preview shows the keyboard", spec(5),
                Themes.forPreview(mContext, "theme_preview").spec);
    }

    @Test
    public void colourRowsSuitTheKeyStyle() {
        assertTrue(ThemeSpec.slotApplies(ThemeSpec.PRESSED, ThemeSpec.FLAT));
        assertFalse(ThemeSpec.slotApplies(ThemeSpec.SKIRT, ThemeSpec.FLAT));
        assertTrue(ThemeSpec.slotApplies(ThemeSpec.SKIRT, ThemeSpec.SCULPTED));
        assertFalse(ThemeSpec.slotApplies(ThemeSpec.OUTLINE, ThemeSpec.SCULPTED));
        assertTrue(ThemeSpec.slotApplies(ThemeSpec.GLOW, ThemeSpec.TERMINAL));
        for (int slot = 0; slot < ThemeSpec.SLOTS; slot++) {
            assertNotEquals(0, CustomThemeActivity.Fragment.title(slot, ThemeSpec.FLAT));
        }
        assertEquals(ThemeSpec.SLOTS, CustomThemeActivity.Fragment.ORDER.length);
        assertEquals(ThemeSpec.SLOTS, ThemeSpec.SLOT_NAMES.length);
    }

    @Test
    public void hexColours() {
        assertEquals(Integer.valueOf(0xFF123ABC), ColourPickerDialog.parse("#123abc"));
        assertEquals(Integer.valueOf(0xFF123ABC), ColourPickerDialog.parse("123ABC"));
        assertEquals(Integer.valueOf(0x80FFFFFF), ColourPickerDialog.parse("#80FFFFFF"));
        assertNull(ColourPickerDialog.parse("#12345"));
        assertNull(ColourPickerDialog.parse("#GG0000"));
        assertEquals("#123ABC", ColourPickerDialog.format(0xFF123ABC));
        assertEquals("#80FFFFFF", ColourPickerDialog.format(0x80FFFFFF));
    }

    @Test
    public void colourPicker() {
        ColourPickerView v = new ColourPickerView(mContext);
        v.measure(android.view.View.MeasureSpec.makeMeasureSpec(600,
                android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec
                .makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
        int[] got = new int[1];
        v.setListener(c -> got[0] = c);
        v.setColour(0xFFFF0000);
        // Top right of the square: the pure hue; bottom: black.
        v.startDrag(1);
        v.pick(v.square().right, v.square().top);
        assertEquals(0xFFFF0000, got[0]);
        v.pick(v.square().left, v.square().bottom);
        assertEquals(0xFF000000, got[0]);
        // A third along the hue strip: green.
        v.pick(v.square().right, v.square().top);
        v.startDrag(2);
        v.pick(v.hueStrip().left + v.hueStrip().width() / 3, v.hueStrip().centerY());
        assertEquals(120, v.hsv()[0], 1);
        assertEquals(0, Color.red(got[0]), 4);
        assertEquals(255, Color.green(got[0]), 4);
        // Alpha is kept.
        v.setColour(0x80FFFFFF);
        assertEquals(0x80, Color.alpha(v.getColour()));
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
    }

    // ------------------------------------------------------------------ keys

    private static final int[] FLAT = {5, 7, 10, 11};
    private static final int[] SHAPED = {12, 13, 14, 15, 16};

    /** With borders, a plain key is filled; without, it shows the keyboard behind it. */
    @Test
    public void keyBordersToggle() {
        for (int id : FLAT) {
            ThemeSpec s = spec(id);
            for (int kind = 0; kind < KeyKind.COUNT; kind++) {
                Drawable with = new KeyboardLook(mContext, s, true).keyBackground(kind);
                Drawable without = new KeyboardLook(mContext, s, false).keyBackground(kind);
                assertEquals("theme " + id + " key", s.face(kind) | 0xFF000000,
                        centre(with, new int[0]));
                assertEquals("theme " + id + " no border", 0, centre(without, new int[0]));
                // Pressed and active keys still light up without borders.
                assertEquals(s.c[ThemeSpec.PRESSED] | 0xFF000000, centre(without,
                        new int[] {android.R.attr.state_pressed}));
                assertEquals(s.c[ThemeSpec.ACTIVE] | 0xFF000000, centre(without,
                        new int[] {android.R.attr.state_checkable, android.R.attr.state_checked}));
                // The label does not move when a bare key lights up.
                assertEquals(padding(without, new int[0]),
                        padding(without, new int[] {android.R.attr.state_pressed}));
            }
        }
    }

    @Test
    public void highContrastHasOutlinesAndReadableLabels() {
        ThemeSpec t = spec(Palettes.HIGH_CONTRAST_DARK);
        assertNotEquals(0, t.c[ThemeSpec.OUTLINE]);
        assertEquals(0xFFFFFFFF, t.c[ThemeSpec.LABEL]);
        assertEquals(0xFF000000, t.c[ThemeSpec.BACKGROUND]);
        ThemeSpec light = spec(Palettes.HIGH_CONTRAST_LIGHT);
        assertEquals(0xFF000000, light.c[ThemeSpec.OUTLINE]);
        assertEquals(0xFF000000, light.c[ThemeSpec.LABEL]);
    }

    /** Every kind of shaped key draws in every state; the borders setting does not hide it. */
    @Test
    public void shapedKeysDrawEveryState() {
        int P = android.R.attr.state_pressed, K = android.R.attr.state_checkable,
                C = android.R.attr.state_checked, E = android.R.attr.state_empty;
        for (int id : SHAPED) {
            ThemeSpec s = spec(id);
            assertNotEquals(ThemeSpec.FLAT, s.shape);
            for (int kind = 0; kind < KeyKind.COUNT; kind++) {
                for (boolean borders : new boolean[] {true, false}) {
                    String what = "theme " + id + " kind " + kind + " borders " + borders;
                    Drawable d = new KeyboardLook(mContext, s, borders).keyBackground(kind);
                    int normal = centre(d, new int[0], KEY_W, KEY_H);
                    int pressed = centre(d, new int[] {P}, KEY_W, KEY_H);
                    int on = centre(d, new int[] {K, C}, KEY_W, KEY_H);
                    assertEquals(what + " opaque face", 0xFF, Color.alpha(normal));
                    assertNear(what + " face colour", s.face(kind), normal, 0x30);
                    assertNotEquals(what + " pressed", normal, pressed);
                    assertNotEquals(what + " on", normal, on);
                    assertEquals(what + " empty", 0, centre(d, new int[] {E}, KEY_W, KEY_H));
                    Rect pad = padding(d, new int[0]);
                    assertTrue(what + " padding " + pad, pad.left > 0 && pad.top > 0
                            && pad.left + pad.right < KEY_W && pad.top + pad.bottom < KEY_H);
                }
            }
        }
    }

    @Test
    public void lookCapabilities() {
        KeyboardLook typewriter = new KeyboardLook(mContext, spec(12), true);
        assertTrue(typewriter.hintsAboveKey());
        assertTrue(typewriter.blankSpaceBar());
        assertNotNull(typewriter.typeface());
        KeyboardLook material = new KeyboardLook(mContext, spec(7), true);
        assertFalse(material.hintsAboveKey());
        assertFalse(material.blankSpaceBar());
        assertNull(material.typeface());
        assertEquals("flat cursor keys in the hint colour", spec(7).c[ThemeSpec.HINT],
                material.cursorColor(KeyKind.NORMAL));
        KeyboardLook terminal = new KeyboardLook(mContext, spec(15), true);
        assertEquals("glow", spec(15).c[ThemeSpec.GLOW], terminal.shadowColor());
        assertTrue(terminal.shadowRadius() > 0);
        assertEquals("dark labels: no shadow", 0,
                new KeyboardLook(mContext, spec(5), true).shadowColor());
    }

    // ------------------------------------------------------------------ popups

    /** Popup keys are drawn in the theme's key style, on a panel shaped for it. */
    @Test
    public void popupsFollowTheKeyStyle() {
        for (int id : SHAPED) {
            ThemeSpec s = spec(id);
            KeyboardLook popup = new KeyboardLook(mContext, s, true).popupLook();
            assertEquals("no background of its own", 0,
                    centre(popup.keyboardBackground(), new int[0]));
            Drawable key = popup.keyBackground(KeyKind.NORMAL);
            int normal = centre(key, new int[0], KEY_W, KEY_H);
            assertNear("theme " + id + " popup key is a key", s.face(KeyKind.NORMAL), normal, 0x30);
            assertNotEquals(normal, centre(key, new int[] {android.R.attr.state_pressed},
                    KEY_W, KEY_H));
        }
        // Flat themes with borders: keys on the panel; without: bare until pressed.
        ThemeSpec m = spec(7);
        assertEquals(m.face(0) | 0xFF000000, centre(new KeyboardLook(mContext, m, true)
                .popupLook().keyBackground(KeyKind.NORMAL), new int[0]));
        assertEquals(0, centre(new KeyboardLook(mContext, m, false).popupLook()
                .keyBackground(KeyKind.NORMAL), new int[0]));
        // Panel corners suit the style.
        float material = new KeyboardLook(mContext, spec(7), true).panelCornerDp();
        float terminal = new KeyboardLook(mContext, spec(15), true).panelCornerDp();
        float typewriter = new KeyboardLook(mContext, spec(12), true).panelCornerDp();
        assertTrue(terminal < material && material < typewriter);
        Drawable panel = new KeyboardLook(mContext, spec(15), true).popupPanel();
        assertTrue(panel instanceof GradientDrawable);
        assertEquals(spec(15).c[ThemeSpec.PANEL] | 0xFF000000, centre(panel, new int[0]));
    }

    // ------------------------------------------------------------------ helpers

    private static void assertNear(String what, int expected, int actual, int tolerance) {
        for (int s : new int[] {16, 8, 0}) {
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

    private static Rect padding(Drawable d, int[] state) {
        Rect pad = new Rect();
        d.setState(state);
        d.setBounds(0, 0, KEY_W, KEY_H);
        d.getCurrent().getPadding(pad);
        return pad;
    }
}
