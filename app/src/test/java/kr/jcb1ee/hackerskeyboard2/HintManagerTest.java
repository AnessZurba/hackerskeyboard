package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** The tip rules: counts, learned gestures, fields, one per session, a row that can't show it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class HintManagerTest {
    private SharedPreferences mPrefs;
    private HintManager mHints;
    private final List<HintManager.Tip> mShown = new ArrayList<>();
    private boolean mRowFree = true;
    private int mHidden;

    @Before
    public void setUp() {
        mPrefs = ApplicationProvider.getApplicationContext()
                .getSharedPreferences("tips_test", Context.MODE_PRIVATE);
        mPrefs.edit().clear().commit();
        mHints = new HintManager(mPrefs);
        mHints.setHost(new HintManager.Host() {
            @Override
            public boolean showTip(HintManager.Tip tip) {
                if (!mRowFree) return false;
                mShown.add(tip);
                return true;
            }

            @Override
            public void hideTip() {
                mHidden++;
            }
        });
    }

    private static EditorInfo field(int inputType) {
        EditorInfo ei = new EditorInfo();
        ei.inputType = inputType;
        return ei;
    }

    private void newSession() {
        mHints.onStartInput(field(InputType.TYPE_CLASS_TEXT), false);
    }

    private void backspaces(int n, long start, long gap) {
        for (int i = 0; i < n; i++) mHints.onBackspaceTap(start + i * gap);
    }

    @Test
    public void fiveBackspacesWithinThreeSecondsShowTheSwipeTip() {
        newSession();
        backspaces(4, 1000, 300);
        assertTrue("not after four", mShown.isEmpty());
        mHints.onBackspaceTap(2200);
        assertEquals(HintManager.Tip.BACKSPACE_SWIPE, mHints.showing());
        assertEquals(1, mHints.shownCount(HintManager.Tip.BACKSPACE_SWIPE));
    }

    @Test
    public void slowOrBrokenBackspaceRunsDoNot() {
        newSession();
        backspaces(5, 1000, 900); // 3.6 s
        assertTrue("too slow", mShown.isEmpty());
        newSession();
        backspaces(3, 10000, 100);
        mHints.onOtherKey();
        backspaces(2, 10400, 100);
        assertTrue("not in a row", mShown.isEmpty());
    }

    @Test
    public void oneTipPerSession() {
        newSession();
        backspaces(5, 1000, 100);
        mHints.onArrowTap();
        mHints.onArrowTap();
        assertEquals(1, mShown.size());
        mHints.onStartInput(field(InputType.TYPE_CLASS_TEXT), true); // restart: same session
        mHints.onArrowTap();
        mHints.onArrowTap();
        assertEquals(1, mShown.size());
        newSession();
        mHints.onArrowTap();
        mHints.onArrowTap();
        assertEquals(HintManager.Tip.SPACE_TRACKPAD, mShown.get(1));
    }

    @Test
    public void atMostThreeTimesEver() {
        for (int i = 0; i < 5; i++) {
            newSession();
            mHints.onArrowTap();
            mHints.onArrowTap();
        }
        assertEquals(HintManager.MAX_SHOWS, mShown.size());
        assertEquals(3, mHints.shownCount(HintManager.Tip.SPACE_TRACKPAD));
    }

    @Test
    public void neverOnceLearnedOrDismissed() {
        mHints.learned(HintManager.Tip.SPACE_TRACKPAD);
        newSession();
        mHints.onArrowTap();
        mHints.onArrowTap();
        assertTrue("learned", mShown.isEmpty());

        backspaces(5, 1000, 100);
        assertEquals(HintManager.Tip.BACKSPACE_SWIPE, mHints.showing());
        mHints.dismissed();
        assertNull(mHints.showing());
        assertTrue(mHints.isLearned(HintManager.Tip.BACKSPACE_SWIPE));
        newSession();
        backspaces(5, 5000, 100);
        assertEquals("dismissed: never again", 1, mShown.size());
    }

    @Test
    public void usingTheGestureTakesTheTipAway() {
        newSession();
        backspaces(5, 1000, 100);
        mHints.learned(HintManager.Tip.BACKSPACE_SWIPE);
        assertEquals(1, mHidden);
        assertNull(mHints.showing());
        mHints.learned(HintManager.Tip.SPACE_TRACKPAD); // another tip: nothing to hide
        assertEquals(1, mHidden);
    }

    @Test
    public void notInTerminalsPasswordsNumbersOrPhones() {
        int[] types = {
                InputType.TYPE_NULL,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_PHONE,
                InputType.TYPE_CLASS_DATETIME,
        };
        for (int type : types) {
            mHints.onStartInput(field(type), false);
            backspaces(5, 1000, 100);
            assertTrue("type " + Integer.toHexString(type), mShown.isEmpty());
        }
        assertFalse(HintManager.fieldAllowsTips(null));
        assertTrue(HintManager.fieldAllowsTips(field(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)));
    }

    @Test
    public void notWhileTheRowCannotShowIt() {
        newSession();
        mRowFree = false; // row hidden by the user, chips or words in it
        backspaces(5, 1000, 100);
        assertTrue(mShown.isEmpty());
        assertEquals("not counted as shown", 0, mHints.shownCount(HintManager.Tip.BACKSPACE_SWIPE));
        mRowFree = true;
        backspaces(5, 2000, 100);
        assertEquals("the same session may still show one", 1, mShown.size());
    }

    @Test
    public void showTipsSettingAndReset() {
        mPrefs.edit().putBoolean(HintManager.PREF_SHOW_TIPS, false).commit();
        newSession();
        backspaces(5, 1000, 100);
        assertTrue("off", mShown.isEmpty());
        mPrefs.edit().putBoolean(HintManager.PREF_SHOW_TIPS, true).commit();
        mHints.learned(HintManager.Tip.BACKSPACE_SWIPE);
        mHints.reset();
        assertFalse(mHints.isLearned(HintManager.Tip.BACKSPACE_SWIPE));
        assertTrue("the setting itself stays", mPrefs.getBoolean(HintManager.PREF_SHOW_TIPS, false));
        backspaces(5, 2000, 100);
        assertEquals(1, mShown.size());
    }

    @Test
    public void cursorPlacedThreeTimesOrSelectTappedThrice() {
        newSession();
        mHints.onCursorPlaced(1000);
        mHints.onCursorPlaced(40000);
        mHints.onCursorPlaced(50000);
        assertTrue("first one too long ago", mShown.isEmpty());
        mHints.onCursorPlaced(60000);
        assertEquals(HintManager.Tip.SELECT_TRACKPAD, mHints.showing());
        newSession();
        mHints.onSelectButtonTap();
        mHints.onSelectButtonTap();
        assertEquals(1, mShown.size());
        mHints.onSelectButtonTap();
        assertEquals(2, mShown.size());
    }

    @Test
    public void thirdKeyboardOpenShowsTheSwipesTip() {
        for (int i = 1; i <= 2; i++) {
            newSession();
            mHints.onKeyboardOpened(true);
        }
        assertTrue(mShown.isEmpty());
        newSession();
        mHints.onKeyboardOpened(true);
        assertEquals(HintManager.Tip.SWIPE_ACTIONS, mHints.showing());
        mHints.learned(HintManager.Tip.SWIPE_ACTIONS);
        newSession();
        mHints.onKeyboardOpened(true);
        assertEquals(1, mShown.size());
    }

    @Test
    public void swipesTipOnlyWithTheSwipesItDescribes() {
        for (int i = 0; i < 4; i++) {
            newSession();
            mHints.onKeyboardOpened(false);
        }
        assertTrue(mShown.isEmpty());
    }

    @Test
    public void thirdLanguageSwitchShowsTheSlideTip() {
        newSession();
        mHints.onLanguageSwitchedOtherwise();
        newSession();
        mHints.onLanguageSwitchedOtherwise();
        assertTrue(mShown.isEmpty());
        newSession();
        mHints.onLanguageSwitchedOtherwise();
        assertEquals(HintManager.Tip.LANGUAGE_SLIDE, mHints.showing());
    }

    @Test
    public void clipboardTipNeedsClipsAndNothingPinned() {
        for (int i = 0; i < 3; i++) {
            newSession();
            mHints.onClipboardOpened(false, false);
        }
        assertTrue("no clips to pin", mShown.isEmpty());
        newSession();
        mHints.onClipboardOpened(true, false);
        assertEquals(HintManager.Tip.CLIP_PIN, mHints.showing());
        newSession();
        mHints.onClipboardOpened(true, true); // something pinned: learned
        assertTrue(mHints.isLearned(HintManager.Tip.CLIP_PIN));
        newSession();
        mHints.onClipboardOpened(true, false);
        assertEquals(1, mShown.size());
    }
}
