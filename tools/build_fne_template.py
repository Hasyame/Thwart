# -*- coding: utf-8 -*-
"""Writes app/src/main/assets/campaigns/fne.json — the Peur de Rien campaign.

Fear No Evil has no scenario order. Before every game the villains push two
places whether or not the heroes go there; three pushes and a place is gone.
The table then plays any scenario still standing, and a subordinate villain is
drawn for it.

The app does the drawing. This is a companion, and where the rules say draw at
random it draws — the environments before the choice, the villain after it —
rather than asking the table what they drew.

No MarvelCDB codes: the encounter side of this pack is not published, so the
environments and villains are named from a local table (localCardNames),
French, off the box in the house. The card database wins wherever it has an
entry, so this drops away on its own the day those cards appear.

Generated rather than hand-written because the five interchangeable scenarios
share their whole victory bookkeeping, and five copies are five chances to
drift apart.
"""
import io
import json
import os

FR, EN = "fr", "en"


def text(fr, en=None):
    return {FR: fr, EN: en if en is not None else fr}


# id, French name, English name, pressure counter.
SCENARIOS = [
    ("s1_musee", "Cambriolage du Musée d'Art", "Art Museum Heist", "pressionMusee"),
    ("s2_poursuite", "La Poursuite", "The Getaway", "pressionPoursuite"),
    ("s3_racket", "Racket en Bande Organisée", "Protection Racket", "pressionRacket"),
    ("s4_raft", "L'Évasion du Raft", "The Raft Breakout", "pressionRaft"),
    ("s5_rotatives", "Arrêtez les Rotatives !", "Stop the Presses", "pressionRotatives"),
]

# Subordinate villains, as local pseudo-codes since the cards are not published.
VILLAINS = [
    ("fne_villain_hammerhead", "Hammerhead"),
    ("fne_villain_bullseye", "Bullseye"),
    ("fne_villain_electro", "Electro"),
    ("fne_villain_homme_pourpre", "L'Homme Pourpre"),
    ("fne_villain_mary_typhoide", "Mary Typhoïde"),
]

VILLAIN_CODES = [code for code, _ in VILLAINS]

# What a scenario suffers per tick already on it, applied from its own setup.
# Mechanics only — what to place and where, never the book's prose.
PRESSURE_SETUP = {
    "s1_musee": text(
        "Placez 1 menace par case cochée sur la manigance principale (2 en Expert).",
        "Place 1 threat per ticked box on the main scheme (2 in Expert).",
    ),
    "s2_poursuite": text(
        "Révélez le \"Camion-Citerne\" ; à 2 cases, 1 menace de plus dessus (2 en Expert).",
        "Reveal the Tanker Truck; at 2 boxes, 1 more threat on it (2 in Expert).",
    ),
    "s3_racket": text(
        "Placez 1 menace par case cochée sur chaque manigance principale (2 en Expert).",
        "Place 1 threat per ticked box on every main scheme (2 in Expert).",
    ),
    "s4_raft": text(
        "Carte Tenace à chaque sbire PRISONNIER ; à 2 cases, aussi un boost face cachée.",
        "Tough on each PRISONER minion; at 2 boxes, a facedown boost as well.",
    ),
    "s5_rotatives": text(
        "Retirez 1 jeton Endurance par case cochée de chaque soutien DAILY BUGLE.",
        "Remove 1 Use token per ticked box from each DAILY BUGLE support.",
    ),
}

# What each job puts on the table, from its own page: the main scheme deck and
# the encounter sets. The chosen villain's own set joins them every time.
def q(name):
    """A card title as it reads mid-sentence, in quotes."""
    return '"%s"' % name


def q_all(names):
    """A list of titles, each quoted, joined the way a deck list reads."""
    return ", ".join(q(name) for name in names)


# Main scheme title, a note that is not part of the title, and the encounter
# sets — each kept apart so the quotes land on the titles and nowhere else.
NOTES_EN = {"": "", " (une par joueur)": " (one per player)"}

