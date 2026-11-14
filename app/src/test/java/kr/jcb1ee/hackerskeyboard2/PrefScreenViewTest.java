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
        prefs.edit().putString(KeyboardSwitcher.PREF_KEYBOARD_LAYOUT, "10").commit();
        LatinKeyboardView view = ThemePreviewPreference.makePreview(c);
        assertNotNull(view.getKeyboard());
        org.junit.Assert.assertEquals(KeyboardTheme.HIGH_CONTRAST, idOf(view.getTheme()));
        prefs.edit().putString(KeyboardSwitcher.PREF_KEYBOARD_LAYOUT, "5").commit();
        view = ThemePreviewPreference.makePreview(c);
        org.junit.Assert.assertTrue("Material Light is light", view.getTheme().light);
        // It draws (no crash with no keyboard service running).
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(view.getMeasuredWidth(),
                Math.max(1, view.getMeasuredHeight()), android.graphics.Bitmap.Config.ARGB_8888);
        view.draw(new android.graphics.Canvas(b));
        prefs.edit().clear().commit();
    }

    private static int idOf(KeyboardTheme t) {
        return t.keyStroke == 0xFFFFFFFF ? KeyboardTheme.HIGH_CONTRAST : -1;
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
        for (String key : new String[] {"pref_keyboard_layout", "pref_hint_mode",
                "pref_label_scale_v2"}) {
            Preference p = pf.findPreference(key);
            assertNotNull(key, p);
            pf.onDisplayPreferenceDialog(p);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            activity.getSupportFragmentManager().executePendingTransactions();
        }
        controller.pause().stop().destroy();
    }
}
