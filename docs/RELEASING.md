# Building a release

## Source publication

Publish this clean repository, whose root contains `app/` and `gradlew`. The private development repository and its history are not part of this export. Review `git ls-files` and `git log --all` before adding a GitHub remote. Do not push the original development repository or its tags.

The repository deliberately excludes device diagnostics, backups, screenshots, prototypes, sample-content apps, machine configuration, APKs and signing keys. GPL-3.0-only applies to Telum's original source. Include source corresponding to distributed binaries and retain third-party notices.

## Signing

Debug builds are for local testing. They use Android's local debug keystore and are not public release artifacts.

Release builds are minified and unsigned by default:

```sh
./gradlew :app:assembleRelease :app:lintRelease
```

To sign your own release, set all four environment variables before building:

- `TELUM_KEYSTORE`: absolute path to your private keystore.
- `TELUM_STORE_PASSWORD`: keystore password.
- `TELUM_KEY_ALIAS`: signing key alias.
- `TELUM_KEY_PASSWORD`: key password.

Keep the keystore and passwords outside the repository. Preserve the same key for future updates. The build rejects partially supplied credentials. Never distribute an APK signed with a debug key as an official release.

Verify the APK with Android build tools: `apksigner verify --verbose --print-certs PATH_TO_APK`. Confirm the certificate is your release certificate. Archive the matching R8 `mapping.txt` privately for diagnostic retracing. Test the signed APK on a TV before publishing a release.

GitHub CI builds a debug test artifact and an unsigned release artifact. It does not publish a release or hold signing credentials.

## Installation information for a public release

Publish the following information with each signed APK:

- The version name and version code.
- The source tag or commit.
- The APK filename and SHA-256 checksum.
- The release certificate SHA-256 fingerprint from `apksigner`.
- The minimum Android version and known device limits.
- The device tests completed with this exact APK.
- Any update restrictions or changes to permissions.

Link the [agent guide](INSTALL-AGENT.md) and [manual guide](INSTALL.md) from the release notes. Keep the APK, checksum, and corresponding source available together. Do not label an emulator-only test as a physical SHIELD test.

## Service terms

TheAudioDB's [API terms](https://www.theaudiodb.com/docs_terms_of_use.php) distinguish free use from app-store distribution. Check their current terms and arrange the required subscription before publishing a build through an app store. The source uses the documented shared free API key, not a private credential. Do not put a private key into a public repository.

Open-Meteo has [usage terms](https://open-meteo.com/en/terms) and attribution requirements. Confirm the appropriate service plan for your distribution. The app includes provider attribution; source-code licensing does not grant rights to third-party artwork or services.
