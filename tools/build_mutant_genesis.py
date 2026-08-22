# -*- coding: utf-8 -*-
"""Builds the Mutant Genesis campaign template.

Five scenarios in a fixed order, Sabretooth through Magneto, and every one of
them has to be won: this campaign has no branch and no way past a scenario the
players cannot beat. A loss costs nothing and is simply played again, which is
the rulebook's own wording.

WHAT THE CAMPAIGN REMEMBERS

Four things carry between scenarios, and all four are recorded rather than
asked twice:

  the roles        chosen once, at the first scenario, one each
  the side scheme  each scenario has one, and beating it earns the next
                   scenario's role upgrade
  Future Past      cards that were still in the deck, the discard pile or in
                   play are shuffled back in next time; the ones in the victory
                   display are gone for good
  the rescued      Jubilee, the CAPTIVE allies who can join a deck, and the
                   allies left behind who cannot be used again

ROLES, AND WHY THE ENGINE GREW A FEATURE FOR THEM

Each player picks a role and is dealt one random upgrade from that role's own
set of five. That is a draw per player from a different pile each, which no
campaign had needed before, so DrawDefinition gained per-hero pools. An upgrade
that has been in play is removed from the campaign for the player who had it,
so each player's five shrink independently -- hence a per-hero spent list too.

Card codes are MarvelCDB codes for pack 'mut_gen', read from the bundled card
database rather than transcribed.
"""
import io
import json

NL = chr(10)

# --- villains, one stage per difficulty step --------------------------------
SABRETOOTH = ["32060", "32061", "32062"]
SENTINEL = ["32084", "32085", "32086"]
MASTER_MOLD = ["32109", "32110", "32111"]
MAGNETO = ["32138", "32139", "32140"]

# Mansion Attack has four villains and only one in play at a time. The (A)
# sides are standard, the (B) sides expert.
BROTHERHOOD_A = ["32121a", "32122a", "32123a", "32124a"]
BROTHERHOOD_B = ["32121b", "32122b", "32123b", "32124b"]

# --- the campaign's own cards ------------------------------------------------
FRIGHTENED_POLICE = "32171a"
ENEMY_OF_MY_ENEMY = "32172a"
FIND_THE_PRISONERS = "32173a"
SURPRISE_ATTACK = "32174a"
MAGNETOS_FORTRESS = "32175a"

JUBILEE = "32088b"
CAPTIVES = ["32089", "32090", "32091", "32092"]

# Everything in the Future Past set that can be recorded and shuffled back.
FUTURE_PAST = ["32166", "32167", "32168", "32169", "32170"]

# --- the four roles ----------------------------------------------------------
# id, printed name, the two aspects it opens up, and its five upgrades.
ROLES = [
    ("brawler", "Brawler", "Aggression + Protection",
     ["32176", "32177", "32178", "32179", "32180"]),
    ("commander", "Commander", "Aggression + Leadership",
     ["32181", "32182", "32183", "32184", "32185"]),
    ("defender", "Defender", "Justice + Protection",
     ["32186", "32187", "32188", "32189", "32190"]),
    ("peacekeeper", "Peacekeeper", "Justice + Leadership",
     ["32191", "32192", "32193", "32194", "32195"]),
]

# The marker a player records for their role. Not a card: nothing shows this
# list, it only tells the upgrade draw which pile to deal from.
ROLE_MARKER = {role: "role:" + role for role, _, _, _ in ROLES}


# ---------------------------------------------------------------------------
# FRENCH
#
# Keyed by the English string, so adding a language costs a dictionary rather
# than an edit at every call site. Terms follow the bilingual rules reference
# the app bundles and the reference glossary: affinite, mechant, sbire,
# manigance, carte Rencontre. Card and set names follow MarvelCDB's French data.
# ---------------------------------------------------------------------------

