# -*- coding: utf-8 -*-
"""Writes app/src/main/assets/campaigns/fne.json.

Written as a script rather than by hand because the file repeats itself five
times over — every interchangeable scenario shares the same victory bookkeeping
— and five hand-copied blocks are five chances for them to drift apart.
"""
import io
import json
import os

FR, EN = "fr", "en"


def text(fr, en):
    return {FR: fr, EN: en}


# The five interchangeable scenarios: id, names, and the counter that records
# how far the villains have pushed it while the heroes were elsewhere.
SCENARIOS = [
    ("s1_musee", "Cambriolage du Musée d'Art", "Art Museum Heist", "pressionMusee"),
    ("s2_poursuite", "La Poursuite", "The Getaway", "pressionPoursuite"),
    ("s3_racket", "Racket en Bande Organisée", "Protection Racket", "pressionRacket"),
    ("s4_raft", "L'Évasion du Raft", "The Raft Breakout", "pressionRaft"),
    ("s5_rotatives", "Arrêtez les Rotatives !", "Stop the Presses", "pressionRotatives"),
]

# What each scenario suffers per tick already on it, from its own campaign
# setup. Mechanics only: what to place and where, never the book's prose.
PRESSURE_SETUP = {
    "s1_musee": text(
        "Placez 1 menace par case cochée sur la manigance principale (2 en Expert).",
        "Place 1 threat per ticked box on the main scheme (2 in Expert).",
    ),
    "s2_poursuite": text(
        "Trouvez et révélez le Camion-Citerne. À 2 cases cochées, placez 1 menace "
        "supplémentaire dessus (2 en Expert).",
        "Find and reveal the Tanker Truck. At 2 ticked boxes, place 1 extra threat "
        "on it (2 in Expert).",
    ),
    "s3_racket": text(
        "Placez 1 menace par case cochée sur chaque manigance principale (2 en Expert).",
        "Place 1 threat per ticked box on every main scheme (2 in Expert).",
    ),
    "s4_raft": text(
        "Donnez une carte d'état Tenace à chaque sbire PRISONNIER. À 2 cases cochées, "
        "donnez-leur aussi une carte de boost face cachée.",
        "Give each PRISONER minion a Tough status card. At 2 ticked boxes, also give "
        "each one a facedown boost card.",
    ),
    "s5_rotatives": text(
        "Retirez 1 jeton Endurance par case cochée de chaque soutien DAILY BUGLE.",
        "Remove 1 Use token per ticked box from each DAILY BUGLE support.",
    ),
}

VILLAINS = [
    ("hammerhead", "Hammerhead", "Hammerhead"),
    ("bullseye", "Bullseye", "Bullseye"),
    ("electro", "Electro", "Electro"),
    ("homme_pourpre", "L'Homme Pourpre", "Purple Man"),
    ("mary_typhoide", "Mary Typhoïde", "Typhoid Mary"),
]


def progression_actions():
    """One button per scenario, for the two environments drawn this game.

    KNOWN WRONG, and the reason is written down so the fix is not re-derived:
    page 9 runs draw -> progress -> choose, in that order, and this runs after
    the choice with five unbounded buttons. It must become part of the previous
    game's outcome so the ticks land before the table picks, exactly two
    environments are drawn (or one, ticked twice), and only environments still
    in the pool are offered.

    What is *not* wrong: the scenario played is not restricted to the two drawn.
    The villains push two places; the heroes may go anywhere unresolved.
    """
    steps = []
    for scenario_id, name_fr, name_en, counter in SCENARIOS:
        steps.append({
            "text": text(
                "Environnement pioché : %s" % name_fr,
                "Environment drawn: %s" % name_en,
            ),
            "when": {"all": [
                {"counter": c, "atMost": 0} for _, _, _, c in SCENARIOS
            ]},
            "action": {
                "id": "progress_" + scenario_id,
                "label": text("Faire progresser", "Advance"),
                "repeatable": True,
                "effects": [{"op": "addCounter", "counter": counter, "value": 1}],
            },
        })
    return steps


