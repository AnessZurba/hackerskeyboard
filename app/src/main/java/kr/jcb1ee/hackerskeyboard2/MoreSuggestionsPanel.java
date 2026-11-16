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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * More word suggestions, over the keys (Samsung's expanded suggestions): plain words in three
 * columns lined up under the strip's three words, continuing from them in reading order. It
 * opens downward from the toolbar row with the words fading in one after another, and closes
 * back up into the row.
 */
final class MoreSuggestionsPanel extends FrameLayout {
    /** Row height, as the strip's. */
    static final int ROW_DP = 45;
    static final long REVEAL_MS = 220, CLOSE_MS = 160, CHIP_MS = 150, STAGGER_MS = 18;

    private final ScrollView mScroll;
    private final Grid mFlow;
    private final List<String> mWords = new ArrayList<>();
    private String mHighlighted;
    private SuggestionStrip.Listener mListener;
    private int mBackground, mForeground, mAccent;
    private ValueAnimator mReveal;
    private boolean mClosing;

    MoreSuggestionsPanel(Context context) {
        super(context);
        mScroll = new ScrollView(context);
        mScroll.setVerticalScrollBarEnabled(false);
        mScroll.setClipToPadding(false);
        mFlow = new Grid(context);
        mFlow.setPadding(dp(40), 0, dp(40), dp(8));
        mScroll.addView(mFlow, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(mScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        // The keys under it must not get the touches.
        setClickable(true);
        setContentDescription(context.getString(R.string.suggestions_more));
    }

    void setListener(SuggestionStrip.Listener listener) {
        mListener = listener;
    }

    void setColours(int background, int foreground, int accent) {
        mBackground = background;
        mForeground = foreground;
        mAccent = accent;
        setBackgroundColor(background);
        for (int i = 0; i < mFlow.getChildCount(); i++) style((TextView) mFlow.getChildAt(i));
    }

    /**
     * Shows {@code words} (best first), {@code highlighted} (the pending auto-correction, or
     * null) in the accent colour; {@code rtl} lays the chips out right to left. Chips that
     * were not there before fade in, one after another, when {@code animate}.
     */
    void setWords(List<String> words, String highlighted, boolean rtl, boolean animate) {
        if (words.equals(mWords) && TextUtils.equals(highlighted, mHighlighted)
                && rtl == mFlow.mRtl) {
            return;
        }
        int kept = 0;
        while (kept < words.size() && kept < mWords.size() && words.get(kept).equals(mWords.get(kept))) {
            kept++;
        }
        mWords.clear();
        mWords.addAll(words);
        mHighlighted = highlighted;
        mFlow.mRtl = rtl;
        while (mFlow.getChildCount() > kept) mFlow.removeViewAt(mFlow.getChildCount() - 1);
        for (int i = kept; i < words.size(); i++) {
            final String word = words.get(i);
            TextView chip = new TextView(getContext());
            chip.setText(word);
            chip.setContentDescription(word);
            chip.setMaxLines(1);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setGravity(Gravity.CENTER);
            chip.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            chip.setPadding(dp(6), 0, dp(6), 0);
            chip.setFocusable(true);
            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (mListener != null) mListener.onSuggestionPicked(word);
            });
            mFlow.addView(chip, new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(ROW_DP)));
            if (animate) {
                chip.setAlpha(0f);
                chip.setTranslationY(-dp(6));
                chip.animate().alpha(1f).translationY(0f).setDuration(CHIP_MS)
                        .setStartDelay(Math.min(i - kept, 16) * STAGGER_MS)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        }
        for (int i = 0; i < mFlow.getChildCount(); i++) style((TextView) mFlow.getChildAt(i));
        mFlow.requestLayout();
    }

    /**
     * Where the strip's first and last word columns start and end, as distances from the
     * panel's left and right edges, so the columns line up under them.
     */
    void setColumnInsets(int left, int right) {
        if (left == mFlow.getPaddingLeft() && right == mFlow.getPaddingRight()) return;
        mFlow.setPadding(left, 0, right, mFlow.getPaddingBottom());
    }

    List<String> words() {
        return new ArrayList<>(mWords);
    }

    /** The chip for {@code word}, or null (tests). */
    TextView chip(String word) {
        int i = mWords.indexOf(word);
        return i >= 0 && i < mFlow.getChildCount() ? (TextView) mFlow.getChildAt(i) : null;
    }

    /** Chip {@code index}'s left edge, in the panel (tests: reading order). */
    int chipLeft(int index) {
        return mFlow.getChildAt(index).getLeft();
    }

    boolean isRightToLeft() {
        return mFlow.mRtl;
    }

    boolean isClosing() {
        return mClosing;
    }

    private void style(TextView chip) {
        boolean highlighted = chip.getText().toString().equals(mHighlighted);
        chip.setTextColor(highlighted ? mAccent : mForeground);
        chip.setTypeface(highlighted ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadius(dp(10));
        pressed.setColor(SuggestionStrip.blend(mBackground, mForeground, 0.28f));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], new android.graphics.drawable.ColorDrawable(0));
        chip.setBackground(states);
    }

    /**
     * Opens downward from the top edge (the toolbar row), over what is below; {@code done}
     * runs once it covers the keys.
     */
    void reveal(boolean animate, Runnable done) {
        cancelReveal();
        mClosing = false;
        if (!animate) {
            setClipBounds(null);
            if (done != null) done.run();
            return;
        }
        setClipBounds(new Rect(0, 0, Math.max(1, getWidth()), 0));
        mScroll.setTranslationY(-dp(16));
        mReveal = ValueAnimator.ofFloat(0f, 1f);
        mReveal.setDuration(REVEAL_MS);
        mReveal.setInterpolator(new DecelerateInterpolator(1.6f));
        mReveal.addUpdateListener(a -> clipTo((float) a.getAnimatedValue()));
        mReveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setClipBounds(null);
                mScroll.setTranslationY(0f);
                if (mReveal == animation) mReveal = null;
                if (done != null) done.run();
            }
        });
        mReveal.start();
    }

    /** Closes back up into the toolbar row; {@code done} runs once it's out of sight. */
    void conceal(boolean animate, Runnable done) {
        cancelReveal();
        mClosing = true;
        if (!animate) {
            if (done != null) done.run();
            return;
        }
        mReveal = ValueAnimator.ofFloat(1f, 0f);
        mReveal.setDuration(CLOSE_MS);
        mReveal.setInterpolator(new DecelerateInterpolator());
        mReveal.addUpdateListener(a -> clipTo((float) a.getAnimatedValue()));
        mReveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mReveal == animation) mReveal = null;
                if (done != null) done.run();
            }
        });
        mReveal.start();
    }

    private void clipTo(float fraction) {
        int h = getHeight() > 0 ? getHeight() : getLayoutParams() != null
                ? Math.max(0, getLayoutParams().height) : 0;
        setClipBounds(new Rect(0, 0, Math.max(1, getWidth()), Math.round(h * fraction)));
        mScroll.setTranslationY(-dp(16) * (1f - fraction));
        mScroll.setAlpha(0.4f + 0.6f * fraction);
    }

    private void cancelReveal() {
        if (mReveal == null) return;
        ValueAnimator a = mReveal;
        mReveal = null;
        a.removeAllListeners();
        a.cancel();
        mScroll.setAlpha(1f);
        mScroll.setTranslationY(0f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * Words in three equal columns (as the strip's), left to right row by row; right to left
     * for Hebrew and Arabic.
     */
    static final class Grid extends ViewGroup {
        static final int COLUMNS = 3;
        boolean mRtl;

        Grid(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int inner = Math.max(0, width - getPaddingLeft() - getPaddingRight());
            int column = inner / COLUMNS;
            int rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.measure(MeasureSpec.makeMeasureSpec(column, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(child.getLayoutParams().height,
                                MeasureSpec.EXACTLY));
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            }
            int rows = (getChildCount() + COLUMNS - 1) / COLUMNS;
            int height = rows * rowHeight + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int inner = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
                int col = i % COLUMNS, row = i / COLUMNS;
                int x = inner * col / COLUMNS;
                int left = mRtl ? getPaddingLeft() + inner - x - w : getPaddingLeft() + x;
                int top = getPaddingTop() + row * h;
                child.layout(left, top, left + w, top + h);
            }
        }
    }
}
