# EGY VPN

Android VPN client project. Development is currently on
[`feature/multi-engine-foundation`](https://github.com/elbrance25-byte/EGY-VPN/tree/feature/multi-engine-foundation).

## Selected transport engines

The project is limited to these three engines:

1. **sing-box / libbox** — VLESS, VMess, Trojan, Shadowsocks and Hysteria2.
2. **DNSTT / Slipstream** — DNS tunnel transport.
3. **MasterDnsVPN** — separate DNS tunnel implementation.

Only one engine may own the Android VPN/TUN session at a time. No WireGuard or OpenVPN
integration is part of this project.

## Current implementation status

- [x] Shared engine contract and serialized connection manager.
- [x] Initial dependency wiring for libbox 1.14.4.
- [x] Explicit Android coroutines runtime dependency.
- [x] Local fail-closed validation for sing-box JSON profiles and supported outbound types.
- [ ] Android VpnService and VPN permission/foreground lifecycle.
- [ ] libbox CommandServer + PlatformInterface integration.
- [ ] End-to-end tunnel and traffic-leak tests.
- [ ] DNSTT / Slipstream adapter.
- [ ] MasterDnsVPN adapter.

**Important:** Selecting an engine or passing local JSON validation does not mean a VPN is
connected. The app must not report CONNECTED until the native engine has started, the TUN
interface is attached, and end-to-end traffic checks pass.

## sing-box profile input

The first sing-box integration accepts a complete sing-box JSON configuration. It must
contain an `outbounds` array with at least one supported outbound type:

- `vless`
- `vmess`
- `trojan`
- `shadowsocks`
- `hysteria2`

The local validator is only an early check. libbox performs authoritative schema validation
when the engine adapter is integrated. Server addresses, UUIDs, passwords, TLS settings and
other credentials must come from a user-supplied profile or a verified public source; the
application does not fabricate server credentials.

## Build

The Android CI workflow builds the debug APK with Java 17, Gradle 8.9 and Android SDK 35.
