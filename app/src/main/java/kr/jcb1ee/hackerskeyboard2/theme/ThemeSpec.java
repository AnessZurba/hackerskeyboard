package kr.jcb1ee.hackerskeyboard2.theme;

import android.graphics.Color;

import java.util.Arrays;
import java.util.Objects;

/**
 * One theme variant as data: a key shape and a colour for each {@link #SLOTS slot}. Built-in
 * variants are in {@link Palettes}; a custom theme is a copy of one with some colours changed.
 * {@link KeyboardLook} turns a spec into what the keyboard draws.
 */
final class ThemeSpec {
    /** Flat rounded rectangles (Material, High contrast). */
    static final int FLAT = 0;
    static final int ROUND = KeyCapDrawable.ROUND;
    static final int SCULPTED = KeyCapDrawable.SCULPTED;
    static final int TERMINAL = KeyCapDrawable.TERMINAL;

    // Colour slots. The names are stored in custom theme settings: never rename one.
    static final int BACKGROUND = 0;
    static final int KEY = 1;
    static final int LABEL = 2;
    static final int MODIFIER = 3;
    static final int MODIFIER_LABEL = 4;
    static final int ACCENT = 5;
    static final int ACCENT_LABEL = 6;
    /** A pressed key (flat keys; shaped keys darken or light their own face). */
    static final int PRESSED = 7;
    /** A modifier or caps lock that is on, or an action key. */
    static final int ACTIVE = 8;
    /** Key outline (flat), metal rim (round) or cell outline (terminal); 0 for none. */
    static final int OUTLINE = 9;
    static final int HINT = 10;
    /** The sides of a sculpted keycap, below and around its top face. */
    static final int SKIRT = 11;
    static final int MODIFIER_SKIRT = 12;
    static final int ACCENT_SKIRT = 13;
    /** Label glow (terminal), 0 for none. */
    static final int GLOW = 14;
    /** The long-press popup's panel and its outline. */
    static final int PANEL = 15;
    static final int PANEL_OUTLINE = 16;
    static final int SLOTS = 17;

    /** Settings names of the slots, indexed by slot. */
    static final String[] SLOT_NAMES = {"background", "key", "label", "modifier",
            "modifier_label", "accent", "accent_label", "pressed", "active", "outline", "hint",
            "skirt", "modifier_skirt", "accent_skirt", "glow", "panel", "panel_outline"};

    /** Whether a slot changes anything for keys of this shape. */
    static boolean slotApplies(int slot, int shape) {
        switch (slot) {
            case PRESSED: return shape == FLAT;
            case OUTLINE: return shape != SCULPTED;
            case SKIRT: case MODIFIER_SKIRT: case ACCENT_SKIRT: return shape == SCULPTED;
            case GLOW: return shape == TERMINAL;
            default: return true;
        }
    }

    final int shape;
    final int[] c = new int[SLOTS];
    /** Outline width of flat keys, in dp (0: no outline unless a custom one is set). */
    final float strokeDp;
    /** Label font family, or null for the keyboard's own. */
    final String font;
    final int fontStyle;
    final float glowDp;

    ThemeSpec(int shape, float strokeDp, String font, int fontStyle, float glowDp) {
        this.shape = shape;
        this.strokeDp = strokeDp;
        this.font = font;
        this.fontStyle = fontStyle;
        this.glowDp = glowDp;
    }

    ThemeSpec copy() {
        ThemeSpec s = new ThemeSpec(shape, strokeDp, font, fontStyle, glowDp);
        System.arraycopy(c, 0, s.c, 0, SLOTS);
        return s;
    }

    ThemeSpec set(int slot, int colour) {
        c[slot] = colour;
        return this;
    }

    /** The face colour of a key kind ({@link KeyKind}). */
    int face(int kind) {
        return c[kind == KeyKind.MODIFIER ? MODIFIER : kind == KeyKind.ACCENT ? ACCENT : KEY];
    }

    int label(int kind) {
        return c[kind == KeyKind.MODIFIER ? MODIFIER_LABEL
                : kind == KeyKind.ACCENT ? ACCENT_LABEL : LABEL];
    }

    int skirt(int kind) {
        return c[kind == KeyKind.MODIFIER ? MODIFIER_SKIRT
                : kind == KeyKind.ACCENT ? ACCENT_SKIRT : SKIRT];
    }

    /** Dark labels on a light keyboard. */
    boolean isLight() {
        return luminance(c[BACKGROUND]) > 0.5f;
    }

    static float luminance(int c) {
        return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ThemeSpec)) return false;
        ThemeSpec s = (ThemeSpec) o;
        return shape == s.shape && Arrays.equals(c, s.c) && strokeDp == s.strokeDp
                && Objects.equals(font, s.font) && fontStyle == s.fontStyle && glowDp == s.glowDp;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(c) * 31 + shape;
    }
}
