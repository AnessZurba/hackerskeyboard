#!/usr/bin/env bash
# Builds the optional (downloadable) word suggestion dictionaries, which are not in the APK:
# the user downloads one from the "dicts-v1" GitHub release (Settings > Word suggestions >
# Dictionaries > Download) and imports it with "Browse". Only English, Hebrew and Arabic are
# bundled (app/src/main/assets/dicts).
#
#   tools/mkdicts.sh [OUTPUT_DIR [LANG...]]        (default build/dicts, every language)
#   ./gradlew optionalDicts                         (the same, from Gradle)
#
# Writes OUTPUT_DIR/main_<lang>.dict, a NOTICE.txt with each file's source, licence and
# changes, and SHA256SUMS: upload all of them as the assets of the release. The intermediate
# word lists stay in OUTPUT_DIR/src (the quality tests sample words from them).
#
# Sources (see NOTICE.txt as written below):
#   AOSP LatinIME word lists (Apache 2.0) at the decoder's pinned commit, converted as is,
#   for the languages AOSP has; Leipzig Corpora Collection news corpora (CC BY 4.0) for the
#   few layout languages AOSP has none for (uk, bg, hu, sk, ca), via tools/mkcombined.py.
# Leipzig archives are fetched once into $HK_LEIPZIG_CACHE (default ~/.cache/hk-leipzig);
# only their *-words.txt is kept. Deterministic: SOURCE_DATE_EPOCH fixes the headers' date.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
out=${1:-$here/../build/dicts}
shift || true
mkdir -p "$out/src"
out=$(cd "$out" && pwd)
export SOURCE_DATE_EPOCH=${SOURCE_DATE_EPOCH:-1794700000}
leipzig=${HK_LEIPZIG_CACHE:-$HOME/.cache/hk-leipzig}
LEIPZIG_URL=https://downloads.wortschatz-leipzig.de/corpora
# Keep in sync with tools/aosp-dicttool/makedict.sh.
AOSP_COMMIT=127336e9f29d69607eab55982324b210279ae8c5

# name  AOSP word list
AOSP="cs:cs da:da de:de el:el es:es fi:fi fr:fr hr:hr it:it lt:lt lv:lv nb:nb nl:nl
pl:pl pt:pt_BR pt_pt:pt_PT ro:ro ru:ru sl:sl sr:sr sv:sv tr:tr"
# name  mkcombined.py script  words kept  Leipzig corpora (news only, no Wikipedia)
LEIPZIG="uk:ukrainian:200000:ukr_news_2023_1M,ukr_news_2022_1M
bg:bulgarian:200000:bul_news_2022_1M,bul_news_2020_1M
hu:hungarian:200000:hun_news_2023_1M,hun_news_2022_1M
sk:slovak:200000:slk_newscrawl_2016_1M,slk_newscrawl_2015_1M
ca:catalan:200000:cat_newscrawl_2016_1M,cat_news_2022_300K"

