# -*- coding: utf-8 -*-
"""Builds The Mad Titan's Shadow campaign template.

The campaign's memory is its "campaign pool": cards earned in one scenario that
change the setup of later ones. That is modelled as a card list rather than a
set of flags, because it is literally a pool of cards — which also lets the app
show each one by name with a preview.

Two recorded facts are not cards (the tower took damage; a scheme was
completed), so those stay flags. Flags are read with countTrue >= 1 rather than
a bare flag test: the engine files a flag under the scenario that set it, so a
plain read only sees the scenario it was written in.
"""
import json, io

# --- campaign pool cards, all reverse faces of the campaign side schemes -----
COSMO = "21180b"           # back of Secure the Landing Pad
BLACK_SWAN = "21182b"      # back of Save the Shawarma Place
DEFENSIVE_PROTOCOLS = "21184b"   # back of Hack Sanctuary's Computer
ODINS_ARMOR = "21186b"     # back of Find the Norn Stones
SECURITY_BREACH = "21181"
SHAWARMA = "21183"
SYSTEM_SHOCK = "21185"
NORN_STONE = "21187a"
ODIN = "21139a"
SUMMONED_BACK = "21188"

LOKIS = ["21160", "21161", "21162", "21163", "21164"]


def t(fr, en):
    return {"fr": fr, "en": en}


def pool(code):
    """True once this card is in the campaign pool."""
    return {"cardList": "campaignPool", "contains": code}


def flag(name):
    """A recorded fact. Counted rather than read, see the module docstring."""
    return {"countTrue": name, "countAtLeast": 1}


def vp_prompt():
    return {
        "id": "vp",
        "type": "number",
        "label": t(
            "Combien de points de victoire avez-vous accumulés ?",
            "How many victory points did you accumulate?",
        ),
    }


def hp_prompt():
    return {
        "id": "hpPerHero",
        "type": "perHeroNumber",
        "label": t("Points de vie restants", "Remaining hit points"),
        "when": {"difficulty": "expert"},
    }


def hp_effect():
    return {
        "op": "setHeroCounter",
        "counter": "hp",
        "from": "hpPerHero",
        "when": {"difficulty": "expert"},
    }


def expert_steps():
    """Hit points carried between scenarios, on an Expert campaign only."""
    return [
        {
            "text": t(
                "Expert : régler les points de vie de chaque héros sur la valeur ci-dessous",
                "Expert: set each hero's hit points to the value below",
            ),
            "when": {"difficulty": "expert"},
            "showCounter": "hp",
        },
        {
            "text": t(
                "Expert : placer un pion accélération sur une manigance principale "
                "pour soigner un héros à fond",
                "Expert: place an acceleration token on a main scheme to heal a hero to full",
            ),
            "when": {"difficulty": "expert"},
            "action": {
                "id": "heal",
                "label": t("Soigner", "Heal"),
                "perHero": True,
                "effects": [{"op": "setHeroCounter", "counter": "hp", "value": 999}],
            },
        },
    ]


# Setup steps the pool drives, shared by the scenarios that use them.
def shawarma_step():
    return {
        "text": t(
            "Chaque joueur mélange un exemplaire de cette carte dans son deck",
            "Each player shuffles one copy of this card into their deck",
        ),
        "cards": [SHAWARMA],
        "when": pool(SHAWARMA),
    }


def system_shock_step():
    return {
        "text": t(
            "Chaque joueur mélange un exemplaire de cette obligation dans son deck",
            "Each player shuffles one copy of this obligation into their deck",
        ),
        "cards": [SYSTEM_SHOCK],
        "when": pool(SYSTEM_SHOCK),
    }


def security_breach_step():
    return {
        "text": t(
            "Mélanger cette manigance annexe dans le deck rencontre",
            "Shuffle this side scheme into the encounter deck",
        ),
        "cards": [SECURITY_BREACH],
        "when": pool(SECURITY_BREACH),
    }


def infinity_stones_step():
    return {
        "text": t(
            "Chaque joueur défausse la moitié supérieure de son deck",
            "Each player discards the top half of their deck",
        ),
        "when": flag("infinityStones1B"),
    }


scenarios = []

