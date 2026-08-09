# -*- coding: utf-8 -*-
"""Builds docs/game_contents.json from a list of what the boxes contain.

The structure comes from the boxes, which is the whole point: MarvelCDB is
missing nineteen scenarios, and it cannot say which pack a difficulty arrived
in at all. Codes and French names are filled in from the MarvelCDB API where
they exist and left null where they do not, so the gaps are visible rather than
papered over with the English name.

Usage:
  python tools/build_game_contents.py
"""
import json
import io
import os
import urllib.request

EN = "https://marvelcdb.com/api/public/cards/?encounter=1"
FR = "https://fr.marvelcdb.com/api/public/cards/?encounter=1"
EN_PACKS = "https://marvelcdb.com/api/public/packs/"
FR_PACKS = "https://fr.marvelcdb.com/api/public/packs/"
CONTENTS = "docs/game_contents.json"

# --- the boxes ---------------------------------------------------------------
# (name, code, type, scenarios, modular sets, difficulties the box adds)
# Names are English; the French comes from the API below.
WAVES = [
    (0, [
        ("Core Set", "core", "core",
         ["Rhino", "Klaw", "Ultron"],
         ["Bomb Scare", "Masters of Evil", "Under Attack", "Legions of Hydra",
          "The Doomsday Chair"],
         ["Standard I", "Expert I"]),
    ]),
    (1, [
        ("The Green Goblin", "gob", "scenario_pack",
         ["Risky Business", "Mutagen Formula"],
         ["Goblin Gimmicks", "A Mess of Things", "Power Drain",
          "Running Interference"],
         []),
        ("The Wrecking Crew", "twc", "scenario_pack",
         ["Wrecking Crew"], [], []),
        ("The Rise of Red Skull", "trors", "campaign_box",
         ["Crossbones", "Absorbing Man", "Taskmaster", "Zola", "Red Skull"],
         ["Hydra Assault", "Weapon Master", "Hydra Patrol"],
         []),
    ]),
    (2, [
        ("The Once and Future Kang", "toafk", "scenario_pack",
         ["Kang"],
         ["Temporal", "Anachronauts", "Master of Time"],
         []),
        ("Galaxy's Most Wanted", "gmw", "campaign_box",
         ["Brotherhood of Badoon", "Infiltrate the Museum", "Escape the Museum",
          "Nebula", "Ronan"],
         ["Band of Badoon", "Galactic Artifacts", "Kree Militants",
          "Menagerie Medley", "Space Pirates", "Badoon Headhunter",
          "Ship Command"],
         []),
    ]),
    (3, [
        ("The Mad Titan's Shadow", "mts", "campaign_box",
         ["Ebony Maw", "Tower Defense", "Thanos", "Hela", "Loki"],
         ["The Black Order", "Armies of Titan", "Children of Thanos",
          "Infinity Gauntlet", "Legions of Hel", "Frost Giants", "Enchantress"],
         []),
    ]),
    (4, [
        ("The Hood", "hood", "scenario_pack",
         ["The Hood"],
         ["Beasty Boys", "Brothers Grimm", "Mister Hyde", "Wrecking Crew",
          "Sinister Syndicate", "Crossfire's Crew", "Ransacked Armory",
          "State of Emergency", "Streets of Mayhem"],
         ["Standard II", "Expert II"]),
        ("Sinister Motives", "sm", "campaign_box",
         ["Sandman", "Venom", "Mysterio", "Sinister Six", "Venom Goblin"],
         ["City in Chaos", "Down to Earth", "Goblin Gear", "Guerilla Tactics",
          "Osborn Tech", "Personal Nightmare", "Sinister Assault",
          "Symbiotic Strength", "Whispers of Paranoia"],
         []),
    ]),
    (5, [
        ("Mutant Genesis", "mut_gen", "campaign_box",
         ["Sabretooth", "Project Wideawake", "Master Mold", "Mansion Attack",
          "Magneto"],
         ["Mystique", "Brotherhood", "Operation Zero Tolerance", "Sentinels",
          "Acolytes", "Future Past"],
         []),
    ]),
    (6, [
        ("MojoMania", "mojo", "scenario_pack",
         ["Magog", "Spiral", "Mojo"],
         ["Crime", "Fantasy", "Horror", "Sci-Fi", "Sitcom", "Western"],
         []),
        ("NeXt Evolution", "next_evol", "campaign_box",
         ["Morlock Siege", "On the Run", "Juggernaut", "Mister Sinister",
          "Stryfe"],
         [],
         []),
    ]),
    (7, [
        ("The Age of Apocalypse", "aoa", "campaign_box",
         ["Unus", "Four Horsemen", "Apocalypse", "Dark Beast", "En Sabah Nur"],
         ["Infinites", "Dystopian Nightmare", "Hounds", "Dark Riders",
          "Savage Land", "Genosha", "Blue Moon", "Celestial Tech",
          "Clan Akkaba", "Age of Apocalypse"],
         ["Standard III"]),
    ]),
    (8, [
        ("Agents of S.H.I.E.L.D.", "aos", "campaign_box",
         ["Black Widow", "Batroc", "M.O.D.O.K.", "Thunderbolts", "Baron Zemo"],
         ["A.I.M. Abduction", "A.I.M. Science", "Batroc's Brigade",
          "Scientist Supreme", "S.H.I.E.L.D."],
         []),
    ]),
    (9, [
        ("Trickster Takeover", "tt", "scenario_pack",
         ["Enchantress", "Loki, God of Lies"],
         ["Trickster Magic"],
         []),
        ("Civil War", "cw", "campaign_box",
         ["Resistance: Captain Marvel", "Resistance: Iron Man",
          "Resistance: Spider-Woman", "Resistance: Captain America",
          "Registration: Captain Marvel", "Registration: Iron Man",
          "Registration: Spider-Woman", "Registration: Captain America"],
         ["Dangerous Recruits", "Mighty Avengers", "Heroes For Hire", "Paladin",
          "Cape-Killer", "The Initiative", "Martial Law", "Maria Hill",
          "New Avengers", "Secret Avengers", "Namor", "Atlanteans",
          "Spider-Man", "Defenders", "Hell's Kitchen"],
         []),
    ]),
    (10, [
        ("Synthezoid Smackdown", "synthezoid", "scenario_pack",
         ["Resistance: She-Hulk", "Registration: She-Hulk"],
         ["S.H.I.E.L.D. Ops", "Thunderbolts", "Taskmaster", "Deadly Duo",
          "Vision", "Young Avengers", "Scarlet Twins", "Moon Knight",
          "Royal Guard"],
         []),
        # Bullseye and Electro are scenarios, not modular sets. Fear No Evil
        # fields six villains, and the campaign draws which one you face.
        ("Fear No Evil", "fne", "campaign_box",
         ["The Getaway", "Protection Racket", "The Raft Breakout", "Kingpin",
          "Bullseye", "Electro"],
         [],
         []),
    ]),
    (11, [
        ("Shadowland", "shadowland", "scenario_pack",
         ["Shadows in the Night", "Shadow Labyrinth", "Heart of Shadow"],
         [],
         []),
    ]),
]

