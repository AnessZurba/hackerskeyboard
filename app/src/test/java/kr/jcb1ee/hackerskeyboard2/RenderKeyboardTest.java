package kr.jcb1ee.hackerskeyboard2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w411dp-h891dp-xxhdpi")
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
        // -PrenderTheme=<theme id> and -PrenderBorders=false pick the look.
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
        if (System.getProperty("renderTheme") != null) {
            prefs.edit().putString(kr.jcb1ee.hackerskeyboard2.theme.Themes.PREF_THEME,
                    System.getProperty("renderTheme")).commit();
        }
        if (System.getProperty("renderBorders") != null) {
            prefs.edit().putBoolean(kr.jcb1ee.hackerskeyboard2.theme.Themes.PREF_KEY_BORDERS,
                    Boolean.parseBoolean(System.getProperty("renderBorders"))).commit();
        }
        // -PrenderVariant=light|dark|system sets "Light or dark"; -PrenderNight=true makes the
        // system dark.
        if (System.getProperty("renderVariant") != null) {
            prefs.edit().putString(kr.jcb1ee.hackerskeyboard2.theme.Themes.PREF_MODE,
                    System.getProperty("renderVariant")).commit();
        }
        org.robolectric.RuntimeEnvironment.setQualifiers(
                Boolean.parseBoolean(System.getProperty("renderNight")) ? "+night" : "+notnight");
        // -PrenderMode=2 draws the full 5-row (PC) layout (pref_keyboard_mode_portrait).
        if (System.getProperty("renderMode") != null) {
            prefs.edit().putString("pref_keyboard_mode_portrait",
                    System.getProperty("renderMode")).commit();
        }
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
        // The word suggestion strip, as while typing "helo" (with a pending auto-correction).
        if ("words".equals(panel)) toolbar.showSuggestions("helo", "hello", "help", true);
        if ("words-plain".equals(panel)) toolbar.showSuggestions("help", "hello", "held", false);
        if ("words-rtl".equals(panel)) toolbar.showSuggestions("שלום", "שלומי", "שלומות", false);
        // The more-suggestions panel over the keys (words-more, words-more-rtl), and the
        // toolbar after the strip's arrow (words-toolbar).
        java.util.List<String> more = java.util.Arrays.asList("hello", "help", "helo", "held",
                "helm", "hell", "hello's", "helot", "helps", "helped", "helper", "helpful",
                "helium", "helix", "helmet", "Helen", "Helena", "helping", "hellish", "helms");
        if ("words-more".equals(panel) || "words-more-open".equals(panel)) {
            toolbar.showSuggestions("helo", "hello", "help", true, more);
        }
        if ("words-more-rtl".equals(panel)) {
            toolbar.showSuggestions("שלום", "שלומי", "שלומות", false, java.util.Arrays.asList(
                    "שלומי", "שלום", "שלומות", "שלוש", "שלושה", "שלי", "שלה", "שלו", "שלנו",
                    "שלכם", "שלהם", "שלווה", "שליח", "שלט"));
        }
        if (panel != null && panel.startsWith("words-more")) {
            int w0 = ime.getResources().getDisplayMetrics().widthPixels;
            toolbar.measure(View.MeasureSpec.makeMeasureSpec(w0, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            toolbar.layout(0, 0, w0, toolbar.getMeasuredHeight());
            if (!"words-more".equals(panel)) toolbar.openMoreSuggestions();
        }
        if ("words-toolbar".equals(panel)) {
            toolbar.showSuggestions("helo", "hello", "help", true);
            toolbar.suggestionStrip().toolbarButton().performClick();
        }
        // Let the animations finish.
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        // A tip in the row (tip, tip-long), and the gesture label (label-delete, label-trackpad).
        if ("tip".equals(panel)) toolbar.showTip(HintManager.Tip.BACKSPACE_SWIPE);
        if ("tip-long".equals(panel)) toolbar.showTip(HintManager.Tip.SELECT_TRACKPAD);
        if ("label-delete".equals(panel)) {
            String[] label = GestureLabel.deleteSwipe(ime.getResources(), 3, false);
            toolbar.setGestureLabel(label[0], label[1]);
        }
        if ("label-trackpad".equals(panel)) {
            String[] label = GestureLabel.trackpad(ime.getResources(), false);
            toolbar.setGestureLabel(label[0], label[1]);
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
        // -PrenderPress=g,Esc,+Ctrl draws those keys pressed (a "+" key: turned on instead).
        String press = System.getProperty("renderPress");
        if (press != null) {
            LatinKeyboardView view = KeyboardSwitcher.getInstance().getInputView();
            for (String name : press.split(",")) {
                boolean on = name.startsWith("+");
                String label = on ? name.substring(1) : name;
                for (Keyboard.Key key : view.getKeyboard().getKeys()) {
                    if (key.label == null || !label.contentEquals(key.label)) continue;
                    if (on) key.on = true; else key.pressed = true;
                }
            }
            view.invalidateAllKeys();
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
        // -PrenderLongPress=e opens that key's long-press popup and draws it above the key.
        View popup = null;
        int popupX = 0, popupY = 0;
        String longPress = System.getProperty("renderLongPress");
        if (longPress != null) {
            LatinKeyboardView view = KeyboardSwitcher.getInstance().getInputView();
            for (Keyboard.Key key : view.getKeyboard().getKeys()) {
                if (key.label == null || !longPress.contentEquals(key.label)) continue;
                try {
                    view.onLongPress(key);
                } catch (RuntimeException e) {
                    // no window to show it in: the popup is laid out all the same
                }
                java.lang.reflect.Field f =
                        LatinKeyboardBaseView.class.getDeclaredField("mMiniKeyboardContainer");
                f.setAccessible(true);
                popup = (View) f.get(view);
                popup.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                popup.layout(0, 0, popup.getMeasuredWidth(), popup.getMeasuredHeight());
                int[] at = new int[2];
                view.getLocationInWindow(at);
                int[] base = new int[2];
                toolbar.getLocationInWindow(base);
                popupX = Math.max(0, Math.min(w - popup.getMeasuredWidth(),
                        at[0] - base[0] + key.x + key.width / 2 - popup.getMeasuredWidth() / 2));
                popupY = Math.max(0, at[1] - base[1] + key.y - popup.getMeasuredHeight()
                        + popup.getPaddingBottom());
                view.invalidateAllKeys();
                break;
            }
        }
        Canvas canvas = new Canvas(bmp);
        toolbar.draw(canvas);
        if (popup != null) {
            canvas.translate(popupX, popupY);
            popup.draw(canvas);
        }
        try (java.io.FileOutputStream file = new java.io.FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, file);
        }
    }
}
