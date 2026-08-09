# -*- coding: utf-8 -*-
"""Builds app/src/main/assets/rules_reference.json from deejimy/mc-reference.

A keyword lookup that works at a table with no signal, in both languages.

The source is <https://github.com/deejimy/mc-reference>, a reference guide for
Marvel Champions maintained by deejimy and released under CC0. Every glossary
page carries the French text and the English it was translated from, side by
side, which is why it is used for both: the two can never drift apart, and the
guide tracks rulebook v1.8 rather than whatever a scraped web page happens to
show.

This replaced a scrape of MarvelCDB's rules page, which was English only and
came with the fragility you would expect — it once swallowed the site's own
analytics script into the last entry.

Usage:
  python tools/build_rules_reference.py
"""
import io
import json
import os
import re
import tarfile
import tempfile
import urllib.request

SOURCE_REPO = "https://github.com/deejimy/mc-reference"
SOURCE_TARBALL = SOURCE_REPO + "/archive/refs/heads/main.tar.gz"
LICENCE = "CC0-1.0"
OUTPUT = "app/src/main/assets/rules_reference.json"

# The English original sits in a collapsed block at the foot of each page, with
# the English term on the first line of it.
ENGLISH_BLOCK = re.compile(
    r'<details class="source">\s*<summary>[^<]*</summary>\s*(.*?)</details>',
    re.S,
)
HEADING = re.compile(r"^#\s+(.+)$", re.M)
# "See also" is a cross-reference between pages of a website, and means nothing
# in a list that has no links.
SEE_ALSO = re.compile(r"^\s*_?(?:Voir aussi|See also)\s*:.*$", re.M | re.I)
# Embedded images, written the Obsidian way — ![[pion_acceleration.png]]. They
# have to go before the link rules below, which would otherwise keep the file
# name and print "!pion_acceleration.png" in the middle of a sentence.
EMBEDDED_IMAGE = re.compile(r"!\[\[[^\]]*\]\]")
MD_IMAGE = re.compile(r"!\[[^\]]*\]\([^)]*\)")
WIKI_LINK = re.compile(r"\[\[([^\]|]+)(?:\|([^\]]+))?\]\]")
MD_LINK = re.compile(r"\[([^\]]+)\]\([^)]*\)")
HTML_TAG = re.compile(r"<[^>]+>")
ADMONITION = re.compile(r"^\s*(?:!!!|\?\?\?).*$", re.M)
EMPHASIS = re.compile(r"_([^_\n]+)_")


def fetch_source(into):
    """Downloads the reference guide and returns the extracted docs directory."""
    archive = os.path.join(into, "mc-reference.tar.gz")
    request = urllib.request.Request(
        SOURCE_TARBALL, headers={"User-Agent": "Thwart/1.0"}
    )
    with urllib.request.urlopen(request) as response, io.open(archive, "wb") as out:
        out.write(response.read())
    with tarfile.open(archive) as tar:
        tar.extractall(into)
    for name in os.listdir(into):
        docs = os.path.join(into, name, "docs")
        if os.path.isdir(docs):
            return docs
    raise SystemExit("no docs/ directory in the downloaded archive")


