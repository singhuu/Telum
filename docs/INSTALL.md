# Install Telum manually

For guided installation, use the [agent guide](INSTALL-AGENT.md). This manual is the command reference for both methods.

Telum is optimized for NVIDIA SHIELD Pro. Other Android TV boxes can use the same procedure when their device software permits it.

## 1. Check the requirements

You need:

- An Android TV device with Android 10 or newer, API 29 or newer.
- A working TV remote and access to the TV settings.
- A Windows, macOS, or Linux computer.
- A private network connection to the TV, or a supported USB debugging connection.
- A signed Telum APK, or the tools to build a local test APK.

On the TV, open **Settings > Device Preferences > About** or **Settings > System > About**. Record the model and Android version. Menu names depend on the device software.

Android version and SHIELD Experience version are different numbers. Use the Android version for this check.

If the device uses Android 9 or older, stop. Telum requires Android 10 APIs. Do not change firmware as part of this procedure.

## 2. Get an APK

1. Open this repository on GitHub.
2. Open **Releases**.
3. Read the release notes for device limits.
4. Download the signed APK from that release, if available.
5. Record its version, filename, and source address.
6. Compare its SHA-256 checksum with the release checksum, if provided.

Replace `APK_PATH` below with the full APK path. Keep the quotation marks around paths that contain spaces.

