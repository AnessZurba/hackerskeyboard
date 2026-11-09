package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** Opens every settings screen, and inflates the keyboard view in every theme. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SettingsScreensTest {
    private static void open(Class<? extends Activity> cls) {
        Robolectric.buildActivity(cls).setup().pause().stop().destroy();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test public void mainSettings() { open(LatinIMESettings.class); }
    @Test public void viewSettings() { open(PrefScreenView.class); }
    @Test public void actionSettings() { open(PrefScreenActions.class); }
    @Test public void feedbackSettings() { open(PrefScreenFeedback.class); }

    /** Every sub-screen link in the main settings must resolve to an activity of this app. */
    @Test
    public void subScreenLinksResolve() {
        LatinIMESettings a = Robolectric.buildActivity(LatinIMESettings.class).setup().get();
        androidx.preference.PreferenceFragmentCompat f = (androidx.preference.PreferenceFragmentCompat)
                a.getSupportFragmentManager().findFragmentById(android.R.id.content);
        java.util.ArrayList<android.content.Intent> intents = new java.util.ArrayList<>();
        collectIntents(f.getPreferenceScreen(), intents);
        org.junit.Assert.assertFalse("no sub-screen links found", intents.isEmpty());
        for (android.content.Intent i : intents) {
            org.junit.Assert.assertEquals(String.valueOf(i), a.getPackageName(), i.getComponent().getPackageName());
            org.junit.Assert.assertNotNull("no activity for " + i,
                    a.getPackageManager().resolveActivity(i, 0));
        }
    }

    /** Hebrew is listed once in the input languages (it had "he" and "iw" entries). */
    @Test
    public void hebrewListedOnce() {
        InputLanguageSelection a = Robolectric.buildActivity(InputLanguageSelection.class).setup().get();
        androidx.preference.PreferenceFragmentCompat f = (androidx.preference.PreferenceFragmentCompat)
                a.getSupportFragmentManager().findFragmentById(android.R.id.content);
        int hebrew = 0;
        androidx.preference.PreferenceGroup g = f.getPreferenceScreen();
        for (int i = 0; i < g.getPreferenceCount(); i++) {
            String title = String.valueOf(g.getPreference(i).getTitle());
            if (title.contains("[iw") || title.contains("[he")) hebrew++;
        }
        org.junit.Assert.assertEquals(1, hebrew);
    }

    /** A saved "he" (the dropped entry) is read as "iw", so a chosen Hebrew keyboard stays. */
    @Test
    public void savedHebrewKept() {
        org.junit.Assert.assertEquals("en_US,iw,ar", LanguageSwitcher.normalizeLanguages("en_US,he,ar"));
        org.junit.Assert.assertEquals("iw", LanguageSwitcher.normalizeLanguages("he,iw"));
        org.junit.Assert.assertEquals("iw_IL", LanguageSwitcher.normalizeLanguages("he_IL"));
    }

    private static void collectIntents(androidx.preference.PreferenceGroup g,
            java.util.List<android.content.Intent> out) {
        for (int i = 0; i < g.getPreferenceCount(); i++) {
            androidx.preference.Preference p = g.getPreference(i);
            if (p.getIntent() != null && p.getIntent().getComponent() != null) out.add(p.getIntent());
            if (p instanceof androidx.preference.PreferenceGroup) {
                collectIntents((androidx.preference.PreferenceGroup) p, out);
            }
        }
    }

    @Test
    public void savedCrashIsShownInSettings() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        java.io.File f = new java.io.File(app.getFilesDir(), "last_crash.txt");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            out.write("test crash".getBytes("UTF-8"));
        }
        Activity a = Robolectric.buildActivity(LatinIMESettings.class).setup().get();
        android.app.AlertDialog d = (android.app.AlertDialog)
                org.robolectric.shadows.ShadowAlertDialog.getLatestDialog();
        assertNotNull(d);
        d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        org.junit.Assert.assertFalse(f.exists());
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                a.getSystemService(Context.CLIPBOARD_SERVICE);
        org.junit.Assert.assertEquals("test crash", cm.getPrimaryClip().getItemAt(0).getText().toString());
    }

    @Test
    public void everyTheme() {
        Context app = ApplicationProvider.getApplicationContext();
        int[] layouts = {
            R.layout.input_ics, R.layout.input_gingerbread, R.layout.input_stone_bold,
            R.layout.input_trans_neon, R.layout.input_material_dark, R.layout.input_material_light,
            R.layout.input_ics_darker, R.layout.input_material_black,
            R.layout.input_popup_ics, R.layout.input_popup_material_dark,
            R.layout.input_popup_material_black, R.layout.input_popup_material_light,
        };
        for (int layout : layouts) {
            Context themed = new ContextThemeWrapper(app, android.R.style.Theme_DeviceDefault_InputMethod);
            View v = LayoutInflater.from(themed).inflate(layout, null);
            assertNotNull(app.getResources().getResourceEntryName(layout), v);
        }
    }
}