def readable(text):
    """Markdown and site markup down to something a phone can print."""
    text = SEE_ALSO.sub("", text)
    text = ADMONITION.sub("", text)
    text = EMBEDDED_IMAGE.sub("", text)
    text = MD_IMAGE.sub("", text)
    # [[Page|Label]] is a link to another page of the guide; the label is the
    # only part that means anything here.
    text = WIKI_LINK.sub(lambda m: m.group(2) or m.group(1), text)
    text = MD_LINK.sub(lambda m: m.group(1), text)
    text = HTML_TAG.sub("", text)
    text = text.replace("`", "").replace("**", "").replace("*", "")
    # Italics are written with underscores in the French pages — "_Voir :
    # Effet d'altération_" — and printed as-is they look like a typo. Only a
    # matched pair is unwrapped, so an underscore inside a word is left alone.
    text = EMPHASIS.sub(lambda m: m.group(1), text)
    lines = []
    for line in text.split("\n"):
        line = line.rstrip()
        # A dash at the start of a line is a bullet everywhere in this guide.
        line = re.sub(r"^(\s*)-\s+", lambda m: m.group(1) + "• ", line)
        lines.append(line.strip() if not line.startswith(" ") else line)
    text = "\n".join(lines)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def glossary_entries(docs):
    """One entry per glossary page, with both languages."""
    root = os.path.join(docs, "Glossaire")
    entries = []
    for folder, _, names in os.walk(root):
        for name in sorted(names):
            if not name.endswith(".md"):
                continue
            raw = io.open(os.path.join(folder, name), encoding="utf-8").read()

            english = ENGLISH_BLOCK.search(raw)
            if not english:
                # A page with no English original is a page this cannot key on.
                continue
            english_lines = english.group(1).strip().split("\n")
            term = english_lines[0].strip().strip('"')
            english_body = readable("\n".join(english_lines[1:]))

            heading = HEADING.search(raw)
            term_fr = heading.group(1).strip() if heading else term
            french_body = readable(raw[heading.end():english.start()] if heading
                                   else raw[:english.start()])

            if not term or not english_body or not french_body:
                continue
            entries.append({
                "term": term,
                "termFr": term_fr,
                "en": english_body,
                "fr": french_body,
            })
    return entries


def structural_entries(docs, existing):
    """The sections that are not glossary terms: the golden rules and friends.

    French comes from the guide's front page. It carries no English there, so
    the English is whatever the previous build already had for the same section
    — the four of them are the rules everybody knows by heart and have not
    changed. A section with no English to pair with is left out rather than
    shipped half translated.
    """
    index = io.open(os.path.join(docs, "index.md"), encoding="utf-8").read()
    known = {
        "Les règles d'Or": "THE GOLDEN RULES",
        "La Sombre Règle": "THE GRIM RULE",
        "Limitation du Matériel": "COMPONENT LIMITATIONS",
        "Déroulement d'un Round": "ROUND OVERVIEW",
    }
    sections = dict(re.findall(r"^##\s+(.+?)\s*$\n(.*?)(?=^##\s|\Z)",
                               index, re.M | re.S))
    entries = []
    for french_title, english_term in known.items():
        body = sections.get(french_title)
        english = existing.get(english_term)
        if not body or not english:
            continue
        entries.append({
            "term": english_term,
            "termFr": french_title,
            "en": english,
            "fr": readable(body),
        })
    return entries


def previous_english():
    """The English text the last build produced, by term."""
    if not os.path.exists(OUTPUT):
        return {}
    with io.open(OUTPUT, encoding="utf-8") as handle:
        return {e["term"]: e["en"] for e in json.load(handle).get("entries", [])}


def main():
    existing = previous_english()
    with tempfile.TemporaryDirectory() as workspace:
        docs = fetch_source(workspace)
        entries = structural_entries(docs, existing) + glossary_entries(docs)

    entries.sort(key=lambda e: e["term"])
    document = {
        "_note": (
            "Generated by tools/build_rules_reference.py from " + SOURCE_REPO
            + " (" + LICENCE + "), a Marvel Champions reference guide maintained by "
            "deejimy. Every glossary page there carries the French text beside the "
            "English it was translated from, so both languages come from one source "
            "and cannot drift apart. Do not edit this file by hand."
        ),
        "source": SOURCE_REPO,
        "sourceLicence": LICENCE,
        "sourceCredit": "deejimy",
        "entries": entries,
    }
    with io.open(OUTPUT, "w", encoding="utf-8", newline="\n") as out:
        json.dump(document, out, ensure_ascii=False, indent=2)
        out.write("\n")

    missing_fr = [e["term"] for e in entries if not e["fr"]]
    longest = max(entries, key=lambda e: len(e["en"]))
    print("entries:", len(entries))
    print("first:", entries[0]["term"], "| last:", entries[-1]["term"])
    print("longest:", longest["term"], len(longest["en"]), "chars")
    print("without French:", len(missing_fr))


main()
