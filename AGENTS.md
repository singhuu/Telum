# Telum repository instructions

## Installation requests

If the user asks to install Telum, read [the agent installation guide](docs/INSTALL-AGENT.md) first.

Use [the manual guide](docs/INSTALL.md) as the command reference. Follow its checks in order. Do not treat repository access as access to the user's TV.

These instructions apply to any assistant. They require no provider-specific tool or plugin. If you cannot open a linked document, ask the user for its text.

## Project facts

- The app package is `dev.telum.launcher`.
- The main activity is `dev.telum.launcher/.MainActivity`.
- Android 10, API 29, is the minimum version.
- NVIDIA SHIELD Pro is the main optimization target.
- Other Android TV boxes need device-specific installation and Home checks.
- A signed release APK is preferred. An unsigned APK cannot install.

## Source changes

Read [contribution instructions](CONTRIBUTING.md) before source changes. Read [release instructions](docs/RELEASING.md) before preparing an APK for distribution.

Keep performance statements within [the published evidence](docs/PERFORMANCE.md). Do not describe emulator tests as physical TV tests.
