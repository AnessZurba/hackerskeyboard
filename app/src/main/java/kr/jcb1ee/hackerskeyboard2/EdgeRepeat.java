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

import android.os.Handler;
import android.os.Looper;

/**
 * Keeps a drag gesture going while the finger rests at the edge of the screen, where it
 * can't move any further: the trackpad keeps moving the cursor that way, the backspace
 * swipe keeps taking words. Steps start after a short pause and speed up the longer the
 * finger stays; leaving the edge (or lifting) stops them.
 */
final class EdgeRepeat {
    interface Step {
        /** One step towards {@code direction} (-1 or +1). */
        void step(int direction);
    }

    /** How close to an edge (dp) counts as at the edge. */
    static final int EDGE_DP = 20;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final long mFirstDelay, mSlowest, mFastest;
    private int mDirection, mCount;
    private Step mStep;

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            if (mDirection == 0 || mStep == null) return;
            mStep.step(mDirection);
            mCount++;
            // From the slowest interval to the fastest over ten steps.
            long delay = Math.max(mFastest, mSlowest - mCount * (mSlowest - mFastest) / 10);
            mHandler.postDelayed(this, delay);
        }
    };

    EdgeRepeat(long firstDelay, long slowest, long fastest) {
        mFirstDelay = firstDelay;
        mSlowest = slowest;
        mFastest = fastest;
    }

    /** -1 at the start edge, +1 at the end edge, else 0. */
    static int direction(float position, float start, float end, float zone) {
        if (position <= start + zone) return -1;
        if (position >= end - zone) return 1;
        return 0;
    }

    /** Repeats {@code step} towards {@code direction} (0 stops). */
    void set(int direction, Step step) {
        mStep = step;
        if (direction == mDirection) return;
        stop();
        if (direction == 0) return;
        mDirection = direction;
        mCount = 0;
        mHandler.postDelayed(mTick, mFirstDelay);
    }

    void stop() {
        mHandler.removeCallbacks(mTick);
        mDirection = 0;
    }
}
