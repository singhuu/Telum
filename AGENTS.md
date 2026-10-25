# Telum repository instructions

## Installation requests

If the user asks you to install Telum, read [the agent installation guide](docs/INSTALL-AGENT.md) first. Follow its rules and stages.

Use [the installation guide](docs/INSTALL.md) for commands and correct results. Use [the troubleshooting guide](docs/TROUBLESHOOTING.md) for errors.

Access to this repository is not access to the TV device of the user. These instructions apply to all agents. They need no special tool or plugin.

## Project facts

- The app package is `dev.telum.launcher`.
- The main activity is `dev.telum.launcher/.MainActivity`.
- The minimum version is Android 10, API 29.
- NVIDIA SHIELD TV is the primary target. Its original Home app is `com.google.android.tvlauncher`.
- Releases contain `telum.apk` and `SHA256SUMS`. The [release workflow](.github/workflows/release.yml) builds and signs them.
- An APK that has no signature cannot install.

## Source changes

Read [contributing](CONTRIBUTING.md) before you change the source. Read [releasing](docs/RELEASING.md) before you prepare a release.

Run `./gradlew :app:behaviorChecks :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease` before you submit a change.

Keep performance statements within [the published evidence](docs/PERFORMANCE.md). Do not describe an emulator test as a test on a TV device.

Write documents in ASD-STE100 Simplified Technical English. See [contributing](CONTRIBUTING.md).