# Civil War and Synthezoid Smackdown share one restricted modular pool and draw
# more sets than anything else. The app reads both rules off the cards now — a
# pack with leaders in it is a versus pack — so this is documentation of the
# boxes rather than something the app consults.
SPECIAL = {
    "cw": {"modularCount": [3, 4], "modularsRestrictedTo": ["cw", "synthezoid"]},
    "synthezoid": {"modularCount": [3, 4], "modularsRestrictedTo": ["cw", "synthezoid"]},
}


# Where the box and MarvelCDB spell the same scenario differently. Kept
# explicit rather than fuzzy-matched: a near-miss matcher would happily bind
# "Sinister Six" to "Mister Sinister", and a wrong code is worse than a gap.
ALIASES = {
    "ronan": "ronan the accuser",
    "sinister six": "the sinister six",
    "m.o.d.o.k.": "m.o.d.o.k",
    "loki, god of lies": "god of lies",
}


def fetch(url):
    with urllib.request.urlopen(url) as response:
        return json.loads(response.read().decode("utf-8"))


def english_index(cards, kind):
    """Lowercased set name to (code, name), for one card set type."""
    out = {}
    for card in cards:
        if card.get("card_set_type_name_code") != kind:
            continue
        code, name = card.get("card_set_code"), card.get("card_set_name")
        if code and name:
            out[name.strip().lower()] = (code, name)
    return out


