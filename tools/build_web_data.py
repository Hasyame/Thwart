# -*- coding: utf-8 -*-
"""Builds web/data/*.json for the web companion.

The whole card catalogue ships with the page rather than being fetched at
runtime: 4,375 cards trim to about 2.3 MB, which is 0.3 MB over the wire once
the server gzips it. That is cheaper than a round trip, and it means the page
works on a train.

Only the fields the companion actually shows are kept. MarvelCDB returns 55 per
card and the page reads about 30 of them; carrying the rest would triple the
download for nothing.

French comes from the locale subdomain, not a query parameter -- the same thing
the Android app learned the hard way.

Usage:
  python tools/build_web_data.py
"""
import io
import json
import os
import shutil
import urllib.request

SOURCES = {
    "en": "https://marvelcdb.com/api/public/cards/?encounter=1",
    "fr": "https://fr.marvelcdb.com/api/public/cards/?encounter=1",
}
OUTPUT_DIR = "web/data"
RULES_SOURCE = "app/src/main/assets/rules_reference.json"

# What the companion renders. Anything not here is not shown, so shipping it
# would only cost the reader bandwidth.
KEEP = (
    "code", "name", "subname", "type_name", "type_code",
    "faction_name", "faction_code", "pack_name", "pack_code",
    "card_set_name", "card_set_type_name_code", "set_code",
    "text", "flavor", "traits", "back_text", "back_name",
    "cost", "thwart", "attack", "defense", "health", "hand_size",
    "resource_energy", "resource_mental", "resource_physical", "resource_wild",
    "threat", "base_threat", "escalation_threat", "scheme_acceleration",
    "boost", "attack_cost", "thwart_cost", "stage", "quantity", "is_unique",
    "imagesrc", "backimagesrc",
)


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "Thwart-Web/1.0"})
    with urllib.request.urlopen(request) as response:
        return json.loads(response.read().decode("utf-8"))


def trim(card):
    # Zero and empty are dropped rather than written out: a card with no attack
    # value and a card with an attack of 0 are different things, and MarvelCDB
    # uses absence for the first. Keeping 0 would print a stat the card has not
    # got.
    return {key: card[key] for key in KEEP
            if key in card and card[key] not in (None, "", 0, False)}


def main():
    if not os.path.isdir(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)

    for locale, url in SOURCES.items():
        cards = fetch(url)
        slim = [trim(card) for card in cards]
        path = os.path.join(OUTPUT_DIR, "cards." + locale + ".json")
        with io.open(path, "w", encoding="utf-8", newline="\n") as out:
            json.dump(slim, out, ensure_ascii=False, separators=(",", ":"))
            out.write("\n")
        size = os.path.getsize(path) / 1024.0 / 1024.0
        print("%s: %d cards, %.2f MB" % (path, len(slim), size))

    # One rules file, shared with the app rather than copied by hand, so the two
    # cannot say different things about what retaliate does.
    shutil.copyfile(RULES_SOURCE, os.path.join(OUTPUT_DIR, "rules.json"))
    with io.open(RULES_SOURCE, encoding="utf-8") as handle:
        print("rules.json: %d entries" % len(json.load(handle)["entries"]))


main()
