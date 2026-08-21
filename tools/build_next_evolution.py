# -*- coding: utf-8 -*-
"""Builds the NeXt Evolution campaign template.

Five scenarios in a fixed order, Morlock Siege through Stryfe, and all five
have to be won. A loss costs nothing and is played again, which is the
rulebook's own wording.

THE PLAYER SIDE SCHEMES, WHICH ARE THE WHOLE CAMPAIGN

Six campaign player side schemes, each with a campaign environment printed on
its back. Every scenario the table picks one they have not picked before, put
it into play, and shuffle that scheme's paired encounter card into the deck for
the rest of the campaign, whether or not they beat the scheme.

Beating it before the scenario ends flips it and earns the environment, which
then comes out in every later scenario. Failing to beat it loses that reward
for good: the card is out of the campaign and cannot be chosen again.

The app does not draw this. The rules say the players choose, so it is offered
as six actions, each of which stops being offered once its scheme is recorded.
That is what `notContains` is for.

Two more things carry: which Marauders were routed in scenario 1, since they
are removed from the game in scenario 2, and how much damage Hope Summers took,
which the players may hand back as damage or as threat next scenario.

Card codes are MarvelCDB codes for pack 'next_evol'. The pairing of scheme to
encounter card to environment is the table printed in the campaign log, not a
guess.
"""
import io
import json

NL = chr(10)

# --- villains ---------------------------------------------------------------
# Seven Marauders, shared by scenarios 1 and 2. The (A) sides are standard, the
# (B) sides expert.
MARAUDERS_A = ["40070a", "40071a", "40072a", "40073a", "40074a", "40075a", "40076a"]
MARAUDERS_B = ["40070b", "40071b", "40072b", "40073b", "40074b", "40075b", "40076b"]

JUGGERNAUT = ["40118", "40119", "40120"]
SINISTER = ["40136", "40137", "40138"]
STRYFE = ["40163", "40164", "40165"]

# --- the campaign's own cards -----------------------------------------------
# scheme, environment, the encounter card it drags in, and the printed name.
# Straight from the campaign log table on the back cover.
SIDE_SCHEMES = [
    ("40190a", "40190b", "40199", "Assemble the Team"),
    ("40191a", "40191b", "40201", "Establish Safehouse"),
    ("40192a", "40192b", "40203", "Gear Up"),
    ("40193a", "40193b", "40200", "Mission Prep"),
    ("40194a", "40194b", "40198", "Practice Maneuvers"),
    ("40195a", "40195b", "40202", "Prepare Defenses"),
]

HOPE_SUMMERS = "40204"
TELEPORTED_AWAY = "40146"
STRYFES_GRASP = "40168a"
BLACK_TOM = "40134"


# ---------------------------------------------------------------------------
# FRENCH
#
# Names come from MarvelCDB's French data rather than a translation, since the
# pack is published in French and the player is holding the card. Game terms
# follow the reference glossary: mechant, sbire, manigance, carte Rencontre.
# ---------------------------------------------------------------------------

