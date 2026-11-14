package kr.jcb1ee.hackerskeyboard2;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;

import java.util.Locale;

/**
 * Resources for one input language (layouts, dictionaries, separators) without changing the
 * app's own language: a configuration context for that locale. The old way switched the whole
 * app's Configuration.locale and back (updateConfiguration), which anything reading a string in
 * between (toolbar, settings, toasts) could catch in the wrong language. The pseudo-region
 * locales that select layout variants (de_NE, cs_QY, ...) work the same way.
 */
final class LocaleContext {
    private LocaleContext() {
    }

    static Context of(Context base, Locale locale) {
        Configuration conf = new Configuration(base.getResources().getConfiguration());
        conf.setLocales(new LocaleList(locale));
        return base.createConfigurationContext(conf);
    }

    /** "de_NE" -> Locale("de", "NE"); "iw" -> Locale("iw"). */
    static Locale parse(String name) {
        String[] parts = name.split("_", 3);
        return parts.length == 1 ? new Locale(parts[0])
                : parts.length == 2 ? new Locale(parts[0], parts[1])
                : new Locale(parts[0], parts[1], parts[2]);
    }
}
