# Troubleshooting

Use this guide when a step in the [installation guide](INSTALL.md) gives a different result. Find the stage, then find the result that you see.

Do one correction at a time. Then do the failed step again. If the same error occurs two times, stop. Collect the [diagnostic information](#collect-diagnostic-information) and [report the problem](#report-a-problem).

If the TV device shows no home screen, do the [emergency restore](#emergency-restore) first.

## Computer and ADB

| Result | Cause | Correction |
| --- | --- | --- |
| `adb` is not recognized, or `command not found` | ADB is not installed, or the terminal cannot find it. | Go to the working folder. Do the path command from [Get ADB](INSTALL.md#get-adb) again. |
| `adb` works in one terminal but not in a new terminal | The path change applies only to one terminal. | Do the path command again in the new terminal. |
| `Expand-Archive` or `unzip` fails | The download is incomplete. | Delete `platform-tools.zip`. Download it again. |
| `curl` in Windows PowerShell shows an `Invoke-WebRequest` error | In Windows PowerShell, `curl` is a different command. | Type `curl.exe`. |
| macOS does not let `adb` start | macOS blocked the downloaded file. | Use `brew install --cask android-platform-tools`, or allow the file in **System Settings** › **Privacy & Security**. |
| `adb pair` is not a known command | The ADB version is too old. | Download the current Platform Tools from [Get ADB](INSTALL.md#get-adb). |

## Download

| Result | Cause | Correction |
| --- | --- | --- |
| `Checksum OK` does not show, or the result is `FAILED` | The download is incomplete or changed. | Delete `telum.apk` and `SHA256SUMS`. Download both again. If the result is the same, stop. Then report the problem. |
| `telum.apk` is smaller than 1 MB | The file is an error page, not an APK. | Open the [Releases page](https://github.com/singhuu/Telum/releases) in a browser. Make sure that the latest release has `telum.apk`. |
| `curl: (6) Could not resolve host` | The computer has no internet connection, or a proxy blocks it. | Make sure that the computer can open web pages. Then try again. |
| `curl: (22)` and `404` | No published release has the file. A draft release is not available for download. | Open the [Releases page](https://github.com/singhuu/Telum/releases) in a browser. Do not use APK files from other websites. |
| `gh attestation verify` fails | The file did not come from the Telum release build, or the GitHub CLI is not signed in. | Sign in with `gh auth login`. Then do the command again. If it fails again, do not install the file. |

## Connection

| Result | Cause | Correction |
| --- | --- | --- |
| `Connection refused` | Debugging is off, or the port is wrong. | Make sure that debugging is on. See [step 4](INSTALL.md#4-turn-on-debugging-on-the-tv-device). Use the port that the TV shows. |
| `Connection timed out`, or `No route to host` | The computer and the TV device are on different networks. | Connect both to the same home network. Do not use a guest network. Disconnect a VPN on the computer. |
| The IP address is not correct | The router gave the TV device a new address. | Read the address on the TV again. |
| `failed to authenticate` | The TV has not approved the computer. | Look at the TV. Select the option that always allows this computer. Select **OK**. |
| State `unauthorized` | The approval message was not accepted. | Wake the TV. Accept the message. If no message shows, do the [reset of the connection](#reset-the-adb-connection). |
| State `offline` | The connection stopped. | Do the [reset of the connection](#reset-the-adb-connection). |
| `more than one device/emulator` | ADB sees more than one device. | Add `-s "TV_SERIAL"` to the command. |
| `device 'TV_SERIAL' not found` | The serial is not correct, or the TV is disconnected. | Do `adb devices -l` again. Use the serial from the first column. |
| The pairing works, but the connection fails | The pairing port and the connection port are different. | Use the port from the main **Wireless debugging** screen. |
| The connection stops after a restart | ADB does not connect again automatically. | Do `adb connect TV_IP:5555` again. |
| The model is not your TV device | ADB is connected to a different device. | Stop. Make sure that you use the IP address of your TV device. |

### Reset the ADB connection

1. Disconnect:

```sh
adb disconnect
adb kill-server
```

2. On the TV, set debugging to off. Then set it to on again.
3. Connect again with [step 5](INSTALL.md#5-connect-the-computer-to-the-tv-device).

If the TV does not show the approval message, open **Developer options**. Select **Revoke USB debugging authorizations**. Then connect again.

## Installation

| Result | Cause | Correction |
| --- | --- | --- |
| `Performing Streamed Install` and no result for a long time | The TV shows a Google Play Protect message. | Look at the TV. Read the message. Continue only if the APK came from the Telum Releases page and its checksum is correct. |
| `INSTALL_FAILED_VERIFICATION_FAILURE` or `INSTALL_FAILED_ABORTED` | Google Play Protect stopped the installation, or the message was not answered. | Do the installation again. Look at the TV during the installation. |
| `INSTALL_PARSE_FAILED_NO_CERTIFICATES` | The APK has no signature. | Use `telum.apk` from the Releases page. Do not use an `unsigned` APK. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The installed Telum has a different signature. | See [Signature mismatch](#signature-mismatch). Keep the installed Telum until you select an option. |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | The installed Telum is newer than the APK. | Use the same version or a newer version. |
| `INSTALL_FAILED_OLDER_SDK` | The Android version is older than Android 10. | Telum cannot operate on this device. |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | The TV device has not sufficient storage. | Make space in **Settings** › **Apps** or **Storage**. Do not delete app data without the agreement of the owner. |
| `INSTALL_FAILED_USER_RESTRICTED` | A setting or a device policy blocks installations from ADB. | Look in **Developer options** for a setting that controls app installation through ADB or USB. If an organization manages the device, speak to its administrator. |
| `failed to stat telum.apk` or `No such file or directory` | The terminal is not in the working folder. | Go to the working folder. See [Make a working folder](INSTALL.md#make-a-working-folder). |
| `Activity class ... does not exist` | Telum is not installed for this Android user. | Do the installation again with `--user USER_ID`. |

## Setup

| Result | Cause | Correction |
| --- | --- | --- |
| No intro shows | The device has no OpenGL ES 3.0, or the intro already played after this restart. | This is correct. The setup continues without the intro. |
| No sound during the intro | **Intro sound** is off, or the TV volume is low. | Set **Control centre** › **Telum** › **Intro sound** to on. |
| **Your apps** shows **No apps found**, or the list stops | The app list did not load. | Select **Try again**. |
| **Continue** does not operate on **Your apps** | No app is selected. | Select at least one app. |
| Music controls show no music | Notification access is off. | In **Final touches**, select **Music controls** and do the steps on the screen. |
| No city shows for weather | The city search failed. | Make sure that the TV device has an internet connection. Type the city name again. |
| Setup shows again after each start | Setup was not completed. | Go through setup until Telum shows the home screen. |

## Home selection

| Result | Cause | Correction |
| --- | --- | --- |
| **Android TV Home still opens first** on SHIELD TV | The original Home app has a higher priority. | Do [step 9.2](INSTALL.md#92-shield-tv-disable-the-original-home-app). |
| **... still opens first** on another device | The original Home app of the device has a higher priority. | Telum stays available as an app. This guide has no tested procedure to disable the Home app of other devices. Report the device model in an issue. |
| **Your TV can't switch Home from here** | The device has no Home dialog. | Do [step 9.3](INSTALL.md#93-set-the-home-app-with-adb). |
| Android shows no list of Home apps | The device software has no list, or Android refused the request. | Do [step 9.3](INSTALL.md#93-set-the-home-app-with-adb). |
| `set-home-activity` is not in `cmd package help` | The device software does not have this command. | Stop the Home change. Telum stays available as an app. |
| `set-home-activity` gives an error | The component or the user number is not correct. | Use `dev.telum.launcher/.MainActivity` and the `USER_ID` from step 6. |
| `Unknown package` from `pm disable-user` | The device is not a SHIELD TV, or its Home package has a different name. | Stop. Do not disable a different package. |

## After the Home change

| Result | Cause | Correction |
| --- | --- | --- |
| Home shows a list of Home apps each time | No Home app is permanent. | Select **Telum**. Then select the option that always uses Telum. Or do [step 9.3](INSTALL.md#93-set-the-home-app-with-adb). |
| Home shows a black or empty screen | No Home app is set, or Telum stopped. | Do the [emergency restore](#emergency-restore). |
| The microphone button or voice search does not operate | A remote or voice package is disabled. | Telum and this guide do not disable these packages. Do `adb -s "TV_SERIAL" shell pm list packages -d --user USER_ID`. Compare the result with your list from [step 6](INSTALL.md#6-record-the-recovery-values). If the result has a new package other than `com.google.android.tvlauncher`, enable it: `adb -s "TV_SERIAL" shell pm enable --user USER_ID PACKAGE`. |
| **Settings** does not open | The shortcut is not available. | Open **Control centre** › **All settings**. Or use the command `adb -s "TV_SERIAL" shell am start -a android.settings.SETTINGS`. |
| Telum stops or restarts | An error occurred in Telum. | Collect the [diagnostic information](#collect-diagnostic-information). If Home does not operate, do the emergency restore. Then report the problem. |
| An app opens, but Home opens the previous home screen | Telum is not the Home app for this user. | Do [step 9.3](INSTALL.md#93-set-the-home-app-with-adb). |

### Emergency restore

Use this procedure if the TV device shows no home screen.

1. Connect ADB. If the TV restarted, do `adb connect TV_IP:5555` again.
2. Restore the previous Home app.
   - On SHIELD TV, enable the original Home app and set it:

```sh
adb -s "TV_SERIAL" shell pm enable --user USER_ID com.google.android.tvlauncher
adb -s "TV_SERIAL" shell cmd package set-home-activity --user USER_ID com.google.android.tvlauncher/.MainActivity
```

   - On other devices, set the `PREVIOUS_HOME` from step 6:

```sh
adb -s "TV_SERIAL" shell cmd package set-home-activity --user USER_ID "PREVIOUS_HOME"
```

3. Push Home. The previous home screen shows.

If ADB cannot connect, restart the TV device. Then try again.

If Telum still opens, use the [restore procedure without a computer](INSTALL.md#without-a-computer-shield-tv). That procedure needs Telum to operate.

If ADB cannot connect and Telum does not open, this guide has no tested recovery. This is why [step 11](INSTALL.md#11-finish) recommends that debugging stays on.

## Optional features

| Result | Cause | Correction |
| --- | --- | --- |
| No music controls | Notification access is off, or the music app has no media session. | Give notification access to **Telum music controls**. Start music in the app. |
| No dim music display | The music app is not a known music app, **Music idle display** is off, or the remote was used. | See the supported music apps in [app support](APPS.md). Wait 60 seconds without the remote. |
| No Live Activities | TV listings access is off, or the app shares no rows. | Allow TV listings access. Many apps share no rows. This is not an installation problem. |
| No artist photo | No photo exists, or the network request failed. | Album art shows in place of the photo. This is correct. |
| No weather | **Weather** is off, no city is set, or no internet connection. | Set **Weather** to on. Select **Weather city**. |

## Signature mismatch

Android updates an app only when the new APK has the same signature as the installed app. A test APK that you built yourself and a release APK have different signatures.

1. Find out where the installed Telum came from.
2. Find out where the new APK came from.
3. If both came from the Telum Releases page, stop. Report the problem.
4. If the installed Telum is a test build, select one option:
   - Keep the installed Telum.
   - Remove the installed Telum, then install the release APK.

> [!CAUTION]
> Removal deletes the Telum settings. If Telum is the Home app, restore the previous Home app before you remove Telum.

To remove the test build and install the release:

1. Do the [restore procedure](INSTALL.md#restore-the-previous-home-app), if Telum is the Home app.
2. Remove Telum:

```sh
adb -s "TV_SERIAL" uninstall dev.telum.launcher
```

3. Do [step 7](INSTALL.md#7-install-telum-and-open-it) and the steps after it again.

Do not try to bypass the signature check of Android.

To compare the certificates of two APK files, use `apksigner` from the Android SDK Build Tools:

```sh
apksigner verify --print-certs "FIRST_APK"
apksigner verify --print-certs "SECOND_APK"
```

On Windows, use `apksigner.bat`. Compare the `SHA-256 digest` lines. The release notes give the fingerprint of the release certificate.

## Collect diagnostic information

These commands only read information. They do not change the TV device.

```sh
adb version
adb devices -l
adb -s "TV_SERIAL" shell getprop ro.product.manufacturer
adb -s "TV_SERIAL" shell getprop ro.product.model
adb -s "TV_SERIAL" shell getprop ro.build.version.release
adb -s "TV_SERIAL" shell "dumpsys package dev.telum.launcher | grep -E 'versionName|versionCode'"
adb -s "TV_SERIAL" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s "TV_SERIAL" shell pm list packages -d
adb -s "TV_SERIAL" logcat -d -b crash
adb -s "TV_SERIAL" logcat -d -s Telum
```

Telum also keeps a short log of slow drawing. Read it with this command:

```sh
adb -s "TV_SERIAL" shell cat /sdcard/Android/media/dev.telum.launcher/watchdog.log
```

If the file does not exist, Telum recorded no slow drawing.

Examine the output before you share it. Remove IP addresses, serial numbers, account names, and media titles.

## Report a problem

1. Open [Issues](https://github.com/singhuu/Telum/issues) and select **New issue**.
2. Select **Installation problem** or **Bug report**.
3. Give the TV device model, the Android version, the Telum version, and the step that failed.
4. Add the command and its full output. Remove private information first.
5. If an AI agent helped you, add its completion report.

> [!CAUTION]
> Do not put IP addresses, serial numbers, pairing codes, passwords, or account data in an issue. Issues are public.
