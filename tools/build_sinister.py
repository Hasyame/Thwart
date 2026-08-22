# -*- coding: utf-8 -*-
"""Builds the Sinister Motives campaign template.

English first. Every string carries only an `en` value, and LocalizedText falls
back to it, so a French player reads English here until the translation lands
rather than reading a blank.

THE REPUTATION TRACK

This is the campaign's own machinery, and it turns out to need no engine work.
A campaign counter holds the score. The six conditions on the printed track are
six questions at the end of a scenario, and their answers add to that counter —
the app does the adding, so nobody totals a column by hand. Each box on the
track is a setup step gated on the counter having reached that node, which is
exactly what a "Setup:" box means: it triggers at the start of every remaining
scenario.

Boxes that are not "Setup:" boxes resolve once, when the node is first marked.
Those carry a flag and check it, so they stop offering themselves afterwards.

WHAT THE APP DRAWS

The Community Service side scheme and the Osborn Tech attachments are drawn at
random by the rules, so the app draws them: `draw` picks, `excluding` keeps it
off what is already recorded, and `addDrawnCard` files the result. The players
are asked whether the side scheme ended in the victory display, never which one
it was — the app dealt it and already knows.

The S.H.I.E.L.D. Tech reward deals three at random per player, so it needs a
per-hero draw rather than the campaign-wide kind.

Card codes are MarvelCDB codes for pack 'sm', read from the bundled card
database rather than transcribed.
"""
import io
import json

# --- villains, one stage per difficulty step --------------------------------
SANDMAN = ["27061", "27062", "27063"]
VENOM = ["27073", "27074", "27075"]
MYSTERIO = ["27084", "27085", "27086"]
VENOM_GOBLIN = ["27113", "27114", "27115"]

# The Sinister Six share a scenario, so all six are the villain deck at once.
SINISTER_SIX = ["27094", "27095", "27096", "27097", "27098", "27099"]

# --- the cards the campaign instructions name -------------------------------
PUBLIC_OUTCRY_STANDARD = "27174a"
PUBLIC_OUTCRY_EXPERT = "27174b"
SMEAR_CAMPAIGN = "27175"
SNITCHES = "27181"
VENOM_ALLY = "27190"
LIGHT_AT_THE_END = "27102a"

COMMUNITY_SERVICE = ["27176", "27177", "27178", "27179", "27180"]
OSBORN_TECH = ["27147", "27148", "27149", "27150", "27151", "27152"]
SHIELD_TECH = ["27182a", "27183a", "27184a", "27185a", "27186a", "27187a",
               "27188a", "27189a"]

# Minions carrying the names of the Sinister Six, for the finale.
SINISTER_ASSAULT = ["27158", "27159", "27160", "27161", "27162", "27163"]


# The three Osborn Tech boxes are the same box at three nodes.
OSBORN_DRAW = "__osborn__"

# The S.H.I.E.L.D. Tech reward, dealt three at a time to each player.
SHIELD_DEAL = "__shield__"


# ---------------------------------------------------------------------------
# FRENCH
#
# Keyed by the English string, so adding a language costs a dictionary rather
# than an edit at every call site. Terms follow the bilingual rules reference
# the app bundles; card and set names follow MarvelCDB's French data, since this
# pack is published and the player is holding the card.
# ---------------------------------------------------------------------------

