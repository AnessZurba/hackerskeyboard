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

package kr.jcb1ee.hackerskeyboard2.suggest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The main dictionaries there are: English, Hebrew and Arabic in the APK (assets/dicts), the
 * other layout languages' as optional files, downloaded in the browser and imported with
 * {@link DictionaryFiles}: those of AOSP's word lists from HeliBoard's dictionary repository
 * (the same lists), the rest (uk bg hu sk ca, pt_BR) made by tools/mkdicts.sh and published
 * on this repository's release. The keyboard has no INTERNET permission: it never downloads
 * anything itself.
 */
public final class DictionaryCatalog {
    /**
     * Where our own optional dictionaries are published (the languages AOSP has no list for,
     * and pt_BR): + "main_LANG.dict".
     */
    public static final String DOWNLOAD_BASE_URL =
            "https://github.com/AnessZurba/hackerskeyboard/releases/download/dicts-v1/";
    /**
     * The dictionaries made from AOSP's word lists are downloaded from HeliBoard's dictionary
     * repository (GPL-3.0 repository; these files are AOSP's Apache 2.0 lists), pinned to a
     * commit so the files and their checksums cannot change under us.
     */
    public static final String HELIBOARD_COMMIT = "ff0cd52be8d9530200fefe8b296c53d4f662091e";
    public static final String HELIBOARD_BASE_URL =
            "https://codeberg.org/Helium314/aosp-dictionaries/raw/commit/" + HELIBOARD_COMMIT
                    + "/dictionaries/";
    private static final String HELIBOARD_NOTE = " File from HeliBoard's aosp-dictionaries"
            + " (codeberg.org/Helium314/aosp-dictionaries, commit " + HELIBOARD_COMMIT.substring(0, 7)
            + ").";

    private static final String AOSP = "AOSP LatinIME word list (%s_wordlist, commit 127336e9),"
            + " Apache License 2.0, Copyright (C) The Android Open Source Project.";
    private static final String LEIPZIG = "Word frequencies from the Leipzig Corpora Collection"
            + " (Wortschatz, Leipzig University; wortschatz.uni-leipzig.de), %s, CC BY 4.0;"
            + " this dictionary is CC BY 4.0 too. Reduced to the %s most frequent words.";

    /** One dictionary: its name (file main_NAME.dict), language name and attribution. */
    public static final class Entry {
        /** "de", "pt_pt": the file is main_NAME.dict; the locale's language comes first. */
        public final String name;
        /** The language in its own words ("Deutsch"). */
        public final String label;
        /** Source and licence, as the NOTICE says. */
        public final String attribution;
        /** In the APK rather than a download. */
        public final boolean bundled;
        /** The SHA-256 of the published file (optional dictionaries), lower-case hex. */
        public final String sha256;
        /** Where to download it (optional dictionaries), or null. */
        private final String mUrl;

        Entry(String name, String label, String attribution, boolean bundled, String url,
                String sha256) {
            this.name = name;
            this.label = label;
            this.attribution = attribution;
            this.bundled = bundled;
            this.mUrl = url;
            this.sha256 = sha256;
        }

        /** The file name, in the assets, the release and the app's files. */
        public String fileName() {
            return "main_" + name + ".dict";
        }

        /** The language part of the name ("pt" for "pt_pt"). */
        public String language() {
            int sep = name.indexOf('_');
            return sep >= 0 ? name.substring(0, sep) : name;
        }

