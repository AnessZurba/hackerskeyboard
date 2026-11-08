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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The input view: a toolbar row above the keyboard, with panels for text selection,
 * clipboard history and emoji that take the keyboard's place (at its height) while open.
 * Swiping up on the keyboard shows or hides the toolbar row.
 */
final class KeyboardToolbar extends LinearLayout implements ClipboardHistory.Listener {
    static final String PREF_SHOW_TOOLBAR = "pref_show_toolbar";
    private static final String PREF_EMOJI_RECENTS = "pref_emoji_recents";
    private static final int MAX_RECENTS = 40;
    private static final int PANEL_NONE = 0, PANEL_SELECT = 1, PANEL_CLIPBOARD = 2, PANEL_EMOJI = 3;

    private final LatinIME mIme;
    private final LinearLayout mBar;
    private final FrameLayout mBody;
    private final ImageView[] mButtons = new ImageView[3];
    private final List<View> mCells = new ArrayList<>();
    private final HorizontalScrollView mChipScroll;
    private final InlineContentClipView mChipClip; // holds mChipScroll
    private final LinearLayout mChipRow;
    private final LinearLayout mPinnedRow; // the password manager's own icon, fixed at the start
    private final ImageView mChipsDismiss;
    // True while the row is shown only because autofill chips are offered.
    private boolean mShownForChips;
    private LatinKeyboardView mKeyboardView;
    private View mPanel;
    private int mOpenPanel = PANEL_NONE;
    private final int mBarHeight;
    private boolean mToolbarShown;

    // Colours sampled from the keyboard's background.
    private int mBackground = Color.BLACK;
    private int mForeground = Color.WHITE;
    private int mAccent = 0xFF5E97F6;

    // Selection panel state
    private boolean mSelecting;
    private TextView mSelectButton;

