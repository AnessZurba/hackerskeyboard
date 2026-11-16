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

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

/**
 * The home screen's practice card: a few gestures to try in the test field, each ticked off
 * when the keyboard sees it done (the same points that retire the tips, see
 * {@link HintManager#learned}). "Hide" puts it away; "Reset tips" in the settings brings it
 * back, unticked. The keyboard runs in this app's process, so ticks appear as they happen.
 */
final class PracticeCard implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int[] LABELS = {R.string.practice_space_trackpad,
            R.string.practice_backspace_swipe, R.string.practice_undo_swipe,
            R.string.practice_swipe_actions, R.string.practice_select_trackpad};

    private final View mCard;
    private final TextView mSummary;
    private final CheckBox[] mItems = new CheckBox[HintManager.PRACTICE.length];
    private final SharedPreferences mPrefs;

    PracticeCard(Activity activity) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        mCard = activity.findViewById(R.id.main_practice_card);
        mSummary = activity.findViewById(R.id.main_practice_summary);
        LinearLayout list = activity.findViewById(R.id.main_practice_list);
        for (int i = 0; i < mItems.length; i++) {
            CheckBox box = new CheckBox(activity);
            box.setText(LABELS[i]);
            box.setTextSize(14);
            box.setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.setup_text_primary));
            box.setClickable(false); // ticked by doing the gesture, not by tapping
            box.setFocusable(false);
            mItems[i] = box;
            list.addView(box);
        }
        activity.findViewById(R.id.main_practice_hide).setOnClickListener(v ->
                mPrefs.edit().putBoolean(HintManager.PREF_PRACTICE_HIDDEN, true).apply());
        refresh();
    }

    void start() {
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        refresh();
    }

    void stop() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key == null || key.startsWith("tip_")) refresh(); // null: cleared
    }

    void refresh() {
        boolean all = true;
        for (int i = 0; i < mItems.length; i++) {
            boolean done = HintManager.isPracticed(mPrefs, HintManager.PRACTICE[i]);
            mItems[i].setChecked(done);
            all &= done;
        }
        mSummary.setText(all ? R.string.practice_done : R.string.practice_summary);
        mCard.setVisibility(mPrefs.getBoolean(HintManager.PREF_PRACTICE_HIDDEN, false)
                ? View.GONE : View.VISIBLE);
    }

    /** How many are ticked off (tests). */
    int done() {
        int n = 0;
        for (CheckBox box : mItems) if (box.isChecked()) n++;
        return n;
    }
}
