# Install Telum with an AI assistant

This guide is for an assistant that helps a person install Telum. It applies to any model or provider.

Use the [manual guide](INSTALL.md) for commands, expected results, and recovery. Do not invent download addresses, menu names, device values, or test results.

## Start the conversation

Explain that Telum replaces the home screen. It does not replace the TV operating system or the streaming apps.

Ask for missing information in one short group:

- What is the TV box model and Android version?
- Is a Windows, macOS, or Linux computer available?
- Can the user operate the TV remote during installation?
- Does the user want to test Telum first or also make it the Home app?

Use information that the user already supplied. Do not ask for it again.

Check whether your tools run on the user's computer. A cloud terminal usually cannot reach the user's home network. If local access is unavailable, give the user one command at a time. Ask for the result before the next dependent command.

This procedure needs a local computer. If none is available, explain that requirement before continuing. Do not replace it with an unverified remote-access service.

## Installation stages

| Stage | Required action | Evidence required before the next stage |
| --- | --- | --- |
| 1. Check the device | Follow manual step 1. | The model and Android version are known. Android is version 10 or newer. |
| 2. Select the APK | Follow manual step 2. | The source, filename, version, and build type are known. The checksum matches when one is published. |
| 3. Connect | Follow manual steps 3 and 4. | One selected serial has state `device`. The reported model matches the user's TV. |
| 4. Record recovery | Follow manual step 5. | The previous Home component and active user number are recorded. Settings opens on the TV. |
| 5. Install and test | Follow manual step 6. | Installation reports `Success`. The user confirms that Telum opens and remote navigation works. |
| 6. Select Home | Follow manual step 7 only if requested. | Home opens Telum after app exit and after a restart. |
| 7. Configure | Follow manual step 8. | The user chose optional features and confirmed their results. |
| 8. Finish | Follow manual step 9. | The user has recovery instructions and a clear installation status. |

## Select a real artifact

Open the Releases page of the repository that the user supplied. Inspect the release notes and assets. Prefer a stable release with a signed APK and a published SHA-256 checksum.

Do not assume that a release exists. Do not install a source archive, unsigned APK, or unrelated APK from a search result.

If a signed release is unavailable, explain the available choices. The user can wait for a release or build a local test APK. Use the [source build procedure](INSTALL.md#build-a-test-apk) if the user chooses the test build. Explain its update limitations before installation.

For an existing Telum installation, record its version before an update. Use the same signing identity. A signature error requires investigation, not automatic removal of the installed app.

## Guide each action

1. State the next action and its purpose.
2. Substitute verified values into the command.
3. Use the command syntax for the user's operating system.
4. Select the TV with `adb -s` for each device command.
5. Run the action locally, or ask the user to run it.
6. Check the output.
7. Ask the user to confirm visible TV changes when needed.

Do not send a long script that changes several device settings without checks. Do not claim that a command ran when you only supplied it.

If a command fails, use the [troubleshooting table](INSTALL.md#troubleshooting). Stop dependent changes until the cause is known. Do not repeatedly try the same failed command.

## Home selection and recovery

Installing Telum does not require removal of the stock launcher. Test it as an ordinary app first.

If the user has not requested Home selection, ask before that change. Explain how to restore the previous Home app. The normal Home procedure preserves the stock launcher.

The optional SHIELD procedure can disable one verified stock Home package. Read its conditions before use. Explain the effect and obtain agreement for that specific change. Keep the ADB connection and restore command available during the test.

Do not disable recommendation services, remote services, voice services, or unrelated packages. Do not root the device, unlock its bootloader, or change its firmware for this installation.

If device software blocks Home selection, report the limit. A working app installation is still useful for testing. Do not describe it as a completed Home replacement.

## Optional features

Use the [feature setup table](INSTALL.md#8-configure-optional-features). The user must choose each optional permission or network feature.

Do not request account passwords or streaming tokens. The user signs in through the streaming app. Missing recommendations do not prove that installation failed.

## Completion report

Report these facts in plain language:

- The TV model and Android version.
- The APK version, source, and build type.
- Whether Telum opened and passed the remote checks.
- Whether Telum became the Home app and passed the restart check.
- Which optional features the user tested.
- How to restore the previous Home app.
- Any failed or incomplete checks.

Keep local addresses and device serials out of public issues. Do not retain pairing codes or account information.

These instructions support different assistants. They do not establish that every model will follow them correctly.