SCENARIO_DECKS = {
    "s1_musee": (
        "Cambriolage du Musée d'Art",
        "",
        ["Cambriolage du Musée d'Art", "Policiers", "Le Hibou", "Standard"],
    ),
    "s2_poursuite": (
        "La Poursuite",
        "",
        ["La Poursuite", "Policiers", "Conduite", "Standard"],
    ),
    "s3_racket": (
        "Racket en Bande Organisée",
        " (une par joueur)",
        ["Racket en Bande Organisée", "Désastres", "Mafia des Survêtes", "Standard"],
    ),
    "s4_raft": (
        "L'Évasion du Raft",
        "",
        ["L'Évasion du Raft", "Le Hibou", "Tombstone", "Standard"],
    ),
    "s5_rotatives": (
        "Arrêtez les Rotatives !",
        "",
        ["Arrêtez les Rotatives !", "Tombstone", "Mafia des Survêtes", "Standard"],
    ),
}

# The piece of business each job runs beyond its decks.
SCENARIO_EXTRA_EN = {
    "s1_musee": "One of the four artworks, drawn by stage 1A, starts attached to the villain.",
    "s2_poursuite": "The \"En Tête / Roue Contre Roue\" attachment starts \"En Tête\" face up.",
    "s3_racket": "Each player takes a main scheme into their own play area.",
    "s4_raft": "The villain gets the \"Passe-Partout\" attachment.",
    "s5_rotatives": "Each player is dealt a random DAILY BUGLE support (3 Use tokens).",
}

SCENARIO_EXTRA = {
    "s1_musee": "Une des quatre œuvres d'art, tirée par le stade 1A, commence attachée au méchant.",
    "s2_poursuite": "L'attachement \"En Tête / Roue Contre Roue\" commence face \"En Tête\" visible.",
    "s3_racket": "Chaque joueur prend une manigance principale dans sa propre zone de jeu.",
    "s4_raft": "Le méchant reçoit l'attachement \"Passe-Partout\".",
    "s5_rotatives": "Chaque joueur reçoit au hasard un soutien DAILY BUGLE (3 jetons Endurance).",
}


# Two lines apiece, setting the scene. Written for the app in the register the
# other campaigns use — the book's own narration stays in the book.
SCENARIO_FLAVOUR_EN = {
    "s1_musee": (
        "A silent alarm at the Art Museum, and a security desk that has stopped answering. The thieves did not bother to cover their tracks: it looks as though they wanted to be noticed."
    ),
    "s2_poursuite": (
        "A yellow sports car running south through Hell's Kitchen, and the tunnel is not far off. Out of the city it will be gone for good — the road has to be cut."
    ),
    "s3_racket": (
        "Men with bats are taking a corner shop apart while the owner hides behind his own counter. They are calling it a late payment."
    ),
    "s4_raft": (
        "Someone walked through the gates of the Raft on a full access pass and is opening the cells one by one. The inmates you put there are coming for you."
    ),
    "s5_rotatives": (
        "The Daily Bugle offices have been stormed and the editor is somewhere inside, tied up. Behind the door, somebody is enjoying themselves."
    ),
    "s6_caid": (
        "The whole crime wave was staged, and the street blames you for it. Only one man could have run a campaign like that against you: Le Caïd."
    ),
}

SCENARIO_FLAVOUR = {
    "s1_musee": (
        "Une alarme silencieuse au Musée d'Art, et un service de sécurité qui ne "
        "répond plus. Les voleurs n'ont pas pris la peine de couvrir leurs traces : "
        "on dirait qu'ils voulaient être remarqués."
    ),
    "s2_poursuite": (
        "Une voiture de sport jaune file vers le sud à travers Hell's Kitchen, et le "
        "tunnel n'est plus très loin. Sortie de la ville, elle sera introuvable — il "
        "faut lui couper la route."
    ),
    "s3_racket": (
        "Des voyous armés de battes mettent une boutique de quartier en pièces pendant "
        "que le propriétaire se cache derrière son comptoir. Ils appellent ça des "
        "pénalités de retard."
    ),
    "s4_raft": (
        "Quelqu'un a franchi les grilles du Raft avec un passe d'accès intégral et "
        "ouvre les cellules une à une. Les détenus que vous avez mis là se retournent "
        "contre vous."
    ),
    "s5_rotatives": (
        "Les bureaux du Daily Bugle sont pris d'assaut et le rédacteur en chef est "
        "quelque part à l'intérieur, ligoté. Derrière la porte, quelqu'un s'amuse."
    ),
    "s6_caid": (
        "Toute cette vague de crimes était orchestrée, et la rue vous en tient pour "
        "responsables. Une seule personne pouvait monter une campagne pareille contre "
        "vous : Le Caïd."
    ),
}


