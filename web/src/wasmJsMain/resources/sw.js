// App-shell files (small, name-stable, content changes every deploy) are
// NETWORK-FIRST so a fresh version lands on the next launch while online.
// Everything else (hashed wasm, art, data) is cache-first with background
// refresh, keeping the grimoire instant and fully offline-capable.
// __BUILD__ is stamped by CI, invalidating old caches on activate.
const VERSION = '__BUILD__';
const CACHE = 'grimoire-' + VERSION;

const isShellRequest = (request) => {
  if (request.mode === 'navigate') return true;
  const path = new URL(request.url).pathname;
  return path.endsWith('/') ||
    path.endsWith('index.html') ||
    path.endsWith('grimoire.js') ||
    path.endsWith('manifest.webmanifest');
};

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  event.respondWith(
    caches.open(CACHE).then((cache) => {
      if (isShellRequest(request)) {
        return fetch(request)
          .then((response) => {
            if (response && response.ok) cache.put(request, response.clone());
            return response;
          })
          .catch(() => cache.match(request));
      }
      return cache.match(request).then((hit) => {
        const network = fetch(request)
          .then((response) => {
            if (response && response.ok) cache.put(request, response.clone());
            return response;
          })
          .catch(() => hit);
        return hit || network;
      });
    })
  );
});
