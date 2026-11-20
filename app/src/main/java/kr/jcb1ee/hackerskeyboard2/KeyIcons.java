package kr.jcb1ee.hackerskeyboard2;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/**
 * Key icons made of other icons and text, drawn straight onto the keyboard's canvas. They used
 * to be drawn once into bitmaps, which the keyboard then scaled; drawn here they stay as sharp
 * as the vector icons and text they are made of, and take the theme's tint like any icon.
 */
final class KeyIcons {
    private KeyIcons() {}

    /** A key's main icon in the middle of the key and a popup hint over the whole key. */
    static final class WithHint extends Drawable {
        private final Drawable mMain;
        private final Drawable mHint;
        private final int mWidth;
        private final int mHeight;
        private final Rect mPadding = new Rect();

        /** {@code hint} may be null: just the main icon, centred in a key-sized drawable. */
        WithHint(Drawable main, Drawable hint, int width, int height) {
            mMain = main;
            mHint = hint;
            mWidth = width;
            mHeight = height;
            if (hint != null) hint.getPadding(mPadding);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            // The main icon centres inside the hint's padding (the key's face).
            int w = mMain.getIntrinsicWidth();
            int h = mMain.getIntrinsicHeight();
            int x = b.left + (b.width() + mPadding.left - mPadding.right - w) / 2;
            int y = b.top + (b.height() + mPadding.top - mPadding.bottom - h) / 2;
            mMain.setBounds(x, y, x + w, y + h);
            mMain.draw(canvas);
            if (mHint != null) {
                mHint.setBounds(b);
                mHint.draw(canvas);
            }
        }

        @Override public int getIntrinsicWidth() { return mWidth; }
        @Override public int getIntrinsicHeight() { return mHeight; }

        @Override
        public void setAlpha(int alpha) {
            mMain.setAlpha(alpha);
            if (mHint != null) mHint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            mMain.setColorFilter(filter);
            if (mHint != null) mHint.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Text followed by an icon, as "123" and a mic on the symbols key. */
    static final class TextThenIcon extends Drawable {
        private final String mText;
        private final Drawable mIcon;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int mWidth;
        private final int mHeight;

        TextThenIcon(String text, Drawable icon, float textSizePx, int width, int height) {
            mText = text;
            mIcon = icon;
            mWidth = width;
            mHeight = height;
            mPaint.setColor(0xFFFFFFFF); // tinted like the icons
            mPaint.setTypeface(Typeface.DEFAULT_BOLD);
            mPaint.setTextSize(textSizePx);
            mPaint.setTextAlign(Paint.Align.LEFT);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float textWidth = mPaint.measureText(mText);
            int iw = mIcon.getIntrinsicWidth();
            int ih = mIcon.getIntrinsicHeight();
            // The icon's own side margins are part of the pair: trim a third of them.
            float iconWidth = iw * 0.7f;
            float x = b.left + (b.width() - textWidth - iconWidth) / 2;
            Paint.FontMetrics fm = mPaint.getFontMetrics();
            float baseline = b.exactCenterY() - (fm.ascent + fm.descent) / 2;
            canvas.drawText(mText, x, baseline, mPaint);
            int ix = Math.round(x + textWidth - iw * 0.15f);
            int iy = b.top + (b.height() - ih) / 2;
            mIcon.setBounds(ix, iy, ix + iw, iy + ih);
            mIcon.draw(canvas);
        }

        @Override public int getIntrinsicWidth() { return mWidth; }
        @Override public int getIntrinsicHeight() { return mHeight; }

        @Override
        public void setAlpha(int alpha) {
            mPaint.setAlpha(alpha);
            mIcon.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            mPaint.setColorFilter(filter);
            mIcon.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
