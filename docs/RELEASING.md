# Release Telum

This procedure is for the maintainers of the repository. GitHub Actions builds and signs each release APK from a version tag. The [release workflow](../.github/workflows/release.yml) makes a draft release. A maintainer tests the APK of the draft on a TV device, and then publishes it.

## Build types

| Build | Signature | Use |
| --- | --- | --- |
| Debug APK | The local debug key of the computer that built it. | Tests on your own devices only. |
| Release APK from the [build workflow](../.github/workflows/build.yml) | No signature. | Build checks only. It cannot install. |
| Release APK from the release workflow | The release key. | Public releases. |

Android updates an app only with an APK that has the same signature. All public releases must use the same release key.

## Prepare the release key one time

Do this procedure one time for the repository.

> [!CAUTION]
> Keep two backup copies of the key file and its password. If you lose the key, you cannot publish updates. Users must then remove Telum and lose their settings to install a new key.

1. Make a folder for the key outside the repository. For example, `Documents/telum-signing`.
2. Open a terminal in that folder.
3. Make the key. Use `keytool` from JDK 17 or newer:

```sh
keytool -genkeypair -v -storetype PKCS12 -keystore telum-release.p12 -alias telum -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Telum"
```

4. Type a strong password two times when `keytool` asks. Record it in a password manager.

The certificate shows only `CN=Telum`. Do not put a personal name or an email address in the certificate. Each APK shows the certificate to all users.

A PKCS12 key file uses one password for the file and for the key.

5. Record the SHA-256 fingerprint of the certificate:

```sh
keytool -list -v -keystore telum-release.p12 -alias telum
```

Keep the `SHA256:` value. Each release must show the same value. `keytool` shows the value in capital letters with colons. The release notes and `apksigner` show it in small letters without colons. The digits are the same.

6. Copy the key file and the password to two safe places. For example, use a password manager and an offline USB drive.

## Add the secrets to GitHub one time

The release workflow reads four repository secrets. GitHub keeps them encrypted. Workflow logs do not show them.

| Secret | Value |
| --- | --- |
| `TELUM_KEYSTORE_BASE64` | The key file in Base64 text. |
| `TELUM_STORE_PASSWORD` | The password of the key file. |
| `TELUM_KEY_ALIAS` | `telum` |
| `TELUM_KEY_PASSWORD` | The same password as `TELUM_STORE_PASSWORD`. |

1. Convert the key file to Base64 text and copy it.

Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\telum-release.p12")) | Set-Clipboard
```

macOS:

```sh
base64 -i telum-release.p12 | pbcopy
```

Linux:

```sh
base64 -w0 telum-release.p12
```

2. On GitHub, open the repository. Select **Settings** › **Secrets and variables** › **Actions**.
3. Select **New repository secret**. Type the name `TELUM_KEYSTORE_BASE64`. Paste the Base64 text. Select **Add secret**.
4. Add the other three secrets the same way.
5. Clear the clipboard. For example, copy a different short text.

With the [GitHub CLI](https://cli.github.com/), you can also use `gh secret set NAME`. The command asks for the value.

Turn on two-factor authentication for your GitHub account. A person with access to your account can start the release workflow.

## Publish a release

1. Increase `versionCode` by one in `app/build.gradle.kts`. Set `versionName` to the new version, for example `0.1.1`.
2. Add a section for the new version to [CHANGELOG.md](../CHANGELOG.md). Its heading is `## 0.1.1`. The release notes copy this section.
3. Commit the changes and push them to `main`.
4. Make sure that the **Android build** workflow passed for that commit.
5. Make the version tag and push it:

```sh
git tag -a v0.1.1 -m "Telum 0.1.1"
git push origin v0.1.1
```

The tag must be `v` followed by `versionName`. If they are different, the release workflow stops.

6. Open **Actions** › **Release** on GitHub. Wait until the workflow passes.
7. Open **Releases**. The new release has the label **Draft**. Only maintainers see a draft.
8. Download `telum.apk` and `SHA256SUMS` from the draft. Make sure that the checksum is correct.
9. Make sure that the **Signing certificate SHA-256** in the draft notes is the fingerprint that you recorded.
10. Install this exact APK on a SHIELD TV. Do the [release checks](TESTING.md). If possible, also test another Android TV device.
11. Edit the draft. Replace the line that starts with `MAINTAINER:` with the device tests. Give the device model, the Android version, the date, and the results.
12. Select **Publish release**. Make sure that **Set as the latest release** is selected.

After you publish, the address `https://github.com/singhuu/Telum/releases/latest/download/telum.apk` downloads the new APK.

13. Open the workflow run. Download the `telum-VERSION-mapping` artifact. Keep it with your release records. GitHub deletes it after 90 days. You need it to read the stack traces of a release build.

## If a release fails

| Problem | Correction |
| --- | --- |
| The workflow says that a secret is not set. | Add the secret. Then select **Re-run all jobs** in the workflow run. |
| The tag does not match `versionName`. | Delete the tag on GitHub and on your computer. Correct the version. Make the tag again. |
| The build or a check fails. | Read the log. Correct the source. Delete the tag. Make the tag again on the corrected commit. The workflow makes no draft when it fails before the last step. |
| The workflow says that `CHANGELOG.md` has no section. | Add the section `## VERSION`. Delete the tag. Make the tag again on the new commit. |
| The APK fails the device tests. | Do not publish the draft. Delete the draft and the tag. Correct the source. Use a new version code. |
| The fingerprint is different. | Do not publish. Make sure that the secrets contain the correct key file. |
| The attestation step fails. | The repository must be public. Artifact attestations need a public repository on a free GitHub plan. |

To delete a tag:

```sh
git tag -d v0.1.1
git push origin :refs/tags/v0.1.1
```

Do not change a release after you publish it. If a published APK has a problem, publish a new version with a higher version code.

## Source and licenses

Each release tag identifies the source of its APK. GPL-3.0-only applies to the original source of Telum. Keep the source of each published APK available. Keep the third-party notices in the repository and in the app.

The repository does not contain device diagnostics, backups, screenshots of accounts, machine settings, APK files, or signing keys. Do not add them.

## Service terms

TheAudioDB has [API terms](https://www.theaudiodb.com/docs_terms_of_use.php). They make a difference between free use and distribution through an app store. Read the current terms before you publish Telum through an app store. The source uses the documented shared free API key. Do not put a private key in the repository.

Open-Meteo has [terms of use](https://open-meteo.com/en/terms) and attribution requirements. Make sure that your distribution agrees with their service plans. The app shows the attribution of the provider. The source license does not give rights to third-party artwork or services.
