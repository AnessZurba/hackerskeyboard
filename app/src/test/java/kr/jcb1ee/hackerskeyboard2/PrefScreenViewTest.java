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
    @Test
    public void openThemePicker() {
        ActivityController<PrefScreenView> controller =
                Robolectric.buildActivity(PrefScreenView.class).setup();
        PrefScreenView activity = controller.get();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Fragment f = activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
        assertNotNull(f);
        PreferenceFragmentCompat pf = (PreferenceFragmentCompat) f;
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