# Notes on each subordinate, from the pages that introduce them. What the
# villain does at the table, in a line or two — not their card text.
VILLAIN_TIPS_EN = {
    "fne_villain_hammerhead": [
        "Hammerhead plays to stun: his forced response stuns anyone he damages.",
        "Tête la Première and the Sous-Chefs hit harder against a stunned hero.",
    ],
    "fne_villain_bullseye": [
        "Every one of his boost cards gains an extra boost icon.",
        "His encounter cards remove INDIVIDU allies and supports: lost for the campaign.",
    ],
    "fne_villain_electro": [
        "She spends the Charge tokens on Charge Électrique for extra boosts.",
        "Energy resources strip her charges, but open you to her treacheries.",
    ],
    "fne_villain_homme_pourpre": [
        "Low stats, but every card of his deals out a facedown encounter card.",
        "His INFLUENCÉ minions are Vulnerable: stun them or confuse them.",
    ],
    "fne_villain_mary_typhoide": [
        "She flips at the end of every villain phase, alternating her two faces.",
        "So neither her attack nor her scheme favours her two turns running.",
    ],
}

VILLAIN_TIPS = {
    "fne_villain_hammerhead": [
        "Hammerhead veut sonner : sa Réponse forcée sonne tout personnage qu'il blesse.",
        "Tête la Première et les Sous-Chefs frappent plus fort sur un héros sonné.",
    ],
    "fne_villain_bullseye": [
        "Toutes ses cartes de boost gagnent une icône de boost supplémentaire.",
        "Ses cartes Rencontre retirent alliés et soutiens INDIVIDU : perdus pour la campagne.",
    ],
    "fne_villain_electro": [
        "Elle dépense les jetons Charge de Charge Électrique pour des boosts en plus.",
        "Les ressources Énergie retirent ses charges, mais exposent à ses traîtrises.",
    ],
    "fne_villain_homme_pourpre": [
        "Statistiques basses, mais chaque carte attribue une Rencontre face cachée.",
        "Ses sbires INFLUENCÉ sont Vulnérables : sonnez-les ou désorientez-les.",
    ],
    "fne_villain_mary_typhoide": [
        "Elle se retourne à la fin de chaque phase du Méchant, alternant ses deux faces.",
        "Ni son attaque ni sa manigance ne sont donc à son avantage deux tours de suite.",
    ],
}


def villain_tips():
    """One block per subordinate, shown only for the one this job drew."""
    steps = []
    for code, _ in VILLAINS:
        for index, line in enumerate(VILLAIN_TIPS[code]):
            steps.append({
                "text": text(line, VILLAIN_TIPS_EN[code][index]),
                "when": {"drawIs": "villain:" + code},
            })
    return steps


def villain_setup():
    """The subordinate's own setup, keyed on which one this job drew.

    Four of the five are the same shape: two stages, a harder pair on Expert.
    Mary Typhoide is not — she arrives through her environment, and her job is
    won on tokens rather than on her health.
    """
    steps = []
    for code, name in VILLAINS:
        if code.endswith("mary_typhoide"):
            continue
        steps.append({
            "text": text(
                "Deck Méchant : %s (I) et (II)." % q(name),
                "Villain deck: %s (I) and (II)." % q(name),
            ),
            "when": {"all": [{"drawIs": "villain:" + code}, {"difficulty": "standard"}]},
        })
        steps.append({
            "text": text(
                "Deck Méchant : %s (II) et (III)." % q(name),
                "Villain deck: %s (II) and (III)." % q(name),
            ),
            "when": {"all": [{"drawIs": "villain:" + code}, {"difficulty": "expert"}]},
        })

    mary = "fne_villain_mary_typhoide"
    steps.append({
        "text": text(
            "Révélez la manigance annexe \"Gagner la Confiance\" et l'environnement "
            "\"Psyché Perturbée\".",
            "Reveal the \"Gagner la Confiance\" side scheme and the "
            "\"Psyché Perturbée\" environment.",
        ),
        "when": {"drawIs": "villain:" + mary},
    })
    # The book turns this face at random, so the app turns it. Two draws
    # rather than one: the stage differs by difficulty, and a draw's pool is
    # fixed when the template is written.
    for difficulty, stage in (("standard", "a"), ("expert", "b")):
        steps.append({
            "text": text(
                "Mise en place de \"Psyché Perturbée\" : commencez sur cette face, "
                "tirée au hasard par l'application.",
                "\"Psyché Perturbée\" setup: start on this face, drawn at random by "
                "the app.",
            ),
            "draw": {
                "id": "maryFace",
                "from": ["fne_face_mary_" + stage, "fne_face_bloody_" + stage],
            },
            "when": {"all": [{"drawIs": "villain:" + mary}, {"difficulty": difficulty}]},
        })
    steps.append({
        "text": text(
            "Ce scénario se gagne en plaçant trois pions sur \"Psyché Perturbée\", pas en "
            "vainquant le méchant.",
            "This scenario is won by placing three tokens on \"Psyché Perturbée\", not "
            "by defeating the villain.",
        ),
        "when": {"drawIs": "villain:" + mary},
    })
    return steps


