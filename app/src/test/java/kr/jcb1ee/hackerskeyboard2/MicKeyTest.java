package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The mic/settings key: the last one picked from its popup sticks; the Fn layer's is just a mic. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class MicKeyTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @After
    public void reset() {
        LatinKeyboard.sMicKeyChoice = 0;
    }

    private Keyboard.Key f1(int layout) {
        LatinKeyboard kb = new LatinKeyboard(mContext, layout, 0,
                LatinIME.sKeyboardSettings.keyboardHeightPercent);
        kb.setVoiceMode(true, true);
        for (Keyboard.Key k : kb.getKeys()) if (kb.isF1Key(k)) return k;
        return null;
    }

    @Test
    public void lastPickedSticksOnTheMainKeyboard() {
        Keyboard.Key key = f1(R.xml.kbd_full);
        assertNotNull(key);
        assertEquals("mic by default", LatinKeyboardView.KEYCODE_VOICE, key.codes[0]);
        assertEquals("popup: settings only", R.xml.popup_settings, key.popupResId);

        assertTrue(LatinKeyboard.rememberMicKeyChoice(mContext, LatinKeyboardView.KEYCODE_OPTIONS));
        key = f1(R.xml.kbd_full);
        assertEquals("settings after picking it", LatinKeyboardView.KEYCODE_OPTIONS, key.codes[0]);
        assertEquals("popup: the mic only", R.xml.popup_voice, key.popupResId);

        // Kept across restarts.
        LatinKeyboard.sMicKeyChoice = 0;
        LatinKeyboard.loadMicKeyChoice(
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(mContext));
        assertEquals(LatinKeyboardView.KEYCODE_OPTIONS, f1(R.xml.kbd_full).codes[0]);

        assertTrue(LatinKeyboard.rememberMicKeyChoice(mContext, LatinKeyboardView.KEYCODE_VOICE));
        assertEquals(LatinKeyboardView.KEYCODE_VOICE, f1(R.xml.kbd_full).codes[0]);
        assertFalse("other keys aren't remembered", LatinKeyboard.rememberMicKeyChoice(mContext, 'a'));
    }

    /** Each popup holds just the other choice. */
    @Test
    public void popupsHoldTheOtherChoice() {
        Keyboard settings = new Keyboard(mContext, 0, R.xml.popup_settings);
        assertEquals(1, settings.getKeys().size());
        assertEquals(LatinKeyboardView.KEYCODE_OPTIONS, settings.getKeys().get(0).codes[0]);
        Keyboard voice = new Keyboard(mContext, 0, R.xml.popup_voice);
        assertEquals(1, voice.getKeys().size());
        assertEquals(LatinKeyboardView.KEYCODE_VOICE, voice.getKeys().get(0).codes[0]);
    }

    @Test
    public void fnLayerMicHasNoSettingsPopup() {
        LatinKeyboard.sMicKeyChoice = LatinKeyboardView.KEYCODE_OPTIONS; // whatever was picked
        Keyboard.Key key = f1(R.xml.kbd_full_fn);
        assertNotNull(key);
        assertEquals(LatinKeyboardView.KEYCODE_VOICE, key.codes[0]);
        assertEquals("no long press", 0, key.popupResId);
    }
}
