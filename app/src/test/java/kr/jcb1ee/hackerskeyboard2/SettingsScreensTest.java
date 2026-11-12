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

    /** Edge to edge (Android 15+): the settings list keeps clear of the system bars. */
    @Test
    public void settingsKeepClearOfTheSystemBars() {
        LatinIMESettings a = Robolectric.buildActivity(LatinIMESettings.class).setup().get();
        android.view.View content = a.findViewById(android.R.id.content);
        androidx.core.view.WindowInsetsCompat insets = new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                        androidx.core.graphics.Insets.of(0, 60, 0, 48))
                .build();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(content, insets);
        org.junit.Assert.assertEquals("above the navigation bar", 48, content.getPaddingBottom());
    }

    /**
     * The settings list starts below the action bar however the action bar meets it: above it
     * (older AppCompat), or drawn over its top because it extends under the status bar (1.8).
     */
    @Test
    public void listStartsBelowTheActionBar() {
        Context c = ApplicationProvider.getApplicationContext();
        android.widget.FrameLayout root = new android.widget.FrameLayout(c);
        android.view.View bar = new android.view.View(c);
        android.view.View content = new android.view.View(c);
        root.addView(bar);
        root.addView(content);
        root.layout(0, 0, 1000, 2000);
        // Action bar under the status bar (0..200), content from the top: pad by 200.
        bar.layout(0, 0, 1000, 200);
        content.layout(0, 0, 1000, 2000);
        org.junit.Assert.assertEquals(200, SystemBars.topOf(content, bar, true, 60));
        // Content already below the action bar: no padding.
        content.layout(0, 200, 1000, 2000);
        org.junit.Assert.assertEquals(0, SystemBars.topOf(content, bar, true, 60));
        // No action bar: the status bar's height.
        org.junit.Assert.assertEquals(60, SystemBars.topOf(content, null, false, 60));
    }

    /**
     * On the real settings screen, the first item is not under the action bar. Android 15,
     * where apps are drawn edge to edge and AppCompat 1.8 extends the action bar upwards.
     */
    @Test
    @Config(sdk = 35)
    public void firstSettingVisible() {
        LatinIMESettings a = Robolectric.buildActivity(LatinIMESettings.class).setup().visible().get();
        android.view.View decor = a.getWindow().getDecorView();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(decor,
                new androidx.core.view.WindowInsetsCompat.Builder()
                        .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                                androidx.core.graphics.Insets.of(0, 60, 0, 48))
                        .build());
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1080, 2340);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        decor.layout(0, 0, 1080, 2340);
        android.view.View bar = a.findViewById(androidx.appcompat.R.id.action_bar_container);
        androidx.recyclerview.widget.RecyclerView list = findList(decor);
        org.junit.Assert.assertNotNull(list);
        int[] b = new int[2];
        int[] l = new int[2];
        bar.getLocationInWindow(b);
        list.getChildAt(0).getLocationInWindow(l);
        org.junit.Assert.assertTrue("first item at " + l[1] + ", action bar ends at "
                + (b[1] + bar.getHeight()), l[1] >= b[1] + bar.getHeight());
    }

    private static androidx.recyclerview.widget.RecyclerView findList(android.view.View v) {
        if (v instanceof androidx.recyclerview.widget.RecyclerView) {
            return (androidx.recyclerview.widget.RecyclerView) v;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                androidx.recyclerview.widget.RecyclerView r = findList(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
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
            R.layout.input_ics, R.layout.input_stone_bold,
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
