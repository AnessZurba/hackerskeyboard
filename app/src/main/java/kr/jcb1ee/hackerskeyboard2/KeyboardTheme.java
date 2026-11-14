package kr.jcb1ee.hackerskeyboard2;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;

/**
 * A keyboard theme drawn from a few colours instead of images: background, key, pressed key,
 * active key (Enter, a modifier that is on), outline, label and hint, and the popup panel.
 * Keys are rounded rectangles; with key borders off, ordinary keys lose their shape and only
 * the label shows (a pressed or active key still lights up).
 *
 * <p>The numbers are the stored theme ids (pref_keyboard_layout), shared with the older
 * image-based themes in {@link KeyboardSwitcher}, so they must never change.
 */
final class KeyboardTheme {
    static final int MATERIAL_DARK = 4;
    static final int MATERIAL_LIGHT = 5;
    static final int MATERIAL_BLACK = 7;
    /** Wallpaper colours on Android 12+, light or dark with the system. */
    static final int MATERIAL_YOU = 8;
    static final int AMOLED_BLACK = 9;
    static final int HIGH_CONTRAST = 10;

    static final String PREF_KEY_BORDERS = "pref_key_borders";

    /** Theme ids of removed themes (ICS, Gingerbread, Transparent, ICS dark hints). */
    private static final int[] REMOVED = {0, 1, 3, 6};

    /** A removed theme's id becomes Material Black (the default); others stay. */
    static int current(int id) {
        for (int r : REMOVED) if (r == id) return MATERIAL_BLACK;
        return id;
    }

    /** Reads the stored theme, rewriting a removed one so settings show what is drawn. */
    static int migrate(android.content.SharedPreferences prefs) {
        int stored;
        try {
            stored = Integer.parseInt(prefs.getString(KeyboardSwitcher.PREF_KEYBOARD_LAYOUT,
                    KeyboardSwitcher.DEFAULT_LAYOUT_ID));
        } catch (NumberFormatException e) {
            stored = MATERIAL_BLACK;
        }
        int id = current(stored);
        if (id != stored) {
            prefs.edit().putString(KeyboardSwitcher.PREF_KEYBOARD_LAYOUT, String.valueOf(id)).apply();
        }
        return id;
    }

    /** Dark labels and icons (light backgrounds). */
    final boolean light;
    final int background;
    final int key;
    final int keyPressed;
    final int keyActive;
    /** Outline colour, or 0 for none. */
    final int keyStroke;
    final float strokeDp;
    final int text;
    final int hint;
    final int popup;
    final int popupStroke;

    private KeyboardTheme(boolean light, int background, int key, int keyPressed, int keyActive,
            int keyStroke, float strokeDp, int text, int hint, int popup, int popupStroke) {
        this.light = light;
        this.background = background;
        this.key = key;
        this.keyPressed = keyPressed;
        this.keyActive = keyActive;
        this.keyStroke = keyStroke;
        this.strokeDp = strokeDp;
        this.text = text;
        this.hint = hint;
        this.popup = popup;
        this.popupStroke = popupStroke;
    }

    static boolean isColourTheme(int id) {
        return id == MATERIAL_DARK || id == MATERIAL_LIGHT || id == MATERIAL_BLACK
                || id == MATERIAL_YOU || id == AMOLED_BLACK || id == HIGH_CONTRAST;
    }

    /** The theme for a stored id, or null for an image-based one. */
    static KeyboardTheme forId(Context context, int id) {
        switch (id) {
            case MATERIAL_DARK: return materialDark();
            case MATERIAL_LIGHT: return materialLight();
            case MATERIAL_BLACK:
                return new KeyboardTheme(false, 0xFF000000, 0xFF2C2C2E, 0xFF484849, 0xFF3A6186,
                        0, 0, 0xCCFFFFFF, 0x80FFFFFF, 0xFF000000, 0xFF3A3A3A);
            case AMOLED_BLACK:
                // Pure black throughout; keys are only outlined, so lit pixels stay few.
                return new KeyboardTheme(false, 0xFF000000, 0xFF000000, 0xFF2A2A2A, 0xFF1E3A5F,
                        0xFF2E2E2E, 1, 0xE6FFFFFF, 0x80FFFFFF, 0xFF000000, 0xFF2E2E2E);
            case HIGH_CONTRAST:
                // White on black with white outlines, yellow hints; active keys dark blue.
                return new KeyboardTheme(false, 0xFF000000, 0xFF000000, 0xFF5A5A5A, 0xFF0D47A1,
                        0xFFFFFFFF, 1.5f, 0xFFFFFFFF, 0xFFFFEB3B, 0xFF000000, 0xFFFFFFFF);
            case MATERIAL_YOU: return materialYou(context);
            default: return null;
        }
    }

