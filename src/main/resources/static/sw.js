const CACHE_NAME = 'flowcast-v4-dock';
const STATIC_ASSETS = [
  '/',
  '/traffic',
  '/css/app.css',
  '/js/app.js',
  '/js/animated-top-dock.bundle.js',
  '/js/animated-top-dock.bundle.css',
  '/manifest.webmanifest',
  '/icon.svg'
];

self.addEventListener('install', event => {
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.map(k => (k !== CACHE_NAME ? caches.delete(k) : null)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);

  // Strictly network-only for /api/ to prevent stale traffic data
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(
      fetch(event.request).catch(() =>
        new Response(
          JSON.stringify({
            status: 'OFFLINE',
            source: 'FlowCast ServiceWorker',
            retrievedAt: new Date().toISOString(),
            data: null,
            reason: 'You are currently offline. Live traffic and route calculations require an active network connection.'
          }),
          { headers: { 'Content-Type': 'application/json' }, status: 503 }
        )
      )
    );
    return;
  }

  // Network-first for HTML pages and app assets so mobile styles and updates apply instantly
  if (url.origin === self.location.origin) {
    event.respondWith(
      fetch(event.request)
        .then(response => {
          if (response && response.status === 200) {
            const responseClone = response.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(event.request, responseClone));
          }
          return response;
        })
        .catch(() => caches.match(event.request))
    );
  }
});
