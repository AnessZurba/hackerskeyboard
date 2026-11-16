package kr.jcb1ee.hackerskeyboard2.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import kr.jcb1ee.hackerskeyboard2.R;

/**
 * One colour of the custom theme (a {@link ThemeSpec} slot of the light or dark variant): its
 * swatch, and a colour picker when tapped.
 */
final class ColourPreference extends Preference {
    final boolean dark;
    final int slot;

    ColourPreference(Context context, boolean dark, int slot, int title) {
        super(context);
        this.dark = dark;
        this.slot = slot;
        setKey(CustomTheme.key(dark, slot));
        setPersistent(false); // stored by CustomTheme, as an int
        setTitle(title);
        setIconSpaceReserved(false);
        setWidgetLayoutResource(R.layout.pref_colour_swatch);
        updateSummary();
    }

    private SharedPreferences prefs() {
        return androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    /** The colour drawn now: the user's, or the base theme's. */
    int colour() {
        return CustomTheme.spec(getContext(), prefs(), dark).c[slot];
    }

    void updateSummary() {
        SharedPreferences prefs = prefs();
        String hex = ColourPickerDialog.format(colour());
        setSummary(CustomTheme.isSet(prefs, dark, slot) ? hex
                : getContext().getString(R.string.theme_colour_from_base, hex));
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View swatch = holder.findViewById(R.id.colour_swatch);
        if (swatch != null) swatch.setBackground(ColourPickerDialog.swatch(getContext(), colour()));
    }

    @Override
    protected void onClick() {
        ColourPickerDialog.show(getContext(), getTitle(), colour(),
                new ColourPickerDialog.Callback() {
                    @Override
                    public void onPicked(int colour) {
                        CustomTheme.set(prefs(), dark, slot, colour);
                        updateSummary();
                    }

                    @Override
                    public void onUseBase() {
                        CustomTheme.clear(prefs(), dark, slot);
                        updateSummary();
                    }
                });
    }
}
