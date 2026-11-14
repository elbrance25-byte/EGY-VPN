# EGY VPN

Android VPN client project. Development is on
[`feature/multi-engine-foundation`](https://github.com/elbrance25-byte/EGY-VPN/tree/feature/multi-engine-foundation).
The `main` branch is not modified by this work.

## Product requirements

- Android `VpnService` full-device tunnel, with connection state reported by the selected engine.
- Arabic-first VPN-style interface with engine selection and local profile import.
- No WireGuard or OpenVPN engine.
- No fabricated server addresses or credentials. The app currently requires a valid user-supplied server profile; free public servers are not bundled or guaranteed.

## Integrated engines

### sing-box / libbox 1.14.4
- Supports profiles using VLESS, VMess, Trojan, Shadowsocks and Hysteria2 outbounds.
- Android integration uses `CommandServer`, a `PlatformInterface`, and `VpnService.Builder` TUN creation.
- Outbound sockets are protected from routing back into the VPN.
- A successful service start is not a substitute for device-side traffic and leak testing.

### DNSTT / Slipstream
- DNSTT uses the upstream Go client and the app's Android TUN/SOCKS5 forwarding path.
- Slipstream support uses the upstream Android bridge and its native ARM64 library.
- Slipstream is currently available on ARM64 builds only; DNSTT is built for ARM64 and ARMv7.
- The Go mobile APIs for DNSTT and MasterDnsVPN are compiled into one unified gomobile AAR to avoid conflicting Go JNI runtimes.

### MasterDnsVPN
- Uses the pinned upstream Android/Go implementation and its gVisor-based TUN-to-SOCKS5 bridge.
- Android `VpnService` protects the engine's outbound sockets and attaches the device TUN to the local proxy.
- Requires a server profile with `domains`, `encryption_key`, and `resolvers`; no credentials or public endpoints are invented.

## Build and artifacts

The GitHub Actions workflow builds ARM64 and ARMv7 debug APKs using Java 17, Gradle 8.9, Android SDK 35, and a pinned Go/gomobile toolchain.

The latest successful build is available from the repository's Actions workflow:
https://github.com/elbrance25-byte/EGY-VPN/actions

## Profile formats

- **sing-box:** a complete sing-box JSON configuration containing an `outbounds` array with at least one supported outbound type.
- **DNSTT:** JSON fields `transport` (`dnstt` or `slipstream`), `dns_server`, and `tunnel_domain`; DNSTT also requires `public_key`.
- **MasterDnsVPN:** JSON fields `domains`, `encryption_key`, and `resolvers` (resolver IPs separated by newlines). Optional values include `dns_server`, `listen_port`, `protocol_type`, and encryption options.

Importing or saving a profile only stores it locally. A profile must contain valid server-side settings and the server must be reachable before a connection can work.

## Verification status

- CI build: successful for ARM64 and ARMv7 debug APKs.
- APK archive integrity: verified.
- Android runtime: not yet tested on a physical phone.
- End-to-end internet traffic, DNS/IPv6 leak behaviour, reconnect, network handover, and server compatibility: still require device-side testing.

A green Gradle build proves compilation and packaging only; it does not prove that a tunnel carries working internet traffic.
