#!/bin/sh
### BEGIN INIT INFO
# Provides:          wifi-apsta
# Required-Start:    $local_fs $syslog
# Required-Stop:     $local_fs $syslog
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: WiFi AP mode for MT7612U
# Description:       Brings up wlan0 as an Access Point.
#                    AP+STA concurrent mode is disabled due to driver bugs
#                    (virtual interface beacon timer broken on kernel 5.10).
### END INIT INFO

# Ensure sbin directories are in PATH — BusyBox default PATH is
# /usr/bin:/bin which misses iw, hostapd, wpa_supplicant, iptables, etc.
export PATH="/usr/sbin:/sbin:/usr/bin:/bin:$PATH"

# -----------------------------------------------------------------------
# AE400 WiFi AP init script  (production)
#
# Hardware : MediaTek MT7612U USB dongle (mt76x2u driver)
# Topology : wlan0 = AP (access point only)
#
# KNOWN DRIVER ISSUES (kernel 5.10 in-tree mt76x2u):
#   1. Virtual AP (ap0) beacon timer broken (~0 TX beacons/5s)
#   2. WPA2 handshake completes but client immediately disassociates
#   3. STA on virtual interface stuck at AUTHENTICATING
#
# WORKAROUND: AP-only on wlan0 (primary), open mode (no WPA).
#   - Client connects, gets DHCP, captive portal shows web UI
#   - Re-enable AP+STA and WPA2 when driver is upgraded
#
# Startup sequence:
#   1. USB mode-switch (if dongle presents as CD-ROM 0e8d:2870)
#   2. Load mt76x2u driver (disable_usb_sg=1 via modprobe.d)
#   3. Wait for wlan0
#   4. Set regulatory domain (AU)
#   5. Start hostapd on wlan0
#   6. Start dnsmasq DHCP/captive-portal server
#
# Config files:
#   /etc/hostapd-ap0.conf           — AP config (runs on wlan0)
#   /etc/dnsmasq-ap0.conf           — DHCP + captive portal for AP clients
#   /etc/modprobe.d/mt76-usb.conf   — disable_usb_sg=1
# -----------------------------------------------------------------------

WLAN_IF="wlan0"
AP_IP="192.168.4.1"
AP_NETMASK="255.255.255.0"

HOSTAPD_CONF="/etc/hostapd-ap0.conf"
DNSMASQ_CONF="/etc/dnsmasq-ap0.conf"
HOSTAPD_PID="/var/run/hostapd_ap0.pid"
DNSMASQ_PID="/var/run/dnsmasq_ap0.pid"

# USB IDs for the MT7612U dongle
USB_CDROM_VID="0e8d"
USB_CDROM_PID="2870"
USB_WIFI_VID="0e8d"
USB_WIFI_PID="7612"

# Timeouts (seconds)
USB_SWITCH_TIMEOUT=10
IFACE_TIMEOUT=15

LOGPFX="wifi-apsta"

log_msg() {
    logger -t "$LOGPFX" "$1"
    echo "$LOGPFX: $1"
}

# -----------------------------------------------------------------------
# USB mode-switch: some MT7612U dongles present as a USB CD-ROM
# (0e8d:2870) on first plug-in.  usb_modeswitch flips them to WiFi mode.
# -----------------------------------------------------------------------
do_usb_modeswitch() {
    # Check if dongle is in CD-ROM mode
    if ! lsusb 2>/dev/null | grep -qi "${USB_CDROM_VID}:${USB_CDROM_PID}"; then
        return 0  # Not in CD-ROM mode, nothing to do
    fi
    log_msg "MT7612U in CD-ROM mode (${USB_CDROM_VID}:${USB_CDROM_PID}), switching..."

    # Try usb_modeswitch if available
    if command -v usb_modeswitch >/dev/null 2>&1; then
        usb_modeswitch -v "$USB_CDROM_VID" -p "$USB_CDROM_PID" \
            -W -R -w 5000 2>/dev/null
    else
        # Fallback: try eject on the device
        for dev in /dev/sr* /dev/cdrom; do
            if [ -b "$dev" ]; then
                eject "$dev" 2>/dev/null
            fi
        done
    fi

    # Wait for WiFi mode to appear
    _tries=0
    while [ "$_tries" -lt "$USB_SWITCH_TIMEOUT" ]; do
        if lsusb 2>/dev/null | grep -qi "${USB_WIFI_VID}:${USB_WIFI_PID}"; then
            log_msg "USB mode-switch successful"
            sleep 1  # Let kernel enumerate the new device
            return 0
        fi
        _tries=$((_tries + 1))
        sleep 1
    done
    log_msg "WARN: USB mode-switch timeout, WiFi device may not appear"
    return 1
}