def french_names(cards, kind):
    out = {}
    for card in cards:
        if card.get("card_set_type_name_code") == kind and card.get("card_set_code"):
            out[card["card_set_code"]] = card.get("card_set_name")
    return out


def existing_french():
    """French names already in the file, so regenerating never loses them.

    Translating is slow, hand work; regenerating is a command somebody runs
    without thinking. Whatever is in the file wins over whatever the API says,
    because the file is where a person who owns the cards wrote it down.
    """
    try:
        with io.open("docs/game_contents.json", encoding="utf-8") as f:
            previous = json.load(f)
    except (OSError, ValueError):
        return {}
    kept = {}
    for wave in previous.get("waves", []):
        for pack in wave.get("packs", []):
            for key in ("scenarios", "modularSets"):
                for entry in pack.get(key, []):
                    if entry.get("fr"):
                        kept[(key, entry["en"])] = entry["fr"]
    return kept


def pack_names():
    """Packs whose title MarvelCDB returns in English on its French endpoint.

    Found rather than listed: a pack whose French name is byte-for-byte the
    English one either needs translating or is named after a character and never
    will be. Both look the same from here, so both are written out with `fr`
    null and a person decides which is which — the same way the set names were
    done. Anything already filled in is kept.
    """
    kept = {}
    if os.path.exists(CONTENTS):
        with io.open(CONTENTS, encoding="utf-8") as handle:
            for entry in json.load(handle).get("packNames", []):
                if entry.get("code") and entry.get("fr"):
                    kept[entry["code"]] = entry["fr"]

    english = {p["code"]: p["name"] for p in fetch(EN_PACKS)}
    french = {p["code"]: p["name"] for p in fetch(FR_PACKS)}
    rows = [
        {"code": code, "en": name, "fr": kept.get(code)}
        for code, name in sorted(english.items(), key=lambda item: item[1])
        if french.get(code) == name
    ]
    return rows


def pack_corrections(document):
    """French pack titles, from the `packNames` block of the reference file.

    Kept in their own section of the generated file rather than mixed in with
    the set corrections: thirty-four codes name both a pack and an encounter set
    (`deadpool`, `magneto`, `gambit`), so one shared table would rename the set
    whenever the pack was corrected.

    A pack whose French title is the English one — every pack named after a
    character — belongs nowhere near this file. Only a genuine translation
    counts.
    """
    corrections = {}
    for entry in document.get("packNames", []):
        code, french = entry.get("code"), entry.get("fr")
        if code and french and french != entry.get("en"):
            corrections[code] = french
    return corrections


def write_overrides(waves, api_french, packs=None):
    """Emits the file the app reads, from the file a person edits.

    Two files existed and only one of them was displayed, so a name typed into
    the reference list did nothing until somebody copied it across. This derives
    the app's copy, which means the reference list is the single place to work.

    Only names the card database gets wrong get through. Writing every French
    name here would work and be a mistake: it would freeze a copy of MarvelCDB's
    translations into the app, so a name they fixed upstream would stay wrong
    locally forever. A correction file should contain corrections.
    """
    corrections = {}
    for wave in waves:
        for pack in wave["packs"]:
            for entry in pack["scenarios"] + pack["modularSets"]:
                code, french = entry["code"], entry["fr"]
                if not code or not french:
                    continue
                # Nothing to correct when the card database already says this,
                # nor when it says nothing but neither do we.
                if french != api_french.get(code) and french != entry["en"]:
                    corrections[code] = french

    document = {
        "_note": (
            "Generated by tools/build_game_contents.py from docs/game_contents.json. "
            "Edit that file, not this one. Set names MarvelCDB has not translated, or has "
            "translated in a way that does not match the printed cards; a code absent here "
            "keeps whatever the card database says."
        ),
        "fr": dict(sorted(corrections.items())),
        "packs": {"fr": dict(sorted((packs or {}).items()))},
    }
    with io.open("app/src/main/assets/set_name_overrides.json", "w",
                 encoding="utf-8", newline="\n") as out:
        json.dump(document, out, ensure_ascii=False, indent=2)
        out.write("\n")
    print("app corrections written:", len(corrections), "sets,", len(packs or {}), "packs")


