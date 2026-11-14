# EGY VPN

Android VPN client project. Development is on
[`feature/multi-engine-foundation`](https://github.com/elbrance25-byte/EGY-VPN/tree/feature/multi-engine-foundation).
The `main` branch is not modified by this work.

## Product requirements

- Android `VpnService` full-device tunnel, with connection state reported by the selected engine.
- Arabic-first VPN-style interface with engine selection and local profile import.
- No WireGuard or OpenVPN engine.
- No fabricated server addresses or credentials. The app can fetch public community VLESS, VMess, Trojan, Shadowsocks, and Hysteria2 share links and test TCP endpoint reachability before selecting a candidate. Public nodes are third-party, unstable, and not guaranteed.

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

The GitHub Actions workflow builds an ARM64 debug APK using Java 17, Gradle 8.9, Android SDK 35, and a pinned Go/gomobile toolchain. ARM64 is selected because the pinned Slipstream Android native library is verified for this ABI.

The latest build result and its APK artifact are available from the repository's Actions workflow:
https://github.com/elbrance25-byte/EGY-VPN/actions

## Public free-server discovery

The app fetches public community feeds:
- https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/best.txt
- https://raw.githubusercontent.com/chaishpish/free_server_v2ray/main/@TFMTProto

The current in-app importer converts VLESS, VMess, Trojan, Shadowsocks, and Hysteria2 share links into sing-box TUN profiles and checks whether the endpoint TCP port is reachable. It deliberately skips protocols it cannot safely convert. A reachable TCP port is not proof that authentication, TLS/Reality, the VPN handshake, DNS, or end-to-end traffic works. The sing-box engine must still complete the actual connection. Do not use unknown free nodes for banking, email, or sensitive accounts; node operators may observe traffic metadata and unencrypted traffic.

## Server availability by engine\n\n- sing-box can import the public community profiles listed above.\n- DNSTT requires a tunnel domain delegated to a DNS server plus the server public key; a public recursive DNS resolver alone is not a DNSTT server.\n- MasterDnsVPN requires a server operator to configure the matching tunnel domain and encryption key. The upstream project documents a self-hosted server installation; it does not publish a universal pool of free server credentials.\n- For this reason, the free public server picker currently targets sing-box profiles only. DNSTT and MasterDnsVPN remain selectable engines for compatible profiles supplied by a server operator.\n\n## Profile formats

- **sing-box:** a complete sing-box JSON configuration containing an `outbounds` array with at least one supported outbound type.
- **DNSTT:** JSON fields `transport` (`dnstt` or `slipstream`), `dns_server`, and `tunnel_domain`; DNSTT also requires `public_key`.
- **MasterDnsVPN:** JSON fields `domains`, `encryption_key`, and `resolvers` (resolver IPs separated by newlines). Optional values include `dns_server`, `listen_port`, `protocol_type`, and encryption options.

Importing or saving a profile only stores it locally. A profile must contain valid server-side settings and the server must be reachable before a connection can work.

## Verification status

- CI build: ARM64 debug APK only; the current build workflow has not restored ARMv7.
- APK archive integrity: verified.
- Public feed discovery, share-link conversion, and parallel TCP reachability filtering: implemented; successful VPN handshakes are not yet established by the feed scan.\n- Android runtime: not yet tested on a physical phone.
- End-to-end internet traffic, DNS/IPv6 leak behaviour, reconnect, network handover, and server compatibility: still require device-side testing.

A green Gradle build proves compilation and packaging only; it does not prove that a tunnel carries working internet traffic.
