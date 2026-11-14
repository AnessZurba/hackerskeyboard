package kr.jcb1ee.hackerskeyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;

/**
 * The theme settings' preview: the keyboard as it will look, drawn by the keyboard's own view
 * with the chosen theme and key borders. It follows changes to either setting.
 */
public final class ThemePreviewPreference extends Preference {
    public ThemePreviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.pref_theme_preview);
        setSelectable(false);
        setPersistent(false);
    }

    /** Redraws the preview (after the theme or key-border setting changed). */
    void refresh() {
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setClickable(false);
        FrameLayout box = (FrameLayout) holder.findViewById(R.id.theme_preview);
        box.removeAllViews();
        final LatinKeyboardView view = makePreview(getContext());
        box.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // Fit the keys into the row (the keyboard is laid out for the screen's width).
        box.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                view.setAvailableWidth(r - l - v.getPaddingLeft() - v.getPaddingRight()));
    }

    /** A keyboard view in the stored theme, showing the main keyboard; it takes no touches. */
    @android.annotation.SuppressLint("ClickableViewAccessibility") // a picture: nothing to click
    static LatinKeyboardView makePreview(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int id = KeyboardTheme.migrate(prefs);
        KeyboardTheme theme = KeyboardTheme.forId(context, id);
        int layout = KeyboardSwitcher.layoutFor(id, theme);
        LatinKeyboardView view = (LatinKeyboardView) android.view.LayoutInflater.from(context)
                .inflate(layout, null);
        if (theme != null) {
            view.applyTheme(theme, prefs.getBoolean(KeyboardTheme.PREF_KEY_BORDERS, true));
        }
        view.setPreviewEnabled(false);
        LatinKeyboard keyboard = new LatinKeyboard(context, R.xml.kbd_full, 0, PREVIEW_HEIGHT_PERCENT);
        keyboard.setVoiceMode(true, true); // fills in the mic/settings key, as on the keyboard
        view.setKeyboard(keyboard);
        view.setOnTouchListener((v, e) -> true); // a picture, not a keyboard
        return view;
    }

    /** The preview's height, as a share of the screen (smaller than the real keyboard). */
    private static final float PREVIEW_HEIGHT_PERCENT = 25f;

    /** Whether a settings key changes what the preview shows. */
    static boolean affectsPreview(String key) {
        return KeyboardSwitcher.PREF_KEYBOARD_LAYOUT.equals(key)
                || KeyboardTheme.PREF_KEY_BORDERS.equals(key)
                || "pref_hint_mode".equals(key) || "pref_label_scale_v2".equals(key);
    }
}
