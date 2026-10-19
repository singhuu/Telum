# Privacy

Telum has no account, analytics or telemetry service. Preferences remain in the app's local storage. Android backup is disabled.

## Optional network features

Weather is off until enabled. Looking up a city sends the search text to Open-Meteo's geocoding service. Forecast requests send the selected coordinates. Telum does not request device location permission. The weather service also receives your IP address.

Artist photos are off until enabled. Telum sends the current artist's name to TheAudioDB, then downloads the returned image. This shares music-related information and your IP address with the provider and image host. Artwork lookups can fail or return no match; album art is the fallback.

Programme posters and media artwork may use HTTPS URLs supplied by other apps. Fetching those URLs discloses the image request and your IP address to their hosts. Telum also supports artwork supplied locally by an installed app. Downloaded images are cached in app-private storage.

## Android permissions

- Internet access supports the optional services and remote artwork.
- Read TV listings allows Telum to read the Watch Next and preview rows other apps expose. It does not grant access to private streaming accounts.
- Notification access allows discovery and control of active media sessions. The listener does not process or store notification contents. Android grants broad access through this permission, so enable it only if you want music controls.

Turn the integrations off in Telum settings, or revoke their permissions in Android settings. Clear Telum's storage or uninstall it to remove its preferences and caches.

The frame watchdog writes a capped diagnostic log under `Android/media/dev.telum.launcher/watchdog.log`. It records timing, state labels and stack traces, not track titles. It is readable through ADB. Inspect logs before sharing them.
