'use strict';

/* Thwart Web — card search and the rules reference, offline.
 *
 * No framework and no build step. The whole catalogue is a JSON file the page
 * ships with, so a search is an array scan over 4,375 objects: about a
 * millisecond, and no network at all after the first visit. */

var UI = {
  en: {
    cards: 'Cards', rules: 'Rules',
    searchCards: 'Search cards', searchRules: 'Search the rules',
    noCards: 'No card matches your search.', noRules: 'No rule matches your search.',
    englishOnly: 'English only for now.',
    all: 'All', showing: function (n) { return n + (n === 1 ? ' card' : ' cards'); },
    cost: 'Cost', thwart: 'THW', attack: 'ATK', defense: 'DEF', health: 'HP',
    hand: 'Hand', threat: 'Threat', boost: 'Boost', from: 'From'
  },
  fr: {
    cards: 'Cartes', rules: 'Règles',
    searchCards: 'Rechercher des cartes', searchRules: 'Rechercher dans les règles',
    noCards: 'Aucune carte ne correspond à votre recherche.',
    noRules: 'Aucune règle ne correspond à votre recherche.',
    englishOnly: 'En anglais seulement pour le moment.',
    all: 'Tout', showing: function (n) { return n + (n === 1 ? ' carte' : ' cartes'); },
    cost: 'Coût', thwart: 'DÉJ', attack: 'ATQ', defense: 'DÉF', health: 'PV',
    hand: 'Main', threat: 'Menace', boost: 'Boost', from: 'Vient de'
  }
};

var PAGE = 60;               // rows added per scroll batch
var IMAGES = 'https://marvelcdb.com';

// A stored choice wins; failing that, follow the browser. Written out rather
// than squeezed into one expression, where || and === bind in an order that
// quietly turns a stored "en" back into French.
function initialLanguage() {
  var stored = localStorage.getItem('lang');
  if (stored === 'en' || stored === 'fr') { return stored; }
  return (navigator.language || 'en').slice(0, 2) === 'fr' ? 'fr' : 'en';
}

var state = {
  lang: initialLanguage(),
  view: 'cards',
  cards: [],
  rules: [],
  matches: [],
  shown: 0,
  query: '',
  type: null,
  aspect: null
};

/* ---------- helpers ---------- */

