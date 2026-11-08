package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.text.InputType;
import android.widget.EditText;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The main screen's test field can pose as a number, multi-line or password field. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainTestFieldTest {
    @Test
    public void typeToggleChangesTheField() {
        Main main = Robolectric.buildActivity(Main.class).setup().get();
        EditText field = main.findViewById(R.id.main_setup_edit_test);
        assertEquals(InputType.TYPE_CLASS_TEXT, field.getInputType() & InputType.TYPE_MASK_CLASS);

        main.findViewById(R.id.main_test_type_number).performClick();
        assertEquals(InputType.TYPE_CLASS_NUMBER, field.getInputType() & InputType.TYPE_MASK_CLASS);

        main.findViewById(R.id.main_test_type_lines).performClick();
        assertNotEquals(0, field.getInputType() & InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        main.findViewById(R.id.main_test_type_password).performClick();
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                field.getInputType() & InputType.TYPE_MASK_VARIATION);
        assertEquals(0, field.getInputType() & InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        main.findViewById(R.id.main_test_type_text).performClick();
        assertEquals(InputType.TYPE_CLASS_TEXT, field.getInputType());
    }

    /** The screen makes room for the keyboard and keeps the focused test field above it. */
    @Test
    public void screenFollowsTheKeyboard() {
        Main main = Robolectric.buildActivity(Main.class).setup().get();
        android.widget.ScrollView root = main.findViewById(R.id.main_root);
        EditText field = main.findViewById(R.id.main_setup_edit_test);
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2000, android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2000);
        field.requestFocus();
        for (int keyboard : new int[] {300, 900}) { // two frames of the slide
            androidx.core.view.WindowInsetsCompat insets = new androidx.core.view.WindowInsetsCompat.Builder()
                    .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),
                            androidx.core.graphics.Insets.of(0, 0, 0, keyboard))
                    .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                            androidx.core.graphics.Insets.of(0, 60, 0, 40))
                    .build();
            Main.applyInsets(root, insets);
            assertEquals("room for the keyboard", keyboard, root.getPaddingBottom());
            assertEquals("clear of the status bar", 60, root.getPaddingTop());
            android.graphics.Rect r = new android.graphics.Rect();
            field.getDrawingRect(r);
            root.offsetDescendantRectToMyCoords(field, r);
            org.junit.Assert.assertTrue("field above the keyboard at " + keyboard,
                    r.bottom <= root.getScrollY() + 2000 - keyboard);
        }
    }
}