# ---------------------------------------------------------------- scenario 1
scenarios.append({
    "id": "s1_ebony_maw",
    "name": t("Ebony Maw", "Ebony Maw"),
    "flavour": t(
        "Ebony Maw assiège Knowhere pour y prendre la Pierre de Pouvoir. Le Corps de Knowhere "
        "ne tiendra pas seul.",
        "Ebony Maw is storming Knowhere for the Power Stone. The Knowhere Corps will not hold "
        "on their own.",
    ),
    "victoryLabel": t("Ebony Maw est vaincu !", "Ebony Maw is beaten!"),
    "defeatLabel": t("Ebony Maw vous a vaincus !", "Ebony Maw has beaten you!"),
    "baseSetup": {
        "villainDeck": {"standard": ["21071", "21072"], "expert": ["21072", "21073"]},
        "mainScheme": ["21074a", "21075a"],
        "encounterSets": ["ebony_maw", "black_order", "armies_of_titan", "standard"],
    },
    "campaignSetup": [
        {
            "text": t(
                "Mettre cette manigance annexe en jeu",
                "Put this side scheme into play",
            ),
            "cards": ["21180a"],
        },
        {
            "text": t(
                "Mélanger cette manigance annexe dans le deck rencontre",
                "Shuffle this side scheme into the encounter deck",
            ),
            "cards": [SECURITY_BREACH],
        },
    ],
    "onVictory": {
        "prompts": [
            vp_prompt(),
            {
                "id": "landingPadDefeated",
                "type": "boolean",
                "label": t(
                    "Avez-vous déjoué {card:21180a} ?",
                    "Did you defeat {card:21180a}?",
                ),
                "cards": ["21180a"],
            },
            {
                "id": "knowhere1B",
                "type": "boolean",
                "label": t(
                    "Avez-vous complété l'étape 1B de la manigance principale ?",
                    "Did you complete main scheme stage 1B?",
                ),
                "cards": ["21074a"],
            },
            hp_prompt(),
        ],
        "effects": [
            {
                "op": "addCard",
                "cardList": "campaignPool",
                "cardCode": COSMO,
                "when": {"answer": "landingPadDefeated"},
            },
            {
                "op": "addCard",
                "cardList": "campaignPool",
                "cardCode": SECURITY_BREACH,
                "when": {"answer": "knowhere1B"},
            },
            hp_effect(),
        ],
        "next": [{"goto": "s2_tower_defense"}],
    },
    "onDefeat": {"next": [{"goto": "s1_ebony_maw"}]},
})

# ---------------------------------------------------------------- scenario 2
scenarios.append({
    "id": "s2_tower_defense",
    "name": t("Tower Defense", "Tower Defense"),
    "flavour": t(
        "Corvus Glaive et Proxima Midnight frappent la Tour des Avengers pour s'emparer de la "
        "Pierre de Réalité.",
        "Corvus Glaive and Proxima Midnight strike Avengers Tower to seize the Reality Stone.",
    ),
    "victoryLabel": t("Le Black Order est vaincu !", "The Black Order is beaten!"),
    "defeatLabel": t("Le Black Order vous a vaincus !", "The Black Order has beaten you!"),
    "baseSetup": {
        "villainDeck": {
            "standard": ["21092", "21093", "21095", "21096"],
            "expert": ["21093", "21094", "21096", "21097"],
        },
        "mainScheme": ["21098a", "21099a"],
        "encounterSets": ["tower_defense", "armies_of_titan", "standard"],
    },
    "campaignSetup": [
        {
            "text": t(
                "Mettre {card:21100} en jeu sur sa face Stronghold, et lui attacher "
                "{card:21101} à la manigance 2B",
                "Put {card:21100} into play on its Stronghold side, and attach {card:21101} "
                "to scheme 2B",
            ),
            "cards": ["21100", "21101"],
        },
        {
            "text": t(
                "Pour durcir la partie, placer des dégâts par joueur sur {card:21100} :\n"
                "  Standard : 1 par joueur\n"
                "  Expert : 2 par joueur\n"
                "  Héroïque : 3 par joueur",
                "To make it harder, place damage per player on {card:21100}:\n"
                "  Standard: 1 per player\n"
                "  Expert: 2 per player\n"
                "  Heroic: 3 per player",
            ),
            "cards": ["21100"],
        },
        {
            "text": t(
                "Mettre cette manigance annexe en jeu",
                "Put this side scheme into play",
            ),
            "cards": ["21182a"],
        },
        security_breach_step(),
    ] + expert_steps(),
    "onVictory": {
        "prompts": [
            vp_prompt(),
            {
                "id": "shawarmaDefeated",
                "type": "boolean",
                "label": t(
                    "Avez-vous déjoué {card:21182a} ?",
                    "Did you defeat {card:21182a}?",
                ),
                "cards": ["21182a"],
            },
            {
                "id": "blackSwanInVictory",
                "type": "boolean",
                "label": t(
                    "Avez-vous {card:21182b} dans la pile de victoire ?",
                    "Do you have {card:21182b} in the victory display?",
                ),
                "cards": ["21182b"],
            },
            {
                "id": "towerDamaged",
                "type": "boolean",
                "label": t(
                    "Avez-vous retourné {card:21100} sur sa face Damaged ?",
                    "Did you flip {card:21100} to its Damaged side?",
                ),
                "cards": ["21100"],
            },
            hp_prompt(),
        ],
        "effects": [
            {
                "op": "addCard",
                "cardList": "campaignPool",
                "cardCode": SHAWARMA,
                "when": {"answer": "shawarmaDefeated"},
            },
            {
                "op": "addCard",
                "cardList": "campaignPool",
                "cardCode": BLACK_SWAN,
                "when": {"notAnswer": "blackSwanInVictory"},
            },
            {"op": "setFlag", "flag": "towerDamaged", "from": "towerDamaged"},
            hp_effect(),
        ],
        "next": [{"goto": "s3_thanos"}],
    },
    "onDefeat": {"next": [{"goto": "s2_tower_defense"}]},
})