def environment_board():
    """One line per resolved job: which environment, and which face up.

    The app knows both — a job seen through is ACHEVE, a job the villains pushed
    to three is ECHOUE and was never played — so it says so rather than leaving
    the table to reconstruct it from the log.
    """
    lines = []
    for scenario_id, name_fr, _, counter in SCENARIOS:
        lines.append({
            "text": text(
                "Mettez en jeu %s, face ACHEVÉ, et résolvez sa Mise en place." % q(name_fr),
                "Put %s into play, ACHEVÉ face up, and resolve its Setup." % q(name_fr),
            ),
            "when": {"flag": "acheve." + scenario_id},
        })
        lines.append({
            "text": text(
                "Mettez en jeu %s, face ÉCHOUÉ, et résolvez sa Mise en place." % q(name_fr),
                "Put %s into play, ÉCHOUÉ face up, and resolve its Setup." % q(name_fr),
            ),
            # Both routes to a failed job, the same condition the choice
            # list and Le Caïd read.
            "when": {"any": [
                {"counter": counter, "atLeast": 3},
                {"flag": "echoue." + scenario_id},
            ]},
        })
    return lines



def shared_campaign_setup():
    """The p.9 sequence every scenario runs, minus the draw the app now does.

    Steps 1 to 7 happen before this screen — the app draws the environments and
    pushes their scenarios on the choice page, and the villain was dealt when
    the campaign began. What is left is the book's order from step 8: the allies
    struck off, the two Expert lines, every settled environment face up, and the
    campaign ally.
    """
    return [
        # 8. Strike off what the campaign has taken, and fill the deck back up.
        {
            "text": text(
                "Retirez de vos decks les alliés et soutiens inscrits comme retirés "
                "de la campagne, puis complétez jusqu'à la taille légale.",
                "Remove from your decks the allies and supports recorded as removed "
                "from the campaign, then top up to the legal deck size.",
            ),
            "when": {"cardList": "alliesRetires", "minSize": 1},
        },
        # 10 and 11. Expert carries damage between games.
        {
            "text": text(
                "Campagne Experte Uniquement : reprenez les points de vie enregistrés au "
                "scénario précédent.",
                "Expert: set each hit point total to the value recorded last scenario.",
            ),
            "when": {"difficulty": "expert"},
        },
        {
            "text": text(
                "Campagne Experte Uniquement : chaque joueur peut prendre une carte "
                "Rencontre face cachée "
                "pour se soigner de sa REC.",
                "Expert: each player may take a facedown encounter card to heal for "
                "their REC.",
            ),
            "when": {"difficulty": "expert"},
        },
        # 12. Every settled job's environment, named and face up.
    ] + environment_board() + [
        # 13. She is out only while she is won and still standing.
        {
            "text": text(
                "Mettez en jeu l'alliée de campagne \"Mary Typhoïde\".",
                "Put the campaign ally \"Typhoid Mary\" into play.",
            ),
            "when": {"all": [
                {"flag": "confianceGagnee"},
                {"notFlag": "maryVaincue"},
            ]},
        },
        # Once she is gone she stays gone, and the line saying so replaces the
        # one that put her out.
        {
            "text": text(
                "Retirez \"Mary Typhoïde\" de vos decks : elle est perdue pour la campagne.",
            "Remove \"Mary Typhoïde\" from your decks: she is lost for the campaign.",
            ),
            "when": {"flag": "maryVaincue"},
        },
    ]


