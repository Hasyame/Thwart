# -*- coding: utf-8 -*-
"""Builds The Rise of Red Skull campaign template.

This campaign needs no engine work: everything it does already exists. What it
carries between scenarios is four separate things, so it uses four card lists
rather than one pool — the EXPERIMENTAL attachments the encounter deck keeps
gaining, the upgrades the players earn, the allies they rescue, and the allies
they leave behind a prison door. Merging them into one list would lose which is
which, and scenario 5 needs to tell them apart.

Delay counters are a campaign counter because they are a number that becomes a
different number later: threat on the final main scheme, per player on Expert.

Card codes are MarvelCDB codes for pack 'trors', each one checked against the
API rather than transcribed.
"""
import json, io

# --- villains, one stage per difficulty step --------------------------------
CROSSBONES = ["04058", "04059", "04060"]        # I, II, III
ABSORBING_MAN = ["04076", "04077", "04078"]
TASKMASTER = ["04093", "04094", "04095"]
ZOLA = ["04109", "04110", "04111"]
RED_SKULL = ["04125", "04126", "04127"]

# --- the campaign log's four kinds of memory --------------------------------
EXPERIMENTAL = ["04072", "04073", "04074", "04075"]   # Experimental Weapons
TECH = ["04155", "04156", "04157", "04158"]           # Hydra Campaign upgrades
CONDITIONS = ["04159a", "04160a", "04161a", "04162a"]  # "Basic" conditions
ALLIES = ["04097", "04098", "04099", "04100"]         # Taskmaster's captives
HYDRA_PRISON = "04122"


def t(fr, en):
    return {"fr": fr, "en": en}


def flag(name):
    """A recorded fact. Counted, because the engine files it under its scenario."""
    return {"countTrue": name, "countAtLeast": 1}


def not_flag(name):
    """The same fact, absent. Not the same as never having been asked."""
    return {"countTrue": name, "countAtMost": 0}


def has_cards(list_id):
    return {"cardList": list_id, "minSize": 1}


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


# --- steps every scenario after the first repeats ---------------------------
def setup_keyword_step():
    return {
        "text": t(
            "Chaque joueur cherche dans son deck les cartes Mise en place et les met en jeu",
            "Each player searches their deck for Setup cards and puts them into play",
        ),
    }


def experimental_step():
    """The encounter deck keeps whatever Crossbones brought into play.

    Shown as the list itself rather than as a sentence: which attachments were
    recorded is the whole point, and by scenario 5 nobody remembers.
    """
    return {
        "text": t(
            "Mélangez dans le deck Rencontre chaque attachement EXPERIMENTAL noté",
            "Shuffle each recorded EXPERIMENTAL attachment into the encounter deck",
        ),
        "showCardList": "experimental",
        "when": has_cards("experimental"),
    }


