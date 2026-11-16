package kr.jcb1ee.hackerskeyboard2.theme;

import android.app.backup.BackupManager;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import kr.jcb1ee.hackerskeyboard2.R;
import kr.jcb1ee.hackerskeyboard2.SystemBars;

/**
 * The custom theme screen: a live preview, the base theme, which variant (light or dark) is
 * being edited, a row per colour, and a reset.
 */
public class CustomThemeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (icicle == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new Fragment())
                    .commit();
        }
        SystemBars.padContent(this);
    }

    public static class Fragment extends PreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        static final String KEY_USE = "custom_theme_use";
        static final String KEY_COLOURS = "custom_theme_colours";
        static final String KEY_RESET = "custom_theme_reset";

        private SharedPreferences mPrefs;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.prefs_custom_theme);
            mPrefs = getPreferenceManager().getSharedPreferences();
            Themes.migrate(mPrefs);
            SwitchPreferenceCompat use = findPreference(KEY_USE);
            use.setOnPreferenceChangeListener((p, on) -> {
                mPrefs.edit().putString(Themes.PREF_THEME, String.valueOf((Boolean) on
                        ? Palettes.CUSTOM : CustomTheme.baseFamily(mPrefs))).apply();
                return true;
            });
            findPreference(KEY_RESET).setOnPreferenceClickListener(p -> {
                confirmReset();
                return true;
            });
            update();
            mPrefs.registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onDestroy() {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }

        private boolean editingDark() {
            return CustomTheme.editingDark(requireContext(), mPrefs);
        }

        /** Shows the settings as stored: in use or not, and the rows for the edited variant. */
        private void update() {
            SwitchPreferenceCompat use = findPreference(KEY_USE);
            use.setChecked(String.valueOf(Palettes.CUSTOM)
                    .equals(mPrefs.getString(Themes.PREF_THEME, null)));
            boolean dark = editingDark();
            findPreference(KEY_RESET).setSummary(dark ? R.string.theme_custom_reset_dark
                    : R.string.theme_custom_reset_light);
            PreferenceCategory colours = findPreference(KEY_COLOURS);
            colours.removeAll();
            colours.setTitle(dark ? R.string.theme_custom_colours_dark
                    : R.string.theme_custom_colours_light);
            int shape = CustomTheme.base(requireContext(), mPrefs, dark).shape;
            for (int slot : ORDER) {
                if (!ThemeSpec.slotApplies(slot, shape)) continue;
                colours.addPreference(new ColourPreference(requireContext(), dark, slot,
                        title(slot, shape)));
            }
        }

        /** The colour rows, in screen order. */
        static final int[] ORDER = {ThemeSpec.BACKGROUND, ThemeSpec.KEY, ThemeSpec.LABEL,
                ThemeSpec.SKIRT, ThemeSpec.MODIFIER, ThemeSpec.MODIFIER_LABEL,
                ThemeSpec.MODIFIER_SKIRT, ThemeSpec.ACCENT, ThemeSpec.ACCENT_LABEL,
                ThemeSpec.ACCENT_SKIRT, ThemeSpec.PRESSED, ThemeSpec.ACTIVE, ThemeSpec.OUTLINE,
                ThemeSpec.HINT, ThemeSpec.GLOW, ThemeSpec.PANEL, ThemeSpec.PANEL_OUTLINE};

        static int title(int slot, int shape) {
            switch (slot) {
                case ThemeSpec.BACKGROUND: return R.string.theme_slot_background;
                case ThemeSpec.KEY:
                    return shape == ThemeSpec.SCULPTED ? R.string.theme_slot_key_top
                            : R.string.theme_slot_key;
                case ThemeSpec.LABEL: return R.string.theme_slot_label;
                case ThemeSpec.MODIFIER: return R.string.theme_slot_modifier;
                case ThemeSpec.MODIFIER_LABEL: return R.string.theme_slot_modifier_label;
                case ThemeSpec.ACCENT: return R.string.theme_slot_accent;
                case ThemeSpec.ACCENT_LABEL: return R.string.theme_slot_accent_label;
                case ThemeSpec.PRESSED: return R.string.theme_slot_pressed;
                case ThemeSpec.ACTIVE: return R.string.theme_slot_active;
                case ThemeSpec.OUTLINE:
                    return shape == ThemeSpec.ROUND ? R.string.theme_slot_rim
                            : R.string.theme_slot_outline;
                case ThemeSpec.HINT: return R.string.theme_slot_hint;
                case ThemeSpec.SKIRT: return R.string.theme_slot_skirt;
                case ThemeSpec.MODIFIER_SKIRT: return R.string.theme_slot_modifier_skirt;
                case ThemeSpec.ACCENT_SKIRT: return R.string.theme_slot_accent_skirt;
                case ThemeSpec.GLOW: return R.string.theme_slot_glow;
                case ThemeSpec.PANEL: return R.string.theme_slot_panel;
                default: return R.string.theme_slot_panel_outline;
            }
        }

        private void confirmReset() {
            boolean dark = editingDark();
            new AlertDialog.Builder(requireContext())
                    .setMessage(dark ? R.string.theme_custom_reset_dark
                            : R.string.theme_custom_reset_light)
                    .setPositiveButton(R.string.theme_custom_reset,
                            (d, w) -> CustomTheme.reset(mPrefs, dark))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            // The preview follows the settings by itself (ThemePreviewPreference).
            if (key == null || getContext() == null) return;
            new BackupManager(requireContext()).dataChanged();
            if (CustomTheme.PREF_BASE.equals(key) || CustomTheme.PREF_EDITING.equals(key)
                    || Themes.PREF_MODE.equals(key)) {
                update(); // the base's key style has other rows, or the other variant shows
            } else if (Themes.PREF_THEME.equals(key)) {
                ((SwitchPreferenceCompat) findPreference(KEY_USE)).setChecked(
                        String.valueOf(Palettes.CUSTOM).equals(prefs.getString(key, null)));
            } else if (key.startsWith(CustomTheme.PREFIX)) {
                PreferenceCategory colours = findPreference(KEY_COLOURS);
                for (int i = 0; i < colours.getPreferenceCount(); i++) {
                    ((ColourPreference) colours.getPreference(i)).updateSummary();
                }
            }
        }
    }
}
