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
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Up to three word suggestions in the toolbar row, in place of its buttons (Samsung / Gboard
 * style): the best one in the middle, the next ones beside it. Colours come from the toolbar.
 */
final class SuggestionStrip extends LinearLayout {
    /** A tapped suggestion. */
    interface Listener {
        void onSuggestionPicked(String word);
    }

    static final int LEFT = 0, MIDDLE = 1, RIGHT = 2;

    /** The three word views, left to right on screen (see {@link #slotView}). */
    private final TextView[] mSlots = new TextView[3];
    private final View[] mDividers = new View[2];
    private final String[] mWords = new String[3];
    private boolean mMiddleIsCorrection;
    private boolean mRtl;
    private int mBackground = Color.BLACK, mForeground = Color.WHITE, mAccent = 0xFF8AB4F8;

    private Listener mListener;

    SuggestionStrip(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                View divider = new View(context);
                mDividers[i - 1] = divider;
                LayoutParams lp = new LayoutParams(Math.max(1, dp(1)), dp(20));
                addView(divider, lp);
            }
            final TextView v = new TextView(context);
            // maxLines, not singleLine: a single-line TextView lays out "very wide" and
            // draws centred text off screen.
            v.setMaxLines(1);
            v.setEllipsize(TextUtils.TruncateAt.END);
            v.setGravity(Gravity.CENTER);
            v.setPadding(dp(6), 0, dp(6), 0);
            // Hebrew / Arabic words read right to left whatever the strip's direction.
            v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            final int position = i;
            v.setOnClickListener(view -> {
                String word = mWords[slotAt(position)];
                if (word == null || mListener == null) return;
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                mListener.onSuggestionPicked(word);
            });
            mSlots[i] = v;
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
            lp.setMargins(dp(2), dp(3), dp(2), dp(3));
            addView(v, lp);
        }
        applyColours();
    }

    void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Shows the words (any may be null); {@code middleIsCorrection}: the middle one is what
     * space will put in place of the typed word.
     */
    void setWords(String left, String middle, String right, boolean middleIsCorrection) {
        mWords[LEFT] = left;
        mWords[MIDDLE] = middle;
        mWords[RIGHT] = right;
        mMiddleIsCorrection = middleIsCorrection && middle != null;
        // Right-to-left words: the second and third suggestions swap sides, as the keyboard
        // apps for those languages do; the best one stays in the middle. The views are swapped
        // here rather than through the layout direction: the app does not declare
        // supportsRtl, so views never lay out right to left.
        mRtl = isRtl(middle != null ? middle : left != null ? left : right);
        for (int i = 0; i < 3; i++) {
            String word = mWords[slotAt(i)];
            mSlots[i].setText(word != null ? word : "");
            mSlots[i].setClickable(word != null);
            mSlots[i].setContentDescription(word);
        }
        applyColours();
    }

    void clear() {
        setWords(null, null, null, false);
    }

    boolean hasWords() {
        return mWords[LEFT] != null || mWords[MIDDLE] != null || mWords[RIGHT] != null;
    }

    /** The word in {@code slot} (LEFT, MIDDLE, RIGHT), or null. */
    String wordAt(int slot) {
        return mWords[slot];
    }

    /** The view showing {@code slot}'s word (on the right for LEFT with right-to-left words). */
    TextView slotView(int slot) {
        return mSlots[slotAt(slot)];
    }

    /** The slot shown at {@code position} (0-2, left to right), and the other way round. */
    private int slotAt(int position) {
        return mRtl && position != MIDDLE ? 2 - position : position;
    }

    /** Whether the words are laid out right to left (Hebrew, Arabic). */
    boolean isRightToLeft() {
        return mRtl;
    }

    boolean middleIsCorrection() {
        return mMiddleIsCorrection;
    }

    void setColours(int background, int foreground, int accent) {
        mBackground = background;
        mForeground = foreground;
        mAccent = accent;
        applyColours();
    }

    private void applyColours() {
        for (int i = 0; i < 3; i++) {
            TextView v = mSlots[i];
            boolean middle = i == MIDDLE;
            v.setTextColor(middle && mMiddleIsCorrection ? mAccent : mForeground);
            v.setTypeface(middle ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            GradientDrawable pressed = new GradientDrawable();
            pressed.setCornerRadius(dp(8));
            pressed.setColor(blend(mBackground, mForeground, 0.25f));
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[] {android.R.attr.state_pressed}, pressed);
            v.setBackground(states);
        }
        for (View d : mDividers) {
            d.setBackgroundColor(blend(mBackground, mForeground, 0.3f));
            d.setVisibility(hasWords() ? VISIBLE : INVISIBLE);
        }
    }

    private static boolean isRtl(String word) {
        if (word == null) return false;
        for (int i = 0; i < word.length(); ) {
            int c = word.codePointAt(i);
            switch (Character.getDirectionality(c)) {
                case Character.DIRECTIONALITY_RIGHT_TO_LEFT:
                case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
                    return true;
                case Character.DIRECTIONALITY_LEFT_TO_RIGHT:
                    return false;
                default:
                    break;
            }
            i += Character.charCount(c);
        }
        return false;
    }

    private static int blend(int a, int b, float t) {
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
