# -*- coding: utf-8 -*-
"""Builds the Age of Apocalypse campaign template.

Written as a script rather than by hand so the repetitive parts — the mission
and overseer setup that every scenario shares — are written once and cannot
drift between scenarios.
"""
import json, io

MISSIONS = ["45166a", "45167a", "45168a", "45169a"]      # 1/5 - 4/5
PROTECT_THE_PROFESSOR = "45170a"                          # 5/5, scenario 5 only
OVERSEERS = ["45179a", "45180a", "45181a", "45182a", "45183a"]
MISSION_TEAM = "45171a"


NL = chr(10)


def t(fr, en):
    return {"fr": fr, "en": en}


def standard_iii_step():
    return {
        "text": t(
            "Choisissez Standard I ou Standard III (identique pour tous les scénarios)",
            "Choose Standard I or Standard III (same for every scenario)",
        ),
    }


def mission_steps(scenario_number):
    """The side-mission setup, identical for scenarios 1 to 4."""
    return [
        {
            "text": t(
                "Mélangez le set modulaire Ère d'Apocalypse dans le deck Rencontre",
                "Shuffle the Age of Apocalypse modular set into the encounter deck",
            ),
        },
        {
            # The draw itself carries no text: the four steps below name the
            # mission that came up and say what it wants, in one line each.
            "text": t("", ""),
            "draw": {"id": "mission", "from": MISSIONS, "excluding": "missionsUsed"},
        },
    ] + mission_setup_steps() + mission_legacy_steps() + [
        {
            "text": t(
                "Ajoutez ce sbire HIÉRARQUE à la zone de mission et placez près de lui la carte Règles de Mission",
                "Add this OVERSEER minion to the mission area and put Mission Rules beside it",
            ),
            "draw": {"id": "overseer", "from": OVERSEERS, "excluding": "overseersDefeated"},
        },
        {
            "text": t(
                "Le premier joueur prend le contrôle de ce soutien, face MISSION visible",
                "First player takes control of this support card, MISSION side faceup",
            ),
            "cards": [MISSION_TEAM],
        },
        {
            "text": t(
                "Chaque joueur cherche un allié dans son deck et l'ajoute à sa main",
                "Each player searches their deck for an ally and takes it into hand",
            ),
        },
    ]


def expert_steps(first_scenario=False):
    """Hit points carried between scenarios, on an Expert campaign only."""
    steps = []
    if not first_scenario:
        steps.append({
            "text": t(
                "Campagne Experte Uniquement : fixez les points de vie de chaque joueur à la valeur indiquée dans le registre de campagne",
                "Expert: set each hero's hit points to the value below",
            ),
            "when": {"difficulty": "expert"},
            "showCounter": "hp",
        })
        steps.append({
            "text": t(
                "Campagne Experte Uniquement : chaque joueur peut placer 3 menaces "
                "sur la MISSION." + NL +
                "Cela soigne son identité jusqu'à sa valeur de points de vie maximale.",
                "Expert: place 3 threat on the MISSION to heal a hero to full",
            ),
            "when": {"difficulty": "expert"},
            "action": {
                "id": "heal",
                "label": t("Soigner", "Heal"),
                "perHero": True,
                "effects": [{"op": "setHeroCounter", "counter": "hp", "value": 999}],
            },
        })
    return steps


def mission_outcome_prompts(scenario_number):
    """What the campaign log needs recorded after scenarios 1 to 4.

    The app already knows which mission and overseer were in play — it drew
    them — so it only asks what it cannot know. Whether the mission fell is
    recorded for the campaign summary; its reward and its penalty are both
    printed on the back of the card and resolve inside the scenario, so nothing
    carries forward from it.
    """
    return [
        {
            "id": "vp",
            "type": "number",
            "label": t(
                "Combien de points de victoire avez-vous accumulés ?",
                "How many victory points did you accumulate?",
            ),
        },
        {
            "id": "missionDefeated",
            "type": "boolean",
            "label": t(
                "La manigance annexe MISSION {mission} a-t-elle été déjouée ?",
                "Was the MISSION {mission} defeated?",
            ),
        },
        {
            "id": "overseerDefeated",
            "type": "boolean",
            "label": t(
                "Le sbire HIÉRARQUE {overseer} a-t-il été vaincu ?",
                "Was the OVERSEER {overseer} defeated?",
            ),
        },
        {
            "id": "hpPerHero",
            "type": "perHeroNumber",
            "label": t("Points de vie restants", "Remaining hit points"),
            "when": {"difficulty": "expert"},
        },
    ]


