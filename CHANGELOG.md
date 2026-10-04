# Changelog

## 1.0.0 (native app, versionCode 100)

First release of the native Kotlin + Compose app (branch `native-app-v0`, merged into `main`). No root, no INTERNET permission, every change read back from Android.

- Inventory: package list with filters, app details, protected-package guard (static core list plus live launcher, keyboard, dialer, SMS app, WebView, Shizuku and this app).
- Operations: suspend / unsuspend, disable / enable, force stop, remove for user 0, restore with `pm install-existing`, clear data.
- Snapshots, undo with per-package read-back, JSON import / export, pins, batch actions with one snapshot per batch.
- Debloat presets from a knowledge base with risk labels and review.
- Runtime permissions and AppOps (uid and package scope), read-only Self-check, ROM report template.
- Autostart: boot receivers, background execution ops, component disable / enable.
- Notifications: mute, listener access, Do Not Disturb access.
- Network: full block via Android 11+ Chain 3, background data via netpolicy.
- Install & clean: APK / APKS / XAPK installer with OBB and Inspect, APK extract, cache trim, empty folders, running apps.

Known open items: shorthand boot receiver button state, Do Not Disturb access on HyperOS (see `docs/PLAN.md`).

## 1.2.0 (Shevery WebUI module, Cyber App Manager)

The earlier WebUI module that this repository started as. Its files remain at the repository root (`webui/`, `*.sh`, `module.prop`).

- Package registry: search, filter (all/user/system/frozen/removed/running/selected), sort (name/risk/frozen-first/data use), batch freeze/unfreeze/force-stop/remove/restore/pin
- Knowledge base of common Android/Google/Xiaomi/Samsung/OEM packages with friendly names, categories and risk tags, plus heuristics (overlay/vendor/AOSP detection) for anything unlisted
- Debloat presets built from the knowledge base, reviewable before freezing
- Snapshots with diff/restore/export/import, auto-snapshot before destructive batches, and undo for the last 10 batches
- Pinned-frozen list re-applied automatically by `service.sh` every Shizuku session, and from the WebUI or `snapshot.sh`
- Per-app detail sheet: version, installer, dates, runtime permissions, background-activity restriction, standby bucket
- On-device data-usage aggregation (since boot / 24 h / all history / since last charge), split into mobile, Wi-Fi and VPN-tunnel traffic
- Optional bring-your-own-key AI advisor (OpenAI, Anthropic Claude, Google Gemini, DeepSeek, Mistral, xAI, or any OpenAI-compatible/local endpoint), with local validation, retry on temporary provider overload, and friendly error messages
- Dynamic protection of the current launcher, keyboard, default dialer and default SMS app, on top of a static core/overlay list
- Themed pickers throughout (no native `<select>`/`<datalist>` popups), fixed-pixel menu heights, a build-consistency check and an on-screen script-error bar