    private static KeyboardTheme materialDark() {
        return new KeyboardTheme(false, 0xFF263238, 0xFF37474F, 0xFF546E7A, 0xFF1565C0,
                0, 0, 0xCCFFFFFF, 0x80FFFFFF, 0xFF263238, 0xFF4F5B62);
    }

    private static KeyboardTheme materialLight() {
        return new KeyboardTheme(true, 0xFFECEFF1, 0xFFFFFFFF, 0xFFB0BEC5, 0xFF90CAF9,
                0, 0, 0xFF000000, 0xB337474F, 0xFFECEFF1, 0xFFB0BEC5);
    }

    static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static KeyboardTheme materialYou(Context context) {
        boolean night = isNight(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return night ? materialDark() : materialLight();
        }
        if (night) {
            return new KeyboardTheme(false,
                    c(context, android.R.color.system_neutral1_900),
                    c(context, android.R.color.system_neutral2_800),
                    c(context, android.R.color.system_neutral2_600),
                    c(context, android.R.color.system_accent1_700),
                    0, 0,
                    c(context, android.R.color.system_neutral1_50),
                    c(context, android.R.color.system_neutral2_300),
                    c(context, android.R.color.system_neutral1_800),
                    c(context, android.R.color.system_neutral2_600));
        }
        return new KeyboardTheme(true,
                c(context, android.R.color.system_neutral2_100),
                c(context, android.R.color.system_neutral1_10),
                c(context, android.R.color.system_neutral2_300),
                c(context, android.R.color.system_accent1_200),
                0, 0,
                c(context, android.R.color.system_neutral1_900),
                c(context, android.R.color.system_neutral2_600),
                c(context, android.R.color.system_neutral1_50),
                c(context, android.R.color.system_neutral2_300));
    }

    private static int c(Context context, int id) {
        return context.getColor(id);
    }

    /** Whether two themes would draw the same (Material You changes with wallpaper and night). */
    boolean sameAs(KeyboardTheme o) {
        return o != null && light == o.light && background == o.background && key == o.key
                && keyPressed == o.keyPressed && keyActive == o.keyActive && text == o.text
                && hint == o.hint && popup == o.popup;
    }

    // ------------------------------------------------------------------ drawables

    Drawable keyboardBackground() {
        return new ColorDrawable(background);
    }

    /** Key background with the same states as the old btn_keyboard_key_material_* selectors. */
    Drawable keyBackground(Context context, boolean borders) {
        float d = context.getResources().getDisplayMetrics().density;
        Drawable normal = borders ? shape(key, keyStroke, d) : new ColorDrawable(Color.TRANSPARENT);
        Drawable pressed = shape(keyPressed, keyStroke, d);
        Drawable active = shape(keyActive, keyStroke, d);
        StateListDrawable s = new StateListDrawable();
        int A = android.R.attr.state_active, P = android.R.attr.state_pressed,
                K = android.R.attr.state_checkable, C = android.R.attr.state_checked,
                E = android.R.attr.state_empty;
        // Action keys.
        s.addState(new int[] {A, P}, pressed);
        s.addState(new int[] {A}, active);
        // Toggle keys.
        s.addState(new int[] {K, C, P}, pressed);
        s.addState(new int[] {K, P}, pressed);
        s.addState(new int[] {K, C}, active);
        s.addState(new int[] {K}, normal);
        // Empty background keys.
        s.addState(new int[] {E, P}, pressed);
        s.addState(new int[] {E}, new ColorDrawable(Color.TRANSPARENT));
        // Normal keys.
        s.addState(new int[] {P}, pressed);
        s.addState(new int[0], normal);
        return s;
    }

    /** Keys inside a popup panel: no shape until pressed. */
    Drawable popupKeyBackground(Context context) {
        float d = context.getResources().getDisplayMetrics().density;
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_pressed}, shape(keyPressed, 0, d));
        s.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
        return s;
    }

    Drawable popupPanel(Context context) {
        float d = context.getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        g.setColor(popup);
        g.setStroke(Math.max(1, Math.round(d)), popupStroke);
        g.setCornerRadius(10 * d);
        return g;
    }

    private Drawable shape(int fill, int stroke, float d) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(5 * d);
        if (stroke != 0) g.setStroke(Math.max(1, Math.round(strokeDp * d)), stroke);
        int inset = Math.round(1.5f * d);
        return new InsetDrawable(g, inset, inset, inset, inset);
    }
}
