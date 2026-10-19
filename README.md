# Telum

A remote-first home screen for Android TV 10 and newer. Telum puts your apps, music and the rows your apps publish on one screen.

Telum is named after Gamma Sagittae, the Dart. See the [star-name entry](https://ase.exopla.net/index.php/Telum).

## Features

- App tiles with backgrounds that follow the selected app. Installed banners and icons supply the artwork; common TV services also have defined color palettes.
- Hold OK to move or hide an app. Hidden apps remain available in settings.
- Music controls through Android media sessions. After 60 seconds without interaction during playback, the home screen collapses into dim artwork. Press a navigation key to return.
- Optional artist photos, weather, Continue Watching and selectable rows from apps.
- A custom Canvas interface with no advertising, analytics, account or bundled streaming catalogue.

Music idle mode supports Spotify, YouTube Music, Amazon Music and TuneIn. Video and unknown app sessions retain ordinary media controls without starting this mode. The mode reduces static UI and shifts the artwork periodically. It cannot guarantee protection against burn-in. It respects Android's screensaver and display timeout.

## Build

Install JDK 17 or 21 and Android SDK platform 35 with build tools 35.0.0. Set `ANDROID_HOME` to your SDK folder, or create a local `local.properties` containing `sdk.dir`. Do not commit that file.

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

On Linux or macOS:

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

The first build downloads Gradle and dependencies. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Release builds are unsigned unless you supply your own signing credentials. See [releases](docs/RELEASING.md).

## Install and configure

1. Enable developer options and network or USB debugging on your TV. Authorize your computer.
2. Run `adb devices` and use your TV's serial explicitly in subsequent commands.
3. Run `adb -s YOUR_TV_SERIAL install app/build/outputs/apk/debug/app-debug.apk`.
4. Open Telum from your current launcher and test navigation before selecting it as the default Home app in your TV's settings.
5. In Control center > Telum, enable Music controls and grant notification access if you want media integration. Enable Show what you're watching and grant TV listings permission for available app rows.

Some TV firmware does not offer a default Home selector. Firmware-specific launcher replacement is outside this installation guide. Keep a working way to reach system settings and restore your previous home screen.

Telum uses `dev.telum.launcher`. Builds under the old development package are separate apps: settings, notification access and the Home role do not migrate automatically. Installing Telum does not replace or remove that app.

## App compatibility

Any installed app with a TV launcher activity can appear. Netflix's tile and color background do not require membership. Personalized artwork and recommendations require data published by the installed app, and may depend on sign-in, membership, region and firmware. Telum does not retrieve your Netflix account history.

See [app support](docs/APPS.md) for the package coverage and limits. Missing recommendations leave the ordinary app background usable.

## Privacy and credits

Weather and artist photos are off by default. Their providers receive requests only when you enable the relevant feature. See [privacy](docs/PRIVACY.md) and [third-party notices](THIRD_PARTY_NOTICES.md).

## Development status

This is an early release. The repository includes build CI and a [manual test checklist](docs/TESTING.md). Emulator checks do not establish compatibility with every TV, streaming account or firmware. Subscription-dependent integrations need testing with the relevant account.

## License

Telum's original source is licensed under [GPL-3.0-only](LICENSE). Bundled fonts and the Gradle wrapper retain their own licenses; see [third-party notices](THIRD_PARTY_NOTICES.md). Service names belong to their owners. Telum is an independent launcher.