FR = {
    "Unofficial, reconstructed for the app: the rulebook and cards from the Mutant Genesis box are still needed to play.\n\nThe app keeps the campaign log for you. It remembers each player's role, which campaign side schemes were beaten, which Future Past cards are still in circulation, and who was rescued. It also deals what the rules deal at random: the role upgrade each player starts a scenario with, from that player's own set, and the order the Brotherhood arrive in.\n\nExpert is the expert campaign: the harder villain stages, and hit points that carry from one scenario to the next.":
        "Non officiel, reconstitué pour l'application : le livret et les cartes de la boîte La Genèse des Mutants restent nécessaires pour jouer.\n\nL'application tient le journal de campagne pour vous. Elle retient le rôle de chaque joueur, les manigances annexes de campagne vaincues, les cartes Avenir Passé encore en circulation, et qui a été secouru. Elle distribue aussi ce que les règles distribuent au hasard : l'amélioration de rôle avec laquelle chaque joueur commence un scénario, prise dans son propre set, et l'ordre d'arrivée de la Confrérie.\n\nExpert correspond à la campagne experte : les stades de méchant les plus durs, et des points de vie qui se reportent d'un scénario au suivant.",
    'Each player may place 1 acceleration token on the main scheme to heal to full.\nA player defeated last scenario rejoins this way.':
        "Chaque joueur peut placer 1 jeton d'accélération sur la manigance principale pour se soigner complètement.\nUn joueur vaincu au scénario précédent revient ainsi.",
    'A player defeated last scenario rejoins this way.':
        'Un joueur vaincu au scénario précédent revient ainsi.',
    'An upgrade is dealt to each player from their own role.':
        'Une amélioration est distribuée à chaque joueur depuis son propre rôle.',
    'Asteroid M is above them and Master Mold is inside it. Magneto cannot be finished while his fortress still stands.':
        "L'Astéroïde M est au-dessus d'eux et le Moule Initial est à l'intérieur. Magnéto ne peut pas être achevé tant que sa forteresse tient encore.",
    'Brawler':
        'Bagarreur',
    'Brawler (Aggression + Protection)':
        'Bagarreur (Agressivité + Protection)',
    'Commander':
        'Commandeur',
    'Commander (Aggression + Leadership)':
        'Commandeur (Agressivité + Commandement)',
    'Defender':
        'Défenseur',
    'Defender (Justice + Protection)':
        'Défenseur (Justice + Protection)',
    "Each of these rescued allies may be shuffled into any player's deck.":
        "Chacun de ces alliés secourus peut être mélangé au deck d'un joueur.",
    'Each player may place 1 acceleration token on the main scheme to heal to full.':
        "Chaque joueur peut placer 1 jeton d'accélération sur la manigance principale pour se soigner complètement.",
    'Each player takes a different role.':
        'Chaque joueur prend un rôle différent.',
    'Expert is the expert campaign: the harder villain stages, and hit points that carry from one scenario to the next.':
        "Expert correspond à la campagne experte : les stades de méchant les plus durs, et des points de vie qui se reportent d'un scénario au suivant.",
    'How many victory points are in the victory display?':
        'Combien de points de victoire y a-t-il dans la pile de victoire ?',
    'Is Jubilee in play?':
        'Jubilé est-elle en jeu ?',
    'Jubilee joins you. Put her into play.':
        'Jubilé vous rejoint. Mettez-la en jeu.',
    'Magneto':
        'Magnéto',
    'Magneto knows where the Sentinels are made, and for once he and the X-Men want the same thing. The factory is still building.':
        "Magnéto sait où les Sentinelles sont fabriquées, et pour une fois lui et les X-Men veulent la même chose. L'usine tourne toujours.",
    'Magneto rules the world with an iron fist.':
        "Magnéto règne sur le monde d'une main de fer.",
    'Mansion Attack':
        "L'Attaque du Manoir",
    'Master Mold':
        'Moule Initial',
    'Mutant Genesis':
        'La Genèse des Mutants',
    'Peacekeeper':
        'Pacificateur',
    'Peacekeeper (Justice + Leadership)':
        'Pacificateur (Justice + Commandement)',
    'Place cards facedown under Operation Zero Tolerance for the difficulty you are playing: 1 on Standard, 2 on Expert.':
        'Placez des cartes face cachée sous Opération Tolérance Zéro selon la difficulté jouée : 1 en Standard, 2 en Expert.',
    'Place damage on Robert Kelly for the difficulty you are playing: 1 on Standard, 2 on Expert.':
        'Placez des dégâts sur Robert Kelly selon la difficulté jouée : 1 en Standard, 2 en Expert.',
    'Project Wideawake':
        'Projet Wideawake',
    'Remaining hit points':
        'Points de vie restants',
    'Reveal this side scheme.':
        'Révélez cette manigance annexe.',
    'Sabretooth':
        'Dents-de-Sabre',
    'Senator Kelly has made a career of the fear of mutants, and Sabretooth has come for him. Whatever the X-Men think of the man, they cannot let him die on that stage.':
        'Le sénateur Kelly a bâti sa carrière sur la peur des mutants, et Dents-de-Sabre vient pour lui. Quoi que les X-Men pensent de cet homme, ils ne peuvent pas le laisser mourir sur cette estrade.',
    "Set each player's hit points to what they had left at the end of the last scenario.":
        "Fixez les points de vie de chaque joueur à ce qu'il lui restait à la fin du scénario précédent.",
    'Shuffle the Future Past set and set it aside. This is the Future Past deck.':
        "Mélangez le set Avenir Passé et mettez-le de côté. C'est le deck Avenir Passé.",
    'Shuffle these Future Past cards into the encounter deck. Shuffle the rest of the Future Past deck and set it aside.':
        'Mélangez ces cartes Avenir Passé dans le deck Rencontre. Mélangez le reste du deck Avenir Passé et mettez-le de côté.',
    'Take this role':
        'Prenez ce rôle',
    'The Brotherhood are inside the school. Magneto wanted the X-Men called home, and it worked.':
        "La Confrérie est dans l'école. Magnéto voulait rappeler les X-Men chez eux, et cela a marché.",
    'The Brotherhood arrive in this order. Defeat 2 of them on Standard, 3 on Expert, to win.':
        'La Confrérie arrive dans cet ordre. Vainquez-en 2 en Standard, 3 en Expert, pour gagner.',
    'The Sentinels are already in the streets, and every mutant they take is one more card face down under a scheme nobody can remove.':
        "Les Sentinelles sont déjà dans les rues, et chaque mutant qu'elles emportent est une carte de plus face cachée sous une manigance que personne ne peut retirer.",
    "The app keeps the campaign log for you. It remembers each player's role, which campaign side schemes were beaten, which Future Past cards are still in circulation, and who was rescued. It also deals what the rules deal at random: the role upgrade each player starts a scenario with, from that player's own set, and the order the Brotherhood arrive in.":
        "L'application tient le journal de campagne pour vous. Elle retient le rôle de chaque joueur, les manigances annexes de campagne vaincues, les cartes Avenir Passé encore en circulation, et qui a été secouru. Elle distribue aussi ce que les règles distribuent au hasard : l'amélioration de rôle avec laquelle chaque joueur commence un scénario, prise dans son propre set, et l'ordre d'arrivée de la Confrérie.",
    'These allies were left behind and cannot be used for the rest of the campaign.':
        'Ces alliés ont été abandonnés et ne peuvent plus servir de la campagne.',
    'Unofficial, reconstructed for the app: the rulebook and cards from the Mutant Genesis box are still needed to play.':
        "Non officiel, reconstitué pour l'application : le livret et les cartes de la boîte La Genèse des Mutants restent nécessaires pour jouer.",
    'Was the campaign side scheme defeated?':
        'La manigance annexe de campagne a-t-elle été vaincue ?',
    'Which Future Past cards were in the encounter deck, the discard pile or in play? The rest reached the victory display and are out of the campaign.':
        'Quelles cartes Avenir Passé étaient dans le deck Rencontre, la pile de défausse ou en jeu ? Les autres ont atteint la pile de victoire et quittent la campagne.',
    'Which allies ended the game under Find the Prisoners or Rescue Captives? They cannot be used again.':
        'Quels alliés ont fini la partie sous Trouver Les Prisonniers ou Secourir les Captifs ? Ils ne pourront plus servir.',
    'Which captive allies entered play?':
        'Quels alliés captifs sont entrés en jeu ?',
}


