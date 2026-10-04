# VOID // APPS: roadmap and remaining work

Updated: 2026-10-04. See `docs/HANDOFF.md` for the narrative and evidence.

## Product goal

A safe, reversible, local Android package manager built with Kotlin, Compose, and Shizuku UserService. It never pretends a shell command succeeded when the ROM rejected it. Shizuku is the only execution path; Root and Dhizuku are out of scope.

## Complete

- M0 core, Shizuku connection, UserService/AIDL, ExecBridge, console, CI.
- A1 inventory, protected guard, parsers, details, real-device counts.
- A2 single-package Suspend/Unsuspend, Disable/Enable, Force stop, Remove/Restore, Clear data, read-back.
- A3 snapshots, Restore/Undo, JSON Import/Export, Pins, batch operations. Acceptance test passed on the phone.
- Debloat track: knowledge base, SAFE-only presets, review screen, compact disclaimer, search in preset list and package list, cross-manager restore via `pm install-existing`, one snapshot per batch, sequential read-back. CI and phone acceptance passed.
- A4 permissions and AppOps. Phone-verified on the reference phone (see HANDOFF).

## A5 autostart: partly done, one open item

Done and phone-verified:

- Read-only boot receiver audit from `dumpsys package` (BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, QUICKBOOT_POWERON, MY_PACKAGE_REPLACED), Receiver Resolver Table only.
- Background execution state (RUN_IN_BACKGROUND, RUN_ANY_IN_BACKGROUND) shown in the card; changes go through the A4 AppOps card.
- Component Disable/Enable round trip for a receiver whose class is outside the app package (`com.thirtytwo.steps/androidx.profileinstaller.ProfileInstallReceiver`): APPLIED both ways.

### OPEN: component control for shorthand receivers (`pkg/.Cls`)

_Left open on purpose by the owner. Pick this up later._

- Symptom: `.BootReceiver` / `.StartOnBootListener` rows keep showing **Disable** while the repository reports "component is already disabled". The card and the repository disagree.
- Seen on three apps. On `com.eyalm.adns/.services.BootReceiver` the repository returned APPLIED for Disable, then "already disabled", while the row stayed on Disable: the device state is right, the card check is wrong.
- Most likely cause: the card checks state with `isComponentDisabled(receiver.component, set)` using the shorthand name, while the set built by `queryDisabledComponents` holds fully qualified `pkg/pkg.Cls` names. Normalising both sides with `expandComponentName()` should make them agree.
- Device state to remember: `com.thirtytwo.steps/com.thirtytwo.steps.BootReceiver`, `ch.abertschi.adfree/ch.abertschi.adfree.StartOnBootListener`, and `com.eyalm.adns/com.eyalm.adns.services.BootReceiver` were really disabled during testing. Re-enable from the console with `pm enable <pkg>/<full.Cls>` if needed.
- Acceptance when fixed: Disable then Enable on `.BootReceiver` both APPLIED, and the row button flips each time.

## A6 notifications: partly done, one open item

Implemented from `kreza6173-pixel/void-pulse`, per-app only:

- Posting notifications (mute): POST_NOTIFICATIONS through the A4 permission path; phone-tested and working. Allow/Revoke read-back matches Android.
- Listener section: apps with no listener service correctly show the empty state. Listener allow/revoke remains available for apps that declare one.
- DND access: `cmd notification allow_dnd/disallow_dnd` returns exit 0 on HyperOS but the secure setting does not change. The UI correctly reports **NOT_APPLIED** and warns that Android silently ignored it.

### OPEN: DND access control on HyperOS

_Left open on purpose by the owner. Pick this up later._

- Evidence: `cmd notification allow_dnd 'com.thirtytwo.steps'` returned exit 0, but `enabled_notification_policy_access_packages` remained unchanged (`before: not granted`, `after: not granted`).
- Do not claim DND access was granted when read-back disagrees.
- Possible future investigation: read `dumpsys notification` and test the HyperOS-specific AppOps/policy path. No automatic fallback is trusted yet.

## Then

| Step | Source module | Commands to port |
|---|---|---|
| A7 per-app network block, background data | `kreza6173-pixel/VOID-WALL` (`webui/wall.js`) | Chain 3 via `cmd connectivity` (Android 11+), background data via `netpolicy` |
| A8 installer for APK, APKS, XAPK, OBB, extract | `kreza6173-pixel/pulse-install` (`webui/script.js`, `service.sh`) | streamed `pm install-create` / `install-write -S <size> -` / `install-commit`, `unzip`, XAPK `manifest.json`, OBB copy, `pm path` extract |
| 1.0 release | this repository | README, About, icon, fastlane, signed release, final smoke test, merge to `main` |

## Gates

No INTERNET permission. No protected bypass. No destructive batch without preview and snapshot. No support claim without a device probe. No release claim while CI or device acceptance is red. Code and its strings/resources always land in one commit. Shell filters are measured on the phone before they are relied on. A write control is shown only where a phone test showed Android accepts it. ROM-specific support comes from issue reports with phone evidence. APKM and root-only firewall features remain out of scope.
