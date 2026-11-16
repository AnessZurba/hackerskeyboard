package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertNotNull;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import kr.jcb1ee.hackerskeyboard2.theme.KeyKind;
import kr.jcb1ee.hackerskeyboard2.theme.Themes;

/** Opens the "View" settings screen and the keyboard theme picker. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class PrefScreenViewTest {
    /** The preview draws the keyboard in the chosen theme and follows the theme setting. */
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void themePreviewFollowsTheSetting() {
        android.content.Context c = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        android.content.SharedPreferences prefs =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(c);
        prefs.edit().putString(Themes.PREF_THEME, "10")
                .putString(Themes.PREF_MODE, Themes.MODE_DARK).commit();
        LatinKeyboardView view = ThemePreviewPreference.makePreview(c);
        assertNotNull(view.getKeyboard());
        org.junit.Assert.assertTrue(view.getLook().sameAs(Themes.forKeyboard(c)));
        org.junit.Assert.assertFalse("High contrast, dark", view.getLook().isLight());
        // The light/dark setting reaches the preview.
        prefs.edit().putString(Themes.PREF_MODE, Themes.MODE_LIGHT).commit();
        view = ThemePreviewPreference.makePreview(c);
        org.junit.Assert.assertTrue("High contrast, light", view.getLook().isLight());
        prefs.edit().putString(Themes.PREF_THEME, "5").commit(); // Material Light, migrated
        view = ThemePreviewPreference.makePreview(c);
        org.junit.Assert.assertTrue("Material Light is light", view.getLook().isLight());
        org.junit.Assert.assertEquals("7", prefs.getString(Themes.PREF_THEME, null));
        // It draws (no crash with no keyboard service running).
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(view.getMeasuredWidth(),
                Math.max(1, view.getMeasuredHeight()), android.graphics.Bitmap.Config.ARGB_8888);
        view.draw(new android.graphics.Canvas(b));
        // The other look settings on that screen reach the preview too.
        prefs.edit().putString(LatinIME.PREF_HINT_MODE, "0")
                .putString("pref_label_scale_v2", "0.8").putString("pref_top_row_scale", "0.6").commit();
        ThemePreviewPreference.makePreview(c);
        org.junit.Assert.assertEquals(0, LatinIME.sKeyboardSettings.hintMode);
        org.junit.Assert.assertEquals(0.8f, LatinIME.sKeyboardSettings.labelScalePref, 0.001f);
        org.junit.Assert.assertEquals(0.6f, LatinIME.sKeyboardSettings.topRowScale, 0.001f);
        for (String key : new String[] {"pref_hint_mode", "pref_label_scale_v2", "pref_top_row_scale",
                "pref_keyboard_layout", "pref_key_borders", "pref_theme_mode",
                "pref_custom_theme_light_key", "custom_theme_editing"}) {
            org.junit.Assert.assertTrue(key, ThemePreviewPreference.affectsPreview(key));
        }
        prefs.edit().clear().commit();
        LatinIME.sKeyboardSettings.labelScalePref = 1f;
        LatinIME.sKeyboardSettings.topRowScale = 1f;
    }

    /** The keyboard tells the theme each key's kind by its code. */
    @Test
    public void keyKinds() {
        android.content.Context c = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        org.junit.Assert.assertEquals(KeyKind.ACCENT, KeyKind.of(LatinIME.ASCII_ENTER, true));
        org.junit.Assert.assertEquals(KeyKind.ACCENT,
                KeyKind.of(LatinKeyboardView.KEYCODE_ESCAPE, true));
        org.junit.Assert.assertEquals(KeyKind.NORMAL, KeyKind.of(LatinIME.ASCII_SPACE, true));
        LatinKeyboard kbd = new LatinKeyboard(c, R.xml.kbd_full, 0, 30f);
        java.util.Map<String, Integer> found = new java.util.HashMap<>();
        for (Keyboard.Key k : kbd.getKeys()) {
            int code = k.codes != null && k.codes.length > 0 ? k.codes[0] : 0;
            String name = k.label != null ? k.label.toString() : String.valueOf(code);
            found.put(name, KeyKind.of(code, k.modifier));
        }
        org.junit.Assert.assertEquals(Integer.valueOf(KeyKind.NORMAL), found.get("a"));
        org.junit.Assert.assertEquals(Integer.valueOf(KeyKind.NORMAL), found.get("1"));
        org.junit.Assert.assertEquals("space", Integer.valueOf(KeyKind.NORMAL), found.get("32"));
        org.junit.Assert.assertEquals(Integer.valueOf(KeyKind.MODIFIER), found.get("Ctrl"));
        org.junit.Assert.assertEquals(Integer.valueOf(KeyKind.MODIFIER), found.get("Alt"));
        org.junit.Assert.assertEquals("shift", Integer.valueOf(KeyKind.MODIFIER), found.get("-1"));
        org.junit.Assert.assertEquals("backspace", Integer.valueOf(KeyKind.MODIFIER), found.get("-5"));
        org.junit.Assert.assertEquals(Integer.valueOf(KeyKind.ACCENT), found.get("Esc"));
        org.junit.Assert.assertEquals("enter", Integer.valueOf(KeyKind.ACCENT), found.get("10"));
    }

    /** Long-press popups are 10% taller than before (0.325in keys, now 0.3575in). */
    @Test
    public void popupKeysAreTaller() {
        android.content.Context c = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        float expected = 0.325f * 1.1f * dm.xdpi;
        org.junit.Assert.assertEquals(expected,
                c.getResources().getDimension(R.dimen.popup_key_height), 1f);
        LatinKeyboard kbd = new LatinKeyboard(c, R.xml.kbd_full, 0, 30f);
        int checked = 0;
        for (Keyboard.Key k : kbd.getKeys()) {
            if (k.label == null || !"e".contentEquals(k.label) && !",".contentEquals(k.label)) continue;
            Keyboard popup = k.getPopupKeyboard(c, 0);
            assertNotNull("popup of " + k.label, popup);
            for (Keyboard.Key p : popup.getKeys()) {
                org.junit.Assert.assertEquals("popup key of " + k.label, expected, p.height, 1.5f);
            }
            checked++;
        }
        org.junit.Assert.assertTrue(checked > 0);
    }

    @Test
    public void openThemePicker() {
        ActivityController<PrefScreenView> controller =
                Robolectric.buildActivity(PrefScreenView.class).setup();
        PrefScreenView activity = controller.get();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Fragment f = activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
        assertNotNull(f);
        PreferenceFragmentCompat pf = (PreferenceFragmentCompat) f;
        assertNotNull("theme preview", ((PreferenceFragmentCompat) activity.getSupportFragmentManager()
                .findFragmentById(android.R.id.content)).findPreference("theme_preview"));
        assertNotNull("custom theme", pf.findPreference("custom_theme"));
        for (String key : new String[] {"pref_keyboard_layout", "pref_theme_mode",
                "pref_hint_mode", "pref_label_scale_v2"}) {
            Preference p = pf.findPreference(key);
            assertNotNull(key, p);
            pf.onDisplayPreferenceDialog(p);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            activity.getSupportFragmentManager().executePendingTransactions();
        }
        controller.pause().stop().destroy();
    }
}
