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
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A one-line tip in the toolbar row, in the buttons' place like the word suggestions
 * (see {@link HintManager}), with an × that dismisses it for good. Colours come from the
 * toolbar.
 */
final class TipStrip extends LinearLayout {
    private final TextView mText;
    private final TextView mClose;
    private Runnable mOnDismiss;
    private int mBackground = Color.BLACK, mForeground = Color.WHITE;

    TipStrip(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        mText = new TextView(context);
        mText.setMaxLines(2); // short copy fits one line; a narrow screen wraps, never cuts
        mText.setEllipsize(TextUtils.TruncateAt.END);
        mText.setGravity(Gravity.CENTER_VERTICAL);
        mText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mText.setPadding(dp(12), 0, dp(4), 0);
        LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
        addView(mText, lp);
        mClose = new TextView(context);
        mClose.setText("×");
        mClose.setGravity(Gravity.CENTER);
        mClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        mClose.setContentDescription(context.getString(R.string.tip_dismiss));
        mClose.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (mOnDismiss != null) mOnDismiss.run();
        });
        LayoutParams close = new LayoutParams(dp(44), dp(38));
        close.setMargins(0, 0, dp(2), 0);
        addView(mClose, close);
        applyColours();
    }

    void setText(CharSequence text) {
        mText.setText(text);
        mText.setContentDescription(text);
    }

    CharSequence text() {
        return mText.getText();
    }

    /** The × button (tests). */
    TextView closeButton() {
        return mClose;
    }

    void setOnDismiss(Runnable onDismiss) {
        mOnDismiss = onDismiss;
    }

    void setColours(int background, int foreground) {
        mBackground = background;
        mForeground = foreground;
        applyColours();
    }

    private void applyColours() {
        mText.setTextColor(mForeground);
        mClose.setTextColor(blend(mBackground, mForeground, 0.7f));
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.OVAL);
        pressed.setColor(blend(mBackground, mForeground, 0.25f));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        mClose.setBackground(states);
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
