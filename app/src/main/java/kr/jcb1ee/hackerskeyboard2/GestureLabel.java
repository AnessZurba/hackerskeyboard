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
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;

/**
 * Laid over the keys (or a panel) while a gesture runs: the space-bar or Select-button
 * trackpad, or a backspace swipe. The keys fade, as on iOS, and a label in the middle says
 * what the gesture is doing ("3 words") and how it works. Touches pass through.
 */
final class GestureLabel extends View {
    private String mStatus, mHint;
    private int mScrim;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    GestureLabel(Context context) {
        super(context);
    }

    String status() {
        return mStatus;
    }

    /** Shows the label over a scrim of {@code scrim}; a null status hides it. */
    void set(String status, String hint, int scrim) {
        if (TextUtils.equals(status, mStatus) && TextUtils.equals(hint, mHint) && scrim == mScrim) return;
        mStatus = status;
        mHint = hint;
        mScrim = scrim;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mStatus == null) return;
        canvas.drawColor(mScrim);
        float density = getResources().getDisplayMetrics().density;
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        float statusSize = 20 * scaled, hintSize = 13 * scaled, pad = 14 * density, gap = 4 * density;
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mPaint.setTextSize(statusSize);
        float width = mPaint.measureText(mStatus);
        float height = statusSize;
        if (mHint != null) {
            mPaint.setTypeface(Typeface.DEFAULT);
            mPaint.setTextSize(hintSize);
            float hintWidth = mPaint.measureText(mHint);
            float room = getWidth() - 4 * pad;
            if (hintWidth > room && room > 0) { // narrow screen: a smaller hint, one line
                hintSize *= room / hintWidth;
                hintWidth = room;
            }
            width = Math.max(width, hintWidth);
            height += gap + hintSize;
        }
        width = Math.min(width + 2 * pad, getWidth() - 2 * pad);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        mRect.set(cx - width / 2, cy - height / 2 - pad, cx + width / 2, cy + height / 2 + pad);
        mPaint.setColor(0xE0202124);
        canvas.drawRoundRect(mRect, 16 * density, 16 * density, mPaint);
        float y = mRect.top + pad + statusSize * 0.8f;
        mPaint.setColor(0xFFFFFFFF);
        mPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mPaint.setTextSize(statusSize);
        canvas.drawText(mStatus, cx, y, mPaint);
        if (mHint != null) {
            mPaint.setTypeface(Typeface.DEFAULT);
            mPaint.setTextSize(hintSize);
            mPaint.setColor(0xFFBDC1C6);
            canvas.drawText(mHint, cx, y + gap + hintSize, mPaint);
        }
    }
}
