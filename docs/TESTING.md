# Release checks

Do these checks with the exact APK of the draft release. Record the source commit, the APK checksum, the device model, the Android version, and the results.

## Build checks

Run this command. The release workflow also runs the checks for the release build.

```sh
./gradlew :app:behaviorChecks :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease
```

The behavior checks run on the Java virtual machine. They examine the idle timer, the app profiles, the app selection in setup, and the animation settling. They do not examine a TV device.

## APK identity

- The checksum of the downloaded `telum.apk` agrees with `SHA256SUMS`.
- `apksigner verify --print-certs telum.apk` shows the fingerprint of the release certificate.
- The package is `dev.telum.launcher`. The app name is Telum. The banner shows the Telum dart.

## First start

- On a new installation, the intro plays. OK, Back, or an arrow skips it. The volume buttons do not skip it. With **Motion** set to **Reduced**, the intro is shorter.
- A device without OpenGL ES 3.0 starts setup without the intro.
- The welcome screen shows **Next** after four seconds. Before that, buttons do nothing.
- **Your apps** lists the installed apps. Select apps, arrange them, and cancel a move with Back. Restart Telum. The selection stays. With no app selected, **Continue** does not operate.
- **Your style** shows **Recommended** on the left and **Light** on the right. **Recommended** is selected. No location is set. Each button label is on one line.
- **Light** turns off the optional integrations. **Recommended** turns them on only when setup is complete.
- Examine each row of **Customize**. Change the font on the last row.
- Examine Back, an interrupted setup, refused permissions, and the return from notification settings. A refused permission or no weather city does not stop the setup.
- In **Final touches**, allow TV listings access. Each selected app that shares rows offers its own row choice.
- In the city dialog, the Enter key closes the keyboard. With an empty field, it moves to **Cancel**.
- The closing screen shows **Enter Telum** after four seconds. Home then shows.

## Home question

- After setup, Telum asks **Make Telum your Home?** if the Home button does not open Telum.
- **Not now** closes the question. **Control centre** › **Telum** shows **Make Telum your Home**.
- **Make Telum Home** opens the Android Home dialog. Select Telum.
- On SHIELD TV with Android TV Home enabled, Telum shows **Android TV Home still opens first**.
- After you disable Android TV Home, the Home button opens Telum. Only one Telum screen is open.
- After a restart, the Home button opens Telum. The intro plays one time.
- When the Home button opens Telum, setup does not ask the question.

## Remote and lifecycle

- Move to each tile. Open an app and return.
- Hold OK to move a tile. Hide an app and show it again.
- Open and close **Control centre**. Open **All settings**.
- Examine Home, Back, the media buttons, the volume, and voice search with the actual remote.
- Leave Home without input. Make sure that Telum does not draw continuously and writes no watchdog log. Examine again after you return from an app and after the screensaver.

## Setup again

- Select **Run setup again** in **Control centre**. Change the apps, the order, the features, the rows, and the city.
- Select **Exit setup**. The live settings do not change.
- Do it again and select **Save and Enter**. All the changes apply together.
- Android permission changes stay. Setup does not undo them.

## Music idle display

- Give notification access and start music. Do not touch the remote for 60 seconds. The apps, the controls, and the clock go away. Dim artwork shows.
- Push an arrow, OK, or Back. The first push shows Home again and opens no app. The next push moves normally.
- Examine play, pause, skip, and volume. Pause or the end of the session shows Home again. A dialog or another app stops the idle timer.
- Examine missing artwork, late artwork, a track change, and new artwork for the same track. Turn artist photos off and on.
- Wait through an artwork move and the screensaver timeout. Telum must not keep the display on.
- Set **Music idle display** to off with music controls on. The dim display does not show.

## Live Activities and apps

- Examine the Netflix tile without sign-in. Then examine the rows that Netflix shares with a signed-in member account. Record these as different results.
- Examine known app colors and an app that has no profile. An app without an icon still has a usable tile.
- Allow TV listings. Rows with progress show. Rows that you select in **What each app shows** show. With no rows, the normal app view shows.
- Turn off rows during a query or a download. The results do not come back.

## Privacy and network

- Examine the app without a network connection.
- Refuse each permission. Remove notification access after you gave it.
- Examine the tracked files of the repository. They must not contain account screenshots, private logs, keys, or personal paths.

## Limits of these checks

An emulator media session tests the Android integration only. It does not test Spotify or Netflix. A passing build or a simulated session does not prove support for real accounts, and does not prove protection against burn-in.