FR = {
    # The name printed on the French box, from MarvelCDB rather than
    # translated here.
    "Sinister Motives": "Sinistres Motivations",
    "Unofficial, reconstructed for the app: the rulebook and cards from "
    "the Sinister Motives box are still needed to play."
    "\n\n"
    "The app keeps the reputation track for you. It asks the six printed "
    "conditions after each scenario, does the adding, and shows only the "
    "boxes the track has reached. It also deals what the rules deal at "
    "random: the Community Service side scheme, the Osborn Tech "
    "attachments, and each player's three S.H.I.E.L.D. Tech upgrades.":
        "Campagne non officielle, reconstituée pour l'application : le livret "
        "et les cartes de la boîte Sinistres Motivations restent nécessaires "
        "pour jouer."
        "\n\n"
        "L'application tient la piste de réputation à votre place : elle pose "
        "les six conditions imprimées après chaque scénario, fait l'addition et "
        "n'affiche que les cases atteintes. Elle distribue aussi ce que les "
        "règles tirent au hasard : la manigance annexe Intérêt Général, les "
        "attachements Technologie Osborn et les trois améliorations Techno du "
        "SHIELD de chaque joueur.",

    # The reputation track.
    "An Osborn Tech attachment is drawn and recorded.":
        "Un attachement Technologie Osborn est tiré et enregistré.",
    "Record it": "Enregistrer",
    "Record them": "Enregistrer",
    "Done": "Fait",
    "Setup: shuffle every recorded Osborn Tech attachment into the encounter "
    "deck.":
        "Mise en place : mélangez dans le deck Rencontre chaque attachement "
        "Technologie Osborn enregistré.",
    "Three S.H.I.E.L.D. Tech upgrades are dealt to each player. Keep one for "
    "the rest of the campaign.":
        "Trois améliorations Techno du SHIELD sont distribuées à chaque joueur. "
        "Gardez-en une pour le reste de la campagne.",
    "Setup: place 1 threat per player on the main scheme.":
        "Mise en place : placez 1 menace par joueur sur la manigance principale.",
    "Each player may take 1 additional mulligan during step 13 of setup.":
        "Chaque joueur peut faire 1 mulligan supplémentaire à l'étape 13 de la "
        "mise en place.",
    "Setup: in player order, each player puts a minion from the encounter deck "
    "or discard pile into play engaged with them. Shuffle.\n"
    "Any player who does not takes 1 facedown encounter card.":
        "Mise en place : dans l'ordre du tour, chaque joueur met en jeu un sbire "
        "du deck Rencontre ou de la pile de défausse, engagé avec lui.\n"
        "Mélangez. Tout joueur qui ne le fait pas reçoit 1 carte Rencontre face "
        "cachée.",
    "Each player adds the maximum copies of one aspect card of their choice, "
    "from any aspect, to their deck for the rest of the campaign.":
        # Two lines: French runs longer than the English here, and the guard
        # caps a setup step at a line.
        "Chaque joueur choisit une carte d'affinité, de n'importe quelle "
        "affinité." + chr(10) +
        "Il en ajoute le nombre maximum d'exemplaires à son deck pour le reste "
        "de la campagne.",
    "Setup: each player flips their S.H.I.E.L.D. Tech upgrade to its Enhanced "
    "side.":
        "Mise en place : chaque joueur retourne son amélioration Techno du "
        "SHIELD sur sa face Améliorée.",
    "Setup: the first player searches the encounter deck and discard pile for "
    "a scenario side scheme and reveals it.\n"
    "Place 1 threat per player on it. Shuffle.":
        "Mise en place : le premier joueur cherche dans le deck Rencontre et la "
        "pile de défausse une manigance annexe du scénario et la révèle.\n"
        "Placez 1 menace par joueur dessus. Mélangez.",
    "Each player chooses one card from their deck and records it.":
        "Chaque joueur choisit une carte de son deck et l'enregistre.",
    "Setup: each player searches their deck and discard pile for their "
    "recorded card and adds it to hand. Shuffle.":
        "Mise en place : chaque joueur cherche dans son deck et sa pile de "
        "défausse la carte qu'il a enregistrée et l'ajoute à sa main. Mélangez.",
    "Setup: each player may search their collection for a Helicarrier support "
    "and put it into play.":
        "Mise en place : chaque joueur peut chercher un soutien Héliporteur "
        "dans sa collection et le mettre en jeu.",
    "Setup: each player may search their collection for a Symbiote Suit "
    "upgrade and put it into play.":
        "Mise en place : chaque joueur peut chercher une amélioration Costume "
        "de Symbiote dans sa collection et la mettre en jeu.",
    "Setup: deal 1 facedown encounter card to each player.":
        "Mise en place : attribuez 1 carte Rencontre face cachée à chaque joueur.",

    # The scenarios.
    "Sandman": "L'Homme-Sable",
    "Venom": "Venom",
    "Mysterio": "Mysterio",
    "The Sinister Six": "Les Sinistres Six",
    "Venom Goblin": "Le Bouffon Venom",

    # What every scenario sets up.
    "Put Public Outcry into play, Standard Mode side up.":
        "Mettez en jeu Indignation Populaire, face Mode Standard visible.",
    "Put Public Outcry into play, Expert Mode side up.":
        "Mettez en jeu Indignation Populaire, face Mode Expert visible.",
    "Put Public Outcry into play.": "Mettez en jeu Indignation Populaire.",
    "Shuffle these into the encounter deck.":
        "Mélangez ces cartes dans le deck Rencontre.",
    "Shuffle this Community Service side scheme into the encounter deck.":
        "Mélangez cette manigance annexe Intérêt Général dans le deck Rencontre.",
    "Put the Venom ally into play under the first player's control.":
        "Mettez en jeu l'allié Venom sous le contrôle du premier joueur.",
    "Expert Campaign Only: place 2 additional sand counters on City Streets and resolve "
    "Surging Sands.":
        "Campagne Experte Uniquement : placez 2 jetons Sable supplémentaires sur "
        "Rues de la Ville et "
        "résolvez Afflux de Sable.",
    "Place this much threat on Light at the End.":
        "Placez autant de menace sur Lumière au Bout du Tunnel.",
    "Shuffle the Sinister Assault minion matching each villain left standing "
    "into the encounter deck.":
        "Mélangez dans le deck Rencontre le sbire Assaut Sinistre correspondant "
        "à chaque méchant encore en jeu.",

    # The questions after a scenario.
    "How many victory points are in the victory display?":
        "Combien de points de victoire y a-t-il dans la pile de victoire ?",
    "No minions in play?": "Aucun sbire en jeu ?",
    "No side schemes in play?": "Aucune manigance annexe en jeu ?",
    "No threat on the main scheme?": "Aucune menace sur la manigance principale ?",
    "No acceleration tokens in play?":
        "Aucun pion Accélération en jeu ?",
    "No defeated identities?": "Aucune identité vaincue ?",
    "Is the Community Service side scheme in the victory display?":
        "La manigance annexe Intérêt Général est-elle dans la pile de victoire ?",
    "Remaining hit points": "Points de vie restants",
    "How many Illusion cards are in all player decks?":
        "Combien de cartes Illusion y a-t-il dans les decks des joueurs ?",
    "Which villains were still in play?": "Quels méchants étaient encore en jeu ?",

    # The story.
    "Sandman fills the streets with a tidal wall of dust, and the people "
    "caught in it are running out of time.":
        "L'Homme-Sable noie les rues sous un mur de poussière, et ceux qui sont "
        "pris dedans n'ont plus beaucoup de temps.",
    "Venom comes at you blind with rage. The bell tower across the rooftops "
    "might be the only thing that slows him down.":
        "Venom fonce sur vous, aveuglé par la rage. Le clocher de l'autre côté "
        "des toits est peut-être la seule chose qui puisse le ralentir.",
    "Deeper into Oscorp, a maze of mirrors turns your own fears back on you "
    "until you cannot tell them from the room.":
        "Plus profond dans Oscorp, un labyrinthe de miroirs vous renvoie vos "
        "propres peurs jusqu'à ce que vous ne les distinguiez plus de la pièce.",
    "The smoke clears on a silo, and all six of them step out of the dark at "
    "once.":
        "La fumée se dissipe sur un silo, et tous les six sortent de l'ombre en "
        "même temps.",
    "Osborn has bound the symbiote to his will, and he is over the city now, "
    "making an army out of everyone below.":
        "Osborn a plié le symbiote à sa volonté, et il survole la ville, en "
        "train de faire une armée de tous ceux qui sont en dessous.",
}


