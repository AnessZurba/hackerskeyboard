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
    private LatinKeyboardView mKeyboardView;
    private View mPanel;
    private int mOpenPanel = PANEL_NONE;
    private final int mBarHeight;
    private boolean mToolbarShown;
    private ValueAnimator mAnimator;

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
            mBar.addView(b, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        }
        addView(mBar, new LayoutParams(LayoutParams.MATCH_PARENT, mBarHeight));

        mBody = new FrameLayout(ime);
        addView(mBody, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ime);
        mToolbarShown = prefs.getBoolean(PREF_SHOW_TOOLBAR, true);
        mBar.getLayoutParams().height = mToolbarShown ? mBarHeight : 0;
        mBar.setVisibility(mToolbarShown ? VISIBLE : GONE);
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

    /** Shows or hides the toolbar row, animating its height; remembered across restarts. */
    void toggleToolbar() {
        mToolbarShown = !mToolbarShown;
        PreferenceManager.getDefaultSharedPreferences(mIme).edit()
                .putBoolean(PREF_SHOW_TOOLBAR, mToolbarShown).apply();
        if (!mToolbarShown) closePanel();
        if (mAnimator != null) mAnimator.cancel();
        final ViewGroup.LayoutParams lp = mBar.getLayoutParams();
        mBar.setVisibility(VISIBLE);
        mAnimator = ValueAnimator.ofInt(lp.height, mToolbarShown ? mBarHeight : 0);
        mAnimator.setDuration(160);
        mAnimator.addUpdateListener(a -> {
            lp.height = (Integer) a.getAnimatedValue();
            mBar.requestLayout();
            if (lp.height == 0 && !mToolbarShown) mBar.setVisibility(GONE);
        });
        mAnimator.start();
    }

    // ------------------------------------------------------------------ panels

    /** Back to the keyboard; called when input starts or finishes. */
    void closePanel() {
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
            mButtons[i].setImageTintList(ColorStateList.valueOf(active ? mAccent : faded(mForeground)));
        }
    }

    // ------------------------------------------------------------------ selection panel

    private View buildSelectPanel() {
        LinearLayout grid = column();
        grid.setPadding(dp(4), dp(4), dp(4), dp(4));
        // label, action; null action = spacer
        Object[][] rows = {
                {"Home", act(KeyEvent.KEYCODE_MOVE_HOME), "▲", act(KeyEvent.KEYCODE_DPAD_UP),
                        "End", act(KeyEvent.KEYCODE_MOVE_END), mIme.getString(R.string.toolbar_select_all), ctx(android.R.id.selectAll)},
                {"◀", act(KeyEvent.KEYCODE_DPAD_LEFT), mIme.getString(R.string.toolbar_select), (Runnable) this::toggleSelecting,
                        "▶", act(KeyEvent.KEYCODE_DPAD_RIGHT), mIme.getString(R.string.toolbar_copy), ctx(android.R.id.copy)},
                {"◀◀", word(KeyEvent.KEYCODE_DPAD_LEFT), "▼", act(KeyEvent.KEYCODE_DPAD_DOWN),
                        "▶▶", word(KeyEvent.KEYCODE_DPAD_RIGHT), mIme.getString(R.string.toolbar_cut), ctx(android.R.id.cut)},
                {mIme.getString(R.string.toolbar_undo), ctrl(KeyEvent.KEYCODE_Z, false),
                        mIme.getString(R.string.toolbar_redo), ctrl(KeyEvent.KEYCODE_Z, true),
                        "⌫", (Runnable) () -> mIme.onKey(Keyboard.KEYCODE_DELETE, null, 0, 0),
                        mIme.getString(R.string.toolbar_paste), ctx(android.R.id.paste)},
        };
        for (Object[] row : rows) {
            LinearLayout line = row();
            for (int i = 0; i < row.length; i += 2) {
                String label = (String) row[i];
                Runnable action = (Runnable) row[i + 1];
                TextView cell = cell(label, 15);
                boolean repeats = label.startsWith("◀") || label.startsWith("▶")
                        || label.equals("▲") || label.equals("▼") || label.equals("⌫");
                if (repeats) {
                    setRepeating(cell, action);
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
        updateSelectButton();
    }

    private void updateSelectButton() {
        if (mSelectButton == null) return;
        mSelectButton.setBackground(cellBackground(mSelecting));
        mSelectButton.setTextColor(mSelecting ? mBackground : mForeground);
    }

    /** A cursor key; extends the selection while Select is on. */
    private Runnable act(final int keyCode) {
        return () -> sendKey(keyCode, mSelecting ? KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON : 0);
    }

    /** A word jump (Ctrl+arrow); extends the selection while Select is on. */
    private Runnable word(final int keyCode) {
        return () -> sendKey(keyCode, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON
                | (mSelecting ? KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON : 0));
    }

    private Runnable ctrl(final int keyCode, final boolean shift) {
        return () -> sendKey(keyCode, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON
                | (shift ? KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON : 0));
    }

    private Runnable ctx(final int id) {
        return () -> {
            InputConnection ic = mIme.getCurrentInputConnection();
            if (ic != null) ic.performContextMenuAction(id);
            if (id == android.R.id.copy || id == android.R.id.cut || id == android.R.id.paste) {
                mSelecting = false;
                updateSelectButton();
            }
        };
    }

    private void sendKey(int keyCode, int meta) {
        InputConnection ic = mIme.getCurrentInputConnection();
        if (ic == null) return;
        long now = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta,
                -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        ic.sendKeyEvent(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0,
                meta, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
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

    private void stopRepeat() {
        if (mRepeat != null) mHandler.removeCallbacks(mRepeat);
        mRepeat = null;
    }

    // ------------------------------------------------------------------ clipboard panel

    private View buildClipboardPanel() {
        ScrollView scroll = new ScrollView(getContext());
        LinearLayout list = column();
        list.setPadding(dp(6), dp(4), dp(6), dp(6));
        scroll.addView(list);
        fillClipboardList(list);
        scroll.setTag(list);
        return scroll;
    }

    private void fillClipboardList(LinearLayout list) {
        list.removeAllViews();
        final ClipboardHistory history = ClipboardHistory.get(getContext());
        if (!history.isEnabled()) {
            list.addView(message(R.string.toolbar_clipboard_off));
            return;
        }
        List<ClipboardHistory.Clip> clips = history.clips();
        if (clips.isEmpty()) {
            list.addView(message(R.string.toolbar_clipboard_empty));
            return;
        }
        for (final ClipboardHistory.Clip clip : new ArrayList<>(clips)) {
            TextView row = cell((clip.pinned ? "📌 " : "") + clip.text.trim(), 14);
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            row.setMaxLines(2);
            row.setEllipsize(TextUtils.TruncateAt.END);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                mIme.onText(clip.text);
            });
            row.setOnLongClickListener(v -> {
                history.togglePin(clip);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(3), 0, dp(3));
            list.addView(row, lp);
        }
        TextView clear = cell(mIme.getString(R.string.toolbar_clipboard_clear), 14);
        clear.setOnClickListener(v -> history.clearUnpinned());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(40));
        lp.setMargins(0, dp(8), 0, 0);
        list.addView(clear, lp);
    }

    @Override
    public void onClipboardHistoryChanged() {
        if (mOpenPanel == PANEL_CLIPBOARD && mPanel != null) {
            fillClipboardList((LinearLayout) mPanel.getTag());
        }
    }

    // ------------------------------------------------------------------ emoji panel

    private View buildEmojiPanel() {
        loadEmoji();
        LinearLayout panel = column();
        final GridView grid = new GridView(getContext());
        grid.setColumnWidth(dp(46));
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(2));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        final EmojiAdapter adapter = new EmojiAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            String emoji = adapter.getItem(position);
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            mIme.onText(emoji);
            addRecent(emoji);
        });
        panel.addView(grid, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        // Category tabs, recents first, and a backspace key.
        LinearLayout tabs = row();
        HorizontalScrollView tabScroll = new HorizontalScrollView(getContext());
        tabScroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout tabRow = row();
        tabScroll.addView(tabRow);
        final List<String[]> pages = new ArrayList<>();
        List<String> tabLabels = new ArrayList<>();
        pages.add(recents());
        tabLabels.add("🕘");
        for (int i = 0; i < mGroups.size(); i++) {
            pages.add(mGroups.get(i));
            tabLabels.add(mGroups.get(i)[0]);
        }
        final int initial = pages.get(0).length > 0 ? 0 : 1;
        for (int i = 0; i < pages.size(); i++) {
            final int page = i;
            TextView tab = new TextView(getContext());
            tab.setText(tabLabels.get(i));
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(10), 0, dp(10), 0);
            tab.setOnClickListener(v -> {
                if (page == 0) pages.set(0, recents());
                adapter.setEmoji(pages.get(page));
                grid.setSelection(0);
                highlightTab(tabRow, page);
            });
            tabRow.addView(tab, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        }
        adapter.setEmoji(pages.get(initial));
        highlightTab(tabRow, initial);
        tabs.addView(tabScroll, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        TextView backspace = cell("⌫", 18);
        setRepeating(backspace, () -> mIme.onKey(Keyboard.KEYCODE_DELETE, null, 0, 0));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(56), LayoutParams.MATCH_PARENT);
        blp.setMargins(dp(4), dp(4), dp(4), dp(4));
        tabs.addView(backspace, blp);
        panel.addView(tabs, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));
        return panel;
    }

    private void highlightTab(LinearLayout tabRow, int selected) {
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            tabRow.getChildAt(i).setBackground(i == selected ? cellBackground(false) : null);
        }
    }

    private final class EmojiAdapter extends BaseAdapter {
        private String[] mEmoji = new String[0];

        void setEmoji(String[] emoji) {
            mEmoji = emoji;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return mEmoji.length; }
        @Override public String getItem(int position) { return mEmoji[position]; }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView v = convertView instanceof TextView ? (TextView) convertView : null;
            if (v == null) {
                v = new TextView(getContext());
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
                v.setGravity(Gravity.CENTER);
                v.setLayoutParams(new GridView.LayoutParams(GridView.LayoutParams.MATCH_PARENT, dp(46)));
            }
            v.setText(mEmoji[position]);
            return v;
        }
    }

    private void loadEmoji() {
        if (mGroups != null) return;
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
        String saved = PreferenceManager.getDefaultSharedPreferences(getContext())
                .getString(PREF_EMOJI_RECENTS, "");
        return saved.isEmpty() ? new String[0] : saved.split(" ");
    }

    private void addRecent(String emoji) {
        List<String> list = new ArrayList<>(Arrays.asList(recents()));
        list.remove(emoji);
        list.add(0, emoji);
        while (list.size() > MAX_RECENTS) list.remove(list.size() - 1);
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                .putString(PREF_EMOJI_RECENTS, TextUtils.join(" ", list)).apply();
    }

    // ------------------------------------------------------------------ look

    /** Takes the toolbar's colours from the keyboard background, so every theme matches. */
    private void applyColours(Drawable keyboardBackground) {
        int colour = Color.BLACK;
        if (keyboardBackground != null) {
            try {
                Bitmap bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
                Drawable d = keyboardBackground.getConstantState() != null
                        ? keyboardBackground.getConstantState().newDrawable().mutate() : keyboardBackground;
                d.setBounds(0, 0, 16, 16);
                d.draw(new Canvas(bmp));
                colour = bmp.getPixel(8, 8);
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
        setBackgroundColor(mBackground);
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
