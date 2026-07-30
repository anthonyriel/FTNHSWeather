const CACHE_NAME = 'ftnhs-weather-cache-v1';
const urlsToCache = [
    '/',
    '/forecast',
    '/history',
    '/analytics',
    '/about',
    '/manifest.json'
];

// Install Service Worker and cache core static assets
self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => {
                return cache.addAll(urlsToCache);
            })
    );
});

// Fetch cached assets when offline
self.addEventListener('fetch', event => {
    event.respondWith(
        caches.match(event.request)
            .then(response => {
                // Cache hit - return response, otherwise fetch from network
                if (response) {
                    return response;
                }
                return fetch(event.request);
            })
    );
});