def t(en):
    """The string in both languages, French looked up by its English.

    A string with no French entry keeps the English, and `missing_french()`
    below reports it rather than letting it pass unnoticed.
    """
    return {"en": en, "fr": FR.get(en, en)}


def at_least(node):
    """True once the reputation track has reached this node."""
    return {"counter": "reputation", "atLeast": node}


def unmarked(node, flag):
    """A once-only box: its node is reached and the box has not fired yet.

    Keyed by the box rather than by the node. Node 1 carries two of them, and
    three separate nodes carry an Osborn Tech draw.
    """
    return {"all": [at_least(node), {"notFlag": flag}]}


# ---------------------------------------------------------------------------
# THE REPUTATION TRACK
#
# Node by node, as printed on the component. An earlier pass placed these by
# measuring where each box sat beside the track, and got five of the fourteen
# wrong — including two the measurements looked confident about. Positions are
# not a source; the component is.
#
# `flag` names a box that resolves once, when its node is first marked. The
# rest are "Setup:" boxes, which apply at the start of every later scenario for
# as long as the node stays marked.
# ---------------------------------------------------------------------------

# node, flag (None for a recurring Setup box), text
NODES = [
    (1, "osborn1", OSBORN_DRAW),
    (1, None,
     "Setup: shuffle every recorded Osborn Tech attachment into the encounter "
     "deck."),
    (1, "shield1", SHIELD_DEAL),

    (5, None, "Setup: place 1 threat per player on the main scheme."),
    (5, None, "Each player may take 1 additional mulligan during step 13 of setup."),

    (9, None,
     "Setup: in player order, each player puts a minion from the encounter deck "
     "or discard pile into play engaged with them. Shuffle.\n"
     "Any player who does not takes 1 facedown encounter card."),
    (9, "aspect9",
     "Each player adds the maximum copies of one aspect card of their choice, "
     "from any aspect, to their deck for the rest of the campaign."),

    (13, "osborn13", OSBORN_DRAW),
    (13, None,
     "Setup: each player flips their S.H.I.E.L.D. Tech upgrade to its Enhanced "
     "side."),

    (17, None,
     "Setup: the first player searches the encounter deck and discard pile for "
     "a scenario side scheme and reveals it.\n"
     "Place 1 threat per player on it. Shuffle."),
    (17, "planning17",
     "Each player chooses one card from their deck and records it."),
    (17, None,
     "Setup: each player searches their deck and discard pile for their "
     "recorded card and adds it to hand. Shuffle."),

    (21, "osborn21", OSBORN_DRAW),
    (21, None,
     "Setup: each player may search their collection for a Helicarrier support "
     "and put it into play."),

    (25, None,
     "Setup: each player may search their collection for a Symbiote Suit "
     "upgrade and put it into play."),
    (25, None, "Setup: deal 1 facedown encounter card to each player."),
]

