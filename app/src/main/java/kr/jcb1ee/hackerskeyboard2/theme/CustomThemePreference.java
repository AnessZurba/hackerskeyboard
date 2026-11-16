package kr.jcb1ee.hackerskeyboard2.theme;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import androidx.preference.Preference;

/** The theme settings' row that opens the custom theme screen. */
public final class CustomThemePreference extends Preference {
    public CustomThemePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
    }

    @Override
    protected void onClick() {
        getContext().startActivity(new Intent(getContext(), CustomThemeActivity.class));
    }
}