# ---------------------------------------------------------------- scenario 3
scenarios.append({
    "id": "s3_thanos",
    "name": t("Thanos", "Thanos"),
    "flavour": t(
        "Thanos tient les six Pierres. Il reste une seule occasion de l'arrêter, à bord de "
        "Sanctuary.",
        "Thanos holds all six Stones. There is one chance left to stop him, aboard Sanctuary.",
    ),
    "victoryLabel": t("Thanos est vaincu !", "Thanos is beaten!"),
    "defeatLabel": t("Thanos vous a vaincus !", "Thanos has beaten you!"),
    "baseSetup": {
        "villainDeck": {"standard": ["21111", "21112"], "expert": ["21112", "21113"]},
        "mainScheme": ["21114a", "21115a"],
        "encounterSets": [
            "thanos", "infinity_gauntlet", "black_order", "children_of_thanos", "standard",
        ],
    },
    "campaignSetup": [
        {
            "text": t(
                "Attacher l'Infinity Gauntlet à Thanos, puis mélanger les six Pierres à part, "
                "face cachée",
                "Attach the Infinity Gauntlet to Thanos, then shuffle the six Stones aside, facedown",
            ),
            "cards": ["21129"],
        },
        {
            "text": t(
                "Mettre cette manigance annexe en jeu",
                "Put this side scheme into play",
            ),
            "cards": ["21184a"],
        },
        {
            "text": t(
                "Mettre cet allié en jeu sous le contrôle du premier joueur",
                "Put this ally into play under the first player's control",
            ),
            "cards": [COSMO],
            "when": pool(COSMO),
        },
        security_breach_step(),
        shawarma_step(),
        {
            "text": t(
                "Mettre ce sbire en jeu, engagé avec le premier joueur",
                "Put this minion into play engaged with the first player",
            ),
            "cards": [BLACK_SWAN],
            "when": pool(BLACK_SWAN),
        },
        {
            "text": t(
                "Infliger 3 dégâts à chaque identité : la Tour des Avengers était endommagée",
                "Deal 3 damage to each identity: Avengers Tower was left damaged",
            ),
            "when": flag("towerDamaged"),
        },
    ] + expert_steps(),
    "onVictory": {
        "prompts": [
            vp_prompt(),
            {
                "id": "protocolsInVictory",
                "type": "boolean",
                "label": t(
                    "Avez-vous {card:" + DEFENSIVE_PROTOCOLS + "} dans la pile de victoire ?",
                    "Do you have {card:" + DEFENSIVE_PROTOCOLS + "} in the victory display?",
                ),
                "cards": ["21184b"],
            },
            {
                "id": "infinityStones1B",
                "type": "boolean",
                "label": t(
                    "Avez-vous complété l'étape 1B de la manigance principale ?",
                    "Did you complete main scheme stage 1B?",
                ),
                "cards": ["21114a"],
            },
            hp_prompt(),
        ],
        "effects": [
            {
                "op": "addCard",
                "cardList": "campaignPool",
                "cardCode": SYSTEM_SHOCK,
                "when": {"notAnswer": "protocolsInVictory"},
            },
            {"op": "setFlag", "flag": "infinityStones1B", "from": "infinityStones1B"},
            hp_effect(),
        ],
        "next": [{"goto": "s4_hela"}],
    },
    "onDefeat": {"next": [{"goto": "s3_thanos"}]},
})

