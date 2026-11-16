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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kr.jcb1ee.hackerskeyboard2.suggest.DictionaryCatalog;
import kr.jcb1ee.hackerskeyboard2.suggest.DictionaryFiles;

/**
 * Word suggestion dictionaries per language: English, Hebrew and Arabic are in the APK; for
 * the others "Download" opens the published file in the browser (the app has no INTERNET
 * permission) and "Browse" imports a file through the system picker (no storage permission),
 * see {@link DictionaryFiles}. The user's keyboard languages come first. Each row shows the
 * dictionary's source and licence.
 */
public class DictionariesActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (icicle == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new DictionariesFragment())
                    .commit();
        }
        SystemBars.padContent(this); // edge to edge (Android 15+): keep clear of the bars
    }

    public static class DictionariesFragment extends PreferenceFragmentCompat {
        private static final String STATE_PENDING = "pending";
        private final ExecutorService mIo = Executors.newSingleThreadExecutor();
        private final Handler mMain = new Handler(Looper.getMainLooper());
        /** The dictionary the picker was opened for. */
        private String mPending;
        private ActivityResultLauncher<String[]> mPicker;
        private final List<DictionaryPreference> mRows = new ArrayList<>();

        @Override
        public void onCreate(Bundle state) {
            super.onCreate(state);
            if (state != null) mPending = state.getString(STATE_PENDING);
            mPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    this::picked);
        }

        @Override
        public void onSaveInstanceState(Bundle out) {
            super.onSaveInstanceState(out);
            out.putString(STATE_PENDING, mPending);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mIo.shutdown();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = requireContext();
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
            setPreferenceScreen(screen);
            Preference intro = new Preference(context);
            intro.setSummary(R.string.dictionaries_intro);
            intro.setSelectable(false);
            intro.setIconSpaceReserved(false);
            screen.addPreference(intro);

            Set<DictionaryCatalog.Entry> mine = keyboardLanguages(context);
            List<DictionaryCatalog.Entry> others = new ArrayList<>();
            for (DictionaryCatalog.Entry e : DictionaryCatalog.all()) {
                if (!mine.contains(e)) others.add(e);
            }
            others.sort((a, b) -> java.text.Collator.getInstance().compare(a.label, b.label));
            addCategory(screen, R.string.dictionaries_your_languages, mine);
            addCategory(screen, R.string.dictionaries_other_languages, others);
        }

        private void addCategory(PreferenceScreen screen, int title,
                Iterable<DictionaryCatalog.Entry> entries) {
            if (!entries.iterator().hasNext()) return;
            PreferenceCategory category = new PreferenceCategory(requireContext());
            category.setTitle(title);
            category.setIconSpaceReserved(false);
            screen.addPreference(category);
            for (DictionaryCatalog.Entry e : entries) {
                DictionaryPreference row = new DictionaryPreference(requireContext(), e, this);
                category.addPreference(row);
                mRows.add(row);
            }
        }

        /** The dictionaries of the input languages the user picked, in their order. */
        static Set<DictionaryCatalog.Entry> keyboardLanguages(Context context) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            String langs = LanguageSwitcher.normalizeLanguages(
                    sp.getString(LatinIME.PREF_SELECTED_LANGUAGES, ""));
            Set<DictionaryCatalog.Entry> out = new LinkedHashSet<>();
            for (String l : langs.split(",")) {
                if (l.isEmpty()) continue;
                DictionaryCatalog.Entry e = DictionaryCatalog.forLocale(l);
                if (e != null) out.add(e);
            }
            return out;
        }

        void download(DictionaryCatalog.Entry e) {
            String url = e.downloadUrl();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException ex) {
                toast(getString(R.string.dictionary_no_browser, url));
            }
        }

        void browse(DictionaryCatalog.Entry e) {
            mPending = e.name;
            try {
                // Any type: a .dict is application/octet-stream at best.
                mPicker.launch(new String[] {"*/*"});
            } catch (ActivityNotFoundException ex) {
                mPending = null;
            }
        }

        void remove(DictionaryCatalog.Entry e) {
            if (DictionaryFiles.remove(requireContext(), e.name)) {
                changed();
                toast(getString(R.string.dictionary_removed, e.label));
            }
        }

        private void picked(Uri uri) {
            DictionaryCatalog.Entry e = mPending != null ? DictionaryCatalog.byName(mPending) : null;
            mPending = null;
            if (uri == null || e == null) return;
            Context app = requireContext().getApplicationContext();
            toast(getString(R.string.dictionary_importing));
            mIo.execute(() -> {
                String message;
                boolean ok = false;
                try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new java.io.IOException("no stream");
                    DictionaryFiles.install(app, e, in);
                    ok = true;
                    message = app.getString(R.string.dictionary_imported, e.label);
                } catch (DictionaryFiles.InvalidDictionaryException ex) {
                    message = problemText(app, ex);
                } catch (java.io.IOException | SecurityException ex) {
                    message = app.getString(R.string.dictionary_error_io);
                }
                final String text = message;
                final boolean imported = ok;
                mMain.post(() -> {
                    Toast.makeText(app, text, Toast.LENGTH_LONG).show();
                    if (imported && isAdded()) changed();
                });
            });
        }

        static String problemText(Context c, DictionaryFiles.InvalidDictionaryException ex) {
            switch (ex.problem) {
                case BAD_SIZE:
                    return c.getString(R.string.dictionary_error_size);
                case WRONG_LANGUAGE:
                    return c.getString(R.string.dictionary_error_language, ex.getMessage());
                case NOT_A_DICTIONARY:
                    return c.getString(R.string.dictionary_error_not_a_dictionary);
                default:
                    return c.getString(R.string.dictionary_error_io);
            }
        }

        /** A dictionary was imported or removed: tell the keyboard, show the new status. */
        private void changed() {
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .putLong(DictionaryFiles.PREF_CHANGED, System.currentTimeMillis()).apply();
            for (DictionaryPreference row : mRows) row.refresh();
        }

        private void toast(String text) {
            Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    /** One language: its name, status, source and licence, and buttons. */
    static final class DictionaryPreference extends Preference {
        final DictionaryCatalog.Entry mEntry;
        private final DictionariesFragment mOwner;

        DictionaryPreference(Context context, DictionaryCatalog.Entry entry,
                DictionariesFragment owner) {
            super(context);
            mEntry = entry;
            mOwner = owner;
            setLayoutResource(R.layout.pref_dictionary);
            setKey("dictionary_" + entry.name);
            setPersistent(false);
            setSelectable(false);
            setTitle(entry.label + " [" + entry.name + "]");
            setSummary(entry.attribution);
        }

        void refresh() {
            notifyChanged();
        }

        /** The status line: included, installed (published file or not), not installed. */
        String status() {
            Context c = getContext();
            if (mEntry.bundled) return c.getString(R.string.dictionary_status_bundled);
            if (!DictionaryFiles.isImported(c, mEntry.name)) {
                return c.getString(R.string.dictionary_status_not_installed);
            }
            java.util.Map<String, String> h = DictionaryFiles.header(c, mEntry.name);
            String version = h != null && h.get("version") != null ? " · v" + h.get("version") : "";
            return c.getString(DictionaryFiles.isPublished(c, mEntry)
                    ? R.string.dictionary_status_installed
                    : R.string.dictionary_status_installed_other) + version;
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);
            holder.itemView.setClickable(false);
            ((TextView) holder.findViewById(R.id.dictionary_status)).setText(status());
            View buttons = holder.findViewById(R.id.dictionary_buttons);
            buttons.setVisibility(mEntry.bundled ? View.GONE : View.VISIBLE);
            if (mEntry.bundled) return;
            boolean installed = DictionaryFiles.isImported(getContext(), mEntry.name);
            View remove = holder.findViewById(R.id.dictionary_remove);
            remove.setVisibility(installed ? View.VISIBLE : View.GONE);
            remove.setOnClickListener(v -> mOwner.remove(mEntry));
            holder.findViewById(R.id.dictionary_download)
                    .setOnClickListener(v -> mOwner.download(mEntry));
            holder.findViewById(R.id.dictionary_browse)
                    .setOnClickListener(v -> mOwner.browse(mEntry));
        }
    }
}