def vp_prompt():
    return {
        "id": "vp",
        "type": "number",
        "label": text(
            "Combien de points de victoire avez-vous accumulés ?",
            "How many victory points did you gather?",
        ),
    }


def victory_outcome(scenario_id):
    """The bookkeeping after any of the five interchangeable scenarios."""
    return {
        "message": text(
            "Vous avez battu {villain}. %s passe ACHEVÉ : mettez cet "
            "environnement en jeu sur sa face ACHEVÉ à chaque partie suivante. "
            "Le Caïd sera un peu plus préparé à vous affronter."
            % ("{card:%s}" % scenario_id),
            "You beat {villain}. %s turns ACHEVÉ: put that environment into play "
            "on its ACHEVÉ face in every later game. Le Caïd will be that bit "
            "better prepared for you."
            % ("{card:%s}" % scenario_id),
        ),
        "prompts": [
            vp_prompt(),
            # Two tokens on Psyche Perturbee wins her over, which only the
            # job she is behind can put on the table — and only once. She
            # is dealt to a single scenario, so this is asked there and nowhere
            # else — a table that never faces her is never asked about a card
            # they have not seen.
            {
                "id": "confiance",
                "type": "boolean",
                "label": text(
                    "\"Psyché Perturbée\" est-elle en jeu avec au moins 2 pions dessus ?",
                    "Is \"Disturbed Psyche\" in play with at least 2 tokens on it?",
                ),
                "when": {"all": [
                    {"drawIs": "villain:fne_villain_mary_typhoide"},
                    {"notFlag": "confianceGagnee"},
                ]},
            },
            # Either side can put her down, so the victory pile is the thing to
            # look at rather than who did it. Asked only of a table that has
            # actually met her — facing her, or fighting alongside her — and
            # only until the game she does not come back from.
            {
                "id": "mary",
                "type": "boolean",
                "label": text(
                    "\"Mary Typhoïde\" / \"Bloody Mary\" est-elle dans la pile de victoire ?",
                    "Is \"Typhoid Mary\" / \"Bloody Mary\" in the victory pile?",
                ),
                "when": {"all": [
                    {"any": [
                        {"drawIs": "villain:fne_villain_mary_typhoide"},
                        {"flag": "confianceGagnee"},
                    ]},
                    {"notFlag": "maryVaincue"},
                ]},
            },
            {
                "id": "retires",
                "type": "deckCardSelect",
                "label": text(
                    "Quels alliés ou soutiens uniques ont été retirés de la partie ?",
                    "Which unique allies or supports were removed from the game?",
                ),
            },
        ],
        "effects": [
            {"op": "setFlag", "flag": "confianceGagnee", "boolValue": True,
             "when": {"answer": "confiance"}},
            {"op": "setFlag", "flag": "maryVaincue", "boolValue": True,
             "when": {"answer": "mary"}},
            {"op": "addCardsFromAnswer", "cardList": "alliesRetires", "from": "retires"},
            {"op": "setFlag", "flag": "acheve." + scenario_id, "boolValue": True},
        ],
        "next": [{"choose": True}],
    }


def defeat_outcome(counter, scenario_id):
    """A lost job is not a failed one.

    The book is explicit that losing does not fail a scenario and that it can be
    played again, so nothing about the environment is recorded here. Only three
    ticks fails a job, and that is counted from the draw, not from this page.
    """
    return {
        "message": text(
            "{villain} vous a échappé. Vous pouvez y retourner tout de suite. "
            "Si vous continuez, %s passe ÉCHOUÉ : elle sort de la campagne et "
            "son environnement reste sur la table, face ÉCHOUÉ."
            % ("{card:%s}" % scenario_id),
            "{villain} got away. You can go straight back and try again. If you "
            "continue, %s turns ÉCHOUÉ: it leaves the campaign and its "
            "environment stays on the table, ÉCHOUÉ face up."
            % ("{card:%s}" % scenario_id),
        ),
        # Recorded only if the players move on. Going back costs nothing, which
        # is exactly what the book allows.
        "onContinue": [
            {"op": "setFlag", "flag": "echoue." + scenario_id, "boolValue": True},
        ],
        "prompts": [vp_prompt()],
        "effects": [
            # Standard lets a lost place be tried again; only Expert pushes it.
            {"op": "addCounter", "counter": counter, "value": 1, "max": 3,
             "when": {"difficulty": "expert"}},
        ],
        "next": [{"choose": True}],
    }