# ---------------------------------------------------------------- scenario 4
scenarios.append({
    "id": "s4_hela",
    "name": t("Hela", "Hela"),
    "flavour": t(
        "Loki a pris le trône d'Asgard et jeté Odin en Hel. Sans le Père de Tout, personne "
        "n'arrêtera le trickster.",
        "Loki has taken Asgard's throne and cast Odin into Hel. Without the All-Father, nobody "
        "stops the trickster.",
    ),
    "victoryLabel": t("Hela est vaincue !", "Hela is beaten!"),
    "defeatLabel": t("Hela vous a vaincus !", "Hela has beaten you!"),
    "baseSetup": {
        "villainDeck": {"standard": ["21136a"], "expert": ["21137a"]},
        "mainScheme": ["21138a"],
        "encounterSets": ["hela", "legions_of_hel", "frost_giants", "standard"],
    },
    "campaignSetup": [
        {
            "text": t(
                "Mettre cette manigance annexe en jeu",
                "Put this side scheme into play",
            ),
            "cards": ["21186a"],
        },
        {
            "text": t(
                "Mélanger cette traîtrise dans le deck rencontre",
                "Shuffle this treachery into the encounter deck",
            ),
            "cards": [SUMMONED_BACK],
        },
        shawarma_step(),
        system_shock_step(),
        infinity_stones_step(),
    ] + expert_steps(),
    "onVictory": {
        "prompts": [
            vp_prompt(),
            {
                "id": "nornStonesDefeated",
                "type": "boolean",
                "label": t(
                    "Avez-vous déjoué {card:21186a} ?",
                    "Did you defeat {card:21186a}?",
                ),
                "cards": ["21186a"],
            },
            {
                "id": "odinsArmor",
                "type": "boolean",
                "label": t(
                    "Avez-vous {card:" + ODINS_ARMOR + "} dans la pile de victoire ?",
                    "Do you have {card:" + ODINS_ARMOR + "} in the victory display?",
                ),
                "cards": ["21186b"],
            },
            hp_prompt(),
        ],
        "effects": [
            {
                "op": "addCard",
                "cardList": "campaignPool",
                "cardCode": NORN_STONE,
                "when": {"answer": "nornStonesDefeated"},
            },
            {
                "op": "addCard",
                "cardList": "campaignPool",
                "cardCode": ODIN,
                "when": {"answer": "odinsArmor"},
            },
            hp_effect(),
        ],
        "next": [{"goto": "s5_loki"}],
    },
    "onDefeat": {"next": [{"goto": "s4_hela"}]},
})

