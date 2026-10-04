# VOID // APPS: complete project handoff

Updated: 2026-10-04
Branch: `native-app-v0`
Application ID: `io.github.kreza6173pixel.voidapps`
Reference device: Xiaomi Redmi Note 14, HyperOS Global ROM, Android 16, SDK 36, Shizuku shell uid 2000.

This is the durable record of product decisions, engineering rules, phone evidence and remaining work.

## Product idea

VOID // APPS is a local Kotlin + Jetpack Compose Android package manager using Shizuku UserService/AIDL. It has no INTERNET permission, refuses protected packages, performs reversible operations and reads state back before reporting success.

## Engineering rules

No INTERNET permission. Commands only through Shizuku and ExecBridge. Validate and quote package and permission names. Every write is read back; APPLIED means the new state matches. Protected packages are refused in the repository. Code and resources land together. Diagnose CI failures from logs. ExecBridge output is capped at 64 KiB, so shell filters are measured on the phone before being relied on and extra reads are non-fatal. A write control is shown only after a phone probe proves it works.

## Working agreement with the owner

Implement a whole section (code, tests, wiring, strings) before asking for a phone test. The owner tests the section as one unit, then the next section starts. No test requests after single-line changes.

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
| A7 to A8 | open | |
| 1.0 release | open | |

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

## A5 open item (left for later by the owner)

Component control for receivers listed in shorthand form (`pkg/.Cls`) is not reliable in the UI.

- Seen on `com.thirtytwo.steps/.BootReceiver`, `ch.abertschi.adfree/.StartOnBootListener` and `com.eyalm.adns/.services.BootReceiver`.
- The repository and device agree those components are disabled, but the card still shows Disable. Probable cause: shorthand versus fully qualified comparison.
- Both components above are still disabled on the reference phone. Restore with fully qualified `pm enable` commands from the console if needed.

## A6 open item (left for later by the owner)

DND access control on HyperOS is not reliable and is intentionally left open.

- On `com.thirtytwo.steps`, `cmd notification allow_dnd 'com.thirtytwo.steps'` returned exit 0 with no output.
- Read-back from `enabled_notification_policy_access_packages` stayed unchanged: before not granted, after not granted.
- This is treated as **NOT_APPLIED**, not success. Possible future path: inspect `dumpsys notification` and test the ROM-specific AppOps/policy mechanism.
- Per-app mute works and was phone-verified. The tested app had no notification listener service, so there was no listener toggle to test there.

## Remaining work

1. A5 shorthand receiver UI state (optional, owner decides when).
2. A6 DND access on HyperOS (optional, owner decides when).
3. A7 Chain3/netpolicy based on `VOID-WALL`.
4. A8 streamed APK/APKS/XAPK installer based on `pulse-install`; APKM stays out of scope.
5. 1.0 README, About, icon, fastlane, signed release, smoke test and merge to `main`.

## Safety decisions

AppOps is separate from runtime permissions. OEM operations are shown but never changed. Self-check never writes. ROM-specific differences are expected and supported through issue reports. `com.miui.securitycenter` stays unprotected, but system-uid writes are refused. Root, Dhizuku and APKM are out of scope.

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
- `b0350d5`: previous docs update.
