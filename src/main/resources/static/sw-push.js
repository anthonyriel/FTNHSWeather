// Listen for incoming push notifications from the server
self.addEventListener('push', function(event) {
    let data = { title: 'FTNHS Weather Hub', body: 'New weather or suspension update available.' };
    
    if (event.data) {
        try {
            data = event.data.json();
        } catch (e) {
            data.body = event.data.text();
        }
    }

    const options = {
        body: data.body,
        icon: 'https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.1/icons/cloud-sun-fill.svg',
        badge: 'https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.1/icons/cloud-sun-fill.svg',
        vibrate: [200, 100, 200],
        data: {
            url: data.url || '/'
        }
    };

    event.waitUntil(
        self.registration.showNotification(data.title, options)
    );
});

// Handle notification click events (opens/focuses the app)
self.addEventListener('notificationclick', function(event) {
    event.notification.close();
    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then(windowClients => {
            for (let client of windowClients) {
                if (client.url === event.notification.data.url && 'focus' in client) {
                    return client.focus();
                }
            }
            if (clients.openWindow) {
                return clients.openWindow(event.notification.data.url);
            }
        })
    );
});