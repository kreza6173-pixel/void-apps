# Release guide (1.0.0)

Everything here can be done from the phone. The keystore never enters the repository or a chat.

## 1. Signing keys (GitHub secrets)

The CI workflow already contains a signed release step. It runs only when these four secrets exist in **this** repository (Settings > Secrets and variables > Actions > New repository secret). The names are the same as in PULSE // BATTERY, so the same keystore can be reused.

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

Next CI run uploads `app-release` next to `app-debug` and prints the certificate fingerprint in the run summary. Without secrets the step is skipped and CI stays green.

Keep the `.jks` file and its password in two separate safe places. Losing either means no update can be installed over the released app. Delete the `.b64` file after saving the secret. A release-signed APK has a different signature from the CI debug APK, so moving from debug to release needs one uninstall.

## 2. Images

Store images live in `fastlane/metadata/android/en-US/images/` (`icon.png` 512x512, `featureGraphic.png` 1024x500, `phoneScreenshots/1.png` to `6.png`). `docs/images/social-preview.png` (1280x640) goes to GitHub Settings > General > Social preview. `docs/images/profile-banner.png` (1500x500) is for profiles and posts.

## 3. Merge and tag

1. Merge the pull request `native-app-v0` into `main`.
2. Create the tag and GitHub release `v1.0.0` on `main` (Releases > Draft a new release > tag `v1.0.0`), attach the signed `app-release.apk` from the CI run of that commit.

## 4. F-Droid

1. Fork `https://gitlab.com/fdroid/fdroiddata`.
2. Add `docs/fdroid/io.github.kreza6173pixel.voidapps.yml` from this repository as `metadata/io.github.kreza6173pixel.voidapps.yml`.
3. Open a merge request. F-Droid builds the app itself from the `v1.0.0` tag and signs it with its own key (unless reproducible builds are set up later).

Checks already done for F-Droid: no INTERNET permission, no proprietary libraries (Shizuku API and AndroidX only), the Google dependency-info block is disabled in `build.gradle.kts`, MIT license, fastlane metadata present.
