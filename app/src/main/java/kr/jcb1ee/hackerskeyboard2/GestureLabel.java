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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;

/**
 * Laid over the keys (or a panel) while a gesture runs: the space-bar or Select-button
 * trackpad, or a backspace swipe. The keys fade, as on iOS, and a label in the middle says
 * what releasing will do ("Release to delete 3 words") and how to back out. Touches pass through.
 */
final class GestureLabel extends View {
    private String mStatus, mHint;
    private int mScrim;
    /** A small pill near the top, no scrim (a brief note after undo or redo). */
    private boolean mCompact;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    GestureLabel(Context context) {
        super(context);
    }

    String status() {
        return mStatus;
    }

    String hint() {
        return mHint;
    }

    /**
     * The backspace swipe's label: what release will do and how to back out; at the screen's
     * left edge, that holding there takes more.
     */
    static String[] deleteSwipe(Resources res, int words, boolean atEdge) {
        if (words == 0) {
            return new String[] {res.getString(R.string.gesture_nothing_selected),
                    res.getString(R.string.gesture_delete_start_hint)};
        }
        return new String[] {res.getQuantityString(R.plurals.gesture_delete_words, words, words),
                res.getString(atEdge ? R.string.gesture_delete_edge_hint : R.string.gesture_delete_hint)};
    }

    /**
     * After a swipe undid (or redid) an edit: what happened, and the swipe for the other way
     * (or for once more), whichever swipe has that action.
     */
    static String[] undoRedo(Resources res, boolean redo, String leftAction, String rightAction) {
        String hint = null;
        if (!redo) { // offer redo, else undoing more
            if ("redo".equals(rightAction)) hint = res.getString(R.string.gesture_swipe_right_to_redo);
            else if ("redo".equals(leftAction)) hint = res.getString(R.string.gesture_swipe_left_to_redo);
        } else {
            if ("undo".equals(leftAction)) hint = res.getString(R.string.gesture_swipe_left_to_undo);
            else if ("undo".equals(rightAction)) hint = res.getString(R.string.gesture_swipe_right_to_undo);
        }
        return new String[] {res.getString(redo ? R.string.gesture_redo : R.string.gesture_undo), hint};
    }

    /** A trackpad's label (space bar, Select button): moving the cursor, or selecting. */
    static String[] trackpad(Resources res, boolean selecting) {
        return new String[] {
                res.getString(selecting ? R.string.gesture_selecting : R.string.gesture_moving_cursor),
                res.getString(selecting ? R.string.gesture_selecting_hint : R.string.gesture_trackpad_hint)};
    }

    /** Shows the label over a scrim of {@code scrim}; a null status hides it. */
    void set(String status, String hint, int scrim) {
        if (TextUtils.equals(status, mStatus) && TextUtils.equals(hint, mHint) && scrim == mScrim) return;
        mStatus = status;
        mHint = hint;
        mScrim = scrim;
        mCompact = false;
        invalidate();
    }

    /** A brief, small note ("Undo") near the top of the keys: no scrim, no second line. */
    void setCompact(String status) {
        mStatus = status;
        mHint = null;
        mScrim = 0;
        mCompact = true;
        invalidate();
    }

    boolean isCompact() {
        return mCompact && mStatus != null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mStatus == null) return;
        canvas.drawColor(mScrim);
        float density = getResources().getDisplayMetrics().density;
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        float statusSize = (mCompact ? 14 : 20) * scaled, hintSize = 13 * scaled,
                pad = (mCompact ? 8 : 14) * density, gap = 4 * density;
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTypeface(mCompact ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
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
        if (mCompact) width += pad; // a pill: a little wider than tall
        width = Math.min(width + 2 * pad, getWidth() - 2 * pad);
        float cx = getWidth() / 2f;
        float cy = mCompact ? Math.min(getHeight() / 2f, 28 * density) : getHeight() / 2f;
        mRect.set(cx - width / 2, cy - height / 2 - pad, cx + width / 2, cy + height / 2 + pad);
        mPaint.setColor(0xE0202124);
        float radius = mCompact ? mRect.height() / 2 : 16 * density;
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
        float y = mRect.top + pad + statusSize * 0.8f;
        mPaint.setColor(0xFFFFFFFF);
        mPaint.setTypeface(mCompact ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
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
