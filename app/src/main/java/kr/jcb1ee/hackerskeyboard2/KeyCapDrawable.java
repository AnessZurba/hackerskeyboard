package kr.jcb1ee.hackerskeyboard2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * One key of a styled theme ({@link KeyboardTheme.KeyStyle}), drawn from shapes and
 * gradients so it is sharp at any density: a typewriter key (a round face in a metal rim), a
 * sculpted keycap (a top face on a darker skirt) or a terminal key (an outlined cell).
 *
 * <p>Its padding is the face, measured on the current bounds, so a key's label centres on the
 * face and its hints sit in the face's corner; a pressed key keeps the padding of the normal one
 * (the label does not jump).
 */
final class KeyCapDrawable extends Drawable {
    static final int ROUND = 1;
    static final int SCULPTED = 2;
    static final int TERMINAL = 3;

    private final int mShape;
    private final int mFace;
    private final int mSkirt;
    private final int mRim;
    private final boolean mPressed;
    private final float mD;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The whole cap: rim (round), skirt (sculpted) or cell (terminal). */
    private final RectF mOuter = new RectF();
    /** The face the label sits on. */
    private final RectF mFaceRect = new RectF();
    /** The face of the unpressed key, for the padding. */
    private final RectF mPadRect = new RectF();
    private final RectF mTmp = new RectF();
    private Shader mOuterShader;
    private Shader mFaceShader;
    private Shader mGloss;
    private int mAlpha = 255;
    /** The usual key width, shared by a theme's keys (see {@link KeyboardTheme#setKeyUnit}). */
    private final float[] mUnit;
    /** The unit and bounds the shapes were laid out for. */
    private float mLaidUnit = -1;
    private final Rect mLaidBounds = new Rect();

    KeyCapDrawable(int shape, int face, int skirt, int rim, boolean pressed, float density,
            float[] unit) {
        mUnit = unit;
        mShape = shape;
        mFace = face;
        mSkirt = skirt;
        mRim = rim;
        mPressed = pressed;
        mD = density;
    }

    static int blend(int from, int to, float t) {
        float s = 1 - t;
        return Color.argb(Math.round(Color.alpha(from) * s + Color.alpha(to) * t),
                Math.round(Color.red(from) * s + Color.red(to) * t),
                Math.round(Color.green(from) * s + Color.green(to) * t),
                Math.round(Color.blue(from) * s + Color.blue(to) * t));
    }

    private static int darker(int c, float t) {
        return (blend(c, Color.BLACK, t) & 0x00FFFFFF) | (c & 0xFF000000);
    }

    private static int lighter(int c, float t) {
        return (blend(c, Color.WHITE, t) & 0x00FFFFFF) | (c & 0xFF000000);
    }

    /** Lays the shapes out for the current bounds and key unit, if either changed. */
    private void layout() {
        Rect b = getBounds();
        float unit = mUnit != null ? mUnit[0] : 0;
        if (b.equals(mLaidBounds) && unit == mLaidUnit) return;
        mLaidBounds.set(b);
        mLaidUnit = unit;
        float d = mD;
        switch (mShape) {
            case ROUND: layoutRound(b, d, unit); break;
            case SCULPTED: layoutSculpted(b, d); break;
            default: layoutTerminal(b, d); break;
        }
    }

    // ------------------------------------------------------------------ typewriter

