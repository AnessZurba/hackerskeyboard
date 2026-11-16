package kr.jcb1ee.hackerskeyboard2.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

/**
 * The custom theme screen: rows for the base theme's key style and the edited variant, the
 * colour picker, using the theme, and reset. With -PrenderScreens=<dir> it also saves pictures
 * of the screen (and of the colour picker) there.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CustomThemeScreenTest {
    private final SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(
            ApplicationProvider.getApplicationContext());

    @After
    public void tearDown() {
        mPrefs.edit().clear().commit();
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void editTheCustomTheme() throws Exception {
        mPrefs.edit().putString(Themes.PREF_THEME, "7")
                .putString(Themes.PREF_MODE, Themes.MODE_DARK)
                .putString(CustomTheme.PREF_BASE, "13").commit();
        ActivityController<CustomThemeActivity> controller =
                Robolectric.buildActivity(CustomThemeActivity.class).setup();
        CustomThemeActivity activity = controller.get();
        idle();
        CustomThemeActivity.Fragment f = (CustomThemeActivity.Fragment) activity
                .getSupportFragmentManager().findFragmentById(android.R.id.content);
        assertNotNull(f.findPreference(Themes.CUSTOM_PREVIEW_KEY));
        PreferenceCategory colours = f.findPreference(CustomThemeActivity.Fragment.KEY_COLOURS);
        // Keycaps: key sides, no outline; editing what is drawn (dark).
        assertNotNull(colours.findPreference(CustomTheme.key(true, ThemeSpec.SKIRT)));
        assertEquals(null, colours.findPreference(CustomTheme.key(true, ThemeSpec.OUTLINE)));
        assertEquals(null, colours.findPreference(CustomTheme.key(true, ThemeSpec.PRESSED)));
        render(activity, "custom_theme_screen.png");

        // Pick a colour for the keys.
        ColourPreference key = colours.findPreference(CustomTheme.key(true, ThemeSpec.KEY));
        key.performClick();
        idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        renderDialog(dialog, "custom_theme_picker.png");
        android.widget.EditText hex = findEditText(dialog.getWindow().getDecorView());
        hex.setText("#3366CC");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(0xFF3366CC, mPrefs.getInt(CustomTheme.key(true, ThemeSpec.KEY), 0));
        assertEquals("#3366CC", key.getSummary().toString());

        // Use the theme: the keyboard draws it.
        SwitchPreferenceCompat use = f.findPreference(CustomThemeActivity.Fragment.KEY_USE);
        assertFalse(use.isChecked());
        use.performClick();
        idle();
        assertEquals("17", mPrefs.getString(Themes.PREF_THEME, null));
        assertEquals(0xFF3366CC, Themes.forKeyboard(activity).spec.c[ThemeSpec.KEY]);

        // The light tab: its own rows and colours.
        ((VariantTabsPreference) f.findPreference(CustomTheme.PREF_EDITING)).setEditingDark(false);
        idle();
        assertNotNull(colours.findPreference(CustomTheme.key(false, ThemeSpec.KEY)));
        assertEquals(null, colours.findPreference(CustomTheme.key(true, ThemeSpec.KEY)));
        assertFalse(Themes.forPreview(activity, Themes.CUSTOM_PREVIEW_KEY).spec.c[ThemeSpec.KEY]
                == 0xFF3366CC);

        // Another base: Material (flat) has a pressed key and outline, no sides.
        mPrefs.edit().putString(CustomTheme.PREF_BASE, "7").commit();
        idle();
        assertNotNull(colours.findPreference(CustomTheme.key(false, ThemeSpec.PRESSED)));
        assertNotNull(colours.findPreference(CustomTheme.key(false, ThemeSpec.OUTLINE)));
        assertEquals(null, colours.findPreference(CustomTheme.key(false, ThemeSpec.SKIRT)));

        // Reset the dark colours.
        ((VariantTabsPreference) f.findPreference(CustomTheme.PREF_EDITING)).setEditingDark(true);
        idle();
        f.findPreference(CustomThemeActivity.Fragment.KEY_RESET).performClick();
        idle();
        ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE)
                .performClick();
        idle();
        assertFalse(mPrefs.contains(CustomTheme.key(true, ThemeSpec.KEY)));
        controller.pause().stop().destroy();
    }

    /** Renders the custom screen in light mode with a terminal base, for a second picture. */
    @Test
    public void renderTerminalBase() throws Exception {
        org.junit.Assume.assumeTrue(System.getProperty("renderScreens") != null);
        mPrefs.edit().putString(Themes.PREF_THEME, "17")
                .putString(Themes.PREF_MODE, Themes.MODE_LIGHT)
                .putString(CustomTheme.PREF_BASE, "15").commit();
        CustomTheme.set(mPrefs, false, ThemeSpec.LABEL, 0xFF7A1FA2);
        CustomTheme.set(mPrefs, false, ThemeSpec.BACKGROUND, 0xFFF3E9F7);
        ActivityController<CustomThemeActivity> controller =
                Robolectric.buildActivity(CustomThemeActivity.class).setup();
        idle();
        render(controller.get(), "custom_theme_screen_terminal_light.png");
        controller.pause().stop().destroy();
    }

    private static android.widget.EditText findEditText(View v) {
        if (v instanceof android.widget.EditText) return (android.widget.EditText) v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.widget.EditText e = findEditText(g.getChildAt(i));
                if (e != null) return e;
            }
        }
        return null;
    }

    private static void render(CustomThemeActivity activity, String name) throws Exception {
        View root = activity.getWindow().getDecorView();
        save(root, name, 2400);
    }

    private static void renderDialog(AlertDialog dialog, String name) throws Exception {
        save(dialog.getWindow().getDecorView(), name, 0);
    }

    private static void save(View root, String name, int height) throws Exception {
        String dir = System.getProperty("renderScreens");
        if (dir == null) return;
        int w = root.getResources().getDisplayMetrics().widthPixels;
        int hSpec = height > 0 ? View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                : View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int wSpec = View.MeasureSpec.makeMeasureSpec(height > 0 ? w : w * 9 / 10,
                View.MeasureSpec.EXACTLY);
        for (int i = 0; i < 2; i++) {
            root.measure(wSpec, hSpec);
            root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
            idle();
        }
        Bitmap bmp = Bitmap.createBitmap(root.getMeasuredWidth(), root.getMeasuredHeight(),
                Bitmap.Config.ARGB_8888);
        bmp.eraseColor(0xFF808080);
        root.draw(new Canvas(bmp));
        new java.io.File(dir).mkdirs();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(dir, name))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        assertTrue(new java.io.File(dir, name).length() > 0);
    }
}
