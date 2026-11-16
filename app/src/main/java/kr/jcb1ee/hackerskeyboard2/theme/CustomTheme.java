package kr.jcb1ee.hackerskeyboard2.theme;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The custom theme: a base theme family whose colours the user may change, separately for the
 * light and the dark variant. Stored as settings: pref_custom_theme_base (a family id) and
 * pref_custom_theme_{light,dark}_{slot} (a colour) for each changed slot; a slot with no
 * setting keeps the base theme's colour.
 */
final class CustomTheme {
    static final String PREFIX = "pref_custom_theme_";
    static final String PREF_BASE = PREFIX + "base";
    /** Which variant the custom theme screen edits ("light" or "dark"); not a look setting. */
    static final String PREF_EDITING = "custom_theme_editing";

    private CustomTheme() {}

    static String key(boolean dark, int slot) {
        return PREFIX + (dark ? "dark_" : "light_") + ThemeSpec.SLOT_NAMES[slot];
    }

    /** The base family (never the custom theme itself). */
    static int baseFamily(SharedPreferences prefs) {
        int id;
        try {
            id = Integer.parseInt(prefs.getString(PREF_BASE, String.valueOf(Themes.DEFAULT)));
        } catch (NumberFormatException e) {
            return Themes.DEFAULT;
        }
        ThemeFamily f = ThemeFamily.of(id);
        return f == null || f.id == Palettes.CUSTOM ? Themes.DEFAULT : f.id;
    }

    /** The base theme's variant, before the user's colours. */
    static ThemeSpec base(Context context, SharedPreferences prefs, boolean dark) {
        return Themes.builtIn(context, baseFamily(prefs), dark);
    }

    static ThemeSpec spec(Context context, SharedPreferences prefs, boolean dark) {
        ThemeSpec s = base(context, prefs, dark).copy();
        for (int slot = 0; slot < ThemeSpec.SLOTS; slot++) {
            String k = key(dark, slot);
            if (prefs.contains(k)) {
                try {
                    s.c[slot] = prefs.getInt(k, s.c[slot]);
                } catch (ClassCastException ignored) {
                    // not a colour: keep the base one
                }
            }
        }
        return s;
    }

    static boolean isSet(SharedPreferences prefs, boolean dark, int slot) {
        return prefs.contains(key(dark, slot));
    }

    static void set(SharedPreferences prefs, boolean dark, int slot, int colour) {
        prefs.edit().putInt(key(dark, slot), colour).apply();
    }

    static void clear(SharedPreferences prefs, boolean dark, int slot) {
        prefs.edit().remove(key(dark, slot)).apply();
    }

    /** Back to the base theme's colours, for one variant. */
    static void reset(SharedPreferences prefs, boolean dark) {
        SharedPreferences.Editor e = prefs.edit();
        for (int slot = 0; slot < ThemeSpec.SLOTS; slot++) e.remove(key(dark, slot));
        e.apply();
    }

    /** Whether the screen edits the dark variant: as chosen there, else what is drawn now. */
    static boolean editingDark(Context context, SharedPreferences prefs) {
        String v = prefs.getString(PREF_EDITING, null);
        if (Themes.MODE_DARK.equals(v)) return true;
        if (Themes.MODE_LIGHT.equals(v)) return false;
        return Themes.wantsDark(context, prefs);
    }
}
