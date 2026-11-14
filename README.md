# EGY VPN

Android VPN client project. Development is on
[`feature/multi-engine-foundation`](https://github.com/elbrance25-byte/EGY-VPN/tree/feature/multi-engine-foundation).
The `main` branch is not modified by this work.

## Product requirements

- A real Android VPN tunnel carrying device traffic, not a simulated connected state.
- Mainstream VPN-style interface with engine selection and local profile import.
- Free/public servers only; public endpoints must be validated and are not guaranteed to remain online.
- No WireGuard or OpenVPN engine.

## Selected transport research

1. **sing-box / libbox 1.14.4** — VLESS, VMess, Trojan, Shadowsocks and Hysteria2. Android integration requires a `CommandServer`, a complete `PlatformInterface`, and a `VpnService.openTun(TunOptions)` implementation. The upstream Android app is the reference for this lifecycle.
2. **DNSTT / Slipstream** — the upstream DNSTT Client Android implementation establishes a TUN, excludes its own tunnel sockets from VPN routing, forwards traffic through a local SOCKS5 client, and verifies the tunnel before reporting connected. It requires the upstream Go mobile AAR; Slipstream additionally requires its native Android library. The dependency and license notices must be bundled with any port.
3. **MasterDnsVPN** — remains subject to source/API and dependency-license audit before integration.

References:
- https://github.com/SagerNet/sing-box-for-android
- https://github.com/singbox-android/libbox
- https://github.com/dnstt-xyz/dnstt_xyz_app
- https://github.com/masterking32/MasterDnsVPN
- https://github.com/RevocGG/MasterDnsVPN-AndroidGG

## Current implementation status

### Implemented
- Shared engine contract and serialized connection manager.
- libbox 1.14.4 dependency declared.
- Local fail-closed validation for sing-box JSON profiles and supported outbound types.
- Updated EGY VPN Compose home screen with engine selection.
- Local profile text/file import and private on-device profile storage.
- Honest disconnected state; connection control is disabled until a real engine adapter is connected.

### Not yet implemented / not yet verified
- sing-box `CommandServer` + `PlatformInterface` + Android TUN adapter.
- DNSTT/Slipstream native dependency packaging and VPN service adapter.
- MasterDnsVPN adapter and dependency audit.
- VPN permission flow and full foreground-service lifecycle.
- End-to-end traffic verification, IPv4/IPv6 leak tests, DNS leak tests, reconnect and network handover tests.
- Physical-device validation.

**Important:** A successful Gradle build only verifies that the APK compiles. It does not prove that a tunnel works. The app must not report CONNECTED until a supported engine is running, Android TUN is attached, and traffic verification succeeds.

## Profile formats

The sing-box validator accepts a complete sing-box JSON configuration containing an `outbounds` array with at least one supported outbound type:

- `vless`
- `vmess`
- `trojan`
- `shadowsocks`
- `hysteria2`

The UI currently imports and stores profile text locally. Importing or saving a profile does not mean it is connected or validated by a running engine. Server addresses and credentials must come from a user-provided profile or a verified public source; credentials are never fabricated.

## Build

The Android CI workflow builds the debug APK with Java 17, Gradle 8.9 and Android SDK 35.
