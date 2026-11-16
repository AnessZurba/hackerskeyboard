package kr.jcb1ee.hackerskeyboard2.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;

/**
 * Everything the keyboard view needs to draw a theme: its background, a background, label
 * colour and icon tint per {@link KeyKind}, the hint colour and label font, and the long-press
 * popup's panel and keys. The view calls only this; how a theme looks is decided here and in
 * {@link ThemeSpec}, so a new theme never touches the view.
 *
 * <p>Get one from {@link Themes#forKeyboard}. A look is cheap to make: drawables are made when
 * asked for, so the keyboard can make a fresh one to compare with {@link #sameAs}.
 */
public final class KeyboardLook {
    final ThemeSpec spec;
    private final boolean mBorders;
    /** The look of a popup's keys (see {@link #popupLook}). */
    private final boolean mPopup;
    private final float mD;
    /** The usual key width in pixels, shared by this look's key drawables (0: unknown). */
    private final float[] mUnit = new float[1];
    /** Made when first asked for, by KeyKind. */
    private final Drawable[] mKeys = new Drawable[KeyKind.COUNT];
    private final ColorFilter[] mIcons = new ColorFilter[KeyKind.COUNT];

    KeyboardLook(Context context, ThemeSpec spec, boolean borders) {
        this(spec, borders, false, context.getResources().getDisplayMetrics().density);
    }

    private KeyboardLook(ThemeSpec spec, boolean borders, boolean popup, float density) {
        this.spec = spec;
        mBorders = borders;
        mPopup = popup;
        mD = density;
    }

    /** Whether the keyboard is light (dark labels and icons). */
    public boolean isLight() {
        return spec.isLight();
    }

    /** Whether two looks draw the same (Material You and light/dark modes can move on). */
    public boolean sameAs(KeyboardLook o) {
        return o != null && spec.equals(o.spec) && mBorders == o.mBorders && mPopup == o.mPopup;
    }

    // ------------------------------------------------------------------ the keyboard

    public Drawable keyboardBackground() {
        return new ColorDrawable(mPopup ? Color.TRANSPARENT : spec.c[ThemeSpec.BACKGROUND]);
    }

    /**
     * The background of keys of one kind, with the states of the keyboard's keys (pressed,
     * a toggle that is on, an action key, an empty key). Every state has the same padding, which
     * is where the label goes: ask the current state ({@code getCurrent().getPadding()}) once the
     * drawable has the key's bounds, since a shaped key's face follows the key's size.
     */
    public Drawable keyBackground(int kind) {
        if (mKeys[kind] == null) mKeys[kind] = newKeyBackground(kind);
        return mKeys[kind];
    }

    /** Tints icons in the label colour of their key's kind. */
    public ColorFilter iconFilter(int kind) {
        if (mIcons[kind] == null) {
            mIcons[kind] = new PorterDuffColorFilter(labelColor(kind), PorterDuff.Mode.SRC_IN);
        }
        return mIcons[kind];
    }

    private Drawable newKeyBackground(int kind) {
        if (spec.shape != ThemeSpec.FLAT) return shapedBackground(kind);
        Drawable normal = mBorders ? flatKey(spec.face(kind)) : bare();
        Drawable pressed = flatKey(spec.c[ThemeSpec.PRESSED]);
        Drawable active = mPopup ? pressed : flatKey(spec.c[ThemeSpec.ACTIVE]);
        return states(normal, pressed, active, pressed, pressed);
    }

    public int labelColor(int kind) {
        return spec.label(kind);
    }

    /** Labels of cursor keys: flat themes show them in the hint colour. */
    public int cursorColor(int kind) {
        return spec.shape == ThemeSpec.FLAT ? spec.c[ThemeSpec.HINT] : spec.label(kind);
    }

    public int hintColor() {
        return spec.c[ThemeSpec.HINT];
    }

    /** The label font, or null for the keyboard's own. */
    public Typeface typeface() {
        return spec.font == null ? null : Typeface.create(spec.font, spec.fontStyle);
    }

    /** Label shadow: a glow, a soft shadow under light labels, or none (0). */
    public int shadowColor() {
        if (spec.c[ThemeSpec.GLOW] != 0 && spec.glowDp > 0) return spec.c[ThemeSpec.GLOW];
        return ThemeSpec.luminance(spec.label(KeyKind.NORMAL)) > 0.5f ? 0xBB000000 : 0;
    }

    /** The label shadow's radius in pixels. */
    public float shadowRadius() {
        if (spec.c[ThemeSpec.GLOW] != 0 && spec.glowDp > 0) return spec.glowDp * mD;
        return shadowColor() != 0 ? 2.75f : 0;
    }