// Accent-insensitive, same as the app: "peril" finds "Péril", and a French
// player should not have to reach for the accent key mid game.
function fold(text) {
  return (text || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

function el(tag, className, text) {
  var node = document.createElement(tag);
  if (className) { node.className = className; }
  if (text != null) { node.textContent = text; }
  return node;
}

// The game's icons, which MarvelCDB writes as [energy], [per_hero] and so on.
// Left alone they read as literal square brackets in the middle of a sentence,
// which is how the Android app still shows them.
var GLYPHS = {
  star: '★', unique: '❖',
  energy: 'ENERGY', mental: 'MENTAL', physical: 'PHYSICAL', wild: 'WILD',
  per_hero: 'PER HERO', boost: 'BOOST', crisis: 'CRISIS',
  amplify: 'AMPLIFY', acceleration: 'ACCELERATION', hazard: 'HAZARD',
  attack: 'ATTACK', thwart: 'THWART', defense: 'DEFENSE', cost: 'COST'
};

// MarvelCDB card text carries <b>, <i>, <em> and <hr> and nothing else — I
// counted. Everything else is escaped rather than trusted, because this text
// arrives over the network and lands in innerHTML.
function richText(text) {
  return (text || '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/&lt;(\/?)(b|i|em|hr)\s*\/?&gt;/gi, '<$1$2>')
    .replace(/\[([a-z_]+)\]/g, function (whole, token) {
      if (token === 'star' || token === 'unique') {
        return '<span class="sym">' + GLYPHS[token] + '</span>';
      }
      // Traits get a badge too — there are a dozen of them and they are not
      // worth a table, but a word in a box beats a word in brackets.
      var label = GLYPHS[token] || token.replace(/_/g, ' ').toUpperCase();
      return '<span class="glyph">' + label + '</span>';
    });
}

function t() { return UI[state.lang]; }

/* ---------- data ---------- */

function load() {
  var cards = fetch('data/cards.' + state.lang + '.json').then(function (r) { return r.json(); });
  var rules = fetch('data/rules.json').then(function (r) { return r.json(); });
  return Promise.all([cards, rules]).then(function (both) {
    state.cards = both[0];
    state.cards.forEach(function (card) {
      card._name = fold(card.name);
      card._all = fold([card.name, card.subname, card.traits, card.text, card.card_set_name].join(' '));
    });
    state.rules = (both[1].entries || []).map(function (entry) {
      return {
        term: entry.term,
        body: (state.lang === 'fr' && entry.fr) ? entry.fr : entry.en,
        untranslated: state.lang === 'fr' && !entry.fr,
        _term: fold(entry.term),
        _body: fold(entry.en + ' ' + (entry.fr || ''))
      };
    });
  });
}

/* ---------- cards ---------- */

var TYPES = ['Hero', 'Alter-Ego', 'Ally', 'Event', 'Support', 'Upgrade', 'Resource',
  'Villain', 'Minion', 'Treachery', 'Attachment', 'Side Scheme', 'Main Scheme',
  'Environment', 'Obligation', 'Player Side Scheme'];
var ASPECTS = ['Aggression', 'Justice', 'Leadership', 'Protection', 'Basic', "'Pool"];

function aspectClass(card) {
  var code = (card.faction_code || 'basic').replace(/[^a-z]/g, '');
  return code || 'basic';
}

function buildFilters() {
  var box = document.getElementById('filters');
  box.textContent = '';

  function group(values, key) {
    values.forEach(function (value) {
      var chip = el('button', 'chip', value);
      chip.type = 'button';
      chip.setAttribute('aria-pressed', String(state[key] === value));
      chip.onclick = function () {
        state[key] = state[key] === value ? null : value;
        buildFilters();
        search();
      };
      box.appendChild(chip);
    });
  }
  group(ASPECTS, 'aspect');
  group(TYPES, 'type');
}

function search() {
  var needle = fold(state.query);
  var list = state.cards;

  if (state.aspect) {
    list = list.filter(function (c) { return c.faction_name === state.aspect; });
  }
  if (state.type) {
    list = list.filter(function (c) { return c.type_name === state.type; });
  }
  if (needle) {
    // Prefix on the name first, then anywhere in the name, then the rest of the
    // card. Typing "spider" should open with Spider-Man, not with the eleven
    // cards whose rules text mentions him.
    var starts = [], within = [], rest = [];
    list.forEach(function (c) {
      var at = c._name.indexOf(needle);
      if (at === 0) { starts.push(c); }
      else if (at > 0) { within.push(c); }
      else if (c._all.indexOf(needle) >= 0) { rest.push(c); }
    });
    list = starts.concat(within, rest);
  }

  state.matches = list;
  state.shown = 0;
  document.getElementById('results').textContent = '';
  document.getElementById('count').textContent = t().showing(list.length);
  more();
}

function more() {
  var results = document.getElementById('results');
  if (!state.matches.length) {
    results.appendChild(el('li', 'empty', t().noCards));
    return;
  }
  var end = Math.min(state.shown + PAGE, state.matches.length);
  var frag = document.createDocumentFragment();

  for (var i = state.shown; i < end; i++) {
    var card = state.matches[i];
    var row = el('li', 'row');
    row.appendChild(el('span', 'pip ' + aspectClass(card), (card.name || '?').charAt(0)));

    var meta = el('div', 'meta');
    meta.appendChild(el('div', 'name', card.name + (card.subname ? ' — ' + card.subname : '')));
    meta.appendChild(el('div', 'sub', [card.type_name, card.card_set_name || card.pack_name]
      .filter(Boolean).join(' · ')));
    row.appendChild(meta);

    if (card.cost != null) { row.appendChild(el('span', 'cost', String(card.cost))); }
    row.onclick = openCard.bind(null, card);
    frag.appendChild(row);
  }
  results.appendChild(frag);
  state.shown = end;
}

function openCard(card) {
  var body = document.getElementById('detail-body');
  body.textContent = '';

  body.appendChild(el('h2', null, card.name));
  if (card.subname) { body.appendChild(el('p', 'subname', card.subname)); }
  if (card.traits) { body.appendChild(el('p', 'traits', card.traits)); }

  var stats = el('div', 'stats');
  [[t().cost, card.cost], [t().thwart, card.thwart], [t().attack, card.attack],
   [t().defense, card.defense], [t().health, card.health], [t().hand, card.hand_size],
   [t().threat, card.base_threat != null ? card.base_threat : card.threat],
   [t().boost, card.boost]
  ].forEach(function (pair) {
    if (pair[1] == null) { return; }
    var chip = el('span', 'stat', pair[0] + ' ');
    chip.appendChild(el('b', null, String(pair[1])));
    stats.appendChild(chip);
  });
  if (stats.childNodes.length) { body.appendChild(stats); }

  if (card.text) {
    var text = el('div', 'body');
    text.innerHTML = richText(card.text);
    body.appendChild(text);
  }
  if (card.flavor) { body.appendChild(el('p', 'flavor', card.flavor)); }

  if (card.imagesrc) {
    var img = new Image();
    img.loading = 'lazy';
    img.alt = card.name;
    img.src = IMAGES + card.imagesrc;
    body.appendChild(img);
  }

  if (card.back_text) {
    if (card.back_name) { body.appendChild(el('h2', null, card.back_name)); }
    var back = el('div', 'body');
    back.innerHTML = richText(card.back_text);
    body.appendChild(back);
  }

  body.appendChild(el('p', 'where', t().from + ': ' + [card.pack_name, card.card_set_name]
    .filter(Boolean).join(' · ')));

  document.getElementById('detail').showModal();
}

/* ---------- rules ---------- */

function renderRules() {
  var needle = fold(document.getElementById('rq').value);
  var list = state.rules;

  if (needle) {
    // The term before the body: "stun" should answer with STUNNED, not with the
    // eleven rules that happen to use the word.
    var byTerm = list.filter(function (r) { return r._term.indexOf(needle) >= 0; });
    var byBody = list.filter(function (r) {
      return byTerm.indexOf(r) < 0 && r._body.indexOf(needle) >= 0;
    });
    list = byTerm.concat(byBody);
  }

  var host = document.getElementById('rules');
  host.textContent = '';
  if (!list.length) {
    host.appendChild(el('li', 'empty', t().noRules));
    return;
  }

  var frag = document.createDocumentFragment();
  list.forEach(function (rule) {
    var item = el('li', 'rule short');
    item.appendChild(el('h2', null, rule.term));
    item.appendChild(el('p', null, rule.body));
    item.onclick = function () {
      item.classList.toggle('short');
      if (!item.classList.contains('short') && rule.untranslated && !item.querySelector('.note')) {
        item.appendChild(el('p', 'note', t().englishOnly));
      }
    };
    frag.appendChild(item);
  });
  host.appendChild(frag);
}

/* ---------- shell ---------- */

function show(view) {
  state.view = view;
  document.getElementById('view-cards').hidden = view !== 'cards';
  document.getElementById('view-rules').hidden = view !== 'rules';
  document.getElementById('title').textContent = view === 'cards' ? t().cards : t().rules;
  Array.prototype.forEach.call(document.querySelectorAll('.tabs button'), function (button) {
    button.classList.toggle('on', button.dataset.view === view);
  });
}

function applyLanguage() {
  document.documentElement.lang = state.lang;
  document.getElementById('lang').textContent = state.lang.toUpperCase();
  document.getElementById('q').placeholder = t().searchCards;
  document.getElementById('rq').placeholder = t().searchRules;
  Array.prototype.forEach.call(document.querySelectorAll('.tabs button'), function (button) {
    button.lastChild.textContent = button.dataset.view === 'cards' ? t().cards : t().rules;
  });
  show(state.view);
}

function start() {
  applyLanguage();
  buildFilters();
  return load().then(function () {
    search();
    renderRules();
    document.getElementById('boot').hidden = true;
  });
}

document.getElementById('q').addEventListener('input', function (event) {
  state.query = event.target.value;
  search();
});
document.getElementById('rq').addEventListener('input', renderRules);

Array.prototype.forEach.call(document.querySelectorAll('.tabs button'), function (button) {
  button.onclick = function () { show(button.dataset.view); };
});

document.getElementById('lang').onclick = function () {
  state.lang = state.lang === 'fr' ? 'en' : 'fr';
  localStorage.setItem('lang', state.lang);
  document.getElementById('boot').hidden = false;
  start();
};

document.getElementById('close').onclick = function () {
  document.getElementById('detail').close();
};
document.getElementById('detail').addEventListener('click', function (event) {
  // Tapping the backdrop closes it, which on a phone is what a thumb expects.
  if (event.target.id === 'detail') { this.close(); }
});

// Rows are added as the reader reaches the bottom rather than all at once:
// 4,375 rows in one go is a locked-up phone.
new IntersectionObserver(function (entries) {
  if (entries[0].isIntersecting && state.shown < state.matches.length) { more(); }
}).observe(document.getElementById('sentinel'));

if ('serviceWorker' in navigator) {
  window.addEventListener('load', function () {
    navigator.serviceWorker.register('sw.js');
  });
}

start();
