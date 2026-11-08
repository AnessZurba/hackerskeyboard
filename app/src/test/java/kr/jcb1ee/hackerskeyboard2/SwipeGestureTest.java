package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** Swipe gestures on the keyboard, driven with real key positions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SwipeGestureTest {
    private KeyboardToolbar mToolbar;
    private LatinKeyboardView mView;
    private final List<Integer> mTyped = new ArrayList<>();

    @Before
    public void setUp() {
        // Like any current phone (the S25 included), report a distinct multi-touch screen.
        Shadows.shadowOf(androidx.test.core.app.ApplicationProvider
                .<android.app.Application>getApplicationContext().getPackageManager())
                .setSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT, true);
        LatinIME ime = Robolectric.buildService(LatinIME.class).create().get();
        mToolbar = (KeyboardToolbar) ime.onCreateInputView();
        idle();
        mToolbar.measure(android.view.View.MeasureSpec.makeMeasureSpec(320, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        mToolbar.layout(0, 0, 320, mToolbar.getMeasuredHeight());
        mView = (LatinKeyboardView) ((FrameLayout) mToolbar.getChildAt(1)).getChildAt(0);
        final LatinKeyboardBaseView.OnKeyboardActionListener real = mView.getOnKeyboardActionListener();
        mView.setOnKeyboardActionListener((LatinKeyboardBaseView.OnKeyboardActionListener)
                java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] {LatinKeyboardBaseView.OnKeyboardActionListener.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("onKey")) mTyped.add((Integer) args[0]);
                            return method.invoke(real, args);
                        }));
    }

    private static void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private float[] centre(int code) {
        for (Keyboard.Key k : mView.getKeyboard().getKeys()) {
            if (k.codes != null && k.codes[0] == code) {
                return new float[] {k.x + k.width / 2f, k.y + k.height / 2f};
            }
        }
        throw new AssertionError("no key " + (char) code);
    }

    private static MotionEvent ev(long down, long t, int action, float[]... pts) {
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

    private static float[] p(int id, float[] xy, float dy) {
        return new float[] {id, xy[0], xy[1] + dy};
    }

    private void tap(int code, long t) {
        float[] c = centre(code);
        mView.onTouchEvent(ev(t, t, MotionEvent.ACTION_DOWN, p(0, c, 0)));
        mView.onTouchEvent(ev(t, t + 50, MotionEvent.ACTION_UP, p(0, c, 0)));
        idle();
    }

    /** A swipe up from the bottom row to the top row toggles the toolbar and types nothing. */
    @Test
    public void swipeUpTogglesToolbarAndTypesNothing() {
        boolean shown = mToolbar.isToolbarShown();
        float[] from = centre('v'), to = centre('t');
        long t0 = SystemClock.uptimeMillis();
        mView.onTouchEvent(ev(t0, t0, MotionEvent.ACTION_DOWN, p(0, from, 0)));
        for (int i = 1; i <= 5; i++) {
            float[] at = {from[0] + (to[0] - from[0]) * i / 5, from[1] + (to[1] - from[1]) * i / 5};
            mView.onTouchEvent(ev(t0, t0 + i * 10, MotionEvent.ACTION_MOVE, p(0, at, 0)));
        }
        mView.onTouchEvent(ev(t0, t0 + 60, MotionEvent.ACTION_UP, p(0, to, 0)));
        idle();
        assertEquals("swipe up toggles the toolbar", !shown, mToolbar.isToolbarShown());

        // The key the swipe ended on must not be typed by the next touch.
        tap('g', t0 + 500);
        assertEquals("only the tapped key is typed", java.util.Collections.singletonList((int) 'g'), mTyped);
    }

    /**
     * A swipe up that slows down before the finger lifts (as people do when aiming) is
     * still a swipe, and types nothing.
     */
    @Test
    public void swipeUpThatSlowsDownStillCounts() {
        boolean shown = mToolbar.isToolbarShown();
        float[] from = centre('v'), to = centre('t');
        long t0 = SystemClock.uptimeMillis();
        mView.onTouchEvent(ev(t0, t0, MotionEvent.ACTION_DOWN, p(0, from, 0)));
        for (int i = 1; i <= 5; i++) {
            float[] at = {from[0] + (to[0] - from[0]) * i / 5, from[1] + (to[1] - from[1]) * i / 5};
            mView.onTouchEvent(ev(t0, t0 + i * 20, MotionEvent.ACTION_MOVE, p(0, at, 0)));
        }
        // Finger settles on the top row for a moment, then lifts.
        mView.onTouchEvent(ev(t0, t0 + 180, MotionEvent.ACTION_MOVE, p(0, to, 0)));
        mView.onTouchEvent(ev(t0, t0 + 240, MotionEvent.ACTION_UP, p(0, to, 0)));
        idle();
        assertEquals("nothing typed", java.util.Collections.emptyList(), mTyped);
        assertEquals("swipe up toggles the toolbar", !shown, mToolbar.isToolbarShown());
    }

    /**
     * A fast swipe whose touch samples are far apart (as on a real phone) must not be taken
     * for two fingers by the old "sudden jump" filter, which typed the key at the end.
     */
    @Test
    public void fastSwipeUpTypesNothing() {
        boolean shown = mToolbar.isToolbarShown();
        float[] from = centre('v'), to = centre('t');
        long t0 = SystemClock.uptimeMillis();
        mView.onTouchEvent(ev(t0, t0, MotionEvent.ACTION_DOWN, p(0, from, 0)));
        // One frame's worth of a fast swipe: farther than the old filter's jump threshold.
        mView.onTouchEvent(ev(t0, t0 + 16, MotionEvent.ACTION_MOVE, p(0, to, 0)));
        mView.onTouchEvent(ev(t0, t0 + 40, MotionEvent.ACTION_UP, p(0, to, 0)));
        idle();
        assertEquals("nothing typed", java.util.Collections.emptyList(), mTyped);
        assertEquals("swipe up toggles the toolbar", !shown, mToolbar.isToolbarShown());
    }

    /**
     * Once a stroke is clearly a swipe, keys stop reacting: holding still on a key after
     * swiping onto it must not open its long-press popup (or type it).
     */
    @Test
    public void swipeThatPausesOpensNoPopup() throws Exception {
        boolean shown = mToolbar.isToolbarShown();
        float[] from = centre('c'), to = {centre('c')[0], centre('e')[1]};
        long t0 = SystemClock.uptimeMillis();
        mView.onTouchEvent(ev(t0, t0, MotionEvent.ACTION_DOWN, p(0, from, 0)));
        for (int i = 1; i <= 4; i++) {
            float[] at = {from[0] + (to[0] - from[0]) * i / 4, from[1] + (to[1] - from[1]) * i / 4};
            mView.onTouchEvent(ev(t0, t0 + i * 15, MotionEvent.ACTION_MOVE, p(0, at, 0)));
        }
        // Rest on the top row (an accented-letter key) longer than the long-press delay.
        org.robolectric.shadows.ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS);
        java.lang.reflect.Field popup = LatinKeyboardBaseView.class.getDeclaredField("mMiniKeyboardVisible");
        popup.setAccessible(true);
        assertEquals("no long-press popup", false, popup.getBoolean(mView));
        mView.onTouchEvent(ev(t0, t0 + 700, MotionEvent.ACTION_UP, p(0, to, 0)));
        idle();
        assertEquals("nothing typed", java.util.Collections.emptyList(), mTyped);
        assertEquals("swipe up toggles the toolbar", !shown, mToolbar.isToolbarShown());
    }

    private boolean previewShowing() throws Exception {
        java.lang.reflect.Field f = LatinKeyboardBaseView.class.getDeclaredField("mPreviewPopup");
        f.setAccessible(true);
        android.widget.PopupWindow w = (android.widget.PopupWindow) f.get(mView);
        return w != null && w.isShowing();
    }

    /** Key previews (the enlarged-key bubbles) must not keep showing during a swipe. */
    @Test
    public void noKeyPreviewsDuringASwipe() throws Exception {
        mView.setPreviewEnabled(true);
        float[] from = centre('c'), to = {centre('c')[0], centre('e')[1]};
        long t0 = SystemClock.uptimeMillis();
        mView.onTouchEvent(ev(t0, t0, MotionEvent.ACTION_DOWN, p(0, from, 0)));
        idle();
        org.junit.Assert.assertTrue("control: a touch shows its key's preview", previewShowing());
        for (int i = 1; i <= 4; i++) {
            float[] at = {from[0], from[1] + (to[1] - from[1]) * i / 4};
            mView.onTouchEvent(ev(t0, t0 + i * 15, MotionEvent.ACTION_MOVE, p(0, at, 0)));
            idle();
        }
        // Previews close 10ms after they are released.
        org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS);
        org.junit.Assert.assertFalse("no preview once it is a swipe", previewShowing());
        mView.onTouchEvent(ev(t0, t0 + 80, MotionEvent.ACTION_UP, p(0, to, 0)));
        idle();
    }

    /** Control for the test above: a plain long press on 'e' does open its popup here. */
    @Test
    public void longPressOnEOpensPopup() throws Exception {
        float[] e = centre('e');
        long t0 = SystemClock.uptimeMillis();
        mView.onTouchEvent(ev(t0, t0, MotionEvent.ACTION_DOWN, p(0, e, 0)));
        org.robolectric.shadows.ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS);
        java.lang.reflect.Field popup = LatinKeyboardBaseView.class.getDeclaredField("mMiniKeyboardVisible");
        popup.setAccessible(true);
        assertEquals("long press opens the popup", true, popup.getBoolean(mView));
    }

    /** Slides the held space bar 60px right in the given time; returns the arrow presses. */
    private int trackpadRight(long durationMs) {
        mTyped.clear();
        float[] space = centre(' ');
        long t0 = SystemClock.uptimeMillis();
        mView.onTouchEvent(ev(t0, t0, MotionEvent.ACTION_DOWN, p(0, space, 0)));
        org.robolectric.shadows.ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        long t = SystemClock.uptimeMillis();
        for (int i = 1; i <= 6; i++) {
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(durationMs / 6));
            float[] at = {space[0] + i * 10, space[1]};
            mView.onTouchEvent(ev(t0, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, p(0, at, 0)));
        }
        org.junit.Assert.assertEquals("label while the trackpad is on", "Moving the cursor",
                mView.gestureOverlayStatus());
        mView.onTouchEvent(ev(t0, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                p(0, new float[] {space[0] + 60, space[1]}, 0)));
        org.junit.Assert.assertNull("label gone after", mView.gestureOverlayStatus());
        idle();
        int arrows = 0;
        for (int code : mTyped) {
            org.junit.Assert.assertNotEquals("the held space bar types no space", (int) ' ', code);
            if (code == LatinKeyboardView.KEYCODE_DPAD_RIGHT) arrows++;
        }
        return arrows;
    }

    /** Holding space turns the keyboard into a trackpad; a faster slide goes further. */
    @Test
    public void spaceTrackpadMovesTheCursorWithAcceleration() {
        int slow = trackpadRight(600);
        int fast = trackpadRight(60);
        org.junit.Assert.assertTrue("slow slide moves the cursor right, got " + slow, slow > 0);
        org.junit.Assert.assertTrue("faster slide moves further (" + fast + " > " + slow + ")", fast > slow);
    }

    /** Two-thumb typing (bottom row, then top row) is not a swipe. */
    @Test
    public void twoThumbTypingIsNotASwipe() {
        boolean shown = mToolbar.isToolbarShown();
        float[] a = centre('v'), b = centre('t');
        long t0 = SystemClock.uptimeMillis();
        mView.onTouchEvent(ev(t0, t0, MotionEvent.ACTION_DOWN, p(0, a, 0)));
        mView.onTouchEvent(ev(t0, t0 + 40, MotionEvent.ACTION_POINTER_DOWN
                | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), p(0, a, 0), p(1, b, 0)));
        mView.onTouchEvent(ev(t0, t0 + 60, MotionEvent.ACTION_POINTER_UP, p(0, a, 0), p(1, b, 0)));
        // Thumb B drifts up a few pixels as it taps, as real thumbs do.
        for (int i = 1; i <= 3; i++) {
            mView.onTouchEvent(ev(t0, t0 + 60 + i * 8, MotionEvent.ACTION_MOVE, p(1, b, -i * 2)));
        }
        mView.onTouchEvent(ev(t0, t0 + 92, MotionEvent.ACTION_UP, p(1, b, -6)));
        idle();
        assertEquals("toolbar must not toggle", shown, mToolbar.isToolbarShown());
    }

    /** Holding a key and sliding up into its popup types the popup key (not a swipe). */
    @Test
    public void slidingUpIntoALongPressPopupTypesThePopupKey() {
        boolean shown = mToolbar.isToolbarShown();
        float[] e = centre('e');
        float keyHeight = mView.getKeyboard().getKeys().get(0).height;
        long t0 = SystemClock.uptimeMillis();
        mView.onTouchEvent(ev(t0, t0, MotionEvent.ACTION_DOWN, p(0, e, 0)));
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        long t = SystemClock.uptimeMillis();
        for (int i = 1; i <= 6; i++) {
            mView.onTouchEvent(ev(t0, t + i * 20, MotionEvent.ACTION_MOVE, p(0, e, -keyHeight * i / 6)));
        }
        mView.onTouchEvent(ev(t0, t + 140, MotionEvent.ACTION_UP, p(0, e, -keyHeight)));
        idle();
        assertEquals("not a swipe", shown, mToolbar.isToolbarShown());
        assertEquals("one popup key typed: " + mTyped, 1, mTyped.size());
        org.junit.Assert.assertNotEquals("a popup key, not the held key", (int) 'e', (int) mTyped.get(0));
    }
}