Windows PowerShell:

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath "APK_PATH"
```

macOS:

```sh
shasum -a 256 "APK_PATH"
```

Linux:

```sh
sha256sum "APK_PATH"
```

The checksum must match exactly, apart from letter case. If it differs, stop. A matching checksum confirms file consistency with the published value. It does not establish that the publisher is trustworthy.

If no signed release exists, use [Build a test APK](#build-a-test-apk) or wait for a release. GitHub source archives are source files. CI debug APKs are test builds. CI unsigned release APKs cannot install.

## 3. Prepare the computer and TV

1. Download [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) for your computer.
2. Extract the download.
3. Open a terminal in the extracted `platform-tools` directory.
4. Add that directory to the current terminal's command path.

Windows PowerShell:

```powershell
$env:Path = "$($PWD.Path);$env:Path"
```

macOS or Linux:

```sh
export PATH="$PWD:$PATH"
```

5. Check the tool.

```sh
adb version
```

The output must identify Android Debug Bridge. These path changes apply only to the current terminal.

6. On the TV's **About** screen, select **Build** seven times.
7. Open **Developer options**.
8. Enable the debugging method available on your device.

On SHIELD, look for **Network debugging**. Other devices can offer **Wireless debugging** or **USB debugging**. Use the [Android developer-options instructions](https://developer.android.com/studio/debug/dev-options) if the menu differs.

Keep network debugging on a private network. Do not forward its port through your router.

## 4. Connect and identify the TV

Use one connection method. For network connections, put the computer and TV on the same local network.

| Method | Action |
| --- | --- |
| Network debugging, including supported SHIELD firmware | Use `adb connect TV_IP:CONNECT_PORT`. Use the address and port shown by the TV. |
| Wireless debugging with a pairing code | Use `adb pair TV_IP:PAIR_PORT`. Enter the code at the local prompt. Then use `adb connect TV_IP:CONNECT_PORT` if needed. |
| USB debugging | Connect a data cable to the device's supported debugging port. Follow the manufacturer's cable instructions. |

Pairing and connection ports can differ. Do not assume port 5555 for every TV. Do not share the pairing code in a public issue.

1. Accept the computer authorization prompt on the TV.
2. List the connected devices.

```sh
adb devices -l
```

3. Find the TV entry with state `device`.
4. Record its exact serial from the first column.

In all remaining commands, replace `TV_SERIAL` with that value. Do not use an emulator serial for your TV.

5. Check the connected device.

```sh
adb -s "TV_SERIAL" shell getprop ro.product.model
adb -s "TV_SERIAL" shell getprop ro.build.version.release
adb -s "TV_SERIAL" shell getprop ro.build.version.sdk
```

The model must match your TV box. The SDK number must be at least `29`. If the state is `unauthorized` or `offline`, use [Troubleshooting](#troubleshooting).

## 5. Record a recovery path

1. Get the active Android user number.

```sh
adb -s "TV_SERIAL" shell am get-current-user
```

2. Replace `USER_ID` in later commands with that number.
3. Record the current Home component.

```sh
adb -s "TV_SERIAL" shell cmd package resolve-activity --brief --user USER_ID -a android.intent.action.MAIN -c android.intent.category.HOME
```

The component has the form `package/activity`. Save the exact value as `PREVIOUS_HOME`. The package is the part before `/`. Save it as `PREVIOUS_PACKAGE`.

If the result is a system chooser or no activity, identify the existing Home app before replacement. Do not use the chooser as a recovery component.

4. Open system settings through ADB.

```sh
adb -s "TV_SERIAL" shell am start --user USER_ID -a android.settings.SETTINGS
```

5. Confirm that settings are visible on the TV.

6. Check a remote shortcut that opens system settings without Telum.

On a SHIELD remote with a Settings button, try that button. Some button assignments require a long press. Confirm the behavior on your remote. If no independent shortcut works, retain ADB access during the Home trial.

Keep this terminal and the recovery values available until the Home tests finish.

## 6. Install and test Telum

For an update, record the installed version first:

```sh
adb -s "TV_SERIAL" shell dumpsys package dev.telum.launcher
```

The output includes `versionName` and `versionCode` for an installed package. A fresh installation has no existing package record.

1. Install the selected APK.

```sh
adb -s "TV_SERIAL" install -r --user USER_ID "APK_PATH"
```

The expected result is `Success`. The `-r` option preserves existing app data when the update is compatible.

2. Open Telum as an ordinary app.

```sh
adb -s "TV_SERIAL" shell am start --user USER_ID -n dev.telum.launcher/.MainActivity
```

3. Confirm that the TV shows Telum.
4. Complete any setup screens that this build shows.
5. Move between the app tiles with the remote.
6. Open an app.
7. Open Telum again from the existing launcher.
8. Open Telum's Control center.
9. Confirm that system settings remain accessible.

Before Home selection, the Home button can still open the previous launcher. That is expected.

## 7. Select Telum as Home, optional

Do this only after the previous tests pass. Keep the previous launcher installed.

1. Open the TV's default-app settings, if available.
2. Select Telum as the Home app.

Some devices show a Home chooser when you press Home. Select Telum and the permanent-choice option, if offered.

If no selector exists, check the device command support:

```sh
adb -s "TV_SERIAL" shell cmd package help
```

If the output includes `set-home-activity`, use:

```sh
adb -s "TV_SERIAL" shell cmd package set-home-activity --user USER_ID dev.telum.launcher/.MainActivity
```

If the command reports an error, stop Home changes. Keep Telum available as an ordinary app. SHIELD users can review the optional procedure below.

3. Press Home on the remote.
4. Confirm that Telum appears.
5. Open another app.
6. Press Home again.
7. Restart the TV from its settings menu.
8. Confirm that Home still opens Telum.
9. Check the remote's volume and voice controls.

A successful command alone does not prove that Home replacement works.

### Optional SHIELD fallback

This procedure disables the stock Home app for one Android user. Use it only on a confirmed NVIDIA SHIELD. Other devices require their own instructions.

All these conditions must be true:

- Telum passed the ordinary-app tests.
- The normal Home procedure failed or the stock Home app still returns.
- `PREVIOUS_PACKAGE` is exactly `com.google.android.tvlauncher`.
- The stock launcher is currently enabled for `USER_ID`.
- The recorded `PREVIOUS_HOME` opens the stock home screen.
- ADB and system settings remain accessible.
- A tested remote shortcut opens settings without Telum, or ADB remains enabled during the trial.
- You agree to disable this specific stock Home app.

Check the enabled package and recovery activity:

```sh
adb -s "TV_SERIAL" shell pm list packages -e --user USER_ID com.google.android.tvlauncher
adb -s "TV_SERIAL" shell am start --user USER_ID -n "PREVIOUS_HOME"
```

The package output must contain the exact stock package. Confirm that the stock home screen appears.

1. Save the [restore procedure](#restore-the-previous-home-app) before continuing.
2. Open Telum again.
3. Disable only the verified stock Home package.

```sh
adb -s "TV_SERIAL" shell am start --user USER_ID -n dev.telum.launcher/.MainActivity
adb -s "TV_SERIAL" shell pm disable-user --user USER_ID com.google.android.tvlauncher
```

4. Press Home.
5. If a chooser appears, select Telum.
6. Repeat the app-return and restart checks above.

If Home fails, restore the stock app immediately. Do not disable recommendation, voice, remote, or other system packages.

## 8. Configure optional features

Open **Control center > Telum**. Some builds also offer these choices during initial setup.

| Feature | Setup and check |
| --- | --- |
| Music controls | Enable **Music controls**. Grant notification access to **Telum music controls** in Android settings. Start music in its app. Return to Telum. |
| Dim music display | During supported music playback, leave Telum untouched for 60 seconds. Check the dim artwork. Press an arrow to restore the tiles. |
| App content | Enable **Show what you're watching**. Grant TV listings permission if requested. The installed app must publish content. |
| Artist photos | Enable the option if wanted. The provider receives artist names. Album artwork remains the fallback. |
| Weather | Enable weather if wanted. Select your city. The provider receives the selected location. |

Netflix's app tile and background colors do not require a subscription. Its account content can require sign-in and a subscription. See [app support](APPS.md) and [privacy](PRIVACY.md).

## 9. Finish

1. Record the APK version and completed checks.
2. Save the recovery values and restore instructions locally.
3. Check that your remote shortcut still opens system settings without Telum.
4. Turn off debugging after this independent recovery check passes.

To use ADB later, enable debugging again through that settings route. If no independent route works, restore the stock launcher before you turn debugging off. You can continue to test Telum as an ordinary app.

## Restore the previous Home app

If you used the SHIELD fallback, re-enable the stock package first:

```sh
adb -s "TV_SERIAL" shell pm enable --user USER_ID com.google.android.tvlauncher
```

Use the TV's Home selector to choose the previous launcher. If the device supports `set-home-activity`, you can use the recorded component:

```sh
adb -s "TV_SERIAL" shell cmd package set-home-activity --user USER_ID "PREVIOUS_HOME"
adb -s "TV_SERIAL" shell am start --user USER_ID -n "PREVIOUS_HOME"
```

Press Home to check the result. Check again after a restart. If a command fails, open settings with the command from step 5.

Restore a working Home app before uninstalling Telum. Uninstalling Telum deletes its settings. Use the TV's app settings for removal only if you want that deletion.

## Build a test APK

A local debug APK is suitable for testing. It uses your computer's debug signing key. A later official APK can have a different key and refuse to update it. Telum currently has no settings export feature.

1. Download or clone the source from the repository supplied by the user.
2. Record the selected release tag or commit.
3. Install JDK 17 or 21.
4. Install Android SDK Platform 35 and Build Tools 35.0.0 through Android Studio's SDK Manager.
5. Set `ANDROID_HOME` to the SDK directory.
6. Open a terminal in the source directory that contains `gradlew` and `app/`.
7. Check Java with `java -version`.
8. Build the test APK.

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

macOS or Linux:

```sh
chmod +x gradlew
./gradlew :app:assembleDebug :app:lintDebug
```

The first build downloads dependencies. The expected result is `BUILD SUCCESSFUL`. The APK is `app/build/outputs/apk/debug/app-debug.apk`.

9. Use its full path as `APK_PATH` in step 2.

Do not distribute this test APK as an official release. See [release signing](RELEASING.md) for public builds.

## Troubleshooting

| Result | Next action |
| --- | --- |
| `adb` is not found | Repeat the command-path setup in step 3. |
| No device appears | Check the cable or network. Confirm that debugging is enabled. Guest networks can block local connections. |
| `unauthorized` | Unlock or wake the TV. Accept the computer prompt. Reconnect if needed. |
| `offline` | Reconnect the selected TV. Read its current network address again. |
| More than one device | Use the exact TV serial with `-s`. |
| Pairing succeeds but connection fails | Use the connection port from the main Wireless debugging screen, not the pairing port. |
| `INSTALL_FAILED_OLDER_SDK` | Check the Android version. Android 9 and older cannot run Telum. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Use the [signature mismatch procedure](#resolve-a-signature-mismatch). Keep the installed app until you choose a recovery option. |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | Use the same or a newer version. Do not force a downgrade during normal installation. |
| `INSTALL_FAILED_USER_RESTRICTED` | Check device policy and installation permission with the device owner. |
| Invalid or unsigned APK | Download the signed release or build the debug APK. Do not use the unsigned CI artifact. |
| Insufficient storage | Check storage in TV settings. Do not delete app data without the owner's agreement. |
| Home still opens stock | Check step 7. Use the SHIELD fallback only when all its conditions match. |
| No music or recommendations | Check the optional permissions and the source app. These failures do not require launcher reinstallation. |

## Resolve a signature mismatch

Android requires a compatible signing identity for an update. A local debug build and an official release usually have different identities.

1. Check where the installed APK came from.
2. Check where the proposed update came from.
3. If both came from the same release provider, report the mismatch to that provider.

Keep the installed app while the provider investigates. Do not bypass Android's signature checks.

If you have both APK files, inspect their certificates with Android SDK Build Tools:

```sh
apksigner verify --verbose --print-certs "INSTALLED_APK_COPY"
apksigner verify --verbose --print-certs "UPDATE_APK_PATH"
```

Replace these values with the actual file paths. On Windows, use `apksigner.bat`. The tool is in the SDK's `build-tools/VERSION` directory. Compare the SHA-256 certificate fingerprints. See the official [apksigner reference](https://developer.android.com/tools/apksigner).

For a deliberate change from a debug build to an official release, choose one option:

- Keep the current installation and wait for a compatible update.
- Restore the previous Home app, then remove Telum through TV settings and install the new APK.

Removal deletes Telum settings. Telum has no settings export feature. Record your choices before removal if you accept this loss. Do not remove Telum while it is your only working Home app.

## Command references

Connection and APK installation use [Android Debug Bridge](https://developer.android.com/tools/adb). Home selection uses the Android [package manager shell](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/PackageManagerShellCommand.java).

The device's own command help takes precedence when firmware changes command support. These procedures do not require root access.