def environment_draw():
    """The p.9 draw, run at the end of a game so it lands before the choice.

    Two prompts rather than one: both drawn environments push their scenario,
    and when only one is left the rule is to tick it twice — which falls out
    for free if the player names it in both, so no third question is needed.

    The scenario played afterwards is deliberately unconstrained. The villains
    hit two places; the heroes go wherever they like, and the place they walk
    past is the one that falls.
    """
    options = [
        {"id": sid, "label": text(fr, en)}
        for sid, fr, en, _ in SCENARIOS
    ]
    prompts = [
        {
            "id": "env1",
            "type": "choice",
            "label": text(
                "Premier environnement pioché",
                "First environment drawn",
            ),
            "options": options,
        },
        {
            "id": "env2",
            "type": "choice",
            "label": text(
                "Second environnement pioché (le même s'il n'en restait qu'un)",
                "Second environment drawn (the same one if only one was left)",
            ),
            "options": options,
        },
    ]
    effects = []
    for prompt in ("env1", "env2"):
        for sid, _, _, counter in SCENARIOS:
            effects.append({
                "op": "addCounter",
                "counter": counter,
                "value": 1,
                "max": 3,
                "when": {"choice": prompt, "choiceIs": sid},
            })
    return prompts, effects


def pressure_report():
    """Where each place stands, shown before the table commits to one.

    The campaign's whole question is which place you can afford to walk past,
    and that is unanswerable if the ticks live only on a paper log. Two ticks
    is the moment worth saying out loud: one more visit from the villains and
    that scenario is gone.
    """
    steps = []
    for _, name_fr, name_en, counter in SCENARIOS:
        steps.append({
            "text": text(
                "%s : 1 case cochée." % name_fr,
                "%s: 1 box ticked." % name_en,
            ),
            "when": {"counter": counter, "equals": 1},
        })
        steps.append({
            "text": text(
                "%s : 2 cases cochées — une de plus et il est perdu." % name_fr,
                "%s: 2 boxes ticked — one more and it is lost." % name_en,
            ),
            "when": {"counter": counter, "equals": 2},
        })
        steps.append({
            "text": text(
                "%s : 3 cases cochées — échoué, son environnement reste en jeu." % name_fr,
                "%s: 3 boxes ticked — failed, its environment stays in play." % name_en,
            ),
            "when": {"counter": counter, "atLeast": 3},
        })
    return steps


def shared_campaign_setup():
    """The p.9 sequence every scenario runs before it is played."""
    steps = [{
        "text": text(
            "Retirez les environnements des scénarios achevés ou échoués, mélangez "
            "le reste et piochez-en deux. S'il n'en reste qu'un, il compte double.",
            "Remove the environments of finished or failed scenarios, shuffle the "
            "rest and draw two. If only one is left, it counts twice.",
        ),
    }]
    steps += pressure_report()
    steps += progression_actions()
    steps += [
        {
            "text": text(
                "Mettez en jeu l'environnement de chaque scénario résolu, face ACHEVÉ "
                "ou ÉCHOUÉ, et résolvez sa Mise en place.",
                "Put each resolved scenario's environment into play, ACHIEVED or "
                "FAILED face up, and resolve its Setup.",
            ),
            "when": {"any": [
                {"countTrue": "acheve", "countAtLeast": 1},
            ] + [
                {"counter": counter, "atLeast": 3} for _, _, _, counter in SCENARIOS
            ]},
        },
        {
            "text": text(
                "Retirez de vos decks les alliés et soutiens inscrits comme retirés "
                "de la campagne, puis complétez jusqu'à la taille légale.",
                "Remove from your decks the allies and supports recorded as removed "
                "from the campaign, then top up to the legal deck size.",
            ),
            "when": {"cardList": "alliesRetires", "minSize": 1},
        },
        {
            "text": text(
                "Mettez en jeu l'alliée de campagne Mary Typhoïde.",
                "Put the campaign ally Typhoid Mary into play.",
            ),
            "when": {"all": [
                {"flag": "confianceGagnee"},
                {"notFlag": "maryVaincue"},
            ]},
        },
        {
            "text": text(
                "Campagne Expert : reprenez les points de vie enregistrés au "
                "scénario précédent.",
                "Expert campaign: set each hit point total to the value recorded "
                "last scenario.",
            ),
            "when": {"difficulty": "expert"},
        },
        {
            "text": text(
                "Expert : chaque joueur peut prendre une carte Rencontre face cachée "
                "pour se soigner de sa REC.",
                "Expert: each player may take a facedown encounter card to heal for "
                "their REC.",
            ),
            "when": {"difficulty": "expert"},
        },
    ]
    return steps


