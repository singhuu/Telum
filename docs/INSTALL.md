# Install Telum

This guide gives all the steps and commands to install Telum. The recommended procedure uses an AI agent that follows the [agent guide](INSTALL-AGENT.md). The agent uses this guide as its command reference. You can also do the steps yourself.

For errors, use the [troubleshooting guide](TROUBLESHOOTING.md).

## Contents

1. [Check the TV device](#1-check-the-tv-device)
2. [Prepare the computer](#2-prepare-the-computer)
3. [Download Telum](#3-download-telum)
4. [Turn on debugging on the TV device](#4-turn-on-debugging-on-the-tv-device)
5. [Connect the computer to the TV device](#5-connect-the-computer-to-the-tv-device)
6. [Record the recovery values](#6-record-the-recovery-values)
7. [Install Telum and open it](#7-install-telum-and-open-it)
8. [Do the Telum setup](#8-do-the-telum-setup)
9. [Select Telum as Home](#9-select-telum-as-home)
10. [Set the optional features](#10-set-the-optional-features)
11. [Finish](#11-finish)

Other procedures: [Update Telum](#update-telum), [Restore the previous Home app](#restore-the-previous-home-app), [Remove Telum](#remove-telum), [Install from the TV without a computer](#install-from-the-tv-without-a-computer), [Build a test APK](#build-a-test-apk).

## Terms and values

| Term | Meaning |
| --- | --- |
| TV device | The NVIDIA SHIELD TV, Android TV box, or TV that operates Android TV. |
| Home app | The app that opens when you push the Home button on the remote. |
| Original Home app | The Home app that came with the TV device. On SHIELD TV, it is Android TV Home, package `com.google.android.tvlauncher`. |
| ADB | Android Debug Bridge. A tool from Google that sends commands from the computer to the TV device. |
| APK | An Android installation file. Telum releases contain `telum.apk`. |

The commands use these placeholders. Replace each placeholder with your value. Keep the quotation marks.

| Placeholder | Value | Example |
| --- | --- | --- |
| `TV_IP` | The IP address of the TV device. | `192.168.1.20` |
| `TV_SERIAL` | The ADB serial of the TV device, from step 5. | `192.168.1.20:5555` |
| `USER_ID` | The Android user number, from step 6. | `0` |
| `PREVIOUS_HOME` | The Home component before Telum, from step 6. | `com.google.android.tvlauncher/.MainActivity` |

## 1. Check the TV device

1. On the TV device, open **Settings**.
   - On SHIELD TV, select the gear icon at the top right of the home screen.
   - In Telum, open **Control centre** and select **All settings**.
2. Find the **About** screen.
   - SHIELD TV and Android TV: **Device Preferences** › **About**.
   - Google TV: **System** › **About**.
3. Record the model and the Android version.

The SHIELD Experience version and the Android version are different numbers. SHIELD Experience 9 contains Android 11. Use the Android version for this check.

If the Android version is 9 or older, stop. Telum cannot operate on Android 9 or older. A SHIELD TV can get Android 11 through the official system update in its settings. Do not install modified firmware for Telum.

## 2. Prepare the computer

The computer must be on the same home network as the TV device. A guest network or a VPN can block the connection.

### Make a working folder

Open a terminal. On Windows, use **Windows PowerShell** or **Terminal**. On macOS, use **Terminal**.

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force "$HOME\Downloads\telum-install"
Set-Location "$HOME\Downloads\telum-install"
```

macOS or Linux:

```sh
mkdir -p ~/Downloads/telum-install
cd ~/Downloads/telum-install
```

Use this folder for all the remaining commands.

### Get ADB

First, find out if ADB is already installed:

```sh
adb version
```

If the output starts with `Android Debug Bridge version`, go to [step 3](#3-download-telum). If the terminal cannot find `adb`, download the official Android SDK Platform Tools from Google.

Windows PowerShell:

```powershell
curl.exe -fL -o platform-tools.zip https://dl.google.com/android/repository/platform-tools-latest-windows.zip
Expand-Archive -Force platform-tools.zip -DestinationPath .
$env:Path = "$PWD\platform-tools;$env:Path"
adb version
```

macOS:

```sh
curl -fL -o platform-tools.zip https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
unzip -o -q platform-tools.zip
export PATH="$PWD/platform-tools:$PATH"
adb version
```

Linux:

```sh
curl -fL -o platform-tools.zip https://dl.google.com/android/repository/platform-tools-latest-linux.zip
unzip -o -q platform-tools.zip
export PATH="$PWD/platform-tools:$PATH"
adb version
```

On Windows, type `curl.exe`, not `curl`. In Windows PowerShell, `curl` is a different command.

The path change applies only to the current terminal. If you open a new terminal, go to the working folder and do the path command again.

You can also use a package manager. Open a new terminal after the installation.

| Computer | Command | Note |
| --- | --- | --- |
| Windows | `winget install Google.PlatformTools` | The current version from Google. |
| macOS | `brew install --cask android-platform-tools` | The current version from Google. |
| Debian or Ubuntu | `sudo apt install adb` | Some versions of this package are too old for `adb pair`. For Wireless debugging, use the download from Google. |

## 3. Download Telum

1. Download the APK and its checksum file from the latest release.

Windows PowerShell:

```powershell
curl.exe -fL -o telum.apk https://github.com/singhuu/Telum/releases/latest/download/telum.apk
curl.exe -fL -o SHA256SUMS https://github.com/singhuu/Telum/releases/latest/download/SHA256SUMS
```

macOS or Linux:

```sh
curl -fL -o telum.apk https://github.com/singhuu/Telum/releases/latest/download/telum.apk
curl -fL -o SHA256SUMS https://github.com/singhuu/Telum/releases/latest/download/SHA256SUMS
```

The commands show no error when the download is correct. If a command shows `404`, no published release has the file. See [Download](TROUBLESHOOTING.md#download).

2. Make sure that the checksum is correct.

Windows PowerShell:

```powershell
$expected = (Get-Content SHA256SUMS).Split(' ')[0]
$actual = (Get-FileHash -Algorithm SHA256 telum.apk).Hash
if ($actual -eq $expected) { 'Checksum OK' } else { 'CHECKSUM MISMATCH' }
```

macOS:

```sh
shasum -a 256 -c SHA256SUMS
```

Linux:

```sh
sha256sum -c SHA256SUMS
```

The correct result is `Checksum OK` on Windows and `telum.apk: OK` on macOS and Linux. If the result is different, delete both files and download them again. If the result is different again, stop. Do not install that file.

3. Read the release notes on the [Releases page](https://github.com/singhuu/Telum/releases). They give the device tests and the known limits of the release.

A correct checksum shows that the file is the file that the release published. It does not show that the publisher is trustworthy.

If you have the [GitHub CLI](https://cli.github.com/), you can also examine the build record. This command shows if GitHub Actions built the file from the Telum repository:

```sh
gh attestation verify telum.apk --repo singhuu/Telum
```

## 4. Turn on debugging on the TV device

ADB can connect only when debugging is on. You do these steps on the TV with the remote.

### NVIDIA SHIELD TV

1. Open **Settings** › **Device Preferences** › **About**.
2. Highlight **Build**. Push OK seven times. The TV shows a message that you are now a developer.
3. Push Back. Open **Device Preferences** › **Developer options**.
4. Set **Network debugging** to on.
5. Record the IP address that the TV shows under **Network debugging**. For example: `192.168.1.20:5555`.

If the TV does not show the address, open **Settings** › **Network & Internet**, and then select your network. The IP address is on that screen. SHIELD TV uses port `5555`.

### Other Android TV and Google TV devices

1. Open the **About** screen from [step 1](#1-check-the-tv-device).
2. Highlight **Build** or **Android TV OS build**. Push OK seven times.
3. Push Back. Open **Developer options**. On Google TV, it is in **System**.
4. Turn on one of these options:
   - **Wireless debugging**. Use this option if the device has it.
   - **Network debugging**.
   - **USB debugging**. Some devices also accept network connections on port 5555 with this option.
5. Record the IP address and the port that the TV shows.

The menu names can be different on your device. For more information, see the [Android developer options](https://developer.android.com/studio/debug/dev-options).

Use debugging only on your private home network. Do not forward the debugging port on your router.

## 5. Connect the computer to the TV device

### Connect with Network debugging or USB debugging

1. Connect to the TV device:

```sh
adb connect TV_IP:5555
```

The correct result is `connected to TV_IP:5555`. At the first connection, the result can also be `failed to authenticate`. This is correct until you approve the computer on the TV.

2. Look at the TV. It shows a message that asks you to allow debugging from this computer.
3. If the message has an option that always allows this computer, select it.
4. Select **OK** or **Allow**.

### Connect with Wireless debugging and a pairing code

1. On the TV, open **Wireless debugging**. Select **Pair device with pairing code**.
2. The TV shows a pairing address with a port, and a six-digit code.
3. On the computer, start the pairing with that address:

```sh
adb pair TV_IP:PAIRING_PORT
```

4. Type the six-digit code when ADB asks for it.
5. Push Back on the TV. The **Wireless debugging** screen shows the connection address and port. This port is different from the pairing port.
6. Connect with the connection port:

```sh
adb connect TV_IP:CONNECTION_PORT
```

The correct result of `adb pair` is `Successfully paired`. The correct result of `adb connect` is `connected to TV_IP:CONNECTION_PORT`.

Do not give the pairing code to other persons.

### Find the serial of the TV device

1. Show the connected devices:

```sh
adb devices -l
```

2. Find the line with your TV device and the state `device`. For example:

```text
192.168.1.20:5555      device product:mdarcy model:SHIELD_Android_TV device:mdarcy
```

3. Record the first column as `TV_SERIAL`. In the example, it is `192.168.1.20:5555`.

If the state is `unauthorized` or `offline`, or no device shows, see [Connection](TROUBLESHOOTING.md#connection).

Use `-s "TV_SERIAL"` in every command from this point. This makes sure that each command goes to the correct device.

4. Make sure that the computer is connected to the correct device:

```sh
adb -s "TV_SERIAL" shell getprop ro.product.manufacturer
adb -s "TV_SERIAL" shell getprop ro.product.model
adb -s "TV_SERIAL" shell getprop ro.build.version.release
adb -s "TV_SERIAL" shell getprop ro.build.version.sdk
```

The results show the manufacturer, the model, the Android version, and the API level. A SHIELD TV shows `NVIDIA` and `SHIELD Android TV`. The API level must be `29` or higher.

## 6. Record the recovery values

These values let you restore your current home screen. Record them in a text file in the working folder.

1. Get the Android user number:

```sh
adb -s "TV_SERIAL" shell am get-current-user
```

The result is one number. Record it as `USER_ID`. On most TV devices, it is `0`.

2. Get the current Home component:

```sh
adb -s "TV_SERIAL" shell cmd package resolve-activity --brief --user USER_ID -a android.intent.action.MAIN -c android.intent.category.HOME
```

The last line of the result has the form `package/activity`. Record it as `PREVIOUS_HOME`. On SHIELD TV, it is usually `com.google.android.tvlauncher/.MainActivity`.

Some results are not a recovery value:

| Last line | Meaning | Value to record |
| --- | --- | --- |
| Starts with `android/`, for example `android/com.android.internal.app.ResolverActivity` | Android shows a list of Home apps. No single Home app is set. | On SHIELD TV, use `com.google.android.tvlauncher/.MainActivity`. On other devices, record the Home app that the user selects in the list. |
| Contains no `/` | No Home app was found. | Record the result. Do not change the Home app. |
| `dev.telum.launcher/.MainActivity` | Telum is already the Home app, for example after an earlier installation. | On SHIELD TV, use `com.google.android.tvlauncher/.MainActivity`. On other devices, ask the user which Home app they used before. |

3. Record the disabled packages:

```sh
adb -s "TV_SERIAL" shell pm list packages -d --user USER_ID
```

The result is a list of lines that start with `package:`. The list can be empty. You need this list if you restore the TV device later. Telum does not disable these packages.

4. Make sure that ADB can open the system settings:

```sh
adb -s "TV_SERIAL" shell am start --user USER_ID -a android.settings.SETTINGS
```

The TV shows **Settings**. Push Back to close it.

Keep the terminal and the recovery values until you finish this guide.

## 7. Install Telum and open it

1. If Telum is already installed, record its version:

```sh
adb -s "TV_SERIAL" shell "dumpsys package dev.telum.launcher | grep -E 'versionName|versionCode'"
```

If the result is empty, Telum is not installed.

2. Look at the TV during the next step. Google Play Protect can show a message on the TV.
3. Install Telum:

```sh
adb -s "TV_SERIAL" install -r --user USER_ID telum.apk
```

The correct result is `Success`. The `-r` option keeps the Telum settings during an update.

If Google Play Protect shows a message, read it. Continue only if `telum.apk` came from the Telum Releases page and its checksum is correct. If the result is not `Success`, see [Installation](TROUBLESHOOTING.md#installation).

4. Open Telum:

```sh
adb -s "TV_SERIAL" shell am start --user USER_ID -n dev.telum.launcher/.MainActivity
```

The TV shows Telum. The Home button still opens your previous Home app. This is correct until step 9.

After the first installation, the Home button can show a list of Home apps. Android does this when it finds a new Home app. Select your previous Home app until step 9.

## 8. Do the Telum setup

Use the remote for these screens. The arrows move the highlight. OK selects. Back goes to the previous screen.

1. An intro with stars plays. Push OK or an arrow to skip it. The volume buttons do not skip it. Devices without OpenGL ES 3.0 do not show the intro.
2. On the welcome screen, select **Next**. **Next** shows after four seconds.
3. On **Your apps**, push OK on an app to add it or remove it. All apps are selected at the start.
4. To change the order, select **Arrange apps**. The first five apps fill the top row of Home. Then select **Continue**.
5. On **Your style**, highlight **Recommended** or **Light**. Then select **Continue with Recommended** or **Continue with Light**.
6. To change single settings, select **Customize** in place of step 5. The **Your features** screen shows. Select **Continue** when you are done.
7. On **Final touches**, connect the features that you selected, or skip them. You can connect them later. Then select **Start using Telum**.
8. The closing screen shows **Enter Telum** after four seconds. Select it.
9. Telum asks **Make Telum your Home?** Go to step 9.

Apps that you do not select stay installed. You find them in **Control centre** › **Telum** › **Hidden apps**.

To open **Control centre** from Home:

1. Push Up. The highlight moves to **Search** in the top bar.
2. Push Right. The highlight moves to **Control centre**.
3. Push OK.

To do the setup again later, open **Control centre** and select **Run setup again**.

## 9. Select Telum as Home

Do this step only after Telum operates correctly as an app. Do not remove your previous Home app.

### 9.1 Use the Telum question

1. When Telum asks **Make Telum your Home?**, select **Make Telum Home**.
2. Android shows a list of Home apps. Select **Telum**.
3. If Android asks for confirmation, select **Set as default**.

If you selected **Not now**, open **Control centre** › **Telum** › **Make Telum your Home**. This item shows only when the Home button does not open Telum.

Telum then shows one of these results:

| Telum shows | Meaning | Next step |
| --- | --- | --- |
| **Telum is now your Home** | The Home button opens Telum. | Go to [9.4](#94-test-the-home-app). |
| **Android TV Home still opens first** | The original Home app has a higher priority than Telum. This is normal on SHIELD TV. | On SHIELD TV, go to [9.2](#92-shield-tv-disable-the-original-home-app). On other devices, see [Home selection](TROUBLESHOOTING.md#home-selection). |
| **Your TV can't switch Home from here** | The device has no Home dialog. | Go to [9.3](#93-set-the-home-app-with-adb). |
| No message | You selected **Not now**, or **Cancel** in the Android list. The Home app did not change. | Use **Control centre** › **Telum** › **Make Telum your Home** to try again. |

The name in the second result can be different on other devices.

At the second request, the Android list can also show **Don't ask again**. Do not select it. If you selected it, go to [9.3](#93-set-the-home-app-with-adb).

### 9.2 SHIELD TV: disable the original Home app

The original Home app of SHIELD TV has a higher priority than all other Home apps. Telum can become the Home app only when the original Home app is disabled. A disabled app stays on the device. You can enable it again at any time.

Do this procedure only if all these conditions are true:

- The TV device is an NVIDIA SHIELD TV. Step 5 showed `NVIDIA` and `SHIELD Android TV`.
- The command in step 1 below shows `package:com.google.android.tvlauncher`, or the package is already disabled.
- Telum opened and operated correctly in steps 7 and 8.
- You have the [restore procedure](#restore-the-previous-home-app). On SHIELD TV, the restore uses `com.google.android.tvlauncher/.MainActivity`.
- Debugging stays on. See [step 11](#11-finish).

> [!CAUTION]
> Disable only `com.google.android.tvlauncher`. Do not disable other Google or NVIDIA packages. Some of them operate the remote, the microphone, or voice search.

1. Make sure that the original Home app is on the device:

```sh
adb -s "TV_SERIAL" shell pm list packages --user USER_ID com.google.android.tvlauncher
adb -s "TV_SERIAL" shell pm list packages -d --user USER_ID com.google.android.tvlauncher
```

The first command shows `package:com.google.android.tvlauncher`. If it shows nothing, stop. This procedure does not apply to the device.

The second command shows the package only if it is already disabled. In that case, go to step 3.

2. Disable the original Home app:

```sh
adb -s "TV_SERIAL" shell pm disable-user --user USER_ID com.google.android.tvlauncher
```

The correct result is `Package com.google.android.tvlauncher new state: disabled-user`.

3. Push Home on the remote.
4. If Android shows a list of Home apps, select **Telum**. Then select the option that always uses Telum.
5. Make sure that the Home button opens Telum:

```sh
adb -s "TV_SERIAL" shell cmd package resolve-activity --brief --user USER_ID -a android.intent.action.MAIN -c android.intent.category.HOME
```

The last line is `dev.telum.launcher/.MainActivity`. If it is different, go to [9.3](#93-set-the-home-app-with-adb).

### 9.3 Set the Home app with ADB

1. Make sure that the device has the command:

```sh
adb -s "TV_SERIAL" shell cmd package help
```

The output must contain `set-home-activity`. If it does not, stop the Home change. Telum stays available as an app.

2. Set Telum as the Home app:

```sh
adb -s "TV_SERIAL" shell cmd package set-home-activity --user USER_ID dev.telum.launcher/.MainActivity
```

The correct result is `Success`. A `Success` result alone does not prove that the Home button opens Telum. Do the tests in 9.4.

### 9.4 Test the Home app

1. Push Home. Telum shows.
2. Open an app from Telum. Push Home. Telum shows again.
3. Restart the TV device.
   - SHIELD TV and Android TV: **Settings** › **Device Preferences** › **About** › **Restart**.
   - Google TV: **Settings** › **System** › **Restart**.
   - Or use the command `adb -s "TV_SERIAL" reboot`.
4. After the restart, Telum shows.
5. Make sure that the volume buttons, the microphone button, and voice search operate.

After a restart, connect ADB again with the `adb connect` command from step 5.

If a test fails, do the [restore procedure](#restore-the-previous-home-app). Then see [After the Home change](TROUBLESHOOTING.md#after-the-home-change).

## 10. Set the optional features

Open **Control centre** › **Telum**. Setup also offers these features.

| Feature | Setup |
| --- | --- |
| Music controls | Set **Music controls** to on. In Android settings, give notification access to **Telum music controls**. On SHIELD TV, the setup screen shows the steps. Start music in a music app. Then push Home. |
| Music idle display | Music controls must be on. Set **Music idle display** to on. Play music in Spotify, YouTube Music, Amazon Music, or TuneIn. Do not touch the remote for 60 seconds. Dim album art shows. Push an arrow to show the tiles. |
| Live Activities | Set **Live Activities** to on. Allow TV listings access when Android asks. Select the rows in **What each app shows**. Each app decides what it shares. |
| Artist photos | Music controls must be on. Set **Artist photos** to on. Telum sends artist names to TheAudioDB. Album art shows when no photo is available. |
| Weather | Set **Weather** to on. Select **Weather city** and find your city. Telum sends the city to Open-Meteo. |
| Intro sound | Set **Intro sound** to on or off. |

The Netflix tile and background colors do not need a subscription. Netflix content can need sign-in and a subscription. See [app support](APPS.md) and [privacy](PRIVACY.md).

## 11. Finish

1. Record the Telum version and the tests that passed.
2. Keep the recovery values and the [restore procedure](#restore-the-previous-home-app) on your computer.
3. Make sure that you can open **Settings** without the computer: **Control centre** › **All settings**.
4. Decide if debugging stays on. Read the table first.

| Debugging | Advantage | Risk |
| --- | --- | --- |
| On (recommended while Telum is your Home app) | If Telum stops, the computer can restore the previous Home app. | A computer on your network can connect only after you approve it on the TV. A computer that you approved can connect at any time. |
| Off | No computer can connect. | To turn debugging on again, you must open **Settings** through Telum. If Telum does not start, no tested recovery exists without ADB. |

If you want debugging off, first use Telum as your Home app for some days. Turn it off only after Telum operated correctly in that time.

To turn debugging off on SHIELD TV, set **Network debugging** to off. To use ADB later, turn it on again in **Developer options**.

## Update Telum

1. Download the new `telum.apk` and `SHA256SUMS`. Use [step 3](#3-download-telum).
2. Connect ADB. Use [step 5](#5-connect-the-computer-to-the-tv-device).
3. Install the new APK over the installed app:

```sh
adb -s "TV_SERIAL" install -r --user USER_ID telum.apk
```

The correct result is `Success`. Telum keeps its settings.

4. Make sure that Telum is still the Home app:

```sh
adb -s "TV_SERIAL" shell cmd package resolve-activity --brief --user USER_ID -a android.intent.action.MAIN -c android.intent.category.HOME
```

The last line is `dev.telum.launcher/.MainActivity`. If it is different, do [step 9.3](#93-set-the-home-app-with-adb).

If the result of step 3 is `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the installed Telum has a different signature. See [Signature mismatch](TROUBLESHOOTING.md#signature-mismatch).

You can also update from the TV with the Downloader app. See [Install from the TV](#install-from-the-tv-without-a-computer).

## Restore the previous Home app

Use this procedure to make your previous home screen the Home app again.

### With ADB

1. If you disabled the original Home app on SHIELD TV, enable it:

```sh
adb -s "TV_SERIAL" shell pm enable --user USER_ID com.google.android.tvlauncher
```

The correct result is `Package com.google.android.tvlauncher new state: enabled`.

2. Set the previous Home app. On SHIELD TV, `PREVIOUS_HOME` is `com.google.android.tvlauncher/.MainActivity`. On other devices, use the value from [step 6](#6-record-the-recovery-values).

```sh
adb -s "TV_SERIAL" shell cmd package set-home-activity --user USER_ID "PREVIOUS_HOME"
```

The correct result is `Success`.

3. Push Home. The previous home screen shows.
4. Restart the TV device. The previous home screen shows again.

If `set-home-activity` is not available, push Home. Select the previous Home app in the list.

### Without a computer, SHIELD TV

Use this procedure only while Telum operates. It opens **Settings** through Telum.

1. Open **Control centre** › **All settings**.
2. Open **Apps** › **See all apps** › **Show system apps**.
3. Select **Android TV Home**. Select **Enable**.
4. Push Home. If Android shows a list, select **Android TV Home**.

The menu names can be different in other versions of the device software.

## Remove Telum

> [!CAUTION]
> Restore the previous Home app before you remove Telum. If you remove Telum while it is the only Home app, the TV device can show no home screen.

1. Do the [restore procedure](#restore-the-previous-home-app).
2. Remove Telum:

```sh
adb -s "TV_SERIAL" uninstall dev.telum.launcher
```

The correct result is `Success`. You can also use **Settings** › **Apps** › **Telum** › **Uninstall**.

Removal deletes the Telum settings. Telum has no export for its settings.

## Install from the TV without a computer

This procedure installs Telum as an app. It cannot disable the original Home app on SHIELD TV. On SHIELD TV, the Home button continues to open Android TV Home.

1. On the TV, open the Google Play Store.
2. Install **Downloader** by AFTVnews.
3. Open Downloader. In the address field, type this address:

```text
https://github.com/singhuu/Telum/releases/latest/download/telum.apk
```

4. Select **Go**. Downloader downloads the APK.
5. If Android asks for permission to install unknown apps, allow it for Downloader.
   - On SHIELD TV, the setting is in **Settings** › **Device Preferences** › **Security & restrictions** › **Unknown sources**.
6. Select **Install**. Then select **Open**.
7. Do the [Telum setup](#8-do-the-telum-setup).
8. Delete the APK when Downloader asks.

To make Telum the Home app on SHIELD TV later, do steps 2, 4, 5, 6 and 9 with a computer.

Downloader cannot examine the checksum. The address above downloads the file only from the Telum Releases page.

## Build a test APK

A test APK that you build yourself uses the debug key of your computer. A release APK uses a different key. Android cannot update one with the other. To change from one to the other, you must remove Telum, and this deletes its settings.

1. Download or clone the source from the [Telum repository](https://github.com/singhuu/Telum).
2. Record the release tag or the commit.
3. Install JDK 17 or 21.
4. Install Android SDK Platform 35 and Build Tools 35.0.0. Use the SDK Manager of Android Studio.
5. Set the `ANDROID_HOME` environment variable to the SDK folder.
6. Open a terminal in the source folder. The folder contains `gradlew` and `app`.
7. Build the test APK.

Windows PowerShell:

```powershell
.\gradlew.bat :app:behaviorChecks :app:assembleDebug :app:lintDebug
```

macOS or Linux:

```sh
chmod +x gradlew
./gradlew :app:behaviorChecks :app:assembleDebug :app:lintDebug
```

The first build downloads its dependencies. The correct result is `BUILD SUCCESSFUL`. The APK is `app/build/outputs/apk/debug/app-debug.apk`.

8. Use this file in place of `telum.apk` in step 7.

Do not publish a test APK as a release. See [releasing](RELEASING.md).

## Command references

ADB commands: [Android Debug Bridge](https://developer.android.com/tools/adb). Home commands: the [package manager shell](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/PackageManagerShellCommand.java).

The command help on your device has priority, because the device software can change the available commands. These procedures do not need root access.