    // Emoji data, loaded on first use
    private List<String> mGroupNames;
    private List<String[]> mGroups;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    KeyboardToolbar(LatinIME ime) {
        super(ime);
        mIme = ime;
        setOrientation(VERTICAL);
        mBarHeight = dp(42);

        mBar = new LinearLayout(ime);
        mBar.setOrientation(HORIZONTAL);
        mBar.setGravity(Gravity.CENTER_VERTICAL);
        int[] icons = {R.drawable.ic_toolbar_select, R.drawable.ic_toolbar_clipboard,
                R.drawable.ic_toolbar_emoji};
        int[] descriptions = {R.string.toolbar_select, R.string.toolbar_clipboard,
                R.string.toolbar_emoji};
        final int[] panels = {PANEL_SELECT, PANEL_CLIPBOARD, PANEL_EMOJI};
        for (int i = 0; i < icons.length; i++) {
            ImageView b = new ImageView(ime);
            b.setImageResource(icons[i]);
            b.setContentDescription(ime.getString(descriptions[i]));
            b.setScaleType(ImageView.ScaleType.CENTER);
            final int panel = panels[i];
            b.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                togglePanel(panel);
            });
            mButtons[i] = b;
            // Samsung-style: each icon on its own circle, centred in an equal share of the row.
            FrameLayout cell = new FrameLayout(ime);
            cell.addView(b, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
            mBar.addView(cell, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
            mCells.add(cell);
        }
        // Autofill chips (password managers) take the buttons' place while offered.
        // Pinned chips (the password manager's icon) at the start, as on Samsung; they keep
        // the size they come with (see addChips), so they can't crowd out the rest.
        mPinnedRow = new LinearLayout(ime);
        mPinnedRow.setOrientation(HORIZONTAL);
        mPinnedRow.setGravity(Gravity.CENTER_VERTICAL);
        mPinnedRow.setPadding(dp(4), 0, 0, 0);
        mPinnedRow.setVisibility(GONE);
        mBar.addView(mPinnedRow, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        mChipScroll = new HorizontalScrollView(ime);
        mChipScroll.setHorizontalScrollBarEnabled(false);
        mChipScroll.setFillViewport(true); // so the chips can be centred when they fit
        mChipRow = new LinearLayout(ime);
        mChipRow.setOrientation(HORIZONTAL);
        mChipRow.setGravity(Gravity.CENTER); // centred, as on Samsung; scrolls when too many
        mChipRow.setPadding(dp(4), 0, dp(4), 0);
        mChipScroll.addView(mChipRow, new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        mChipClip = new InlineContentClipView(ime);
        mChipClip.addView(mChipScroll, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mChipClip.setVisibility(GONE);
        mBar.addView(mChipClip, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        // ">" puts the regular toolbar back in the chips' place.
        mChipsDismiss = new ImageView(ime);
        mChipsDismiss.setImageResource(R.drawable.ic_toolbar_chips_dismiss);
        mChipsDismiss.setScaleType(ImageView.ScaleType.CENTER);
        mChipsDismiss.setContentDescription(ime.getString(R.string.toolbar_show_buttons));
        mChipsDismiss.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            dismissChips();
        });
        mChipsDismiss.setVisibility(GONE);
        mBar.addView(mChipsDismiss, new LayoutParams(dp(44), LayoutParams.MATCH_PARENT));
        addView(mBar, new LayoutParams(LayoutParams.MATCH_PARENT, mBarHeight));

        mBody = new FrameLayout(ime);
        addView(mBody, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ime);
        mToolbarShown = prefs.getBoolean(PREF_SHOW_TOOLBAR, true);
        // The row always takes its space, so the input window never changes height (which
        // made the keys jump). Hidden, it is invisible and LatinIME.onComputeInsets gives
        // its space and touches to the app.
        mBar.setVisibility(mToolbarShown ? VISIBLE : INVISIBLE);
        mBarSpaceGiven = !mToolbarShown;
        ClipboardHistory.get(ime).setListener(this);
    }

    /** Puts a (new) keyboard view below the toolbar, e.g. after a theme change. */
    void setKeyboardView(LatinKeyboardView view) {
        closePanel();
        if (mKeyboardView != null && mKeyboardView.getParent() == mBody) {
            mBody.removeView(mKeyboardView);
        }
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        mKeyboardView = view;
        mBody.addView(view, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        applyColours(view.getBackground());
    }

    // ------------------------------------------------------------------ toolbar row

    boolean isToolbarShown() {
        return mToolbarShown;
    }

    /**
     * Shows or hides the toolbar row; remembered across restarts. The row's space opens
     * or closes in one step and only the row itself fades and slides, because resizing
     * the input window on every animation frame makes the keys jitter.
     */
    void toggleToolbar() {
        mToolbarShown = !mToolbarShown;
        PreferenceManager.getDefaultSharedPreferences(mIme).edit()
                .putBoolean(PREF_SHOW_TOOLBAR, mToolbarShown).apply();
        if (!mToolbarShown) closePanel();
        boolean wasShown = mBar.getVisibility() == VISIBLE && mBar.getAlpha() > 0;
        // Hidden while chips are offered: keep them, and hide the row when they go.
        mShownForChips = !mToolbarShown && hasChips();
        updateChipsButton();
        if (mToolbarShown && !wasShown) {
            revealBar();
        } else if (!mToolbarShown && !hasChips()) {
            concealBar();
        }
    }

    private void revealBar() {
        mBar.animate().cancel();
        setBarSpaceGiven(false); // take the space back first, then fade in
        mBar.setVisibility(VISIBLE);
        mBar.setAlpha(0f);
        mBar.setTranslationY(mBarHeight / 2f);
        mBar.animate().alpha(1f).translationY(0f).setDuration(180).withEndAction(null).start();
    }

    private void concealBar() {
        mBar.animate().cancel();
        mBar.animate().alpha(0f).translationY(mBarHeight / 2f).setDuration(150).withEndAction(() -> {
            mBar.setVisibility(INVISIBLE);
            if (!hasChips() && !mSearching) {
                for (View cell : mCells) cell.setVisibility(VISIBLE); // ready for next time
            }
            mBar.setAlpha(1f);
            mBar.setTranslationY(0f);
            setBarSpaceGiven(true);
        }).start();
    }

    // ------------------------------------------------------------------ autofill chips

    /** Chip height, for LatinIME's inline-suggestions request. */
    int chipHeight() {
        return mBarHeight - dp(6);
    }

    /** The keyboard's background colour, as sampled for the toolbar. */
    int backgroundColour() {
        return mBackground;
    }

    boolean hasChips() {
        return mChipRow.getChildCount() > 0 || mPinnedRow.getChildCount() > 0;
    }

    void showChips(List<View> chips) {
        showChips(chips, java.util.Collections.<View>emptyList());
    }

    /**
     * Shows a password manager's inline suggestion chips in place of the buttons (or puts
     * the buttons back for empty lists): pinned ones (its icon) fixed at the start, the rest
     * centred (scrolling when they don't fit), and a ">" button that brings the regular toolbar back. A hidden toolbar
     * slides in for them and hides again when they are gone; the swipe-up setting is left
     * alone.
     */
    void showChips(List<View> chips, List<View> pinned) {
        mChipRow.removeAllViews();
        mPinnedRow.removeAllViews();
        addChips(mPinnedRow, pinned);
        addChips(mChipRow, chips);
        boolean any = hasChips();
        if (mSearching) return; // the search bar has the row; chips come back after it
        // Chips gone from a row shown only for them: it fades out empty, and the buttons
        // come back once it's hidden (not during the fade).
        boolean concealing = !any && mShownForChips;
        for (View cell : mCells) cell.setVisibility(any || concealing ? GONE : VISIBLE);
        mChipClip.setVisibility(any ? VISIBLE : GONE);
        mPinnedRow.setVisibility(mPinnedRow.getChildCount() > 0 ? VISIBLE : GONE);
        mChipsDismiss.setVisibility(any ? VISIBLE : GONE);
        updateChipsButton();
        mChipScroll.scrollTo(0, 0);
        if (any && !mToolbarShown && !mShownForChips) {
            mShownForChips = true;
            revealBar();
        } else if (!any && mShownForChips) {
            mShownForChips = false;
            concealBar();
        }
    }

    /**
     * ">" brings the toolbar's buttons back; with the toolbar kept hidden there are none to
     * bring back, so it's "v": hide the strip.
     */
    private void updateChipsButton() {
        mChipsDismiss.setImageResource(mToolbarShown ? R.drawable.ic_toolbar_chips_dismiss
                : R.drawable.ic_toolbar_chips_hide);
        mChipsDismiss.setContentDescription(mIme.getString(mToolbarShown
                ? R.string.toolbar_show_buttons : R.string.toolbar_hide_chips));
    }

    private void addChips(LinearLayout row, List<View> chips) {
        for (View chip : chips) {
            if (chip.getParent() instanceof ViewGroup) ((ViewGroup) chip.getParent()).removeView(chip);
            // Keep the size the chip came with: an inline suggestion's view is sized by the
            // password manager's renderer, and can't measure itself. Re-wrapped, it stretched
            // across the row (crowding out ">") or, in the scrolling row, shrank to nothing.
            ViewGroup.LayoutParams own = chip.getLayoutParams();
            LinearLayout.LayoutParams lp = own != null
                    ? new LinearLayout.LayoutParams(own.width, own.height)
                    : new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(3), 0, dp(3), 0);
            lp.gravity = Gravity.CENTER_VERTICAL;
            row.addView(chip, lp);
        }
    }

    /** ">" on the chips: the regular toolbar (or, if the user keeps it hidden, nothing). */
    private void dismissChips() {
        showChips(java.util.Collections.<View>emptyList());
    }

    // Whether the hidden row's space is given to the app (see LatinIME.onComputeInsets).
    private boolean mBarSpaceGiven;

    private void setBarSpaceGiven(boolean given) {
        mBarSpaceGiven = given;
        requestLayout(); // recomputes the input window's insets
    }

    /** Height at the top of this view that the app may use (and touch) right now. */
    int spaceGivenToApp() {
        return mBarSpaceGiven ? mBar.getHeight() : 0;
    }

    // ------------------------------------------------------------------ panels

    /** Back to the keyboard; called when input starts or finishes. */
    void closePanel() {
        endEmojiSearch(false);
        if (mOpenPanel == PANEL_NONE) return;
        mOpenPanel = PANEL_NONE;
        stopRepeat();
        if (mPanel != null) mBody.removeView(mPanel);
        mPanel = null;
        mSelecting = false;
        if (mKeyboardView != null) mKeyboardView.setVisibility(VISIBLE);
        updateButtons();
    }

    private void togglePanel(int panel) {
        if (mOpenPanel == panel) {
            closePanel();
            return;
        }
        int height = mKeyboardView != null && mKeyboardView.getHeight() > 0
                ? mKeyboardView.getHeight() : dp(220);
        closePanel();
        View content;
        switch (panel) {
            case PANEL_SELECT: content = buildSelectPanel(); break;
            case PANEL_CLIPBOARD: content = buildClipboardPanel(); break;
            default: content = buildEmojiPanel(); break;
        }
        mPanel = content;
        mOpenPanel = panel;
        mBody.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, height));
        if (mKeyboardView != null) mKeyboardView.setVisibility(INVISIBLE); // keeps its height
        updateButtons();
    }