def t(en):
    """One string in both languages, English first."""
    return {"en": en, "fr": FR.get(en, en)}


def missing_french(data):
    """Every string the French map has not been taught yet."""
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

def role_steps():
    """Scenario 1 only: each player takes a role, and no two the same.

    Four per-hero actions rather than one question, because a role is picked at
    the table while the decks are being built, not answered at the end of a
    game. The engine cannot enforce "a different one each" -- nothing expresses
    a constraint across players -- so the instruction says it and the table
    keeps to it, which is what the rulebook asks of them anyway.
    """
    steps = [{
        "text": t("Each player takes a different role."),
    }]
    for role_id, name, aspects, _ in ROLES:
        steps.append({
            "text": t(name + " (" + aspects + ")"),
            "action": {
                "id": "role_" + role_id,
                "label": t("Take this role"),
                "perHero": True,
                "effects": [{
                    "op": "addCard",
                    "cardList": "role",
                    "cardCode": ROLE_MARKER[role_id],
                    "perHero": True,
                }],
            },
        })
    return steps


def upgrade_draw(earned_flag=None):
    """One random upgrade per player, from that player's own role.

    The pool is per hero, keyed by the marker the player recorded, and each
    player's spent upgrades are struck from theirs alone: an upgrade that has
    been in play is out of the campaign for whoever had it. Running the pile
    down to nothing is the "use it or lose it" rule working.
    """
    step = {
        "text": t("An upgrade is dealt to each player from their own role."),
        "draw": {
            "id": "roleUpgrade",
            "perHero": True,
            "perHeroPoolList": "role",
            "perHeroPools": {ROLE_MARKER[r]: ups for r, _, _, ups in ROLES},
            "excludingPerHero": "spentUpgrades",
        },
    }
    if earned_flag:
        step["when"] = {"flag": earned_flag}
    return step