def scenario(scenario_id, name_fr, name_en, counter):
    return {
        "id": scenario_id,
        # The scenario's name is the printed title of its main scheme and of
        # the environment that carries it, so it stays French in both languages
        # like every other card title here. name_en is kept for the day
        # MarvelCDB publishes the pack and the real English title is known.
        "name": text(name_fr),
        "flavour": text(SCENARIO_FLAVOUR[scenario_id], SCENARIO_FLAVOUR_EN[scenario_id]),
        "pressureCounterId": counter,
        # Two ways a job fails: the villains push it to three, or the players
        # lose there and choose to move on rather than go back.
        "failedWhen": {"any": [
            {"counter": counter, "atLeast": 3},
            {"flag": "echoue." + scenario_id},
        ]},
        "victoryLabel": text("Le méchant est vaincu !", "The villain is beaten!"),
        "defeatLabel": text("Le méchant l'emporte !", "The villain wins!"),
        "baseSetup": {},
        # Read in the order it is done at the table: who you are facing, the
        # three decks, then the setup, then the notes on the subordinate.
        # What to find and put out, before anything is laid down.
        "preSetup": [
            # Dealt when the campaign began and kept quiet until now; this is
            # where the players find out who is behind the job.
            {"text": text(
                "Méchant SUBORDONNÉ : {villain}.",
                "SUBORDINATE villain: {villain}.",
            )},
            # The environment of the job being played stays out of the game
            # until the job resolves, but it wants to be within reach: it is
            # turned to one face or the other the moment the scenario ends.
            {"text": text(
                "Gardez à portée l'environnement %s : vous le retournerez sur sa "
                "face ACHEVÉ ou ÉCHOUÉ à la fin de la partie." % ("{card:%s}" % scenario_id),
                # The faces keep their printed names in both languages: the card
                # in the player's hand came out of the French box and says
                # ACHEVÉ on one side and ÉCHOUÉ on the other.
                "Keep the %s environment within reach: you turn it to its ACHEVÉ "
                "or ÉCHOUÉ face when the scenario ends." % ("{card:%s}" % scenario_id),
            )},
            {"include": "mechant"},
            {"text": text(
                "Deck Manigance Principale : %s%s."
                % (q(SCENARIO_DECKS[scenario_id][0]), SCENARIO_DECKS[scenario_id][1]),
                "Main scheme deck: %s%s."
                % (q(SCENARIO_DECKS[scenario_id][0]), NOTES_EN[SCENARIO_DECKS[scenario_id][1]]),
            )},
            {"text": text(
                "deck Rencontre : %s, plus le set du méchant."
                % q_all(SCENARIO_DECKS[scenario_id][2]),
                "Encounter deck: %s, plus the villain's set."
                % q_all(SCENARIO_DECKS[scenario_id][2]),
            )},
        ],
        # The mise en place itself.
        "campaignSetup": [
            {"text": text(SCENARIO_EXTRA[scenario_id], SCENARIO_EXTRA_EN[scenario_id])},
            {
                "text": PRESSURE_SETUP[scenario_id],
                "when": {"counter": counter, "atLeast": 1},
            },
            {"include": "campagne"},
        ],
        # Read once the table is set: how this subordinate plays.
        "information": [{"include": "conseils"}],
        "onVictory": victory_outcome(scenario_id),
        "onDefeat": defeat_outcome(counter, scenario_id),
    }


