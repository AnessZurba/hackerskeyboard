#!/usr/bin/env python3
# Builds app/src/main/assets/wordlists/<lang>.txt from an AOSP LatinIME wordlist, e.g.
#   curl -s "https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/main/dictionaries/en_US_wordlist.combined.gz?format=TEXT" | base64 -d > en_US.gz
#   python3 tools/mkwordlist.py en_US.gz app/src/main/assets/wordlists/en.txt 60000
import gzip, re, sys
src, dst, limit = sys.argv[1], sys.argv[2], int(sys.argv[3])
best = {}
ok = re.compile(r"^[a-z']+$")
with gzip.open(src, 'rt', encoding='utf-8') as f:
    for line in f:
        if not line.startswith(' word='):
            continue
        fields = dict(kv.split('=', 1) for kv in line.strip().split(',') if '=' in kv)
        if 'offensive' in fields.get('flags', '') or fields.get('not_a_word') == 'true':
            continue
        w = fields['word'].lower()
        if not ok.match(w) or w.strip("'") != w:
            continue
        fr = int(fields['f'])
        if fr > best.get(w, -1):
            best[w] = fr
top = sorted(best.items(), key=lambda kv: -kv[1])[:limit]
top.sort()
with open(dst, 'w', encoding='utf-8') as out:
    out.write('# English word frequencies for adaptive key targets.\n')
    out.write('# Derived from AOSP LatinIME dictionaries/en_US_wordlist.combined.gz (Apache License 2.0).\n')
    out.write('# Format: word<TAB>frequency (0-255, log scale), sorted by word.\n')
    for w, fr in top:
        out.write(f'{w}\t{fr}\n')
print(len(best), len(top))
