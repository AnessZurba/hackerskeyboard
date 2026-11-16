package kr.jcb1ee.hackerskeyboard2.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;

/**
 * The built-in theme variants, by variant id. A variant id is a stored theme id
 * (pref_keyboard_layout) that means one look: they must never be renumbered or reused.
 */
final class Palettes {
    static final int MATERIAL_LIGHT = 5;
    /** "Material Black" before the light/dark families; now Material's dark variant. */
    static final int MATERIAL_DARK = 7;
    /** Wallpaper colours on Android 12+, in a light and a dark variant. */
    static final int MATERIAL_YOU = 8;
    static final int HIGH_CONTRAST_DARK = 10;
    /** Black on white with black outlines; also replaced the Stone image theme (id 2). */
    static final int HIGH_CONTRAST_LIGHT = 11;
    /** Round typewriter keys: ivory faces in chrome rims on a dark body. */
    static final int TYPEWRITER = 12;
    /** Sculpted keycaps, retro beige alphas, grey modifiers, red Esc and Enter (light). */
    static final int KEYCAPS_RETRO = 13;
    /** Sculpted keycaps, black alphas with pink legends, pink Esc and Enter (dark). */
    static final int KEYCAPS_MIDNIGHT = 14;
    /** Green phosphor on black, outlined cells and glowing labels. */
    static final int TERMINAL = 15;
    /** The terminal on paper: green and blue ink in outlined cells on a pale page. */
    static final int TERMINAL_LIGHT = 16;
    /** The user's own colours over a base theme, see {@link CustomTheme}. */
    static final int CUSTOM = 17;

    private Palettes() {}

    /**
     * The spec of a built-in variant id, light or dark where the id has both (Material You),
     * or null if the id is not a built-in variant.
     */
    static ThemeSpec forId(Context context, int id, boolean dark) {
        switch (id) {
            case MATERIAL_LIGHT:
                return flat(0xFFECEFF1, 0xFFFFFFFF, 0xFFB0BEC5, 0xFF90CAF9, 0, 0, 0xFF000000,
                        0xB337474F, 0xFFECEFF1, 0xFFB0BEC5);
            case MATERIAL_DARK:
                return flat(0xFF000000, 0xFF2C2C2E, 0xFF484849, 0xFF3A6186, 0, 0, 0xCCFFFFFF,
                        0x80FFFFFF, 0xFF000000, 0xFF3A3A3A);
            case MATERIAL_YOU: return materialYou(context, dark);
            case HIGH_CONTRAST_DARK:
                // White on black with white outlines, yellow hints; active keys dark blue.
                return flat(0xFF000000, 0xFF000000, 0xFF5A5A5A, 0xFF0D47A1, 0xFFFFFFFF, 1.5f,
                        0xFFFFFFFF, 0xFFFFEB3B, 0xFF000000, 0xFFFFFFFF);
            case HIGH_CONTRAST_LIGHT:
                // Black on white with black outlines, dark blue hints; active keys yellow
                // (black labels stay readable on them).
                return flat(0xFFFFFFFF, 0xFFFFFFFF, 0xFFBDBDBD, 0xFFFFD600, 0xFF000000, 1.5f,
                        0xFF000000, 0xFF0D47A1, 0xFFFFFFFF, 0xFF000000);
            case TYPEWRITER:
                // Ivory keys with dark ink, black glass modifiers, an oxblood Enter, chrome rims.
                return styled(ThemeSpec.ROUND, 0xFF1C1713, 0xFF8A6A3E, 0xFF2A231D, 0xFFB9B4A8,
                        0xFFF1E7CF, 0, 0xFF2A1F16,
                        0xFF2B2724, 0, 0xFFEDE3C8,
                        0xFF8E2B21, 0, 0xFFF6EAD2,
                        0xFFA8342A, 0xFFA9A9A4, "serif-monospace", Typeface.BOLD, 0, 0);
            case KEYCAPS_RETRO:
                // Beige alphas, grey modifiers and red Esc/Enter, as on classic beige computers.
                return styled(ThemeSpec.SCULPTED, 0xFF3A3834, 0xFF7A7466, 0xFF3A3834, 0xFF6F6B62,
                        0xFFE6E0D1, 0xFFBDB5A3, 0xFF3A3731,
                        0xFF77756F, 0xFF55534E, 0xFFF1EDE4,
                        0xFFC9402F, 0xFF92281D, 0xFFFFF4EA,
                        0xFF3E8A8C, 0, null, 0, 0, 0);
            case KEYCAPS_MIDNIGHT:
                // Black alphas with pink legends, grey modifiers, pink Esc/Enter.
                return styled(ThemeSpec.SCULPTED, 0xFF0A0A0C, 0xFF9C7880, 0xFF1B1B1E, 0xFF3A3A40,
                        0xFF2D2D32, 0xFF202024, 0xFFF0BFCB,
                        0xFF404047, 0xFF2C2C32, 0xFFE4E4E8,
                        0xFFE9A8B8, 0xFFB57585, 0xFF2A2A2E,
                        0xFFA5466A, 0, null, 0, 0, 0);
            case TERMINAL:
                // Green phosphor: outlined cells, glowing monospace labels.
                return styled(ThemeSpec.TERMINAL, 0xFF020702, 0xFF2E9E4E, 0xFF020702, 0xFF1F7A3A,
                        0xFF061409, 0, 0xFF4BFF7E,
                        0xFF04100A, 0, 0xFF3FD7FF,
                        0xFF140F02, 0, 0xFFFFC94B,
                        0xFF0E4A1E, 0xFF1F7A3A, "monospace", Typeface.BOLD, 0xCC1EFF5A, 4);
            case TERMINAL_LIGHT:
                // A printing terminal: green, blue and amber ink in ruled cells on pale paper,
                // with a faint bleed around the letters.
                return styled(ThemeSpec.TERMINAL, 0xFFE9E6D8, 0xFF5F8A69, 0xFFE9E6D8, 0xFF3E7A4E,
                        0xFFF7F5EC, 0, 0xFF1B5E30,
                        0xFFF1EFE4, 0, 0xFF1D5A85,
                        0xFFF6EEDA, 0, 0xFF9A5200,
                        0xFFCDE5D2, 0xFF6C9A77, "monospace", Typeface.BOLD, 0x3A2E8B45, 1.5f);
            default:
                return null;
        }
    }

