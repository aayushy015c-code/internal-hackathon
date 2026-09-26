// Needed so Chrome lets you "install" the site as an app.
// Network first: always get the newest files, and only use the saved copy
// if the server is down. (Cache first made our code changes not show up.)
const CACHE = "silent-signal-v2";

self.addEventListener("install", () => self.skipWaiting());

self.addEventListener("activate", (event) => {
  // delete old caches
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))));
});

self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);
  // only our own pages, never the API calls
  if (event.request.method !== "GET" || url.origin !== location.origin) return;

  event.respondWith(
    fetch(event.request)
      .then((response) => {
        const copy = response.clone();
        caches.open(CACHE).then((cache) => cache.put(event.request, copy));
        return response;
      })
      .catch(() => caches.match(event.request))
  );
});
