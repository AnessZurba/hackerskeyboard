package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;

import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The toolbar's "swap ends" relies on text fields keeping a reversed selection (anchor
 * after the moving end) and reporting it in that order. Check it against EditText.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class ReversedSelectionTest {
    @Test
    public void editTextKeepsReversedSelection() {
        EditText field = new EditText(ApplicationProvider.getApplicationContext());
        field.setText("hello world");
        InputConnection ic = field.onCreateInputConnection(new EditorInfo());
        ic.setSelection(8, 2); // anchor 8, moving end 2
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        assertEquals("anchor reported first", 8, et.selectionStart);
        assertEquals("moving end reported second", 2, et.selectionEnd);
        // What the toolbar does next: move the moving end one word left.
        int[] sel = LatinIME.newSelection(et.text.toString(), et.selectionStart, et.selectionEnd,
                -1, true, true);
        ic.setSelection(sel[0], sel[1]);
        // Still reversed (EditText reports the anchor as its "start"): selected range 0..8.
        assertEquals(8, field.getSelectionStart());
        assertEquals(0, field.getSelectionEnd());
    }
}