    /** A flat theme: every kind of key alike. */
    private static ThemeSpec flat(int background, int key, int pressed, int active, int stroke,
            float strokeDp, int text, int hint, int panel, int panelStroke) {
        ThemeSpec s = new ThemeSpec(ThemeSpec.FLAT, strokeDp, null, 0, 0);
        int[] c = s.c;
        c[ThemeSpec.BACKGROUND] = background;
        c[ThemeSpec.KEY] = c[ThemeSpec.MODIFIER] = c[ThemeSpec.ACCENT] = key;
        c[ThemeSpec.SKIRT] = c[ThemeSpec.MODIFIER_SKIRT] = c[ThemeSpec.ACCENT_SKIRT] = key;
        c[ThemeSpec.LABEL] = c[ThemeSpec.MODIFIER_LABEL] = c[ThemeSpec.ACCENT_LABEL] = text;
        c[ThemeSpec.PRESSED] = pressed;
        c[ThemeSpec.ACTIVE] = active;
        c[ThemeSpec.OUTLINE] = stroke;
        c[ThemeSpec.HINT] = hint;
        c[ThemeSpec.PANEL] = panel;
        c[ThemeSpec.PANEL_OUTLINE] = panelStroke;
        return s;
    }

    /** A theme of shaped keys: faces, skirts and labels per kind (normal, modifier, accent). */
    private static ThemeSpec styled(int shape, int background, int hint, int panel,
            int panelStroke, int face, int skirt, int label, int modFace, int modSkirt,
            int modLabel, int accFace, int accSkirt, int accLabel, int active, int rim,
            String font, int fontStyle, int glow, float glowDp) {
        ThemeSpec s = new ThemeSpec(shape, 0, font, fontStyle, glowDp);
        int[] c = s.c;
        c[ThemeSpec.BACKGROUND] = background;
        c[ThemeSpec.KEY] = face;
        c[ThemeSpec.SKIRT] = skirt;
        c[ThemeSpec.LABEL] = label;
        c[ThemeSpec.MODIFIER] = modFace;
        c[ThemeSpec.MODIFIER_SKIRT] = modSkirt;
        c[ThemeSpec.MODIFIER_LABEL] = modLabel;
        c[ThemeSpec.ACCENT] = accFace;
        c[ThemeSpec.ACCENT_SKIRT] = accSkirt;
        c[ThemeSpec.ACCENT_LABEL] = accLabel;
        c[ThemeSpec.PRESSED] = KeyCapDrawable.blend(face, Color.BLACK, 0.15f);
        c[ThemeSpec.ACTIVE] = active;
        c[ThemeSpec.OUTLINE] = rim;
        c[ThemeSpec.HINT] = hint;
        c[ThemeSpec.GLOW] = glow;
        c[ThemeSpec.PANEL] = panel;
        c[ThemeSpec.PANEL_OUTLINE] = panelStroke;
        return s;
    }

    private static ThemeSpec materialYou(Context context, boolean dark) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return forId(context, dark ? MATERIAL_DARK : MATERIAL_LIGHT, dark);
        }
        if (dark) {
            return flat(c(context, android.R.color.system_neutral1_900),
                    c(context, android.R.color.system_neutral2_800),
                    c(context, android.R.color.system_neutral2_600),
                    c(context, android.R.color.system_accent1_700),
                    0, 0,
                    c(context, android.R.color.system_neutral1_50),
                    c(context, android.R.color.system_neutral2_300),
                    c(context, android.R.color.system_neutral1_800),
                    c(context, android.R.color.system_neutral2_600));
        }
        return flat(c(context, android.R.color.system_neutral2_100),
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
}
