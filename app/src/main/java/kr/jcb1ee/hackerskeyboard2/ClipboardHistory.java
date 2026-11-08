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

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.PersistableBundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Recent clipboard texts for the toolbar's clipboard panel. The keyboard, as the default
 * input method, may read the clipboard while it is in use; each new text clip is added
 * here. Clips that apps mark as sensitive (password managers, Android 13+) are skipped.
 * Pinned clips are kept until unpinned; the others are capped at {@link #MAX_UNPINNED}.
 */
final class ClipboardHistory implements ClipboardManager.OnPrimaryClipChangedListener {
    private static final String TAG = "HK/ClipboardHistory";
    static final String PREF_ENABLED = "pref_clipboard_history";
    private static final String FILE = "clipboard_history.json";
    private static final int MAX_UNPINNED = 50;
    private static final int MAX_LENGTH = 20000;
    private static final String EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE";

    static final class Clip {
        final String text;
        boolean pinned;

        Clip(String text, boolean pinned) {
            this.text = text;
            this.pinned = pinned;
        }
    }

    interface Listener {
        void onClipboardHistoryChanged();
    }

    private static ClipboardHistory sInstance;

    static synchronized ClipboardHistory get(Context context) {
        if (sInstance == null) sInstance = new ClipboardHistory(context.getApplicationContext());
        return sInstance;
    }

    private final Context mContext;
    private final List<Clip> mClips = new ArrayList<>(); // newest first
    private boolean mLoaded;
    private boolean mEnabled = true;
    private boolean mListening;
    private Listener mListener;

    private ClipboardHistory(Context context) {
        mContext = context;
    }

    void setEnabled(boolean enabled) {
        mEnabled = enabled;
        ClipboardManager cm = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        if (enabled && !mListening) {
            cm.addPrimaryClipChangedListener(this);
            mListening = true;
        } else if (!enabled && mListening) {
            cm.removePrimaryClipChangedListener(this);
            mListening = false;
        }
    }

    boolean isEnabled() {
        return mEnabled;
    }

    void setListener(Listener listener) {
        mListener = listener;
    }

    List<Clip> clips() {
        ensureLoaded();
        return mClips;
    }

    @Override
    public void onPrimaryClipChanged() {
        if (!mEnabled) return;
        try {
            ClipboardManager cm = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm == null ? null : cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            ClipDescription description = clip.getDescription();
            if (description != null) {
                PersistableBundle extras = description.getExtras();
                if (extras != null && extras.getBoolean(EXTRA_IS_SENSITIVE, false)) return;
            }
            CharSequence text = clip.getItemAt(0).coerceToText(mContext);
            if (text == null) return;
            add(text.toString());
        } catch (RuntimeException e) {
            // Reading the clipboard can be refused when the keyboard is not in use.
            Log.i(TAG, "Could not read the clipboard: " + e);
        }
    }

    void add(String text) {
        if (text.trim().isEmpty() || text.length() > MAX_LENGTH) return;
        ensureLoaded();
        boolean pinned = false;
        for (Iterator<Clip> it = mClips.iterator(); it.hasNext(); ) {
            Clip c = it.next();
            if (c.text.equals(text)) {
                pinned = c.pinned;
                it.remove();
            }
        }
        mClips.add(0, new Clip(text, pinned));
        trim();
        changed();
    }

    void togglePin(Clip clip) {
        clip.pinned = !clip.pinned;
        changed();
    }

    void remove(Clip clip) {
        mClips.remove(clip);
        changed();
    }

    void setPinned(java.util.Collection<Clip> clips, boolean pinned) {
        for (Clip c : clips) c.pinned = pinned;
        changed();
    }

    void removeAll(java.util.Collection<Clip> clips) {
        mClips.removeAll(clips);
        changed();
    }

    /** Removes every clip that is not pinned. */
    void clearUnpinned() {
        ensureLoaded();
        for (Iterator<Clip> it = mClips.iterator(); it.hasNext(); ) {
            if (!it.next().pinned) it.remove();
        }
        changed();
    }

    private void trim() {
        int unpinned = 0;
        for (Iterator<Clip> it = mClips.iterator(); it.hasNext(); ) {
            Clip c = it.next();
            if (!c.pinned && ++unpinned > MAX_UNPINNED) it.remove();
        }
    }

    private void changed() {
        save();
        if (mListener != null) mListener.onClipboardHistoryChanged();
    }

    private void ensureLoaded() {
        if (mLoaded) return;
        mLoaded = true;
        File file = new File(mContext.getFilesDir(), FILE);
        if (!file.exists()) return;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int off = 0, n;
            while (off < bytes.length && (n = in.read(bytes, off, bytes.length - off)) > 0) off += n;
            JSONArray array = new JSONArray(new String(bytes, 0, off, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                mClips.add(new Clip(o.getString("t"), o.optBoolean("p")));
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read clipboard history", e);
        }
    }

    private void save() {
        try {
            JSONArray array = new JSONArray();
            for (Clip c : mClips) {
                JSONObject o = new JSONObject();
                o.put("t", c.text);
                if (c.pinned) o.put("p", true);
                array.put(o);
            }
            File file = new File(mContext.getFilesDir(), FILE);
            File tmp = new File(mContext.getFilesDir(), FILE + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(array.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(file)) Log.w(TAG, "Could not replace " + file);
        } catch (Exception e) {
            Log.w(TAG, "Could not save clipboard history", e);
        }
    }

    /** Forgets the whole history, pinned clips included. */
    void clearAll() {
        mClips.clear();
        mLoaded = true;
        //noinspection ResultOfMethodCallIgnored
        new File(mContext.getFilesDir(), FILE).delete();
        if (mListener != null) mListener.onClipboardHistoryChanged();
    }
}