    /** Hints go half above the key's top-right corner (round keys: outside the circle). */
    public boolean hintsAboveKey() {
        return spec.shape == ThemeSpec.ROUND;
    }

    /** Whether the plain space bar is drawn blank, like a real one (shaped keys). */
    public boolean blankSpaceBar() {
        return spec.shape != ThemeSpec.FLAT;
    }

    /**
     * The usual key width (the keyboard's median), so a round key on a wide key matches its
     * neighbours. Applies to the drawables this look made.
     */
    public void setKeyUnit(int px) {
        mUnit[0] = px;
    }

    // ------------------------------------------------------------------ popups

    /** The look of the keys inside a long-press popup: the same key style, no background. */
    public KeyboardLook popupLook() {
        return new KeyboardLook(spec, mBorders, true, mD);
    }

    /** The long-press popup's panel, with corners that suit the key style. */
    public Drawable popupPanel() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(spec.c[ThemeSpec.PANEL]);
        int stroke = spec.c[ThemeSpec.PANEL_OUTLINE];
        if (Color.alpha(stroke) != 0) {
            float w = spec.shape == ThemeSpec.TERMINAL ? 1.5f : 1f;
            g.setStroke(Math.max(1, Math.round(w * mD)), stroke);
        }
        g.setCornerRadius(panelCornerDp() * mD);
        return g;
    }

    float panelCornerDp() {
        switch (spec.shape) {
            case ThemeSpec.ROUND: return 18;
            case ThemeSpec.SCULPTED: return 7;
            case ThemeSpec.TERMINAL: return 3;
            default: return 10;
        }
    }

    // ------------------------------------------------------------------ drawables

    private static Drawable states(Drawable normal, Drawable pressed, Drawable active,
            Drawable activePressed, Drawable togglePressed) {
        StateListDrawable s = new StateListDrawable();
        int A = android.R.attr.state_active, P = android.R.attr.state_pressed,
                K = android.R.attr.state_checkable, C = android.R.attr.state_checked,
                E = android.R.attr.state_empty;
        // Action keys.
        s.addState(new int[] {A, P}, activePressed);
        s.addState(new int[] {A}, active);
        // Toggle keys.
        s.addState(new int[] {K, C, P}, activePressed);
        s.addState(new int[] {K, P}, togglePressed);
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

    private int inset() {
        return Math.round(1.5f * mD);
    }

    /** Nothing to draw, with a key's padding (the label does not move when it lights up). */
    private Drawable bare() {
        int i = inset();
        return new InsetDrawable(new ColorDrawable(Color.TRANSPARENT), i, i, i, i);
    }

    private Drawable flatKey(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(5 * mD);
        int stroke = spec.c[ThemeSpec.OUTLINE];
        if (Color.alpha(stroke) != 0) {
            float w = spec.strokeDp > 0 ? spec.strokeDp : 1;
            g.setStroke(Math.max(1, Math.round(w * mD)), stroke);
        }
        int i = inset();
        return new InsetDrawable(g, i, i, i, i);
    }

    private Drawable shapedBackground(int kind) {
        int activeFace = spec.c[ThemeSpec.ACTIVE];
        Drawable normal = cap(spec.face(kind), spec.skirt(kind), false);
        Drawable pressed = cap(spec.face(kind), spec.skirt(kind), true);
        Drawable active = cap(activeFace, darkerSkirt(activeFace), false);
        Drawable activePressed = cap(activeFace, darkerSkirt(activeFace), true);
        return states(normal, pressed, active, activePressed, pressed);
    }

    /** A key that is on has no skirt colour of its own: a darker shade of its face. */
    private int darkerSkirt(int face) {
        return spec.shape == ThemeSpec.SCULPTED ? KeyCapDrawable.blend(face, Color.BLACK, 0.3f) : 0;
    }

    private Drawable cap(int face, int skirt, boolean pressed) {
        int rim = spec.c[ThemeSpec.OUTLINE];
        if (pressed && spec.shape == ThemeSpec.ROUND) {
            // Ivory darkens, black glass lightens.
            face = ThemeSpec.luminance(face) > 0.3f ? KeyCapDrawable.blend(face, Color.BLACK, 0.16f)
                    : KeyCapDrawable.blend(face, Color.WHITE, 0.14f);
        }
        if (pressed && spec.shape == ThemeSpec.TERMINAL) {
            // A pressed cell lights up in the phosphor (ink) colour.
            rim = spec.label(KeyKind.NORMAL);
            face = KeyCapDrawable.blend(face, rim, 0.22f);
        }
        return new KeyCapDrawable(spec.shape, face, skirt, rim, pressed, mD, mUnit);
    }
}
