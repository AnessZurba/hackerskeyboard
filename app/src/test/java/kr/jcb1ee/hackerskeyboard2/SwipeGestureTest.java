package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** Two-thumb typing must not be read as a swipe; a real one-finger swipe still is. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SwipeGestureTest {
    private static MotionEvent ev(long down, long t, int action, float[][] pts) {
        int n = pts.length;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[n];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[n];
        for (int i = 0; i < n; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = (int) pts[i][0];
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = pts[i][1];
            coords[i].y = pts[i][2];
            coords[i].pressure = 1;
            coords[i].size = 1;
        }
        return MotionEvent.obtain(down, t, action, n, props, coords, 0, 0, 1, 1, 0, 0, 0, 0);
    }

    private KeyboardToolbar setUp(LatinKeyboardView[] out) {
        LatinIME ime = Robolectric.buildService(LatinIME.class).create().get();
        KeyboardToolbar toolbar = (KeyboardToolbar) ime.onCreateInputView();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        toolbar.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        toolbar.layout(0, 0, 1080, toolbar.getMeasuredHeight());
        out[0] = (LatinKeyboardView) ((FrameLayout) toolbar.getChildAt(1)).getChildAt(0);
        return toolbar;
    }

    @Test
    public void twoThumbTypingIsNotASwipe() {
        LatinKeyboardView[] kv = new LatinKeyboardView[1];
        KeyboardToolbar toolbar = setUp(kv);
        boolean shown = toolbar.isToolbarShown();
        float low = kv[0].getHeight() * 0.85f, high = kv[0].getHeight() * 0.1f;
        long t0 = SystemClock.uptimeMillis();
        // Thumb A on the bottom row, thumb B on the top row, A lifts, B lifts.
        kv[0].onTouchEvent(ev(t0, t0, MotionEvent.ACTION_DOWN, new float[][] {{0, 700, low}}));
        kv[0].onTouchEvent(ev(t0, t0 + 40, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[][] {{0, 700, low}, {1, 720, high}}));
        kv[0].onTouchEvent(ev(t0, t0 + 60, MotionEvent.ACTION_POINTER_UP,
                new float[][] {{0, 700, low}, {1, 720, high}}));
        // Thumb B drifts up a few pixels as it taps, as real thumbs do.
        for (int i = 1; i <= 3; i++) {
            kv[0].onTouchEvent(ev(t0, t0 + 60 + i * 8, MotionEvent.ACTION_MOVE,
                    new float[][] {{1, 720, high - i * 4}}));
        }
        kv[0].onTouchEvent(ev(t0, t0 + 92, MotionEvent.ACTION_UP, new float[][] {{1, 720, high - 16}}));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("toolbar must not toggle", shown, toolbar.isToolbarShown());
    }

    @Test
    public void oneFingerSwipeUpTogglesToolbar() {
        LatinKeyboardView[] kv = new LatinKeyboardView[1];
        KeyboardToolbar toolbar = setUp(kv);
        boolean shown = toolbar.isToolbarShown();
        float h = kv[0].getHeight();
        long t0 = SystemClock.uptimeMillis();
        kv[0].onTouchEvent(ev(t0, t0, MotionEvent.ACTION_DOWN, new float[][] {{0, 500, h * 0.9f}}));
        for (int i = 1; i <= 5; i++) {
            kv[0].onTouchEvent(ev(t0, t0 + i * 10, MotionEvent.ACTION_MOVE,
                    new float[][] {{0, 500, h * 0.9f - i * h * 0.17f}}));
        }
        kv[0].onTouchEvent(ev(t0, t0 + 60, MotionEvent.ACTION_UP, new float[][] {{0, 500, h * 0.05f}}));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("swipe up toggles the toolbar", !shown, toolbar.isToolbarShown());
    }
}