        /** Where to download it (optional dictionaries): HeliBoard's repository or ours. */
        public String downloadUrl() {
            return mUrl;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * The checksums of the files on our own release (DOWNLOAD_BASE_URL): those lines of
     * build/dicts/SHA256SUMS as tools/mkdicts.sh writes it (deterministic), pasted here;
     * DictionaryFilesTest compares them when it is there.
     */
    static final String SHA256SUMS = ""
            + "9d298f73dedc8a46bca68f6646b6d5b22a55119ec6ada49630cafcfc8fb7e57d  main_bg.dict\n"
            + "6ce5fec6741e66c1d6349a40b33f7c321fce59df5970d037bbfdc1bea0811f3f  main_ca.dict\n"
            + "3cfd4a32ae6f6b90dd0e460faf7e36ec5a5482c41b077224f628800a59e51ce0  main_hu.dict\n"
            + "01742d4d0b35dbb472314d8f119285df37c9cb0ecfe9375cd8365a2e76d2c8bd  main_pt.dict\n"
            + "149b5d619d689ca670b4b99a515618ea3baef806bb1a647b06d5e6c556fe0ecc  main_sk.dict\n"
            + "751840c6966246ce70f175591bd50ff5035c3708dcd31bc635a8a909f13a589b  main_uk.dict\n";

    /**
     * The AOSP word lists' dictionaries in HeliBoard's repository, at HELIBOARD_BASE_URL:
     * name, file (without .dict), SHA-256. They are AOSP's lists unchanged (same words,
     * header dates and versions; measured the same as our own builds of them).
     */
    static final String HELIBOARD_FILES = ""
            + "cs main_cs d2317360a5af36b9db484892cbb2975d53cd40c01b9e8a2a1f23251be80cfa86\n"
            + "da main_da 7eca55127764b36d7823b7ff86b4e2a0dabbf76bfc0d5659df657c6ea0f24f13\n"
            + "de main_de__legacy b2e0393587a8a0d974e6bbee2ee50acd2af6b4ec2d138334ce3626873a0e85ad\n"
            + "el main_el__legacy c0794d9e5f85fbe623b7b3e617f2a9d7f45011648b289a0b263dfdafddac9332\n"
            + "es main_es 44a048bdb352b7b3f3d07a86ad1fb1692052b5a7838a57bc39caaae6181fcbad\n"
            + "fi main_fi 87bc832f9b0f191907f1d08a12527a2c0e8854e23db807fa28af1936a4c0a87a\n"
            + "fr main_fr 6cc7bc856de7928606ec974a17887f80848c7692560824ab05153a48cb193c94\n"
            + "hr main_hr 3c1bb98883ac5bc96a96e206972b5fc8c4a04ad8992d26c4d365ae0cdcc8f686\n"
            + "it main_it fa9841c4a10fad7a422a61c84fb1c8bd7618b5b9d2debddaa84e1e95f841c1e8\n"
            + "lt main_lt 47155863f9fe3c188c9c5d4f850d02a4b499e9828fecc4bfb8305f65fedfcef2\n"
            + "lv main_lv d9ecea24f9b769462834c8760661678f62f33267bec0cc9b069e8fc2ae986e32\n"
            + "nb main_nb 6e4c39ba5e471af8be2d8ea4a62d2cf0afded233481a3f7c6486889696014ffe\n"
            + "nl main_nl 537078e3ba369c87063031f03051d80a0ee121a5459f8192811ca254fc959d19\n"
            + "pl main_pl 5b2d08c14128bcfb9d62db3cd5df8f0af8f7c2e653d9913f72f9f87d64648864\n"
            + "pt_pt main_pt_pt c9e5607bd63143930b41ca91919bce23e8abca8172ad24a0fdede029398c95d7\n"
            + "ro main_ro 882c7276bb909afcebc3dddf9086ea0d8c37519ee136971cc448b1b80c51539c\n"
            + "ru main_ru 4fce87b579571b4116bb4696f4bdf2a7f1c7fcf01928c05a9e08f7503667a56d\n"
            + "sl main_sl d779d40bee274be2a5596e6df7882ac4685327c360810000ab5b900c6a79fe32\n"
            + "sr main_sr dace1f700bb5335ed5c9e627a32dd02642c37f6f7fa524867786ca0496b20500\n"
            + "sv main_sv fd4ff64e40d0c7cd34914f473090b21c266e0ab9631b04a96e799fbe4879a5e4\n"
            + "tr main_tr__legacy bdd66c0873e65fb71e20f7c8dee85dd7ab9be59f627fff7521790ff8326c9f18\n";

    private static final Map<String, String> PUBLISHED = new HashMap<>();
    private static final Map<String, String[]> HELIBOARD = new HashMap<>();

    static {
        for (String line : SHA256SUMS.split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (f.length == 2) PUBLISHED.put(f[1], f[0]);
        }
        for (String line : HELIBOARD_FILES.split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (f.length == 3) HELIBOARD.put(f[0], new String[] {f[1] + ".dict", f[2]});
        }
    }

    private static final List<Entry> ALL;

    static {
        List<Entry> l = new ArrayList<>();
        l.add(new Entry("en", "English", String.format(AOSP, "en_US"), true, null, null));
        l.add(new Entry("iw", "עברית", String.format(AOSP, "iw")
                + " Words with Latin letters or digits dropped.", true, null, null));
        l.add(new Entry("ar", "العربية", "Word frequencies from the Leipzig Corpora Collection"
                + " (Wortschatz, Leipzig University; wortschatz.uni-leipzig.de), Arabic news 2020"
                + " and Wikipedia 2021, CC BY 4.0; this dictionary is CC BY 4.0 too. The 150,000"
                + " most frequent words, without diacritics.", true, null, null));
        aosp(l, "cs", "Čeština", "cs");
        aosp(l, "da", "Dansk", "da");
        aosp(l, "de", "Deutsch", "de");
        aosp(l, "el", "Ελληνικά", "el");
        aosp(l, "es", "Español", "es");
        aosp(l, "fi", "Suomi", "fi");
        aosp(l, "fr", "Français", "fr");
        aosp(l, "hr", "Hrvatski", "hr");
        aosp(l, "it", "Italiano", "it");
        aosp(l, "lt", "Lietuvių", "lt");
        aosp(l, "lv", "Latviešu", "lv");
        aosp(l, "nb", "Norsk bokmål", "nb");
        aosp(l, "nl", "Nederlands", "nl");
        aosp(l, "pl", "Polski", "pl");
        aosp(l, "pt", "Português (Brasil)", "pt_BR");
        aosp(l, "pt_pt", "Português (Portugal)", "pt_PT");
        aosp(l, "ro", "Română", "ro");
        aosp(l, "ru", "Русский", "ru");
        aosp(l, "sl", "Slovenščina", "sl");
        aosp(l, "sr", "Српски", "sr");
        aosp(l, "sv", "Svenska", "sv");
        aosp(l, "tr", "Türkçe", "tr");
        leipzig(l, "uk", "Українська", "Ukrainian news 2022 and 2023", "200,000");
        leipzig(l, "bg", "Български", "Bulgarian news 2020 and 2022", "200,000");
        leipzig(l, "hu", "Magyar", "Hungarian news 2022 and 2023", "200,000");
        leipzig(l, "sk", "Slovenčina", "Slovak news crawl 2015 and 2016", "200,000");
        leipzig(l, "ca", "Català", "Catalan news crawl 2016 and news 2022", "136,000");
        ALL = Collections.unmodifiableList(l);
    }

    private static void aosp(List<Entry> l, String name, String label, String list) {
        String[] heli = HELIBOARD.get(name);
        if (heli != null) {
            l.add(new Entry(name, label, String.format(AOSP, list) + HELIBOARD_NOTE, false,
                    HELIBOARD_BASE_URL + heli[0], heli[1]));
        } else {
            ours(l, name, label, String.format(AOSP, list));
        }
    }

    private static void leipzig(List<Entry> l, String name, String label, String corpora,
            String words) {
        ours(l, name, label, String.format(LEIPZIG, corpora, words));
    }

    private static void ours(List<Entry> l, String name, String label, String attribution) {
        String file = "main_" + name + ".dict";
        l.add(new Entry(name, label, attribution, false, DOWNLOAD_BASE_URL + file,
                PUBLISHED.get(file)));
    }

    private DictionaryCatalog() {}

    /** Every dictionary, the bundled ones first. */
    public static List<Entry> all() {
        return ALL;
    }

    /** The dictionary called {@code name} ("de", "pt_pt"), or null. */
    public static Entry byName(String name) {
        for (Entry e : ALL) if (e.name.equals(name)) return e;
        return null;
    }

    /**
     * The dictionary an input locale uses ("de_NE" and "de" use "de", "pt_PT" uses "pt_pt",
     * "he" uses "iw"), or null when there is none for its language.
     */
    public static Entry forLocale(String locale) {
        for (String name : AospSuggestionEngine.dictionaryNames(locale)) {
            Entry e = byName(name);
            if (e != null) return e;
        }
        return null;
    }
}
