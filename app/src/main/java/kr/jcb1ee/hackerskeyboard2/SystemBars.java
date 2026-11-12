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

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Apps targeting Android 15+ are drawn edge to edge, under the status and navigation bars.
 * This keeps a screen's content (the settings lists) clear of them: padding at the sides and
 * bottom, and at the top whatever the action bar leaves uncovered or covers. How the action
 * bar meets the content changed between AppCompat versions (1.8 lets it extend under the
 * status bar), so the top is measured from where the action bar actually ends.
 */
final class SystemBars {
    private SystemBars() {
    }

    static void padContent(final AppCompatActivity activity) {
        final View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        final int[] bars = new int[4];
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            bars[0] = i.left;
            bars[1] = i.top;
            bars[2] = i.right;
            bars[3] = i.bottom;
            update(activity, content, bars);
            return insets;
        });
        content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                update(activity, content, bars));
        View actionBar = activity.findViewById(androidx.appcompat.R.id.action_bar_container);
        if (actionBar != null) {
            actionBar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                    update(activity, content, bars));
        }
        ViewCompat.requestApplyInsets(content);
    }

    private static void update(AppCompatActivity activity, View content, int[] bars) {
        int top = topOf(content, activity.findViewById(androidx.appcompat.R.id.action_bar_container),
                activity.getSupportActionBar() != null && activity.getSupportActionBar().isShowing(),
                bars[1]);
        if (content.getPaddingLeft() != bars[0] || content.getPaddingTop() != top
                || content.getPaddingRight() != bars[2] || content.getPaddingBottom() != bars[3]) {
            content.setPadding(bars[0], top, bars[2], bars[3]);
        }
    }

    /**
     * The content's top padding: without an action bar, the status bar's height; with one, as
     * much as the action bar reaches below the content's top (0 when it sits above it).
     */
    static int topOf(View content, View actionBar, boolean actionBarShown, int statusBar) {
        if (!actionBarShown || actionBar == null) return statusBar;
        if (actionBar.getHeight() == 0) return 0; // not laid out yet; measured again after
        return Math.max(0, topInRoot(actionBar) + actionBar.getHeight() - topInRoot(content));
    }

    /** A view's top in its root view's coordinates (both views share the window's root). */
    private static int topInRoot(View v) {
        int y = 0;
        for (View p = v; p != null; ) {
            y += p.getTop() - (p == v ? 0 : p.getScrollY());
            p = p.getParent() instanceof View ? (View) p.getParent() : null;
        }
        return y;
    }
}
