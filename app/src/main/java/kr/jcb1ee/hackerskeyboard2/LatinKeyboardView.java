/*
 * Copyright (C) 2008 The Android Open Source Project
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

import java.util.List;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import kr.jcb1ee.hackerskeyboard2.Keyboard.Key;

import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.PopupWindow;
import android.widget.TextView;

public class LatinKeyboardView extends LatinKeyboardBaseView {
    static final String TAG = "HK/LatinKeyboardView";

	// The keycode list needs to stay in sync with the
	// res/values/keycodes.xml file.

	// FIXME: The following keycodes should really be renumbered
	// since they conflict with existing KeyEvent keycodes.
    static final int KEYCODE_OPTIONS = -100;
    static final int KEYCODE_OPTIONS_LONGPRESS = -101;
    static final int KEYCODE_VOICE = -102;
    static final int KEYCODE_F1 = -103;
    static final int KEYCODE_NEXT_LANGUAGE = -104;
    static final int KEYCODE_PREV_LANGUAGE = -105;
    static final int KEYCODE_COMPOSE = -10024;
    
	// The following keycodes match (negative) KeyEvent keycodes.
    // Would be better to use the real KeyEvent values, but many
    // don't exist prior to the Honeycomb API (level 11).
    static final int KEYCODE_DPAD_UP = -19;
    static final int KEYCODE_DPAD_DOWN = -20;
    static final int KEYCODE_DPAD_LEFT = -21;
    static final int KEYCODE_DPAD_RIGHT = -22;
    static final int KEYCODE_DPAD_CENTER = -23;
    static final int KEYCODE_ALT_LEFT = -57;
    static final int KEYCODE_PAGE_UP = -92;
    static final int KEYCODE_PAGE_DOWN = -93;
    static final int KEYCODE_ESCAPE = -111;
    static final int KEYCODE_FORWARD_DEL = -112;
    static final int KEYCODE_CTRL_LEFT = -113;
    static final int KEYCODE_CAPS_LOCK = -115;
    static final int KEYCODE_SCROLL_LOCK = -116;
    static final int KEYCODE_META_LEFT = -117;
    static final int KEYCODE_FN = -119;
    static final int KEYCODE_SYSRQ = -120;
    static final int KEYCODE_BREAK = -121;
    static final int KEYCODE_HOME = -122;
    static final int KEYCODE_END = -123;
    static final int KEYCODE_INSERT = -124;
    static final int KEYCODE_FKEY_F1 = -131;
    static final int KEYCODE_FKEY_F2 = -132;
    static final int KEYCODE_FKEY_F3 = -133;
    static final int KEYCODE_FKEY_F4 = -134;
    static final int KEYCODE_FKEY_F5 = -135;
    static final int KEYCODE_FKEY_F6 = -136;
    static final int KEYCODE_FKEY_F7 = -137;
    static final int KEYCODE_FKEY_F8 = -138;
    static final int KEYCODE_FKEY_F9 = -139;
    static final int KEYCODE_FKEY_F10 = -140;
    static final int KEYCODE_FKEY_F11 = -141;
    static final int KEYCODE_FKEY_F12 = -142;
    static final int KEYCODE_NUM_LOCK = -143;

    private Keyboard mPhoneKeyboard;


    /** Whether we've started dropping move events because we found a big jump */
    private boolean mDroppingEvents;
    /** Whether multi-touch disambiguation is off because a real multi-touch event occurred. */
    private boolean mDisableDisambiguation;
    /** The distance threshold at which we start treating the touch session as a multi-touch */
    private int mJumpThresholdSquare = Integer.MAX_VALUE;
    /** The y coordinate of the last row */
    private int mLastRowY;

    public LatinKeyboardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LatinKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // TODO(klausw): migrate attribute styles to LatinKeyboardView?
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.LatinKeyboardBaseView, defStyle, R.style.LatinKeyboardBaseView);
        LayoutInflater inflate =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        int previewLayout = 0;
        int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            int attr = a.getIndex(i);

            if (attr == R.styleable.LatinKeyboardBaseView_keyPreviewLayout) {
                previewLayout = a.getResourceId(attr, 0);
                if (previewLayout == R.layout.null_layout) previewLayout = 0;
            } else if (attr == R.styleable.LatinKeyboardBaseView_keyPreviewOffset) {
                mPreviewOffset = a.getDimensionPixelOffset(attr, 0);
            } else if (attr == R.styleable.LatinKeyboardBaseView_keyPreviewHeight) {
                mPreviewHeight = a.getDimensionPixelSize(attr, 80);
            } else if (attr == R.styleable.LatinKeyboardBaseView_popupLayout) {
                mPopupLayout = a.getResourceId(attr, 0);
                if (mPopupLayout == R.layout.null_layout) mPopupLayout = 0;
            }
        }
        a.recycle();

        final Resources res = getResources();

        // If true, popups are forced to remain inside the keyboard area. If false,
        // they can extend above it. Enable clipping just for Android P since drawing
        // outside the keyboard area doesn't work on that version.
        boolean clippingEnabled = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P);

        if (previewLayout != 0) {
            mPreviewPopup = new PopupWindow(context);
            if (!isInEditMode())
                Log.i(TAG, "new mPreviewPopup " + mPreviewPopup + " from " + this);
            mPreviewText = (TextView) inflate.inflate(previewLayout, null);
            mPreviewTextSizeLarge = (int) res.getDimension(R.dimen.key_preview_text_size_large);
            mPreviewPopup.setContentView(mPreviewText);
            mPreviewPopup.setBackgroundDrawable(null);
            mPreviewPopup.setTouchable(false);
            mPreviewPopup.setAnimationStyle(R.style.KeyPreviewAnimation);
            mPreviewPopup.setClippingEnabled(clippingEnabled);
        } else {
            mShowPreview = false;
        }

        if (mPopupLayout != 0) {
            mMiniKeyboardParent = this;
            mMiniKeyboardPopup = new PopupWindow(context);
            if (!isInEditMode())
                Log.i(TAG, "new mMiniKeyboardPopup " + mMiniKeyboardPopup + " from " + this);
            mMiniKeyboardPopup.setBackgroundDrawable(null);
            mMiniKeyboardPopup.setAnimationStyle(R.style.MiniKeyboardAnimation);
            mMiniKeyboardPopup.setClippingEnabled(clippingEnabled);
            mMiniKeyboardVisible = false;
        }
    }

    public void setPhoneKeyboard(Keyboard phoneKeyboard) {
        mPhoneKeyboard = phoneKeyboard;
    }

    @Override
    public void setPreviewEnabled(boolean previewEnabled) {
        if (getKeyboard() == mPhoneKeyboard) {
            // Phone keyboard never shows popup preview (except language switch).
            super.setPreviewEnabled(false);
        } else {
            super.setPreviewEnabled(previewEnabled);
        }
    }

    @Override
    public void setKeyboard(Keyboard newKeyboard) {
        final Keyboard oldKeyboard = getKeyboard();
        if (oldKeyboard instanceof LatinKeyboard) {
            // Reset old keyboard state before switching to new keyboard.
            ((LatinKeyboard)oldKeyboard).keyReleased();
        }
        super.setKeyboard(newKeyboard);
        // One-seventh of the keyboard width seems like a reasonable threshold
        mJumpThresholdSquare = newKeyboard.getMinWidth() / 7;
        mJumpThresholdSquare *= mJumpThresholdSquare;
        // Get Y coordinate of the last row based on the row count, assuming equal height
        int numRows = newKeyboard.mRowCount;
        mLastRowY = (newKeyboard.getHeight() * (numRows - 1)) / numRows;
    }

    @Override
    protected boolean onLongPress(Key key) {
        int primaryCode = key.codes[0];
        if (primaryCode == KEYCODE_OPTIONS && key.popupResId != R.xml.popup_mic) {
            // (The mic/settings key opens its popup instead, to pick the other one.)
            return invokeOnKey(KEYCODE_OPTIONS_LONGPRESS);
        } else if (primaryCode == KEYCODE_DPAD_CENTER) {
            return invokeOnKey(KEYCODE_COMPOSE);
        } else if (primaryCode == '0' && getKeyboard() == mPhoneKeyboard) {
            // Long pressing on 0 in phone number keypad gives you a '+'.
            return invokeOnKey('+');
        } else {
            return super.onLongPress(key);
        }
    }

    private boolean invokeOnKey(int primaryCode) {
        getOnKeyboardActionListener().onKey(primaryCode, null,
                LatinKeyboardBaseView.NOT_A_TOUCH_COORDINATE,
                LatinKeyboardBaseView.NOT_A_TOUCH_COORDINATE);
        return true;
    }

    /**
     * This function checks to see if we need to handle any sudden jumps in the pointer location
     * that could be due to a multi-touch being treated as a move by the firmware or hardware.
     * Once a sudden jump is detected, all subsequent move events are discarded
     * until an UP is received.<P>
     * When a sudden jump is detected, an UP event is simulated at the last position and when
     * the sudden moves subside, a DOWN event is simulated for the second key.
     * @param me the motion event
     * @return true if the event was consumed, so that it doesn't continue to be handled by
     * KeyboardView.
     */
    private boolean handleSuddenJump(MotionEvent me) {
        final int action = me.getAction();
        final int x = (int) me.getX();
        final int y = (int) me.getY();
        boolean result = false;

        // Real multi-touch event? Stop looking for sudden jumps
        if (me.getPointerCount() > 1) {
            mDisableDisambiguation = true;
        }
        if (mDisableDisambiguation) {
            // If UP, reset the multi-touch flag
            if (action == MotionEvent.ACTION_UP) mDisableDisambiguation = false;
            return false;
        }

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            // Reset the "session"
            mDroppingEvents = false;
            mDisableDisambiguation = false;
            break;
        case MotionEvent.ACTION_MOVE:
            // Is this a big jump?
            final int distanceSquare = (mLastX - x) * (mLastX - x) + (mLastY - y) * (mLastY - y);
            // Check the distance and also if the move is not entirely within the bottom row
            // If it's only in the bottom row, it might be an intentional slide gesture
            // for language switching
            if (distanceSquare > mJumpThresholdSquare
                    && (mLastY < mLastRowY || y < mLastRowY)) {
                // If we're not yet dropping events, start dropping and send an UP event
                if (!mDroppingEvents) {
                    mDroppingEvents = true;
                    // Send an up event
                    MotionEvent translated = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                            MotionEvent.ACTION_UP,
                            mLastX, mLastY, me.getMetaState());
                    super.onTouchEvent(translated);
                    translated.recycle();
                }
                result = true;
            } else if (mDroppingEvents) {
                // If moves are small and we're already dropping events, continue dropping
                result = true;
            }
            break;
        case MotionEvent.ACTION_UP:
            if (mDroppingEvents) {
                // Send a down event first, as we dropped a bunch of sudden jumps and assume that
                // the user is releasing the touch on the second key.
                MotionEvent translated = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                        MotionEvent.ACTION_DOWN,
                        x, y, me.getMetaState());
                super.onTouchEvent(translated);
                translated.recycle();
                mDroppingEvents = false;
                // Let the up event get processed as well, result = false
            }
            break;
        }
        // Track the previous coordinate
        mLastX = x;
        mLastY = y;
        return result;
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        LatinKeyboard keyboard = (LatinKeyboard) getKeyboard();
        if (LatinIME.sKeyboardSettings.showTouchPos) {
            mLastX = (int) me.getX();
            mLastY = (int) me.getY();
            invalidate();
        }

        // If there was a sudden jump, return without processing the actual motion event.
        // The sudden-jump filter is for old screens that reported two fingers as one jumping
        // pointer. On distinct multi-touch screens it misread fast swipes as two taps and
        // typed the keys at both ends (AOSP's keyboard keeps this hack off too).
        if (!hasDistinctMultitouch()
                && handleSuddenJump(me)) return true;
        // Reset any bounding box controls in the keyboard
        if (me.getAction() == MotionEvent.ACTION_DOWN) {
            keyboard.keyReleased();
        }

        if (me.getAction() == MotionEvent.ACTION_UP) {
            int languageDirection = keyboard.getLanguageChangeDirection();
            if (languageDirection != 0) {
                getOnKeyboardActionListener().onKey(
                        languageDirection == 1 ? KEYCODE_NEXT_LANGUAGE : KEYCODE_PREV_LANGUAGE,
                        null, mLastX, mLastY);
                me.setAction(MotionEvent.ACTION_CANCEL);
                keyboard.keyReleased();
                return super.onTouchEvent(me);
            }
        }

        return super.onTouchEvent(me);
    }

    /****************************  INSTRUMENTATION  *******************************/

    private int mLastX;
    private int mLastY;
    private Paint mPaint;

    @Override
    public void draw(Canvas c) {
        LatinIMEUtil.GCUtils.getInstance().reset();
        boolean tryGC = true;
        for (int i = 0; i < LatinIMEUtil.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
            try {
                super.draw(c);
                tryGC = false;
            } catch (OutOfMemoryError e) {
                tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait("LatinKeyboardView", e);
            }
        }
        if (LatinIME.sKeyboardSettings.showTouchPos) {
            if (mPaint == null) {
                mPaint = new Paint();
                mPaint.setColor(0x80FFFFFF);
                mPaint.setAntiAlias(false);
            }
            c.drawLine(mLastX, 0, mLastX, getHeight(), mPaint);
            c.drawLine(0, mLastY, getWidth(), mLastY, mPaint);
        }
    }
}
