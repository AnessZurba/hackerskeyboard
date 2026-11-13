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
@Config(sdk = 36)
public class ToolbarTest {
    private static void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    /** Edge to edge: the keys stay above the navigation bar, whose space takes the keyboard colour. */
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void keysStayAboveTheNavigationBar() {
        LatinIME ime = Robolectric.buildService(LatinIME.class).create().get();
        KeyboardToolbar toolbar = (KeyboardToolbar) ime.onCreateInputView();
        idle();
        // As the window's decor view gets them (LatinIME listens there).
        ime.setNavigationBar(new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(),
                        androidx.core.graphics.Insets.of(0, 0, 0, 63))
                .setInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.navigationBars(),
                        androidx.core.graphics.Insets.of(0, 0, 0, 63))
                .build());
        int w = 1080;
        for (int pass = 0; pass < 3; pass++) { // the padding is set after a layout; lay out again
            idle();
            toolbar.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            toolbar.layout(0, 0, w, toolbar.getMeasuredHeight());
        }
        assertEquals(63, toolbar.getPaddingBottom());
        View body = toolbar.getChildAt(1);
        assertEquals("keys end where the navigation bar starts",
                toolbar.getHeight() - 63, body.getBottom());
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, toolbar.getHeight(),
                android.graphics.Bitmap.Config.ARGB_8888);
        toolbar.draw(new android.graphics.Canvas(bmp));
        assertEquals("navigation bar space has the keyboard colour",
                toolbar.backgroundColour(), bmp.getPixel(w / 2, toolbar.getHeight() - 10));
    }

    /** The navigation bar's size is taken from the keyboard window's decor view. */
    @Test
    public void navigationBarReadFromTheWindow() {
        LatinIME ime = Robolectric.buildService(LatinIME.class).create().get();
        ime.onCreateInputView();
        idle();
        View decor = ime.getWindow().getWindow().getDecorView();
        decor.dispatchApplyWindowInsets(new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(),
                        androidx.core.graphics.Insets.of(0, 0, 0, 48))
                .setInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.navigationBars(),
                        androidx.core.graphics.Insets.of(0, 0, 0, 48))
                .build().toWindowInsets());
        idle();
        assertEquals(48, ime.navigationBarInsets().bottom);
    }

    /**
     * The keyboard's own gesture bar (a caption bar) counts while shown and not when hidden
     * (landscape left an empty band when its size was kept).
     */
    @Test
    public void gestureBarCountsOnlyWhileShown() {
        LatinIME ime = Robolectric.buildService(LatinIME.class).create().get();
        ime.onCreateInputView();
        idle();
        int cap = androidx.core.view.WindowInsetsCompat.Type.captionBar();
        androidx.core.graphics.Insets bar = androidx.core.graphics.Insets.of(0, 0, 0, 180);
        ime.setNavigationBar(new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(cap, bar).setInsetsIgnoringVisibility(cap, bar).setVisible(cap, true)
                .build());
        assertEquals(180, ime.navigationBarInsets().bottom);
        // Hidden, as the platform reports it: nothing shown, the stable size still there.
        ime.setNavigationBar(new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(cap, androidx.core.graphics.Insets.NONE)
                .setInsetsIgnoringVisibility(cap, bar).setVisible(cap, false)
                .build());
        assertEquals(0, ime.navigationBarInsets().bottom);
    }

    /** Landscape with a cutout at one side: the keys fit in what is left, none cut off. */
    @Test
    public void keysFitBesideACutout() {
        LatinIME ime = Robolectric.buildService(LatinIME.class).create().get();
        KeyboardToolbar toolbar = (KeyboardToolbar) ime.onCreateInputView();
        idle();
        int cut = androidx.core.view.WindowInsetsCompat.Type.displayCutout();
        ime.setNavigationBar(new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(cut, androidx.core.graphics.Insets.of(100, 0, 0, 0)).build());
        idle();
        int screen = ime.getResources().getDisplayMetrics().widthPixels;
        LatinKeyboardView view = (LatinKeyboardView) ((ViewGroup) toolbar.getChildAt(1)).getChildAt(0);
        Keyboard kb = view.getKeyboard();
        assertEquals(100, toolbar.getPaddingLeft());
        assertTrue("keyboard " + kb.getMinWidth() + " fits " + (screen - 100),
                kb.getMinWidth() <= screen - 100);
        for (Keyboard.Key k : kb.getKeys()) {
            assertTrue("key at " + k.x + "+" + k.width, k.x + k.width <= screen - 100 + 1);
        }
        // Back to no cutout: the keys get their full width again.
        ime.setNavigationBar(new androidx.core.view.WindowInsetsCompat.Builder().build());
        idle();
        assertTrue(view.getKeyboard().getMinWidth() > screen - 100);
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
        assertEquals("three buttons, the (hidden) autofill chip rows and their > button",
                6, bar.getChildCount());
        assertTrue(body.getChildAt(0) instanceof LatinKeyboardView);
        assertTrue(toolbar.isToolbarShown());

        ClipboardHistory.get(ime).add("hello clipboard");

        for (int i = 0; i < 3; i++) {
            button(bar, i).performClick();
            idle();
            assertEquals("panel " + i + " opened", 2, body.getChildCount());
            assertEquals(View.INVISIBLE, body.getChildAt(0).getVisibility());
            View panel = body.getChildAt(1);
            assertNotNull(panel);
            // Second tap goes back to the keyboard.
            button(bar, i).performClick();
            idle();
            assertEquals(1, body.getChildCount());
            assertEquals(View.VISIBLE, body.getChildAt(0).getVisibility());
        }

        // Clipboard panel lists the clip.
        button(bar, 1).performClick();
        idle();
        View clipPanel = body.getChildAt(1);
        assertTrue(containsText(clipPanel, "hello clipboard"));
        toolbar.closePanel();

        // Emoji panel has a grid with emoji.
        button(bar, 2).performClick();
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

    /** Hiding the toolbar keeps the input view's height and gives the row's space to the app. */
    @Test
    public void hidingKeepsHeightAndGivesSpaceToApp() {
        LatinIME ime = Robolectric.buildService(LatinIME.class).create().get();
        KeyboardToolbar toolbar = (KeyboardToolbar) ime.onCreateInputView();
        idle();
        if (!toolbar.isToolbarShown()) toolbar.toggleToolbar();
        settle(toolbar);
        int height = toolbar.getMeasuredHeight();
        assertEquals(0, toolbar.spaceGivenToApp());

        toolbar.toggleToolbar(); // hide
        settle(toolbar);
        assertEquals("window height unchanged", height, toolbar.getMeasuredHeight());
        assertEquals("row's space goes to the app", toolbar.getChildAt(0).getHeight(),
                toolbar.spaceGivenToApp());
        assertTrue(toolbar.spaceGivenToApp() > 0);

        toolbar.toggleToolbar(); // show
        settle(toolbar);
        assertEquals(height, toolbar.getMeasuredHeight());
        assertEquals(0, toolbar.spaceGivenToApp());
    }

    /** Autofill chips show in place of the buttons, revealing a hidden toolbar meanwhile. */
    @Test
    public void autofillChipsRevealHiddenToolbar() {
        LatinIME ime = Robolectric.buildService(LatinIME.class).create().get();
        KeyboardToolbar toolbar = (KeyboardToolbar) ime.onCreateInputView();
        idle();
        if (toolbar.isToolbarShown()) toolbar.toggleToolbar(); // user keeps it hidden
        settle(toolbar);
        assertTrue("hidden row's space is the app's", toolbar.spaceGivenToApp() > 0);
        LinearLayout bar = (LinearLayout) toolbar.getChildAt(0);

        android.widget.TextView chip = new android.widget.TextView(ime);
        chip.setText("user@example.com");
        toolbar.showChips(java.util.Collections.<View>singletonList(chip));
        settle(toolbar);
        assertEquals(View.VISIBLE, bar.getVisibility());
        assertEquals("space taken back for the chips", 0, toolbar.spaceGivenToApp());
        assertEquals(View.GONE, bar.getChildAt(0).getVisibility()); // buttons make way
        assertFalse("the setting is not changed", toolbar.isToolbarShown());
        View hide = null;
        for (int i = 0; i < bar.getChildCount(); i++) {
            if (ime.getString(R.string.toolbar_hide_chips).contentEquals(
                    bar.getChildAt(i).getContentDescription() == null ? "" : bar.getChildAt(i).getContentDescription())) {
                hide = bar.getChildAt(i);
            }
        }
        assertNotNull("toolbar kept hidden: the button hides the strip (v, not >)", hide);
        hide.performClick();
        assertEquals("no buttons while it fades out", View.GONE, bar.getChildAt(0).getVisibility());
        settle(toolbar);
        assertTrue("strip hidden again", toolbar.spaceGivenToApp() > 0);
        assertEquals("buttons ready for next time", View.VISIBLE, bar.getChildAt(0).getVisibility());
        toolbar.showChips(java.util.Collections.<View>singletonList(chip));
        settle(toolbar);

        toolbar.showChips(java.util.Collections.<View>emptyList());
        settle(toolbar);
        assertEquals(View.VISIBLE, bar.getChildAt(0).getVisibility());
        assertTrue("hidden again", toolbar.spaceGivenToApp() > 0);
    }

    /** Pinned chips stay at the start; ">" brings the regular buttons back. */
    @Test
    public void pinnedChipsAndShowButtons() {
        LatinIME ime = Robolectric.buildService(LatinIME.class).create().get();
        KeyboardToolbar toolbar = (KeyboardToolbar) ime.onCreateInputView();
        idle();
        if (!toolbar.isToolbarShown()) toolbar.toggleToolbar();
        LinearLayout bar = (LinearLayout) toolbar.getChildAt(0);
        android.widget.TextView chip = new android.widget.TextView(ime);
        chip.setText("Open 1Password");
        // Inline suggestion views arrive sized by the password manager's renderer.
        chip.setLayoutParams(new ViewGroup.LayoutParams(120, 90));
        android.widget.TextView icon = new android.widget.TextView(ime);
        icon.setText("1P");
        icon.setLayoutParams(new ViewGroup.LayoutParams(90, 90)); // sized by the renderer
        toolbar.showChips(java.util.Collections.<View>singletonList(chip),
                java.util.Collections.<View>singletonList(icon));
        settle(toolbar);
        assertEquals(View.GONE, bar.getChildAt(0).getVisibility());
        View dismiss = null;
        for (int i = 0; i < bar.getChildCount(); i++) {
            View c = bar.getChildAt(i);
            if (ime.getString(R.string.toolbar_show_buttons).contentEquals(
                    c.getContentDescription() == null ? "" : c.getContentDescription())) dismiss = c;
        }
        assertNotNull(dismiss);
        assertEquals(View.VISIBLE, dismiss.getVisibility());
        assertTrue("> on screen", dismiss.getWidth() > 0 && dismiss.getRight() <= bar.getWidth());
        assertEquals("the chip keeps the size it came with", 120, chip.getWidth());
        assertEquals("pinned chip shown", View.VISIBLE, ((View) icon.getParent()).getVisibility());
        assertTrue("pinned icon at the start", icon.getLeft() + ((View) icon.getParent()).getLeft() < 40);
        View viewport = (View) chip.getParent().getParent(); // the scrolling row
        float chipCentre = chip.getLeft() + chip.getWidth() / 2f;
        assertEquals("the chip is centred, as on Samsung", viewport.getWidth() / 2f, chipCentre, 3);
        dismiss.performClick();
        settle(toolbar);
        assertFalse(toolbar.hasChips());
        assertEquals(View.VISIBLE, bar.getChildAt(0).getVisibility());
        assertEquals(View.GONE, dismiss.getVisibility());
    }

    /** The keyboard asks password managers for chips in its own style, not a white pill. */
    @Test
    public void autofillChipsUseTheKeyboardStyle() {
        LatinIME ime = Robolectric.buildService(LatinIME.class).create().get();
        ime.onCreateInputView();
        idle();
        android.view.inputmethod.InlineSuggestionsRequest request =
                ime.onCreateInlineSuggestionsRequest(new android.os.Bundle());
        assertNotNull(request);
        android.os.Bundle style = request.getInlinePresentationSpecs().get(0).getStyle();
        assertTrue("a v1 style is offered",
                androidx.autofill.inline.UiVersions.getVersions(style).contains(
                        androidx.autofill.inline.UiVersions.INLINE_UI_VERSION_1));
        assertTrue("with our own chip background", style.toString().length() > 0
                && containsKey(style, "chip"));
    }

    private static boolean containsKey(android.os.Bundle b, String part) {
        for (String k : b.keySet()) {
            if (k.toLowerCase(java.util.Locale.ROOT).contains(part)) return true;
            Object v = b.get(k);
            if (v instanceof android.os.Bundle && containsKey((android.os.Bundle) v, part)) return true;
        }
        return false;
    }

    private static void settle(View v) {
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        v.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        v.layout(0, 0, 320, v.getMeasuredHeight());
    }

    private static View button(LinearLayout bar, int i) {
        return ((ViewGroup) bar.getChildAt(i)).getChildAt(0);
    }

    private static boolean containsText(View v, String text) {
        if (v instanceof android.widget.TextView
                && ((android.widget.TextView) v).getText().toString().contains(text)) return true;
        if (v instanceof android.widget.AdapterView) {
            android.widget.Adapter a = ((android.widget.AdapterView<?>) v).getAdapter();
            for (int i = 0; i < a.getCount(); i++) {
                Object item = a.getItem(i);
                if (text.equals(item) || (item instanceof ClipboardHistory.Clip
                        && text.equals(((ClipboardHistory.Clip) item).text))) return true;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (containsText(g.getChildAt(i), text)) return true;
        }
        return false;
    }
}