# -----------------------------------------------------------------------
# Wait for a network interface to appear
# -----------------------------------------------------------------------
wait_for_iface() {
    _iface="$1"
    _timeout="${2:-$IFACE_TIMEOUT}"
    _count=0
    while [ "$_count" -lt "$_timeout" ]; do
        if [ -d "/sys/class/net/$_iface" ]; then
            return 0
        fi
        _count=$((_count + 1))
        sleep 1
    done
    return 1
}

# -----------------------------------------------------------------------
# Discover which phy the MT7612U USB dongle is on
# -----------------------------------------------------------------------
find_mt76_phy() {
    for _phy_path in /sys/class/ieee80211/phy*; do
        [ -d "$_phy_path" ] || continue
        _phy=$(basename "$_phy_path")
        if [ -d "$_phy_path/device" ]; then
            _driver=$(basename "$(readlink -f "$_phy_path/device/driver")" 2>/dev/null)
            if [ "$_driver" = "mt76x2u" ]; then
                echo "$_phy"
                return 0
            fi
        fi
    done
    return 1
}

# -----------------------------------------------------------------------
# Find the interface name on the mt76 phy
# -----------------------------------------------------------------------
find_mt76_iface() {
    _phy="$1"
    for _netdev in /sys/class/ieee80211/"$_phy"/device/net/*; do
        if [ -d "$_netdev" ]; then
            basename "$_netdev"
            return 0
        fi
    done
    return 1
}

# -----------------------------------------------------------------------
# START
# -----------------------------------------------------------------------
do_start() {
    log_msg "Starting WiFi AP mode"

    # --- Step 1: USB mode-switch if needed ---
    do_usb_modeswitch

    # --- Step 2: Load driver module ---
    # The disable_usb_sg=1 parameter is set via /etc/modprobe.d/mt76-usb.conf
    if ! lsmod | grep -q "mt76x2u"; then
        log_msg "Loading mt76x2u driver module"
        modprobe mt76x2u 2>/dev/null
        sleep 2
    fi

    # --- Step 3: Discover hardware ---
    MT76_PHY=$(find_mt76_phy)
    if [ -z "$MT76_PHY" ]; then
        # Module may need more time after USB enumeration
        sleep 3
        MT76_PHY=$(find_mt76_phy)
        if [ -z "$MT76_PHY" ]; then
            log_msg "ERROR: MT7612U not found (no mt76x2u phy)"
            return 1
        fi
    fi
    log_msg "Found MT7612U on $MT76_PHY"

    # Identify the interface (kernel may name it wlx... or wlan0)
    REAL_IF=$(find_mt76_iface "$MT76_PHY")
    if [ -z "$REAL_IF" ]; then
        log_msg "ERROR: No network interface found on $MT76_PHY"
        return 1
    fi

    # If the interface isn't named wlan0, rename it for consistency
    if [ "$REAL_IF" != "$WLAN_IF" ]; then
        ip link set "$REAL_IF" down 2>/dev/null
        ip link set "$REAL_IF" name "$WLAN_IF" 2>/dev/null
        if [ $? -ne 0 ]; then
            log_msg "WARN: Could not rename $REAL_IF to $WLAN_IF, using $REAL_IF"
            WLAN_IF="$REAL_IF"
            # Update hostapd config to use actual interface name
            sed -i "s/^interface=.*/interface=$WLAN_IF/" "$HOSTAPD_CONF"
        fi
    fi
    log_msg "WiFi interface: $WLAN_IF"

    # --- Step 4: Set regulatory domain ---
    # Must be done before any channel operations.  Requires
    # CONFIG_CFG80211_REQUIRE_SIGNED_REGDB=n or a valid regulatory.db.
    ip link set "$WLAN_IF" up
    iw reg set AU 2>/dev/null
    sleep 1  # Let reg domain propagate
    _regdom=$(iw reg get 2>/dev/null | head -n 1)
    log_msg "Regulatory domain: $_regdom"

    # --- Step 5: Assign AP IP and start hostapd ---
    if [ ! -f "$HOSTAPD_CONF" ]; then
        log_msg "ERROR: $HOSTAPD_CONF not found"
        return 1
    fi

    ip addr flush dev "$WLAN_IF" 2>/dev/null
    ip addr add "${AP_IP}/24" dev "$WLAN_IF" 2>/dev/null

    hostapd -B -P "$HOSTAPD_PID" "$HOSTAPD_CONF"
    if [ $? -eq 0 ]; then
        log_msg "hostapd started on $WLAN_IF"
    else
        log_msg "ERROR: hostapd failed to start"
        log_msg "hostapd config: $(grep -v '^#' "$HOSTAPD_CONF" | grep -v '^$' | tr '\n' ' ')"
        return 1
    fi

    # --- Step 6: Start DHCP/captive-portal server for AP clients ---
    if [ -f "$DNSMASQ_CONF" ]; then
        # Stop the stock system dnsmasq — it binds to 0.0.0.0:53 (wildcard)
        # which prevents our AP dnsmasq from binding to 192.168.4.1:53.
        # Use killall because BusyBox lacks start-stop-daemon.
        if killall dnsmasq 2>/dev/null; then
            sleep 1
            log_msg "Stopped stock dnsmasq to free port 53"
        fi

        # Update dnsmasq config interface to match actual interface name
        _runtime_dnsmasq="/var/run/dnsmasq-ap0.runtime.conf"
        sed "s/^interface=.*/interface=$WLAN_IF/" "$DNSMASQ_CONF" > "$_runtime_dnsmasq"
        dnsmasq --conf-file="$_runtime_dnsmasq" --pid-file="$DNSMASQ_PID"
        if [ $? -eq 0 ]; then
            log_msg "dnsmasq DHCP/captive-portal started for $WLAN_IF"
        else
            log_msg "WARN: dnsmasq failed to start"
        fi
    else
        log_msg "WARN: $DNSMASQ_CONF not found, AP clients won't get DHCP"
    fi

    log_msg "WiFi AP mode started successfully"
    return 0
}

