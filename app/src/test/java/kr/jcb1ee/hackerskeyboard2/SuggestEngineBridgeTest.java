package kr.jcb1ee.hackerskeyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import kr.jcb1ee.hackerskeyboard2.suggest.SuggestTestSupport;

/** The old Suggest path with the new engine as its main dictionary (EngineDictionary). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = SuggestTestSupport.QUALIFIERS)
public class SuggestEngineBridgeTest {
    private static WordComposer typed(String word) {
        WordComposer w = new WordComposer();
        for (char c : word.toCharArray()) w.add(c, new int[] {c});
        return w;
    }

    @Test
    public void suggestUsesTheEngine() {
        Suggest suggest = new Suggest(ApplicationProvider.getApplicationContext(),
                new int[] {R.raw.main});
        // The bundled old-format dictionary is a placeholder: no suggestions without the engine.
        assertFalse(suggest.hasMainDictionary());
        suggest.setMainEngine(SuggestTestSupport.englishEngine());
        suggest.setCorrectionMode(Suggest.CORRECTION_FULL);
        try {
            assertTrue(suggest.hasMainDictionary());
            assertTrue(suggest.isValidWord("hello"));
            assertFalse(suggest.isValidWord("helo"));
            List<CharSequence> s = suggest.getSuggestions(null, typed("helo"), false, null);
            // Suggest puts the typed word first, then the corrections.
            assertEquals("helo", s.get(0).toString());
            assertEquals("hello", s.get(1).toString());
            assertTrue(suggest.hasMinimalCorrection());
        } finally {
            suggest.close();
        }
    }
}