def kingpin():
    return {
        "id": "s6_caid",
        "name": text("Le Caïd"),
        "flavour": text(SCENARIO_FLAVOUR["s6_caid"], SCENARIO_FLAVOUR_EN["s6_caid"]),
        "victoryLabel": text("Le Caïd est tombé !", "Kingpin has fallen!"),
        "defeatLabel": text("Le Caïd vous échappe.", "Kingpin gets away."),
        "baseSetup": {},
        "preSetup": [
            {"text": text("Deck Méchant : \"Le Caïd\" (A1).",
                          "Villain deck: \"Le Caïd\" (A1)."),
             "when": {"difficulty": "standard"}},
            {"text": text("Deck Méchant : \"Le Caïd\" (B1).",
                          "Villain deck: \"Le Caïd\" (B1)."),
             "when": {"difficulty": "expert"}},
            {"text": text(
                "Deck Manigance Principale : \"Le Gambit du Roi\", \"Fin de la Partie\".",
                "Main scheme deck: \"King's Gambit\", \"End Game\".",
            )},
            {"text": text(
                "deck Rencontre : \"Le Caïd\", \"Tombstone\", \"Mafia des Survêtes\".",
                "Encounter deck: \"Le Caïd\", \"Tombstone\", \"Mafia des Survêtes\".",
            )},
            {"text": text("Ce scénario n'utilise pas le set Standard.",
                          "This scenario uses no Standard set."),
             "when": {"difficulty": "standard"}},
            {"text": text("Ce scénario utilise le set Expert à la place du Standard.",
                          "This scenario uses the Expert set instead of Standard."),
             "when": {"difficulty": "expert"}},
        ],
        "campaignSetup": [
            {"include": "campagne"},
            {"text": text(
                "Révélez votre sbire Némésis mis de côté. S'il double un personnage "
                "en jeu, prenez un sbire SUBORDONNÉ à la place.",
                "Reveal your set-aside nemesis minion. If it doubles a character in "
                "play, take a SUBORDINATE minion instead.",
            )},
            {
                "text": text(
                    "3 environnements ACHEVÉ ou plus : carte Tenace à chaque sbire en jeu.",
                    "3+ ACHEVÉ environments: a Tough status card on each minion in play.",
                ),
                "when": {"countTrue": "acheve", "countAtLeast": 3},
            },
            {
                "text": text(
                    "4 environnements ACHEVÉ ou plus : trouvez et révélez \"James Wesley\".",
                    "4+ ACHEVÉ environments: find and reveal \"James Wesley\".",
                ),
                "when": {"countTrue": "acheve", "countAtLeast": 4},
            },
        ],
        "onVictory": {
            "prompts": [vp_prompt()],
            "effects": [{"op": "setFlag", "flag": "acheve.s6_caid", "boolValue": True}],
            "next": [{"end": True}],
        },
        # The last villain may be faced again as often as a table can stand.
        # Stopping there is a decision, offered as a button on the defeat page
        # rather than concluded by the app.
        "onDefeat": {
            "next": [{"goto": "s6_caid"}],
        },
    }