def victory_outcome(scenario_id):
    """The same bookkeeping after every interchangeable scenario."""
    return {
        "prompts": [
            {
                "id": "vp",
                "type": "number",
                "label": text(
                    "Combien de points de victoire avez-vous accumulés ?",
                    "How many victory points did you gather?",
                ),
            },
            {
                "id": "confiance",
                "type": "boolean",
                "label": text(
                    "Psyché Perturbée est-elle en jeu avec au moins 2 menaces dessus ?",
                    "Is Disturbed Psyche in play with at least 2 threat on it?",
                ),
            },
            {
                "id": "mary",
                "type": "boolean",
                "label": text(
                    "Mary Typhoïde / Bloody Mary est-elle dans la pile de victoire ?",
                    "Is Typhoid Mary / Bloody Mary in the victory pile?",
                ),
            },
        ] + environment_draw()[0] + [
            {
                "id": "retires",
                "type": "deckCardSelect",
                "label": text(
                    "Quels alliés ou soutiens uniques ont été retirés de la partie ?",
                    "Which unique allies or supports were removed from the game?",
                ),
            },
        ],
        "effects": environment_draw()[1] + [
            {"op": "setFlag", "flag": "confianceGagnee", "boolValue": True,
             "when": {"answer": "confiance"}},
            {"op": "setFlag", "flag": "maryVaincue", "boolValue": True,
             "when": {"answer": "mary"}},
            {"op": "addCardsFromAnswer", "cardList": "alliesRetires", "from": "retires"},
            {"op": "setFlag", "flag": "acheve." + scenario_id, "boolValue": True},
        ],
        "next": [{"choose": True}],
    }


def defeat_outcome(scenario_id, counter):
    prompts, effects = environment_draw()
    return {
        "prompts": prompts,
        "effects": [
            # Standard lets a lost scenario be played again; only Expert pushes
            # it further along the track.
            {"op": "addCounter", "counter": counter, "value": 1, "max": 3,
             "when": {"difficulty": "expert"}},
        ] + effects,
        "next": [{"choose": True}],
    }


def scenario(scenario_id, name_fr, name_en, counter):
    return {
        "id": scenario_id,
        "name": text(name_fr, name_en),
        "victoryLabel": text("Le méchant est vaincu !", "The villain is beaten!"),
        "defeatLabel": text("Le méchant l'emporte !", "The villain wins!"),
        "baseSetup": {},
        "campaignSetup": [
            {"include": "campagne"},
            {
                "text": text(
                    "Méchant SUBORDONNÉ : celui déjà inscrit pour ce scénario, "
                    "sinon un jamais affronté, sinon un au hasard.",
                    "SUBORDINATE villain: the one already recorded for this "
                    "scenario, otherwise one never faced, otherwise one at random.",
                ),
            },
            {
                "text": PRESSURE_SETUP[scenario_id],
                "when": {"counter": counter, "atLeast": 1},
            },
        ],
        "onVictory": victory_outcome(scenario_id),
        "onDefeat": defeat_outcome(scenario_id, counter),
    }


