# -*- coding: utf-8 -*-
"""Builds the Agents of S.H.I.E.L.D. campaign template.

Five scenarios in order, Black Widow through Baron Zemo, all five to be won,
and a loss costs nothing.

THE MOLE, WHICH IS WHAT THIS CAMPAIGN IS ABOUT

Nine evidence cards: three means, three motives, three opportunities. One of
each is sealed in the A.I.M. envelope and names the mole. The other six go in
the S.H.I.E.L.D. envelope, and the players win them one at a time by ending a
scenario with a board member who has no secrets left.

That makes the deduction pure elimination, and the app does it. Every evidence
card gained is recorded, and each category shows only the cards still unseen.
Gain two of the three means and the third one is on screen by itself: that is
the mole's means, and nobody had to work it out.

What the app does NOT do is name the mole. Which combination belongs to which
board member is a grid of icons printed in the campaign log, and it is not in
the rulebook as text. Guessing it would be worse than leaving it out, because a
wrong grid sends the table to a wrong accusation. The app narrows the three
cards and the printed log turns them into a name.

WHAT ELSE CARRIES

Secrets sit on each of the three board members and carry from one scenario to
the next, which is the campaign's other spine: four secrets (three in expert)
and that board member turns for good. Each is a counter, recorded at the end of
a scenario and shown at the start of the next.

Card codes are MarvelCDB codes for pack 'aos'.
"""
import io
import json

NL = chr(10)

# --- villains ---------------------------------------------------------------
BLACK_WIDOW = ["50064", "50065", "50066"]          # I, II, III
BATROC = ["50086a", "50086b"]                       # A, B
MODOK = ["50103a", "50103b"]
CITIZEN_V = ["50129a", "50129b"]
# Zemo has two stages per difficulty: masked then unmasked.
ZEMO_STANDARD = ["50165a", "50165b"]                # A1, A2
ZEMO_EXPERT = ["50166a", "50166b"]                  # B1, B2

# --- the executive board ----------------------------------------------------
BOARD = [
    ("50181a", "secretsMedical", "Chief Medical Officer"),
    ("50182a", "secretsSurveillance", "Chief Surveillance Officer"),
    ("50183a", "secretsTactical", "Chief Tactical Officer"),
]

# --- evidence, by category --------------------------------------------------
MEANS = [("50185", "Medical Records"), ("50186", "Wiretap"),
         ("50187", "Security Scanner")]
MOTIVE = [("50188", "Money"), ("50189", "Blackmail"), ("50190", "Ideology")]
OPPORTUNITY = [("50191", "Security Clearance"), ("50192", "Travel"),
               ("50193", "Authority")]

ALERT_LEVEL = "50090a"
ADAPTOIDS = ["50109", "50110", "50111", "50112"]

# One Elite Thunderbolt minion per qualifying modular set.
THUNDERBOLTS = ["50139", "50143", "50148", "50152", "50156", "50161"]


