#!/usr/bin/env bash
# Builds an AOSP-format main dictionary (.dict, binary format v2) with AOSP's own dicttool
# (makedict), compiled from the pinned AOSP LatinIME commit and run on the local JVM.
#
#   tools/aosp-dicttool/makedict.sh <input.combined[.gz]> <output.dict> [makedict options]
#
# Examples (from the repository root):
#   # English, from AOSP's en_US wordlist (Apache 2.0):
#   tools/aosp-dicttool/makedict.sh aosp:en_US_wordlist.combined.gz app/src/main/assets/dicts/main_en.dict
#   # Any wordlist in AOSP's "combined" text format:
#   tools/aosp-dicttool/makedict.sh my.combined out.dict -t on
#
# An input of the form aosp:<name> is taken from the dictionaries/ directory of the AOSP
# checkout. Needs git, a JDK (javac/java 17+) and curl (for junit and jsr305, which dicttool
# compiles against). Everything is cached in $HK_DICTTOOL_CACHE (default
# ~/.cache/hk-dicttool); set AOSP_LATINIME to use an existing LatinIME checkout instead.
#
# makedict options are passed through, e.g. "-t on" (code point table: smaller files for
# non-Latin scripts) or "-4" (format v4, needs the native library; not used by the app).
set -euo pipefail

AOSP_URL=https://android.googlesource.com/platform/packages/inputmethods/LatinIME
# Keep in sync with app/src/main/cpp/aosp/README.md.
AOSP_COMMIT=127336e9f29d69607eab55982324b210279ae8c5
MAVEN=https://repo1.maven.org/maven2

if [ $# -lt 2 ]; then
    sed -n '2,20p' "$0"
    exit 2
fi
input=$1
output=$2
shift 2

cache=${HK_DICTTOOL_CACHE:-$HOME/.cache/hk-dicttool}
mkdir -p "$cache"

src=${AOSP_LATINIME:-}
if [ -z "$src" ]; then
    src=$cache/LatinIME-$AOSP_COMMIT
    if [ ! -d "$src/.git" ]; then
        rm -rf "$src"
        git init -q "$src"
        git -C "$src" remote add origin "$AOSP_URL"
        git -C "$src" fetch -q --depth 1 origin "$AOSP_COMMIT"
        git -C "$src" checkout -q FETCH_HEAD
    fi
fi

fetch_jar() { # group/path artifact version
    local jar=$cache/$2-$3.jar
    if [ ! -f "$jar" ]; then
        # Maven Central sometimes answers 429: retry a few times.
        curl -fsSL --retry 5 --retry-delay 30 --retry-all-errors -o "$jar.part" \
            "$MAVEN/$1/$2/$3/$2-$3.jar"
        mv "$jar.part" "$jar"
    fi
}
fetch_jar junit junit 4.13.2
fetch_jar org/hamcrest hamcrest-core 1.3
fetch_jar com/google/code/findbugs jsr305 3.0.2
cp=$cache/junit-4.13.2.jar:$cache/hamcrest-core-1.3.jar:$cache/jsr305-3.0.2.jar

classes=$cache/classes-$AOSP_COMMIT
if [ ! -f "$classes/.done" ]; then
    rm -rf "$classes"
    mkdir -p "$classes"
    # The sources of AOSP's dicttool_aosp target (tools/dicttool/Android.bp and the
    # dicttool_deps filegroup in Android.bp).
    L=java/src/com/android/inputmethod/latin
    {
        find "$src/tools/dicttool/src" "$src/tools/dicttool/compat" "$src/tools/dicttool/tests" \
            "$src/common/src" "$src/$L/makedict" \
            "$src/tests/src/com/android/inputmethod/latin/makedict" -name '*.java'
        for f in BinaryDictionary DicTraverseSession Dictionary NgramContext SuggestedWords \
                settings/SettingsValuesForSuggestion utils/BinaryDictionaryUtils \
                utils/CombinedFormatUtils utils/JniUtils define/DebugFlags \
                define/DecoderSpecificConstants; do
            echo "$src/$L/$f.java"
        done
        echo "$src/tests/src/com/android/inputmethod/latin/utils/ByteArrayDictBuffer.java"
    } > "$classes.srcs"
    javac -nowarn -encoding UTF-8 -d "$classes" -cp "$cp" @"$classes.srcs"
    touch "$classes/.done"
fi

case $input in
    aosp:*) input=$src/dictionaries/${input#aosp:} ;;
esac
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
case $input in
    *.gz) gzip -dc "$input" > "$tmp/input.combined" ;;
    *) cp "$input" "$tmp/input.combined" ;;
esac

mkdir -p "$(dirname "$output")"
java -cp "$classes:$cp" com.android.inputmethod.latin.dicttool.Dicttool \
    makedict -s "$tmp/input.combined" -d "$output" "$@"
echo "Wrote $output ($(wc -c < "$output") bytes)"
