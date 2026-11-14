package mobile

import (
    dnstt "www.bamsoftware.com/git/dnstt.git/mobile"
    master "masterdnsvpn-go/cmd/android"
)

// DnsttClient wraps the upstream DNSTT client for gomobile.
type DnsttClient struct {
    client *dnstt.DnsttClient
}

func NewClient(dnsServer, tunnelDomain, publicKey, listenAddr string) (*DnsttClient, error) {
    client, err := dnstt.NewClient(dnsServer, tunnelDomain, publicKey, listenAddr)
    if err != nil {
        return nil, err
    }
    return &DnsttClient{client: client}, nil
}

func (c *DnsttClient) Start() error {
    if c == nil || c.client == nil {
        return nil
    }
    return c.client.Start()
}

func (c *DnsttClient) Stop() {
    if c != nil && c.client != nil {
        c.client.Stop()
    }
}

func (c *DnsttClient) IsRunning() bool {
    return c != nil && c.client != nil && c.client.IsRunning()
}

// MobileProtectCallback is called for every outbound MasterDnsVPN socket.
type MobileProtectCallback interface {
    Protect(fd int32) bool
}

// MobileConfig contains the Android-facing MasterDnsVPN settings used by EGY VPN.
type MobileConfig struct {
    ProfileDir string
    ResolversInline string
    Domains string
    DataEncryptionMethod int
    EncryptionKey string
    ProtocolType string
    ListenIP string
    ListenPort int
    SOCKS5Auth bool
    SOCKS5User string
    SOCKS5Pass string
}

func NewDefaultConfig() *MobileConfig {
    c := master.NewDefaultConfig()
    return &MobileConfig{
        ProfileDir: c.ProfileDir,
        ResolversInline: c.ResolversInline,
        Domains: c.Domains,
        DataEncryptionMethod: c.DataEncryptionMethod,
        EncryptionKey: c.EncryptionKey,
        ProtocolType: c.ProtocolType,
        ListenIP: c.ListenIP,
        ListenPort: c.ListenPort,
        SOCKS5Auth: c.SOCKS5Auth,
        SOCKS5User: c.SOCKS5User,
        SOCKS5Pass: c.SOCKS5Pass,
    }
}

func SetProtectCallback(callback MobileProtectCallback) {
    master.SetProtectCallback(callback)
}

func StartInstance(key string, config *MobileConfig) error {
    if config == nil {
        return master.StartInstance(key, master.NewDefaultConfig())
    }
    c := master.NewDefaultConfig()
    c.ProfileDir = config.ProfileDir
    c.ResolversInline = config.ResolversInline
    c.Domains = config.Domains
    c.DataEncryptionMethod = config.DataEncryptionMethod
    c.EncryptionKey = config.EncryptionKey
    c.ProtocolType = config.ProtocolType
    c.ListenIP = config.ListenIP
    c.ListenPort = config.ListenPort
    c.SOCKS5Auth = config.SOCKS5Auth
    c.SOCKS5User = config.SOCKS5User
    c.SOCKS5Pass = config.SOCKS5Pass
    return master.StartInstance(key, c)
}

func StopInstance(key string) {
    master.StopInstance(key)
}

func GetLastError(key string) string {
    return master.GetLastError(key)
}

func StartTunBridge(instanceID string, tunFD int32, mtu int32, listenAddr string) error {
    return master.StartTunBridge(instanceID, tunFD, mtu, listenAddr)
}

func StopTunBridge() {
    master.StopTunBridge()
}

func IsTunBridgeRunning() bool {
    return master.IsTunBridgeRunning()
}

func SetTunDisableIPv6(disabled bool) {
    master.SetTunDisableIPv6(disabled)
}
