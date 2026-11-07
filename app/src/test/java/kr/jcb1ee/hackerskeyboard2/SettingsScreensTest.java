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
        };
        for (int layout : layouts) {
            Context themed = new ContextThemeWrapper(app, android.R.style.Theme_DeviceDefault_InputMethod);
            View v = LayoutInflater.from(themed).inflate(layout, null);
            assertNotNull(app.getResources().getResourceEntryName(layout), v);
        }
    }
}