def mission_outcome_effects():
    return [
        # Struck whether or not it was defeated: a mission that was attempted is
        # spent. Whether it fell only decides which of its two backs resolves,
        # and that happens inside the scenario.
        {"op": "addDrawnCard", "cardList": "missionsUsed", "from": "mission"},
    ] + mission_outcome_flags() + [
        # An overseer is struck only when it fell, so one that survived stays in
        # the pool for the next scenario.
        {
            "op": "addDrawnCard",
            "cardList": "overseersDefeated",
            "from": "overseer",
            "when": {"answer": "overseerDefeated"},
        },
        {
            "op": "setHeroCounter",
            "counter": "hp",
            "from": "hpPerHero",
            "when": {"difficulty": "expert"},
        },
    ]



# --- what each MISSION brings with it ----------------------------------------
# Setup runs only in the scenario that drew it; the outcome lasts the rest of
# the campaign, so it is a flag read with countTrue like every other one.
MISSION_RULES = [
    # (code, flag stem, setup fr/en, defeated fr/en, failed fr/en)
    ("45166a", "seattle",
     ("Mettez de côté chaque exemplaire de l'amélioration {card:45176}",
      "Set each copy of the Desperate Measures upgrade aside"),
     ("Chaque joueur peut mélanger 1 {card:45176} dans son deck, hors taille minimale",
      "Each player may shuffle 1 Desperate Measures into their deck, outside the minimum"),
     ("Retirez de la campagne chaque exemplaire de {card:45176}",
      "Remove each copy of Desperate Measures from the campaign")),

    ("45167a", "evacuate",
     ("Chaque joueur mélange un exemplaire de {card:45178} dans son deck",
      "Each player shuffles a copy of Panicked Refugees into their deck"),
     ("{card:45178} retirée. Chaque joueur ajoute 1 amélioration de n'importe quelle affinité, hors taille minimale",
      "Panicked Refugees removed. Each player adds 1 upgrade of any aspect, outside the minimum"),
     ("Chaque joueur mélange un exemplaire de {card:45178} dans son deck",
      "Each player shuffles a copy of Panicked Refugees into their deck")),

    ("45168a", "seawall",
     ("Mélangez {card:45177} dans le deck Rencontre",
      "Shuffle the North American Sea Wall side scheme into the encounter deck"),
     ("{card:45177} retirée. Chaque joueur ajoute 1 soutien de n'importe quelle affinité, hors taille minimale",
      "Sea Wall removed. Each player adds 1 support of any aspect, outside the minimum"),
     ("Mélangez {card:45177} dans le deck Rencontre lors de la mise en place",
      "Shuffle North American Sea Wall into the encounter deck during setup")),

    ("45169a", "lostmutants",
     ("Mettez de côté chaque allié de campagne",
      "Set each campaign ally aside"),
     ("Chaque joueur ajoute un allié de campagne à son deck, hors taille minimale",
      "Each player adds a campaign ally to their deck, outside the minimum"),
     ("Retirez de la campagne chaque allié de campagne",
      "Remove each campaign ally from the campaign")),
]


def mission_setup_steps():
    """The chosen mission's own setup, shown only when it is the one drawn."""
    return [
        {
            "text": t(
                "Jouez avec la MISSION {mission} — " + setup[0],
                "Play with the MISSION {mission} — " + setup[1],
            ),
            "cards": [code],
            "when": {"drawIs": "mission:" + code},
        }
        for code, _stem, setup, _d, _f in MISSION_RULES
    ]


def mission_legacy_steps():
    """What an earlier mission left behind, for every scenario after it."""
    steps = []
    for code, stem, _s, defeated, failed in MISSION_RULES:
        steps.append({
            "text": t(defeated[0], defeated[1]),
            "cards": [code],
            "when": {"countTrue": stem + "Won", "countAtLeast": 1},
        })
        steps.append({
            "text": t(failed[0], failed[1]),
            "cards": [code],
            "when": {"countTrue": stem + "Lost", "countAtLeast": 1},
        })
    return steps


def mission_outcome_flags():
    """Records how the mission drawn for this scenario ended."""
    effects = []
    for code, stem, _s, _d, _f in MISSION_RULES:
        effects.append({
            "op": "setFlag", "flag": stem + "Won", "boolValue": True,
            "when": {"all": [{"drawIs": "mission:" + code}, {"answer": "missionDefeated"}]},
        })
        effects.append({
            "op": "setFlag", "flag": stem + "Lost", "boolValue": True,
            "when": {"all": [{"drawIs": "mission:" + code}, {"notAnswer": "missionDefeated"}]},
        })
    return effects