def future_past_steps(first):
    """The Sentinels of the future, and which of them are still coming."""
    if first:
        return [{
            "text": t("Shuffle the Future Past set and set it aside. This is "
                      "the Future Past deck."),
        }]
    return [{
        "text": t("Shuffle these Future Past cards into the encounter deck. "
                  "Shuffle the rest of the Future Past deck and set it aside."),
        "showCardList": "futurePast",
        "when": {"cardList": "futurePast", "minSize": 1},
    }]


def rescued_steps():
    """Who came back with you, from scenario 3 onwards."""
    return [
        {
            "text": t("Jubilee joins you. Put her into play."),
            "cards": [JUBILEE],
            "when": {"flag": "jubilee"},
        },
        {
            "text": t("Each of these rescued allies may be shuffled into any "
                      "player's deck."),
            "showCardList": "captives",
            "when": {"cardList": "captives", "minSize": 1},
        },
        {
            "text": t("These allies were left behind and cannot be used for "
                      "the rest of the campaign."),
            "showCardList": "leftBehind",
            "when": {"cardList": "leftBehind", "minSize": 1},
        },
    ]


def expert_steps():
    """Persistent damage, which is the whole of the expert campaign."""
    return [
        {
            "text": t("Set each player's hit points to what they had left at "
                      "the end of the last scenario."),
            "when": {"difficulty": "expert"},
        },
        {
            "text": t("Each player may place 1 acceleration token on the main "
                      "scheme to heal to full." + NL +
                      "A player defeated last scenario rejoins this way."),
            "when": {"difficulty": "expert"},
        },
    ]


def side_scheme_step(code):
    """The campaign side scheme, which is what earns the next role upgrade."""
    return {"text": t("Reveal this side scheme."), "cards": [code]}


# ---------------------------------------------------------------------------
# THE END OF A SCENARIO
# ---------------------------------------------------------------------------

