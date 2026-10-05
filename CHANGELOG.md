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
