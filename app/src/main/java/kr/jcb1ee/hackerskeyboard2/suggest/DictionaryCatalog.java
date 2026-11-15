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
 * other layout languages' as optional files (made by tools/mkdicts.sh, published as assets of
 * a GitHub release, downloaded in the browser and imported with {@link DictionaryFiles}). The
 * keyboard has no INTERNET permission: it never downloads anything itself.
 */
public final class DictionaryCatalog {
    /** Where the optional dictionaries are published: + "main_LANG.dict". */
    public static final String DOWNLOAD_BASE_URL =
            "https://github.com/AnessZurba/hackerskeyboard/releases/download/dicts-v1/";

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

        Entry(String name, String label, String attribution, boolean bundled) {
            this.name = name;
            this.label = label;
            this.attribution = attribution;
            this.bundled = bundled;
            this.sha256 = bundled ? null : PUBLISHED.get(fileName());
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

        /** Where to download it (optional dictionaries). */
        public String downloadUrl() {
            return DOWNLOAD_BASE_URL + fileName();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * The published files' checksums: build/dicts/SHA256SUMS as tools/mkdicts.sh writes it
     * (deterministic), pasted here; DictionaryFilesTest compares them when it is there.
     */
    static final String SHA256SUMS = ""
            + "9d298f73dedc8a46bca68f6646b6d5b22a55119ec6ada49630cafcfc8fb7e57d  main_bg.dict\n"
            + "6ce5fec6741e66c1d6349a40b33f7c321fce59df5970d037bbfdc1bea0811f3f  main_ca.dict\n"
            + "de9f0a7a3ac9edc6f92fd93560526e461601c0c3fe213a5de54f15636d743e1d  main_cs.dict\n"
            + "aca06ff8a2f848821fc4de4ed00264cac3f772b08ee85700f4958eb913855ba2  main_da.dict\n"
            + "c116a5552b8c74b4884c0cb5f5910db6cffb8347eefff579aec6fdec3f38be6a  main_de.dict\n"
            + "bc8a1beebb54f4751844a260949670b3b7dbf4ff82d1382dcb649df6ea77f8b1  main_el.dict\n"
            + "379a046977b07cdca16398ce3b0535ca0c95a279c229c71c9173effd105d8b37  main_es.dict\n"
            + "8a30ffbb48c194ef52029f54deddd9e29c1115fabfce3e049c7c87f08bd4c14f  main_fi.dict\n"
            + "680ddf77a2b8d2c6d951f1a04a779f383a220955ad77c46d5d56b85f80b9dbe9  main_fr.dict\n"
            + "f7689a1fcbe0ee8ecc9f37ecb8b0b419e66d0fdf1efcdffdafc29aefcf6fd22a  main_hr.dict\n"
            + "3cfd4a32ae6f6b90dd0e460faf7e36ec5a5482c41b077224f628800a59e51ce0  main_hu.dict\n"
            + "8fc2f60925f399ff75f1a8a07d4287c35f19b4fc2dd22a652fd5790f698b782e  main_it.dict\n"
            + "6397b1b016ec238d67a8fc2ca7d715a618fa1aea35aa62c9e95830ee91feaa25  main_lt.dict\n"
            + "3b078fa94cd2af2e1f8624d5be68106005b4b3e992533ccdb3c9847557bf6487  main_lv.dict\n"
            + "b9f1c2628d1e34b5c9bb7fde6bbf8887759e400e4123369b3d61b04ab737cd58  main_nb.dict\n"
            + "1647b26770baca07e4a6093aaf3ff460f69e362a5e53842cc4f1131a699a7978  main_nl.dict\n"
            + "7a3f98b7e1e655f3d88f5bf1a1c744da7fe3b021fe08ab8dd4228f72a494e232  main_pl.dict\n"
            + "01742d4d0b35dbb472314d8f119285df37c9cb0ecfe9375cd8365a2e76d2c8bd  main_pt.dict\n"
            + "d1de1866c56c145683490293b3b80dbe14603142b1ab3d973161708cca5c9622  main_pt_pt.dict\n"
            + "04316cdaa0d3e98c317bb2a239c6635dacbdb35bbe70fca78caae5839144c0f5  main_ro.dict\n"
            + "55c2255169ddad5572e3e4a3fd580f003f64920de0fd57f2c0118ad4ea9aa289  main_ru.dict\n"
            + "149b5d619d689ca670b4b99a515618ea3baef806bb1a647b06d5e6c556fe0ecc  main_sk.dict\n"
            + "f50763443fca2fa29815056fe0899da2fc4604907ad812a8edb0d1a210b8875b  main_sl.dict\n"
            + "cfbbb3726b5e3366c960f06217b2d4f35de27f225fac769048338889ec7d4d31  main_sr.dict\n"
            + "b6f48bc2d4fee4482e91e870d2a44da7a3ce9daa231fe51c138cfc98becaab0c  main_sv.dict\n"
            + "d41a9090d618dfd55c7ff46e6b04f2f99d98d9d3ee4ff987d406de02b160cf0c  main_tr.dict\n"
            + "751840c6966246ce70f175591bd50ff5035c3708dcd31bc635a8a909f13a589b  main_uk.dict\n";

    private static final Map<String, String> PUBLISHED = new HashMap<>();

    static {
        for (String line : SHA256SUMS.split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (f.length == 2) PUBLISHED.put(f[1], f[0]);
        }
    }

    private static final List<Entry> ALL;

    static {
        List<Entry> l = new ArrayList<>();
        l.add(new Entry("en", "English", String.format(AOSP, "en_US"), true));
        l.add(new Entry("iw", "עברית", String.format(AOSP, "iw")
                + " Words with Latin letters or digits dropped.", true));
        l.add(new Entry("ar", "العربية", "Word frequencies from the Leipzig Corpora Collection"
                + " (Wortschatz, Leipzig University; wortschatz.uni-leipzig.de), Arabic news 2020"
                + " and Wikipedia 2021, CC BY 4.0; this dictionary is CC BY 4.0 too. The 150,000"
                + " most frequent words, without diacritics.", true));
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
        l.add(new Entry(name, label, String.format(AOSP, list), false));
    }

    private static void leipzig(List<Entry> l, String name, String label, String corpora,
            String words) {
        l.add(new Entry(name, label, String.format(LEIPZIG, corpora, words), false));
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
