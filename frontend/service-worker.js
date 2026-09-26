// service-worker.js
// -----------------
// The minimum needed to make the app installable as a PWA: a service
// worker that's registered (see index.html) and has a `fetch` handler.
// We use a simple cache-first strategy for the app's own files, so the
// shell still loads if the dev server is briefly unreachable - this is
// NOT an offline-first app (it needs both backends running to do
// anything useful), just enough caching to satisfy installability and
// survive a flaky localhost server for a second.

const CACHE_NAME = "silent-signal-v1";
const APP_SHELL = [
  "index.html",
  "call.html",
  "dashboard.html",
  "settings.html",
  "disguise.html",
  "css/styles.css",
  "js/api.js",
  "js/recorder.js",
  "js/call.js",
  "js/dashboard.js",
  "js/settings.js",
  "js/disguise.js",
  "manifest.json",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL))
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  // Only handle simple GETs for our own app-shell files. Everything else
  // (API calls to :8000/:8080) goes straight to the network, untouched -
  // we never want to accidentally cache a stale distress score or alert.
  if (event.request.method !== "GET") return;

  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;

  event.respondWith(
    caches.match(event.request).then((cached) => cached || fetch(event.request))
  );
});
