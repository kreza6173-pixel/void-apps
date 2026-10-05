# VOID // APPS: complete project handoff

Updated: 2026-10-05
Branches: `main` (released code), `native-app-v0` (development and testing, kept on purpose)
Application ID: `io.github.kreza6173pixel.voidapps`
Reference device: Xiaomi Redmi Note 14, HyperOS Global ROM, Android 16, SDK 36, Shizuku shell uid 2000. The phone is now also rooted (KernelSU); all evidence below was measured with Shizuku as uid 2000.

This is the durable record of product decisions, engineering rules, phone evidence and remaining work.

## Product idea

VOID // APPS is a local Kotlin + Jetpack Compose Android package manager using Shizuku UserService/AIDL. It has no INTERNET permission, refuses protected packages, performs reversible operations and reads state back before reporting success.

## Engineering rules

No INTERNET permission. Commands only through Shizuku and ExecBridge. Validate and quote package and permission names. Every write is read back; APPLIED means the new state matches. Protected packages are refused in the repository. Code and resources land together. Diagnose CI failures from logs. ExecBridge output is capped at 64 KiB, so shell filters are measured on the phone before being relied on and extra reads are non-fatal. A write control is shown only after a phone probe proves it works.

## Working agreement with the owner

Implement a whole section (code, tests, wiring, strings) before asking for a phone test. The owner tests the section as one unit, then the next section starts. No test requests after single-line changes. When a sub-feature fails on the phone and the owner says skip it, it is recorded here as an open item and left for later. New work happens on `native-app-v0` and reaches `main` through a pull request.

## Status

| Step | Status | Evidence |
|---|---|---|
| M0 core | done | Shizuku READY, uid 2000 |
| A1 inventory + guard + details | done | Counts match pm on reference phone |
| A2 package operations | done | Suspend, remove, restore and read-back phone-verified |
| A3 snapshots, undo, pins, batch | done | Phone-verified |
| Debloat | done | CI green and phone acceptance |
| A4 permissions + AppOps | done on reference phone | Self-check system 311/311 clean; user 463/463; HyperOS ask parsed read-only |
| A5 autostart | partly done | Audit and fully qualified component round trip phone-verified; shorthand receivers OPEN |
| A6 notifications | partly done | Mute phone-verified; DND access NOT_APPLIED on HyperOS and left open |
| A7 network | done | Block/unblock and background restrict/allow APPLIED on user, system and protected apps |
| A8 installer + cleaner | done | Owner phone test: every feature worked, running-apps stop fixed |
| 1.0 release | merged | Signing secrets set, CI green with `app-release`, PR #1 merged into `main` (`7dc6cc8`); GitHub release, F-Droid and awesome-shizuku pending, see `docs/RELEASE.md` |
| Root track | future only | Catalog in `docs/ROOT_FUTURE.md` |

## Acceptance record

- Permission Grant/Revoke round trips were verified on Drive, Meet, Play Store and Acode.
- Shared system uid `android.uid.system/1000` is read-only and refuses writes.
- AppOps uid/package scope split was verified on Drive, securitycenter and Acode.
- Package-scope AppOps changes work only when no non-default uid mode overrides them. Uid-scope changes are disabled after the ROM silently kept CAMERA and CALL_PHONE changes.
- `MIUIOP(n)` is read-only. HyperOS `MIUIOP(10017): ask` is now a recognised read-only mode.
- Self-check system group: 311/311, no permission errors, cap hits, AppOps errors, unsplit output or unknown lines.
- A5: boot receivers listed correctly; fully qualified receiver disable/enable round trip APPLIED.
- A6: POST_NOTIFICATIONS mute and restore were phone-tested; permissions card read-back showed not granted then granted.
- A6: DND allow returned exit 0 but read-back remained not granted. The UI correctly reported NOT_APPLIED and did not claim success.
- A8: owner tested the installer, extract, cache trim, empty folders and running apps; all worked as expected and the void-purge app-killer bug is gone.
- A7: `com.google.android.apps.docs` Block APPLIED ("Disabled networking for ..., appId 10176"); Brave blocked and really lost DNS; background data Allow APPLIED on uids 10449 and 14569; Chain 3 read back as on; shared uid 10093 (downloads provider, MTP, sound picker) shown with the warning and no buttons.

