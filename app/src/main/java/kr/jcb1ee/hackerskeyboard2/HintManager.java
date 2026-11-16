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

import android.content.SharedPreferences;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;

/**
 * One-time tips about the keyboard's gestures, shown in the toolbar row when what the user
 * does suggests a gesture would help (five Backspace taps in a row: the backspace swipe).
 * Everything is kept in the app's own preferences; nothing leaves the device.
 *
 * <p>Rules: at most one tip per keyboard session, each tip at most {@link #MAX_SHOWS} times
 * ever, never again once its gesture was used or the tip was dismissed, never in terminals,
 * password, number, phone or date fields, and only where the toolbar row can show it (the
 * host says no while the row is hidden, autofill chips are offered or words are suggested).
 */
final class HintManager {
    static final String PREF_SHOW_TIPS = "pref_show_tips";
    static final String PREF_RESET_TIPS = "pref_reset_tips";
    private static final String KEY_PREFIX = "tip_";
    private static final String KEY_OPENS = "tip_count_opens";
    private static final String KEY_LANGUAGE_SWITCHES = "tip_count_language_switches";
    private static final String KEY_CLIPBOARD_OPENS = "tip_count_clipboard_opens";

    static final int MAX_SHOWS = 3;
    static final int BACKSPACE_TAPS = 5;
    static final long BACKSPACE_WINDOW_MS = 3000;
    static final int ARROW_TAPS = 2;
    static final int CURSOR_PLACES = 3;
    static final long CURSOR_WINDOW_MS = 30000;
    static final int SELECT_TAPS = 3;
    static final int KEYBOARD_OPENS = 3;
    static final int LANGUAGE_SWITCHES = 3;
    static final int CLIPBOARD_OPENS = 3;
    static final int UNDOS = 2;

    /** The home screen's practice card: these gestures, ticked off once done (see Main). */
    static final Tip[] PRACTICE = {Tip.SPACE_TRACKPAD, Tip.BACKSPACE_SWIPE, Tip.UNDO_SWIPE,
            Tip.SWIPE_ACTIONS, Tip.SELECT_TRACKPAD};
    static final String PREF_PRACTICE_HIDDEN = "tip_practice_hidden";

    /** The tips, each with the gesture it teaches. */
    enum Tip {
        BACKSPACE_SWIPE("backspace_swipe", R.string.tip_backspace_swipe),
        SPACE_TRACKPAD("space_trackpad", R.string.tip_space_trackpad),
        SELECT_TRACKPAD("select_trackpad", R.string.tip_select_trackpad),
        SWIPE_ACTIONS("swipe_actions", R.string.tip_swipe_actions),
        LANGUAGE_SLIDE("language_slide", R.string.tip_language_slide),
        CLIP_PIN("clip_pin", R.string.tip_clip_pin),
        UNDO_SWIPE("undo_swipe", R.string.tip_undo_swipe);

        final String key;
        final int text;

        Tip(String key, int text) {
            this.key = key;
            this.text = text;
        }
    }

    /** Where tips are shown: the toolbar row. */
    interface Host {
        /** Shows the tip now if the row can (shown by the user, no chips or words); true if shown. */
        boolean showTip(Tip tip);

        /** Takes the tip away (its gesture was used). */
        void hideTip();
    }

    private final SharedPreferences mPrefs;
    private Host mHost;
    private boolean mFieldAllows;
    private boolean mShownThisSession;
    private Tip mShowing;

    // Trigger state within a session.
    private final long[] mBackspaceTimes = new long[BACKSPACE_TAPS];
    private int mBackspaces;
    private int mArrows;
    private final long[] mCursorTimes = new long[CURSOR_PLACES];
    private int mCursorPlaces;
    private int mSelectTaps;
    private int mUndos;

    HintManager(SharedPreferences prefs) {
        mPrefs = prefs;
    }

    void setHost(Host host) {
        mHost = host;
    }

