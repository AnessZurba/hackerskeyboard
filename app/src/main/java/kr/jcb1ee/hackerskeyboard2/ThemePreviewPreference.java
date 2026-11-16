package kr.jcb1ee.hackerskeyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;

import kr.jcb1ee.hackerskeyboard2.theme.KeyboardLook;
import kr.jcb1ee.hackerskeyboard2.theme.Themes;

/**
 * A theme preview: the keyboard as it will look, drawn by the keyboard's own view in the look
 * {@link Themes#forPreview} gives for this preference's key (the custom theme screen's preview
 * shows the variant being edited). It redraws when a setting it shows changes.
 */
public final class ThemePreviewPreference extends Preference
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public ThemePreviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.pref_theme_preview);
        setSelectable(false);
        setPersistent(false);
        // Before the theme lists below it store their defaults: a new install follows the
        // system, an old one keeps its theme (see Themes.migrate).
        Themes.migrate(PreferenceManager.getDefaultSharedPreferences(context));
    }

    /** Redraws the preview (after a setting it shows changed). */
    void refresh() {
        notifyChanged();
    }

    @Override
    public void onAttached() {
        super.onAttached();
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDetached() {
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onDetached();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (affectsPreview(key)) refresh();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setClickable(false);
        FrameLayout box = (FrameLayout) holder.findViewById(R.id.theme_preview);
        box.removeAllViews();
        final LatinKeyboardView view = makePreview(getContext(),
                Themes.forPreview(getContext(), getKey()));
        box.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // Fit the keys into the row (the keyboard is laid out for the screen's width).
        box.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                view.setAvailableWidth(r - l - v.getPaddingLeft() - v.getPaddingRight()));
    }

    /** A keyboard view in the stored theme, showing the main keyboard; it takes no touches. */
    static LatinKeyboardView makePreview(Context context) {
        return makePreview(context, Themes.forKeyboard(context));
    }

    /** A keyboard view in a look, showing the main keyboard; it takes no touches. */
    @android.annotation.SuppressLint("ClickableViewAccessibility") // a picture: nothing to click
    static LatinKeyboardView makePreview(Context context, KeyboardLook look) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        loadLookSettings(context, prefs);
        LatinKeyboardView view = (LatinKeyboardView) android.view.LayoutInflater.from(context)
                .inflate(R.layout.input_material_dark, null);
        view.applyLook(look);
        view.setPreviewEnabled(false);
        LatinKeyboard keyboard = new LatinKeyboard(context, R.xml.kbd_full, 0, PREVIEW_HEIGHT_PERCENT);
        keyboard.setVoiceMode(true, true); // fills in the mic/settings key, as on the keyboard
        view.setKeyboard(keyboard);
        view.setOnTouchListener((v, e) -> true); // a picture, not a keyboard
        return view;
    }

    /** The preview's height, as a share of the screen (smaller than the real keyboard). */
    private static final float PREVIEW_HEIGHT_PERCENT = 25f;

    /** Settings on the theme screen, besides the theme's own, that change how keys look. */
    private static final String[] LOOK_KEYS = {LatinIME.PREF_HINT_MODE, "pref_label_scale_v2",
            "pref_top_row_scale"};

    /** Whether a settings key changes what the preview shows. */
    static boolean affectsPreview(String key) {
        if (Themes.affectsPreview(key)) return true;
        for (String k : LOOK_KEYS) if (k.equals(key)) return true;
        return false;
    }

    /**
     * Reads the look settings the keyboard keeps in LatinIME.sKeyboardSettings (hints, label
     * size, top-row height) from the saved settings: the keyboard service, which normally
     * updates them, may not be running while the settings screen is open.
     */
    private static void loadLookSettings(Context context, SharedPreferences prefs) {
        GlobalKeyboardSettings s = LatinIME.sKeyboardSettings;
        try {
            s.hintMode = Integer.parseInt(prefs.getString(LatinIME.PREF_HINT_MODE,
                    context.getString(R.string.default_hint_mode)));
            s.labelScalePref = Float.parseFloat(prefs.getString("pref_label_scale_v2", "1.0"));
            s.topRowScale = Float.parseFloat(prefs.getString("pref_top_row_scale", "1.0"));
        } catch (NumberFormatException ignored) {
            // keep what the keyboard last used
        }
    }
}
