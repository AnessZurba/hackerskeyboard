package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;

import android.text.InputType;
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
 * Characterisation tests for ordinary typing: what ends up in the field (and whether the
 * keyboard is shifted afterwards) when words, spaces, punctuation, Backspace and Enter are
 * typed into an empty field. Covers auto-capitals, the double-space period and the
 * punctuation/space swaps, which the suggestion code drives, so it guards the suggestions
 * decision (plan Phase 2). The golden transcript is src/test/resources/typing_flow_golden.txt.
 *
 * <p>Regenerate after a deliberate change with
 * {@code ./gradlew testDebugUnitTest --tests '*TypingFlowTest*' -PupdateGolden} and review the diff.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class TypingFlowTest {
    private static final String GOLDEN = "src/test/resources/typing_flow_golden.txt";
    private static final char SHIFT = '⇧';

    /** Typed one character per key; '\b' is Backspace, '\n' Enter, ⇧ a tap on Shift. */
    private static final String[] TYPED = {
            "hello world",
            "hi. how are you",
            "hi  there",
            "hi, there! ok? yes",
            "dr.who",
            "it's 5 o'clock",
            "hello\b\b\b",
            "hi \b\bey",
            "word\nnext",
            "⇧a⇧b c",
            "a , b",
            "end. ",
            "see you .",
    };

    private final List<String> mActions = new ArrayList<>();

    @Before
    public void distinctMultitouch() {
        Shadows.shadowOf(ApplicationProvider.<android.app.Application>getApplicationContext()
                .getPackageManager()).setSystemFeature(
                        android.content.pm.PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT, true);
    }

    @After
    public void reset() {
        SelectionPanelTest.sConnection = null;
        SelectionPanelTest.sInputType = InputType.TYPE_CLASS_TEXT;
        SelectionPanelTest.sEditorInfo = null;
    }

    @Test
    public void typingMatchesTheGoldenTranscript() throws Exception {
        StringBuilder transcript = new StringBuilder();
        transcript.append("# What typing leaves in the field (TypingFlowTest); | is the cursor.\n");
        transcript.append("# Regenerate with -PupdateGolden.\n");
        Object[][] fields = {
                {"sentence", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES},
                {"multi-line", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE},
                {"plain", InputType.TYPE_CLASS_TEXT},
                {"password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD},
        };
        for (Object[] field : fields) {
            transcript.append("\n## ").append(field[0]).append(" field\n");
            for (String typed : TYPED) {
                transcript.append(String.format("%-20s -> %s\n",
                        "\"" + visible(typed) + "\"", type((Integer) field[1], typed)));
            }
        }
        File golden = new File(GOLDEN);
        if (System.getProperty("updateGolden") != null) {
            golden.getParentFile().mkdirs();
            Files.write(golden.toPath(), transcript.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }
        String expected = new String(Files.readAllBytes(golden.toPath()), StandardCharsets.UTF_8);
        assertEquals("typing changed; if deliberate, regenerate with -PupdateGolden",
                expected, transcript.toString());
    }

    private String type(int inputType, String typed) {
        SelectionPanelTest.sInputType = inputType;
        EditText edit = new EditText(ApplicationProvider.getApplicationContext());
        edit.setInputType(inputType);
        mActions.clear();
        EditorInfo appInfo = new EditorInfo();
        SelectionPanelTest.sConnection = new InputConnectionWrapper(
                edit.onCreateInputConnection(appInfo), true) {
            @Override
            public boolean performEditorAction(int action) {
                mActions.add("editorAction " + action);
                return super.performEditorAction(action);
            }

            @Override
            public boolean sendKeyEvent(android.view.KeyEvent e) {
                // A field in a window gets these through the view tree; this one isn't in one.
                return edit.dispatchKeyEvent(e);
            }
        };
        SelectionPanelTest.sEditorInfo = appInfo;
        LatinIME ime = Robolectric.buildService(SelectionPanelTest.TestIME.class).create().get();
        ime.onCreateInputView();
        EditorInfo ei = ime.getCurrentInputEditorInfo();
        ime.onStartInput(ei, false);
        ime.onStartInputView(ei, false);
        idle();
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            int code = c == '\b' ? Keyboard.KEYCODE_DELETE
                    : c == '\n' ? 10
                    : c == SHIFT ? Keyboard.KEYCODE_SHIFT
                    : c;
            // The key view sends capitals while the keyboard is shifted.
            if (Character.isLetter(c) && c != SHIFT && shiftState(ime) != Keyboard.SHIFT_OFF) {
                code = Character.toUpperCase(c);
            }
            ime.onPress(code);
            ime.onKey(code, new int[] {code}, 0, 0);
            ime.onRelease(code);
            // Let the editor report the new selection, as a real one does.
            idle();
            ime.onUpdateSelection(0, 0, edit.getSelectionStart(), edit.getSelectionEnd(), -1, -1);
            idle();
        }
        String text = edit.getText().toString();
        int cursor = edit.getSelectionStart();
        String out = "\"" + visible(text.substring(0, cursor) + "|" + text.substring(cursor)) + "\""
                + (shiftState(ime) == Keyboard.SHIFT_OFF ? "" : " shifted");
        return mActions.isEmpty() ? out : out + " (" + String.join(", ", mActions) + ")";
    }

    private static int shiftState(LatinIME ime) {
        return ime.mKeyboardSwitcher.getInputView().getShiftState();
    }

    private static void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static String visible(String s) {
        return s.replace("\n", "\\n").replace("\b", "\\b");
    }
}
