/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kr.jcb1ee.hackerskeyboard2;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/**
 * Keeps autofill chips (InlineContentViews) inside this container's bounds. Their content
 * is a surface owned by the password manager's renderer and drawn on top of the keyboard's
 * window, so without this a chip scrolled out of the row (or under the ">" button) would
 * still show. From the Android Open Source Project's AutofillKeyboard sample.
 */
final class InlineContentClipView extends FrameLayout {
    private final ViewTreeObserver.OnDrawListener mOnDrawListener = this::clipDescendants;
    private final Rect mParentBounds = new Rect();
    private final Rect mContentBounds = new Rect();

    InlineContentClipView(Context context) {
        super(context);
        if (Build.VERSION.SDK_INT >= 30) {
            // A transparent surface on top, as in the sample, for the chips' surfaces to
            // stack with.
            SurfaceView background = new SurfaceView(context);
            background.setZOrderOnTop(true);
            background.getHolder().setFormat(PixelFormat.TRANSPARENT);
            addView(background);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnDrawListener(mOnDrawListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnDrawListener(mOnDrawListener);
    }

    private void clipDescendants() {
        if (Build.VERSION.SDK_INT < 30) return;
        mParentBounds.set(0, 0, getWidth(), getHeight());
        clip(this);
    }

    private void clip(View root) {
        if (root instanceof android.widget.inline.InlineContentView) {
            mContentBounds.set(mParentBounds);
            offsetRectIntoDescendantCoords(root, mContentBounds);
            root.setClipBounds(mContentBounds);
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) clip(group.getChildAt(i));
        }
    }
}