    private void updateButtons() {
        for (int i = 0; i < mButtons.length; i++) {
            boolean active = mOpenPanel == i + 1;
            mButtons[i].setImageTintList(ColorStateList.valueOf(active ? mBackground : mForeground));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(active ? mAccent : blend(mBackground, mForeground, 0.10f));
            GradientDrawable pressed = new GradientDrawable();
            pressed.setShape(GradientDrawable.OVAL);
            pressed.setColor(blend(mBackground, mForeground, 0.25f));
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[] {android.R.attr.state_pressed}, pressed);
            states.addState(new int[0], circle);
            mButtons[i].setBackground(states);
        }
    }

    /** The running keyboard service (a restarted service replaces the one we were made by). */
    private LatinIME ime() {
        LatinIME current = LatinIME.sInstance;
        return current != null ? current : mIme;
    }

    /**
     * The connection to the app's text field, or null with a short notice: if the panels
     * ever do nothing in an app, this tells whether the connection is missing.
     */
    private InputConnection connection() {
        InputConnection ic = ime().getCurrentInputConnection();
        if (ic == null) {
            android.widget.Toast.makeText(getContext(), R.string.toolbar_no_connection,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
        return ic;
    }

    // ------------------------------------------------------------------ selection panel

    private View buildSelectPanel() {
        LinearLayout grid = column();
        grid.setPadding(dp(4), dp(4), dp(4), dp(4));
        // label, action; null action = spacer
        // Gboard/Samsung-style editing pad plus a fifth column: select the word or line at
        // the cursor, swap which end of the selection moves, and drop the selection.
        Object[][] rows = {
                {"Home", act(KeyEvent.KEYCODE_MOVE_HOME), "▲", act(KeyEvent.KEYCODE_DPAD_UP),
                        "End", act(KeyEvent.KEYCODE_MOVE_END), mIme.getString(R.string.toolbar_select_all), ctx(android.R.id.selectAll),
                        "⌫", (Runnable) () -> ime().onKey(Keyboard.KEYCODE_DELETE, null, 0, 0)},
                {"◀", act(KeyEvent.KEYCODE_DPAD_LEFT), mIme.getString(R.string.toolbar_select), (Runnable) this::toggleSelecting,
                        "▶", act(KeyEvent.KEYCODE_DPAD_RIGHT), mIme.getString(R.string.toolbar_copy), ctx(android.R.id.copy),
                        "⇄", (Runnable) this::swapEnds},
                {"◀◀", word(KeyEvent.KEYCODE_DPAD_LEFT), "▼", act(KeyEvent.KEYCODE_DPAD_DOWN),
                        "▶▶", word(KeyEvent.KEYCODE_DPAD_RIGHT), mIme.getString(R.string.toolbar_cut), ctx(android.R.id.cut),
                        mIme.getString(R.string.toolbar_line), (Runnable) () -> selectAt(true)},
                {mIme.getString(R.string.toolbar_undo), ctrl(KeyEvent.KEYCODE_Z, false),
                        mIme.getString(R.string.toolbar_redo), ctrl(KeyEvent.KEYCODE_Z, true),
                        mIme.getString(R.string.toolbar_unselect), (Runnable) this::unselect,
                        mIme.getString(R.string.toolbar_paste), ctx(android.R.id.paste),
                        mIme.getString(R.string.toolbar_word), (Runnable) () -> selectAt(false)},
        };
        for (Object[] row : rows) {
            LinearLayout line = row();
            for (int i = 0; i < row.length; i += 2) {
                String label = (String) row[i];
                Runnable action = (Runnable) row[i + 1];
                TextView cell = cell(label, 15);
                boolean repeats = label.startsWith("◀") || label.startsWith("▶")
                        || label.equals("▲") || label.equals("▼") || label.equals("⌫");
                if (label.equals("⌫")) {
                    setBackspace(cell);
                } else if (repeats) {
                    setRepeating(cell, action);
                } else if (label.equals(mIme.getString(R.string.toolbar_select))) {
                    setTrackpad(cell);
                } else {
                    cell.setOnClickListener(v -> {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        action.run();
                    });
                }
                if (label.equals(mIme.getString(R.string.toolbar_select))) mSelectButton = cell;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                line.addView(cell, lp);
            }
            grid.addView(line, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        }
        updateSelectButton();
        return grid;
    }

    private void toggleSelecting() {
        mSelecting = !mSelecting;
        if (!mSelecting) ime().forgetSelection();
        updateSelectButton();
    }

    /**
     * The Select button: a tap toggles Select; holding it (or dragging off it) turns it into
     * a trackpad, like the space bar's, that moves the cursor, or the selection's moving end
     * while Select is on.
     */
    private void setTrackpad(final TextView cell) {
        setTrackpad(cell, true, () -> {
            cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            toggleSelecting();
        });
    }

    /**
     * A key that is also a trackpad when held or dragged (the Select button; the emoji
     * panel's space bar). {@code selectButton}: moves the selection's end while Select is on.
     */
    private void setTrackpad(final TextView cell, final boolean selectButton, final Runnable tap) {
        final CursorTrackpad trackpad = new CursorTrackpad(getResources().getDisplayMetrics().density);
        final int slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
        final boolean[] active = new boolean[1];
        final float[] down = new float[2];
        final boolean[] dragging = new boolean[1];
        final Runnable start = () -> {
            active[0] = true;
            dragging[0] = ime().getCurrentInputConnection() != null
                    && ime().beginCursorDrag(selectButton && mSelecting);
            cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            cell.setAlpha(0.6f);
        };
        final CursorTrackpad.Mover mover = new CursorTrackpad.Mover() {
            @Override
            public boolean chars(int n) {
                if (dragging[0]) return ime().mCursorDrag.chars(n);
                if (connection() == null) return false;
                int code = n < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
                for (int i = Math.abs(n); i > 0; i--) {
                    sendKey(code, selectButton && mSelecting ? KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON : 0);
                }
                return true;
            }

            @Override
            public boolean lines(int n) {
                return dragging[0] && ime().mCursorDrag.lines(n);
            }
        };
        cell.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    active[0] = false;
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                    trackpad.start(e.getRawX(), e.getRawY(), e.getEventTime());
                    mHandler.postDelayed(start, android.view.ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!active[0] && Math.hypot(e.getRawX() - down[0], e.getRawY() - down[1]) > slop) {
                        mHandler.removeCallbacks(start);
                        start.run();
                        trackpad.start(e.getRawX(), e.getRawY(), e.getEventTime());
                    }
                    if (active[0] && trackpad.move(e.getRawX(), e.getRawY(), e.getEventTime(), mover)) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mHandler.removeCallbacks(start);
                    view.setPressed(false);
                    cell.setAlpha(1f);
                    if (dragging[0]) ime().mCursorDrag.end();
                    dragging[0] = false;
                    if (!active[0] && e.getActionMasked() == MotionEvent.ACTION_UP) tap.run();
                    active[0] = false;
                    return true;
                default:
                    return true;
            }
        });
    }

    /** Selects the word/line at the cursor and turns Select on, so arrows extend it. */
    private void selectAt(boolean line) {
        if (connection() == null) return;
        if (ime().selectAtCursor(line)) {
            mSelecting = true;
            updateSelectButton();
        }
    }

    /** Arrows (and word jumps, Home/End) now move the selection's other end. */
    private void swapEnds() {
        if (connection() == null) return;
        if (ime().swapSelectionEnds()) {
            mSelecting = true;
            updateSelectButton();
        } else {
            android.widget.Toast.makeText(getContext(), R.string.toolbar_nothing_selected,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void unselect() {
        if (connection() == null) return;
        ime().collapseSelection();
        mSelecting = false;
        updateSelectButton();
    }

    private void updateSelectButton() {
        if (mSelectButton == null) return;
        mSelectButton.setBackground(cellBackground(mSelecting));
        mSelectButton.setTextColor(mSelecting ? mBackground : mForeground);
    }

    /** A cursor key; extends the selection while Select is on. */
    private Runnable act(final int keyCode) {
        return () -> {
            if (connection() == null) return;
            int steps = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1
                    : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            // Left/right move within the text (an arrow key at its ends would move focus
            // away and close the keyboard); arrow keys only where the text isn't exposed.
            if (steps != 0 && ime().moveCursor(steps, mSelecting, false)) return;
            boolean vertical = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
            boolean up = keyCode == KeyEvent.KEYCODE_DPAD_UP;
            // Up/down in multi-line fields: one step of a drag, which keeps the selection in
            // editors that drop it on Shift+arrow and doesn't jump to the ends of the text.
            if (vertical && ime().beginQuietCursorDrag(mSelecting)) {
                boolean moved = ime().mCursorDrag.lines(up ? -1 : 1);
                ime().mCursorDrag.end();
                if (moved) return;
            }
            // Single-line fields, and the first/last line; see LatinIME.moveCursorVertically.
            if (vertical && ime().moveCursorVertically(up, mSelecting)) {
                return;
            }
            sendKey(keyCode, mSelecting ? KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON : 0);
        };
    }

    /** A word jump (Ctrl+arrow); extends the selection while Select is on. */
    private Runnable word(final int keyCode) {
        return () -> {
            int steps = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
            if (ime().moveCursor(steps, mSelecting, true)) return;
            sendKey(keyCode, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON
                    | (mSelecting ? KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON : 0));
        };
    }

    private Runnable ctrl(final int keyCode, final boolean shift) {
        return () -> sendKey(keyCode, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON
                | (shift ? KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON : 0));
    }

    private Runnable ctx(final int id) {
        return () -> {
            InputConnection ic = connection();
            if (ic != null) ic.performContextMenuAction(id);
            if (id == android.R.id.copy || id == android.R.id.cut || id == android.R.id.paste) {
                mSelecting = false;
                updateSelectButton();
            }
        };
    }

    private void sendKey(int keyCode, int meta) {
        InputConnection ic = connection();
        if (ic == null) return;
        CursorDrag.sendKey(ic, keyCode, meta); // Shift/Ctrl pressed around the key, as on a PC
    }

    // Press-and-hold repeat for arrows and backspace
    private Runnable mRepeat;

    private void setRepeating(View v, final Runnable action) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    action.run();
                    stopRepeat();
                    mRepeat = new Runnable() {
                        @Override
                        public void run() {
                            action.run();
                            mHandler.postDelayed(this, 50);
                        }
                    };
                    mHandler.postDelayed(mRepeat, 400);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    stopRepeat();
                    return true;
                default:
                    return true;
            }
        });
    }

    /**
     * A panel's backspace, like the keyboard's: hold to repeat; slide left to select words
     * back from the cursor (faster slides go further), slide back for fewer, release to
     * delete, back over the key to cancel. Where the swipe is available a tap deletes on
     * release; elsewhere (terminals, or swipe turned off) on touch.
     */
    private void setBackspace(final View v) {
        final Runnable delete = () -> ime().onKey(Keyboard.KEYCODE_DELETE, null, 0, 0);
        final DeleteSwipeAccel accel = new DeleteSwipeAccel(getResources().getDisplayMetrics().density);
        final int slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
        final float[] downX = new float[1];
        // [0] deferred tap pending, [1] repeating, [2] swiping
        final boolean[] state = new boolean[3];
        final Runnable repeat = new Runnable() {
            @Override
            public void run() {
                state[0] = false;
                state[1] = true;
                delete.run();
                mHandler.postDelayed(this, 50);
            }
        };
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    downX[0] = e.getRawX();
                    state[1] = state[2] = false;
                    state[0] = LatinIME.deleteSwipeAvailable();
                    if (!state[0]) delete.run();
                    stopRepeat();
                    mRepeat = repeat;
                    mHandler.postDelayed(repeat, 400);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    // Heading left: a swipe is coming, so don't start repeating meanwhile.
                    if (!state[1] && !state[2] && downX[0] - e.getRawX() > slop) stopRepeat();
                    // The swipe may start after a hold too (repeat running), as on the keyboard.
                    if (!state[2] && LatinIME.deleteSwipeAvailable()
                            && downX[0] - e.getRawX() > accel.step() && ime().startDeleteSwipe()) {
                        state[2] = true;
                        state[0] = false;
                        stopRepeat();
                        accel.start(downX[0], e.getRawX(), e.getEventTime());
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    }
                    if (state[2]) ime().setDeleteSwipeWords(accel.words(e.getRawX(), e.getEventTime()));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                    view.setPressed(false);
                    stopRepeat();
                    if (state[2]) {
                        ime().finishDeleteSwipe(up);
                    } else if (state[0] && up) {
                        delete.run(); // a tap
                    }
                    state[0] = state[1] = state[2] = false;
                    return true;
                default:
                    return true;
            }
        });
    }

    private void stopRepeat() {
        if (mRepeat != null) mHandler.removeCallbacks(mRepeat);
        mRepeat = null;
    }

    // ------------------------------------------------------------------ clipboard panel

    // Samsung Keyboard's clipboard layout: a header ("< Clipboard", pin, delete) over a
    // three-column grid of cards; long press (or pin/delete) starts selecting cards, with
    // "All", a count and pin/delete for the selection in the header.
    private boolean mClipSelecting;
    private final java.util.Set<ClipboardHistory.Clip> mClipSelected = new java.util.HashSet<>();
    private LinearLayout mClipHeader;
    private ClipAdapter mClipAdapter;
    private TextView mClipMessage;

    private View buildClipboardPanel() {
        mClipSelecting = false;
        mClipSelected.clear();
        LinearLayout panel = column();
        mClipHeader = row();
        mClipHeader.setGravity(Gravity.CENTER_VERTICAL);
        mClipHeader.setPadding(dp(4), 0, dp(4), 0);
        panel.addView(mClipHeader, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));

        FrameLayout body = new FrameLayout(getContext());
        GridView grid = new GridView(getContext());
        grid.setNumColumns(3);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(6));
        grid.setVerticalSpacing(dp(6));
        grid.setPadding(dp(6), 0, dp(6), dp(6));
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        mClipAdapter = new ClipAdapter();
        grid.setAdapter(mClipAdapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            ClipboardHistory.Clip clip = mClipAdapter.getItem(position);
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (mClipSelecting) {
                if (!mClipSelected.remove(clip)) mClipSelected.add(clip);
                refreshClipboard();
            } else if (clip.isImage()) {
                if (ime().appConnection() == null) {
                    connection(); // explains
                } else if (!ime().commitImage(ClipboardHistory.get(getContext()).imageFile(clip), clip.mime)) {
                    ime().imageNotAccepted();
                }
            } else if (connection() != null) {
                ime().onText(clip.text);
            }
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            mClipSelecting = true;
            mClipSelected.add(mClipAdapter.getItem(position));
            refreshClipboard();
            return true;
        });
        body.addView(grid);
        mClipMessage = message(R.string.toolbar_clipboard_empty);
        body.addView(mClipMessage);
        panel.addView(body, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        refreshClipboard();
        return panel;
    }

    private void refreshClipboard() {
        if (mClipAdapter == null) return;
        ClipboardHistory history = ClipboardHistory.get(getContext());
        List<ClipboardHistory.Clip> clips = new ArrayList<>();
        if (history.isEnabled()) {
            for (ClipboardHistory.Clip c : history.clips()) if (c.pinned) clips.add(c); // pinned first
            for (ClipboardHistory.Clip c : history.clips()) if (!c.pinned) clips.add(c);
        }
        mClipSelected.retainAll(clips);
        mClipAdapter.setClips(clips);
        mClipMessage.setText(history.isEnabled() ? R.string.toolbar_clipboard_empty
                : R.string.toolbar_clipboard_off);
        mClipMessage.setVisibility(clips.isEmpty() ? VISIBLE : GONE);
        buildClipHeader(clips);
    }

    private void buildClipHeader(final List<ClipboardHistory.Clip> clips) {
        mClipHeader.removeAllViews();
        final ClipboardHistory history = ClipboardHistory.get(getContext());
        if (mClipSelecting) {
            // Back leaves selection mode (the toolbar's clipboard button closes the panel).
            mClipHeader.addView(headerIcon(R.drawable.ic_clip_back, v -> {
                mClipSelecting = false;
                mClipSelected.clear();
                refreshClipboard();
            }));
            final boolean all = !clips.isEmpty() && mClipSelected.size() == clips.size();
            LinearLayout allToggle = row();
            allToggle.setGravity(Gravity.CENTER_VERTICAL);
            ImageView box = new ImageView(getContext());
            box.setImageResource(all ? R.drawable.ic_clip_checked : R.drawable.ic_clip_unchecked);
            box.setImageTintList(ColorStateList.valueOf(all ? mAccent : mForeground));
            allToggle.addView(box, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView allLabel = headerText(mIme.getString(R.string.toolbar_clipboard_all));
            allLabel.setPadding(dp(6), 0, dp(6), 0);
            allToggle.addView(allLabel);
            allToggle.setPadding(dp(6), 0, dp(6), 0);
            allToggle.setOnClickListener(v -> {
                if (all) mClipSelected.clear(); else mClipSelected.addAll(clips);
                refreshClipboard();
            });
            mClipHeader.addView(allToggle, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
            mClipHeader.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 1, 1));
            mClipHeader.addView(headerText(Integer.toString(mClipSelected.size())));
            mClipHeader.addView(headerIcon(R.drawable.ic_clip_pin, v -> {
                if (mClipSelected.isEmpty()) return;
                boolean allPinned = true;
                for (ClipboardHistory.Clip c : mClipSelected) allPinned &= c.pinned;
                history.setPinned(new ArrayList<>(mClipSelected), !allPinned);
                mClipSelecting = false;
                mClipSelected.clear();
                refreshClipboard();
            }));
            mClipHeader.addView(headerIcon(R.drawable.ic_clip_delete, v -> {
                if (mClipSelected.isEmpty()) return;
                history.removeAll(new ArrayList<>(mClipSelected));
                mClipSelecting = false;
                mClipSelected.clear();
                refreshClipboard();
            }));
        } else {
            TextView title = headerText(mIme.getString(R.string.toolbar_clipboard));
            title.setPadding(dp(12), 0, dp(6), 0);
            mClipHeader.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            View.OnClickListener select = v -> {
                if (clips.isEmpty()) return;
                mClipSelecting = true;
                refreshClipboard();
            };
            mClipHeader.addView(headerIcon(R.drawable.ic_clip_pin, select));
            mClipHeader.addView(headerIcon(R.drawable.ic_clip_delete, select));
        }
    }

    private ImageView headerIcon(int icon, View.OnClickListener onClick) {
        ImageView v = new ImageView(getContext());
        v.setImageResource(icon);
        v.setImageTintList(ColorStateList.valueOf(mForeground));
        v.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.OVAL);
        pressed.setColor(blend(mBackground, mForeground, 0.2f));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        v.setBackground(states);
        v.setClickable(true);
        v.setOnClickListener(onClick);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return v;
    }

    private TextView headerText(String text) {
        TextView v = new TextView(getContext());
        v.setText(text);
        v.setTextColor(mForeground);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        v.setPadding(dp(8), 0, dp(8), 0);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private final class ClipAdapter extends BaseAdapter {
        private List<ClipboardHistory.Clip> mClips = new ArrayList<>();

        void setClips(List<ClipboardHistory.Clip> clips) {
            mClips = clips;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return mClips.size(); }
        @Override public ClipboardHistory.Clip getItem(int position) { return mClips.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FrameLayout card = convertView instanceof FrameLayout ? (FrameLayout) convertView : null;
            TextView text;
            ImageView pin, check;
            if (card == null) {
                card = new FrameLayout(getContext());
                card.setLayoutParams(new GridView.LayoutParams(GridView.LayoutParams.MATCH_PARENT, dp(88)));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(12));
                bg.setColor(blend(mBackground, mForeground, 0.10f));
                card.setBackground(bg);
                text = new TextView(getContext());
                text.setTextColor(mForeground);
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                text.setMaxLines(4);
                text.setEllipsize(TextUtils.TruncateAt.END);
                text.setPadding(dp(10), dp(8), dp(10), dp(8));
                card.addView(text, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                pin = new ImageView(getContext());
                pin.setImageResource(R.drawable.ic_clip_pinned);
                pin.setImageTintList(ColorStateList.valueOf(faded(mForeground)));
                card.addView(pin, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP | Gravity.END));
                ImageView picture = new ImageView(getContext());
                picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
                picture.setClipToOutline(true);
                picture.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
                    }
                });
                card.addView(picture, 0, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                check = new ImageView(getContext());
                card.addView(check, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.START));
                card.setTag(new View[] {text, pin, check, picture});
            }
            View[] parts = (View[]) card.getTag();
            text = (TextView) parts[0];
            pin = (ImageView) parts[1];
            check = (ImageView) parts[2];
            ImageView picture = (ImageView) parts[3];
            ClipboardHistory.Clip clip = mClips.get(position);
            text.setText(clip.text.trim());
            text.setVisibility(clip.isImage() ? GONE : VISIBLE);
            picture.setVisibility(clip.isImage() ? VISIBLE : GONE);
            picture.setImageBitmap(clip.isImage() ? thumbnail(clip) : null);
            pin.setVisibility(clip.pinned ? VISIBLE : GONE);
            ((FrameLayout.LayoutParams) pin.getLayoutParams()).setMargins(0, dp(6), dp(6), 0);
            if (mClipSelecting) {
                boolean selected = mClipSelected.contains(clip);
                check.setVisibility(VISIBLE);
                check.setImageResource(selected ? R.drawable.ic_clip_checked : R.drawable.ic_clip_unchecked);
                check.setImageTintList(ColorStateList.valueOf(selected ? mAccent : mForeground));
                ((FrameLayout.LayoutParams) check.getLayoutParams()).setMargins(dp(4), dp(4), 0, 0);
                text.setPadding(dp(10), dp(26), dp(10), dp(8));
            } else {
                check.setVisibility(GONE);
                text.setPadding(dp(10), dp(8), dp(10), dp(8));
            }
            return card;
        }
    }

    private final android.util.LruCache<String, Bitmap> mThumbnails =
            new android.util.LruCache<String, Bitmap>(6 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    /** A small copy of an image clip, for its card. */
    private Bitmap thumbnail(ClipboardHistory.Clip clip) {
        Bitmap cached = mThumbnails.get(clip.image);
        if (cached != null) return cached;
        java.io.File file = ClipboardHistory.get(getContext()).imageFile(clip);
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(file.getPath(), o);
        int target = dp(120);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= target && o.outHeight / (o.inSampleSize * 2) >= target) {
            o.inSampleSize *= 2;
        }
        o.inJustDecodeBounds = false;
        Bitmap bmp = android.graphics.BitmapFactory.decodeFile(file.getPath(), o);
        if (bmp != null) mThumbnails.put(clip.image, bmp);
        return bmp;
    }

    @Override
    public void onClipboardHistoryChanged() {
        if (mOpenPanel == PANEL_CLIPBOARD && mPanel != null) refreshClipboard();
    }

    // ------------------------------------------------------------------ emoji panel

    /**
     * Samsung-style emoji panel: search and category tabs on top, every category in one
     * scrolling grid (recents first) with the tab following the scroll, and gallery, space
     * and backspace at the bottom.
     */
    private View buildEmojiPanel() {
        loadEmoji();
        LinearLayout panel = column();

        // Sections: recents (if any), then each group.
        final List<Object> items = new ArrayList<>(); // String = emoji, Integer = header of section
        final List<Integer> sectionStarts = new ArrayList<>();
        List<String> tabLabels = new ArrayList<>();
        String[] recent = recents();
        List<String[]> sections = new ArrayList<>();
        final List<String> sectionNames = new ArrayList<>();
        if (recent.length > 0) {
            sections.add(recent);
            sectionNames.add(mIme.getString(R.string.toolbar_emoji_recent));
            tabLabels.add("🕘");
        }
        for (int i = 0; i < mGroups.size(); i++) {
            sections.add(mGroups.get(i));
            sectionNames.add(mGroupNames.get(i));
            tabLabels.add(mGroups.get(i)[0]);
        }
        for (int i = 0; i < sections.size(); i++) {
            sectionStarts.add(items.size());
            items.add(i);
            items.addAll(Arrays.asList(sections.get(i)));
        }

        // Top: search, then one tab per section.
        final LinearLayout tabs = row();
        ImageView search = new ImageView(getContext());
        search.setImageResource(R.drawable.ic_emoji_search);
        search.setImageTintList(ColorStateList.valueOf(mForeground));
        search.setScaleType(ImageView.ScaleType.CENTER);
        search.setContentDescription(mIme.getString(R.string.toolbar_emoji_search));
        search.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            startEmojiSearch();
        });
        tabs.addView(search, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        panel.addView(tabs, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(40)));

        final androidx.recyclerview.widget.RecyclerView grid =
                new androidx.recyclerview.widget.RecyclerView(getContext());
        int columns = Math.max(6, (getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels) / dp(46));
        final androidx.recyclerview.widget.GridLayoutManager layout =
                new androidx.recyclerview.widget.GridLayoutManager(getContext(), columns);
        layout.setSpanSizeLookup(new androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return items.get(position) instanceof Integer ? layout.getSpanCount() : 1;
            }
        });
        grid.setLayoutManager(layout);
        grid.setItemAnimator(null);
        final EmojiGridAdapter adapter = new EmojiGridAdapter(items, sectionNames);
        grid.setAdapter(adapter);
        panel.addView(grid, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        final int[] current = {-1};
        final Runnable followScroll = () -> {
            int first = layout.findFirstVisibleItemPosition();
            if (first < 0) return;
            int section = 0;
            for (int i = 0; i < sectionStarts.size(); i++) {
                if (sectionStarts.get(i) <= first) section = i;
            }
            if (section != current[0]) {
                current[0] = section;
                for (int i = 1; i < tabs.getChildCount(); i++) {
                    tabs.getChildAt(i).setBackground(i - 1 == section ? cellBackground(false) : null);
                }
            }
        };
        for (int i = 0; i < tabLabels.size(); i++) {
            final int section = i;
            TextView tab = new TextView(getContext());
            tab.setText(tabLabels.get(i));
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            tab.setGravity(Gravity.CENTER);
            tab.setContentDescription(sectionNames.get(i));
            tab.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                layout.scrollToPositionWithOffset(sectionStarts.get(section), 0);
                current[0] = -1;
                grid.post(followScroll);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
            lp.setMargins(dp(1), dp(3), dp(1), dp(3));
            tabs.addView(tab, lp);
        }
        grid.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(androidx.recyclerview.widget.RecyclerView rv, int dx, int dy) {
                followScroll.run();
            }
        });
        grid.post(followScroll);

        // Bottom: gallery, space, backspace (the toolbar's emoji button goes back to the keys).
        LinearLayout bottom = row();
        TextView space = cell(" ", 15);
        space.setContentDescription(mIme.getString(R.string.toolbar_emoji_space));
        // Space: tap types a space; hold or drag it to move the cursor, like the keyboard's.
        setTrackpad(space, false, () -> {
            space.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (connection() != null) ime().onText(" ");
        });
        TextView backspace = cell("⌫", 18);
        setBackspace(backspace);
        // Gallery: pick a photo to insert, in fields that take images (chat apps).
        ImageView gallery = new ImageView(getContext());
        gallery.setImageResource(R.drawable.ic_emoji_gallery);
        gallery.setImageTintList(ColorStateList.valueOf(mForeground));
        gallery.setScaleType(ImageView.ScaleType.CENTER);
        gallery.setBackground(cellBackground(false));
        gallery.setContentDescription(mIme.getString(R.string.toolbar_gallery));
        gallery.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (!ime().acceptsImage("image/png") && !ime().acceptsImage("image/jpeg")) {
                ime().imageNotAccepted();
                return;
            }
            Intent pick = new Intent(getContext(), ImagePickActivity.class);
            pick.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            getContext().startActivity(pick);
        });
        int[] widths = {64, 0, 72};
        View[] views = {gallery, space, backspace};
        for (int i = 0; i < views.length; i++) {
            LinearLayout.LayoutParams lp = widths[i] > 0
                    ? new LinearLayout.LayoutParams(dp(widths[i]), LayoutParams.MATCH_PARENT)
                    : new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            bottom.addView(views[i], lp);
        }
        panel.addView(bottom, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(46)));
        return panel;
    }

    private static final int TYPE_HEADER = 0, TYPE_EMOJI = 1;

    /** The emoji grid: section headers across the full width, emoji cells in between. */
    private final class EmojiGridAdapter
            extends androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder> {
        private final List<Object> mItems;
        private final List<String> mNames;

        EmojiGridAdapter(List<Object> items, List<String> names) {
            mItems = items;
            mNames = names;
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            return mItems.get(position) instanceof Integer ? TYPE_HEADER : TYPE_EMOJI;
        }

        @Override
        public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            TextView v;
            if (type == TYPE_HEADER) {
                v = new TextView(getContext());
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                v.setTextColor(faded(mForeground));
                v.setPadding(dp(12), dp(10), dp(12), dp(4));
                v.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            } else {
                v = newEmojiCell();
                v.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(
                        LayoutParams.MATCH_PARENT, dp(46)));
            }
            return new androidx.recyclerview.widget.RecyclerView.ViewHolder(v) { };
        }

        @Override
        public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
            Object item = mItems.get(position);
            if (item instanceof Integer) {
                ((TextView) holder.itemView).setText(mNames.get((Integer) item));
            } else {
                bindEmojiCell((EmojiCell) holder.itemView, (String) item, this::notifyDataSetChanged);
            }
        }
    }

    /** An emoji cell: tap types it, a long press offers its skin tones. */
    private EmojiCell newEmojiCell() {
        EmojiCell v = new EmojiCell(getContext());
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        v.setGravity(Gravity.CENTER);
        v.setBackground(pressedBackground());
        return v;
    }

    private void bindEmojiCell(final EmojiCell v, final String emoji, final Runnable toneChanged) {
        v.setText(withTone(emoji));
        final String base = baseOf(emoji);
        v.mHasTones = mTones.containsKey(base);
        v.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            typeEmoji(withTone(emoji));
        });
        v.setOnLongClickListener(view -> {
            String[] tones = mTones.get(base);
            if (tones == null) return false;
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showTonePicker(view, base, tones, toneChanged);
            return true;
        });
    }

    private android.graphics.drawable.Drawable pressedBackground() {
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadius(dp(8));
        pressed.setColor(blend(mBackground, mForeground, 0.2f));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        return states;
    }

    // ------------------------------------------------------------------ emoji search

    private boolean mSearching;
    private LinearLayout mSearchRow;
    private TextView mSearchQuery;
    private LinearLayout mSearchResults;
    private HorizontalScrollView mSearchScroll;
    private android.text.SpannableStringBuilder mQuery;
    private List<String[]> mSearchWords; // {emoji, "name|keyword|..."}

    /**
     * Search, Samsung style: the toolbar row becomes a search bar with the matching emoji,
     * and the keyboard comes back below it to type the query (LatinIME sends the keys to
     * the query instead of the app meanwhile). Tapping a result types it into the app.
     */
    private void startEmojiSearch() {
        closePanel();
        loadSearchWords();
        if (mSearchRow == null) buildSearchRow();
        mQuery = new android.text.SpannableStringBuilder();
        android.text.Selection.setSelection(mQuery, 0);
        mSearching = true;
        for (View cell : mCells) cell.setVisibility(GONE);
        mChipClip.setVisibility(GONE);
        mPinnedRow.setVisibility(GONE);
        mChipsDismiss.setVisibility(GONE);
        mSearchRow.setVisibility(VISIBLE);
        ime().setSearchConnection(new SearchConnection());
        updateSearch();
    }

    /** Leaves the search; back to the emoji panel when {@code toPanel}. */
    private void endEmojiSearch(boolean toPanel) {
        if (!mSearching) return;
        mSearching = false;
        ime().setSearchConnection(null);
        mSearchRow.setVisibility(GONE);
        boolean chips = hasChips();
        for (View cell : mCells) cell.setVisibility(chips ? GONE : VISIBLE);
        mChipClip.setVisibility(chips ? VISIBLE : GONE);
        mPinnedRow.setVisibility(mPinnedRow.getChildCount() > 0 ? VISIBLE : GONE);
        mChipsDismiss.setVisibility(chips ? VISIBLE : GONE);
        if (toPanel) togglePanel(PANEL_EMOJI);
    }

    boolean isSearching() {
        return mSearching;
    }

    /** Back during the search returns to the emoji panel instead of closing the keyboard. */
    void endSearchFromBack() {
        endEmojiSearch(true);
    }

    private void buildSearchRow() {
        mSearchRow = row();
        mSearchRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = headerIcon(R.drawable.ic_clip_back, v -> endEmojiSearch(true));
        mSearchRow.addView(back, new LinearLayout.LayoutParams(dp(40), LayoutParams.MATCH_PARENT));
        mSearchQuery = new TextView(getContext());
        mSearchQuery.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        mSearchQuery.setSingleLine(true);
        mSearchQuery.setEllipsize(android.text.TextUtils.TruncateAt.START);
        mSearchQuery.setGravity(Gravity.CENTER_VERTICAL);
        mSearchQuery.setPadding(dp(12), 0, dp(12), 0);
        mSearchQuery.setCompoundDrawablePadding(dp(6));
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(18));
        pill.setColor(blend(mBackground, mForeground, 0.12f));
        mSearchQuery.setBackground(pill);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(dp(130), dp(34));
        qlp.setMargins(0, 0, dp(4), 0);
        mSearchRow.addView(mSearchQuery, qlp);
        mSearchScroll = new HorizontalScrollView(getContext());
        mSearchScroll.setHorizontalScrollBarEnabled(false);
        mSearchResults = row();
        mSearchScroll.addView(mSearchResults, new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        mSearchRow.addView(mSearchScroll, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        mSearchRow.setVisibility(GONE);
        mBar.addView(mSearchRow, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
    }

    private void updateSearch() {
        String q = mQuery.toString();
        mSearchQuery.setTextColor(q.isEmpty() ? faded(mForeground) : mForeground);
        mSearchQuery.setText(q.isEmpty() ? mIme.getString(R.string.toolbar_emoji_search) : q);
        mSearchResults.removeAllViews();
        for (String emoji : searchEmoji(q, 60)) {
            EmojiCell cell = newEmojiCell();
            cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            bindEmojiCell(cell, emoji, this::updateSearch);
            mSearchResults.addView(cell, new LinearLayout.LayoutParams(dp(42), LayoutParams.MATCH_PARENT));
        }
        mSearchScroll.scrollTo(0, 0);
    }

    /**
     * Emoji whose name or keywords (English, Arabic, Hebrew) match {@code query}: whole
     * words first, then words starting with it, then names containing it. Recents when empty.
     */
    List<String> searchEmoji(String query, int max) {
        loadSearchWords();
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (q.isEmpty()) {
            out.addAll(Arrays.asList(recents()));
            return out.size() > max ? out.subList(0, max) : out;
        }
        List<List<String>> ranks = new ArrayList<>();
        for (int i = 0; i < 4; i++) ranks.add(new ArrayList<>());
        for (String[] entry : mSearchWords) {
            int best = 4;
            String[] words = entry[1].split("\\|");
            for (int i = 0; i < words.length && best > 0; i++) {
                String w = words[i];
                if (w.equals(q)) best = i == 0 ? 0 : Math.min(best, 1);
                else if (w.startsWith(q) || w.contains(" " + q)) best = Math.min(best, 2);
                else if (i == 0 && w.contains(q)) best = Math.min(best, 3);
            }
            if (best < 4) ranks.get(best).add(entry[0]);
        }
        for (List<String> rank : ranks) {
            for (String e : rank) {
                if (out.size() >= max) return out;
                out.add(e);
            }
        }
        return out;
    }

    private void loadSearchWords() {
        if (mSearchWords != null) return;
        loadEmoji();
        mSearchWords = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                getContext().getAssets().open("emoji_search.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab > 0) mSearchWords.add(new String[] {line.substring(0, tab), line.substring(tab + 1)});
            }
        } catch (Exception e) {
            android.util.Log.w("HK/Toolbar", "Could not load emoji search words", e);
        }
    }

    /** Where the keys type during the search: the query. */
    private final class SearchConnection extends android.view.inputmethod.BaseInputConnection {
        SearchConnection() {
            super(KeyboardToolbar.this, true);
        }

        @Override
        public android.text.Editable getEditable() {
            return mQuery;
        }

        private boolean changed(boolean result) {
            if (mSearching) updateSearch();
            return result;
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            if (text != null && text.toString().contains("\n")) return pickFirst();
            return changed(super.commitText(text, newCursorPosition));
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            return changed(super.setComposingText(text, newCursorPosition));
        }

        @Override
        public boolean finishComposingText() {
            return changed(super.finishComposingText());
        }

        @Override
        public boolean deleteSurroundingText(int before, int after) {
            return changed(super.deleteSurroundingText(before, after));
        }

        @Override
        public boolean performEditorAction(int action) {
            return pickFirst();
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DEL:
                    int end = android.text.Selection.getSelectionEnd(mQuery);
                    if (end > 0) {
                        int start = Character.isLowSurrogate(mQuery.charAt(end - 1)) && end > 1 ? end - 2 : end - 1;
                        mQuery.delete(start, end);
                    }
                    return changed(true);
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    return pickFirst();
                case KeyEvent.KEYCODE_ESCAPE:
                case KeyEvent.KEYCODE_BACK:
                    endEmojiSearch(true);
                    return true;
                default:
                    int c = event.getUnicodeChar(event.getMetaState());
                    if (c > 0 && !Character.isISOControl(c)) {
                        mQuery.insert(android.text.Selection.getSelectionEnd(mQuery),
                                new String(Character.toChars(c)));
                        return changed(true);
                    }
                    return true;
            }
        }

        private boolean pickFirst() {
            if (mSearchResults.getChildCount() > 0) mSearchResults.getChildAt(0).performClick();
            return true;
        }
    }

    // ------------------------------------------------------------------ skin tones

    /** Emoji -> its five skin-tone variants; variant -> its emoji. */
    private final java.util.HashMap<String, String[]> mTones = new java.util.HashMap<>();
    private final java.util.HashMap<String, String> mToneBase = new java.util.HashMap<>();

    /** An emoji cell; emoji that come in skin tones get a small corner mark, as in Gboard. */
    private final class EmojiCell extends TextView {
        boolean mHasTones;
        private final android.graphics.Paint mMarkPaint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path mMark = new android.graphics.Path();

        EmojiCell(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!mHasTones) return;
            float size = dp(5), right = getWidth() - dp(6), bottom = getHeight() - dp(4);
            mMark.reset();
            mMark.moveTo(right, bottom - size);
            mMark.lineTo(right, bottom);
            mMark.lineTo(right - size, bottom);
            mMark.close();
            mMarkPaint.setColor(faded(mForeground));
            canvas.drawPath(mMark, mMarkPaint);
        }
    }

    private String baseOf(String emoji) {
        String base = mToneBase.get(emoji);
        return base != null ? base : emoji;
    }

    /** The emoji in the skin tone last chosen for it, or as is. */
    private String withTone(String emoji) {
        String base = baseOf(emoji);
        if (!mTones.containsKey(base)) return emoji;
        return privatePrefs().getString("emoji_tone_" + base, emoji);
    }

    private void typeEmoji(String emoji) {
        if (ime().appConnection() == null) {
            connection(); // explains
            return;
        }
        if (mSearching) ime().typeIntoApp(emoji); else ime().onText(emoji);
        addRecent(emoji);
    }

    private void showTonePicker(View anchor, final String base, String[] tones,
            final Runnable changed) {
        final android.widget.PopupWindow popup = new android.widget.PopupWindow(getContext());
        LinearLayout row = row();
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(blend(mBackground, mForeground, 0.15f));
        row.setBackground(bg);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        List<String> options = new ArrayList<>();
        options.add(base);
        options.addAll(Arrays.asList(tones));
        for (final String option : options) {
            TextView t = new TextView(getContext());
            t.setText(option);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            t.setGravity(Gravity.CENTER);
            t.setBackground(cellBackground(false));
            t.getBackground().setAlpha(option.equals(withTone(base)) ? 255 : 0);
            t.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                privatePrefs().edit().putString("emoji_tone_" + base, option).apply();
                typeEmoji(option);
                changed.run();
                popup.dismiss();
            });
            row.addView(t, new LinearLayout.LayoutParams(dp(48), dp(52)));
        }
        popup.setContentView(row);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(false);
        popup.setWidth(dp(48) * options.size() + dp(8));
        popup.setHeight(dp(60));
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        int width = popup.getWidth();
        int x = Math.max(0, Math.min(loc[0] + anchor.getWidth() / 2 - width / 2,
                getWidth() - width));
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, loc[1] - dp(62));
    }

    private void loadEmoji() {
        if (mGroups != null) return;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                getContext().getAssets().open("emoji_tones.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                String base = line.substring(0, tab);
                String[] variants = line.substring(tab + 1).split(" ");
                mTones.put(base, variants);
                for (String v : variants) mToneBase.put(v, base);
            }
        } catch (Exception e) {
            android.util.Log.w("HK/Toolbar", "Could not load emoji tones", e);
        }
        mGroupNames = new ArrayList<>();
        mGroups = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                getContext().getAssets().open("emoji.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                mGroupNames.add(line.substring(0, tab));
                mGroups.add(line.substring(tab + 1).split(" "));
            }
        } catch (Exception e) {
            android.util.Log.w("HK/Toolbar", "Could not load emoji", e);
        }
    }

    private String[] recents() {
        String saved = privatePrefs().getString(PREF_EMOJI_RECENTS, "");
        return saved.isEmpty() ? new String[0] : saved.split(" ");
    }

    private void addRecent(String emoji) {
        List<String> list = new ArrayList<>(Arrays.asList(recents()));
        list.remove(emoji);
        list.add(0, emoji);
        while (list.size() > MAX_RECENTS) list.remove(list.size() - 1);
        privatePrefs().edit().putString(PREF_EMOJI_RECENTS, TextUtils.join(" ", list)).apply();
    }

    /** Not the default preferences file, which the backup agent sends to the cloud. */
    private SharedPreferences privatePrefs() {
        SharedPreferences prefs = getContext().getSharedPreferences("toolbar_private", Context.MODE_PRIVATE);
        SharedPreferences old = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (old.contains(PREF_EMOJI_RECENTS)) { // moved out of the backed-up file
            prefs.edit().putString(PREF_EMOJI_RECENTS, old.getString(PREF_EMOJI_RECENTS, "")).apply();
            old.edit().remove(PREF_EMOJI_RECENTS).apply();
        }
        return prefs;
    }

    // ------------------------------------------------------------------ look

    /** Takes the toolbar's colours from the keyboard background, so every theme matches. */
    /** Takes the toolbar's colours from the keyboard background, so every theme matches. */
    private void applyColours(Drawable keyboardBackground) {
        int colour = Color.BLACK;
        if (keyboardBackground != null) {
            try {
                Bitmap bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
                Drawable d = keyboardBackground.getConstantState() != null
                        ? keyboardBackground.getConstantState().newDrawable().mutate() : keyboardBackground;
                d.setBounds(0, 0, 32, 32);
                d.draw(new Canvas(bmp));
                colour = bmp.getPixel(16, 16);
                bmp.recycle();
            } catch (RuntimeException ignored) {
                // keep black
            }
        }
        if (Color.alpha(colour) < 200) colour = 0xFF101010; // translucent themes
        mBackground = colour | 0xFF000000;
        boolean light = luminance(mBackground) > 0.5;
        mForeground = light ? 0xFF202124 : 0xFFE8EAED;
        mAccent = light ? 0xFF1A73E8 : 0xFF8AB4F8;
        // Backgrounds per part: when the toolbar row is hidden, its space must stay clear.
        mBar.setBackgroundColor(mBackground);
        mBody.setBackgroundColor(mBackground);
        mChipsDismiss.setImageTintList(ColorStateList.valueOf(faded(mForeground)));
        updateButtons();
    }

    private static double luminance(int c) {
        return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0;
    }

    private static int faded(int colour) {
        return (colour & 0x00FFFFFF) | 0xB3000000;
    }

    private static int blend(int a, int b, float t) {
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private Drawable cellBackground(boolean active) {
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(dp(8));
        normal.setColor(active ? mAccent : blend(mBackground, mForeground, 0.12f));
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadius(dp(8));
        pressed.setColor(blend(mBackground, mForeground, 0.3f));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], normal);
        return states;
    }

    private TextView cell(String label, int sp) {
        TextView v = new TextView(getContext());
        v.setText(label);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(mForeground);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setBackground(cellBackground(false));
        v.setClickable(true);
        return v;
    }

    private TextView message(int text) {
        TextView v = new TextView(getContext());
        v.setText(text);
        v.setTextColor(faded(mForeground));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(16), dp(32), dp(16), dp(16));
        return v;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(getContext());
        l.setOrientation(HORIZONTAL);
        return l;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(getContext());
        l.setOrientation(VERTICAL);
        return l;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
