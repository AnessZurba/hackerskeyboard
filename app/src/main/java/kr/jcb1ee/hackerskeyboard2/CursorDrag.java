/*
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

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.InputConnection;

/**
 * One trackpad drag (space bar, or the selection panel's Select button) moving the cursor
 * or the selection's moving end.
 *
 * <p>The text is read once, when the drag starts, and the cursor is tracked here from then
 * on: reading the selection back after every move is slow, and many editors answer with the
 * selection from before the last move, which made the cursor stutter back and forth (and
 * lose the selection). Sideways moves are worked out here and set directly. Up/down are
 * arrow keys, since only the editor knows where its lines wrap; while one is in flight the
 * following moves wait for the editor to report where it landed.
 *
 * <p>An up arrow on the first line (down on the last) jumps to the start (end) of the text;
 * such a jump is undone, so dragging past the top or bottom just stops there.
 */
final class CursorDrag {
    private static final long KEY_TIMEOUT_MS = 150;

    private final LatinIME mIme;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mKeyTimeout = this::onKeyTimeout;

    private boolean mActive;
    private boolean mSelect;
    private boolean mMultiLine;
    private boolean mMonitoring; // asked the editor for its cursor position
    private String mText;
    private int mOffset;          // where mText starts in the editor
    private int mAnchor, mFocus;  // in mText

    // An arrow key in flight
    private boolean mWaiting;
    private boolean mWaitUp;
    private int mWaitFrom;        // the moving end before the key
    private float mWaitTop;       // its line's top on screen before the key, or NaN
    private boolean mVerifying;   // landed on the very start/end: same line as before?
    private int mQueuedChars, mQueuedLines;

    private float mMarkerTop = Float.NaN; // insertion marker top, from the editor
    private int mMarkerPos = -1;          // the position (in mText) it belongs to

    /** Follows the drag (the cursor loupe). */
    interface Listener {
        void onDragMoved(String text, int anchor, int focus);

        /** The cursor's place on screen, when the app reports it. */
        void onCaretOnScreen(float x, float top, float bottom);

        void onDragEnded();
    }

    private Listener mListener;

    CursorDrag(LatinIME ime) {
        mIme = ime;
    }

    void setListener(Listener listener) {
        mListener = listener;
    }

    private boolean mQuiet;       // no loupe (single toolbar arrow presses)
    private boolean mEndPending;  // ended while an arrow key was in flight
    private boolean mRestore;     // the editor dropped the selection: put it back

    private void moved() {
        if (mListener != null && mActive && !mQuiet && !mEndPending) {
            mListener.onDragMoved(mText, mAnchor, mFocus);
        }
    }

    boolean isActive() {
        return mActive;
    }

    /**
     * Starts a drag. Returns false when the editor doesn't expose its text (terminals): the
     * caller sends arrow keys instead.
     */
    boolean begin(boolean select) {
        return begin(select, false);
    }

