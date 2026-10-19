# Release checks

Run `:app:behaviorChecks`, `:app:assembleDebug`, `:app:assembleRelease`, `:app:lintDebug` and `:app:lintRelease`. The behavior checks execute the idle deadline, package registry and animation settling on the JVM. Record the source commit, device/firmware and APK hash for each device test.

## Remote and lifecycle

- Launch as an ordinary app, navigate every tile, open an app and return.
- Hold OK to rearrange; hide and restore an app. Open and close Control center.
- Verify Home, Back, media controls, volume and voice behavior on the actual remote.
- Leave Home idle and check for sustained rendering or a watchdog log. Check again after returning from an app and after screensaver wake.

## Music idle display

- Grant notification access and start music. Return to Telum without pressing a key for 60 seconds. Apps, controls and the clock should disappear into a dim artwork display.
- Press an arrow, OK or Back. The first press should restore Home without launching anything. A subsequent press should navigate normally.
- Check play/pause, skip and volume. Pausing or ending the session should restore Home. Backgrounding Telum or opening a dialog must cancel the idle timer.
- Check missing artwork, delayed artwork, a track change and a same-track artwork update. Toggle artist photos off and on.
- Wait through an artwork shift and through the TV's own screensaver timeout. Telum must not hold the display awake.

## App content

- Test Netflix's tile without sign-in. Then test any published rows with an authorized member account. Record these as separate results.
- Test known app palettes and an unlisted app. Failed/missing icons must still leave a usable tile.
- Enable TV listings. Confirm rows carrying progress and manually selected recommendation rows. No exposed rows should leave the normal app hero.
- Disable rows during a query or download and verify results do not reappear.

## Privacy and release

- A fresh install should have weather and artist photos off and no preset location.
- Check offline behavior, denied permissions and revoked notification access.
- Inspect the APK label, banner, package name and signing certificate.
- Inspect all tracked public files and fresh repository history. Do not include account screenshots, private logs, keystores or developer-specific paths.

Emulator media publishers test the Android integration, not Spotify or Netflix service behavior. A passing build or a simulated session does not establish real-account support or burn-in prevention.