# Which card list a once-only box records into, when it records anything.
RECORDS = {
    "shield1": "shieldTech",
    "aspect9": "aspectAdvantage",
    "planning17": "planningAhead",
}


def osborn_draw(node, flag):
    """The app draws the attachment, because the rules say "at random".

    Recorded by [addDrawnCard], and struck from the pool by `excluding`, so the
    three marked nodes cannot turn up the same attachment twice.
    """
    return {
        "text": t("An Osborn Tech attachment is drawn and recorded."),
        "when": unmarked(node, flag),
        "draw": {"id": flag, "from": OSBORN_TECH, "excluding": "osbornTech"},
        # Named here as well, so the shuffle step at node 1 can be followed
        # without going back through the campaign log.
        "showCardList": "osbornTech",
        "action": {
            "id": "record_" + flag,
            "label": t("Record it"),
            "effects": [
                {"op": "addDrawnCard", "cardList": "osbornTech", "from": flag},
                {"op": "setFlag", "flag": flag, "boolValue": True},
            ],
        },
    }


def shield_deal(node, flag):
    """Three upgrades dealt to each player, who keeps one.

    A per-hero draw, because the rules deal to each player separately and a
    table of three makes three separate decisions. Keeping one returns the
    others, which is what an `offer` does: the kept card replaces the offer and
    the rest were never struck from the pool.
    """
    return {
        "text": t(
            "Three S.H.I.E.L.D. Tech upgrades are dealt to each player. Keep "
            "one for the rest of the campaign."
        ),
        "when": unmarked(node, flag),
        "draw": {"id": flag, "from": SHIELD_TECH, "offer": 3, "perHero": True},
        "action": {
            "id": "record_" + flag,
            "label": t("Record them"),
            "effects": [
                {"op": "addDrawnCard", "cardList": "shieldTech", "from": flag},
                {"op": "setFlag", "flag": flag, "boolValue": True},
            ],
        },
    }


