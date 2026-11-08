package kr.jcb1ee.hackerskeyboard2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
/**
 * Renders the keyboard (toolbar and keys) to a PNG, to look at layout and theme changes
 * without a phone. Skipped unless asked for:
 *   ./gradlew testDebugUnitTest --tests '*RenderKeyboard*' -PrenderOut=/path/keyboard.png
 */
public class RenderKeyboardTest {
    @Test
    public void render() throws Exception {
        String out = System.getProperty("renderOut");
        org.junit.Assume.assumeTrue("set -PrenderOut to render", out != null);
        LatinIME ime = Robolectric.buildService(LatinIME.class).create().get();
        KeyboardToolbar toolbar = (KeyboardToolbar) ime.onCreateInputView();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        String panel = System.getProperty("renderPanel");
        if ("emoji".equals(panel) || "emojisearch".equals(panel)) {
            int w0 = ime.getResources().getDisplayMetrics().widthPixels;
            toolbar.measure(View.MeasureSpec.makeMeasureSpec(w0, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            toolbar.layout(0, 0, w0, toolbar.getMeasuredHeight());
            View bar = ((android.view.ViewGroup) toolbar.getChildAt(0)).getChildAt(2);
            ((android.view.ViewGroup) bar).getChildAt(0).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        }
        if ("emojisearch".equals(panel)) {
            View body = ((android.view.ViewGroup) toolbar.getChildAt(1)).getChildAt(1);
            View search = ((android.view.ViewGroup) ((android.view.ViewGroup) body).getChildAt(0)).getChildAt(0);
            search.performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            for (char c : "cat".toCharArray()) ime.onKey(c, new int[] {c}, 0, 0);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        }
        if ("clipboard".equals(panel)) {
            ClipboardHistory h = ClipboardHistory.get(ime);
            h.add("ssh user@example.com -p 2222");
            h.add("The quick brown fox jumps over the lazy dog, again and again and again");
            h.add("مرحبا، كيف حالك؟");
            h.add("git push -u origin main");
            h.add("https://github.com/AnessZurba/hackerskeyboard");
            h.add("Meeting at 10:30 tomorrow");
            h.setPinned(java.util.Collections.singletonList(h.clips().get(4)), true);
            View bar = ((android.view.ViewGroup) toolbar.getChildAt(0)).getChildAt(1);
            ((android.view.ViewGroup) bar).getChildAt(0).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        }
        int w = ime.getResources().getDisplayMetrics().widthPixels;
        toolbar.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        toolbar.layout(0, 0, w, toolbar.getMeasuredHeight());
        // Panels size themselves to the keyboard once laid out: lay out twice.
        toolbar.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        toolbar.layout(0, 0, w, toolbar.getMeasuredHeight());
        Bitmap bmp = Bitmap.createBitmap(w, toolbar.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        toolbar.draw(new Canvas(bmp));
        try (java.io.FileOutputStream file = new java.io.FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, file);
        }
    }
}
