package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;

import android.content.SharedPreferences;
import android.view.View;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The home screen's practice card: ticks follow the gestures done, Hide and Reset tips. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class PracticeCardTest {
    private static SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
    }

    @After
    public void tearDown() {
        HintManager.resetAll(prefs());
    }

    @Test
    public void ticksHideAndReset() {
        HintManager.resetAll(prefs());
        Main main = Robolectric.buildActivity(Main.class).setup().get();
        View card = main.findViewById(R.id.main_practice_card);
        assertEquals(View.VISIBLE, card.getVisibility());
        assertEquals(0, main.practiceCard().done());

        // What the keyboard does when a gesture is used (same process: ticks appear live).
        HintManager hints = new HintManager(prefs());
        hints.learned(HintManager.Tip.SPACE_TRACKPAD);
        hints.learned(HintManager.Tip.UNDO_SWIPE);
        assertEquals(2, main.practiceCard().done());

        // A dismissed tip is not a gesture done.
        hints.onStartInput(new android.view.inputmethod.EditorInfo(), false);
        hints.dismissed();
        assertEquals(2, main.practiceCard().done());

        for (HintManager.Tip tip : HintManager.PRACTICE) hints.learned(tip);
        assertEquals(HintManager.PRACTICE.length, main.practiceCard().done());
        assertEquals(main.getString(R.string.practice_done),
                ((android.widget.TextView) main.findViewById(R.id.main_practice_summary)).getText().toString());

        main.findViewById(R.id.main_practice_hide).performClick();
        assertEquals(View.GONE, card.getVisibility());

        HintManager.resetAll(prefs()); // "Reset tips"
        assertEquals(View.VISIBLE, card.getVisibility());
        assertEquals(0, main.practiceCard().done());
    }
}