def reputation_setup():
    """Every box whose node the track has reached, in printed order.

    Node by node, and within a node in the order the component lists them, so a
    table can follow along on the track itself.
    """
    steps = []
    for node, flag, text in NODES:
        if text is OSBORN_DRAW:
            steps.append(osborn_draw(node, flag))
            continue
        if text is SHIELD_DEAL:
            steps.append(shield_deal(node, flag))
            continue

        step = {"text": t(text)}
        if flag is None:
            step["when"] = at_least(node)
        else:
            step["when"] = unmarked(node, flag)
            step["action"] = {
                "id": "done_" + flag,
                "label": t("Done"),
                "effects": [{"op": "setFlag", "flag": flag, "boolValue": True}],
            }
        recorded = RECORDS.get(flag)
        if recorded:
            step["showCardList"] = recorded
        steps.append(step)
    return steps


# ---------------------------------------------------------------------------
# THE END OF A SCENARIO
# ---------------------------------------------------------------------------

def reputation_prompts():
    """The six conditions on the printed track, asked once per scenario."""
    return [
        {"id": "vp", "type": "number",
         "label": t("How many victory points are in the victory display?")},
        {"id": "noMinions", "type": "boolean",
         "label": t("No minions in play?")},
        {"id": "noSideSchemes", "type": "boolean",
         "label": t("No side schemes in play?")},
        {"id": "noThreat", "type": "boolean",
         "label": t("No threat on the main scheme?")},
        {"id": "fewAcceleration", "type": "boolean",
         "label": t("No acceleration tokens in play?")},
        {"id": "noDefeated", "type": "boolean",
         "label": t("No defeated identities?")},
    ]


def reputation_effects():
    """The app does the adding, so nobody totals the column by hand."""
    effects = [{"op": "addCounter", "counter": "reputation", "from": "vp"}]
    for answer in ("noMinions", "noSideSchemes", "noThreat", "fewAcceleration",
                   "noDefeated"):
        effects.append({"op": "addCounter", "counter": "reputation", "value": 1,
                        "when": {"answer": answer}})
    return effects


def service_prompt(draw_id):
    """Whether the drawn side scheme was seen through, not which one it was.

    The app dealt it, so asking which would be asking the player to read back
    something already on the screen.
    """
    return {
        "id": "serviceDone", "type": "boolean",
        "label": t("Is the Community Service side scheme in the victory display?"),
    }


def hp_prompt():
    return {
        "id": "hpPerHero", "type": "perHeroNumber",
        "label": t("Remaining hit points"),
        "when": {"difficulty": "expert"},
    }


def victory(scenario_id, draw_id, goto, extra_prompts=(), extra_effects=()):
    """The victory steps every scenario shares, plus whatever it adds."""
    prompts = reputation_prompts() + [service_prompt(draw_id)]
    prompts += list(extra_prompts)
    prompts.append(hp_prompt())

    effects = reputation_effects() + [
        {"op": "addDrawnCard", "cardList": "communityService", "from": draw_id,
         "when": {"answer": "serviceDone"}},
        {"op": "setHeroCounter", "counter": "hp", "from": "hpPerHero",
         "when": {"difficulty": "expert"}},
    ]
    effects += list(extra_effects)

    return {"prompts": prompts, "effects": effects,
            "next": [{"end": True} if goto is None else {"goto": goto}]}


