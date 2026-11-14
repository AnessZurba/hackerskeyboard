#!/usr/bin/env python3
"""Writes an AOSP "combined" wordlist for tools/aosp-dicttool/makedict.sh.

Two inputs are understood:

  aosp     an AOSP LatinIME combined wordlist (Apache 2.0), cleaned up: words with
           characters outside the language's letters are dropped, as are (Hebrew) words
           with a final letter before their end, and niqqud / harakat are stripped.
  leipzig  one or more Leipzig Corpora Collection "*-words.txt" files (id<TAB>word<TAB>count;
           corpora are CC BY 4.0, https://wortschatz.uni-leipzig.de/en/download). Counts
           of all files are added up; f is 210 + 21 * log10(count / top count), the slope
           of AOSP's own wordlists (about 21 per tenfold drop in frequency).

Usage (from the repository root), as the bundled dictionaries were made
(SOURCE_DATE_EPOCH=1794700000 sets the header date):
  # Hebrew, from AOSP's iw wordlist (all words):
  tools/mkcombined.py aosp hebrew 0 iw.combined ~/.cache/hk-dicttool/LatinIME-*/dictionaries/iw_wordlist.combined.gz
  # Arabic, from the Leipzig news 2020 and Wikipedia 2021 corpora (1M sentences each;
  # https://downloads.wortschatz-leipzig.de/corpora/ara_news_2020_1M.tar.gz and
  # .../ara_wikipedia_2021_1M.tar.gz), the 150,000 most frequent words:
  tools/mkcombined.py leipzig arabic 150000 ar.combined \\
      ara_news_2020_1M/ara_news_2020_1M-words.txt ara_wikipedia_2021_1M/ara_wikipedia_2021_1M-words.txt
  tools/aosp-dicttool/makedict.sh iw.combined app/src/main/assets/dicts/main_iw.dict -t on
  tools/aosp-dicttool/makedict.sh ar.combined app/src/main/assets/dicts/main_ar.dict -t on

The limit (third argument) keeps the N most frequent words; 0 keeps all.
"""
import gzip
import math
import os
import sys
import time
import unicodedata

HEBREW_FINALS = 'ךםןףץ'  # ך ם ן ף ץ


def hebrew_letter(c):
    return 'א' <= c <= 'ת'


def arabic_letter(c):
    # The 36 letters of the Arabic alphabet with hamza forms, ta marbuta and alef maqsura
    # (U+0621-U+063A, U+0641-U+064A); no Persian/Urdu letters, no presentation forms.
    return 'ء' <= c <= 'غ' or 'ف' <= c <= 'ي'


SCRIPTS = {
    'hebrew': dict(letter=hebrew_letter, extra="'\"", header=dict(
        dictionary='main:iw', locale='iw', description='עברית')),
    'arabic': dict(letter=arabic_letter, extra='', header=dict(
        dictionary='main:ar', locale='ar', description='العربية')),
}


def normalise(word):
    """NFKC, without combining marks (niqqud, harakat, superscript alef) and tatweel."""
    word = unicodedata.normalize('NFKC', word)
    return ''.join(c for c in word
                   if not unicodedata.category(c).startswith('M') and c != 'ـ')


def acceptable(word, script):
    s = SCRIPTS[script]
    if not word or not s['letter'](word[0]):
        return False
    if not all(s['letter'](c) or c in s['extra'] for c in word):
        return False
    if script == 'hebrew' and any(c in HEBREW_FINALS for c in word[:-1]):
        return False  # run-together words in the AOSP list ("זמיןלא")
    return True


def read_aosp(path):
    opener = gzip.open if path.endswith('.gz') else open
    with opener(path, 'rt', encoding='utf-8') as f:
        for line in f:
            if not line.startswith(' word='):
                continue
            fields = dict(kv.split('=', 1) for kv in line.strip().split(',') if '=' in kv)
            yield fields['word'], int(fields['f']), fields.get('flags', '')


def read_leipzig(paths):
    counts = {}
    for path in paths:
        with open(path, encoding='utf-8') as f:
            for line in f:
                parts = line.rstrip('\n').split('\t')
                if len(parts) != 3 or ' ' in parts[1]:
                    continue
                counts[parts[1]] = counts.get(parts[1], 0) + int(parts[2])
    return counts


def main():
    fmt, script, limit, dst = sys.argv[1:5]
    sources = sys.argv[5:]
    words = {}  # word -> (f, flags)
    if fmt == 'aosp':
        for raw, f, flags in read_aosp(sources[0]):
            w = normalise(raw)
            if acceptable(w, script) and f > words.get(w, (-1, ''))[0]:
                words[w] = (f, flags)
    elif fmt == 'leipzig':
        counts = {}
        for raw, n in read_leipzig(sources).items():
            w = normalise(raw)
            if acceptable(w, script):
                counts[w] = counts.get(w, 0) + n
        top = max(counts.values())
        for w, n in counts.items():
            f = round(210 + 21 * math.log10(n / top))
            words[w] = (max(1, min(255, f)), '')
    else:
        raise SystemExit('unknown input format ' + fmt)
    ranked = sorted(words.items(), key=lambda kv: (-kv[1][0], kv[0]))
    if int(limit) > 0:
        ranked = ranked[:int(limit)]
    header = dict(SCRIPTS[script]['header'], date=os.environ.get('SOURCE_DATE_EPOCH', str(int(time.time()))), version='1')
    with open(dst, 'w', encoding='utf-8') as out:
        out.write(','.join('%s=%s' % kv for kv in header.items()) + '\n')
        for w, (f, flags) in ranked:
            out.write(' word=%s,f=%d,flags=%s,originalFreq=%d\n' % (w, f, flags, f))
    print('%s: %d words written (%d candidates), f %d..%d'
          % (dst, len(ranked), len(words), ranked[0][1][0], ranked[-1][1][0]))


if __name__ == '__main__':
    main()