    // ------------------------------------------------------------------ rules

    boolean enabled() {
        return mPrefs.getBoolean(PREF_SHOW_TIPS, true);
    }

    int shownCount(Tip tip) {
        return mPrefs.getInt(KEY_PREFIX + tip.key + "_shown", 0);
    }

    boolean isLearned(Tip tip) {
        return mPrefs.getBoolean(KEY_PREFIX + tip.key + "_learned", false);
    }

    /** Whether the gesture was really done (not just its tip dismissed): the practice card. */
    static boolean isPracticed(SharedPreferences prefs, Tip tip) {
        return prefs.getBoolean(KEY_PREFIX + tip.key + "_practiced", false);
    }

    /** The tip on screen now, or null. */
    Tip showing() {
        return mShowing;
    }

    /** Whether tips may appear in this field: not terminals, passwords, numbers, phones or dates. */
    static boolean fieldAllowsTips(EditorInfo ei) {
        if (ei == null) return false;
        int type = ei.inputType;
        int cls = type & InputType.TYPE_MASK_CLASS;
        if (cls != InputType.TYPE_CLASS_TEXT) return false; // TYPE_NULL, numbers, phones, dates
        int variation = type & InputType.TYPE_MASK_VARIATION;
        return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD
                && variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                && variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    /**
     * The keyboard opened on a field. A new session (not a restart of the same field) may
     * show one tip again.
     */
    void onStartInput(EditorInfo ei, boolean restarting) {
        mFieldAllows = fieldAllowsTips(ei);
        if (restarting) return;
        mShownThisSession = false;
        resetTriggers();
    }

    /** The keyboard closed: a tip on screen goes with it. */
    void onFinishSession() {
        mShowing = null;
        resetTriggers();
    }

    private void resetTriggers() {
        mBackspaces = 0;
        mArrows = 0;
        mCursorPlaces = 0;
        mSelectTaps = 0;
        mUndos = 0;
    }

    /** Shows {@code tip} if every rule allows it; true if it is now on screen. */
    boolean maybeShow(Tip tip) {
        if (!enabled() || !mFieldAllows || mShownThisSession || mHost == null) return false;
        if (isLearned(tip) || shownCount(tip) >= MAX_SHOWS) return false;
        if (!mHost.showTip(tip)) return false;
        mShownThisSession = true;
        mShowing = tip;
        mPrefs.edit().putInt(KEY_PREFIX + tip.key + "_shown", shownCount(tip) + 1).apply();
        return true;
    }

    /** The user used the gesture: its tip never shows again (and goes if on screen). */
    void learned(Tip tip) {
        if (!isLearned(tip) || !isPracticed(mPrefs, tip)) {
            mPrefs.edit().putBoolean(KEY_PREFIX + tip.key + "_learned", true)
                    .putBoolean(KEY_PREFIX + tip.key + "_practiced", true).apply();
        }
        if (mShowing == tip) {
            mShowing = null;
            if (mHost != null) mHost.hideTip();
        }
    }

    /** The user closed the tip with ×: it never shows again. */
    void dismissed() {
        Tip tip = mShowing;
        mShowing = null;
        if (tip != null) mPrefs.edit().putBoolean(KEY_PREFIX + tip.key + "_learned", true).apply();
    }

    /** The tip went by itself (timed out, or the row needed its place). */
    void hidden() {
        mShowing = null;
    }

    /** "Reset tips": every tip may show again, as on a new install. */
    void reset() {
        resetAll(mPrefs);
        mShownThisSession = false;
        resetTriggers();
    }

    static void resetAll(SharedPreferences prefs) {
        SharedPreferences.Editor e = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(KEY_PREFIX)) e.remove(key);
        }
        e.apply();
    }

    // ------------------------------------------------------------------ triggers

    /** A Backspace tap (not a repeat): five in a row within 3 s suggest the swipe. */
    void onBackspaceTap(long now) {
        mArrows = 0;
        mBackspaceTimes[mBackspaces % BACKSPACE_TAPS] = now;
        mBackspaces++;
        if (mBackspaces >= BACKSPACE_TAPS) {
            long oldest = mBackspaceTimes[mBackspaces % BACKSPACE_TAPS]; // 5 taps ago
            if (now - oldest <= BACKSPACE_WINDOW_MS) {
                if (maybeShow(Tip.BACKSPACE_SWIPE)) mBackspaces = 0;
            }
        }
    }

    /** An arrow-key tap on the keyboard or the selection panel: two in a row suggest the trackpad. */
    void onArrowTap() {
        mBackspaces = 0;
        mArrows++;
        if (mArrows >= ARROW_TAPS && maybeShow(Tip.SPACE_TRACKPAD)) mArrows = 0;
    }

    /** Any other key: breaks a run of Backspace or arrow taps. */
    void onOtherKey() {
        mBackspaces = 0;
        mArrows = 0;
    }

    /** The cursor was placed by tapping in the text (not by the keyboard). */
    void onCursorPlaced(long now) {
        mCursorTimes[mCursorPlaces % CURSOR_PLACES] = now;
        mCursorPlaces++;
        if (mCursorPlaces >= CURSOR_PLACES
                && now - mCursorTimes[mCursorPlaces % CURSOR_PLACES] <= CURSOR_WINDOW_MS
                && maybeShow(Tip.SELECT_TRACKPAD)) {
            mCursorPlaces = 0;
        }
    }

    /** A tap on the selection panel's Select button. */
    void onSelectButtonTap() {
        mSelectTaps++;
        if (mSelectTaps >= SELECT_TAPS && maybeShow(Tip.SELECT_TRACKPAD)) mSelectTaps = 0;
    }

    /**
     * Undo or redo by other means (the selection panel's buttons, Ctrl+Z): twice in a session
     * suggests the swipe, while swipes left/right do undo/redo.
     */
    void onUndoOtherwise(boolean swipesUndo) {
        mUndos++;
        if (mUndos >= UNDOS && swipesUndo && maybeShow(Tip.UNDO_SWIPE)) mUndos = 0;
    }

    /** The keyboard opened (a new field, not a restart); from the third time, the swipes tip. */
    void onKeyboardOpened(boolean swipesAsTipSays) {
        if (isLearned(Tip.SWIPE_ACTIONS)) return;
        int opens = mPrefs.getInt(KEY_OPENS, 0) + 1;
        mPrefs.edit().putInt(KEY_OPENS, opens).apply();
        if (opens >= KEYBOARD_OPENS && swipesAsTipSays) maybeShow(Tip.SWIPE_ACTIONS);
    }

    /** The language was switched with a key or a swipe action (not the space-bar slide). */
    void onLanguageSwitchedOtherwise() {
        if (isLearned(Tip.LANGUAGE_SLIDE)) return;
        int switches = mPrefs.getInt(KEY_LANGUAGE_SWITCHES, 0) + 1;
        mPrefs.edit().putInt(KEY_LANGUAGE_SWITCHES, switches).apply();
        if (switches >= LANGUAGE_SWITCHES) maybeShow(Tip.LANGUAGE_SLIDE);
    }

    /** The clipboard panel opened; with clips there and none pinned, from the third time. */
    void onClipboardOpened(boolean hasClips, boolean anyPinned) {
        if (anyPinned) {
            learned(Tip.CLIP_PIN);
            return;
        }
        if (isLearned(Tip.CLIP_PIN)) return;
        int opens = mPrefs.getInt(KEY_CLIPBOARD_OPENS, 0) + 1;
        mPrefs.edit().putInt(KEY_CLIPBOARD_OPENS, opens).apply();
        if (opens >= CLIPBOARD_OPENS && hasClips) maybeShow(Tip.CLIP_PIN);
    }
}