def common_setup(scenario_id, expert_outcry=False, ally=False, snitches=False):
    """The campaign instructions shared by every scenario."""
    draw_id = "service_" + scenario_id
    steps = []

    if ally:
        steps.append({"text": t("Put the Venom ally into play under the first "
                                "player's control."),
                      "cards": [VENOM_ALLY]})

    if expert_outcry:
        # Scenario 1 is the only one that names a side of the environment.
        steps.append({"text": t("Put Public Outcry into play, Standard Mode side up."),
                      "cards": [PUBLIC_OUTCRY_STANDARD],
                      "when": {"difficulty": "standard"}})
        steps.append({"text": t("Put Public Outcry into play, Expert Mode side up."),
                      "cards": [PUBLIC_OUTCRY_EXPERT],
                      "when": {"difficulty": "expert"}})
    else:
        steps.append({"text": t("Put Public Outcry into play."),
                      "cards": [PUBLIC_OUTCRY_STANDARD]})

    shuffled = [SMEAR_CAMPAIGN] + ([SNITCHES] if snitches else [])
    steps.append({"text": t("Shuffle these into the encounter deck."),
                  "cards": shuffled})

    # Drawn by the app, and never one already seen through.
    steps.append({
        "text": t("Shuffle this Community Service side scheme into the "
                  "encounter deck."),
        "draw": {"id": draw_id, "from": COMMUNITY_SERVICE,
                 "excluding": "communityService"},
    })
    return draw_id, steps


def scenario(scenario_id, name, villain, main_scheme, sets, goto,
             expert_outcry=False, ally=False, snitches=False,
             extra_setup=(), extra_prompts=(), extra_effects=(), flavour=None):
    draw_id, steps = common_setup(scenario_id, expert_outcry, ally, snitches)
    steps += list(extra_setup)
    steps.append({"include": "reputation"})

    if len(villain) == 6:
        villain_deck = {"standard": villain, "expert": villain}
    else:
        villain_deck = {"standard": [villain[0], villain[1]],
                        "expert": [villain[1], villain[2]]}

    entry = {
        "id": scenario_id,
        "name": t(name),
        "baseSetup": {
            "villainDeck": villain_deck,
            "mainScheme": main_scheme,
            "encounterSets": sets,
        },
        "campaignSetup": steps,
        "onVictory": victory(scenario_id, draw_id, goto, extra_prompts, extra_effects),
        # A lost scenario is played again; only the finale on Expert ends it.
        "onDefeat": {"next": [{"goto": scenario_id}]},
    }
    if flavour:
        entry["flavour"] = t(flavour)
    return entry


