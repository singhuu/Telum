# Test results and limits

This report separates product behavior from performance measurements. It does not establish that Telum is faster than every stock launcher.

## Benefits over the tested stock home screen

The inspected SHIELD stock home screen showed store and promotional tiles. Telum places the installed apps at the center of the home screen. It adds no store tiles or sponsored rows.

You can move or hide app tiles. You can choose which app content appears. Weather and artist photos are optional. Music playback can use a dim artwork display after 60 seconds without input.

These are layout and control benefits. They do not depend on a claimed speed increase. Streaming apps still control their own content and advertisements.

## Current Telum build

Test date: 19 October 2026. Source baseline: initial public source commit `00de2d3f145f9ba4a2dce1c4353b6cbf5b53f3a2`.

The test used an Android TV 11 x86 emulator. The APK used release optimization and a local debug key for testing. It was not an official signed release.

Test APK SHA-256:

```text
DA02E7C656CD129805D09D4B0DFD68F82F0C9275D578012807D6C7BF5C05B1A4
```

| Check | Observed result | Limit |
| --- | --- | --- |
| Playing media, settled home screen | One frame in approximately 28 seconds. | One emulator observation with a synthetic media session. |
| Paused media, settled home screen | One frame in approximately 45 seconds. | Measures app drawing, not whole-device CPU use or power. |
| Music idle transition | Dim artwork appeared after 60 seconds without input. | Synthetic media session, not Spotify account playback. |
| First navigation press | The ordinary home screen returned with the previous focus. No app opened. | The tested D-pad path only. |
| Pause through the on-screen control | Playback state changed to paused. | Global remote media-key routing was not established. |
| Builds and code checks | Behavior checks, debug/release builds, and debug/release lint passed. | Lint reported zero errors and six warnings. |

The behavior checks cover the idle timer, app profiles, and animation settling. Both the development project and the independent public export passed the build checks.

The emulator checks used a synthetic publisher with a recognized music package name. They do not establish Spotify or TuneIn service behavior. Netflix subscriber content was not tested.

## Earlier physical SHIELD checks

Earlier development builds ran on an Android 11 SHIELD identified as product `mdarcy`. The owner identified the box as a SHIELD Pro. The exact retail generation was not independently confirmed.

On 18 October 2026, development test 7 drew one frame during a 12-second paused-media interval. This was an earlier package and source revision. It does not establish the current Telum build's physical-device performance.

## Why there is no stock speed or memory percentage

The stock launcher memory snapshots changed after a reboot and package cleanup. Uptime and installed software differed between those measurements. Later launcher builds also had different features.

There is no controlled comparison of the current Telum build and the stock launcher under matching conditions. A percentage speed or memory claim would therefore be unsupported.

Telum has not demonstrated improved video decoding, network streaming, or recovery from a streaming-app fault. The dim display also does not establish protection against screen burn-in.

## Repeat the checks

Use the [device test checklist](TESTING.md) for functional checks. Record the source commit, APK checksum, signing type, device model, Android version, and enabled features.

For an idle drawing check, substitute the verified TV serial:

```sh
adb -s "TV_SERIAL" shell dumpsys gfxinfo dev.telum.launcher reset
```

1. Leave the settled home screen untouched for the selected interval.
2. Record the interval and playback state.
3. Read the drawing report.

```sh
adb -s "TV_SERIAL" shell dumpsys gfxinfo dev.telum.launcher
```

4. Record `Total frames rendered` if the device exposes it.
5. Repeat the check after an app return and a screensaver wake.

An animation, clock update, or artwork change can add frames. Do not convert this count into a whole-device power claim.

For a stock comparison, keep the device, uptime, apps, display mode, network, and interaction sequence consistent. Run repeated trials for each launcher. Report the full conditions with the results.

This report summarizes local test records. It is not an independent benchmark. Private device logs and account data are excluded from the public repository.