def main():
    kept = existing_french()
    en_cards, fr_cards = fetch(EN), fetch(FR)
    en_villain = english_index(en_cards, "villain")
    en_modular = english_index(en_cards, "modular")
    fr_villain = french_names(fr_cards, "villain")
    fr_modular = french_names(fr_cards, "modular")

    def resolve(name, index, french, kind):
        key = name.strip().lower()
        hit = index.get(ALIASES.get(key, key)) or index.get(key)
        if not hit:
            return {"en": name, "code": None, "fr": kept.get((kind, name))}
        code, en_name = hit
        translated = french.get(code)
        from_api = translated if translated and translated != en_name else None
        return {
            "en": en_name,
            "code": code,
            # Hand-written first, then the API. null rather than the English
            # name: a gap you can see is a gap somebody can fill.
            "fr": kept.get((kind, en_name)) or kept.get((kind, name)) or from_api,
        }

    waves = []
    for wave, packs in WAVES:
        entries = []
        for name, code, kind, scenarios, modulars, difficulties in packs:
            entry = {
                "name": name,
                "code": code,
                "type": kind,
                "scenarios": [
                    resolve(s, en_villain, fr_villain, "scenarios") for s in scenarios
                ],
                "modularSets": [
                    resolve(m, en_modular, fr_modular, "modularSets") for m in modulars
                ],
                "difficultiesAdded": difficulties,
            }
            if code in SPECIAL:
                entry["special"] = SPECIAL[code]
            entries.append(entry)
        waves.append({"wave": wave, "packs": entries})

    document = {
        "_note": (
            "What the boxes contain, listed by somebody holding them. The structure is "
            "authoritative. Codes and French names come from MarvelCDB and are null where "
            "MarvelCDB has nothing, which is how you find what still needs translating or "
            "entering. Regenerate with tools/build_game_contents.py."
        ),
        "generatedFrom": [EN, FR],
        "packNames": pack_names(),
        "waves": waves,
    }

    with io.open("docs/game_contents.json", "w", encoding="utf-8", newline="\n") as out:
        json.dump(document, out, ensure_ascii=False, indent=2)
        out.write("\n")

    write_overrides(
        waves,
        {**fr_villain, **fr_modular},
        packs=pack_corrections(document),
    )
    pending = [e["en"] for e in document["packNames"] if not e["fr"]]
    if pending:
        print("pack titles still in English:", len(pending))
        for name in pending:
            print("   ", name)

    scenarios = [s for w in waves for p in w["packs"] for s in p["scenarios"]]
    modulars = [m for w in waves for p in w["packs"] for m in p["modularSets"]]
    print(
        "scenarios:", len(scenarios),
        "| not in MarvelCDB:", sum(1 for s in scenarios if not s["code"]),
        "| no French name:", sum(1 for s in scenarios if not s["fr"]),
    )
    print(
        "modular sets:", len(modulars),
        "| not in MarvelCDB:", sum(1 for m in modulars if not m["code"]),
        "| no French name:", sum(1 for m in modulars if not m["fr"]),
    )
    print("difficulties by pack:", {
        d: p["code"] for w in waves for p in w["packs"] for d in p["difficultiesAdded"]
    })


main()