# ---------------------------------------------------------------- scenario 5
scenarios.append({
    "id": "s5_loki",
    "name": t("Loki", "Loki"),
    "flavour": t(
        "Odin est libre, mais Loki garde le trône et le Gantelet. Tout se joue au Royaume d'Or.",
        "Odin is free, but Loki keeps the throne and the Gauntlet. It ends in the Golden Realm.",
    ),
    "victoryLabel": t("Loki est vaincu !", "Loki is beaten!"),
    "defeatLabel": t("Loki vous a vaincus !", "Loki has beaten you!"),
    "baseSetup": {
        "villainDeck": {"standard": LOKIS, "expert": LOKIS},
        "mainScheme": ["21165a"],
        "encounterSets": [
            "loki", "infinity_gauntlet", "enchantress", "frost_giants", "standard",
        ],
    },
    "campaignSetup": [
        {
            "text": t(
                "Toucher la carte pour voir quel Loki affronter en premier ; mettre les "
                "quatre autres de côté, hors jeu",
                "Tap the card to see which Loki you face first; set the other four aside, "
                "out of play",
            ),
            "draw": {"id": "loki", "from": LOKIS},
        },
        {
            "text": t(
                "Choisir combien de Loki doivent être vaincus pour gagner :\n"
                "  Recrue : 1\n"
                "  Standard : 2\n"
                "  Expert : 3\n"
                "  Héroïque : 4",
                "Choose how many Lokis must be defeated to win:\n"
                "  Rookie: 1\n"
                "  Standard: 2\n"
                "  Expert: 3\n"
                "  Heroic: 4",
            ),
        },
        {
            "text": t(
                "Mettre cette manigance annexe en jeu",
                "Put this side scheme into play",
            ),
            "cards": ["21189a"],
        },
        {
            "text": t(
                "Mélanger cette traîtrise dans le deck rencontre",
                "Shuffle this treachery into the encounter deck",
            ),
            "cards": [SUMMONED_BACK],
        },
        shawarma_step(),
        system_shock_step(),
        infinity_stones_step(),
        {
            "text": t(
                "Chaque joueur met un exemplaire de cette amélioration en jeu, face Setup",
                "Each player puts one copy of this upgrade into play, Setup side up",
            ),
            "cards": [NORN_STONE],
            "when": pool(NORN_STONE),
        },
        {
            "text": t(
                "Mettre Odin en jeu sur sa face King",
                "Put Odin into play on his King side",
            ),
            "cards": [ODIN],
            "when": pool(ODIN),
        },
        {
            "text": t(
                "Expert : une défaite ici perd la campagne",
                "Expert: losing here loses the campaign",
            ),
            "when": {"difficulty": "expert"},
        },
    ] + expert_steps(),
    "onVictory": {
        "prompts": [vp_prompt()],
        "effects": [],
        "next": [{"end": True}],
    },
    # Standard lets you try again; on Expert the rulebook ends the campaign here,
    # and the app cannot end it on a defeat, so it says so in setup instead.
    "onDefeat": {"next": [{"goto": "s5_loki"}]},
})

template = {
    "_note": (
        "Mechanics only, written for this app: no rules text and no text from the campaign book. "
        "Card codes are MarvelCDB codes for pack 'mts'. The campaign pool is a card list, because "
        "that is what the rulebook calls it and what it holds; the two recorded facts that are not "
        "cards are flags, read with countTrue because the engine files a flag under the scenario "
        "that set it."
    ),
    "id": "mts",
    "schemaVersion": 1,
    "name": t("L'Ombre du Titan Fou", "The Mad Titan's Shadow"),
    # Shown before the campaign starts: what this is, what it still
    # needs from you, and what the app takes off your hands.
    "notice": t(
        "Campagne non officielle, reconstituée pour l'application : le livret et les cartes de la boîte L'Ombre du Titan Fou restent nécessaires pour jouer.\n\nL'application fait pour vous les tirages que la campagne laisse au hasard et tient le registre : les Pierres d'Infinité obtenues, ce qui est passé dans votre pile de victoire, et en campagne experte les points de vie de chaque héros entre les parties.",
        "Unofficial, reconstructed for the app: the rulebook and cards from The Mad Titan's Shadow box are still needed to play.\n\nThe app makes the campaign's random draws for you and keeps the log: the Infinity Stones earned, what went into your victory display, and in an expert campaign each hero's hit points between games.",
    ),
    "packCode": "mts",
    "difficulties": ["standard", "expert"],
    "counters": [
        {
            "id": "hp",
            "scope": "hero",
            "initial": 0,
            "maxFrom": "heroCard.health",
            "activeWhen": {"difficulty": "expert"},
        },
    ],
    "flagSets": [{"id": "towerDamaged"}, {"id": "infinityStones1B"}],
    "cardLists": [{"id": "campaignPool", "scope": "campaign"}],
    "startScenarioId": "s1_ebony_maw",
    "scenarios": scenarios,
}

with io.open("app/src/main/assets/campaigns/mts.json", "w", encoding="utf-8") as f:
    json.dump(template, f, ensure_ascii=False, indent=1)
    f.write("\n")

print("scenarios:", len(scenarios))
for s in scenarios:
    print(" ", s["id"], "| setup:", len(s["campaignSetup"]),
          "| prompts:", [p["id"] for p in s["onVictory"]["prompts"]])
