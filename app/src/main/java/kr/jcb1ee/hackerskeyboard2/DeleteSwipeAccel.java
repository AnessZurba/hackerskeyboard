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
 * when sliding slowly, up to 4x when fast; sliding back selects fewer, and back on the key
 * none (elsewhere at the screen's right edge it keeps giving words back). Used by the keyboard's backspace and the toolbar panels' backspace keys.
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

    // The backspace key (in the same coordinates as the finger). Once the finger has left
    // it, coming back onto it cancels; anywhere else, sliding right just gives words back
    // (and at the screen's right edge keeps giving them back, see EdgeRepeat), even though
    // the key itself sits at that edge.
    private final android.graphics.RectF mKey = new android.graphics.RectF();
    private boolean mHasKey, mLeftKey;

    void setKey(float left, float top, float right, float bottom) {
        mKey.set(left, top, right, bottom);
        mHasKey = true;
        mLeftKey = false;
    }

    /** Whether the finger is back on the backspace key after having left it. */
    boolean backOnKey(float x, float y) {
        boolean on = mHasKey && mKey.contains(x, y);
        if (!on) mLeftKey = true;
        return on && mLeftKey;
    }

    /** One word fewer (the finger rests at the right edge); returns the count. */
    int removeWord() {
        mWords = Math.max(0, (float) Math.ceil(mWords) - 1);
        return (int) mWords;
    }

    int words(float x, float y, long time) {
        // Back on the key, or (before leaving it) back where the swipe started: cancel.
        boolean onKey = mHasKey && mKey.contains(x, y);
        if (backOnKey(x, y) || (!mLeftKey && (!mHasKey || onKey) && x >= mDownX - mStep / 2)) {
            mWords = 0;
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
