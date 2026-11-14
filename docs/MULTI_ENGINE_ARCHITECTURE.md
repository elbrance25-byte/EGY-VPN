# EGY VPN multi-engine architecture

## Approved engines

EGY VPN uses exactly three transport engines:

1. **sing-box / libbox** — VLESS, VMess, Trojan, Shadowsocks and Hysteria2.
   The Android libbox distribution declares GPL-3.0. Distribution must meet
   GPL-3.0 obligations, including license notices and corresponding-source terms.
2. **DNSTT / Slipstream** — DNS tunnel transport. The Android client repository
   declares CC0 for its application code; every native library and transitive
   dependency must be audited independently before redistribution.
3. **MasterDnsVPN** — separate DNS tunnel implementation. The Android fork declares
   MIT; retain copyright and permission notices for incorporated portions.
   Its research/experimental status means it is not production-approved until
   interoperability, security and stability tests pass.

OpenVPN is excluded. No proprietary NicVPN code or undocumented FastDNS behavior
is used or represented as implemented.

## Android VPN ownership

Android provides one active VPN slot per user/profile. EGY VPN therefore has one
app-owned VpnService and one TUN lifecycle coordinator. Only one of the three
engines may own the active tunnel at a time.

Each engine adapter must either consume a file descriptor supplied by the shared
VPN/TUN coordinator, or expose a documented local SOCKS/TCP/UDP endpoint that
the coordinator can route TUN traffic into using a reviewed bridge.

Do not let multiple embedded applications independently establish VPN sessions.
Do not report CONNECTED until the tunnel is attached and end-to-end traffic checks
succeed.

## Configuration and secrets

- Store profiles in app-private storage.
- Keep passwords, UUIDs, private keys and tokens out of logs and analytics.
- Validate imported links/configurations before saving.
- Never invent public servers or credentials; server availability is separate.
- Keep engine-specific parsing separate from UI state.

## Required verification

For every engine and supported ABI:
- Build and load native artifacts on supported Android API levels.
- Confirm IPv4 and IPv6 routing behavior.
- Confirm DNS requests use the configured tunnel resolver and detect leakage.
- Verify external IP and actual upload/download through the tunnel.
- Test stop, crash, network handover, reconnect and process recreation.
- Confirm no traffic escapes after unexpected tunnel failure where required.

## Licensing

Maintain THIRD_PARTY_NOTICES with exact versions, source URLs, license texts and
local modifications for every shipped component. GPL-3.0 distribution
requirements for libbox must be met before publishing APKs. CC0/MIT labels on a
top-level repository do not automatically cover bundled dependencies.
