# Contributing

## Before you submit a change

1. Build the app and run the checks:

```sh
./gradlew :app:behaviorChecks :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease
```

2. Test the change on an Android TV device or emulator.
3. In the pull request, give the Android TV version. Tell how a person with a remote gets to the changed behavior.

## Code rules

- Keep network work and bitmap decoding off the UI thread.
- Do not add continuous animation to an idle home screen.
- Keep each network feature optional and off until the user turns it on.
- Keep the fallbacks for refused permissions.
- Do not log account data or media titles.

## App support

For a new app profile, give the exact package IDs and a reference from an official store or the publisher. Keep a color profile separate from tested recommendations with a signed-in account.

## Assets and licenses

Use GPL-3.0-only for original contributions. Do not submit copyrighted posters, account screenshots, tokens, or keys.

The intro sound comes from `tools/make-intro-hum.py`. Change the script, then run it again. Do not edit the `.ogg` files directly.

## Documents

Write documents in [ASD-STE100](https://www.asd-ste100.org/) Simplified Technical English:

- Keep instructions to 20 words or fewer in each sentence.
- Keep descriptions to 25 words or fewer in each sentence.
- Write one instruction in each sentence. Use the command form of the verb.
- Use one term for one concept. The installation guide lists the terms.
- Put a caution before the step that it applies to.
- Keep the exact command names and screen labels as technical names.
