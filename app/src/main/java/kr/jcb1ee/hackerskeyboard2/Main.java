/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package kr.jcb1ee.hackerskeyboard2;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class Main extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        followKeyboard();
        if (savedInstanceState == null) CrashLog.showIfAny(this);

        TextView version = findViewById(R.id.main_version);
        version.setText("Version " + getString(R.string.auto_version));

        final Button setup1 = findViewById(R.id.main_setup_btn_configure_imes);
        setup1.setOnClickListener(v ->
            startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS))
        );

        final EditText hiddenInput = findViewById(R.id.main_hidden_input);

        final Button setup2 = findViewById(R.id.main_setup_btn_set_ime);
        setup2.setOnClickListener(v -> {
            InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            hiddenInput.requestFocus();
            mgr.showSoftInput(hiddenInput, InputMethodManager.SHOW_FORCED);
            hiddenInput.post(() -> mgr.showInputMethodPicker());
        });

        final Button setup4 = findViewById(R.id.main_setup_btn_input_lang);
        setup4.setOnClickListener(v ->
            startActivity(new Intent(this, InputLanguageSelection.class))
        );

        // The test field can pose as other kinds of field (numbers get the numpad).
        final EditText test = findViewById(R.id.main_setup_edit_test);
        com.google.android.material.button.MaterialButtonToggleGroup type =
                findViewById(R.id.main_test_type);
        type.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            int inputType = InputType.TYPE_CLASS_TEXT;
            if (id == R.id.main_test_type_lines) {
                inputType |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            } else if (id == R.id.main_test_type_number) {
                inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED;
            } else if (id == R.id.main_test_type_password) {
                inputType |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
            }
            int start = test.getSelectionStart(), end = test.getSelectionEnd();
            test.setInputType(inputType); // also sets single/multi-line and password dots
            test.setMinLines(id == R.id.main_test_type_lines ? 3 : 1);
            String text = test.getText().toString();
            if (start >= 0 && end <= text.length()) test.setSelection(start, end);
            test.requestFocus();
            InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            mgr.restartInput(test);
            mgr.showSoftInput(test, 0);
        });

        // Like a chat app, the test field takes images: try the clipboard and gallery images.
        final android.widget.ImageView image = findViewById(R.id.main_test_image);
        final android.view.View removeImage = findViewById(R.id.main_test_image_remove);
        removeImage.setOnClickListener(v -> {
            image.setImageDrawable(null);
            image.setVisibility(android.view.View.GONE);
            removeImage.setVisibility(android.view.View.GONE);
        });
        androidx.core.view.ViewCompat.setOnReceiveContentListener(test, new String[] {"image/*"},
                (view, payload) -> {
                    android.util.Pair<androidx.core.view.ContentInfoCompat, androidx.core.view.ContentInfoCompat> split =
                            payload.partition(item -> item.getUri() != null);
                    if (split.first != null) {
                        image.setImageURI(null);
                        image.setImageURI(split.first.getClip().getItemAt(0).getUri());
                        image.setVisibility(android.view.View.VISIBLE);
                        removeImage.setVisibility(android.view.View.VISIBLE);
                    }
                    return split.second;
                });

        final Button setup5 = findViewById(R.id.main_setup_btn_settings);
        setup5.setOnClickListener(v ->
            startActivity(new Intent(this, LatinIMESettings.class))
        );
    }

    /**
     * The screen follows the keyboard as it slides, frame by frame (Android 11+ reports the
     * keyboard's position on every frame of its animation; AndroidX approximates it before
     * that): the space at the bottom grows with the keyboard and the focused field rides
     * up just above it, instead of the layout jumping once the keyboard has arrived.
     */
    private void followKeyboard() {
        final android.widget.ScrollView root = findViewById(R.id.main_root);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        final boolean[] animating = new boolean[1];
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            if (!animating[0]) applyInsets(root, insets);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
        androidx.core.view.ViewCompat.setWindowInsetsAnimationCallback(root,
                new androidx.core.view.WindowInsetsAnimationCompat.Callback(
                        androidx.core.view.WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
                    @Override
                    public void onPrepare(androidx.core.view.WindowInsetsAnimationCompat animation) {
                        if (isIme(animation)) animating[0] = true;
                    }

                    @Override
                    public androidx.core.view.WindowInsetsCompat onProgress(
                            androidx.core.view.WindowInsetsCompat insets,
                            java.util.List<androidx.core.view.WindowInsetsAnimationCompat> running) {
                        if (animating[0]) applyInsets(root, insets); // every frame of the slide
                        return insets;
                    }

                    @Override
                    public void onEnd(androidx.core.view.WindowInsetsAnimationCompat animation) {
                        if (!isIme(animation)) return;
                        animating[0] = false;
                        androidx.core.view.ViewCompat.requestApplyInsets(root);
                    }

                    private boolean isIme(androidx.core.view.WindowInsetsAnimationCompat a) {
                        return (a.getTypeMask() & androidx.core.view.WindowInsetsCompat.Type.ime()) != 0;
                    }
                });
    }

    /** Pads the screen clear of the system bars and the keyboard; keeps the focus in view. */
    static void applyInsets(android.widget.ScrollView root, androidx.core.view.WindowInsetsCompat insets) {
        androidx.core.graphics.Insets bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars());
        androidx.core.graphics.Insets ime = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.ime());
        root.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
        android.view.View focus = root.findFocus();
        if (focus == null || ime.bottom == 0) return;
        android.graphics.Rect r = new android.graphics.Rect();
        focus.getDrawingRect(r);
        root.offsetDescendantRectToMyCoords(focus, r);
        int margin = Math.round(16 * root.getResources().getDisplayMetrics().density);
        int visibleBottom = root.getScrollY() + root.getHeight() - root.getPaddingBottom();
        if (r.bottom + margin > visibleBottom) {
            root.scrollTo(0, r.bottom + margin - (root.getHeight() - root.getPaddingBottom()));
        }
    }
}