FR = {
    "Remove these Marauders from the game before resolving the main scheme's setup.\nMinions of the same name stay in the encounter deck.":
        'Retirez ces Maraudeurs de la partie avant de résoudre la mise en place de la manigance principale.\nLes sbires du même nom restent dans le deck Rencontre.',
    'Unofficial, reconstructed for the app: the rulebook and cards from the NeXt Evolution box are still needed to play.\n\nThe app keeps the campaign log for you. Which player side schemes you have already chosen, which environments you earned by beating them, the encounter cards they dragged into the deck for good, which Marauders were routed, and how much damage Hope Summers is carrying.\n\nThe side scheme each scenario is a choice, not a draw, so the app offers the ones you have not used and records what you pick rather than picking for you.\n\nExpert is the expert campaign: the harder villain stages, and hit points that carry from one scenario to the next.':
        "Non officiel, reconstitué pour l'application : le livret et les cartes de la boîte Next Evolution restent nécessaires pour jouer.\n\nL'application tient le journal de campagne pour vous. Quelles manigances annexes joueur vous avez déjà choisies, quels environnements vous avez acquis en les vainquant, les cartes Rencontre qu'elles ont ajoutées définitivement au deck, quels Maraudeurs ont été mis en déroute, et combien de dégâts Hope Summers porte encore.\n\nLa manigance annexe de chaque scénario est un choix, pas un tirage : l'application propose celles que vous n'avez pas encore utilisées et note celle que vous prenez, au lieu de choisir à votre place.\n\nExpert correspond à la campagne experte : les stades de méchant les plus durs, et des points de vie qui se reportent d'un scénario au suivant.",
    'In player order, discard encounter cards until a minion or a Psionic attachment is discarded, and reveal it.\nThen shuffle the discard pile back in.':
        "Dans l'ordre du tour, défaussez des cartes Rencontre jusqu'à en défausser un sbire ou un attachement Psionique, et révélez-le.\nPuis remélangez la pile de défausse dans le deck.",
    'Each player may deal themself 1 facedown encounter card to heal to full.\nA player defeated last scenario rejoins this way.':
        'Chaque joueur peut se distribuer 1 carte Rencontre face cachée pour se soigner complètement.\nUn joueur vaincu au scénario précédent revient ainsi.',
    'Each player may place 1 acceleration token on the main scheme to heal to full.\nA player defeated last scenario rejoins this way.':
        "Chaque joueur peut placer 1 jeton d'accélération sur la manigance principale pour se soigner complètement.\nUn joueur vaincu au scénario précédent revient ainsi.",
    'A player defeated last scenario rejoins this way.':
        'Un joueur vaincu au scénario précédent revient ainsi.',
    'Assemble the Team':
        "Réunir l'Équipe",
    'Choose one campaign player side scheme you have not chosen before, and put it into play.':
        "Choisissez une manigance annexe joueur de campagne que vous n'avez pas encore choisie, et mettez-la en jeu.",
    'Choose this one':
        'Choisir celle-ci',
    'Defeat 3 of the 7 Marauders to win.':
        'Vainquez 3 des 7 Maraudeurs pour gagner.',
    'Did you defeat the campaign player side scheme you chose?':
        'Avez-vous vaincu la manigance annexe joueur de campagne que vous aviez choisie ?',
    'Each player may deal themself 1 facedown encounter card to heal to full.':
        'Chaque joueur peut se distribuer 1 carte Rencontre face cachée pour se soigner complètement.',
    'Each player may place 1 acceleration token on the main scheme to heal to full.':
        "Chaque joueur peut placer 1 jeton d'accélération sur la manigance principale pour se soigner complètement.",
    'Establish Safehouse':
        'Établir un Refuge',
    'Expert is the expert campaign: the harder villain stages, and hit points that carry from one scenario to the next.':
        "Expert correspond à la campagne experte : les stades de méchant les plus durs, et des points de vie qui se reportent d'un scénario au suivant.",
    'For each Morlock saved last scenario, choose a player to search their deck for 1 card, add it to hand, then shuffle.':
        "Pour chaque Morlock sauvé au scénario précédent, choisissez un joueur qui cherche 1 carte dans son deck, l'ajoute à sa main, puis mélange.",
    'Gear Up':
        "S'Armer Jusqu'aux Dents",
    'Give each enemy a tough status card, because an earned environment is in play.':
        'Donnez une carte état Coriace à chaque ennemi, car un environnement acquis est en jeu.',
    "Hope Summers cannot be in any player's deck for this campaign.":
        "Hope Summers ne peut être dans le deck d'aucun joueur pendant cette campagne.",
    "Hope's captor gave up the address, an orphanage in Omaha. The door comes off its hinges before you reach it.":
        "Le ravisseur de Hope a lâché l'adresse, un orphelinat à Omaha. La porte sort de ses gonds avant même que vous l'atteigniez.",
    'How many Morlock allies are still in play?':
        "Combien d'alliés Morlocks sont encore en jeu ?",
    'How many victory points are in the victory display?':
        'Combien de points de victoire y a-t-il dans la zone de victoire ?',
    'How much damage is on Hope Summers?':
        'Combien de dégâts y a-t-il sur Hope Summers ?',
    'In player order, discard encounter cards until a minion or a Psionic attachment is discarded, and reveal it.':
        "Dans l'ordre du tour, défaussez des cartes Rencontre jusqu'à en défausser un sbire ou un attachement Psionique, et révélez-le.",
    'Juggernaut':
        'Le Fléau',
    'Mission Prep':
        'Préparer la Mission',
    'Mister Sinister':
        'Mister Sinistre',
    'Morlock Siege':
        'Le Siège des Morlocks',
    'NeXt Evolution':
        'Next Evolution',
    'On the Run':
        'En Fuite',
    'One Marauder is drawn to carry Hope. Defeat that villain twice to win.':
        'Un Maraudeur est tiré pour emmener Hope. Vainquez ce méchant deux fois pour gagner.',
    'Place 1 momentum counter on Juggernaut for each campaign environment in play.':
        'Placez 1 jeton Élan sur Le Fléau pour chaque environnement de campagne en jeu.',
    "Place 1 threat on Stryfe's Grasp for each campaign environment in play.":
        'Placez 1 menace sur Dans les Griffes de Stryfe pour chaque environnement de campagne en jeu.',
    "Place the damage Hope Summers carried out of the last scenario either on her, or as that much threat on Stryfe's Grasp.":
        'Placez les dégâts que Hope Summers a emportés du scénario précédent soit sur elle, soit en autant de menace sur Dans les Griffes de Stryfe.',
    'Place the damage Hope Summers carried out of the last scenario either on her, or as that much threat on Teleported Away.':
        'Placez les dégâts que Hope Summers a emportés du scénario précédent soit sur elle, soit en autant de menace sur Téléporté Ailleurs.',
    'Practice Maneuvers':
        'Entraînement aux Manœuvres',
    'Prepare Defenses':
        'Préparer les Défenses',
    'Put Teleported Away into play, with 1 extra threat for each campaign environment in play.':
        'Mettez Téléporté Ailleurs en jeu, avec 1 menace supplémentaire pour chaque environnement de campagne en jeu.',
    'Put these earned campaign environments into play, in any order.':
        "Mettez en jeu ces environnements de campagne acquis, dans l'ordre de votre choix.",
    'Remaining hit points':
        'Points de vie restants',
    "Remove these Marauders from the game before resolving the main scheme's setup. Minions of the same name stay in the encounter deck.":
        'Retirez ces Maraudeurs de la partie avant de résoudre la mise en place de la manigance principale. Les sbires du même nom restent dans le deck Rencontre.',
    "Set each player's hit points to what they had left at the end of the last scenario.":
        "Fixez les points de vie de chaque joueur à ce qu'il lui restait à la fin du scénario précédent.",
    'Shuffle Black Tom Cassidy with 1 Creeping Willow and deal one to each player facedown. Shuffle the rest into the encounter deck.':
        'Mélangez Black Tom Cassidy avec 1 Saule Rampant et distribuez-en une face cachée à chaque joueur. Mélangez le reste dans le deck Rencontre.',
    'Shuffle these into the encounter deck. They stay there for the rest of the campaign.':
        'Mélangez celles-ci dans le deck Rencontre. Elles y restent pour tout le reste de la campagne.',
    "Sinister went through the portal and Hope went after him. On the other side, Cable's clone is waiting.":
        "Sinistre est passé par le portail et Hope l'a suivi. De l'autre côté, le clone de Cable attend.",
    'Stryfe':
        'Stryfe',
    'Stryfe escapes into the past.':
        "Stryfe s'échappe dans le passé.",
    'The Marauders came for the Morlocks in the tunnels under the city, and Hope Summers was already down there when they arrived.':
        'Les Maraudeurs sont venus chercher les Morlocks dans les tunnels sous la ville, et Hope Summers était déjà en bas quand ils sont arrivés.',
    'The Marauders took Hope with them when they fell back, and they are handing her to the Nasty Boys.':
        "Les Maraudeurs ont emmené Hope en battant en retraite, et ils s'apprêtent à la livrer aux Mauvais Garçons.",
    'The app keeps the campaign log for you. Which player side schemes you have already chosen, which environments you earned by beating them, the encounter cards they dragged into the deck for good, which Marauders were routed, and how much damage Hope Summers is carrying.':
        "L'application tient le journal de campagne pour vous. Quelles manigances annexes joueur vous avez déjà choisies, quels environnements vous avez acquis en les vainquant, les cartes Rencontre qu'elles ont ajoutées définitivement au deck, quels Maraudeurs ont été mis en déroute, et combien de dégâts Hope Summers porte encore.",
    'The side scheme each scenario is a choice, not a draw, so the app offers the ones you have not used and records what you pick rather than picking for you.':
        "La manigance annexe de chaque scénario est un choix, pas un tirage : l'application propose celles que vous n'avez pas encore utilisées et note celle que vous prenez, au lieu de choisir à votre place.",
    'Then shuffle the discard pile back in.':
        'Puis remélangez la pile de défausse dans le deck.',
    'Under the orphanage there is a lift, and under the lift there are vats with people in them.':
        "Sous l'orphelinat il y a un ascenseur, et sous l'ascenseur il y a des cuves avec des gens dedans.",
    'Unofficial, reconstructed for the app: the rulebook and cards from the NeXt Evolution box are still needed to play.':
        "Non officiel, reconstitué pour l'application : le livret et les cartes de la boîte Next Evolution restent nécessaires pour jouer.",
    'Which Marauders ended the game under Routed? They are out of the game from here.':
        'Quels Maraudeurs ont fini la partie sous Déroute ? Ils quittent la partie à partir de maintenant.',
}


