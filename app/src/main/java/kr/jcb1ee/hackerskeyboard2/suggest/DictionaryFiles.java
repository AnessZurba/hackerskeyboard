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

import android.content.Context;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Imported dictionaries: main_NAME.dict files the user picked (from the release, see
 * {@link DictionaryCatalog}), checked and copied into the app's own files (files/dicts), where
 * {@link AospSuggestionEngine#open(Context, String)} looks before the bundled assets. No
 * storage permission is needed: the file comes through the system picker as a stream.
 */
public final class DictionaryFiles {
    /** The app's directory for imported dictionaries, under getFilesDir(). */
    public static final String DIR = "dicts";
    /**
     * A shared-preferences key that changes whenever a dictionary is imported or removed, so
     * the running keyboard reopens its dictionary.
     */
    public static final String PREF_CHANGED = "dictionaries_changed";

    /**
     * Format v2 of AOSP's binary dictionaries (FormatSpec): magic, version, flags, size. Its
     * versions are 2, 201 and 202 (what makedict writes, with or without a code point table).
     */
    static final int MAGIC = 0x9BC13AFE;
    private static final int[] VERSIONS = {2, 201, 202};
    private static final int FIXED_HEADER = 12;
    private static final int MAX_HEADER = 64 * 1024;
    /** Smaller than any real dictionary, bigger than any we make (the largest is 6 MB). */
    static final long MIN_SIZE = 4 * 1024, MAX_SIZE = 32L * 1024 * 1024;

    /** Why a file was not imported. */
    public enum Problem {
        /** Could not be read or written. */
        IO,
        /** Not an AOSP main dictionary (format v2). */
        NOT_A_DICTIONARY,
        /** Too small or too large to be one. */
        BAD_SIZE,
        /** A dictionary for another language. */
        WRONG_LANGUAGE,
    }

    /** A file that was not imported, and why. */
    public static final class InvalidDictionaryException extends Exception {
        public final Problem problem;

        InvalidDictionaryException(Problem problem, String message) {
            super(message);
            this.problem = problem;
        }
    }

    private DictionaryFiles() {}

    public static File dir(Context context) {
        return new File(context.getFilesDir(), DIR);
    }

    /** Where the imported dictionary {@code name} ("de", "pt_pt") is, if it is. */
    public static File file(Context context, String name) {
        return new File(dir(context), "main_" + name + ".dict");
    }

    public static boolean isImported(Context context, String name) {
        return file(context, name).isFile();
    }

    /** A dictionary that was imported. */
    public static final class Imported {
        /** Its header (dictionary, locale, description, version, date...). */
        public final Map<String, String> header;
        /** Whether it is byte for byte the file published for it (its SHA-256). */
        public final boolean verified;

        Imported(Map<String, String> header, boolean verified) {
            this.header = header;
            this.verified = verified;
        }
    }

    /**
     * Reads {@code in} (closed afterwards) as the dictionary for {@code entry}, checks it and
     * stores it, replacing an earlier import.
     */
    public static Imported install(Context context, DictionaryCatalog.Entry entry,
            InputStream in) throws InvalidDictionaryException {
        File dir = dir(context);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new InvalidDictionaryException(Problem.IO, "cannot create " + dir);
        }
        File tmp = new File(dir, entry.fileName() + ".part");
        try {
            MessageDigest sha256 = sha256();
            long size = copy(new DigestInputStream(in, sha256), tmp);
            boolean verified = entry.sha256 != null && entry.sha256.equals(hex(sha256.digest()));
            Map<String, String> header = check(tmp, size, entry);
            File target = file(context, entry.name);
            if (!tmp.renameTo(target)) {
                throw new InvalidDictionaryException(Problem.IO, "cannot write " + target);
            }
            return new Imported(header, verified);
        } finally {
            if (tmp.exists() && !tmp.delete()) tmp.deleteOnExit();
        }
    }

    /** Removes an imported dictionary; returns whether there was one. */
    public static boolean remove(Context context, String name) {
        File f = file(context, name);
        return f.isFile() && f.delete();
    }

    /** Checksums of imported files already computed: path, length, time -> verified. */
    private static final Map<String, Boolean> sPublished = new HashMap<>();

    /** Whether the imported dictionary for {@code entry} is the file published for it. */
    public static boolean isPublished(Context context, DictionaryCatalog.Entry entry) {
        File f = file(context, entry.name);
        if (entry.sha256 == null || !f.isFile()) return false;
        String key = f.getPath() + ':' + f.length() + ':' + f.lastModified();
        synchronized (sPublished) {
            Boolean known = sPublished.get(key);
            if (known != null) return known;
        }
        MessageDigest sha256 = sha256();
        byte[] buf = new byte[64 * 1024];
        try (InputStream in = new DigestInputStream(new FileInputStream(f), sha256)) {
            while (in.read(buf) > 0) {
                // digesting
            }
        } catch (IOException e) {
            return false;
        }
        boolean published = entry.sha256.equals(hex(sha256.digest()));
        synchronized (sPublished) {
            sPublished.put(key, published);
        }
        return published;
    }

    /** The header of an imported dictionary, or null when there is none or it is unreadable. */
    public static Map<String, String> header(Context context, String name) {
        File f = file(context, name);
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return readHeader(in);
        } catch (IOException | InvalidDictionaryException e) {
            return null;
        }
    }

    /**
     * Checks a candidate file: its size, an AOSP v2 header naming a main dictionary for the
     * entry's language, and that the decoder opens it (when the native library is there).
     */
    static Map<String, String> check(File file, long size, DictionaryCatalog.Entry entry)
            throws InvalidDictionaryException {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new InvalidDictionaryException(Problem.BAD_SIZE, size + " bytes");
        }
        Map<String, String> header;
        try (InputStream in = new FileInputStream(file)) {
            header = readHeader(in);
        } catch (IOException e) {
            throw new InvalidDictionaryException(Problem.IO, e.toString());
        }
        String dictionary = header.get("dictionary");
        String locale = header.get("locale");
        if (dictionary == null || !dictionary.startsWith("main:") || locale == null) {
            throw new InvalidDictionaryException(Problem.NOT_A_DICTIONARY,
                    "not a main dictionary: " + header);
        }
        if (!languageOf(locale).equals(entry.language())) {
            throw new InvalidDictionaryException(Problem.WRONG_LANGUAGE, locale);
        }
        if (NativeLibrary.load()) {
            AospSuggestionEngine engine = AospSuggestionEngine.open(file, locale);
            if (engine == null) {
                throw new InvalidDictionaryException(Problem.NOT_A_DICTIONARY,
                        "the decoder cannot open it");
            }
            engine.close();
        }
        return header;
    }

    /** The language of a dictionary's locale, as the catalogue names it ("he_IL" is "iw"). */
    static String languageOf(String locale) {
        String l = locale.toLowerCase(Locale.ROOT).replace('-', '_');
        int sep = l.indexOf('_');
        if (sep >= 0) l = l.substring(0, sep);
        return l.equals("he") ? "iw" : l;
    }

    /**
     * Reads the header of an AOSP format v2 dictionary (FormatSpec / BinaryDictDecoderUtils):
     * magic, version 2, flags, header size, then key and value strings up to that size, each
     * character one byte (0x20-0xFF) or three (a first byte below 0x20), ended by 0x1F.
     */
    static Map<String, String> readHeader(InputStream raw)
            throws IOException, InvalidDictionaryException {
        DataInputStream in = new DataInputStream(raw);
        byte[] fixed = new byte[FIXED_HEADER];
        try {
            in.readFully(fixed);
        } catch (java.io.EOFException e) {
            throw new InvalidDictionaryException(Problem.NOT_A_DICTIONARY, "too short");
        }
        int magic = ((fixed[0] & 0xFF) << 24) | ((fixed[1] & 0xFF) << 16)
                | ((fixed[2] & 0xFF) << 8) | (fixed[3] & 0xFF);
        int version = ((fixed[4] & 0xFF) << 8) | (fixed[5] & 0xFF);
        int headerSize = ((fixed[8] & 0xFF) << 24) | ((fixed[9] & 0xFF) << 16)
                | ((fixed[10] & 0xFF) << 8) | (fixed[11] & 0xFF);
        if (magic != MAGIC) {
            throw new InvalidDictionaryException(Problem.NOT_A_DICTIONARY, "no magic number");
        }
        if (Arrays.binarySearch(VERSIONS, version) < 0) {
            throw new InvalidDictionaryException(Problem.NOT_A_DICTIONARY,
                    "format version " + version);
        }
        if (headerSize < FIXED_HEADER || headerSize > MAX_HEADER) {
            throw new InvalidDictionaryException(Problem.NOT_A_DICTIONARY,
                    "header size " + headerSize);
        }
        byte[] attributes = new byte[headerSize - FIXED_HEADER];
        try {
            in.readFully(attributes);
        } catch (java.io.EOFException e) {
            throw new InvalidDictionaryException(Problem.NOT_A_DICTIONARY, "header cut short");
        }
        Map<String, String> header = new HashMap<>();
        int[] pos = {0};
        while (pos[0] < attributes.length) {
            String key = readString(attributes, pos);
            String value = readString(attributes, pos);
            if (key == null || value == null) {
                throw new InvalidDictionaryException(Problem.NOT_A_DICTIONARY, "bad header");
            }
            header.put(key, value);
        }
        return header;
    }

    private static String readString(byte[] b, int[] pos) {
        StringBuilder s = new StringBuilder();
        while (pos[0] < b.length) {
            int c = b[pos[0]++] & 0xFF;
            if (c == 0x1F) return s.toString();
            if (c < 0x20) {
                if (pos[0] + 2 > b.length) return null;
                c = (c << 16) | ((b[pos[0]] & 0xFF) << 8) | (b[pos[0] + 1] & 0xFF);
                pos[0] += 2;
                if (!Character.isValidCodePoint(c)) return null;
            }
            s.appendCodePoint(c);
        }
        return null; // no terminator
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every Android and JVM has it
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
        return s.toString();
    }

    /** Copies at most MAX_SIZE + 1 bytes (enough to tell it is too large); returns the count. */
    private static long copy(InputStream in, File to) throws InvalidDictionaryException {
        long n = 0;
        try (InputStream src = in; OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = src.read(buf)) > 0) {
                out.write(buf, 0, r);
                n += r;
                if (n > MAX_SIZE) break;
            }
        } catch (IOException e) {
            throw new InvalidDictionaryException(Problem.IO, e.toString());
        }
        return n;
    }
}