FR = {
    "Unofficial, reconstructed for the app: the rulebook and cards from the Agents of S.H.I.E.L.D. box are still needed to play.\n\nThe app keeps the campaign log for you. Secrets on each of the three board members, carried from one scenario to the next, the evidence you have gathered, and the records each scenario passes to the one after it.\n\nIt also does the deduction. Every evidence card you find is one the mole does not have, so the app shows only what is still unaccounted for. When a category is down to one card, that is the mole's. Which board member those three point to is the grid printed in your campaign log, and you read the name off that.\n\nThe cards of this box are not in the French card database yet, so card names here stay in English rather than being invented.\n\nExpert is the expert campaign: the harder villain stages, board members turning at three secrets instead of four, and hit points that carry from one scenario to the next.":
        "Non officiel, reconstitué pour l'application : le livret et les cartes de la boîte Agents du S.H.I.E.L.D. restent nécessaires pour jouer.\n\nL'application tient le journal de campagne pour vous. Les secrets sur chacun des trois Board Members, reportés d'un scénario au suivant, les preuves que vous avez réunies, et ce que chaque scénario transmet au suivant.\n\nElle fait aussi la déduction. Chaque carte Preuve trouvée est une carte que la taupe n'a pas : l'application n'affiche donc que ce qui manque encore. Quand une catégorie se réduit à une seule carte, c'est celle de la taupe. Le Board Member vers lequel ces trois-là pointent se lit sur la grille imprimée dans votre journal de campagne.\n\nLes cartes de cette boîte ne sont pas encore dans la base de données française : les noms de cartes restent donc en anglais plutôt que d'être inventés.\n\nExpert correspond à la campagne experte : les stades de méchant les plus durs, les Board Members qui basculent à trois secrets au lieu de quatre, et des points de vie qui se reportent d'un scénario au suivant.",
    'Each player may place 1 secret counter on a Board Member to heal their identity by its REC.\nA player defeated last scenario rejoins this way.':
        'Chaque joueur peut placer 1 jeton Secret sur un Board Member pour soigner son identité de sa RÉC.\nUn joueur vaincu au scénario précédent revient ainsi.',
    'A board member reaching 4 secrets (3 in expert) flips to its attachment side, and stays that way for the rest of the campaign.':
        'Un Board Member qui atteint 4 secrets (3 en expert) se retourne sur sa face attachement, et y reste pour tout le reste de la campagne.',
    'A player defeated last scenario rejoins this way.':
        'Un joueur vaincu au scénario précédent revient ainsi.',
    'A wrong accusation does not lose the scenario, it only makes it harder. All three board members turning does lose it.':
        'Une accusation erronée ne fait pas perdre le scénario, elle le rend seulement plus dur. Les trois Board Members retournés, si.',
    'Add 2 modular sets, each holding an Elite Thunderbolt minion.':
        "Ajoutez 2 sets modulaires, contenant chacun un sbire Thunderbolt d'Élite.",
    'After mulligans, resolve the Setup ability of each evidence card you have earned.':
        'Après les mulligans, résolvez la capacité Mise en place de chaque carte Preuve que vous avez obtenue.',
    'Agents of S.H.I.E.L.D.':
        'Agents du S.H.I.E.L.D.',
    'Baron Zemo':
        'Baron Zemo',
    'Batroc':
        'Batroc',
    'Black Widow':
        'Black Widow',
    'Chief Medical Officer':
        'Chief Medical Officer',
    'Chief Surveillance Officer':
        'Chief Surveillance Officer',
    'Chief Tactical Officer':
        'Chief Tactical Officer',
    'Citizen V is Helmut Zemo, the Board knew, and the only way out is to name the mole before Zemo turns all three of them.':
        "Citizen V est Helmut Zemo, le Board le savait, et la seule issue est d'identifier la taupe avant que Zemo ne les retourne tous les trois.",
    'Did a Board Member end with no secrets? If so, take one evidence card from the S.H.I.E.L.D. envelope and record it here.':
        "Un Board Member a-t-il fini sans aucun secret ? Si oui, prenez une carte Preuve dans l'enveloppe S.H.I.E.L.D. et notez-la ici.",
    'Each player may place 1 secret counter on a Board Member to heal their identity by its REC.':
        'Chaque joueur peut placer 1 jeton Secret sur un Board Member pour soigner son identité de sa RÉC.',
    'Expert is the expert campaign: the harder villain stages, board members turning at three secrets instead of four, and hit points that carry from one scenario to the next.':
        "Expert correspond à la campagne experte : les stades de méchant les plus durs, les Board Members qui basculent à trois secrets au lieu de quatre, et des points de vie qui se reportent d'un scénario au suivant.",
    'How many Rescued Captive allies are in play?':
        "Combien d'alliés Rescued Captive sont en jeu ?",
    'How many minions and side schemes are in play in total?':
        'Combien y a-t-il de sbires et de manigances annexes en jeu au total ?',
    'How many victory points are in the victory display?':
        'Combien de points de victoire y a-t-il dans la zone de victoire ?',
    "It also does the deduction. Every evidence card you find is one the mole does not have, so the app shows only what is still unaccounted for. When a category is down to one card, that is the mole's. Which board member those three point to is the grid printed in your campaign log, and you read the name off that.":
        "Elle fait aussi la déduction. Chaque carte Preuve trouvée est une carte que la taupe n'a pas : l'application n'affiche donc que ce qui manque encore. Quand une catégorie se réduit à une seule carte, c'est celle de la taupe. Le Board Member vers lequel ces trois-là pointent se lit sur la grille imprimée dans votre journal de campagne.",
    'Keep both envelopes within reach.':
        'Gardez les deux enveloppes à portée de main.',
    'M.O.D.O.K.':
        'M.O.D.O.K.',
    'Means: Medical Records':
        'Moyen : Medical Records',
    'Means: Security Scanner':
        'Moyen : Security Scanner',
    'Means: Wiretap':
        'Moyen : Wiretap',
    'Motive: Blackmail':
        'Mobile : Blackmail',
    'Motive: Ideology':
        'Mobile : Ideology',
    'Motive: Money':
        'Mobile : Money',
    'Opportunity: Authority':
        'Occasion : Authority',
    'Opportunity: Security Clearance':
        'Occasion : Security Clearance',
    'Opportunity: Travel':
        'Occasion : Travel',
    'Place 3 extra lock counters on the top Holding Cell, then take off one for each Rescued Captive you got out last scenario.':
        'Placez 3 jetons Verrou supplémentaires sur la Holding Cell du dessus, puis retirez-en un par Rescued Captive libéré au scénario précédent.',
    'Place this much threat on the Alert Level environment, from the minions and side schemes left standing last scenario.':
        "Placez autant de menace sur l'environnement Alert Level, d'après les sbires et manigances annexes restés en jeu au scénario précédent.",
    'Prepare the evidence and the two envelopes, as the rulebook sets out.':
        "Préparez les preuves et les deux enveloppes, comme l'indique le livret.",
    'Put the three Board Member environments into play, with 2 secret counters on each.':
        'Mettez en jeu les trois environnements Board Member, avec 2 jetons Secret sur chacun.',
    'Put the three Board Member environments into play, with the secrets they carried out of the last scenario.':
        "Mettez en jeu les trois environnements Board Member, avec les secrets qu'ils ont emportés du scénario précédent.",
    'Put these Adaptoid environments into play, and shuffle the Adaptoid minions into the encounter deck.':
        'Mettez ces environnements Adaptoid en jeu, et mélangez les sbires Adaptoid dans le deck Rencontre.',
    'Read those three off the combinations in your campaign log to name the mole.':
        'Reportez ces trois-là sur les combinaisons de votre journal de campagne pour identifier la taupe.',
    'Remaining hit points':
        'Points de vie restants',
    "Sarah Garza's phone leads to a disused stretch of subway tunnel, an A.I.M. base, and Yelena Belova waiting in it.":
        "Le téléphone de Sarah Garza mène à un tronçon de métro désaffecté, à une base d'A.I.M., et à Yelena Belova qui y attend.",
    'Secret counters on the Chief Medical Officer':
        'Jetons Secret sur le Chief Medical Officer',
    'Secret counters on the Chief Surveillance Officer':
        'Jetons Secret sur le Chief Surveillance Officer',
    'Secret counters on the Chief Tactical Officer':
        'Jetons Secret sur le Chief Tactical Officer',
    "Set each player's hit points to what they had left at the end of the last scenario.":
        "Fixez les points de vie de chaque joueur à ce qu'il lui restait à la fin du scénario précédent.",
    'Shuffle the three copies of A.I.M. Interference into the encounter deck.':
        "Mélangez les trois exemplaires d'A.I.M. Interference dans le deck Rencontre.",
    "Shuffle these surviving Thunderbolts and their sets into the encounter deck, except Jolt's.":
        'Mélangez ces Thunderbolts survivants et leurs sets dans le deck Rencontre, sauf celui de Jolt.',
    'The app keeps the campaign log for you. Secrets on each of the three board members, carried from one scenario to the next, the evidence you have gathered, and the records each scenario passes to the one after it.':
        "L'application tient le journal de campagne pour vous. Les secrets sur chacun des trois Board Members, reportés d'un scénario au suivant, les preuves que vous avez réunies, et ce que chaque scénario transmet au suivant.",
    'The cards of this box are not in the French card database yet, so card names here stay in English rather than being invented.':
        "Les cartes de cette boîte ne sont pas encore dans la base de données française : les noms de cartes restent donc en anglais plutôt que d'être inventés.",
    'The heroes are arrested and convicted.':
        'Les héros sont arrêtés et condamnés.',
    "The kidnapped are leaving the country through the A.I.M. Island embassy, and you are going in without the Board's blessing.":
        "Les personnes enlevées quittent le pays par l'ambassade d'A.I.M. Island, et vous y entrez sans l'aval du Board.",
    'The missing were shipped to A.I.M. Island to be copied. A member of the Board is in on it, and you are going anyway.':
        'Les disparus ont été envoyés sur A.I.M. Island pour être copiés. Un membre du Board est dans le coup, et vous y allez quand même.',
    "The mole's evidence is whatever you have not found. These are still unaccounted for.":
        "Les preuves de la taupe sont celles que vous n'avez pas trouvées. Voici ce qui manque encore à l'appel.",
    'Thunderbolts':
        'Thunderbolts',
    'Unofficial, reconstructed for the app: the rulebook and cards from the Agents of S.H.I.E.L.D. box are still needed to play.':
        "Non officiel, reconstitué pour l'application : le livret et les cartes de la boîte Agents du S.H.I.E.L.D. restent nécessaires pour jouer.",
    'Which Adaptoid environments are in play?':
        'Quels environnements Adaptoid sont en jeu ?',
    'Which Thunderbolt minions are still in play?':
        'Quels sbires Thunderbolt sont encore en jeu ?',
    'You land back in Washington and the Thunderbolts are waiting at the airport with a warrant for your arrest.':
        "Vous atterrissez à Washington et les Thunderbolts vous attendent à l'aéroport avec un mandat d'arrêt.",
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

def board_setup(first=False):
    """The three board members, with the secrets they carry.

    Secrets persist across the whole campaign, so every scenario after the
    first puts back exactly what the last one ended with. The counter is shown
    rather than described, because "as many as last time" is the thing nobody
    remembers.
    """
    if first:
        return [{
            "text": t("Put the three Board Member environments into play, "
                      "with 2 secret counters on each."),
            "cards": [code for code, _counter, _name in BOARD],
        }]

    steps = [{
        "text": t("Put the three Board Member environments into play, with "
                  "the secrets they carried out of the last scenario."),
        "cards": [code for code, _counter, _name in BOARD],
    }]
    for code, counter, name in BOARD:
        steps.append({
            "text": t(name),
            "showCounter": counter,
        })
    steps.append({
        "text": t("A board member reaching 4 secrets (3 in expert) flips to "
                  "its attachment side, and stays that way for the rest of "
                  "the campaign."),
    })
    return steps


def interference():
    return [{
        "text": t("Shuffle the three copies of A.I.M. Interference into the "
                  "encounter deck."),
        "cards": ["50184a", "50184b", "50184c"],
    }]


def evidence_setup():
    """The evidence already won, whose Setup abilities are resolved now."""
    return [{
        "text": t("After mulligans, resolve the Setup ability of each "
                  "evidence card you have earned."),
        "showCardList": "evidenceGained",
        "when": {"cardList": "evidenceGained", "minSize": 1},
    }]


def deduction():
    """What the evidence gathered so far leaves standing.

    One step per card, each shown only while that card has not been gained.
    Gaining a card removes it from the list, so a category down to its last
    entry is the answer, worked out by the app rather than by crossing out a
    grid.
    """
    steps = [{
        "text": t("The mole's evidence is whatever you have not found. These "
                  "are still unaccounted for."),
        "when": {"cardList": "evidenceGained", "minSize": 1},
    }]
    for label, group in (("Means", MEANS), ("Motive", MOTIVE),
                         ("Opportunity", OPPORTUNITY)):
        for code, name in group:
            steps.append({
                "text": t(label + ": " + name),
                "cards": [code],
                "when": {
                    "all": [
                        {"cardList": "evidenceGained", "notContains": code},
                        {"cardList": "evidenceGained", "minSize": 1},
                    ],
                },
            })
    steps.append({
        "text": t("Read those three off the combinations in your campaign log "
                  "to name the mole."),
        "when": {"cardList": "evidenceGained", "minSize": 4},
    })
    return steps


def expert_steps():
    return [
        {
            "text": t("Set each player's hit points to what they had left at "
                      "the end of the last scenario."),
            "when": {"difficulty": "expert"},
        },
        {
            "text": t("Each player may place 1 secret counter on a Board "
                      "Member to heal their identity by its REC." + NL +
                      "A player defeated last scenario rejoins this way."),
            "when": {"difficulty": "expert"},
        },
    ]


# ---------------------------------------------------------------------------
# THE END OF A SCENARIO
# ---------------------------------------------------------------------------

def victory(goto, extra_prompts=None, extra_effects=None):
    """Victory steps, in the rulebook's order.

    The evidence question is asked the way the rulebook asks it: did any board
    member finish with no secrets. The app cannot see the table, so it asks,
    then records which card was taken.
    """
    prompts = [
        {"id": "vp", "type": "number",
         "label": t("How many victory points are in the victory display?")},
    ]
    effects = []

    if extra_prompts:
        prompts.extend(extra_prompts)
    if extra_effects:
        effects.extend(extra_effects)

    # Secrets on each board member, which is what the next scenario puts back.
    for _code, counter, name in BOARD:
        prompts.append({
            "id": counter, "type": "number",
            "label": t("Secret counters on the " + name),
        })
        effects.append({"op": "setCounter", "counter": counter, "from": counter})

    prompts.append({
        "id": "evidence", "type": "cardSelect",
        "cards": [code for code, _n in MEANS + MOTIVE + OPPORTUNITY],
        "label": t("Did a Board Member end with no secrets? If so, take one "
                   "evidence card from the S.H.I.E.L.D. envelope and record "
                   "it here."),
    })
    effects.append({"op": "addCardsFromAnswer", "cardList": "evidenceGained",
                    "from": "evidence"})

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
    if not final:
        return {"next": [{"goto": scenario_id}]}
    return {
        "message": t("The heroes are arrested and convicted."),
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
            "id": "s1_black_widow",
            "name": t("Black Widow"),
            "flavour": t("Sarah Garza's phone leads to a disused stretch of "
                         "subway tunnel, an A.I.M. base, and Yelena Belova "
                         "waiting in it."),
            "baseSetup": {
                "villainDeck": {"standard": BLACK_WIDOW[:2],
                                "expert": BLACK_WIDOW[1:]},
                "mainScheme": ["50067a"],
                "encounterSets": ["black_widow_villain", "a.i.m._abduction",
                                  "a.i.m._science", "standard"],
            },
            "campaignSetup": (
                [{"text": t("Prepare the evidence and the two envelopes, "
                            "as the rulebook sets out.")}]
                + board_setup(first=True)
                + interference()
            ),
            "onVictory": victory(
                "s2_batroc",
                extra_prompts=[{
                    "id": "alertLevel", "type": "number",
                    "label": t("How many minions and side schemes are in play "
                               "in total?"),
                }],
                extra_effects=[{"op": "setCounter", "counter": "alertLevel",
                                "from": "alertLevel"}],
            ),
            "onDefeat": defeat("s1_black_widow"),
        },
        {
            "id": "s2_batroc",
            "name": t("Batroc"),
            "flavour": t("The kidnapped are leaving the country through the "
                         "A.I.M. Island embassy, and you are going in without "
                         "the Board's blessing."),
            "baseSetup": {
                "villainDeck": {"standard": BATROC[:1], "expert": BATROC[1:]},
                "mainScheme": ["50087a"],
                "encounterSets": ["batroc", "a.i.m._science",
                                  "batrocs_brigade", "standard"],
            },
            "campaignSetup": (
                [{
                    "text": t("Place this much threat on the Alert Level "
                              "environment, from the minions and side schemes "
                              "left standing last scenario."),
                    "cards": [ALERT_LEVEL],
                    "showCounter": "alertLevel",
                }]
                + board_setup()
                + interference()
                + evidence_setup()
                + deduction()
                + expert_steps()
            ),
            "onVictory": victory(
                "s3_modok",
                extra_prompts=[{
                    "id": "rescued", "type": "number",
                    "label": t("How many Rescued Captive allies are in play?"),
                }],
                extra_effects=[{"op": "setCounter", "counter": "rescuedCaptives",
                                "from": "rescued"}],
            ),
            "onDefeat": defeat("s2_batroc"),
        },
        {
            "id": "s3_modok",
            "name": t("M.O.D.O.K."),
            "flavour": t("The missing were shipped to A.I.M. Island to be "
                         "copied. A member of the Board is in on it, and you "
                         "are going anyway."),
            "baseSetup": {
                "villainDeck": {"standard": MODOK[:1], "expert": MODOK[1:]},
                "mainScheme": ["50104a"],
                "encounterSets": ["m.o.d.o.k.", "scientist_supreme", "standard"],
            },
            "campaignSetup": (
                [{
                    "text": t("Place 3 extra lock counters on the top Holding "
                              "Cell, then take off one for each Rescued "
                              "Captive you got out last scenario."),
                    "showCounter": "rescuedCaptives",
                }]
                + board_setup()
                + interference()
                + evidence_setup()
                + deduction()
                + expert_steps()
            ),
            "onVictory": victory(
                "s4_thunderbolts",
                extra_prompts=[{
                    "id": "adaptoids", "type": "cardSelect", "cards": ADAPTOIDS,
                    "label": t("Which Adaptoid environments are in play?"),
                }],
                extra_effects=[{"op": "addCardsFromAnswer",
                                "cardList": "adaptoids", "from": "adaptoids"}],
            ),
            "onDefeat": defeat("s3_modok"),
        },
        {
            "id": "s4_thunderbolts",
            "name": t("Thunderbolts"),
            "flavour": t("You land back in Washington and the Thunderbolts are "
                         "waiting at the airport with a warrant for your "
                         "arrest."),
            "baseSetup": {
                "villainDeck": {"standard": CITIZEN_V[:1], "expert": CITIZEN_V[1:]},
                "mainScheme": ["50130a"],
                "encounterSets": ["thunderbolts", "standard"],
            },
            "campaignSetup": (
                [{"text": t("Add 2 modular sets, each holding an Elite "
                            "Thunderbolt minion.")}]
                + board_setup()
                + interference()
                + evidence_setup()
                + deduction()
                + expert_steps()
            ),
            "onVictory": victory(
                "s5_baron_zemo",
                extra_prompts=[{
                    "id": "survivors", "type": "cardSelect",
                    "cards": THUNDERBOLTS,
                    "label": t("Which Thunderbolt minions are still in play?"),
                }],
                extra_effects=[{"op": "addCardsFromAnswer",
                                "cardList": "survivingThunderbolts",
                                "from": "survivors"}],
            ),
            "onDefeat": defeat("s4_thunderbolts"),
        },
        {
            "id": "s5_baron_zemo",
            "name": t("Baron Zemo"),
            "flavour": t("Citizen V is Helmut Zemo, the Board knew, and the "
                         "only way out is to name the mole before Zemo turns "
                         "all three of them."),
            "baseSetup": {
                "villainDeck": {"standard": ZEMO_STANDARD, "expert": ZEMO_EXPERT},
                "mainScheme": ["50167a"],
                "encounterSets": ["baron_zemo", "s.h.i.e.l.d._executive_board",
                                  "executive_board_evidence",
                                  "scientist_supreme", "s.h.i.e.l.d.",
                                  "standard"],
            },
            "campaignSetup": (
                [{
                    "text": t("Put these Adaptoid environments into play, and "
                              "shuffle the Adaptoid minions into the encounter "
                              "deck."),
                    "showCardList": "adaptoids",
                    "when": {"cardList": "adaptoids", "minSize": 1},
                }, {
                    "text": t("Shuffle these surviving Thunderbolts and their "
                              "sets into the encounter deck, except Jolt's."),
                    "showCardList": "survivingThunderbolts",
                    "when": {"cardList": "survivingThunderbolts", "minSize": 1},
                }, {
                    "text": t("Keep both envelopes within reach."),
                }]
                + board_setup()
                + evidence_setup()
                + deduction()
                + expert_steps()
                + [{
                    "text": t("A wrong accusation does not lose the scenario, "
                              "it only makes it harder. All three board "
                              "members turning does lose it."),
                }]
            ),
            "onVictory": victory(None),
            "onDefeat": defeat("s5_baron_zemo", final=True),
        },
    ]

    return {
        "id": "aos",
        "schemaVersion": 1,
        "name": t("Agents of S.H.I.E.L.D."),
        "packCode": "aos",
        "untested": True,
        "notice": t(
            "Unofficial, reconstructed for the app: the rulebook and cards "
            "from the Agents of S.H.I.E.L.D. box are still needed to play."
            + NL + NL +
            "The app keeps the campaign log for you. Secrets on each of the "
            "three board members, carried from one scenario to the next, the "
            "evidence you have gathered, and the records each scenario passes "
            "to the one after it."
            + NL + NL +
            "It also does the deduction. Every evidence card you find is one "
            "the mole does not have, so the app shows only what is still "
            "unaccounted for. When a category is down to one card, that is "
            "the mole's. Which board member those three point to is the grid "
            "printed in your campaign log, and you read the name off that."
            + NL + NL +
            "The cards of this box are not in the French card database yet, "
            "so card names here stay in English rather than being invented."
            + NL + NL +
            "Expert is the expert campaign: the harder villain stages, board "
            "members turning at three secrets instead of four, and hit points "
            "that carry from one scenario to the next."
        ),
        "difficulties": ["standard", "expert"],
        "counters": [
            {"id": "hp", "scope": "hero", "initial": 0,
             "maxFrom": "heroCard.health"},
            {"id": "secretsMedical", "scope": "campaign", "initial": 2},
            {"id": "secretsSurveillance", "scope": "campaign", "initial": 2},
            {"id": "secretsTactical", "scope": "campaign", "initial": 2},
            {"id": "alertLevel", "scope": "campaign", "initial": 0},
            {"id": "rescuedCaptives", "scope": "campaign", "initial": 0},
        ],
        "flagSets": [],
        "cardLists": [
            {"id": "evidenceGained", "scope": "campaign"},
            {"id": "adaptoids", "scope": "campaign"},
            {"id": "survivingThunderbolts", "scope": "campaign"},
        ],
        "startScenarioId": "s1_black_widow",
        "scenarios": scenarios,
    }


if __name__ == "__main__":
    data = build()
    missing = missing_french(data)
    if missing:
        print("Untranslated (%d):" % len(missing))
        for line in missing:
            print("   ", line)
    out = "app/src/main/assets/campaigns/aos.json"
    io.open(out, "w", encoding="utf-8", newline=NL).write(
        json.dumps(data, ensure_ascii=False, indent=2) + NL
    )
    print("wrote", out)