def t(en):
    return {"en": en, "fr": FR.get(en, en)}


def missing_french(data):
    missing = []

    def walk(node):
        if isinstance(node, dict):
            en = node.get("en")
            if isinstance(en, str) and node.get("fr") == en and en not in FR:
                missing.append(en)
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(data)
    return missing


# ---------------------------------------------------------------------------
# SETUP
# ---------------------------------------------------------------------------

def choose_side_scheme(scenario_id):
    """The one choice the campaign turns on, offered every scenario.

    Six actions rather than a draw: the rules have the table choose, and a
    choice the app makes for them would take away the only real decision in
    the campaign. Each stops being offered once its scheme is recorded, so the
    pool shrinks exactly as the printed log does.

    Choosing also files the paired encounter card, because that goes into the
    deck whether or not the scheme is ever beaten.
    """
    steps = [{
        "text": t("Choose one campaign player side scheme you have not chosen "
                  "before, and put it into play."),
        "showCardList": "chosenSchemes",
    }]
    for scheme, environment, encounter, name in SIDE_SCHEMES:
        steps.append({
            "text": t(name),
            "cards": [scheme],
            "when": {"cardList": "chosenSchemes", "notContains": scheme},
            "action": {
                "id": "choose_" + scheme,
                "label": t("Choose this one"),
                "effects": [
                    {"op": "addCard", "cardList": "chosenSchemes", "cardCode": scheme},
                    {"op": "addCard", "cardList": "schemeEncounters", "cardCode": encounter},
                    # Remembered per scenario as well, so a replay after a loss
                    # knows which one this scenario is committed to.
                    {"op": "addCard", "cardList": "thisScenarioScheme", "cardCode": scheme},
                ],
            },
        })
    steps.append({
        "text": t("Shuffle these into the encounter deck. They stay there for "
                  "the rest of the campaign."),
        "showCardList": "schemeEncounters",
        "when": {"cardList": "schemeEncounters", "minSize": 1},
    })
    return steps


