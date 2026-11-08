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
 * Pinned clips are kept until unpinned; the others are capped at {@link #MAX_UNPINNED} and
 * go after {@link #MAX_AGE_MS} (a day).
 */
final class ClipboardHistory implements ClipboardManager.OnPrimaryClipChangedListener {
    private static final String TAG = "HK/ClipboardHistory";
    static final String PREF_ENABLED = "pref_clipboard_history";
    private static final String FILE = "clipboard_history.json";
    private static final int MAX_UNPINNED = 50;
    /** Unpinned clips go after a day. */
    static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;
    private static final int MAX_UNPINNED_IMAGES = 20; // images take space: fewer of them
    private static final int MAX_LENGTH = 20000;

    static final class Clip {
        final String text;
        boolean pinned;
        /** An image clip: its file in {@link #imageDir}, and its type; null for text. */
        final String image;
        final String mime;
        /** When it was copied (System.currentTimeMillis), for the day-long expiry. */
        long time = System.currentTimeMillis();

        Clip(String text, boolean pinned) {
            this(text, pinned, null, null);
        }

        Clip(String text, boolean pinned, String image, String mime) {
            this.text = text;
            this.pinned = pinned;
            this.image = image;
            this.mime = mime;
        }

        boolean isImage() {
            return image != null;
        }
    }

    private static final long MAX_IMAGE_BYTES = 15L * 1024 * 1024;
    private final java.util.concurrent.Executor mIo = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.os.Handler mMain = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Where copied images are kept (private; shared with apps only through the FileProvider). */
    static File imageDir(Context context) {
        File dir = new File(context.getFilesDir(), "clip_images");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    File imageFile(Clip clip) {
        return new File(imageDir(mContext), clip.image);
    }

    interface Listener {
        void onClipboardHistoryChanged();
    }

    private static ClipboardHistory sInstance;

    static synchronized ClipboardHistory get(Context context) {
        if (sInstance == null) sInstance = new ClipboardHistory(context.getApplicationContext());
        return sInstance;
    }

    /** Drops the instance (tests: each runs with a fresh app). */
    static synchronized void forget() {
        sInstance = null;
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
        if (expire()) changed();
        return mClips;
    }

    /** Drops unpinned clips older than a day; returns whether any went. */
    private boolean expire() {
        long now = System.currentTimeMillis();
        boolean any = false;
        for (Iterator<Clip> it = mClips.iterator(); it.hasNext(); ) {
            Clip c = it.next();
            if (!c.pinned && now - c.time > MAX_AGE_MS) {
                it.remove();
                deleteImage(c);
                any = true;
            }
        }
        return any;
    }

    @Override
    public void onPrimaryClipChanged() {
        capture(false);
    }

    /**
     * Picks up what was copied while the keyboard wasn't running (Android stops it in the
     * background now and then, and only the running keyboard hears about new copies):
     * the current clip, if it is newer than the last one seen. Called when typing starts.
     */
    void catchUp() {
        capture(true);
    }

    private static final String STATE = "clipboard_state", LAST_SEEN = "last_seen";

    private void capture(boolean catchingUp) {
        if (!mEnabled) return;
        try {
            ClipboardManager cm = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return;
            if (catchingUp && android.os.Build.VERSION.SDK_INT >= 26) {
                // Look at the clip's description first, and read the clip itself only when it
                // is new to us (cheap even for large clips; the default keyboard gets no
                // "pasted from your clipboard" notice either way).
                ClipDescription peek = cm.getPrimaryClipDescription();
                if (peek == null || peek.getTimestamp() <= mContext.getSharedPreferences(
                        STATE, Context.MODE_PRIVATE).getLong(LAST_SEEN, 0)) {
                    return;
                }
            }
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            ClipDescription description = clip.getDescription();
            if (description != null && android.os.Build.VERSION.SDK_INT >= 26) {
                long when = description.getTimestamp();
                android.content.SharedPreferences state =
                        mContext.getSharedPreferences(STATE, Context.MODE_PRIVATE);
                if (catchingUp && when <= state.getLong(LAST_SEEN, 0)) return; // seen it
                state.edit().putLong(LAST_SEEN, when).apply();
            } else if (catchingUp) {
                CharSequence text = clip.getItemAt(0).coerceToText(mContext);
                ensureLoaded();
                for (Clip c : mClips) if (text != null && c.text.equals(text.toString())) return;
            }
            if (description != null) {
                PersistableBundle extras = description.getExtras();
                if (extras != null && extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)) return;
            }
            android.net.Uri uri = clip.getItemAt(0).getUri();
            String imageType = imageType(description, uri);
            if (uri != null && imageType != null) {
                copyImage(uri, imageType);
                return;
            }
            CharSequence text = clip.getItemAt(0).coerceToText(mContext);
            if (text == null) return;
            add(text.toString());
        } catch (RuntimeException e) {
            // Reading the clipboard can be refused when the keyboard is not in use.
            Log.i(TAG, "Could not read the clipboard: " + e);
        }
    }

    private String imageType(ClipDescription description, android.net.Uri uri) {
        if (description != null) {
            for (int i = 0; i < description.getMimeTypeCount(); i++) {
                String type = description.getMimeType(i);
                if (type != null && type.startsWith("image/")) return type;
            }
        }
        if (uri == null) return null;
        try {
            String type = mContext.getContentResolver().getType(uri);
            return type != null && type.startsWith("image/") ? type : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Keeps a copy of a copied image: the app's link to it may stop working (the keyboard
     * may read it only while the clip is current). Named by its contents, so copying the
     * same image again moves it to the top instead of adding it twice.
     */
    private void copyImage(final android.net.Uri uri, final String type) {
        mIo.execute(() -> {
            File tmp = new File(imageDir(mContext), "incoming.tmp");
            try (java.io.InputStream in = mContext.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(tmp)) {
                if (in == null) return;
                java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-1");
                byte[] buf = new byte[64 * 1024];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_IMAGE_BYTES) return;
                    sha.update(buf, 0, n);
                    out.write(buf, 0, n);
                }
                StringBuilder name = new StringBuilder();
                for (byte b : sha.digest()) name.append(String.format("%02x", b));
                String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
                name.append('.').append(ext != null ? ext : "img");
                final String file = name.toString();
                if (!tmp.renameTo(new File(imageDir(mContext), file))) return;
                mMain.post(() -> addImage(file, type));
            } catch (Exception e) {
                Log.i(TAG, "Could not copy the clipboard image: " + e);
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        });
    }

    void addImage(String file, String type) {
        ensureLoaded();
        boolean pinned = false;
        for (Iterator<Clip> it = mClips.iterator(); it.hasNext(); ) {
            Clip c = it.next();
            if (file.equals(c.image)) {
                pinned = c.pinned;
                it.remove();
            }
        }
        mClips.add(0, new Clip("", pinned, file, type));
        trim();
        changed();
    }

    private void deleteImage(Clip clip) {
        if (clip.image == null) return;
        for (Clip c : mClips) if (c != clip && clip.image.equals(c.image)) return;
        //noinspection ResultOfMethodCallIgnored
        imageFile(clip).delete();
    }

    void add(String text) {
        if (text.trim().isEmpty() || text.length() > MAX_LENGTH) return;
        ensureLoaded();
        boolean pinned = false;
        for (Iterator<Clip> it = mClips.iterator(); it.hasNext(); ) {
            Clip c = it.next();
            if (!c.isImage() && c.text.equals(text)) {
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
        if (!clip.pinned) clip.time = System.currentTimeMillis(); // a fresh day once unpinned
        changed();
    }

    void remove(Clip clip) {
        mClips.remove(clip);
        deleteImage(clip);
        changed();
    }

    void setPinned(java.util.Collection<Clip> clips, boolean pinned) {
        for (Clip c : clips) {
            if (c.pinned && !pinned) c.time = System.currentTimeMillis(); // a fresh day
            c.pinned = pinned;
        }
        changed();
    }

    void removeAll(java.util.Collection<Clip> clips) {
        mClips.removeAll(clips);
        for (Clip c : clips) deleteImage(c);
        changed();
    }

    /** Removes every clip that is not pinned. */
    void clearUnpinned() {
        ensureLoaded();
        for (Iterator<Clip> it = mClips.iterator(); it.hasNext(); ) {
            Clip c = it.next();
            if (!c.pinned) {
                it.remove();
                deleteImage(c);
            }
        }
        changed();
    }

    private void trim() {
        expire();
        int unpinned = 0, images = 0;
        for (Iterator<Clip> it = mClips.iterator(); it.hasNext(); ) {
            Clip c = it.next();
            if (c.pinned) continue;
            boolean tooMany = unpinned >= MAX_UNPINNED
                    || (c.isImage() && images >= MAX_UNPINNED_IMAGES);
            if (!tooMany) {
                unpinned++;
                if (c.isImage()) images++;
            } else {
                it.remove();
                deleteImage(c);
            }
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
                String image = o.optString("i", null);
                Clip clip = new Clip(o.optString("t", ""), o.optBoolean("p"), image,
                        image != null ? o.optString("m", "image/*") : null);
                // Clips saved before times were kept start their day now.
                clip.time = o.optLong("w", System.currentTimeMillis());
                mClips.add(clip);
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
                o.put("w", c.time);
                if (c.pinned) o.put("p", true);
                if (c.image != null) {
                    o.put("i", c.image);
                    o.put("m", c.mime);
                }
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
        File[] images = imageDir(mContext).listFiles();
        if (images != null) for (File f : images) //noinspection ResultOfMethodCallIgnored
            f.delete();
        if (mListener != null) mListener.onClipboardHistoryChanged();
    }
}