# -----------------------------------------------------------------------
# STOP
# -----------------------------------------------------------------------
do_stop() {
    log_msg "Stopping WiFi AP mode"

    # Stop dnsmasq
    if [ -f "$DNSMASQ_PID" ]; then
        kill "$(cat "$DNSMASQ_PID")" 2>/dev/null
        rm -f "$DNSMASQ_PID"
        log_msg "dnsmasq stopped"
    fi

    # Stop hostapd
    if [ -f "$HOSTAPD_PID" ]; then
        kill "$(cat "$HOSTAPD_PID")" 2>/dev/null
        rm -f "$HOSTAPD_PID"
        log_msg "hostapd stopped"
    fi

    # Bring down interface
    ip link set "$WLAN_IF" down 2>/dev/null

    # Clean up runtime configs
    rm -f /var/run/dnsmasq-ap0.runtime.conf

    log_msg "WiFi AP mode stopped"
    return 0
}

# -----------------------------------------------------------------------
# STATUS
# -----------------------------------------------------------------------
do_status() {
    echo "=== WiFi AP Status ==="
    MT76_PHY=$(find_mt76_phy)
    if [ -z "$MT76_PHY" ]; then
        echo "MT7612U: NOT DETECTED"
        return 1
    fi
    echo "MT7612U phy: $MT76_PHY"

    # Check disable_usb_sg parameter
    _sg_status=$(cat /sys/module/mt76_usb/parameters/disable_usb_sg 2>/dev/null)
    echo "USB SG disabled: ${_sg_status:-unknown}"

    # Regulatory domain
    echo "Regulatory: $(iw reg get 2>/dev/null | head -n 1)"

    if [ -d "/sys/class/net/$WLAN_IF" ]; then
        echo "AP ($WLAN_IF): UP"
        echo "AP MAC: $(cat /sys/class/net/$WLAN_IF/address 2>/dev/null)"
        ip addr show "$WLAN_IF" 2>/dev/null | grep "inet "
        if [ -f "$HOSTAPD_PID" ] && kill -0 "$(cat "$HOSTAPD_PID")" 2>/dev/null; then
            echo "hostapd: RUNNING"
            # Show connected stations
            _sta_count=0
            if [ -S /var/run/hostapd/"$WLAN_IF" ]; then
                _sta_count=$(hostapd_cli -i "$WLAN_IF" list_sta 2>/dev/null | grep -c "^[0-9a-f]")
            fi
            echo "Connected stations: $_sta_count"
        else
            echo "hostapd: STOPPED"
        fi
    else
        echo "AP ($WLAN_IF): DOWN"
    fi

    if [ -f "$DNSMASQ_PID" ] && kill -0 "$(cat "$DNSMASQ_PID")" 2>/dev/null; then
        echo "dnsmasq: RUNNING"
    else
        echo "dnsmasq: STOPPED"
    fi
}

case "$1" in
    start)
        do_start
        ;;
    stop)
        do_stop
        ;;
    restart)
        do_stop
        sleep 2
        do_start
        ;;
    status)
        do_status
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status}"
        exit 1
        ;;
esac
