package kr.jcb1ee.hackerskeyboard2.theme;

/**
 * A theme as the settings list it: one entry whose light or dark variant is drawn, as the
 * "Light or dark" setting says. A family is stored by its own id (that of one of its variants),
 * and each variant id keeps meaning its variant.
 */
final class ThemeFamily {
    /** The stored id of the family (pref_keyboard_layout). */
    final int id;
    final int lightId;
    final int darkId;

    private ThemeFamily(int id, int lightId, int darkId) {
        this.id = id;
        this.lightId = lightId;
        this.darkId = darkId;
    }

    /** In the order of the settings list (see R.array.keyboard_layout_modes_values). */
    static final ThemeFamily[] ALL = {
        new ThemeFamily(Palettes.MATERIAL_YOU, Palettes.MATERIAL_YOU, Palettes.MATERIAL_YOU),
        new ThemeFamily(Palettes.MATERIAL_DARK, Palettes.MATERIAL_LIGHT, Palettes.MATERIAL_DARK),
        new ThemeFamily(Palettes.HIGH_CONTRAST_DARK, Palettes.HIGH_CONTRAST_LIGHT,
                Palettes.HIGH_CONTRAST_DARK),
        new ThemeFamily(Palettes.TYPEWRITER, Palettes.TYPEWRITER, Palettes.TYPEWRITER),
        new ThemeFamily(Palettes.KEYCAPS_RETRO, Palettes.KEYCAPS_RETRO, Palettes.KEYCAPS_MIDNIGHT),
        new ThemeFamily(Palettes.TERMINAL, Palettes.TERMINAL_LIGHT, Palettes.TERMINAL),
        new ThemeFamily(Palettes.CUSTOM, Palettes.CUSTOM, Palettes.CUSTOM),
    };

    /** The family a variant id belongs to, or null. */
    static ThemeFamily of(int variantId) {
        for (ThemeFamily f : ALL) {
            if (f.id == variantId || f.lightId == variantId || f.darkId == variantId) return f;
        }
        return null;
    }

    /** The variant id drawn for light or dark. */
    int variant(boolean dark) {
        return dark ? darkId : lightId;
    }

    /**
     * Which mode shows this variant id: {@link Themes#MODE_LIGHT} or MODE_DARK for one half of
     * a pair, {@link Themes#MODE_SYSTEM} where the id is not one half (Material You, Typewriter).
     */
    String modeOf(int variantId) {
        if (lightId == darkId) return Themes.MODE_SYSTEM;
        return variantId == lightId ? Themes.MODE_LIGHT : Themes.MODE_DARK;
    }
}