def victory(scenario_id, side_scheme_flag, earned_flag, goto,
            jubilee=False, captives=False, left_behind=False):
    """The victory steps, in the order the rulebook lists them."""
    prompts = [
        {"id": "vp", "type": "number",
         "label": t("How many victory points are in the victory display?")},
        {"id": "sideScheme", "type": "boolean",
         "label": t("Was the campaign side scheme defeated?")},
        {"id": "futurePastLeft", "type": "cardSelect", "cards": FUTURE_PAST,
         "label": t("Which Future Past cards were in the encounter deck, the "
                    "discard pile or in play? The rest reached the victory "
                    "display and are out of the campaign.")},
    ]
    effects = [
        {"op": "setFlag", "flag": side_scheme_flag, "boolValue": True,
         "when": {"answer": "sideScheme"}},
        # Replaced rather than added to: a card that reached the victory
        # display is gone, and adding would shuffle it back in next game.
        {"op": "setCardsFromAnswer", "cardList": "futurePast",
         "from": "futurePastLeft"},
        # The upgrade each player began with is out of the campaign for that
        # player, whether they used it or not.
        {"op": "addDrawnCard", "cardList": "spentUpgrades", "from": "roleUpgrade",
         "perHero": True},
    ]
    if earned_flag is None:
        # Scenario 1 always deals one, so it is always spent.
        pass
    else:
        effects[-1]["when"] = {"flag": earned_flag}

    if jubilee:
        prompts.append({
            "id": "jubilee", "type": "boolean",
            "label": t("Is Jubilee in play?"),
        })
        # Set either way: she is recorded when she is in play and struck when
        # she is not, so a rescue that goes wrong later does not stand.
        effects.append({"op": "setFlag", "flag": "jubilee",
                        "boolValue": True, "when": {"answer": "jubilee"}})
        effects.append({"op": "setFlag", "flag": "jubilee",
                        "boolValue": False, "when": {"notAnswer": "jubilee"}})

    if captives:
        prompts.append({
            "id": "captivesFreed", "type": "cardSelect", "cards": CAPTIVES,
            "label": t("Which captive allies entered play?"),
        })
        effects.append({"op": "addCardsFromAnswer", "cardList": "captives",
                        "from": "captivesFreed"})

    if left_behind:
        prompts.append({
            "id": "stillCaptive", "type": "deckCardSelect",
            "label": t("Which allies ended the game under Find the Prisoners "
                       "or Rescue Captives? They cannot be used again."),
        })
        effects.append({"op": "addCardsFromAnswer", "cardList": "leftBehind",
                        "from": "stillCaptive"})

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

    The one exception is the finale of an expert campaign, where the rulebook
    says losing loses the campaign outright.
    """
    if not final:
        return {"next": [{"goto": scenario_id}]}
    return {
        "message": t("Magneto rules the world with an iron fist."),
        "next": [
            {"end": True, "when": {"difficulty": "expert"}},
            {"goto": scenario_id},
        ],
    }


# ---------------------------------------------------------------------------
# THE SCENARIOS
# ---------------------------------------------------------------------------

def scenario(scenario_id, name, villain, main_scheme, sets, side_scheme,
             side_scheme_flag, earned_flag, goto, flavour,
             first=False, jubilee=False, captives=False, left_behind=False,
             extra_setup=(), villain_deck=None, final=False):
    steps = []
    steps += future_past_steps(first)
    if first:
        steps += role_steps()
    if not first:
        steps += rescued_steps() if (jubilee or captives or left_behind or
                                     scenario_id >= "s3") else []
    steps.append(upgrade_draw(earned_flag))
    steps.append(side_scheme_step(side_scheme))
    steps += list(extra_setup)
    if not first:
        steps += expert_steps()

    if villain_deck is None:
        villain_deck = {
            "standard": [villain[0], villain[1]],
            "expert": [villain[1], villain[2]],
        }

    return {
        "id": scenario_id,
        "name": t(name),
        "flavour": t(flavour),
        "baseSetup": {
            "villainDeck": villain_deck,
            "mainScheme": main_scheme,
            "encounterSets": sets,
        },
        "campaignSetup": steps,
        "onVictory": victory(scenario_id, side_scheme_flag, earned_flag, goto,
                             jubilee=jubilee, captives=captives,
                             left_behind=left_behind),
        "onDefeat": defeat(scenario_id, final=final),
    }


def build():
    scenarios = [
        scenario(
            "s1_sabretooth", "Sabretooth", SABRETOOTH, ["32063a"],
            ["sabretooth", "brotherhood", "mystique", "standard"],
            side_scheme=FRIGHTENED_POLICE, side_scheme_flag="frightenedPolice",
            earned_flag=None, goto="s2_wideawake", first=True,
            flavour="Senator Kelly has made a career of the fear of mutants, "
                    "and Sabretooth has come for him. Whatever the X-Men think "
                    "of the man, they cannot let him die on that stage.",
            extra_setup=[{
                "text": t("Place damage on Robert Kelly for the difficulty "
                          "you are playing: 1 on Standard, 2 on Expert."),
                "cards": ["32066"],
            }],
        ),
        scenario(
            "s2_wideawake", "Project Wideawake", SENTINEL, ["32087a"],
            ["project_wideawake", "sentinels", "zero_tolerance", "standard"],
            side_scheme=ENEMY_OF_MY_ENEMY, side_scheme_flag="enemyOfMyEnemy",
            earned_flag="frightenedPolice", goto="s3_mastermold",
            jubilee=True, captives=True,
            flavour="The Sentinels are already in the streets, and every mutant "
                    "they take is one more card face down under a scheme "
                    "nobody can remove.",
            extra_setup=[{
                "text": t("Place cards facedown under Operation Zero Tolerance "
                          "for the difficulty you are playing: 1 on Standard, "
                          "2 on Expert."),
                "cards": ["32104"],
            }],
        ),
        scenario(
            "s3_mastermold", "Master Mold", MASTER_MOLD, ["32112a"],
            ["master_mold", "sentinels", "zero_tolerance", "standard"],
            side_scheme=FIND_THE_PRISONERS, side_scheme_flag="findThePrisoners",
            earned_flag="enemyOfMyEnemy", goto="s4_mansion",
            jubilee=True, left_behind=True,
            flavour="Magneto knows where the Sentinels are made, and for once "
                    "he and the X-Men want the same thing. The factory is still "
                    "building.",
        ),
        scenario(
            "s4_mansion", "Mansion Attack", None, ["32125a"],
            ["mansion_attack", "brotherhood", "mystique", "standard"],
            side_scheme=SURPRISE_ATTACK, side_scheme_flag="surpriseAttack",
            earned_flag="findThePrisoners", goto="s5_magneto",
            jubilee=True,
            villain_deck={"standard": BROTHERHOOD_A, "expert": BROTHERHOOD_B},
            flavour="The Brotherhood are inside the school. Magneto wanted the "
                    "X-Men called home, and it worked.",
            extra_setup=[{
                "text": t("The Brotherhood arrive in this order. Defeat 2 of "
                          "them on Standard, 3 on Expert, to win."),
                "draw": {"id": "brotherhoodOrder", "from": BROTHERHOOD_A,
                         "count": 4},
                "when": {"difficulty": "standard"},
            }, {
                "text": t("The Brotherhood arrive in this order. Defeat 2 of "
                          "them on Standard, 3 on Expert, to win."),
                "draw": {"id": "brotherhoodOrderExpert", "from": BROTHERHOOD_B,
                         "count": 4},
                "when": {"difficulty": "expert"},
            }],
        ),
        scenario(
            "s5_magneto", "Magneto", MAGNETO, ["32141a"],
            ["magneto_villain", "acolytes", "standard"],
            side_scheme=MAGNETOS_FORTRESS, side_scheme_flag="magnetosFortress",
            earned_flag="surpriseAttack", goto=None, jubilee=True, final=True,
            flavour="Asteroid M is above them and Master Mold is inside it. "
                    "Magneto cannot be finished while his fortress still "
                    "stands.",
        ),
    ]

    return {
        "id": "mg",
        "schemaVersion": 1,
        "name": t("Mutant Genesis"),
        "packCode": "mut_gen",
        "notice": t(
            "Unofficial, reconstructed for the app: the rulebook and cards "
            "from the Mutant Genesis box are still needed to play."
            "\n\n"
            "The app keeps the campaign log for you. It remembers each "
            "player's role, which campaign side schemes were beaten, which "
            "Future Past cards are still in circulation, and who was rescued. "
            "It also deals what the rules deal at random: the role upgrade "
            "each player starts a scenario with, from that player's own set, "
            "and the order the Brotherhood arrive in."
            "\n\n"
            "Expert is the expert campaign: the harder villain stages, and "
            "hit points that carry from one scenario to the next."
        ),
        "difficulties": ["standard", "expert"],
        "counters": [
            {"id": "hp", "scope": "hero", "initial": 0,
             "maxFrom": "heroCard.health"},
        ],
        "flagSets": [
            {"id": "frightenedPolice"}, {"id": "enemyOfMyEnemy"},
            {"id": "findThePrisoners"}, {"id": "surpriseAttack"},
            {"id": "magnetosFortress"}, {"id": "jubilee"},
        ],
        "cardLists": [
            {"id": "role", "scope": "campaign"},
            {"id": "spentUpgrades", "scope": "campaign"},
            {"id": "futurePast", "scope": "campaign"},
            {"id": "captives", "scope": "campaign"},
            {"id": "leftBehind", "scope": "campaign"},
        ],
        "startScenarioId": "s1_sabretooth",
        "scenarios": scenarios,
    }


if __name__ == "__main__":
    data = build()
    missing = missing_french(data)
    if missing:
        print("Untranslated (%d):" % len(missing))
        for line in missing:
            print("   ", line)
    out = "app/src/main/assets/campaigns/mg.json"
    io.open(out, "w", encoding="utf-8", newline=NL).write(
        json.dumps(data, ensure_ascii=False, indent=2) + NL
    )
    print("wrote", out)