def build():
    scenarios = [
        scenario(
            "s1_sandman", "Sandman", SANDMAN, ["27064a"],
            ["sandman", "city_in_chaos", "down_to_earth", "standard"],
            goto="s2_venom", expert_outcry=True,
            flavour="Sandman fills the streets with a tidal wall of dust, and "
                    "the people caught in it are running out of time.",
            extra_setup=[{
                "text": t("Expert Campaign Only: place 2 additional sand counters on City "
                          "Streets and resolve Surging Sands."),
                "when": {"difficulty": "expert"},
            }],
        ),
        scenario(
            "s2_venom", "Venom", VENOM, ["27076a"],
            ["venom", "down_to_earth", "symbiotic_strength", "standard"],
            goto="s3_mysterio",
            flavour="Venom comes at you blind with rage. The bell tower across "
                    "the rooftops might be the only thing that slows him down.",
            extra_effects=[],
            extra_prompts=[],
        ),
        scenario(
            "s3_mysterio", "Mysterio", MYSTERIO, ["27087a", "27088a"],
            ["mysterio", "personal_nightmare", "whispers_of_paranoia", "standard"],
            goto="s4_sinister_six", ally=True, snitches=True,
            flavour="Deeper into Oscorp, a maze of mirrors turns your own fears "
                    "back on you until you cannot tell them from the room.",
            extra_prompts=[{
                "id": "illusions", "type": "number",
                "label": t("How many Illusion cards are in all player decks?"),
            }],
            extra_effects=[{
                "op": "setCounter", "counter": "wakingNightmare", "from": "illusions",
            }],
        ),
        scenario(
            "s4_sinister_six", "The Sinister Six", SINISTER_SIX,
            ["27100a", "27101a"],
            ["sinister_six", "guerrilla_tactics", "standard"],
            goto="s5_venom_goblin", ally=True, snitches=True,
            flavour="The smoke clears on a silo, and all six of them step out "
                    "of the dark at once.",
            extra_setup=[{
                "text": t("Place this much threat on Light at the End."),
                "cards": [LIGHT_AT_THE_END],
                "showCounter": "wakingNightmare",
            }],
            extra_prompts=[{
                "id": "standing", "type": "cardSelect",
                "label": t("Which villains were still in play?"),
                "cards": SINISTER_SIX,
            }],
            extra_effects=[{
                "op": "addCardsFromAnswer", "cardList": "lastOnesStanding",
                "from": "standing",
            }],
        ),
        scenario(
            "s5_venom_goblin", "Venom Goblin", VENOM_GOBLIN,
            ["27116a", "27117a", "27118a", "27119a"],
            ["venom_goblin", "symbiotic_strength", "goblin_gear", "standard"],
            goto=None,
            flavour="Osborn has bound the symbiote to his will, and he is over "
                    "the city now, making an army out of everyone below.",
            extra_setup=[{
                "text": t("Shuffle the Sinister Assault minion matching each "
                          "villain left standing into the encounter deck."),
                "cards": SINISTER_ASSAULT,
                "showCardList": "lastOnesStanding",
            }],
        ),
    ]

    # Losing the finale on Expert loses the campaign; on Standard it is played
    # again like any other scenario.
    scenarios[-1]["onDefeat"] = {"next": [
        {"end": True, "when": {"difficulty": "expert"}},
        {"goto": "s5_venom_goblin"},
    ]}

    return {
        "id": "sm",
        "schemaVersion": 1,
        "name": t("Sinister Motives"),
        "packCode": "sm",

        "notice": t(
            "Unofficial, reconstructed for the app: the rulebook and cards from "
            "the Sinister Motives box are still needed to play."
            "\n\n"
            "The app keeps the reputation track for you. It asks the six printed "
            "conditions after each scenario, does the adding, and shows only the "
            "boxes the track has reached. It also deals what the rules deal at "
            "random: the Community Service side scheme, the Osborn Tech "
            "attachments, and each player's three S.H.I.E.L.D. Tech upgrades."
        ),
        "difficulties": ["standard", "expert"],
        "counters": [
            {"id": "reputation", "scope": "campaign", "initial": 0},
            # The printed track holds Illusion cards from the Mysterio scenario
            # until the Sinister Six scenario turns them into threat.
            {"id": "wakingNightmare", "scope": "campaign", "initial": 0},
            {
                "id": "hp",
                "scope": "hero",
                "initial": 0,
                "maxFrom": "heroCard.health",
                "activeWhen": {"difficulty": "expert"},
            },
        ],
        # One flag per once-only node, so a box that has fired stops offering.
        # One per box that fires once, so a box is offered until it is taken
        # and never again.
        "flagSets": [
            {"id": flag} for _, flag, _ in NODES if flag is not None
        ],
        "cardLists": [
            {"id": "communityService", "scope": "campaign"},
            {"id": "osbornTech", "scope": "campaign"},
            {"id": "lastOnesStanding", "scope": "campaign"},
            {"id": "shieldTech", "scope": "campaign"},
            {"id": "aspectAdvantage", "scope": "campaign"},
            {"id": "planningAhead", "scope": "campaign"},
        ],
        "setupFragments": {"reputation": reputation_setup()},
        "startScenarioId": "s1_sandman",
        "scenarios": scenarios,
    }


def missing_french(data):
    """Every string that came out with its English standing in for French."""
    missing = []

    def walk(node):
        if isinstance(node, dict):
            if ("en" in node and node.get("fr") == node["en"]
                    and node["en"] not in FR):
                missing.append(node["en"])
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(data)
    return sorted(set(missing))


if __name__ == "__main__":
    path = "app/src/main/assets/campaigns/sm.json"
    data = build()
    text = json.dumps(data, ensure_ascii=False, indent=1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text + "\n")
    print("wrote %s, %d bytes, %d scenarios"
          % (path, len(text), len(data["scenarios"])))
    untranslated = missing_french(data)
    if untranslated:
        print("  %d string(s) still English in French:" % len(untranslated))
        for line in untranslated:
            print("    |", line.replace("\n", " / ")[:96])
