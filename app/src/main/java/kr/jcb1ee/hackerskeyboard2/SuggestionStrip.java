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

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Up to three word suggestions in the toolbar row, in place of its buttons (Samsung style):
 * the best one in the middle, the next ones beside it, "⋯" at the start for more suggestions
 * (a panel over the keys) and an arrow at the end that brings the toolbar's buttons back.
 * Right-to-left words mirror the row. Colours come from the toolbar.
 */
final class SuggestionStrip extends LinearLayout {
    /** What the strip's taps do. */
    interface Listener {
        void onSuggestionPicked(String word);

        /** "⋯" (or, while the panel is open, its collapse arrow) was tapped. */
        default void onMoreToggled() {
        }

        /** The end arrow was tapped: the toolbar's buttons instead of the words. */
        default void onShowToolbar() {
        }
    }

    static final int LEFT = 0, MIDDLE = 1, RIGHT = 2;
    /** How long a changed word takes to settle in, before the system's animation scale. */
    static final long WORD_CHANGE_MS = 130;

    /** The three word views, left to right on screen (see {@link #slotView}). */
    private final TextView[] mSlots = new TextView[3];
    private final View[] mDividers = new View[2];
    private final String[] mWords = new String[3];
    private final ImageView mMore;
    private final ImageView mArrow;
    private boolean mExpanded;
    private boolean mMiddleIsCorrection;
    private boolean mRtl;
    private int mAnimatedSlots;
    private int mBackground = Color.BLACK, mForeground = Color.WHITE, mAccent = 0xFF8AB4F8;

    private Listener mListener;

    SuggestionStrip(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        mMore = button(R.drawable.ic_suggestions_more, R.string.suggestions_more, () -> {
            if (mListener != null) mListener.onMoreToggled();
        });
        mArrow = button(R.drawable.ic_toolbar_chips_dismiss, R.string.toolbar_show_buttons, () -> {
            if (mListener != null) mListener.onShowToolbar();
        });
        addView(mMore, new LayoutParams(dp(40), LayoutParams.MATCH_PARENT));
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
        addView(mArrow, new LayoutParams(dp(40), LayoutParams.MATCH_PARENT));
        applyColours();
    }

    private ImageView button(int icon, int description, Runnable action) {
        ImageView b = new ImageView(getContext());
        b.setImageResource(icon);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setContentDescription(getContext().getString(description));
        b.setFocusable(true);
        b.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            action.run();
        });
        return b;
    }

    void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Shows the words (any may be null); {@code middleIsCorrection}: the middle one is what
     * space will put in place of the typed word. Words that changed settle in with a short
     * fade and slide (none when the system's animations are off).
     */
    void setWords(String left, String middle, String right, boolean middleIsCorrection) {
        boolean had = hasWords();
        String[] before = new String[3];
        for (int i = 0; i < 3; i++) before[i] = mSlots[i].getText().toString();
        mWords[LEFT] = left;
        mWords[MIDDLE] = middle;
        mWords[RIGHT] = right;
        mMiddleIsCorrection = middleIsCorrection && middle != null;
        // Right-to-left words: the second and third suggestions swap sides, as the keyboard
        // apps for those languages do; the best one stays in the middle, and "⋯" and the arrow
        // swap ends. The views are swapped here rather than through the layout direction: the
        // app does not declare supportsRtl, so views never lay out right to left.
        boolean rtl = isRtl(middle != null ? middle : left != null ? left : right);
        if (rtl != mRtl) {
            mRtl = rtl;
            placeButtons();
        }
        boolean animate = had && hasWords() && getVisibility() == VISIBLE
                && animationsEnabled(getContext());
        mAnimatedSlots = 0;
        for (int i = 0; i < 3; i++) {
            String word = mWords[slotAt(i)];
            String text = word != null ? word : "";
            TextView v = mSlots[i];
            v.setText(text);
            v.setClickable(word != null);
            v.setFocusable(word != null);
            v.setContentDescription(word);
            if (animate && !text.equals(before[i])) {
                mAnimatedSlots++;
                v.animate().cancel();
                v.setAlpha(0.3f);
                v.setTranslationY(dp(5));
                v.animate().alpha(1f).translationY(0f).setDuration(WORD_CHANGE_MS)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else if (!animate) {
                v.animate().cancel();
                v.setAlpha(1f);
                v.setTranslationY(0f);
            }
        }
        applyColours();
    }

    /** "⋯" at the start and the arrow at the end, in reading order. */
    private void placeButtons() {
        removeView(mMore);
        removeView(mArrow);
        LayoutParams lp = new LayoutParams(dp(40), LayoutParams.MATCH_PARENT);
        addView(mRtl ? mArrow : mMore, 0, lp);
        addView(mRtl ? mMore : mArrow, new LayoutParams(dp(40), LayoutParams.MATCH_PARENT));
        // The arrow points toward the end of the row.
        mArrow.setScaleX(mRtl ? -1f : 1f);
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

    /** The "⋯" / collapse button (tests). */
    ImageView moreButton() {
        return mMore;
    }

    /** The arrow that brings the toolbar's buttons back (tests). */
    ImageView toolbarButton() {
        return mArrow;
    }

    /** How many words the last {@link #setWords} animated (tests). */
    int animatedSlots() {
        return mAnimatedSlots;
    }

    /** "⋯" while the more-suggestions panel is closed, a collapse arrow while it's open. */
    void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        mMore.setImageResource(expanded ? R.drawable.ic_suggestions_collapse
                : R.drawable.ic_suggestions_more);
        mMore.setContentDescription(getContext().getString(expanded
                ? R.string.suggestions_fewer : R.string.suggestions_more));
        if (animationsEnabled(getContext())) {
            mMore.animate().cancel();
            mMore.setAlpha(0.2f);
            mMore.setRotation(expanded ? 90f : -90f);
            mMore.animate().alpha(1f).rotation(0f).setDuration(WORD_CHANGE_MS)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        applyColours();
    }

    boolean isExpanded() {
        return mExpanded;
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
            v.setBackground(pressedBackground(false));
        }
        for (View d : mDividers) {
            d.setBackgroundColor(blend(mBackground, mForeground, 0.3f));
            d.setVisibility(hasWords() ? VISIBLE : INVISIBLE);
        }
        mMore.setImageTintList(ColorStateList.valueOf(mExpanded ? mAccent
                : blend(mBackground, mForeground, 0.75f)));
        mArrow.setImageTintList(ColorStateList.valueOf(blend(mBackground, mForeground, 0.7f)));
        mMore.setBackground(pressedBackground(true));
        mArrow.setBackground(pressedBackground(true));
    }

    private StateListDrawable pressedBackground(boolean round) {
        GradientDrawable pressed = new GradientDrawable();
        if (round) pressed.setShape(GradientDrawable.OVAL);
        else pressed.setCornerRadius(dp(8));
        pressed.setColor(blend(mBackground, mForeground, 0.25f));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        return states;
    }

    /**
     * Whether the system's animations are on (Settings, Developer options, "Animator duration
     * scale" not off, nor turned off by battery saver).
     */
    static boolean animationsEnabled(Context context) {
        float scale = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        if (scale == 0f) return false;
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O
                || ValueAnimator.areAnimatorsEnabled();
    }

    static boolean isRtl(String word) {
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

    static int blend(int a, int b, float t) {
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