wanted() { local n=$1; shift; [ $# -eq 0 ] && return 0; for l in "$@"; do [ "$l" = "$n" ] && return 0; done; return 1; }

words_file() { # corpus -> path of its word list, fetched if needed
    local f=$leipzig/$1-words.txt
    if [ ! -s "$f" ]; then
        mkdir -p "$leipzig"
        curl -fsSL --retry 3 "$LEIPZIG_URL/$1.tar.gz" | tar -xzO --wildcards '*-words.txt' > "$f.part"
        mv "$f.part" "$f"
    fi
    echo "$f"
}

notice=$out/NOTICE.txt.part
cat > "$notice" <<'EOF'
Optional word suggestion dictionaries for Hacker's Keyboard (AOSP binary dictionary format v2)
==============================================================================================

These files are data, not code, and are distributed under their own licences, which
differ from the app's (Apache License 2.0). Download the one for your language, then in the
keyboard's settings open Word suggestions > Dictionaries and import it with "Browse".
They were built with tools/mkdicts.sh (AOSP's own dicttool, tools/aosp-dicttool/makedict.sh,
and tools/mkcombined.py) from the sources below. SHA256SUMS has the files' checksums.

AOSP LatinIME word lists
    Source: https://android.googlesource.com/platform/packages/inputmethods/LatinIME ,
    dictionaries/<list>.combined.gz at commit 127336e9f29d69607eab55982324b210279ae8c5.
    Licence: Apache License 2.0, https://www.apache.org/licenses/LICENSE-2.0
    Copyright (C) The Android Open Source Project.
    Changes: none (converted as is, with a code point table).

Leipzig Corpora Collection word frequency lists
    Source: Leipzig Corpora Collection (Wortschatz, Leipzig University),
    https://wortschatz.uni-leipzig.de/en/download (news corpora, "*-words.txt").
    Licence: Creative Commons Attribution 4.0 International (CC BY 4.0),
    https://creativecommons.org/licenses/by/4.0/ ; these dictionaries are CC BY 4.0 too.
    Citation: D. Goldhahn, T. Eckart, U. Quasthoff: Building Large Monolingual
    Dictionaries at the Leipzig Corpora Collection: From 100 to 200 Languages.
    In: Proceedings of LREC 2012.
    Changes: the counts of the corpora were added up; only words of the language's letters
    were kept (apostrophes and hyphens only between letters); case variants counted as one
    word, spelt as most often; words seen once dropped; the most frequent kept and turned
    into dictionary frequencies (210 + 21 * log10(count / top count)).

Files
EOF

for entry in $AOSP; do
    name=${entry%%:*} list=${entry#*:}
    wanted "$name" "$@" || continue
    # makedict.sh fetches the AOSP checkout on first use; the word list is copied after it.
    "$here/aosp-dicttool/makedict.sh" "aosp:${list}_wordlist.combined.gz" "$out/main_$name.dict" -t on > "$out/src/$name.log" 2>&1 || { cat "$out/src/$name.log"; exit 1; }
    gzip -dc "${AOSP_LATINIME:-${HK_DICTTOOL_CACHE:-$HOME/.cache/hk-dicttool}/LatinIME-$AOSP_COMMIT}/dictionaries/${list}_wordlist.combined.gz" \
        > "$out/src/$name.combined"
    n=$(grep -c '^ word=' "$out/src/$name.combined")
    desc=$(head -1 "$out/src/$name.combined" | sed 's/.*description=\([^,]*\).*/\1/')
    printf '    main_%s.dict  %s  %s words  AOSP %s_wordlist (Apache 2.0)\n' \
        "$name" "$desc" "$n" "$list" >> "$notice"
    echo "main_$name.dict: $n words, $(wc -c < "$out/main_$name.dict") bytes"
done

for entry in $LEIPZIG; do
    IFS=: read -r name script limit corpora <<< "$entry"
    wanted "$name" "$@" || continue
    files=()
    for c in ${corpora//,/ }; do files+=("$(words_file "$c")"); done
    python3 -I "$here/mkcombined.py" leipzig "$script" "$limit" "$out/src/$name.combined" "${files[@]}" > /dev/null
    "$here/aosp-dicttool/makedict.sh" "$out/src/$name.combined" "$out/main_$name.dict" -t on > "$out/src/$name.log" 2>&1 || { cat "$out/src/$name.log"; exit 1; }
    n=$(grep -c '^ word=' "$out/src/$name.combined")
    desc=$(head -1 "$out/src/$name.combined" | sed 's/.*description=\([^,]*\).*/\1/')
    printf '    main_%s.dict  %s  %s words  Leipzig %s (CC BY 4.0)\n' \
        "$name" "$desc" "$n" "${corpora//,/ + }" >> "$notice"
    echo "main_$name.dict: $n words, $(wc -c < "$out/main_$name.dict") bytes"
done

if [ $# -gt 0 ]; then
    rm -f "$notice"
    echo "Built only $* (NOTICE.txt and SHA256SUMS are written by a full build)"
    exit 0
fi
mv "$notice" "$out/NOTICE.txt"
(cd "$out" && sha256sum main_*.dict > SHA256SUMS)
echo "Wrote $out (upload main_*.dict, NOTICE.txt and SHA256SUMS to the dicts-v1 release)"