scenarios = []

# ---------------------------------------------------------------- scenario 1
scenarios.append({
    "id": "s1_unus",
    "name": t("Unus", "Unus"),
    "flavour": t(
        "La X-Force est prise au piège d'une ligne chronologique où règne Apocalypse, et le "
        "prélat Unus les a déjà trouvés.",
        "X-Force is stranded in a timeline ruled by Apocalypse, and the prelate Unus has "
        "already found them.",
    ),
    "victoryLabel": t("Unus est vaincu !", "Unus is beaten!"),
    "defeatLabel": t("Unus vous a vaincus !", "Unus has beaten you!"),
    "baseSetup": {
        "villainDeck": {"standard": ["45059", "45060"], "expert": ["45060", "45061"]},
        "mainScheme": ["45062a"],
        "encounterSets": ["unus", "infinites", "dystopian_nightmare", "standard"],
    },
    "campaignSetup": [
        {"include": "standardIII"},
        {
            "text": t(
                "Mettez {card:45071} en jeu et placez-y la menace de la difficulté choisie :\n"
                "  Escarmouche : 0\n"
                "  Standard : 1 par joueur\n"
                "  Expert : 2 par joueur\n"
                "  Héroïque : 3 par joueur",
                "Put {card:45071} into play and place the threat for your difficulty on it:\n"
                "  Skirmish: 0\n"
                "  Standard: 1 per player\n"
                "  Expert: 2 per player\n"
                "  Heroic: 3 per player",
            ),
            "cards": ["45071"],
        },
    ] + [{"include": "missions"}],
    "onVictory": {
        "prompts": mission_outcome_prompts(1),
        "effects": mission_outcome_effects(),
        "next": [{"goto": "s2_four_horsemen"}],
    },
    "onDefeat": {"next": [{"goto": "s1_unus"}]},
})

# ---------------------------------------------------------------- scenario 2
scenarios.append({
    "id": "s2_four_horsemen",
    "name": t("Les Quatre Cavaliers", "Four Horsemen"),
    "flavour": t(
        "Magnéto accueille la X-Force au quartier général secret des X-Men. Apocalypse les "
        "trouve aussitôt et lance ses Quatre Cavaliers.",
        "Magneto takes X-Force into the X-Men's headquarters. Apocalypse finds it at once and "
        "sends his four horsemen.",
    ),
    "victoryLabel": t("Les cavaliers sont vaincus !", "The horsemen are beaten!"),
    "defeatLabel": t("Les cavaliers vous ont vaincus !", "The horsemen have beaten you!"),
    "baseSetup": {
        # One card each: side A for Skirmish and Standard, side B for Expert and
        # Heroic, so the codes do not change with difficulty.
        "villainDeck": {
            "standard": ["45081a", "45082a", "45083a", "45084a"],
            "expert": ["45081a", "45082a", "45083a", "45084a"],
        },
        "mainScheme": ["45085a"],
        "encounterSets": ["four_horsemen", "hounds", "dystopian_nightmare", "standard"],
    },
    "campaignSetup": [
        {"include": "standardIII"},
        {
            "text": t(
                "Aligner ces quatre méchants dans cet ordre, chacun avec son compteur de points de vie",
                "Set these four villains in a row in this order, each with its own hit point dial",
            ),
            "draw": {
                "id": "horsemen",
                "from": ["45081a", "45082a", "45083a", "45084a"],
                "count": 4,
            },
        },
        {
            "text": t(
                "Face A en Escarmouche et Standard, face B en Expert et Héroïque",
                "Use side A for Skirmish and Standard, side B for Expert and Heroic",
            ),
        },
        {
            "text": t(
                "Donnez le marqueur d'activation au méchant le plus à gauche",
                "Give the active counter to the leftmost villain",
            ),
        },
    ] + [{"include": "missions"}, {"include": "expertHp"}],
    "onVictory": {
        "prompts": mission_outcome_prompts(2),
        "effects": mission_outcome_effects(),
        "next": [{"goto": "s3_apocalypse"}],
    },
    "onDefeat": {"next": [{"goto": "s2_four_horsemen"}]},
})

