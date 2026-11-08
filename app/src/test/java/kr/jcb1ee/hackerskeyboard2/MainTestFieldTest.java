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
}
