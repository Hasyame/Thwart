'use strict';

/* What makes this a companion rather than a website: after one visit it works
   with the phone in aeroplane mode.
 *
 * The shell and the card data are precached, because they are the whole app and
 * they are small (0.3 MB gzipped per language). Card pictures are not — that is
 * hundreds of megabytes — so they are kept as they are seen, which in practice
 * means the cards you look up are the cards you have. */

var VERSION = 'thwart-v1';
var SHELL = VERSION + '-shell';
var IMAGES = VERSION + '-images';

var PRECACHE = [
  './',
  'index.html',
  'styles.css',
  'app.js',
  'manifest.webmanifest',
  'icon-192.png',
  'data/cards.en.json',
  'data/cards.fr.json',
  'data/rules.json'
];

self.addEventListener('install', function (event) {
  event.waitUntil(
    caches.open(SHELL).then(function (cache) { return cache.addAll(PRECACHE); })
      .then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener('activate', function (event) {
  event.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.map(function (key) {
        // Anything from an older VERSION goes, or a stale catalogue outlives the
        // deploy that replaced it.
        return key.indexOf(VERSION) === 0 ? null : caches.delete(key);
      }));
    }).then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (event) {
  var request = event.request;
  if (request.method !== 'GET') { return; }

  var url = new URL(request.url);

  // Card pictures: from the cache if seen before, otherwise fetched and kept.
  // no-cors gives an opaque response, which cannot be read but renders in an
  // <img> perfectly well — which is all this needs.
  if (url.hostname.indexOf('marvelcdb.com') >= 0) {
    event.respondWith(
      caches.open(IMAGES).then(function (cache) {
        return cache.match(request).then(function (hit) {
          if (hit) { return hit; }
          return fetch(request, { mode: 'no-cors' }).then(function (response) {
            cache.put(request, response.clone());
            return response;
          }).catch(function () { return hit || Response.error(); });
        });
      })
    );
    return;
  }

  if (url.origin !== self.location.origin) { return; }

  // Cache first for our own files. They only change when a deploy changes
  // VERSION, and answering from disk is what makes this usable at a table.
  event.respondWith(
    caches.match(request).then(function (hit) {
      return hit || fetch(request).then(function (response) {
        if (response.ok) {
          var copy = response.clone();
          caches.open(SHELL).then(function (cache) { cache.put(request, copy); });
        }
        return response;
      }).catch(function () {
        return caches.match('index.html');
      });
    })
  );
});