def expert_steps():
    """Hit points carried between scenarios, on an Expert campaign only."""
    return [
        {
            "text": t(
                "Campagne Experte Uniquement : fixez les points de vie de chaque joueur à "
                "la valeur indiquée dans le registre de campagne",
                "Expert: set each hero's hit points to the value below",
            ),
            "when": {"difficulty": "expert"},
            "showCounter": "hp",
        },
        {
            "text": t(
                "Campagne Experte Uniquement : chaque joueur peut ajouter à son deck 1 "
                "obligation aléatoire de son Set Campagne Experte",
                "Expert: each player may add 1 random obligation from their Expert Campaign "
                "set to their deck to heal to full",
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


def carried_steps():
    return [setup_keyword_step(), experimental_step()] + expert_steps()


scenarios = []

# ---------------------------------------------------------------- scenario 1
scenarios.append({
    "id": "s1_crossbones",
    "name": t("Crossbones", "Crossbones"),
    "flavour": t(
        "Crossbones jette une armée d'Hydra sur le Projet P.E.G.A.S.U.S. Les armes "
        "expérimentales du site ne doivent pas tomber entre ses mains.",
        "Crossbones throws a Hydra army at Project P.E.G.A.S.U.S. The experimental weapons "
        "held there must not fall into his hands.",
    ),
    "victoryLabel": t("Crossbones est vaincu !", "Crossbones is beaten!"),
    "defeatLabel": t("Crossbones vous a vaincus !", "Crossbones has beaten you!"),
    "baseSetup": {
        "villainDeck": {
            "standard": [CROSSBONES[0], CROSSBONES[1]],
            "expert": [CROSSBONES[1], CROSSBONES[2]],
        },
        "mainScheme": ["04061a", "04062a", "04063a"],
        "encounterSets": [
            "crossbones", "exper_weapon", "hydra_assault", "weap_master",
            "hydra_patrol", "standard",
        ],
    },
    "campaignSetup": [
        {
            "text": t(
                "Les identités choisies pour cette campagne sont fixées : elles ne peuvent "
                "pas changer avant la fin",
                "The identities chosen for this campaign are fixed: they cannot change "
                "before the end",
            ),
        },
    ],
    "onVictory": {
        "prompts": [
            vp_prompt(),
            {
                "id": "tech",
                # Per hero, not per table: "each player chooses one" lets two
                # players take the same upgrade, which one shared list could not
                # record — and it would not say who holds what.
                "type": "perHeroCardSelect",
                "label": t(
                    "Quelle amélioration TECH chaque joueur ajoute-t-il à son deck ?",
                    "Which TECH upgrade does each player add to their deck?",
                ),
                "cards": TECH,
                # Compulsory: the campaign says each player chooses one, not
                # that they may. Every scenario after this assumes the upgrade
                # is in the deck, so the page will not file until every player
                # has picked.
                "min": 1,
            },
            {
                "id": "experimental",
                "type": "cardSelect",
                "label": t(
                    "Quels attachements EXPERIMENTAL sont entrés en jeu ?",
                    "Which EXPERIMENTAL attachments entered play?",
                ),
                "cards": EXPERIMENTAL,
            },
            hp_prompt(),
        ],
        "effects": [
            {"op": "addCardsFromAnswer", "from": "tech", "cardList": "tech"},
            {
                "op": "addCardsFromAnswer",
                "from": "experimental",
                "cardList": "experimental",
            },
            hp_effect(),
        ],
        "next": [{"goto": "s2_absorbing_man"}],
    },
    "onDefeat": {"next": [{"goto": "s1_crossbones"}]},
})

# ---------------------------------------------------------------- scenario 2
scenarios.append({
    "id": "s2_absorbing_man",
    "name": t("L'Homme Absorbant", "Absorbing Man"),
    "flavour": t(
        "Madame Hydra file avec la Pierre d'Infinité. L'Homme Absorbant est payé "
        "pour vous retarder, et il n'a besoin que de temps.",
        "Madame Hydra is away with the Infinity Stone. Absorbing Man is paid to delay you, "
        "and delay is all he needs.",
    ),
    "victoryLabel": t("L'Homme Absorbant est vaincu !", "Absorbing Man is beaten!"),
    "defeatLabel": t("L'Homme Absorbant vous a vaincus !", "Absorbing Man has beaten you!"),
    "baseSetup": {
        "villainDeck": {
            "standard": [ABSORBING_MAN[0], ABSORBING_MAN[1]],
            "expert": [ABSORBING_MAN[1], ABSORBING_MAN[2]],
        },
        "mainScheme": ["04079a"],
        "encounterSets": ["absorbing_man", "hydra_patrol", "standard"],
    },
    "campaignSetup": carried_steps(),
    "onVictory": {
        "prompts": [
            vp_prompt(),
            {
                "id": "delayCounters",
                "type": "number",
                "label": t(
                    "Combien de pions retard sur la manigance principale ?",
                    "How many delay counters on the main scheme?",
                ),
                "cards": ["04079a"],
            },
            {
                "id": "conditions",
                "type": "cardSelect",
                "label": t(
                    "Quelle amélioration Basic chaque joueur attache-t-il à son identité ?",
                    "Which Basic upgrade does each player attach to their identity?",
                ),
                "cards": CONDITIONS,
                # No min here: this one the campaign words as "may choose", so
                # declining is a legal answer and the finale handles the gap.
            },
            hp_prompt(),
        ],
        "effects": [
            # Straight to a counter: scenario 5 turns this number into threat,
            # and doubles it per player on Expert.
            {"op": "setCounter", "counter": "delay", "from": "delayCounters"},
            {
                "op": "addCardsFromAnswer",
                "from": "conditions",
                "cardList": "conditions",
            },
            hp_effect(),
        ],
        "next": [{"goto": "s3_taskmaster"}],
    },
    "onDefeat": {"next": [{"goto": "s2_absorbing_man"}]},
})

# ---------------------------------------------------------------- scenario 3
scenarios.append({
    "id": "s3_taskmaster",
    "name": t("Taskmaster", "Taskmaster"),
    "flavour": t(
        "New York est passée sous bannière Hydra. Taskmaster en est le chef de la police, "
        "et il fait du porte-à-porte.",
        "New York now flies the Hydra banner. Taskmaster is its chief of police, and he is "
        "going door to door.",
    ),
    "victoryLabel": t("Taskmaster est vaincu !", "Taskmaster is beaten!"),
    "defeatLabel": t("Taskmaster vous a vaincus !", "Taskmaster has beaten you!"),
    "baseSetup": {
        "villainDeck": {
            "standard": [TASKMASTER[0], TASKMASTER[1]],
            "expert": [TASKMASTER[1], TASKMASTER[2]],
        },
        "mainScheme": ["04096a"],
        "encounterSets": ["taskmaster", "hydra_patrol", "weap_master", "standard"],
    },
    "campaignSetup": carried_steps(),
    "onVictory": {
        "prompts": [
            vp_prompt(),
            {
                "id": "rescued",
                "type": "cardSelect",
                "label": t(
                    "Quels alliés avez-vous libérés ?",
                    "Which allies did you rescue?",
                ),
                "cards": ALLIES,
            },
            hp_prompt(),
        ],
        "effects": [
            {"op": "addCardsFromAnswer", "from": "rescued", "cardList": "rescued"},
            hp_effect(),
        ],
        "next": [{"goto": "s4_zola"}],
    },
    "onDefeat": {"next": [{"goto": "s3_taskmaster"}]},
})

# ---------------------------------------------------------------- scenario 4
scenarios.append({
    "id": "s4_zola",
    "name": t("Zola", "Zola"),
    "flavour": t(
        "Ellis Island est devenue le laboratoire d'Arnim Zola, et sa prison. Les captifs de "
        "Taskmaster y ont été livrés.",
        "Ellis Island is Arnim Zola's laboratory now, and his prison. Taskmaster's captives "
        "were delivered there.",
    ),
    "victoryLabel": t("Zola est vaincu !", "Zola is beaten!"),
    "defeatLabel": t("Zola vous a vaincus !", "Zola has beaten you!"),
    "baseSetup": {
        "villainDeck": {
            "standard": [ZOLA[0], ZOLA[1]],
            "expert": [ZOLA[1], ZOLA[2]],
        },
        "mainScheme": ["04112a", "04113a"],
        "encounterSets": ["zola", "hydra_assault", "standard"],
    },
    "campaignSetup": carried_steps(),
    "onVictory": {
        "prompts": [
            vp_prompt(),
            {
                "id": "engaged",
                "type": "perHeroBoolean",
                "label": t(
                    "Ce héros était-il engagé avec un ennemi ?",
                    "Was this hero engaged with an enemy?",
                ),
            },
            {
                "id": "prisonInPlay",
                "type": "boolean",
                "label": t(
                    "{card:04122} était-elle encore en jeu ?",
                    "Was {card:04122} still in play?",
                ),
                "cards": [HYDRA_PRISON],
            },
            {
                "id": "imprisoned",
                "type": "cardSelect",
                "label": t(
                    "Quels alliés sont restés sous {card:04122} ?",
                    "Which allies were left underneath {card:04122}?",
                ),
                "cards": ALLIES,
                "when": {"answer": "prisonInPlay"},
            },
            hp_prompt(),
        ],
        "effects": [
            {"op": "setFlag", "flag": "hydraPrison", "from": "prisonInPlay"},
            {
                "op": "addCardsFromAnswer",
                "from": "imprisoned",
                "cardList": "imprisoned",
            },
            hp_effect(),
        ],
        "next": [{"goto": "s5_red_skull"}],
    },
    "onDefeat": {"next": [{"goto": "s4_zola"}]},
})

# ---------------------------------------------------------------- scenario 5
scenarios.append({
    "id": "s5_red_skull",
    "name": t("Le Crâne Rouge", "Red Skull"),
    "flavour": t(
        "La Maison-Blanche est la forteresse du Crâne Rouge, et ses scientifiques achèvent "
        "la machine de Zola. Après, le monde entier.",
        "The White House is Red Skull's fortress, and his scientists are finishing Zola's "
        "machine. After that, the whole world.",
    ),
    "victoryLabel": t("Hydra est vaincue !", "Hydra is beaten!"),
    "defeatLabel": t("Le Crâne Rouge vous a vaincus !", "Red Skull has beaten you!"),
    "baseSetup": {
        "villainDeck": {
            "standard": [RED_SKULL[0], RED_SKULL[1]],
            "expert": [RED_SKULL[1], RED_SKULL[2]],
        },
        "mainScheme": ["04128a", "04129a"],
        "encounterSets": ["red_skull", "hydra_assault", "hydra_patrol", "standard"],
    },
    "campaignSetup": [
        setup_keyword_step(),
        experimental_step(),
        # The delay Absorbing Man bought, paid back as threat. Split by
        # difficulty because Expert multiplies it by the number of players and
        # the app cannot show one number that means both.
        {
            "text": t(
                "Placez sur la manigance principale autant de menace que de pions retard notés",
                "Place as much threat on the main scheme as the delay counters recorded",
            ),
            "showCounter": "delay",
            "when": {"difficulty": "standard"},
        },
        {
            "text": t(
                "Campagne Experte Uniquement : placez cette menace par joueur sur la "
                "manigance principale",
                "Expert: place this much threat per player on the main scheme",
            ),
            "showCounter": "delay",
            "when": {"difficulty": "expert"},
        },
        {
            "text": t(
                "Chaque joueur en forme héros retourne son amélioration Basic sur sa face "
                "Improved : la prison est tombée",
                "Each player in hero form flips their Basic upgrade to its Improved side: "
                "the prison fell",
            ),
            "showCardList": "conditions",
            "when": not_flag("hydraPrison"),
        },
        {
            "text": t(
                "Ces alliés sont restés prisonniers : ils ne peuvent figurer dans aucun deck",
                "These allies were left imprisoned: they cannot be in any deck",
            ),
            "showCardList": "imprisoned",
            "when": has_cards("imprisoned"),
        },
        {
            "text": t(
                "Campagne Experte Uniquement : ces héros étaient engagés avec un ennemi "
                "et se distribuent chacun une carte Rencontre",
                "Expert: these heroes were engaged with an enemy and each deal themselves "
                "an encounter card",
            ),
            "showHeroesWith": "engaged",
            "when": {"difficulty": "expert"},
        },
        {
            "text": t(
                "Campagne Experte Uniquement : si les joueurs perdent cette partie, ils "
                "perdent la campagne",
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
    # Standard lets you try again. On Expert the rulebook ends the campaign
    # here, which the app cannot do on a defeat, so setup says so instead.
    "onDefeat": {"next": [{"goto": "s5_red_skull"}]},
})

template = {
    "_note": (
        "Mechanics only, written for this app: no rules text and no text from the campaign "
        "book. Card codes are MarvelCDB codes for pack 'trors'. Four card lists rather than "
        "one pool, because the campaign log remembers four different things and scenario 5 "
        "has to tell them apart. Flags are read with countTrue because the engine files a "
        "flag under the scenario that set it."
    ),
    "id": "trors",
    "schemaVersion": 1,
    "name": t("L'Ascension du Crâne Rouge", "The Rise of Red Skull"),
    # Shown before the campaign starts: what this is, what it still
    # needs from you, and what the app takes off your hands.
    "notice": t(
        "Campagne non officielle, reconstituée pour l'application : le livret et les cartes de la boîte L'Ascension du Crâne Rouge restent nécessaires pour jouer.\n\nL'application tient le registre à votre place : l'amélioration TECH de chaque joueur, les alliés libérés ou restés prisonniers, les améliorations Basic attachées, les pions retard, et en campagne experte les points de vie de chaque héros entre les parties.",
        "Unofficial, reconstructed for the app: the rulebook and cards from The Rise of Red Skull box are still needed to play.\n\nThe app keeps the log for you: each player's TECH upgrade, the allies freed or left imprisoned, the Basic upgrades attached, the delay counters, and in an expert campaign each hero's hit points between games.",
    ),
    "packCode": "trors",
    "difficulties": ["standard", "expert"],
    "counters": [
        {
            "id": "hp",
            "scope": "hero",
            "initial": 0,
            "maxFrom": "heroCard.health",
            "activeWhen": {"difficulty": "expert"},
        },
        {"id": "delay", "scope": "campaign", "initial": 0},
    ],
    "flagSets": [{"id": "hydraPrison"}],
    "cardLists": [
        {"id": "experimental", "scope": "campaign"},
        {"id": "tech", "scope": "campaign"},
        {"id": "conditions", "scope": "campaign"},
        {"id": "rescued", "scope": "campaign"},
        {"id": "imprisoned", "scope": "campaign"},
    ],
    "startScenarioId": "s1_crossbones",
    "scenarios": scenarios,
}

with io.open("app/src/main/assets/campaigns/trors.json", "w", encoding="utf-8") as f:
    json.dump(template, f, ensure_ascii=False, indent=1)
    f.write("\n")

print("scenarios:", len(scenarios))
for s in scenarios:
    print(" ", s["id"], "| setup:", len(s["campaignSetup"]),
          "| prompts:", [p["id"] for p in s["onVictory"]["prompts"]])
