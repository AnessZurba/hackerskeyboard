package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertArrayEquals;

import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** What the gesture label says: what release does, and how to back out. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class GestureLabelTest {
    private final Resources mRes = ApplicationProvider.getApplicationContext().getResources();

    @Test
    public void backspaceSwipe() {
        assertArrayEquals(new String[] {"Nothing selected", "Slide left to choose words"},
                GestureLabel.deleteSwipe(mRes, 0, false));
        assertArrayEquals(new String[] {"Release to delete 1 word", "Slide back to cancel"},
                GestureLabel.deleteSwipe(mRes, 1, false));
        assertArrayEquals(new String[] {"Release to delete 3 words", "Slide back to cancel"},
                GestureLabel.deleteSwipe(mRes, 3, false));
        assertArrayEquals(new String[] {"Release to delete 7 words", "Keep holding to take more words"},
                GestureLabel.deleteSwipe(mRes, 7, true));
    }

    @Test
    public void trackpad() {
        assertArrayEquals(new String[] {"Moving the cursor", "Reach the screen edge to keep going"},
                GestureLabel.trackpad(mRes, false));
        assertArrayEquals(new String[] {"Selecting", "Release to keep the selection"},
                GestureLabel.trackpad(mRes, true));
    }
}
