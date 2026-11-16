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
 * More word suggestions, over the keys (Samsung's expanded suggestions): the candidates as
 * chips that wrap from line to line, best first in reading order. It opens downward from the
 * toolbar row with the chips fading in one after another, and closes back up into the row.
 */
final class MoreSuggestionsPanel extends FrameLayout {
    static final long REVEAL_MS = 220, CLOSE_MS = 160, CHIP_MS = 150, STAGGER_MS = 18;

    private final ScrollView mScroll;
    private final FlowLayout mFlow;
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
        mFlow = new FlowLayout(context);
        mFlow.setPadding(dp(6), dp(4), dp(6), dp(8));
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
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setMinWidth(dp(56));
            chip.setFocusable(true);
            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (mListener != null) mListener.onSuggestionPicked(word);
            });
            mFlow.addView(chip, new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
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
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(dp(10));
        normal.setColor(SuggestionStrip.blend(mBackground, mForeground, 0.10f));
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadius(dp(10));
        pressed.setColor(SuggestionStrip.blend(mBackground, mForeground, 0.28f));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], normal);
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
     * Chips left to right (right to left for Hebrew and Arabic), wrapping to a new line when
     * full; each full line's chips grow to fill it, so the panel reads as a tidy grid.
     */
    static final class FlowLayout extends ViewGroup {
        boolean mRtl;
        private final int mGap;

        FlowLayout(Context context) {
            super(context);
            mGap = Math.round(6 * context.getResources().getDisplayMetrics().density);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int inner = Math.max(0, width - getPaddingLeft() - getPaddingRight());
            int x = 0, lineHeight = 0, height = 0;
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                int h = child.getLayoutParams().height;
                child.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                int w = child.getMeasuredWidth();
                if (x > 0 && x + mGap + w > inner) {
                    height += lineHeight + mGap;
                    x = 0;
                    lineHeight = 0;
                }
                x += (x > 0 ? mGap : 0) + w;
                lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
            }
            height += lineHeight + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int inner = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
            int count = getChildCount();
            int start = 0, y = getPaddingTop();
            while (start < count) {
                // One line: as many chips as fit.
                int end = start, used = 0, lineHeight = 0;
                while (end < count) {
                    int w = getChildAt(end).getMeasuredWidth();
                    int next = used + (end > start ? mGap : 0) + w;
                    if (end > start && next > inner) break;
                    used = next;
                    lineHeight = Math.max(lineHeight, getChildAt(end).getMeasuredHeight());
                    end++;
                }
                boolean full = end < count; // the last line keeps its chips' own widths
                int extra = full ? (inner - used) / (end - start) : 0;
                int x = 0;
                for (int i = start; i < end; i++) {
                    View child = getChildAt(i);
                    int w = Math.min(inner, child.getMeasuredWidth() + extra);
                    if (w != child.getMeasuredWidth()) {
                        child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(child.getMeasuredHeight(),
                                        MeasureSpec.EXACTLY));
                    }
                    int left = mRtl ? getPaddingLeft() + inner - x - w : getPaddingLeft() + x;
                    child.layout(left, y, left + w, y + child.getMeasuredHeight());
                    x += w + mGap;
                }
                y += lineHeight + mGap;
                start = end;
            }
        }
    }
}
