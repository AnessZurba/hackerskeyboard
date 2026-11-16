package kr.jcb1ee.hackerskeyboard2.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import java.util.Locale;

import kr.jcb1ee.hackerskeyboard2.R;

/** A dialog to pick one colour: the picker square, a hex field and a swatch of the result. */
final class ColourPickerDialog {
    interface Callback {
        void onPicked(int colour);

        /** The user asked for the base theme's colour back. */
        void onUseBase();
    }

    private ColourPickerDialog() {}

    static AlertDialog show(Context context, CharSequence title, int colour, Callback callback) {
        float d = context.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * d);
        box.setPadding(pad, Math.round(8 * d), pad, 0);

        final ColourPickerView picker = new ColourPickerView(context);
        picker.setColour(colour);
        box.addView(picker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Math.round(8 * d), Math.round(8 * d), Math.round(8 * d), 0);
        final View swatch = new View(context);
        final GradientDrawable swatchShape = swatch(context, colour);
        swatch.setBackground(swatchShape);
        int s = Math.round(40 * d);
        row.addView(swatch, new LinearLayout.LayoutParams(s, s));
        final EditText hex = new EditText(context);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setFilters(new InputFilter[] {new InputFilter.LengthFilter(9)});
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setSingleLine(true);
        hex.setHint("#RRGGBB");
        hex.setContentDescription(context.getString(R.string.theme_colour_hex));
        hex.setText(format(colour));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = Math.round(12 * d);
        row.addView(hex, lp);
        box.addView(row);

        final boolean[] typing = {false};
        picker.setListener(c -> {
            swatchShape.setColor(c);
            if (!typing[0]) hex.setText(format(c));
        });
        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence t, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable e) {
                Integer c = parse(e.toString());
                if (c == null || c == picker.getColour()) return;
                typing[0] = true;
                picker.setColour(c);
                swatchShape.setColor(c);
                typing[0] = false;
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(android.R.string.ok,
                        (di, w) -> callback.onPicked(picker.getColour()))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.theme_colour_use_base, (di, w) -> callback.onUseBase())
                .create();
        dialog.show();
        return dialog;
    }

    static GradientDrawable swatch(Context context, int colour) {
        float d = context.getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(colour);
        g.setStroke(Math.max(1, Math.round(d)), 0x80808080);
        return g;
    }

    /** "#RRGGBB", or "#AARRGGBB" for a colour that is not opaque. */
    static String format(int colour) {
        if (Color.alpha(colour) == 0xFF) {
            return String.format(Locale.ROOT, "#%06X", colour & 0xFFFFFF);
        }
        return String.format(Locale.ROOT, "#%08X", colour);
    }

    /** Reads "#RRGGBB" or "#AARRGGBB" (the # is optional), or null. */
    static Integer parse(String text) {
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6 && t.length() != 8) return null;
        try {
            long v = Long.parseLong(t, 16);
            return t.length() == 6 ? (int) (0xFF000000L | v) : (int) v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
