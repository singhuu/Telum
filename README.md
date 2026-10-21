# Telum

Telum is a home screen for Android TV. It is optimized for NVIDIA SHIELD Pro and designed for other Android TV boxes.

Android 10 or newer is required. Device software controls installation and Home selection. Compatibility with every box is not confirmed.

Telum takes its name from a historical name for Gamma Sagittae. See the [star-name entry](https://ase.exopla.net/index.php/Telum).

## Install with an AI assistant, recommended

Give your AI assistant this repository link and this request:

> Help me install Telum on my TV: [paste the GitHub repository link]. Read the README and docs/INSTALL-AGENT.md. Guide me one step at a time. Check my TV model before you give commands. Keep a way to restore my current home screen.

The [agent installation guide](docs/INSTALL-AGENT.md) works without a specific AI provider or tool. It supports assistants with local tools and assistants that only give instructions. An assistant needs access to these documents to use them. If it cannot read the repository, give it the guide text.

The assistant checks your TV, finds a suitable APK, and guides installation. It checks each result before the next step. You keep control of TV prompts and Home selection.

An APK is an Android installation file. A signed release APK is the preferred choice. If no signed release exists, the guide explains how to build a test APK. Source ZIP files and unsigned APK files cannot install the app.

For installation without an assistant, use the [manual installation guide](docs/INSTALL.md).

## Why choose Telum instead of the stock home screen?

Telum gives you control of the home screen content and layout.

| Benefit | Telum behavior |
| --- | --- |
| Your apps come first | Large app tiles support remote navigation. Hold OK to move or hide a tile. |
| No added advertisements | Telum adds no sponsored rows, analytics, or account requirement. Streaming apps control their own advertisements. |
| Content is optional | You choose whether to show app content, music controls, weather, and artist photos. |
| Backgrounds follow your apps | Common services have defined colors. Other apps use their artwork or a fallback color. |
| A dim screen for music | After 60 seconds without input, music playback can replace the tiles with dim artwork. |
| Animations stop when settled | Telum stops drawing settled animations. The tests below record the observed behavior. |

The current Telum build drew one frame during a 45-second paused-media emulator check. This is an idle drawing result, not a speed comparison with the stock launcher. See [test results and limits](docs/PERFORMANCE.md).

Music idle mode supports Spotify, YouTube Music, Amazon Music, and TuneIn package profiles. It moves the artwork periodically and preserves the TV screensaver settings. Press an arrow, OK, or Back to restore the home screen. The mode reduces static screen content. It does not guarantee protection against screen burn-in.

## Device and app support

NVIDIA SHIELD Pro is the main optimization target. Other Android TV and Google TV boxes need Android 10 or newer and permission to install APK files. Home replacement also needs support from the device software. Phone-style Android boxes and modified firmware need separate checks.

An installed app with a TV launcher activity can appear in Telum. Netflix's tile and background colors work without a subscription. App content and recommendations depend on the installed app, account, region, and device software. Telum cannot supply content that an app does not share.

See [app support](docs/APPS.md) for the supported profiles and content limits.

## Build from source

Use the [build procedure](docs/INSTALL.md#build-a-test-apk) to create a local test APK. Maintainers must follow the [release procedure](docs/RELEASING.md) for public APK files.

Telum uses package `dev.telum.launcher`. Earlier packages install as separate apps. Their settings, permissions, and Home selection do not transfer to Telum.

## Privacy

Weather and artist photos are optional. Their providers receive requests only when you enable the related feature. See [privacy](docs/PRIVACY.md) and [third-party notices](THIRD_PARTY_NOTICES.md).

## Project status

This is an early release. The repository includes build checks and a [device test checklist](docs/TESTING.md). The current Telum build has emulator test results. Earlier development builds have physical SHIELD results. Neither establishes support for every device or streaming account.

## License

Telum's original source uses [GPL-3.0-only](LICENSE). Bundled fonts and the Gradle wrapper retain their own licenses. See [third-party notices](THIRD_PARTY_NOTICES.md).

Telum is an independent project. NVIDIA and the named streaming services do not endorse it.