def earned_environments():
    """Every environment won so far, out on the table again."""
    return [{
        "text": t("Put these earned campaign environments into play, in any order."),
        "showCardList": "earnedEnvironments",
        "when": {"cardList": "earnedEnvironments", "minSize": 1},
    }]


def expert_steps(heal="acceleration"):
    """Persistent damage, which is the whole of the expert campaign."""
    if heal == "acceleration":
        healing = ("Each player may place 1 acceleration token on the main "
                   "scheme to heal to full.")
    else:
        healing = ("Each player may deal themself 1 facedown encounter card "
                   "to heal to full.")
    return [
        {
            "text": t("Set each player's hit points to what they had left at "
                      "the end of the last scenario."),
            "when": {"difficulty": "expert"},
        },
        {
            "text": t(healing + NL + "A player defeated last scenario rejoins this way."),
            "when": {"difficulty": "expert"},
        },
    ]


# ---------------------------------------------------------------------------
# THE END OF A SCENARIO
# ---------------------------------------------------------------------------

def victory(goto, marauders=False, morlocks=False, hope=False, final=False):
    """The victory steps, in the order the rulebook lists them."""
    prompts = [
        {"id": "vp", "type": "number",
         "label": t("How many victory points are in the victory display?")},
    ]
    effects = []

    if marauders:
        prompts.append({
            "id": "routed", "type": "cardSelect", "cards": MARAUDERS_A,
            "label": t("Which Marauders ended the game under Routed? They are "
                       "out of the game from here."),
        })
        effects.append({"op": "addCardsFromAnswer", "cardList": "maraudersDefeated",
                        "from": "routed"})

    if morlocks:
        prompts.append({
            "id": "morlocks", "type": "number",
            "label": t("How many Morlock allies are still in play?"),
        })
        effects.append({"op": "setCounter", "counter": "morlocksSaved", "from": "morlocks"})

    # The scheme is earned only if it was beaten. Asked rather than assumed:
    # the app has no way to know what happened at the table.
    prompts.append({
        "id": "schemeBeaten", "type": "boolean",
        "label": t("Did you defeat the campaign player side scheme you chose?"),
    })
    for scheme, environment, _encounter, _name in SIDE_SCHEMES:
        effects.append({
            "op": "addCard", "cardList": "earnedEnvironments", "cardCode": environment,
            "when": {
                "all": [
                    {"answer": "schemeBeaten"},
                    {"cardList": "thisScenarioScheme", "contains": scheme},
                ],
            },
        })

    if hope:
        prompts.append({
            "id": "hopeDamage", "type": "number",
            "label": t("How much damage is on Hope Summers?"),
        })
        effects.append({"op": "setCounter", "counter": "hopeDamage", "from": "hopeDamage"})

    prompts.append({
        "id": "hpPerHero", "type": "perHeroNumber",
        "label": t("Remaining hit points"),
        "when": {"difficulty": "expert"},
    })
    effects.append({"op": "setHeroCounter", "counter": "hp", "from": "hpPerHero",
                    "when": {"difficulty": "expert"}})

    return {
        "prompts": prompts,
        "effects": effects,
        "next": [{"end": True} if goto is None else {"goto": goto}],
    }


