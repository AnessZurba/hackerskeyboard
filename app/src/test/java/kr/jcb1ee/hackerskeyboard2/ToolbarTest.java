package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** Starts the keyboard service and works the toolbar: each panel, clipboard, toggling. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ToolbarTest {
    private static void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test
    public void toolbarPanelsOpenAndClose() {
        LatinIME ime = Robolectric.buildService(LatinIME.class).create().get();
        View input = ime.onCreateInputView();
        idle();
        assertTrue("input view is the toolbar container", input instanceof KeyboardToolbar);
        KeyboardToolbar toolbar = (KeyboardToolbar) input;
        LinearLayout bar = (LinearLayout) toolbar.getChildAt(0);
        FrameLayout body = (FrameLayout) toolbar.getChildAt(1);
        assertEquals(3, bar.getChildCount());
        assertTrue(body.getChildAt(0) instanceof LatinKeyboardView);
        assertTrue(toolbar.isToolbarShown());

        ClipboardHistory.get(ime).add("hello clipboard");

        for (int i = 0; i < 3; i++) {
            bar.getChildAt(i).performClick();
            idle();
            assertEquals("panel " + i + " opened", 2, body.getChildCount());
            assertEquals(View.INVISIBLE, body.getChildAt(0).getVisibility());
            View panel = body.getChildAt(1);
            assertNotNull(panel);
            // Second tap goes back to the keyboard.
            bar.getChildAt(i).performClick();
            idle();
            assertEquals(1, body.getChildCount());
            assertEquals(View.VISIBLE, body.getChildAt(0).getVisibility());
        }

        // Clipboard panel lists the clip.
        bar.getChildAt(1).performClick();
        idle();
        View clipPanel = body.getChildAt(1);
        assertTrue(containsText(clipPanel, "hello clipboard"));
        toolbar.closePanel();

        // Emoji panel has a grid with emoji.
        bar.getChildAt(2).performClick();
        idle();
        assertTrue(containsText(body.getChildAt(1), "😀"));
        toolbar.closePanel();

        toolbar.toggleToolbar();
        idle();
        Robolectric.flushForegroundThreadScheduler();
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertFalse(toolbar.isToolbarShown());
        toolbar.toggleToolbar();
        assertTrue(toolbar.isToolbarShown());
    }

    private static boolean containsText(View v, String text) {
        if (v instanceof android.widget.TextView
                && ((android.widget.TextView) v).getText().toString().contains(text)) return true;
        if (v instanceof android.widget.AdapterView) {
            android.widget.Adapter a = ((android.widget.AdapterView<?>) v).getAdapter();
            for (int i = 0; i < a.getCount(); i++) if (text.equals(a.getItem(i))) return true;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (containsText(g.getChildAt(i), text)) return true;
        }
        return false;
    }
}