## A5 open item (left for later by the owner)

Component control for receivers listed in shorthand form (`pkg/.Cls`) is not reliable in the UI.

- Seen on `com.thirtytwo.steps/.BootReceiver`, `ch.abertschi.adfree/.StartOnBootListener` and `com.eyalm.adns/.services.BootReceiver`.
- The repository and device agree those components are disabled, but the card still shows Disable. Cause confirmed by code review: `isComponentDisabled()` never expands `pkg/.Cls` to `pkg/pkg.Cls` before comparing (see `docs/PLAN.md`).
- Both components above are still disabled on the reference phone. Restore with fully qualified `pm enable` commands from the console if needed.

## A6 open item (left for later by the owner)

DND access control on HyperOS is not reliable and is intentionally left open.

- On `com.thirtytwo.steps`, `cmd notification allow_dnd 'com.thirtytwo.steps'` returned exit 0 with no output.
- Read-back from `enabled_notification_policy_access_packages` stayed unchanged: before not granted, after not granted.
- This is treated as **NOT_APPLIED**, not success. Possible future path: inspect `dumpsys notification` and test the ROM-specific AppOps/policy mechanism, or a uid 0 retry (see `docs/ROOT_FUTURE.md`).
- Per-app mute works and was phone-verified. The tested app had no notification listener service, so there was no listener toggle to test there.

## A7 network (done)

Source: `VOID-WALL` `webui/wall.js`. Files: `inventory/NetworkAccess.kt`, `inventory/NetworkRepository.kt`, `ui/apps/NetworkAccessCard.kt`, `res/values/strings_network.xml`, `NetworkAccessTest.kt`. Block uses Chain 3 (`set-chain3-enabled true` once, then `set-package-networking-enabled`), read back with `get-package-networking-enabled`; background data uses `netpolicy restrict-background-blacklist`. All phone-verified, see the acceptance record.

## A8 installer + cleaner (done)

Sources: `pulse-install` (`webui/script.js`, `service.sh`) and `void-purge` (`webui/index.html`); the owner says every command there worked on his phone.

- Files: `install/InstallLogic.kt` (pure parsers and rules), `install/Axml.kt` (binary manifest reader), `install/InstallerRepository.kt` (browse, inspect, install, auto folder, extract), `install/CleanerRepository.kt` (cache, empty folders, running apps), `ui/tools/ToolsScreen.kt`, `ui/apps/ExtractCard.kt`, `res/values/strings_tools.xml`, `test/.../install/InstallLogicTest.kt`; wiring in `MainActivity.kt`, `HomeScreen.kt`, `AppDetailScreen.kt`.
- The manifest is read with `unzip -p <apk> AndroidManifest.xml | gzip -c | base64` (plain base64 fallback) so large manifests stay under the 64 KiB cap.
- Work directory for bundles: `/data/local/tmp/.void-install-work/`, always removed afterwards.
- Installs are refused only for the static core list (framework, System UI, Shizuku, installers...). Updating Shizuku through Shizuku would cut the session mid-install.
- Running apps bug in void-purge: it never listed processes; it force-stopped every `pm list packages -3` package inside `( ... ) >/dev/null 2>&1 &`, so the result was invisible and the bridge could end the background children. Rebuilt on `ps` with per-package read-back.
- Kept out: APKM, VirusTotal, AI assistant, void-purge orphans / logs / duplicates / root tools.

## Remaining work

1. Owner: GitHub release `v1.0.0` on `main` with the signed APK; delete the old `1.2.0` / `v1.1.2` releases.
2. F-Droid merge request, then the awesome-shizuku pull request (`docs/RELEASE.md` sections 4 and 5).
3. Owner, by hand: in `.github/workflows/ci.yml` delete the stale first comment line ("Copy this file to ...") and rename the heading `Cyber App Manager build failed` to `VOID // APPS build failed`. The connector cannot write workflow files.
4. Branch protection on `main` (block force push and deletion only).
5. A5 shorthand receiver UI state (optional, owner decides when).
6. A6 DND access on HyperOS (optional, owner decides when).
7. In-app About screen (optional).
8. Root track, only if the owner opens it: `docs/ROOT_FUTURE.md`.