def defeat(scenario_id, final=False):
    """A loss costs nothing and is played again.

    The scheme chosen for this scenario stays chosen: the rulebook says a
    replay must face the same one, and beat it, to earn its reward.
    """
    if not final:
        return {"next": [{"goto": scenario_id}]}
    return {
        "message": t("Stryfe escapes into the past."),
        "next": [
            {"end": True, "when": {"difficulty": "expert"}},
            {"goto": scenario_id},
        ],
    }


# ---------------------------------------------------------------------------
# THE SCENARIOS
# ---------------------------------------------------------------------------

def build():
    scenarios = [
        {
            "id": "s1_morlock_siege",
            "name": t("Morlock Siege"),
            "flavour": t("The Marauders came for the Morlocks in the tunnels "
                         "under the city, and Hope Summers was already down "
                         "there when they arrived."),
            "baseSetup": {
                "villainDeck": {"standard": MARAUDERS_A, "expert": MARAUDERS_B},
                "mainScheme": ["40077a"],
                "encounterSets": ["morlock_siege", "military_grade",
                                  "mutant_slayers", "standard"],
            },
            "campaignSetup": (
                [{"text": t("Hope Summers cannot be in any player's deck for "
                            "this campaign."), "cards": [HOPE_SUMMERS]}]
                + choose_side_scheme("s1_morlock_siege")
                + [{"text": t("Defeat 3 of the 7 Marauders to win.")}]
            ),
            "onVictory": victory("s2_on_the_run", marauders=True, morlocks=True),
            "onDefeat": defeat("s1_morlock_siege"),
        },
        {
            "id": "s2_on_the_run",
            "name": t("On the Run"),
            "flavour": t("The Marauders took Hope with them when they fell "
                         "back, and they are handing her to the Nasty Boys."),
            "baseSetup": {
                "villainDeck": {"standard": MARAUDERS_A, "expert": MARAUDERS_B},
                "mainScheme": ["40103a"],
                "encounterSets": ["on_the_run", "military_grade",
                                  "mutant_slayers", "nasty_boys", "standard"],
            },
            "campaignSetup": (
                [{
                    "text": t("Remove these Marauders from the game before "
                              "resolving the main scheme's setup." + NL +
                              "Minions of the same name stay in the encounter "
                              "deck."),
                    "showCardList": "maraudersDefeated",
                    "when": {"cardList": "maraudersDefeated", "minSize": 1},
                }, {
                    "text": t("One Marauder is drawn to carry Hope. Defeat "
                              "that villain twice to win."),
                    "draw": {"id": "captor", "from": MARAUDERS_A,
                             "excluding": "maraudersDefeated"},
                }, {
                    "text": t("For each Morlock saved last scenario, choose a "
                              "player to search their deck for 1 card, add it "
                              "to hand, then shuffle."),
                    "when": {"counter": "morlocksSaved", "atLeast": 1},
                }]
                + earned_environments()
                + [{
                    "text": t("Give each enemy a tough status card, because an "
                              "earned environment is in play."),
                    "when": {"cardList": "earnedEnvironments", "minSize": 1},
                }]
                + choose_side_scheme("s2_on_the_run")
                + expert_steps()
            ),
            "onVictory": victory("s3_juggernaut"),
            "onDefeat": defeat("s2_on_the_run"),
        },
        {
            "id": "s3_juggernaut",
            "name": t("Juggernaut"),
            "flavour": t("Hope's captor gave up the address, an orphanage in "
                         "Omaha. The door comes off its hinges before you "
                         "reach it."),
            "baseSetup": {
                "villainDeck": {"standard": JUGGERNAUT[:2],
                                "expert": JUGGERNAUT[1:]},
                "mainScheme": ["40121a"],
                "encounterSets": ["juggernaut", "hope_summers",
                                  "black_tom_cassidy", "standard"],
            },
            "campaignSetup": (
                earned_environments()
                + [{
                    "text": t("Place 1 momentum counter on Juggernaut for each "
                              "campaign environment in play."),
                    "when": {"cardList": "earnedEnvironments", "minSize": 1},
                }, {
                    "text": t("Shuffle Black Tom Cassidy with 1 Creeping Willow "
                              "and deal one to each player facedown. Shuffle "
                              "the rest into the encounter deck."),
                    "cards": [BLACK_TOM],
                }]
                + choose_side_scheme("s3_juggernaut")
                + expert_steps(heal="encounter")
            ),
            "onVictory": victory("s4_mister_sinister", hope=True),
            "onDefeat": defeat("s3_juggernaut"),
        },
        {
            "id": "s4_mister_sinister",
            "name": t("Mister Sinister"),
            "flavour": t("Under the orphanage there is a lift, and under the "
                         "lift there are vats with people in them."),
            "baseSetup": {
                "villainDeck": {"standard": SINISTER[:2], "expert": SINISTER[1:]},
                "mainScheme": ["40139a"],
                "encounterSets": ["mister_sinister", "flight", "super_strength",
                                  "telepathy", "hope_summers", "nasty_boys",
                                  "standard"],
            },
            "campaignSetup": (
                earned_environments()
                + [{
                    "text": t("Put Teleported Away into play, with 1 extra "
                              "threat for each campaign environment in play."),
                    "cards": [TELEPORTED_AWAY],
                }, {
                    "text": t("Place the damage Hope Summers carried out of "
                              "the last scenario either on her, or as that "
                              "much threat on Teleported Away."),
                    "when": {"counter": "hopeDamage", "atLeast": 1},
                }]
                + choose_side_scheme("s4_mister_sinister")
                + expert_steps()
            ),
            "onVictory": victory("s5_stryfe", hope=True),
            "onDefeat": defeat("s4_mister_sinister"),
        },
        {
            "id": "s5_stryfe",
            "name": t("Stryfe"),
            "flavour": t("Sinister went through the portal and Hope went after "
                         "him. On the other side, Cable's clone is waiting."),
            "baseSetup": {
                "villainDeck": {"standard": STRYFE[:2], "expert": STRYFE[1:]},
                "mainScheme": ["40166a"],
                "encounterSets": ["stryfe", "hope_summers", "extreme_measures",
                                  "mutant_insurrection", "standard"],
            },
            "campaignSetup": (
                earned_environments()
                + [{
                    "text": t("Place 1 threat on Stryfe's Grasp for each "
                              "campaign environment in play."),
                    "cards": [STRYFES_GRASP],
                }, {
                    "text": t("Place the damage Hope Summers carried out of "
                              "the last scenario either on her, or as that "
                              "much threat on Stryfe's Grasp."),
                    "when": {"counter": "hopeDamage", "atLeast": 1},
                }, {
                    "text": t("In player order, discard encounter cards until "
                              "a minion or a Psionic attachment is discarded, "
                              "and reveal it." + NL +
                              "Then shuffle the discard pile back in."),
                }]
                + choose_side_scheme("s5_stryfe")
                + expert_steps(heal="encounter")
            ),
            "onVictory": victory(None),
            "onDefeat": defeat("s5_stryfe", final=True),
        },
    ]

    return {
        "id": "next",
        "schemaVersion": 1,
        "name": t("NeXt Evolution"),
        "untested": True,
        "packCode": "next_evol",
        "notice": t(
            "Unofficial, reconstructed for the app: the rulebook and cards "
            "from the NeXt Evolution box are still needed to play."
            + NL + NL +
            "The app keeps the campaign log for you. Which player side "
            "schemes you have already chosen, which environments you earned "
            "by beating them, the encounter cards they dragged into the deck "
            "for good, which Marauders were routed, and how much damage Hope "
            "Summers is carrying."
            + NL + NL +
            "The side scheme each scenario is a choice, not a draw, so the "
            "app offers the ones you have not used and records what you pick "
            "rather than picking for you."
            + NL + NL +
            "Expert is the expert campaign: the harder villain stages, and "
            "hit points that carry from one scenario to the next."
        ),
        "difficulties": ["standard", "expert"],
        "counters": [
            {"id": "hp", "scope": "hero", "initial": 0,
             "maxFrom": "heroCard.health"},
            {"id": "morlocksSaved", "scope": "campaign", "initial": 0},
            {"id": "hopeDamage", "scope": "campaign", "initial": 0},
        ],
        "flagSets": [],
        "cardLists": [
            {"id": "chosenSchemes", "scope": "campaign"},
            {"id": "schemeEncounters", "scope": "campaign"},
            {"id": "thisScenarioScheme", "scope": "scenario"},
            {"id": "earnedEnvironments", "scope": "campaign"},
            {"id": "maraudersDefeated", "scope": "campaign"},
        ],
        "startScenarioId": "s1_morlock_siege",
        "scenarios": scenarios,
    }


if __name__ == "__main__":
    data = build()
    missing = missing_french(data)
    if missing:
        print("Untranslated (%d):" % len(missing))
        for line in missing:
            print("   ", line)
    out = "app/src/main/assets/campaigns/next.json"
    io.open(out, "w", encoding="utf-8", newline=NL).write(
        json.dumps(data, ensure_ascii=False, indent=2) + NL
    )
    print("wrote", out)
