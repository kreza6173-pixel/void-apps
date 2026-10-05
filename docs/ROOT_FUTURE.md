# VOID // APPS: root features, future only

Updated: 2026-10-04. Status: **not planned for the current track.** The owner asked to record these so they can be added later. Nothing here is implemented, and the app stays Shizuku-only (shell uid 2000) until the owner opens a root track.

The reference phone is now rooted (KernelSU, `su` available). Every phone result in `HANDOFF.md` so far was measured with Shizuku as **uid 2000**. If Shizuku is started through root it runs as **uid 0**, and some results (A5, A6 DND, A7) can differ. Phone reports should always state the Shizuku uid shown on the home screen.

## Rules if a root track is opened

- Root is an opt-in mode, detected with `id -u` = `0` (same gate the owner's modules use). Shell mode stays the default and must keep working on its own.
- Same engineering rules as the rest of the app: quote everything, read every write back, refuse protected packages, preview and snapshot before anything destructive, phone probe before a control is shown.
- No root feature may be required for a shell feature to work.

## Catalog from the owner's repositories

| Feature | Source | Mechanism | Shell fallback today |
|---|---|---|---|
| Per-uid firewall chains (IPv4/IPv6) | `VOID-WALL` `webui/wall.js` `ensureChains()` | own chains `VOIDWALL`, `VOIDWALL_IN`, `VOIDWALL_NAT`, `VOIDWALL6`, `VOIDWALL6_IN` hooked into OUTPUT/INPUT/PREROUTING | partial: A7 Chain 3 block and netpolicy background data |
| Per-app block by uid (Chain 3 alternative) | `VOID-WALL` recipe `block-app-uid` | `iptables -A VOIDWALL -m owner --uid-owner <uid> -j DROP` | A7 Chain 3 (Android 11+) |
| LAN IP block | `VOID-WALL` | `iptables -A VOIDWALL -d <ip> -j DROP; iptables -A VOIDWALL_IN -s <ip> -j DROP` | none |
| Port forward / NAT / DMZ | `VOID-WALL` | `iptables -t nat -A VOIDWALL_NAT ... -j DNAT --to-destination <ip:port>` | none |
| 44 firewall recipes (VPN kill switch, DNS force/leak block, QUIC/DoT block, hardening, transparent proxy, hotspot isolation, IPv6, logging, `tc` throttling) | `VOID-WALL` `RECIPES` | iptables / ip6tables / tc, snapshot to `/data/local/tmp/void-wall-snapshot.rules` before apply | none |
| Raw firewall console with snapshot and undo, typed `RUN VOIDWALL` confirm | `VOID-WALL` | `iptables -S` snapshot, flush and replay on undo | none |
| Wipe all firewall rules | `VOID-WALL` | delete hooks, flush and delete every VOIDWALL chain | none |
| Leftover private app data (corpse finder) | `void-purge` `webui/index.html` | scan `/data/user/0/*/` and `/data/data/*/`, compare with `pm list packages`, `rm -rf` selected | partial: only `/sdcard/Android/data` and `obb` |
| Per-app internal cache clean | `void-purge` | `find /data/user/0/<pkg>/cache -mindepth 1 -exec rm -rf -- {} +` (also `code_cache`) | partial: `pm trim-caches` (global only) |
| ANR / tombstone cleanup | `void-purge` | `find /data/anr /data/tombstones -mindepth 1 -type f -exec rm -f -- {} +` | none |
| App data backup and restore (release-signed apps) | `pulse-battery` `docs/SPEC.md` M5 (paused there) | `tar czf <dest>/data.tar.gz -C /data/data <pkg>`, restore with `tar xzf ... -C /data/data` | debuggable apps only: `run-as <pkg>` |
| Reliable OEM `secure`/`global` settings writes | `void-pulse` `lib.sh`, README | `settings put` as uid 0 where the ROM rejects shell | shell attempt, read back |
| Possible A6 DND access fix | this repo, A6 open item | retry `cmd notification allow_dnd` or the secure setting as uid 0, read back | none (NOT_APPLIED on HyperOS) |

Not root-only, despite the label in their source: `privacy-audit` "Root Tools" freeze (`pm disable-user`) and force-stop already work through shell and are covered by A2. `pulse-install`, `void-autostart` and `android-app-hub` have no root features.

## Suggested order if opened later

1. Root mode detection and a visible mode badge, no behaviour change.
2. App data backup and restore, with snapshot integration (largest user value).
3. Per-app internal cache clean and leftover data finder, preview first.
4. iptables per-uid block as fallback when Chain 3 read-back fails.
5. The VOID-WALL recipe library and console, last, behind the typed confirm.
