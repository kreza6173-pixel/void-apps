# Release guide (1.0.0)

Everything here can be done from the phone. The keystore never enters the repository or a chat.

## Status (2026-10-05)

| Step | State |
|---|---|
| 1. Signing secrets | done, CI builds `app-release` |
| 2. Images | done, icon, featureGraphic, 12 screenshots, social preview, profile banner |
| 3. Merge | done, PR #1 merged into `main` (`7dc6cc8`) |
| 3. Tag + GitHub release `v1.0.0` | owner |
| 4. F-Droid merge request | after the tag exists |
| 5. awesome-shizuku pull request | after the release has a downloadable APK |

## 1. Signing keys (GitHub secrets)

The CI workflow contains a signed release step. It runs only when these four secrets exist in **this** repository (Settings > Secrets and variables > Actions > New repository secret). The names are the same as in PULSE // BATTERY, so the same keystore can be reused.

| Secret | Value |
|---|---|
| `PULSE_KEYSTORE_BASE64` | one-line base64 of the `.jks` file |
| `PULSE_KEYSTORE_PASSWORD` | keystore password |
| `PULSE_KEY_ALIAS` | key alias (for example `pulse`) |
| `PULSE_KEY_PASSWORD` | key password (equals the store password for PKCS12) |

New keystore in Termux, if you do not want to reuse the PULSE one:

```sh
pkg install openjdk-17
keytool -genkeypair -v -keystore void-release.jks -alias void \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 void-release.jks > void-release.b64
termux-setup-storage
cp void-release.jks void-release.b64 ~/storage/downloads/
```

Each CI run then uploads `app-release` next to `app-debug` and prints the certificate fingerprint in the run summary. Without secrets the step is skipped and CI stays green.

Keep the `.jks` file and its password in two separate safe places. Losing either means no update can be installed over the released app. Delete the `.b64` file after saving the secret. A release-signed APK has a different signature from the CI debug APK, so moving from debug to release needs one uninstall.

Secrets belong to the repository, not to a branch: keeping or deleting `native-app-v0` does not affect them.

## 2. Images

- `fastlane/metadata/android/en-US/images/icon.png` (512x512)
- `fastlane/metadata/android/en-US/images/featureGraphic.png` (1024x500)
- `fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg` to `12.jpg` (only images in that folder)
- `docs/images/social-preview.png` (1280x640), also set in GitHub Settings > General > Social preview
- `docs/images/profile-banner.png` (1500x500), for profiles and posts

Icon, feature graphic and banners are drawn from the launcher vector `ic_launcher_foreground.xml`.

## 3. Merge and tag

1. Merge `native-app-v0` into `main` (done for 1.0.0 with PR #1).
2. Create the tag and GitHub release `v1.0.0` on `main` (Releases > Draft a new release > tag `v1.0.0`, target `main`) and attach the signed `app-release.apk` from the CI run of that commit.
3. Delete old releases and tags of the earlier WebUI module (`1.2.0`, `v1.1.2`).

Later updates: work and test on `native-app-v0`, raise `versionCode` / `versionName` in `android-app/app/build.gradle.kts`, add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and a CHANGELOG entry, open a PR into `main`, merge, tag `vX.Y.Z`.

## 4. F-Droid

1. Fork `https://gitlab.com/fdroid/fdroiddata`.
2. Add `docs/fdroid/io.github.kreza6173pixel.voidapps.yml` from this repository as `metadata/io.github.kreza6173pixel.voidapps.yml`.
3. Open a merge request. F-Droid builds the app itself from the `v1.0.0` tag and signs it with its own key (unless reproducible builds are set up later).

Checks already done for F-Droid: no INTERNET permission, no proprietary libraries (Shizuku API and AndroidX only), the Google dependency-info block is disabled in `build.gradle.kts`, Gradle wrapper present, MIT license, fastlane metadata and images present.

## 5. awesome-shizuku

Pull request to `timschneeb/awesome-shizuku` (`README.md`), alphabetical order, format from its `CONTRIBUTING.md`:

```
* [VOID // APPS](https://github.com/kreza6173-pixel/void-apps) - App manager: suspend, disable, debloat presets, permissions and AppOps, autostart, per-app network block, APK/XAPK installer and cleaner. Every change is read back from Android. `MIT`
```

It needs a downloadable APK first, so it is sent after the GitHub release exists.
