# Telum installation guide for AI agents

This guide is for an AI agent that helps a person install Telum on an Android TV device. It applies to all agents and all AI providers. The person is "the user" in this guide.

Telum is a home screen app. It does not replace the operating system of the TV device or the streaming apps.

## Read these documents first

Read all three documents before you give the first command. Use the raw addresses if your tools cannot read GitHub pages.

| Document | Use | Raw address |
| --- | --- | --- |
| This guide | Rules, stages, decisions, and the report. | `https://raw.githubusercontent.com/singhuu/Telum/main/docs/INSTALL-AGENT.md` |
| Installation guide | All commands and their correct results. | `https://raw.githubusercontent.com/singhuu/Telum/main/docs/INSTALL.md` |
| Troubleshooting guide | Errors, their causes, and corrections. | `https://raw.githubusercontent.com/singhuu/Telum/main/docs/TROUBLESHOOTING.md` |

If you cannot read a document, tell the user. Ask the user to paste its text. Do not continue from memory.

These are the only sources for downloads:

- Telum Releases page: `https://github.com/singhuu/Telum/releases`
- Latest Telum APK: `https://github.com/singhuu/Telum/releases/latest/download/telum.apk`
- Latest Telum checksum: `https://github.com/singhuu/Telum/releases/latest/download/SHA256SUMS`
- ADB for Windows, from Google: `https://dl.google.com/android/repository/platform-tools-latest-windows.zip`
- ADB for macOS, from Google: `https://dl.google.com/android/repository/platform-tools-latest-darwin.zip`
- ADB for Linux, from Google: `https://dl.google.com/android/repository/platform-tools-latest-linux.zip`
- ADB through a package manager: the `winget`, `brew`, and `apt` commands in [Get ADB](INSTALL.md#get-adb).

## Rules

You must obey these rules during the full installation.

1. Use only the sources above for downloads. Do not use APK files from other websites, mirrors, or search results.
2. Tell the user the purpose of each action before you do it.
3. Get the agreement of the user before each of these actions:
   - You install software on the computer.
   - You install Telum on the TV device.
   - You make Telum the Home app.
   - You disable, enable, or remove an app.
   - You restart the TV device.
   - You turn off debugging, or tell the user to turn it off.
4. Use `adb -s "TV_SERIAL"` in every command to the TV device.
5. Do one change at a time. Examine the result before the next step.
6. Compare each result with the correct result in the installation guide. If they are different, stop. Use the troubleshooting guide.
7. Do not say that a step is complete without evidence. Evidence is command output that you read, or a visible result that the user tells you.
8. If you only gave a command to the user, say that the user must run it. Do not say that you ran it.
9. Keep the restore commands available until the installation is complete.
10. Do not ask for passwords, account names, payment data, or streaming tokens. Do not read them from the device.
11. Do not root the device. Do not unlock the bootloader, install firmware, or do a factory reset.
12. Change the TV device only with the commands in the installation guide and the troubleshooting guide. Commands that only read information are permitted.
13. Do not disable packages other than `com.google.android.tvlauncher`, and only on a SHIELD TV. Some Google and NVIDIA packages operate the remote, the microphone, and voice search.
14. Do not put IP addresses, serial numbers, or pairing codes in public text, for example in a GitHub issue.

## Select your working mode

| Mode | When | How to operate |
| --- | --- | --- |
| Local mode | You can run commands on the computer of the user, and that computer is on the home network of the TV device. | Run each command yourself. Read the output. Ask the user only for the steps on the TV. |
| Guide mode | You cannot run commands, or you operate in a cloud environment. | Give one command at a time with its correct result. Ask the user to run it and paste the full output. |

A cloud environment cannot connect to a private home network. If you are not sure where you operate, ask the user. In local mode, the command `hostname` shows the name of the computer. The user can compare it with the name of their computer.

In local mode, find the operating system and the shell first. On Windows, use PowerShell syntax and `curl.exe`. On macOS and Linux, use `sh` syntax.

In guide mode, give commands for the operating system of the user only. Tell the user where to type the command and how to copy the output.

## Collect the information

Ask these questions in one message. Do not ask again for information that the user already gave.

1. What is the TV device? For example, NVIDIA SHIELD TV Pro, or Chromecast with Google TV.
2. Which computer do you have: Windows, macOS, or Linux?
3. Are the computer and the TV device on the same home network?
4. Can you use the TV remote during the installation?
5. Do you want Telum as your Home app, or do you want to try it as an app first?

If the user has no computer, explain that the Home change on SHIELD TV needs a computer. Offer [Install from the TV](INSTALL.md#install-from-the-tv-without-a-computer) to try Telum as an app.

## Stages

Do the stages in this sequence. Do not start a stage before you have the evidence of the previous stage.

| Stage | Installation guide | Evidence before the next stage |
| --- | --- | --- |
| 1. Check the device | Step 1 | The model and the Android version are known. Android is version 10 or newer. |
| 2. Prepare the computer | Step 2 | `adb version` shows `Android Debug Bridge version`. |
| 3. Download | Step 3 | The checksum result is `Checksum OK` or `telum.apk: OK`. You read the release notes. |
| 4. Turn on debugging | Step 4 | The user tells you the IP address and the port. |
| 5. Connect | Step 5 | `adb devices -l` shows one `TV_SERIAL` with state `device`. The model is the device of the user. |
| 6. Record recovery | Step 6 | You have `USER_ID`, `PREVIOUS_HOME`, and the list of disabled packages. **Settings** opened on the TV. |
| 7. Install | Step 7 | The result is `Success`. Telum shows on the TV. |
| 8. Setup | Step 8 | The user completed setup and sees **Make Telum your Home?**, or the Telum home screen. |
| 9. Home | Step 9 | The Home component is `dev.telum.launcher/.MainActivity`. Home opens Telum after an app and after a restart. |
| 10. Features | Step 10 | The user selected the features and tested them. |
| 11. Finish | Step 11 | The user has the report and the restore commands. |

Do stage 9 only if the user wants Telum as the Home app.

## Decisions at each stage

### Stage 1: device type

Use the device type to select the Home procedure in stage 9.

| Result | Device type | Home procedure |
| --- | --- | --- |
| Manufacturer `NVIDIA`, model `SHIELD Android TV` | SHIELD TV | 9.1, then 9.2 |
| Other manufacturer, Android 10 or newer | Other device | 9.1, then 9.3 if necessary |
| Android 9 or older | Not supported | Stop. On SHIELD TV, the official system update gives Android 11. |

The user tells you the device in stage 1. In stage 5, ADB gives the manufacturer and the model. If they are different, use the ADB result and tell the user.

### Stage 2: ADB

If `adb version` operates, use that ADB. If not, ask the user before you download Platform Tools. Use the commands in [Get ADB](INSTALL.md#get-adb). Write all files in the working folder.

### Stage 3: download

Download `telum.apk` and `SHA256SUMS` into the working folder. Examine the checksum.

If the checksum is not correct, delete both files and download them again one time. If it is not correct again, stop. Tell the user that the file is not safe to install.

If `curl` shows `404`, no release is published. Tell the user. Do not look for the APK on other websites.

Read the release notes of the latest release. Tell the user the version and the device tests. If the notes give limits for the device of the user, tell the user before stage 7.

If the GitHub CLI is installed and signed in, also do the `gh attestation verify` command. If it is not installed, do not install it only for this check.

### Stage 4: debugging

Give the user the menu steps for the device type. Use one short message for each step. Tell the user what the TV shows after the step.

### Stage 5: connection

Before the connection, tell the user to look at the TV for the approval message. After the connection, make sure that the model is correct. If ADB shows more than one device, ask the user which one is the TV device.

### Stage 6: recovery

Use the table in [step 6](INSTALL.md#6-record-the-recovery-values) to select `PREVIOUS_HOME`. On SHIELD TV, the restore always uses `com.google.android.tvlauncher/.MainActivity`.

Record the values in a text file in the working folder. Name it `telum-restore.txt`. Write these items in the file:

- The date, `TV_SERIAL`, `USER_ID`, and `PREVIOUS_HOME`.
- The list of disabled packages from step 6.
- The restore commands from [Restore the previous Home app](INSTALL.md#restore-the-previous-home-app), with the values filled in.
- This note: "The restore commands need debugging on. To turn debugging on again, you must open Settings through Telum."

In guide mode, give the user the text of this file. Tell the user to keep it.

### Stage 7: installation

If Telum is already installed, record its version. Tell the user to look at the TV during the installation. A Google Play Protect message can show.

If the result is `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, do not remove Telum yourself. Explain the options from [Signature mismatch](TROUBLESHOOTING.md#signature-mismatch). Let the user select.

### Stage 8: setup

The user does the setup with the remote. Tell the user what each screen does. Do not select options for the user. Tell the user to stop at **Make Telum your Home?** if they want to try Telum first.

### Stage 9: Home

Explain the change before you start. Tell the user how to restore the previous Home app.

On SHIELD TV:

1. Tell the user to select **Make Telum Home**. Then tell the user to select **Telum** in the Android list. If Android asks for confirmation, the user selects **Set as default**.
2. Telum shows **Android TV Home still opens first**. This is the correct result on SHIELD TV. If Telum shows no message, the user selected **Not now** or **Cancel**. Do step 9.1 again.
3. Explain that the next command disables the original Android TV Home app. Explain that `pm enable` restores it.
4. Get the agreement of the user.
5. Do [step 9.2](INSTALL.md#92-shield-tv-disable-the-original-home-app).
6. Do the tests in [step 9.4](INSTALL.md#94-test-the-home-app). Ask the user before the restart.

On other devices:

1. Do [step 9.1](INSTALL.md#91-use-the-telum-question).
2. If Telum shows **Telum is now your Home**, do the tests in step 9.4.
3. If Telum shows **... still opens first**, stop the Home change. Tell the user that Telum stays available as an app. Do not disable the Home app of that device. This guide has no tested procedure for it.
4. If Telum shows **Your TV can't switch Home from here**, do [step 9.3](INSTALL.md#93-set-the-home-app-with-adb). Then do the tests.

After the restart, connect ADB again. Do the `resolve-activity` command from step 9.2. The last line must be `dev.telum.launcher/.MainActivity`.

If a test fails, do the restore procedure first. Then find the cause.

### Stage 10: features

Give the user the table from [step 10](INSTALL.md#10-set-the-optional-features). The user selects each feature. Tell the user what each feature sends to a provider, as the table shows.

### Stage 11: finish

Give the completion report. Tell the user to keep `telum-restore.txt`.

Explain the debugging table in [step 11](INSTALL.md#11-finish). Recommend that debugging stays on while Telum is the Home app. If the user wants it off, explain that recovery then needs Telum to operate. Let the user decide.

## Troubleshoot a problem

1. Read the full error. Find it in the [troubleshooting guide](TROUBLESHOOTING.md).
2. Explain the cause to the user in simple words.
3. Do one correction. Then do the failed step again one time.
4. If the same error occurs again, stop. Do not try the same command again.
5. Collect the [diagnostic information](TROUBLESHOOTING.md#collect-diagnostic-information).
6. If the troubleshooting guide does not have the error, explain what you know and what you do not know. Do not guess a command that changes the device.
7. If the user wants, help the user write an issue with the **Installation problem** form. Remove IP addresses, serial numbers, and account data first.

If the TV device shows no home screen at any time, do the [emergency restore](TROUBLESHOOTING.md#emergency-restore) first.

## Speak to the user

- Use simple words. Explain ADB, APK, and Home app when you use them the first time.
- Give one TV step in each message. Tell the user which button to push.
- Tell the user what the TV shows after each step.
- Tell the user when a step takes time, for example a download or a restart.
- If the user is not sure, ask a question. Do not continue with a guess.

## Completion report

Give this report at the end. Fill in each item. Write "not done" for items that you did not do.

```text
Telum installation report
- TV device: <model>, Android <version>
- Computer: <Windows, macOS, or Linux>, working mode: <local or guide>
- Telum: version <version>, from <release address>, checksum <OK or not examined>
- Installation: <Success or the error>
- Setup: <completed or not completed>
- Home app: <Telum, or the previous Home app>; tests: <Home, app return, restart, remote>
- Original Home app: <enabled or disabled>
- Debugging: <on or off>, decided by the user
- Optional features: <the features that the user turned on and tested>
- Restore: see telum-restore.txt; command: <the restore commands>
- Problems: <the problems that remain, or none>
```

Keep IP addresses and serial numbers out of public reports. Do not keep pairing codes.

These instructions support different AI agents. They do not guarantee that each agent follows them correctly. The user keeps control of the TV device.