    /** {@code quiet}: a single step (toolbar arrows), no loupe. */
    boolean begin(boolean select, boolean quiet) {
        if (mActive && mEndPending && mSelect == select) {
            // The last step's arrow key is still in flight: carry on from it.
            mEndPending = false;
            mQuiet = quiet;
            return true;
        }
        finish();
        InputConnection ic = mIme.getCurrentInputConnection();
        EditorInfo ei = mIme.getCurrentInputEditorInfo();
        if (ic == null || ei == null) return false;
        mIme.commitTypedForCursor(ic);
        ExtractedText et = mIme.extractText(ic);
        if (et == null) return false;
        int[] from = mIme.selectionToExtend(et, select);
        mText = et.text.toString();
        mOffset = et.startOffset;
        mAnchor = clamp(from[0]);
        mFocus = clamp(from[1]);
        mSelect = select;
        mMultiLine = (ei.inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT
                && (ei.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        mWaiting = mVerifying = false;
        mEndPending = mRestore = false;
        mQuiet = quiet;
        mQueuedChars = mQueuedLines = 0;
        mMarkerTop = Float.NaN;
        mMarkerPos = -1;
        mActive = true;
        mMonitoring = mMultiLine || mListener != null;
        if (mMonitoring) {
            try {
                ic.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE
                        | InputConnection.CURSOR_UPDATE_MONITOR);
            } catch (RuntimeException ignored) {
                // Optional: without it, jumps are recognised from the text alone.
            }
        }
        moved();
        return true;
    }

    /** Ends the drag (after the arrow key in flight, if any, has landed). */
    void end() {
        if (!mActive) return;
        if (mWaiting) {
            // Finish after the key in flight, and the moves queued behind it, have landed.
            mEndPending = true;
            if (mListener != null) mListener.onDragEnded();
            return;
        }
        finish();
    }

    /** Ends the drag now (the input is gone). */
    void cancel() {
        finish();
    }

    private void finish() {
        if (!mActive) return;
        mActive = false;
        mEndPending = false;
        mHandler.removeCallbacks(mKeyTimeout);
        mWaiting = mVerifying = false;
        InputConnection ic = mIme.getCurrentInputConnection();
        if (mListener != null) mListener.onDragEnded();
        if (ic != null && mMonitoring) {
            try {
                ic.requestCursorUpdates(0);
            } catch (RuntimeException ignored) {
            }
        }
        if (mSelect) {
            mIme.rememberSelection(mOffset + mAnchor, mOffset + mFocus);
        } else {
            mIme.forgetSelection();
        }
    }

    /** Moves by {@code n} characters (negative = left); returns whether anything moved. */
    boolean chars(int n) {
        if (!mActive || n == 0) return false;
        if (mWaiting) {
            mQueuedChars += n;
            return true;
        }
        return applyChars(n);
    }

    /** Moves by {@code n} lines (negative = up), only in multi-line fields. */
    boolean lines(int n) {
        if (!mActive || n == 0 || !mMultiLine) return false;
        if (mWaiting) {
            mQueuedLines += n;
            return true;
        }
        int step = n < 0 ? -1 : 1;
        if (!sendLine(n < 0)) return false;
        mQueuedLines += n - step;
        return true;
    }

    private boolean applyChars(int n) {
        int[] sel = LatinIME.newSelection(mText, mAnchor, mFocus, n, mSelect, false);
        if (sel[0] == mAnchor && sel[1] == mFocus) return false;
        set(sel[0], sel[1]);
        return true;
    }

    private void set(int anchor, int focus) {
        mAnchor = anchor;
        mFocus = focus;
        InputConnection ic = mIme.getCurrentInputConnection();
        if (ic != null) ic.setSelection(mOffset + anchor, mOffset + focus);
        moved();
    }

    private boolean sendLine(boolean up) {
        if (up ? mFocus == 0 : mFocus == mText.length()) return false; // nowhere to go
        InputConnection ic = mIme.getCurrentInputConnection();
        if (ic == null) return false;
        mWaiting = true;
        mWaitUp = up;
        mWaitFrom = mFocus;
        mWaitTop = mMarkerPos == mFocus ? mMarkerTop : Float.NaN;
        int code = up ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
        int meta = mSelect ? KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON : 0;
        long now = SystemClock.uptimeMillis();
        int flags = KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta, -1, 0, flags));
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta, -1, 0, flags));
        mHandler.removeCallbacks(mKeyTimeout);
        mHandler.postDelayed(mKeyTimeout, KEY_TIMEOUT_MS);
        return true;
    }

    /**
     * The editor's selection changed (absolute offsets). Returns true while a drag is on, so
     * the keyboard skips its usual reaction (suggestions) to each step.
     */
    boolean onUpdateSelection(int start, int end) {
        if (!mActive) return false;
        if (!mWaiting || mVerifying) return true;
        int s = start - mOffset, e = end - mOffset;
        if (s < 0 || e < 0 || s > mText.length() || e > mText.length()) {
            resync();
            return true;
        }
        // The report of the selection from before the key: not the key's result yet.
        if (Math.min(s, e) == Math.min(mAnchor, mFocus) && Math.max(s, e) == Math.max(mAnchor, mFocus)) {
            return true;
        }
        int focus;
        if (!mSelect) {
            focus = e;
        } else if (s == mAnchor) {
            focus = e;
        } else if (e == mAnchor) {
            focus = s;
        } else if (s == e) {
            // Some editors drop the selection on Shift+arrow: keep it, put it back.
            focus = s;
            mRestore = true;
        } else {
            // The editor normalised the order, or chose its own anchor.
            focus = Math.abs(s - mWaitFrom) < Math.abs(e - mWaitFrom) ? s : e;
            int other = focus == s ? e : s;
            if (other != mAnchor) mAnchor = other;
        }
        landed(focus);
        return true;
    }

    /** Where the arrow key took the moving end. */
    private void landed(int focus) {
        landedAt(focus);
        moved();
    }

    private void landedAt(int focus) {
        boolean atEdge = mWaitUp ? focus == 0 : focus == mText.length();
        boolean fromEdgeLine = mWaitUp ? mText.lastIndexOf('\n', mWaitFrom - 1) < 0
                : mText.indexOf('\n', mWaitFrom) < 0;
        mFocus = focus;
        if (!mSelect) mAnchor = focus;
        if (atEdge && fromEdgeLine) {
            if (!Float.isNaN(mWaitTop)) {
                // Wait for the editor's cursor position: same line = a jump, not a line.
                mVerifying = true;
                mHandler.removeCallbacks(mKeyTimeout);
                mHandler.postDelayed(mKeyTimeout, KEY_TIMEOUT_MS);
                return;
            }
            undoJump();
        }
        keyDone();
    }

    private void undoJump() {
        set(mSelect ? mAnchor : mWaitFrom, mWaitFrom);
        mQueuedLines = 0; // dragging further the same way would only jump again
    }

    /** The editor's cursor position on screen, while monitoring (multi-line fields). */
    void onUpdateCursorAnchorInfo(CursorAnchorInfo info) {
        if (!mActive) return;
        if (mListener != null && !Float.isNaN(info.getInsertionMarkerHorizontal())) {
            float[] pts = {info.getInsertionMarkerHorizontal(), info.getInsertionMarkerTop(),
                    info.getInsertionMarkerHorizontal(), info.getInsertionMarkerBottom()};
            info.getMatrix().mapPoints(pts); // editor -> screen
            mListener.onCaretOnScreen(pts[0], pts[1], pts[3]);
        }
        // The marker sits at the selection's start in some editors: only use it without one.
        if (info.getSelectionStart() != info.getSelectionEnd()) {
            mMarkerPos = -1;
            return;
        }
        mMarkerTop = info.getInsertionMarkerTop();
        mMarkerPos = info.getSelectionStart() - mOffset;
        if (mVerifying && mMarkerPos == mFocus) {
            // Still on the line it started from: the key jumped to the start/end.
            mVerifying = false;
            if (Float.isNaN(mMarkerTop) || Math.abs(mMarkerTop - mWaitTop) < 1f) undoJump();
            keyDone();
        }
    }

    private void onKeyTimeout() {
        if (!mActive || !mWaiting) return;
        if (mVerifying) {
            // No position from the editor: go by the text alone.
            mVerifying = false;
            undoJump();
            keyDone();
            return;
        }
        // No report: the key didn't move the cursor, or the editor is slow to say. Ask.
        resync();
    }

    /** Reads the selection back from the editor (the text itself doesn't change). */
    private void resync() {
        InputConnection ic = mIme.getCurrentInputConnection();
        ExtractedText et = ic == null ? null : mIme.extractText(ic);
        if (et != null) {
            mText = et.text.toString();
            mOffset = et.startOffset;
            int s = clamp(et.selectionStart), e = clamp(et.selectionEnd);
            if (mSelect && s == e && s != mAnchor) {
                mFocus = s;      // the selection was dropped: put it back
                mRestore = true;
            } else if (mSelect && e == mAnchor && s != mAnchor) {
                mFocus = s;
            } else {
                if (mSelect && s != mAnchor && e != mAnchor) mAnchor = s;
                mFocus = e;
            }
            if (!mSelect) mAnchor = mFocus;
            moved();
        }
        keyDone();
    }

    private void keyDone() {
        mWaiting = false;
        mHandler.removeCallbacks(mKeyTimeout);
        if (mRestore) {
            mRestore = false;
            if (mSelect && mAnchor != mFocus) set(mAnchor, mFocus);
        }
        if (mQueuedLines != 0) {
            int n = mQueuedLines;
            mQueuedLines = 0;
            lines(n);
            if (mWaiting) return; // queued characters wait for that line
        }
        if (mQueuedChars != 0) {
            int n = mQueuedChars;
            mQueuedChars = 0;
            applyChars(n);
        }
        if (mEndPending) finish();
    }

    private int clamp(int pos) {
        return Math.max(0, Math.min(pos, mText.length()));
    }
}