    /**
     * A circle as wide as an ordinary key (or most of the height, if keys are wider than
     * tall), or for a wide key (Shift, Enter, the space bar) a pill as tall as the circles.
     * The padding is the circle's box: the label centres in it, hints go by its top-right
     * corner (raised by the keyboard, see LatinKeyboardBaseView#hintTop).
     */
    private void layoutRound(Rect b, float d, float unit) {
        float m = 1.5f * d;
        float w = b.width() - 2 * m, h = b.height() - 2 * m;
        float cx = b.exactCenterX(), cy = b.exactCenterY() - 0.5f * d;
        float diameter = Math.min(w, h * 0.8f);
        if (unit > 0) diameter = Math.min(diameter, Math.max(unit - 2 * m, 8 * d));
        if (w > diameter * 1.2f) {
            mOuter.set(b.left + m, cy - diameter / 2, b.right - m, cy + diameter / 2);
        } else {
            mOuter.set(cx - diameter / 2, cy - diameter / 2, cx + diameter / 2, cy + diameter / 2);
        }
        float rim = Math.max(1.5f * d, diameter * 0.1f);
        mFaceRect.set(mOuter);
        mFaceRect.inset(rim, rim);
        mPadRect.set(mOuter);
        if (mPressed) {
            mOuter.offset(0, 0.75f * d);
            mFaceRect.offset(0, 0.75f * d);
        }
        // Chrome: bright on top, dark below; a pressed key catches less light.
        int top = mPressed ? lighter(mRim, 0.3f) : lighter(mRim, 0.75f);
        mOuterShader = new LinearGradient(0, mOuter.top, 0, mOuter.bottom,
                new int[] {top, mRim, darker(mRim, 0.45f)}, new float[] {0, 0.55f, 1},
                Shader.TileMode.CLAMP);
        if (mPressed) {
            // Inset: shaded at the top, as if the face sank.
            mFaceShader = new LinearGradient(0, mFaceRect.top, 0, mFaceRect.bottom,
                    darker(mFace, 0.18f), mFace, Shader.TileMode.CLAMP);
        } else {
            float fr = Math.max(1, Math.max(mFaceRect.width(), mFaceRect.height()) * 0.75f);
            mFaceShader = new RadialGradient(mFaceRect.centerX(),
                    mFaceRect.top + mFaceRect.height() * 0.3f, fr,
                    new int[] {lighter(mFace, 0.18f), mFace, darker(mFace, 0.14f)},
                    new float[] {0, 0.55f, 1}, Shader.TileMode.CLAMP);
        }
        mGloss = new LinearGradient(0, mFaceRect.top, 0, mFaceRect.centerY(),
                mPressed ? 0x10FFFFFF : 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
    }

    private void drawRound(Canvas c) {
        float d = mD;
        Paint p = mPaint;
        if (!mPressed) {
            p.setShader(null);
            p.setColor(0x70000000);
            mTmp.set(mOuter);
            mTmp.offset(0, 1.5f * d);
            pill(c, mTmp, p);
        }
        shade(p, mOuterShader);
        pill(c, mOuter, p);
        // A dark line between rim and face.
        p.setShader(null);
        p.setColor(0xFF1A1714);
        mTmp.set(mFaceRect);
        mTmp.inset(-0.8f * d, -0.8f * d);
        pill(c, mTmp, p);
        shade(p, mFaceShader);
        pill(c, mFaceRect, p);
        // Glass-like gloss on the face's upper half.
        shade(p, mGloss);
        mTmp.set(mFaceRect.left + mFaceRect.width() * 0.12f, mFaceRect.top + 0.6f * d,
                mFaceRect.right - mFaceRect.width() * 0.12f, mFaceRect.centerY());
        c.drawOval(mTmp, p);
        p.setShader(null);
    }

    /** Paints with a gradient, at full opacity (a shader is faded by the paint's alpha). */
    private static void shade(Paint p, Shader s) {
        p.setColor(Color.BLACK);
        p.setShader(s);
    }

    private static void pill(Canvas c, RectF r, Paint p) {
        float rad = Math.min(r.width(), r.height()) / 2;
        c.drawRoundRect(r, rad, rad, p);
    }

    // ------------------------------------------------------------------ keycap

    /** A skirt filling the key and a face inset towards the top (seen from the front). */
    private void layoutSculpted(Rect b, float d) {
        float m = 1.2f * d;
        mOuter.set(b.left + m, b.top + m, b.right - m, b.bottom - m);
        float side = Math.min(3.2f * d, mOuter.width() * 0.14f);
        float top = 1.4f * d, bottom = Math.min(5.5f * d, mOuter.height() * 0.16f);
        mPadRect.set(mOuter.left + side, mOuter.top + top, mOuter.right - side,
                mOuter.bottom - bottom);
        mFaceRect.set(mPadRect);
        if (mPressed) {
            // The face travels down and the skirt below it shrinks.
            float travel = Math.min(2 * d, bottom * 0.45f);
            mFaceRect.offset(0, travel);
            mFaceRect.inset(0.4f * d, 0);
        }
        mOuterShader = new LinearGradient(0, mOuter.top, 0, mOuter.bottom,
                new int[] {blend(mSkirt, mFace, 0.35f), mSkirt, darker(mSkirt, 0.22f)},
                new float[] {0, 0.45f, 1}, Shader.TileMode.CLAMP);
        // A pressed light cap shades; a dark one catches a little light (else it barely shows).
        int face = !mPressed ? mFace
                : Color.luminance(mFace) > 0.2f ? darker(mFace, 0.08f) : lighter(mFace, 0.1f);
        mFaceShader = new LinearGradient(0, mFaceRect.top, 0, mFaceRect.bottom,
                new int[] {lighter(face, 0.07f), face, darker(face, 0.05f)},
                new float[] {0, 0.5f, 1}, Shader.TileMode.CLAMP);
    }

    private void drawSculpted(Canvas c) {
        float d = mD;
        Paint p = mPaint;
        float r = 4 * d;
        // Shadow on the plate.
        p.setShader(null);
        p.setColor(0x60000000);
        mTmp.set(mOuter);
        mTmp.offset(0, mPressed ? 0.5f * d : 1.2f * d);
        c.drawRoundRect(mTmp, r, r, p);
        shade(p, mOuterShader);
        c.drawRoundRect(mOuter, r, r, p);
        // A rim of light along the face's top edge, then the face.
        p.setShader(null);
        p.setColor(lighter(mFace, mPressed ? 0.12f : 0.3f));
        mTmp.set(mFaceRect);
        mTmp.offset(0, -0.8f * d);
        float fr = 3 * d;
        c.drawRoundRect(mTmp, fr, fr, p);
        shade(p, mFaceShader);
        c.drawRoundRect(mFaceRect, fr, fr, p);
        p.setShader(null);
    }

    // ------------------------------------------------------------------ terminal

    /** A dark cell with a phosphor outline; a pressed one glows. */
    private void layoutTerminal(Rect b, float d) {
        float m = 2 * d;
        mOuter.set(b.left + m, b.top + m, b.right - m, b.bottom - m);
        mFaceRect.set(mOuter);
        mPadRect.set(mOuter);
        mPadRect.inset(1.5f * d, 1.5f * d);
        mFaceShader = new LinearGradient(0, mOuter.top, 0, mOuter.bottom,
                lighter(mFace, 0.04f), darker(mFace, 0.2f), Shader.TileMode.CLAMP);
    }

    private void drawTerminal(Canvas c) {
        float d = mD;
        Paint p = mPaint;
        float r = 2.5f * d;
        if (mPressed) {
            // A soft halo around the lit cell.
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            for (int i = 3; i >= 1; i--) {
                p.setStrokeWidth(i * 1.6f * d);
                p.setColor((mRim & 0x00FFFFFF) | (0x18 << 24));
                c.drawRoundRect(mOuter, r, r, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        shade(p, mFaceShader);
        c.drawRoundRect(mOuter, r, r, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, d));
        p.setColor(mRim);
        mTmp.set(mOuter);
        mTmp.inset(0.5f * d, 0.5f * d);
        c.drawRoundRect(mTmp, r, r, p);
        p.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------------ Drawable

    @Override
    public void draw(Canvas canvas) {
        if (getBounds().isEmpty()) return;
        layout();
        mPaint.setAlpha(255);
        int save = mAlpha < 255 ? canvas.saveLayerAlpha(null, mAlpha) : -1;
        switch (mShape) {
            case ROUND: drawRound(canvas); break;
            case SCULPTED: drawSculpted(canvas); break;
            default: drawTerminal(canvas); break;
        }
        if (save >= 0) canvas.restoreToCount(save);
    }

    @Override
    public boolean getPadding(Rect padding) {
        Rect b = getBounds();
        if (b.isEmpty()) {
            padding.set(0, 0, 0, 0);
            return false;
        }
        layout();
        padding.set(Math.round(mPadRect.left - b.left), Math.round(mPadRect.top - b.top),
                Math.round(b.right - mPadRect.right), Math.round(b.bottom - mPadRect.bottom));
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