# ---------------------------------------------------------------- scenario 3
scenarios.append({
    "id": "s3_apocalypse",
    "name": t("Apocalypse", "Apocalypse"),
    "flavour": t(
        "Les X-Men marchent sur la citadelle d'Apocalypse, à travers le cœur de son empire et "
        "ses plus puissants prélats.",
        "The X-Men march on Apocalypse's citadel, through the heart of his empire and his "
        "strongest prelates.",
    ),
    "victoryLabel": t("Apocalypse est vaincu !", "Apocalypse is beaten!"),
    "defeatLabel": t("Apocalypse vous a vaincus !", "Apocalypse has beaten you!"),
    "baseSetup": {
        "villainDeck": {"standard": ["45101a", "45102a"], "expert": ["45102a"]},
        "mainScheme": ["45103a"],
        "encounterSets": [
            "apocalypse", "overseer", "dark_riders", "infinites", "standard",
        ],
    },
    "campaignSetup": [
        {"include": "standardIII"},
        {
            "text": t(
                "Commencez Apocalypse sur la face (II) — face (I) pour une partie plus facile",
                "Start Apocalypse on side (II) — side (I) for an easier game",
            ),
            "cards": ["45101a"],
        },
    ] + [{"include": "missions"}, {"include": "expertHp"}],
    "onVictory": {
        "prompts": mission_outcome_prompts(3),
        "effects": mission_outcome_effects(),
        "next": [{"goto": "s4_dark_beast"}],
    },
    "onDefeat": {"next": [{"goto": "s3_apocalypse"}]},
})

# ---------------------------------------------------------------- scenario 4
scenarios.append({
    "id": "s4_dark_beast",
    "name": t("Le Fauve Noir", "Dark Beast"),
    "flavour": t(
        "Sous la citadelle se trouve une machine à voyager dans le temps, et le Fauve qui la "
        "manœuvre n'est pas le leur : c'est Le Fauve Noir.",
        "Beneath the citadel is a time machine, and the Beast operating it is not theirs.",
    ),
    "victoryLabel": t("Le Fauve Noir est vaincu !", "Dark Beast is beaten!"),
    "defeatLabel": t("Le Fauve Noir vous a vaincus !", "Dark Beast has beaten you!"),
    "baseSetup": {
        "villainDeck": {"standard": ["45118", "45119"], "expert": ["45119", "45120"]},
        "mainScheme": ["45121a"],
        "encounterSets": [
            "dark_beast", "blue_moon", "genosha", "savage_land",
            "dystopian_nightmare", "standard",
        ],
    },
    "campaignSetup": [{"include": "standardIII"}, {"include": "missions"}, {"include": "expertHp"}],
    "onVictory": {
        "prompts": mission_outcome_prompts(4),
        "effects": mission_outcome_effects(),
        "next": [{"goto": "s5_en_sabah_nur"}],
    },
    "onDefeat": {"next": [{"goto": "s4_dark_beast"}]},
})

