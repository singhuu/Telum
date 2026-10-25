# Test results and limits

This report keeps the product behavior and the performance measurements separate. It does not show that Telum is faster than each original home screen.

## Differences from the tested original home screen

The original home screen of the tested SHIELD TV showed store tiles and promotional tiles. Telum puts the installed apps at the center of the home screen. It adds no store tiles and no sponsored rows.

You can move and hide app tiles. You select which app content shows. Weather and artist photos are optional. During music, a dim artwork display can show after 60 seconds without input.

These are differences in layout and control. They do not depend on a speed increase. Streaming apps still control their own content and advertisements.

## Version 0.1.0

| Check | Result | Limit |
| --- | --- | --- |
| Build checks | Behavior checks, debug and release builds, and debug and release lint passed. Lint reported zero errors. | Build checks do not test a TV device. |
| Setup and Home question, Android TV 11 emulator | The automatic regression test passed all its checks on two emulator images. It included the intro, the setup, the Home question, and a restart. | An emulator does not have the hardware, the remote, or the device software of a SHIELD TV. |
| SHIELD TV | Debug builds with the same features operated on a SHIELD TV during development. | The release notes give the tests of the signed release APK. |

The emulator does not give useful frame times for the intro. It translates OpenGL ES on the computer.

## Earlier builds

These results come from earlier source revisions. They do not prove the performance of version 0.1.0.

Emulator, 19 October 2026, Android TV 11 x86, release optimization with a local test key:

| Check | Result | Limit |
| --- | --- | --- |
| Playing media, settled home screen | One frame in approximately 28 seconds. | One observation with a simulated media session. |
| Paused media, settled home screen | One frame in approximately 45 seconds. | This measures app drawing. It does not measure the CPU use or the power of the device. |
| Music idle display | Dim artwork showed after 60 seconds without input. | Simulated media session, not a Spotify account. |
| First push of an arrow | The home screen showed again with the previous highlight. No app opened. | The tested arrow path only. |

SHIELD TV, Android 11, product `mdarcy`, identified by the owner as a SHIELD TV Pro:

- On 18 October 2026, a development build drew one frame during a 12-second interval with paused media.
- In October 2026, the earlier intro drew 534 frames in 8.9 seconds at 1920 × 1080. The median frame time was 16.7 ms. The 90th percentile was 16.9 ms.

## Why there is no speed or memory percentage

The memory values of the original home screen changed after a restart and a package cleanup. The uptime and the installed software were different between those measurements. Later Telum builds also had different features.

No controlled comparison of version 0.1.0 and the original home screen exists. A percentage for speed or memory has no evidence.

Telum does not improve video decoding, network streaming, or the recovery of a streaming app. The dim display does not prove protection against screen burn-in.

## Repeat the checks

Use the [release checks](TESTING.md) for the functions. Record the source commit, the APK checksum, the signature type, the device model, the Android version, and the features that are on.

To count the frames that Telum draws when it is idle:

1. Reset the counter:

```sh
adb -s "TV_SERIAL" shell dumpsys gfxinfo dev.telum.launcher reset
```

2. Do not touch the settled home screen for the interval that you select.
3. Record the interval and the playback state.
4. Read the report:

```sh
adb -s "TV_SERIAL" shell dumpsys gfxinfo dev.telum.launcher
```

5. Record `Total frames rendered`, if the device shows it.
6. Do the check again after you return from an app, and after the screensaver.

An animation, a clock update, or new artwork can add frames. Do not use this count to make a statement about the power of the device.

For a comparison with the original home screen, keep these conditions the same: the device, the uptime, the apps, the display mode, the network, and the sequence of actions. Do each test more than one time for each home screen. Report all the conditions with the results.

This report summarizes local test records. It is not an independent benchmark. The public repository does not contain private device logs or account data.
