#!/usr/bin/env python3
"""Push the vault's dictionary.md to PUT /v1/dictionary.

Usage: sync.py <dictionary.md> <base URL> [--dry-run]
Token comes from the SYNC_TOKEN environment variable. Read-only on the vault.
Only the first column and the "Mis-heard as" column are ever sent.
"""
import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request

MISHEARD_HEADER = "mis-heard as"
WIKILINK = re.compile(r"\[\[([^\]]*)\]\]")
MD_LINK = re.compile(r"\[([^\]]*)\]\([^)]*\)")
# Fragments of the mis-heard cell that are notes, not mis-hearings.
NOTE = re.compile(r"^(recorded|note|see|added|confirmed)\b|\d{4}-\d{2}-\d{2}", re.I)


def clean_text(s):
    s = WIKILINK.sub(lambda m: m.group(1).split("|")[-1].rsplit("/", 1)[-1], s)
    s = MD_LINK.sub(r"\1", s)
    s = re.sub(r"[*_`]+", "", s)
    return re.sub(r"\s+", " ", s).strip()


def split_row(line):
    line = line.strip()
    if line.startswith("|"):
        line = line[1:]
    if line.endswith("|") and not line.endswith("\\|"):
        line = line[:-1]
    return [c.strip() for c in re.split(r"(?<!\\)\|", line)]


def is_separator(cells):
    return any(cells) and all(re.fullmatch(r":?-{2,}:?", c) for c in cells if c)


def parse_misheard(cell):
    out = []
    for part in re.split(r"[,;]", cell):
        for frag in re.split(r"\.\s+", part):  # "Chris Fippin. Recorded 2026-09-23"
            frag = clean_text(frag).rstrip(".")
            # Real mis-hearings are short; longer or quoted fragments are prose notes.
            if frag and not NOTE.search(frag) and '"' not in frag and len(frag.split()) <= 4:
                out.append(frag)
    return out


def parse(text):
    terms, seen = [], set()
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        if (lines[i].lstrip().startswith("|") and i + 1 < len(lines)
                and is_separator(split_row(lines[i + 1]))):
            header = [h.lower() for h in split_row(lines[i])]
            mh = header.index(MISHEARD_HEADER) if MISHEARD_HEADER in header else None
            i += 2
            while i < len(lines) and lines[i].lstrip().startswith("|"):
                cells = split_row(lines[i])
                i += 1
                term = clean_text(cells[0]) if cells else ""
                if not term or term.lower() in seen:
                    continue
                seen.add(term.lower())
                mis = parse_misheard(cells[mh]) if mh is not None and mh < len(cells) else []
                mis = [m for m in dict.fromkeys(mis) if m.lower() != term.lower()]
                terms.append({"term": term, "misheard": mis})
        else:
            i += 1
    # Stable: terms with mis-hearings first, then the rest.
    return sorted(terms, key=lambda t: not t["misheard"])


def put(base, token, payload):
    req = urllib.request.Request(
        base.rstrip("/") + "/v1/dictionary",
        data=json.dumps(payload).encode(),
        method="PUT",
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("path")
    ap.add_argument("base_url")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv)
    with open(args.path, encoding="utf-8") as f:  # read-only
        payload = {"terms": parse(f.read())}
    if args.dry_run:
        print(json.dumps(payload, indent=2, ensure_ascii=False))
        return 0
    token = os.environ.get("SYNC_TOKEN")
    if not token:
        print("error: SYNC_TOKEN is not set", file=sys.stderr)
        return 2
    try:
        res = put(args.base_url, token, payload)
    except urllib.error.HTTPError as e:
        print(f"error: HTTP {e.code}: {e.read().decode(errors='replace')[:200]}", file=sys.stderr)
        return 1
    except urllib.error.URLError as e:
        print(f"error: {e.reason}", file=sys.stderr)
        return 1
    print(f"sent {len(payload['terms'])}, server count {res.get('count')}")
    return 0 if res.get("count") == len(payload["terms"]) else 1


if __name__ == "__main__":
    sys.exit(main())
