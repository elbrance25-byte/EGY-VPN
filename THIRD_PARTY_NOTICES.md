# Third-party notices (initial audit)

This is an audit starting point, not a declaration that all components are
integrated or that the full dependency tree has been cleared.

| Component | Planned use | Declared project license | Required action before release |
|---|---|---|---|
| sing-box / libbox | VLESS, VMess, Trojan, Shadowsocks, Hysteria2 | GPL-3.0 | Ship GPL-3.0 text, notices and corresponding source/build information; audit bundled dependencies |
| dnstt_xyz_app | DNSTT / Slipstream Android integration reference | CC0 for application repository | Audit each native library and transitive dependency; preserve separate notices |
| MasterDnsVPN | DNS tunnel engine | MIT in Android fork and upstream repository | Include MIT copyright and permission notices; audit bundled dependencies |

OpenVPN is intentionally excluded. No NicVPN proprietary code is included.

Planned source references:
- https://github.com/SagerNet/sing-box
- https://github.com/singbox-android/libbox
- https://github.com/dnstt-xyz/dnstt_xyz_app
- https://github.com/masterking32/MasterDnsVPN
- https://github.com/RevocGG/MasterDnsVPN-AndroidGG

Before public release, replace this preliminary inventory with exact pinned
versions/commit IDs, complete license texts, checksums, local patches and the
corresponding-source delivery method required by each license.
