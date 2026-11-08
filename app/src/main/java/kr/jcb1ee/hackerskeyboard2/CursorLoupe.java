/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package kr.jcb1ee.hackerskeyboard2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupWindow;

/**
 * A loupe over the text while a trackpad drag (space bar or the Select button) moves the
 * cursor: the line around the cursor, larger, with the caret or the selection marked. A
 * keyboard can't magnify the app's pixels, so it draws the text itself (from the drag's own
 * copy, without asking the app). It sits just above the cursor when the app says where that
 * is, and just above the keyboard otherwise.
 */
final class CursorLoupe implements CursorDrag.Listener {
    static final String PREF = "pref_cursor_loupe";
    private static final int CONTEXT_CHARS = 18;

    private final LatinIME mIme;
    private PopupWindow mPopup;
    private LoupeText mView;
    private boolean mEnabled = true;
    private float mCaretX = Float.NaN, mCaretTop, mCaretBottom; // on screen

    CursorLoupe(LatinIME ime) {
        mIme = ime;
    }

    void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (!enabled) hide();
    }

    boolean isEnabled() {
        return mEnabled;
    }

    boolean isShowing() {
        return mPopup != null && mPopup.isShowing();
    }

    /** What the loupe shows (tests). */
    CharSequence text() {
        return mView == null ? "" : mView.getText();
    }

    @Override
    public void onCaretOnScreen(float x, float top, float bottom) {
        mCaretX = x;
        mCaretTop = top;
        mCaretBottom = bottom;
        if (isShowing()) place();
    }

    // Like iOS: the loupe is for aiming, so it steps aside while the cursor moves fast and
    // comes back once it slows down. Movement is summed over a short window: characters,
    // and a line change counts as LINE_WEIGHT characters.
    private static final long WINDOW_MS = 250;
    private static final int FAST = 12, CALM = 3, LINE_WEIGHT = 6;
    private final java.util.ArrayDeque<long[]> mRecent = new java.util.ArrayDeque<>(); // {time, chars}
    private final android.os.Handler mHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mRecheck = this::recheck;
    private int mLastFocus = -1;
    private boolean mFast;
    private String mText;
    private int mAnchor, mFocus;

    @Override
    public void onDragMoved(String text, int anchor, int focus) {
        if (!mEnabled) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (mLastFocus >= 0 && mText != null && focus != mLastFocus) {
            int distance = Math.abs(focus - mLastFocus);
            int lo = Math.min(focus, mLastFocus), hi = Math.max(focus, mLastFocus);
            if (text.indexOf('\n', lo) >= 0 && text.indexOf('\n', lo) < hi) {
                distance = Math.max(LINE_WEIGHT, Math.min(distance, LINE_WEIGHT * 2));
            }
            mRecent.addLast(new long[] {now, distance});
        }
        mLastFocus = focus;
        mText = text;
        mAnchor = anchor;
        mFocus = focus;
        int moved = recentMovement(now);
        if (moved > FAST) {
            mFast = true;
            hide();
        }
        if (mFast && moved > CALM) {
            mHandler.removeCallbacks(mRecheck);
            mHandler.postDelayed(mRecheck, 100);
            return;
        }
        mFast = false;
        display();
    }

    private int recentMovement(long now) {
        while (!mRecent.isEmpty() && now - mRecent.peekFirst()[0] > WINDOW_MS) mRecent.removeFirst();
        int sum = 0;
        for (long[] m : mRecent) sum += m[1];
        return sum;
    }

    /** While hidden for speed: back once the cursor has calmed down. */
    private void recheck() {
        if (!mFast || mText == null) return;
        if (recentMovement(android.os.SystemClock.uptimeMillis()) > CALM) {
            mHandler.postDelayed(mRecheck, 100);
            return;
        }
        mFast = false;
        display();
    }

    private void display() {
        View anchorView = mIme.loupeAnchor();
        if (anchorView == null || anchorView.getWindowToken() == null) return;
        if (mPopup == null) create(anchorView.getContext());
        show(mText, mAnchor, mFocus);
        if (!mPopup.isShowing()) {
            mPopup.showAtLocation(anchorView, Gravity.NO_GRAVITY, 0, 0);
        }
        place();
    }

    @Override
    public void onDragEnded() {
        hide();
        mHandler.removeCallbacks(mRecheck);
        mRecent.clear();
        mLastFocus = -1;
        mFast = false;
        mText = null;
        mCaretX = Float.NaN;
    }

    void hide() {
        if (mPopup != null && mPopup.isShowing()) mPopup.dismiss();
    }

    private void create(Context context) {
        mView = new LoupeText(context);
        mView.setTextSizeSp(22);
        int pad = dp(14);
        mView.setPadding(pad, dp(6), pad, dp(6));
        mPopup = new PopupWindow(mView, dp(260), dp(52));
        mPopup.setClippingEnabled(false);
        mPopup.setTouchable(false);
        mPopup.setFocusable(false);
        mPopup.setBackgroundDrawable(null);
        mPopup.setAnimationStyle(0);
        mPopup.setElevation(dp(6));
    }

    /** The current line around the cursor, the caret or selection marked. */
    private void show(String text, int anchor, int focus) {
        int lineStart = text.lastIndexOf('\n', Math.max(0, focus - 1)) + 1;
        if (focus == 0) lineStart = 0;
        int lineEnd = text.indexOf('\n', focus);
        if (lineEnd < 0) lineEnd = text.length();
        int from = Math.max(lineStart, focus - CONTEXT_CHARS);
        int to = Math.min(lineEnd, focus + CONTEXT_CHARS);
        // Don't cut a surrogate pair in half.
        if (from > lineStart && Character.isLowSurrogate(text.charAt(from))) from--;
        if (to < lineEnd && to > 0 && Character.isHighSurrogate(text.charAt(to - 1))) to++;
        SpannableString shown = new SpannableString(text.substring(from, to));
        int s = Math.max(from, Math.min(anchor, focus)), e = Math.min(to, Math.max(anchor, focus));
        boolean selection = anchor != focus && s < e;
        int background = mIme.loupeBackground();
        boolean light = (Color.red(background) * 299 + Color.green(background) * 587
                + Color.blue(background) * 114) / 1000 > 128;
        int foreground = light ? 0xFF202124 : 0xFFE8EAED;
        int accent = light ? 0xFF1A73E8 : 0xFF8AB4F8;
        if (selection) {
            shown.setSpan(new BackgroundColorSpan((accent & 0x00FFFFFF) | 0x66000000),
                    s - from, e - from, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(background | 0xFF000000);
        bg.setStroke(Math.max(1, dp(1) / 2), (foreground & 0x00FFFFFF) | 0x40000000);
        mView.setBackground(bg);
        mView.setTextColor(foreground);
        mView.mCaretColor = accent;
        mView.mCaret = selection ? -1 : focus - from;
        mView.mFocus = focus - from;
        mView.setText(shown);
    }

    /** Above the cursor if the app said where it is, else above the keyboard. */
    private void place() {
        View anchor = mIme.loupeAnchor();
        if (anchor == null || mPopup == null) return;
        int[] onScreen = new int[2], inWindow = new int[2];
        anchor.getLocationOnScreen(onScreen);
        anchor.getLocationInWindow(inWindow);
        int originX = onScreen[0] - inWindow[0], originY = onScreen[1] - inWindow[1];
        int w = mPopup.getWidth(), h = mPopup.getHeight();
        int screenW = anchor.getResources().getDisplayMetrics().widthPixels;
        float x, y;
        if (!Float.isNaN(mCaretX) && mCaretBottom < onScreen[1]) {
            x = mCaretX - w / 2f;
            y = mCaretTop - h - dp(10);
            if (y < dp(24)) y = mCaretBottom + dp(10); // no room above: below the line
            if (y + h > onScreen[1]) y = onScreen[1] - h - dp(6);
        } else {
            x = (screenW - w) / 2f;
            y = onScreen[1] - h - dp(10);
        }
        x = Math.max(dp(4), Math.min(x, screenW - w - dp(4)));
        mPopup.update(Math.round(x) - originX, Math.round(y) - originY, w, h);
    }

    private int dp(int value) {
        return Math.round(value * mIme.getResources().getDisplayMetrics().density);
    }

    /**
     * The loupe's text, drawn with the cursor kept near the middle (the text scrolls under
     * it, as under a magnifier), and a caret at the cursor when nothing is selected.
     */
    static final class LoupeText extends View {
        int mCaret = -1, mFocus;
        int mCaretColor = Color.BLUE;
        private CharSequence mText = "";
        private final android.text.TextPaint mTextPaint =
                new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Layout mLayout;

        LoupeText(Context context) {
            super(context);
        }

        void setTextSizeSp(float sp) {
            mTextPaint.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        }

        void setTextColor(int colour) {
            mTextPaint.setColor(colour);
        }

        void setText(CharSequence text) {
            mText = text;
            int width = (int) Math.ceil(Layout.getDesiredWidth(text, mTextPaint)) + 1;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                // Direction from the text itself, so Arabic and Hebrew read right to left.
                mLayout = android.text.StaticLayout.Builder.obtain(text, 0, text.length(),
                                mTextPaint, Math.max(1, width))
                        .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR)
                        .setIncludePad(false)
                        .build();
            } else {
                mLayout = new android.text.StaticLayout(text, mTextPaint, Math.max(1, width),
                        Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            }
            invalidate();
        }

        CharSequence getText() {
            return mText;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (mLayout == null) return;
            int left = getPaddingLeft(), right = getWidth() - getPaddingRight();
            int avail = right - left;
            float textWidth = mLayout.getWidth();
            float focusX = mLayout.getPrimaryHorizontal(Math.max(0, Math.min(mFocus, mText.length())));
            float dx;
            if (textWidth <= avail) {
                dx = left + (avail - textWidth) / 2f;               // fits: centred
            } else {
                dx = left + avail / 2f - focusX;                    // cursor in the middle
                dx = Math.min(left, Math.max(right - textWidth, dx)); // no empty edges
            }
            float dy = (getHeight() - mLayout.getHeight()) / 2f;
            canvas.save();
            canvas.clipRect(left, 0, right, getHeight());
            canvas.translate(dx, dy);
            mLayout.draw(canvas);
            if (mCaret >= 0 && mCaret <= mText.length()) {
                float x = mLayout.getPrimaryHorizontal(mCaret);
                mPaint.setColor(mCaretColor);
                mPaint.setStrokeWidth(Math.max(2f, getResources().getDisplayMetrics().density * 2));
                canvas.drawLine(x, mLayout.getLineTop(0), x, mLayout.getLineBottom(0), mPaint);
            }
            canvas.restore();
        }
    }
}