## Release 1.0.0

- `versionCode 100`, `versionName 1.0.0` in `android-app/app/build.gradle.kts`.
- Store metadata: `fastlane/metadata/android/en-US/` (title, short and full description, changelog `100.txt`, `icon.png`, `featureGraphic.png`, twelve phone screenshots `phoneScreenshots/1.jpg` to `12.jpg` taken on the reference phone).
- `docs/images/social-preview.png` (GitHub social preview) and `docs/images/profile-banner.png`. Icon, feature graphic and banners are drawn from the launcher vector.
- `docs/fdroid/io.github.kreza6173pixel.voidapps.yml`: draft for fdroiddata, builds `subdir: android-app/app` from tag `v1.0.0`.
- `docs/RELEASE.md`: status table, signing secrets (same names as PULSE // BATTERY), images, merge, tag, F-Droid, awesome-shizuku.
- The old Shevery WebUI module (Cyber App Manager: root `*.sh`, `module.prop`, `webui/`, `docs/screenshots`) and the duplicate `docs/ci.yml` were removed before release; they remain in git history. F-Droid builds only `android-app/app`.
- Still named after the old project, on purpose: the Kotlin package `io.github.kreza6173pixel.cyberappmanager` (internal only, the application ID is `voidapps`).

## Safety decisions

AppOps is separate from runtime permissions. OEM operations are shown but never changed. Self-check never writes. ROM-specific differences are expected and supported through issue reports. `com.miui.securitycenter` stays unprotected, but system-uid writes are refused. Dhizuku and APKM are out of scope. Root features are recorded for later in `docs/ROOT_FUTURE.md` and are not part of the current track.

## Commit trail

- `e587840`: anchored AppOps parser, CI #56 green.
- `9055b5a`: measured permission audit sections.
- `0d4cd24`: shared-uid permissions.
- `dc3ec81`, `090c376`, `873a114`: guarded AppOps changes and phone findings.
- `ba860ea`: Self-check, ROM report template and system-uid refusal.
- `d8f885a`: HyperOS `ask` mode read-only.
- `54b9703`: permission-section extractor; system Self-check 311/311 clean.
- `077746ab`: last owner-verified commit before A5 work (CI #71).
- `07ccf3b`: A5 read-only audit rebuilt clean.
- `84ad3c9`, `91082cb`, `a070171`, `eed9ff5`, `f82f0fb`: A5 component control and read-back attempts; shorthand UI state left open.
- `f3c8266`: A5 status recorded, shorthand item left open.
- `21f03bb`: A6 notification card with mute, listener access, DND access and 10 unit tests.
- `b0350d5`, `3b89063`: A6 docs and DND open item.
- `f1d5241`: A7 network card (Chain 3 block, netpolicy background data) and 10 unit tests.
- `8d28882`: A7 docs and `docs/ROOT_FUTURE.md`.
- `e6bd37c`, `de0efab`, `3d3c9d1`: A8 installer + cleaner, Install & clean screen, Extract card, 11 unit tests.
- `ec0a966`: A8 docs.
- `0d9f6df` 1.0.0 release files (README, CHANGELOG, fastlane text, F-Droid draft, docs/RELEASE.md, versionCode 100 / 1.0.0).
- `ff81867` to `2592350`: pre-release cleanup, old WebUI module, duplicate `docs/ci.yml` and a stray test outside the app module removed; Gradle root project renamed to `void-apps`.
- `c76a726`: README points to the `.jpg` screenshots; owner uploaded icon, feature graphic, screenshots and banners; `4e00fbe` removed the placeholder from `phoneScreenshots`.
- `7dc6cc8`: PR #1 merged into `main` (CI #189 green).
- Then the final release-status docs (RELEASE, PLAN, HANDOFF), merged with PR #2.
