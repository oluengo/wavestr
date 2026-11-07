// PWA opcional: guarda la app (HTML, JS, CSS, iconos) para abrirla rápido y sin conexión.
// Lo demás (relays, radios, carátulas) va siempre a la red.
const CACHE = 'wavestr-v1'
self.addEventListener('install', e => { self.skipWaiting(); e.waitUntil(caches.open(CACHE).then(c => c.addAll(['./', './index.html', './icon.svg', './icon-192.png', './manifest.webmanifest']))) })
self.addEventListener('activate', e => e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())))
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url)
  if (e.request.method !== 'GET' || u.origin !== location.origin) return
  // Red primero (para tener siempre la última versión); si falla, la copia guardada.
  e.respondWith(fetch(e.request).then(r => { if (r.ok) { const copy = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)) } return r }).catch(() => caches.match(e.request).then(r => r || caches.match('./index.html'))))
})