def kingpin():
    return {
        "id": "s6_caid",
        "name": text("Le Caïd", "Kingpin"),
        "victoryLabel": text("Le Caïd est tombé !", "Kingpin has fallen!"),
        "defeatLabel": text("Le Caïd vous échappe.", "Kingpin gets away."),
        "baseSetup": {},
        "campaignSetup": [
            {"include": "campagne"},
            {
                "text": text(
                    "Ce scénario n'utilise pas le set Standard. En Expert, il utilise "
                    "le set Expert.",
                    "This scenario does not use the Standard set. In Expert it uses "
                    "the Expert set.",
                ),
            },
            {
                "text": text(
                    "Révélez votre sbire Némésis mis de côté. S'il double un personnage "
                    "en jeu, prenez un sbire SUBORDONNÉ à la place.",
                    "Reveal your set-aside nemesis minion. If it doubles a character in "
                    "play, take a SUBORDINATE minion instead.",
                ),
            },
            {
                "text": text(
                    "3 environnements ACHEVÉ ou plus : donnez une carte d'état Tenace à "
                    "chaque sbire en jeu.",
                    "3 or more ACHIEVED environments: give each minion in play a Tough "
                    "status card.",
                ),
                "when": {"countTrue": "acheve", "countAtLeast": 3},
            },
            {
                "text": text(
                    "4 environnements ACHEVÉ ou plus : trouvez et révélez James Wesley.",
                    "4 or more ACHIEVED environments: find and reveal James Wesley.",
                ),
                "when": {"countTrue": "acheve", "countAtLeast": 4},
            },
        ],
        "onVictory": {
            "prompts": [{
                "id": "vp",
                "type": "number",
                "label": text(
                    "Combien de points de victoire avez-vous accumulés ?",
                    "How many victory points did you gather?",
                ),
            }],
            "effects": [{"op": "setFlag", "flag": "acheve.s6_caid", "boolValue": True}],
            "next": [{"end": True}],
        },
        "onDefeat": {
            "prompts": [{
                "id": "retourne",
                "type": "boolean",
                "label": text(
                    "Expert : avez-vous retourné un environnement ACHEVÉ sur sa face "
                    "ÉCHOUÉ pour rejouer ?",
                    "Expert: did you flip an ACHIEVED environment to its FAILED face "
                    "to replay?",
                ),
                "when": {"difficulty": "expert"},
            }],
            "next": [
                # Expert with nothing left to flip is the one place the campaign
                # can be lost outright.
                {"end": True, "when": {"all": [
                    {"difficulty": "expert"},
                    {"notAnswer": "retourne"},
                ]}},
                {"goto": "s6_caid"},
            ],
        },
    }


def build():
    counters = [
        {
            "id": counter,
            "scope": "campaign",
            "initial": 0,
            "min": 0,
            # Three ticks and the scenario has failed. The book says that is the
            # scenario, not the campaign: "Si un scénario a 3 coches à sa droite,
            # il a Échoué", and p.8 adds that losing a scenario is not losing the
            # campaign. The penalty is its environment entering play ÉCHOUÉ for
            # the rest of the run, and a harder Caïd at the end.
            "max": 3,
        }
        for _, _, _, counter in SCENARIOS
    ]
    counters.append({
        "id": "hp",
        "scope": "hero",
        "initial": 0,
        "maxFrom": "heroCard.health",
        "activeWhen": {"difficulty": "expert"},
    })

    template = {
        "_note": [
            "Peur de Rien / Fear No Evil. Mechanics only: what to place, what to",
            "record, and what carries to the next game. No rules text and nothing",
            "reproduced from the campaign book — this file cannot be played from.",
            "",
            "Unlike every other campaign here there is no scenario order. Two",
            "environments are drawn before each game and push their scenarios",
            "along whether or not anybody goes there; the table then picks which",
            "one to actually play. Three ticks and that scenario is lost.",
            "",
            "Card codes are absent on purpose: MarvelCDB has published only the",
            "hero side of this pack, so there is nothing stable to point at yet.",
            "Setup steps name the cards in words until it does.",
        ],
        "id": "fne",
        "schemaVersion": 1,
        "name": text("Peur de Rien", "Fear No Evil"),
        "packCode": "fne",
        "difficulties": ["standard", "expert"],
        "chooseFirstScenario": True,
        "finaleScenarioId": "s6_caid",
        "counters": counters,
        "flagSets": [
            {"id": "acheve", "scope": "perScenario"},
            {"id": "confianceGagnee", "scope": "campaign"},
            {"id": "maryVaincue", "scope": "campaign"},
        ],
        "cardLists": [{"id": "alliesRetires", "scope": "campaign"}],
        "setupFragments": {"campagne": shared_campaign_setup()},
        "scenarios": [scenario(*s) for s in SCENARIOS] + [kingpin()],
    }

    path = "app/src/main/assets/campaigns/fne.json"
    with io.open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(template, out, ensure_ascii=False, indent=1)
        out.write("\n")
    print("wrote", path, os.path.getsize(path), "bytes")
    print("scenarios:", len(template["scenarios"]))


build()