def build():
    counters = [
        {
            "id": counter,
            "scope": "campaign",
            "initial": 0,
            "min": 0,
            # Three pushes and the place is gone. The book is clear this is the
            # scenario, not the campaign: "Si un scénario a 3 coches à sa droite,
            # il a Échoué", and p.8 that losing a scenario is not losing the run.
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

    local_names = {}
    for sid, name_fr, _, _ in SCENARIOS:
        local_names[sid] = text(name_fr)
    for code, name_fr in VILLAINS:
        local_names[code] = text(name_fr)
    # The two faces of Psyche Perturbee, which the app turns at random.
    for face, face_name in (
        ("fne_face_mary_a", "Mary Typhoïde (A)"),
        ("fne_face_bloody_a", "Bloody Mary (A)"),
        ("fne_face_mary_b", "Mary Typhoïde (B)"),
        ("fne_face_bloody_b", "Bloody Mary (B)"),
    ):
        local_names[face] = text(face_name)

    template = {
        "_note": [
            "Peur de Rien / Fear No Evil. Mechanics only: what to place, what to",
            "record, what carries. No rules text, nothing from the campaign book.",
            "",
            "No scenario order. The app draws two environments before each choice",
            "and pushes them; the table plays any scenario still standing; the app",
            "draws the subordinate villain. Three pushes fails a scenario.",
            "",
            "Card codes are absent - the encounter side is not on MarvelCDB - so",
            "environments and villains are named locally, in French, until it is.",
            "",
            "Generated by tools/build_fne_template.py. Do not edit by hand.",
        ],
        "id": "fne",
        "schemaVersion": 1,
        "name": text("Peur de Rien", "Fear No Evil"),
        "packCode": "fne",
        # Marked in the chooser while the campaign is still being corrected
        # against the book and played through at a real table.
        "wip": True,
        "difficulties": ["standard", "expert"],
        "chooseFirstScenario": True,
        # Page 8 prints an order for a table meeting these five for the first
        # time, and says later campaigns may draw at random instead. Asked here
        # because it governs the whole run, and answered once.
        "setupChoices": [
            {
                "id": "villainOrder",
                "label": text("Ordre des SUBORDONNÉS", "Order of the SUBORDINATES"),
                "options": [
                    {
                        "id": "recommended",
                        "label": text("Ordre recommandé", "Recommended order"),
                        "detail": text(
                            "Pour une première campagne : Hammerhead, Bullseye, "
                            "Electro, puis L'Homme Pourpre et Mary Typhoïde.",
                            "For a first campaign: Hammerhead, Bullseye, Electro, "
                            "then L'Homme Pourpre and Mary Typhoïde.",
                        ),
                    },
                    {
                        "id": "random",
                        "label": text("Au hasard", "At random"),
                        "detail": text(
                            "L'application tire le SUBORDONNÉ de chaque mission "
                            "parmi ceux qui n'ont pas encore été affrontés.",
                            "The app draws each mission's SUBORDINATE from those "
                            "not yet faced.",
                        ),
                    },
                ],
            },
        ],
        "finaleScenarioId": "s6_caid",
        "environmentDraw": {
            "id": "environments",
            "from": [sid for sid, _, _, _ in SCENARIOS],
            "count": 2,
            "counts": {sid: counter for sid, _, _, counter in SCENARIOS},
        },
        "localCardNames": local_names,
        # Shown before the campaign starts. What the app does for the table,
        # what it needs from them, and the one rule worth knowing up front.
        "notice": text(
            "Campagne non officielle, reconstituée pour l'application : le livret "
            "et les cartes de la boîte Peur de Rien restent nécessaires pour jouer."
            "\n\n"
            "L'application tire pour vous les environnements et le SUBORDONNÉ de "
            "chaque mission, et tient le compte de la pression. Une mission qui "
            "atteint trois marqueurs est perdue : elle sort de la campagne. Le "
            "Caïd sera d'autant mieux préparé que vous aurez achevé de missions."
            "\n\n"
            "Les noms de cartes viennent de la boîte française, la seule dont "
            "dispose le développeur. La traduction anglaise du reste est en cours.",

            "Unofficial, reconstructed for the app: the rulebook and cards from "
            "the Fear No Evil box are still needed to play."
            "\n\n"
            "The app draws the environments and each mission's SUBORDINATE for "
            "you, and keeps count of the pressure. A mission that reaches three "
            "marks is lost and leaves the campaign. Kingpin is the better "
            "prepared for every mission you achieve, not for the ones you lose."
            "\n\n"
            "Card names and the campaign's own terms come from the French box, "
            "the only one the developer has, so they may not match the official "
            "English printing. English wording for the rest is on its way.",
        ),
        "villainPool": VILLAIN_CODES,
        # Three ticks fails that job, not the campaign — the book's rule, and
        # the only one that can be survived. Ending the run there instead is
        # arithmetically impossible: two ticks are dealt every rotation into a
        # pile that shrinks as jobs are settled, so whichever job is left
        # standing always reaches three. Simulated three thousand times with
        # perfect play, it was lost three thousand times.
        #
        # A fallen job still costs: it stops being playable, and Le Caïd is
        # harder for every one the players failed to reach.
        "losesWhenScenarioFails": False,
        "counters": counters,
        "flagSets": [
            {"id": "acheve", "scope": "perScenario"},
            {"id": "echoue", "scope": "perScenario"},
            {"id": "confianceGagnee", "scope": "campaign"},
            {"id": "maryVaincue", "scope": "campaign"},
        ],
        "cardLists": [
            {"id": "alliesRetires", "scope": "campaign"},
        ],
        "setupFragments": {
            "campagne": shared_campaign_setup(),
            "mechant": villain_setup(),
            "conseils": villain_tips(),
        },
        "scenarios": [scenario(*s) for s in SCENARIOS] + [kingpin()],
    }

    path = "app/src/main/assets/campaigns/fne.json"
    with io.open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(template, out, ensure_ascii=False, indent=1)
        out.write("\n")
    print("wrote", path, os.path.getsize(path), "bytes,", len(template["scenarios"]), "scenarios")


build()
