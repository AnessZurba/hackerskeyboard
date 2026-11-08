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
        int w = ime.getResources().getDisplayMetrics().widthPixels;
        toolbar.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        toolbar.layout(0, 0, w, toolbar.getMeasuredHeight());
        Bitmap bmp = Bitmap.createBitmap(w, toolbar.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        toolbar.draw(new Canvas(bmp));
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
