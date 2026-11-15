package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.nio.file.Files;

import kr.jcb1ee.hackerskeyboard2.suggest.DictionaryCatalog;
import kr.jcb1ee.hackerskeyboard2.suggest.DictionaryFiles;
import kr.jcb1ee.hackerskeyboard2.suggest.SuggestTestSupport;

/**
 * The Dictionaries screen: the user's languages first, Download opens the published file's
 * URL in a browser, Browse opens the system picker and imports what was picked, Remove.
 */
@RunWith(RobolectricTestRunner.class)
// The import opens the file with the native decoder: the suggest tests' config (one class
// loader per native library).
@Config(sdk = 36, qualifiers = SuggestTestSupport.QUALIFIERS)
public class DictionariesActivityTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private ActivityController<DictionariesActivity> mController;
    private DictionariesActivity.DictionariesFragment mFragment;

    @Before
    public void open() {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(LatinIME.PREF_SELECTED_LANGUAGES, "en_US,de_NE,pt_PT,ko").commit();
        mController = Robolectric.buildActivity(DictionariesActivity.class).setup();
        idle();
        mFragment = (DictionariesActivity.DictionariesFragment) mController.get()
                .getSupportFragmentManager().findFragmentById(android.R.id.content);
    }

    @After
    public void close() {
        mController.pause().stop().destroy();
        for (DictionaryCatalog.Entry e : DictionaryCatalog.all()) DictionaryFiles.remove(mContext, e.name);
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private DictionariesActivity.DictionaryPreference row(String name) {
        DictionariesActivity.DictionaryPreference p =
                mFragment.findPreference("dictionary_" + name);
        assertNotNull(name, p);
        return p;
    }

    @Test
    public void listsTheKeyboardLanguagesFirst() {
        PreferenceScreen screen = mFragment.getPreferenceScreen();
        PreferenceCategory mine = (PreferenceCategory) screen.getPreference(1);
        assertEquals(mContext.getString(R.string.dictionaries_your_languages), mine.getTitle());
        assertEquals(3, mine.getPreferenceCount()); // ko has no dictionary
        assertEquals("dictionary_en", mine.getPreference(0).getKey());
        assertEquals("dictionary_de", mine.getPreference(1).getKey());
        assertEquals("dictionary_pt_pt", mine.getPreference(2).getKey());
        PreferenceCategory others = (PreferenceCategory) screen.getPreference(2);
        assertEquals(DictionaryCatalog.all().size() - 3, others.getPreferenceCount());

        assertEquals(mContext.getString(R.string.dictionary_status_bundled), row("en").status());
        assertEquals(mContext.getString(R.string.dictionary_status_not_installed),
                row("de").status());
        assertTrue(String.valueOf(row("uk").getSummary()).contains("CC BY 4.0"));
        assertTrue(String.valueOf(row("de").getSummary()).contains("Apache License 2.0"));
    }

    @Test
    public void downloadOpensThePublishedFileInTheBrowser() {
        mFragment.download(DictionaryCatalog.byName("de"));
        Intent i = Shadows.shadowOf(mController.get()).getNextStartedActivity();
        assertEquals(Intent.ACTION_VIEW, i.getAction());
        assertEquals(Uri.parse(DictionaryCatalog.DOWNLOAD_BASE_URL + "main_de.dict"), i.getData());
    }

    @Test
    public void browseImportsThePickedFile() throws Exception {
        pick("en", new File("src/main/assets/dicts/main_en.dict"));
        assertTrue(DictionaryFiles.isImported(mContext, "en"));
        assertEquals(mContext.getString(R.string.dictionary_imported, "English"),
                ShadowToast.getTextOfLatestToast());
        assertTrue(PreferenceManager.getDefaultSharedPreferences(mContext)
                .contains(DictionaryFiles.PREF_CHANGED));

        mFragment.remove(DictionaryCatalog.byName("en"));
        assertFalse(DictionaryFiles.isImported(mContext, "en"));
    }

    @Test
    public void browseRejectsAnotherLanguagesFile() throws Exception {
        pick("de", new File("src/main/assets/dicts/main_iw.dict"));
        assertFalse(DictionaryFiles.isImported(mContext, "de"));
        assertEquals(mContext.getString(R.string.dictionary_error_language, "iw"),
                ShadowToast.getTextOfLatestToast());
        assertEquals(mContext.getString(R.string.dictionary_status_not_installed),
                row("de").status());
    }

    @Test
    public void browseRejectsAFileThatIsNoDictionary() throws Exception {
        File junk = File.createTempFile("junk", ".dict");
        Files.write(junk.toPath(), new byte[20_000]);
        pick("de", junk);
        assertFalse(DictionaryFiles.isImported(mContext, "de"));
        assertEquals(mContext.getString(R.string.dictionary_error_not_a_dictionary),
                ShadowToast.getTextOfLatestToast());
    }

    /** Browse for {@code name}, pick {@code file} in the picker, wait for the import. */
    private void pick(String name, File file) throws InterruptedException {
        Activity a = mController.get();
        mFragment.browse(DictionaryCatalog.byName(name));
        ShadowActivity shadow = Shadows.shadowOf(a);
        ShadowActivity.IntentForResult picker = shadow.getNextStartedActivityForResult();
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.intent.getAction());
        String toasts = ShadowToast.getTextOfLatestToast();
        shadow.receiveResult(picker.intent, Activity.RESULT_OK,
                new Intent().setData(Uri.fromFile(file.getAbsoluteFile())));
        // The copy runs on a background thread; its result comes back as a toast.
        for (int i = 0; i < 200; i++) {
            idle();
            String latest = ShadowToast.getTextOfLatestToast();
            if (latest != null && !latest.equals(toasts)
                    && !latest.equals(mContext.getString(R.string.dictionary_importing))) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("no result");
    }
}
