package kr.jcb1ee.hackerskeyboard2;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
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
    /** Black on white with black outlines; replaced the Stone image theme (id 2). */
    static final int HIGH_CONTRAST_LIGHT = 11;
    /** Round typewriter keys: ivory faces in chrome rims on a dark body. */
    static final int TYPEWRITER = 12;
    /** Sculpted keycaps, retro beige alphas, grey modifiers, red Esc and Enter. */
    static final int KEYCAPS_RETRO = 13;
    /** Sculpted keycaps, black alphas with pink legends, pink Esc and Enter. */
    static final int KEYCAPS_MIDNIGHT = 14;
    /** Green phosphor on black, outlined cells and glowing labels. */
    static final int TERMINAL = 15;
    /** Stone (bold), an image theme replaced by HIGH_CONTRAST_LIGHT. */
    private static final int STONE = 2;

    static final String PREF_KEY_BORDERS = "pref_key_borders";

    /** Theme ids of removed themes (ICS, Gingerbread, Transparent, ICS dark hints). */
    private static final int[] REMOVED = {0, 1, 3, 6};

    /** A removed theme's id becomes Material Black (the default); others stay. */
    static int current(int id) {
        if (id == STONE) return HIGH_CONTRAST_LIGHT;
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
    /** Labels in the popup panel (styled themes: the panel is not the key face). */
    final int popupText;
    /** How keys are shaped and coloured beyond the flat rounded rectangle, or null. */
    final KeyStyle style;

    private KeyboardTheme(boolean light, int background, int key, int keyPressed, int keyActive,
            int keyStroke, float strokeDp, int text, int hint, int popup, int popupStroke) {
        this(light, background, key, keyPressed, keyActive, keyStroke, strokeDp, text, hint,
                popup, popupStroke, text, null);
    }

    private KeyboardTheme(boolean light, int background, int key, int keyPressed, int keyActive,
            int keyStroke, float strokeDp, int text, int hint, int popup, int popupStroke,
            int popupText, KeyStyle style) {
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
        this.popupText = popupText;
        this.style = style;
    }

    // ------------------------------------------------------------------ key styles

    /** Plain letters, digits and the space bar. */
    static final int CLASS_NORMAL = 0;
    /** Modifiers and editing keys: Shift, Ctrl, Alt, Tab, Backspace, arrows, F-keys... */
    static final int CLASS_MODIFIER = 1;
    /** Esc and Enter, in an accent colour. */
    static final int CLASS_ACCENT = 2;

    /** Which colours a key takes in a styled theme. */
    static int keyClass(Keyboard.Key key) {
        int code = key.codes != null && key.codes.length > 0 ? key.codes[0] : 0;
        if (code == LatinIME.ASCII_ENTER || code == LatinKeyboardView.KEYCODE_ESCAPE) {
            return CLASS_ACCENT;
        }
        if (key.modifier && code != LatinIME.ASCII_SPACE) return CLASS_MODIFIER;
        return CLASS_NORMAL;
    }

    /** One kind of key: its face, the skirt around it (keycaps), and its label. */
    static final class Cap {
        final int face;
        final int skirt;
        final int text;

        Cap(int face, int skirt, int text) {
            this.face = face;
            this.skirt = skirt;
            this.text = text;
        }
    }

    /**
     * A key style: a shape ({@link KeyCapDrawable#ROUND}, SCULPTED or TERMINAL) and colours
     * per key class, a face for a toggle that is on, the label font and an optional glow.
     */
    static final class KeyStyle {
        final int shape;
        final Cap[] caps;
        /** A modifier (or caps lock) that is on; its label keeps its class colour. */
        final Cap active;
        /** Metal rim (round) or outline (terminal). */
        final int rim;
        final String font;
        final int fontStyle;
        /** Label glow colour, or 0. */
        final int glow;
        final float glowDp;
        /** The usual key width in pixels, shared by this style's drawables (0: unknown). */
        final float[] unit = new float[1];

        KeyStyle(int shape, Cap normal, Cap modifier, Cap accent, Cap active, int rim,
                String font, int fontStyle, int glow, float glowDp) {
            this.shape = shape;
            this.caps = new Cap[] {normal, modifier, accent};
            this.active = active;
            this.rim = rim;
            this.font = font;
            this.fontStyle = fontStyle;
            this.glow = glow;
            this.glowDp = glowDp;
        }

        /** The label font, or null for the keyboard's own. */
        Typeface typeface() {
            return font == null ? null : Typeface.create(font, fontStyle);
        }
    }

    private static KeyboardTheme styled(int background, int hint, int popup, int popupStroke,
            int popupText, KeyStyle s) {
        Cap n = s.caps[CLASS_NORMAL];
        return new KeyboardTheme(false, background, n.face,
                KeyCapDrawable.blend(n.face, Color.BLACK, 0.15f), s.active.face, 0, 0, n.text,
                hint, popup, popupStroke, popupText, s);
    }

    /** Ivory keys with dark ink, black glass modifiers, an oxblood Enter, chrome rims. */
    private static KeyboardTheme typewriter() {
        return styled(0xFF1C1713, 0xFF8A6A3E, 0xFF2A231D, 0xFFB9B4A8, 0xFFF1E7CF,
                new KeyStyle(KeyCapDrawable.ROUND,
                        new Cap(0xFFF1E7CF, 0, 0xFF2A1F16),
                        new Cap(0xFF2B2724, 0, 0xFFEDE3C8),
                        new Cap(0xFF8E2B21, 0, 0xFFF6EAD2),
                        new Cap(0xFFA8342A, 0, 0),
                        0xFFA9A9A4, "serif-monospace", Typeface.BOLD, 0, 0));
    }

    /** Beige alphas, grey modifiers and red Esc/Enter, as on classic beige computers. */
    private static KeyboardTheme keycapsRetro() {
        return styled(0xFF3A3834, 0xFF7A7466, 0xFF3A3834, 0xFF6F6B62, 0xFFE9E3D5,
                new KeyStyle(KeyCapDrawable.SCULPTED,
                        new Cap(0xFFE6E0D1, 0xFFBDB5A3, 0xFF3A3731),
                        new Cap(0xFF77756F, 0xFF55534E, 0xFFF1EDE4),
                        new Cap(0xFFC9402F, 0xFF92281D, 0xFFFFF4EA),
                        new Cap(0xFF3E8A8C, 0xFF2A6365, 0),
                        0, null, 0, 0, 0));
    }

    /** Black alphas with pink legends, grey modifiers, pink Esc/Enter. */
    private static KeyboardTheme keycapsMidnight() {
        return styled(0xFF0A0A0C, 0xFF9C7880, 0xFF1B1B1E, 0xFF3A3A40, 0xFFF0BFCB,
                new KeyStyle(KeyCapDrawable.SCULPTED,
                        new Cap(0xFF2D2D32, 0xFF202024, 0xFFF0BFCB),
                        new Cap(0xFF404047, 0xFF2C2C32, 0xFFE4E4E8),
                        new Cap(0xFFE9A8B8, 0xFFB57585, 0xFF2A2A2E),
                        new Cap(0xFFA5466A, 0xFF73304A, 0),
                        0, null, 0, 0, 0));
    }

    /** Green phosphor: outlined cells, glowing monospace labels. */
    private static KeyboardTheme terminal() {
        return styled(0xFF020702, 0xFF2E9E4E, 0xFF020702, 0xFF1F7A3A, 0xFF4BFF7E,
                new KeyStyle(KeyCapDrawable.TERMINAL,
                        new Cap(0xFF061409, 0, 0xFF4BFF7E),
                        new Cap(0xFF04100A, 0, 0xFF3FD7FF),
                        new Cap(0xFF140F02, 0, 0xFFFFC94B),
                        new Cap(0xFF0E4A1E, 0, 0),
                        0xFF1F7A3A, "monospace", Typeface.BOLD, 0xCC1EFF5A, 4));
    }

    static boolean isColourTheme(int id) {
        return id == MATERIAL_DARK || id == MATERIAL_LIGHT || id == MATERIAL_BLACK
                || id == MATERIAL_YOU || id == AMOLED_BLACK || id == HIGH_CONTRAST
                || id == HIGH_CONTRAST_LIGHT || id == TYPEWRITER || id == KEYCAPS_RETRO
                || id == KEYCAPS_MIDNIGHT || id == TERMINAL;
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
            case HIGH_CONTRAST_LIGHT:
                // Black on white with black outlines, dark blue hints; active keys yellow
                // (black labels stay readable on them).
                return new KeyboardTheme(true, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFBDBDBD, 0xFFFFD600,
                        0xFF000000, 1.5f, 0xFF000000, 0xFF0D47A1, 0xFFFFFFFF, 0xFF000000);
            case MATERIAL_YOU: return materialYou(context);
            case TYPEWRITER: return typewriter();
            case KEYCAPS_RETRO: return keycapsRetro();
            case KEYCAPS_MIDNIGHT: return keycapsMidnight();
            case TERMINAL: return terminal();
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
                && hint == o.hint && popup == o.popup && style == o.style;
    }

    // ------------------------------------------------------------------ drawables

    Drawable keyboardBackground() {
        return new ColorDrawable(background);
    }

    /** Key background with the same states as the old btn_keyboard_key_material_* selectors. */
    Drawable keyBackground(Context context, boolean borders) {
        return keyBackground(context, borders, CLASS_NORMAL);
    }

    /**
     * The background for one key class ({@link #keyClass}). Flat themes draw every class the
     * same. Styled keys are their shape, so the key borders setting does not apply to them.
     */
    Drawable keyBackground(Context context, boolean borders, int keyClass) {
        float d = context.getResources().getDisplayMetrics().density;
        if (style != null) return styledBackground(style, style.caps[keyClass], d);
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

    private static Drawable styledBackground(KeyStyle st, Cap cap, float d) {
        Drawable normal = cap(st, cap, false, d);
        Drawable pressed = cap(st, cap, true, d);
        Drawable active = cap(st, st.active, false, d);
        Drawable activePressed = cap(st, st.active, true, d);
        // The padding is the face, which differs from key to key: the keyboard asks the
        // current state's drawable (getCurrent()) once it has the key's bounds.
        StateListDrawable s = new StateListDrawable();
        int A = android.R.attr.state_active, P = android.R.attr.state_pressed,
                K = android.R.attr.state_checkable, C = android.R.attr.state_checked,
                E = android.R.attr.state_empty;
        s.addState(new int[] {A, P}, activePressed);
        s.addState(new int[] {A}, active);
        s.addState(new int[] {K, C, P}, activePressed);
        s.addState(new int[] {K, P}, pressed);
        s.addState(new int[] {K, C}, active);
        s.addState(new int[] {K}, normal);
        s.addState(new int[] {E, P}, pressed);
        s.addState(new int[] {E}, new ColorDrawable(Color.TRANSPARENT));
        s.addState(new int[] {P}, pressed);
        s.addState(new int[0], normal);
        return s;
    }

    private static Drawable cap(KeyStyle st, Cap cap, boolean pressed, float d) {
        int face = cap.face;
        if (pressed && st.shape == KeyCapDrawable.ROUND) {
            // Ivory darkens, black glass lightens.
            face = luminance(face) > 0.3f ? KeyCapDrawable.blend(face, Color.BLACK, 0.16f)
                    : KeyCapDrawable.blend(face, Color.WHITE, 0.14f);
        }
        int rim = st.rim;
        if (st.shape == KeyCapDrawable.TERMINAL && pressed) {
            // A pressed cell lights up in the phosphor colour.
            rim = st.caps[CLASS_NORMAL].text;
            face = KeyCapDrawable.blend(cap.face, rim, 0.22f);
        }
        return new KeyCapDrawable(st.shape, face, cap.skirt, rim, pressed, d, st.unit);
    }

    private static float luminance(int c) {
        return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
    }

    /**
     * Tells a styled theme's keys the usual key width (the keyboard's median), so a round key
     * on a wide key matches its neighbours. No effect on flat themes.
     */
    void setKeyUnit(int px) {
        if (style != null) style.unit[0] = px;
    }

    /** Keys inside a popup panel: no shape until pressed. */
    Drawable popupKeyBackground(Context context) {
        float d = context.getResources().getDisplayMetrics().density;
        StateListDrawable s = new StateListDrawable();
        // A styled theme's panel is not its key face: a pressed key there takes the face of
        // a key that is on, which its labels are drawn to stand out on.
        int fill = style != null ? style.active.face : keyPressed;
        s.addState(new int[] {android.R.attr.state_pressed}, shape(fill, 0, d));
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
