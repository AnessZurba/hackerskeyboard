package kr.jcb1ee.hackerskeyboard2.theme;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/**
 * A colour picker: a saturation/brightness square for the current hue, and a hue strip below
 * it. The colour's alpha is kept as given.
 */
final class ColourPickerView extends View {
    interface Listener {
        void onColourChanged(int colour);
    }

    private final float mD;
    private final float[] mHsv = new float[3];
    private int mAlpha = 0xFF;
    private Listener mListener;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mSquare = new RectF();
    private final RectF mHue = new RectF();
    private Shader mSquareShader;
    private float mShaderHue = -1;
    private Shader mHueShader;
    /** Which part a drag started on: 1 the square, 2 the hue strip. */
    private int mDragging;

    ColourPickerView(Context context) {
        super(context);
        mD = context.getResources().getDisplayMetrics().density;
        mRing.setStyle(Paint.Style.STROKE);
        mRing.setStrokeWidth(2 * mD);
        setColour(Color.BLACK);
    }

    void setListener(Listener l) {
        mListener = l;
    }

    void setColour(int colour) {
        mAlpha = Color.alpha(colour);
        Color.colorToHSV(colour, mHsv);
        invalidate();
    }

    int getColour() {
        return Color.HSVToColor(mAlpha, mHsv);
    }

    /** Hue, saturation and value, for tests. */
    float[] hsv() {
        return mHsv.clone();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED) w = Math.round(280 * mD);
        int square = Math.min(w, Math.round(220 * mD));
        setMeasuredDimension(w, square + Math.round(48 * mD));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float pad = 8 * mD;
        float strip = 28 * mD;
        mSquare.set(pad, pad, w - pad, h - strip - 2 * pad);
        mHue.set(pad, h - strip - pad, w - pad, h - pad);
        int[] hues = new int[7];
        for (int i = 0; i < hues.length; i++) {
            hues[i] = Color.HSVToColor(new float[] {i * 60f, 1, 1});
        }
        mHueShader = new LinearGradient(mHue.left, 0, mHue.right, 0, hues, null,
                Shader.TileMode.CLAMP);
        mShaderHue = -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mSquare.isEmpty()) return;
        if (mShaderHue != mHsv[0]) {
            int pure = Color.HSVToColor(new float[] {mHsv[0], 1, 1});
            Shader sat = new LinearGradient(mSquare.left, 0, mSquare.right, 0, Color.WHITE, pure,
                    Shader.TileMode.CLAMP);
            Shader val = new LinearGradient(0, mSquare.top, 0, mSquare.bottom, Color.TRANSPARENT,
                    Color.BLACK, Shader.TileMode.CLAMP);
            mSquareShader = new ComposeShader(sat, val, PorterDuff.Mode.SRC_OVER);
            mShaderHue = mHsv[0];
        }
        float r = 6 * mD;
        mPaint.setShader(mSquareShader);
        canvas.drawRoundRect(mSquare, r, r, mPaint);
        mPaint.setShader(mHueShader);
        canvas.drawRoundRect(mHue, r, r, mPaint);
        mPaint.setShader(null);
        // Markers: a ring at the colour, a bar at the hue.
        float x = mSquare.left + mHsv[1] * mSquare.width();
        float y = mSquare.top + (1 - mHsv[2]) * mSquare.height();
        mRing.setColor(mHsv[2] > 0.6f && mHsv[1] < 0.5f ? Color.BLACK : Color.WHITE);
        canvas.drawCircle(x, y, 8 * mD, mRing);
        float hx = mHue.left + mHsv[0] / 360f * mHue.width();
        mRing.setColor(Color.WHITE);
        canvas.drawRoundRect(hx - 3 * mD, mHue.top - 2 * mD, hx + 3 * mD, mHue.bottom + 2 * mD,
                3 * mD, 3 * mD, mRing);
    }

    @SuppressLint("ClickableViewAccessibility") // the hex field next to it is the accessible way
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragging = y >= mHue.top - 8 * mD ? 2 : 1;
                getParent().requestDisallowInterceptTouchEvent(true);
                // fall through
            case MotionEvent.ACTION_MOVE:
                pick(x, y);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDragging = 0;
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    /** Picks the colour at a point (on the part the drag started on). */
    void pick(float x, float y) {
        if (mDragging == 2) {
            mHsv[0] = clamp((x - mHue.left) / mHue.width()) * 359.9f;
        } else {
            mHsv[1] = clamp((x - mSquare.left) / mSquare.width());
            mHsv[2] = 1 - clamp((y - mSquare.top) / mSquare.height());
        }
        invalidate();
        if (mListener != null) mListener.onColourChanged(getColour());
    }

    /** Starts a drag on the square (1) or the hue strip (2), for tests. */
    void startDrag(int part) {
        mDragging = part;
    }

    RectF square() {
        return mSquare;
    }

    RectF hueStrip() {
        return mHue;
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }
}
