package kr.jcb1ee.hackerskeyboard2.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.preference.PreferenceManager;

/**
 * The theme settings, and the look they ask for. The keyboard (and the theme preview) call
 * {@link #forKeyboard}; settings that change the look are listed by {@link #affectsLook}.
 *
 * <p>pref_keyboard_layout holds a theme family's id ({@link ThemeFamily}); pref_theme_mode says
 * whether its light or dark variant is drawn, or which the system is using.
 */
public final class Themes {
    public static final String PREF_THEME = "pref_keyboard_layout";
    public static final String PREF_MODE = "pref_theme_mode";
    public static final String PREF_KEY_BORDERS = "pref_key_borders";
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    /** Material, the default. */
    static final int DEFAULT = Palettes.MATERIAL_DARK;
    /** Stone (bold), an image theme replaced by High contrast (light). */
    private static final int STONE = 2;
    /**
     * Removed themes, drawn as Material Dark: ICS, Gingerbread, Transparent, ICS dark hints,
     * the old Material Dark (4, blue-grey) and AMOLED black (9).
     */
    private static final int[] REMOVED = {0, 1, 3, 4, 6, 9};

    private Themes() {}

    /** The variant id a stored id draws now (a removed theme's replacement). */
    static int current(int id) {
        if (id == STONE) return Palettes.HIGH_CONTRAST_LIGHT;
        for (int r : REMOVED) if (r == id) return Palettes.MATERIAL_DARK;
        return ThemeFamily.of(id) != null ? id : DEFAULT;
    }

    private static boolean known(int id) {
        if (id == STONE || ThemeFamily.of(id) != null) return true;
        for (int r : REMOVED) if (r == id) return true;
        return false;
    }

    /**
     * Brings stored theme settings up to date and returns the theme family's id. A stored
     * variant id (Material Light, Keycaps midnight, a removed theme...) becomes its family, and
     * the first time, or for a variant id restored from an old backup, the light/dark setting
     * is set to that variant so the keyboard looks as it did. With no theme stored (a new
     * install) the keyboard follows the system.
     */
    public static int migrate(SharedPreferences prefs) {
        String raw = prefs.getString(PREF_THEME, null);
        boolean hasMode = prefs.contains(PREF_MODE);
        if (raw == null) {
            if (!hasMode) prefs.edit().putString(PREF_MODE, MODE_SYSTEM).apply();
            return DEFAULT;
        }
        int stored;
        try {
            stored = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            stored = DEFAULT;
        }
        int variant = current(stored);
        ThemeFamily family = ThemeFamily.of(variant);
        // An id this version does not know (from a newer one) says nothing about light or dark.
        String mode = known(stored) ? family.modeOf(variant) : MODE_SYSTEM;
        SharedPreferences.Editor edit = null;
        if (!hasMode || (stored != family.id && !MODE_SYSTEM.equals(mode))) {
            edit = prefs.edit().putString(PREF_MODE, mode);
        }
        if (!raw.equals(String.valueOf(family.id))) {
            if (edit == null) edit = prefs.edit();
            edit.putString(PREF_THEME, String.valueOf(family.id));
        }
        if (edit != null) edit.apply();
        return family.id;
    }

    /** Whether the dark variant is drawn: the setting, or the system's night mode. */
    static boolean wantsDark(Context context, SharedPreferences prefs) {
        String mode = prefs.getString(PREF_MODE, MODE_SYSTEM);
        if (MODE_DARK.equals(mode)) return true;
        if (MODE_LIGHT.equals(mode)) return false;
        return isNight(context);
    }

    static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** The look the keyboard draws, from the stored settings. */
    public static KeyboardLook forKeyboard(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int family = migrate(prefs);
        return look(context, prefs, spec(context, prefs, family, wantsDark(context, prefs)));
    }

    /** The key of the custom theme screen's preview, which shows the variant being edited. */
    public static final String CUSTOM_PREVIEW_KEY = "custom_theme_preview";

    /** The look a theme preview shows; {@code previewKey} is the preview preference's key. */
    public static KeyboardLook forPreview(Context context, String previewKey) {
        if (!CUSTOM_PREVIEW_KEY.equals(previewKey)) return forKeyboard(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        migrate(prefs);
        return look(context, prefs,
                CustomTheme.spec(context, prefs, CustomTheme.editingDark(context, prefs)));
    }

    private static KeyboardLook look(Context context, SharedPreferences prefs, ThemeSpec spec) {
        return new KeyboardLook(context, spec, prefs.getBoolean(PREF_KEY_BORDERS, true));
    }

    /** The spec of a family (the custom theme included) in its light or dark variant. */
    static ThemeSpec spec(Context context, SharedPreferences prefs, int family, boolean dark) {
        if (family == Palettes.CUSTOM) return CustomTheme.spec(context, prefs, dark);
        return builtIn(context, family, dark);
    }

    /** A built-in family's variant (anything else: Material). */
    static ThemeSpec builtIn(Context context, int family, boolean dark) {
        ThemeFamily f = ThemeFamily.of(family);
        if (f == null || f.id == Palettes.CUSTOM) f = ThemeFamily.of(DEFAULT);
        return Palettes.forId(context, f.variant(dark), dark);
    }

    /** Whether a settings change changes how the keyboard looks. */
    public static boolean affectsLook(String key) {
        return PREF_THEME.equals(key) || PREF_MODE.equals(key) || PREF_KEY_BORDERS.equals(key)
                || (key != null && key.startsWith(CustomTheme.PREFIX));
    }

    /** Whether a settings change changes what a theme preview shows. */
    public static boolean affectsPreview(String key) {
        return affectsLook(key) || CustomTheme.PREF_EDITING.equals(key);
    }
}
