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
 * Backspace swipe distance to words, with acceleration: one word per 14dp
 * when sliding slowly, up to 4x when fast; sliding back selects fewer, and back over the
 * key none. Used by the keyboard's backspace and the toolbar panels' backspace keys.
 */
final class DeleteSwipeAccel {
    private final float mDensity;
    private final float mStep;
    private float mDownX, mLastX;
    private long mLastTime;
    private float mWords;

    DeleteSwipeAccel(float density) {
        mDensity = density;
        mStep = Math.max(1f, 14 * density);
    }

    /** How far left of the key the swipe starts. */
    float step() {
        return mStep;
    }

    void start(float downX, float x, long time) {
        mDownX = downX;
        mWords = Math.max(0f, (downX - x) / mStep);
        mLastX = x;
        mLastTime = time;
    }

    /** One more word (the finger rests at the screen's edge); returns the count. */
    int addWord() {
        mWords = (int) mWords + 1;
        return (int) mWords;
    }

    int words(float x, long time) {
        if (x >= mDownX - mStep / 2) {
            mWords = 0; // back on the key: cancel
        } else {
            long dt = Math.max(1, time - mLastTime);
            float dx = mLastX - x; // leftwards = more words
            float speed = Math.abs(dx) / mDensity / dt;
            // A bit livelier than the trackpad's: speeds up from a slower slide, and sooner.
            float boost = 1 + Math.max(0f, Math.min(3f, (speed - 0.18f) * 4f));
            mWords = Math.max(0f, mWords + dx * boost / mStep);
        }
        mLastX = x;
        mLastTime = time;
        return (int) mWords;
    }
}