# ---------------------------------------------------------------- scenario 5
scenarios.append({
    "id": "s5_en_sabah_nur",
    "name": t("En Sabah Nur", "En Sabah Nur"),
    "flavour": t(
        "De retour dans leur passé, l'équipe doit arracher à Apocalypse l'antidote qui sauvera "
        "le Professeur X.",
        "Back in their own past, the team must take from Apocalypse the antidote that will save "
        "Professor X.",
    ),
    "victoryLabel": t("Apocalypse est vaincu !", "Apocalypse is beaten!"),
    "defeatLabel": t("Apocalypse vous a vaincus !", "Apocalypse has beaten you!"),
    "baseSetup": {
        # The three-sided villain: BIOMORPH is the starting form.
        "villainDeck": {"standard": ["45184a", "45185a"], "expert": ["45185a", "45186a"]},
        "mainScheme": ["45147a", "45148a"],
        "encounterSets": ["en_sabah_nur", "celestial_tech", "clan_akkaba", "standard"],
    },
    "campaignSetup": [
        {"include": "standardIII"},
        {
            "text": t(
                "Mettez {card:45163} en jeu",
                "Put Ancient Ritual into play",
            ),
            "cards": ["45163"],
        },
        {
            "text": t(
                "Commencez Apocalypse sur sa face BIOMORPH",
                "Start Apocalypse on his BIOMORPH side",
            ),
            "cards": ["45184a"],
        },
        {
            "text": t(
                "Le Professeur X ne peut pas entrer en jeu durant cette partie",
                "Professor X cannot enter play this game",
            ),
        },
        {
            "text": t(
                "Mélangez le set modulaire Ère d'Apocalypse dans le deck Rencontre",
                "Shuffle the Age of Apocalypse modular set into the encounter deck",
            ),
        },
        {
            "text": t(
                "Jouez avec cette MISSION et suivez sa mise en place dans le journal de campagne",
                "Play with this MISSION side scheme and follow its setup in the campaign log",
            ),
            "cards": [PROTECT_THE_PROFESSOR],
        },
        {
            "text": t(
                "Ajoutez ce sbire HIÉRARQUE à la zone de mission et placez près de lui la carte Règles de Mission",
                "Add this OVERSEER minion to the mission area and put Mission Rules beside it",
            ),
            "draw": {"id": "overseer", "from": OVERSEERS, "excluding": "overseersDefeated"},
        },
        {
            "text": t(
                "Le premier joueur prend le contrôle de ce soutien, face MISSION visible",
                "First player takes control of this support card, MISSION side faceup",
            ),
            "cards": [MISSION_TEAM],
        },
        {
            "text": t(
                "Chaque joueur cherche un allié dans son deck et l'ajoute à sa main",
                "Each player searches their deck for an ally and takes it into hand",
            ),
        },
    ] + mission_legacy_steps() + [{"include": "expertHp"}],
    "onVictory": {
        "prompts": [
            {
                "id": "vp",
                "type": "number",
                "label": t(
                    "Combien de points de victoire avez-vous accumulés ?",
                    "How many victory points did you accumulate?",
                ),
            },
            {
                "id": "professorSaved",
                "type": "boolean",
                "label": t(
                    "La MISSION {card:45170a} a-t-elle été déjouée ?",
                    "Was the MISSION {card:45170a} defeated?",
                ),
            },
        ],
        "effects": [
            {"op": "setFlag", "flag": "professorSaved", "from": "professorSaved"},
        ],
        "next": [{"end": True}],
    },
    "onDefeat": {"next": [{"goto": "s5_en_sabah_nur"}]},
})

template = {
    "_note": (
        "Mechanics only, written for this app: no rules text and no text from the campaign book. "
        "Card codes are MarvelCDB codes for pack 'aoa'; names are resolved from the card database "
        "at runtime so they appear in the player's language."
    ),
    "id": "aoa",
    "schemaVersion": 1,
    "name": t("L'Ère d'Apocalypse", "Age of Apocalypse"),
    # Shown before the campaign starts: what this is, what it still
    # needs from you, and what the app takes off your hands.
    "notice": t(
        "Campagne non officielle, reconstituée pour l'application : le livret et les cartes de la boîte L'Ère d'Apocalypse restent nécessaires pour jouer.\n\nL'application tire pour vous l'ordre des Quatre Cavaliers et le sbire HIÉRARQUE de chaque partie, retient les manigances annexes MISSION déjà jouées et les HIÉRARQUES vaincus, et tient le registre : points de victoire, sort du Professeur, et en campagne experte les points de vie de chaque identité entre les parties.",
        "Unofficial, reconstructed for the app: the rulebook and cards from the Age of Apocalypse box are still needed to play.\n\nThe app draws the order of the four Horsemen and each game's OVERSEER for you, remembers which MISSIONS have been played and which OVERSEERS were defeated, and keeps the log: victory points, what became of the Professor, and in an expert campaign each hero's hit points between games.",
    ),
    "packCode": "aoa",
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
    "flagSets": [{"id": "professorSaved", "scope": "campaign"}] + [
        {"id": stem + suffix}
        for _c, stem, _s, _d, _f in MISSION_RULES
        for suffix in ("Won", "Lost")
    ],
    "cardLists": [
        {"id": "missionsUsed", "scope": "campaign"},
        {"id": "overseersDefeated", "scope": "campaign"},
    ],
    # Written once and included where they belong: the side-mission setup is
    # word for word the same in five scenarios, and five copies of it would be
    # five things to keep in step.
    "setupFragments": {
        "standardIII": [standard_iii_step()],
        "missions": mission_steps(0),
        "expertHp": expert_steps(),
    },
    "startScenarioId": "s1_unus",
    "scenarios": scenarios,
}

with io.open("app/src/main/assets/campaigns/aoa.json", "w", encoding="utf-8") as f:
    json.dump(template, f, ensure_ascii=False, indent=1)
    f.write("\n")

print("scenarios:", len(scenarios))
for s in scenarios:
    print(" ", s["id"], "| setup steps:", len(s["campaignSetup"]),
          "| prompts:", len(s["onVictory"].get("prompts", [])))
