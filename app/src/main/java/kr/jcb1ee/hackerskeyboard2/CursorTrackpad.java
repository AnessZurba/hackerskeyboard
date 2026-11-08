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

/**
 * Turns finger movement into cursor steps, iOS trackpad style: one character per 6dp
 * sideways, one line per 24dp up or down, and a faster finger covers up to 4x more.
 * Used by the space-bar trackpad and the selection panel's Select button.
 */
final class CursorTrackpad {
    interface Mover {
        /** Move by characters (negative = left); return whether anything moved. */
        boolean chars(int n);

        /** Move by lines (negative = up); return whether anything moved. */
        boolean lines(int n);
    }

    private final float mDensity;
    private final float mCharStep;
    private float mLastX, mLastY;
    private long mLastTime;
    private float mAccumX, mAccumY;

    CursorTrackpad(float density) {
        mDensity = density;
        mCharStep = Math.max(1f, 6 * density);
    }

    void start(float x, float y, long time) {
        mLastX = x;
        mLastY = y;
        mLastTime = time;
        mAccumX = mAccumY = 0;
    }

    /** Feeds a finger position; returns whether the cursor moved (for haptics). */
    boolean move(float x, float y, long time, Mover mover) {
        long dt = Math.max(1, time - mLastTime);
        float dx = x - mLastX, dy = y - mLastY;
        mLastX = x;
        mLastY = y;
        mLastTime = time;
        // Acceleration: dp per millisecond above a slow drag speeds the cursor up.
        float speed = (float) Math.hypot(dx, dy) / mDensity / dt;
        float boost = 1 + Math.max(0f, Math.min(3f, (speed - 0.25f) * 3f));
        mAccumX += dx * boost / mCharStep;
        mAccumY += dy * boost / (mCharStep * 4f);
        int chars = (int) mAccumX;
        int lines = (int) mAccumY;
        mAccumX -= chars;
        mAccumY -= lines;
        boolean moved = false;
        if (chars != 0) moved |= mover.chars(chars);
        if (lines != 0) moved |= mover.lines(lines);
        return moved;
    }
}
