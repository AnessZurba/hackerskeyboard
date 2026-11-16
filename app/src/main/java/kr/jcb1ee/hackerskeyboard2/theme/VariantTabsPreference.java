package kr.jcb1ee.hackerskeyboard2.theme;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButtonToggleGroup;

import kr.jcb1ee.hackerskeyboard2.R;

/** Two tabs, Light and Dark: which variant's colours the custom theme screen edits. */
public final class VariantTabsPreference extends Preference {
    public VariantTabsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.pref_variant_tabs);
        setSelectable(false);
        setPersistent(false); // stored as CustomTheme.PREF_EDITING
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setClickable(false);
        MaterialButtonToggleGroup tabs = (MaterialButtonToggleGroup) holder.findViewById(
                R.id.variant_tabs);
        tabs.clearOnButtonCheckedListeners();
        boolean dark = CustomTheme.editingDark(getContext(),
                PreferenceManager.getDefaultSharedPreferences(getContext()));
        tabs.check(dark ? R.id.variant_dark : R.id.variant_light);
        tabs.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) setEditingDark(id == R.id.variant_dark);
        });
    }

    void setEditingDark(boolean dark) {
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                .putString(CustomTheme.PREF_EDITING, dark ? Themes.MODE_DARK : Themes.MODE_LIGHT)
                .apply();
    }
}
