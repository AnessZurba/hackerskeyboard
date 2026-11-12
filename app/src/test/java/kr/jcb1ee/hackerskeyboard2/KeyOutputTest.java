package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;

import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Characterisation tests: what the keyboard sends to the app for each key (and modifier
 * combination), in an ordinary text field, in a terminal (a field that hides its text,
 * like Termux) and in ConnectBot (which gets its own encoding). This is the keyboard's core for SSH and terminal use; the
 * golden transcript (src/test/resources/key_output_golden.txt) records today's behaviour so
 * that refactoring can't change it unnoticed.
 *
 * <p>After a deliberate change, regenerate the golden file with
 * {@code ./gradlew testDebugUnitTest --tests '*KeyOutputTest*' -PupdateGolden} and review its diff.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class KeyOutputTest {
    private static final String GOLDEN = "src/test/resources/key_output_golden.txt";

    private final List<String> mLog = new ArrayList<>();

    @Before
    public void distinctMultitouch() {
        // Like any current phone: without it, holding a modifier can't chord.
        Shadows.shadowOf(ApplicationProvider.<android.app.Application>getApplicationContext()
                .getPackageManager()).setSystemFeature(
                        android.content.pm.PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT, true);
    }

    @After
    public void reset() {
        SelectionPanelTest.sConnection = null;
        SelectionPanelTest.sInputType = InputType.TYPE_CLASS_TEXT;
        SelectionPanelTest.sPackageName = "com.example.chat";
        SelectionPanelTest.sEditorInfo = null;
    }

    /** A modifier held down while the following keys are tapped (chording), released last. */
    private static final class Hold {
        final int code;

        Hold(int code) {
            this.code = code;
        }
    }

    /** Applied to the keyboard's settings before typing. */
    private interface Setting {
        void apply(GlobalKeyboardSettings s);
    }

    private static Hold hold(int code) {
        return new Hold(code);
    }

    private static Setting ctrlKeyCode(int code) {
        return s -> s.chordingCtrlKey = code;
    }

    /**
     * Key sequences: a name, then key codes (tapped one after another), {@link Hold}s (held
     * while the rest is typed) and {@link Setting}s.
     */
    private static final Object[][] KEYS = {
            {"a", 'a'},
            // The key view itself turns letters into capitals while Shift is on; here the
            // service gets the plain code, so this shows what Shift alone adds (nothing).
            {"Shift, code a", Keyboard.KEYCODE_SHIFT, 'a'},
            {"1", '1'},
            {"space", ' '},
            {"Enter", 10},
            {"Tab", 9},
            {"Backspace", Keyboard.KEYCODE_DELETE},
            {"Esc", LatinKeyboardView.KEYCODE_ESCAPE},
            {"Del", LatinKeyboardView.KEYCODE_FORWARD_DEL},
            {"Left", LatinKeyboardView.KEYCODE_DPAD_LEFT},
            {"Up", LatinKeyboardView.KEYCODE_DPAD_UP},
            {"Home", LatinKeyboardView.KEYCODE_HOME},
            {"End", LatinKeyboardView.KEYCODE_END},
            {"PgUp", LatinKeyboardView.KEYCODE_PAGE_UP},
            {"Insert", LatinKeyboardView.KEYCODE_INSERT},
            {"F1", LatinKeyboardView.KEYCODE_FKEY_F1},
            {"F12", LatinKeyboardView.KEYCODE_FKEY_F12},
            {"Ctrl c", LatinKeyboardView.KEYCODE_CTRL_LEFT, 'c'},
            {"Ctrl a", LatinKeyboardView.KEYCODE_CTRL_LEFT, 'a'},
            {"Ctrl [", LatinKeyboardView.KEYCODE_CTRL_LEFT, '['},
            {"Ctrl space", LatinKeyboardView.KEYCODE_CTRL_LEFT, ' '},
            {"Ctrl Left", LatinKeyboardView.KEYCODE_CTRL_LEFT, LatinKeyboardView.KEYCODE_DPAD_LEFT},
            {"Alt x", LatinKeyboardView.KEYCODE_ALT_LEFT, 'x'},
            {"Alt Left", LatinKeyboardView.KEYCODE_ALT_LEFT, LatinKeyboardView.KEYCODE_DPAD_LEFT},
            {"Meta x", LatinKeyboardView.KEYCODE_META_LEFT, 'x'},
            {"Shift Tab", Keyboard.KEYCODE_SHIFT, 9},
            {"Shift Left", Keyboard.KEYCODE_SHIFT, LatinKeyboardView.KEYCODE_DPAD_LEFT},
            {"Ctrl Shift Left", LatinKeyboardView.KEYCODE_CTRL_LEFT, Keyboard.KEYCODE_SHIFT,
                    LatinKeyboardView.KEYCODE_DPAD_LEFT},
            {"Ctrl Alt Del", LatinKeyboardView.KEYCODE_CTRL_LEFT, LatinKeyboardView.KEYCODE_ALT_LEFT,
                    LatinKeyboardView.KEYCODE_FORWARD_DEL},
            {"Ctrl Alt a", LatinKeyboardView.KEYCODE_CTRL_LEFT, LatinKeyboardView.KEYCODE_ALT_LEFT, 'a'},
            {"Ctrl i", LatinKeyboardView.KEYCODE_CTRL_LEFT, 'i'},
            {"Alt Tab", LatinKeyboardView.KEYCODE_ALT_LEFT, 9},
            // Chording: the modifier is held while the other keys are tapped.
            {"hold Ctrl: c c", hold(LatinKeyboardView.KEYCODE_CTRL_LEFT), 'c', 'c'},
            {"hold Ctrl: Left", hold(LatinKeyboardView.KEYCODE_CTRL_LEFT),
                    LatinKeyboardView.KEYCODE_DPAD_LEFT},
            {"hold Shift: a b", hold(Keyboard.KEYCODE_SHIFT), 'a', 'b'},
            {"hold Alt: x", hold(LatinKeyboardView.KEYCODE_ALT_LEFT), 'x'},
            {"hold Ctrl=113: c", ctrlKeyCode(KeyEvent.KEYCODE_CTRL_LEFT),
                    hold(LatinKeyboardView.KEYCODE_CTRL_LEFT), 'c'},
    };

    @Test
    public void keyOutputMatchesTheGoldenTranscript() throws Exception {
        StringBuilder transcript = new StringBuilder();
        transcript.append("# What each key sends to the app (KeyOutputTest). Regenerate with -PupdateGolden.\n");
        for (String field : new String[] {"text", "terminal", "connectbot"}) {
            transcript.append("\n## ").append(field).append(" field\n");
            for (Object[] keys : KEYS) {
                transcript.append(String.format("%-16s -> %s\n", keys[0], type(field, keys)));
            }
        }
        File golden = new File(GOLDEN);
        if (System.getProperty("updateGolden") != null) {
            golden.getParentFile().mkdirs();
            Files.write(golden.toPath(), transcript.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }
        String expected = new String(Files.readAllBytes(golden.toPath()), StandardCharsets.UTF_8);
        assertEquals("key output changed; if deliberate, regenerate with -PupdateGolden",
                expected, transcript.toString());
    }

    /** Types one sequence into a fresh keyboard and field; returns what the app received. */
    private String type(String field, Object[] keys) {
        SelectionPanelTest.sInputType = field.equals("text")
                ? InputType.TYPE_CLASS_TEXT : InputType.TYPE_NULL;
        SelectionPanelTest.sPackageName = field.equals("connectbot")
                ? "org.connectbot" : "com.example.chat";
        EditText edit = new EditText(ApplicationProvider.getApplicationContext());
        edit.setText("hello world");
        edit.setSelection(5);
        mLog.clear();
        EditorInfo appInfo = new EditorInfo();
        SelectionPanelTest.sEditorInfo = appInfo;
        android.view.inputmethod.InputConnection target;
        if (field.equals("text")) {
            target = edit.onCreateInputConnection(appInfo);
        } else {
            // A terminal view (Termux, ConnectBot) hides its text behind a plain connection.
            appInfo.inputType = InputType.TYPE_NULL;
            appInfo.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;
            target = new android.view.inputmethod.BaseInputConnection(edit, true);
        }
        SelectionPanelTest.sConnection = new InputConnectionWrapper(target, true) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                mLog.add("commit \"" + escape(text) + "\"");
                return super.commitText(text, newCursorPosition);
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                mLog.add("compose \"" + escape(text) + "\"");
                return super.setComposingText(text, newCursorPosition);
            }

            @Override
            public boolean finishComposingText() {
                return super.finishComposingText(); // bookkeeping only; not logged
            }

            @Override
            public boolean deleteSurroundingText(int before, int after) {
                mLog.add("delete " + before + "," + after);
                return super.deleteSurroundingText(before, after);
            }

            @Override
            public boolean sendKeyEvent(KeyEvent e) {
                mLog.add((e.getAction() == KeyEvent.ACTION_DOWN ? "down " : "up ")
                        + keyName(e.getKeyCode())
                        + meta(e.getMetaState()));
                return true;
            }

            @Override
            public boolean performContextMenuAction(int id) {
                mLog.add("menu " + (id == android.R.id.selectAll ? "selectAll"
                        : id == android.R.id.copy ? "copy" : id == android.R.id.cut ? "cut"
                        : id == android.R.id.paste ? "paste" : String.valueOf(id)));
                return true;
            }

            @Override
            public boolean performEditorAction(int action) {
                mLog.add("editorAction " + action);
                return true;
            }
        };
        LatinIME ime = Robolectric.buildService(SelectionPanelTest.TestIME.class).create().get();
        ime.onCreateInputView();
        EditorInfo ei = ime.getCurrentInputEditorInfo();
        // Keys send the same in both fields (only the cursor trackpads treat terminals
        // differently), so make sure the terminal case really is one.
        assertEquals(SelectionPanelTest.sInputType, ei.inputType);
        ime.onStartInput(ei, false);
        ime.onStartInputView(ei, false);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        mLog.clear(); // only what the keys send
        List<Integer> held = new ArrayList<>();
        for (int i = 1; i < keys.length; i++) {
            if (keys[i] instanceof Setting) {
                ((Setting) keys[i]).apply(LatinIME.sKeyboardSettings);
            } else if (keys[i] instanceof Hold) {
                int code = ((Hold) keys[i]).code;
                ime.onPress(code);
                held.add(0, code);
            } else {
                int code = keys[i] instanceof Character ? (Character) keys[i] : (Integer) keys[i];
                ime.onPress(code);
                ime.onKey(code, new int[] {code}, 0, 0);
                ime.onRelease(code);
            }
        }
        for (int code : held) {
            ime.onKey(code, new int[] {code}, 0, 0);
            ime.onRelease(code);
        }
        LatinIME.sKeyboardSettings.chordingCtrlKey = 0;
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        return mLog.isEmpty() ? "(nothing)" : String.join("; ", mLog);
    }

    /** KEYCODE_DPAD_LEFT -> DPAD_LEFT (Robolectric's keyCodeToString gives only numbers). */
    private static String keyName(int code) {
        for (java.lang.reflect.Field f : KeyEvent.class.getFields()) {
            try {
                if (f.getName().startsWith("KEYCODE_") && f.getType() == int.class
                        && f.getInt(null) == code) {
                    return f.getName().substring(8);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return String.valueOf(code);
    }

    private static String meta(int meta) {
        StringBuilder m = new StringBuilder();
        if ((meta & KeyEvent.META_SHIFT_ON) != 0) m.append("+Shift");
        if ((meta & KeyEvent.META_CTRL_ON) != 0) m.append("+Ctrl");
        if ((meta & KeyEvent.META_ALT_ON) != 0) m.append("+Alt");
        if ((meta & KeyEvent.META_META_ON) != 0) m.append("+Meta");
        return m.length() == 0 ? "" : " [" + m.substring(1) + "]";
    }

    private static String escape(CharSequence text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c == 0x7f) out.append(String.format("\\x%02x", (int) c));
            else out.append(c);
        }
        return out.toString();
    }
}
