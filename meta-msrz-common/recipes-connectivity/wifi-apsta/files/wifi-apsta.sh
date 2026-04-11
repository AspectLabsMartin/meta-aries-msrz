#!/bin/sh
### BEGIN INIT INFO
# Provides:          wifi-apsta
# Required-Start:    $local_fs $syslog
# Required-Stop:     $local_fs $syslog
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: WiFi AP+STA concurrent mode for MT7612U
# Description:       Brings up wlan0 as STA client, creates ap0 virtual
#                    interface as Access Point. Both share the same channel.
### END INIT INFO

# -----------------------------------------------------------------------
# AE400 WiFi AP+STA init script
#
# Hardware : MediaTek MT7612U USB dongle (mt76x2u driver)
# Topology : wlan0 = STA (client), ap0 = AP (access point)
# Constraint: Both interfaces MUST operate on the same channel
#
# Config files:
#   /etc/wpa_supplicant-wlan0.conf  — STA network credentials
#   /etc/hostapd-ap0.conf           — AP settings (SSID, passphrase)
#   /etc/dnsmasq-ap0.conf           — DHCP server for AP clients
# -----------------------------------------------------------------------

WLAN_IF="wlan0"
AP_IF="ap0"
AP_IP="192.168.4.1"
AP_NETMASK="255.255.255.0"
AP_SUBNET="192.168.4"

WPA_CONF="/etc/wpa_supplicant-wlan0.conf"
HOSTAPD_CONF="/etc/hostapd-ap0.conf"
DNSMASQ_CONF="/etc/dnsmasq-ap0.conf"
WPA_PID="/var/run/wpa_supplicant_wlan0.pid"
HOSTAPD_PID="/var/run/hostapd_ap0.pid"
DNSMASQ_PID="/var/run/dnsmasq_ap0.pid"

LOGPFX="wifi-apsta"

log_msg() {
    logger -t "$LOGPFX" "$1"
    echo "$LOGPFX: $1"
}

# Wait for a network interface to appear (up to timeout seconds)
wait_for_iface() {
    _iface="$1"
    _timeout="${2:-10}"
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

# Discover which phy the MT7612U USB dongle is on
find_mt76_phy() {
    for _phy_path in /sys/class/ieee80211/phy*; do
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

# Find the managed (STA) interface name on the mt76 phy
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

do_start() {
    log_msg "Starting AP+STA mode"

    # --- Discover hardware ---
    MT76_PHY=$(find_mt76_phy)
    if [ -z "$MT76_PHY" ]; then
        # Try loading the module
        modprobe mt76x2u 2>/dev/null
        sleep 2
        MT76_PHY=$(find_mt76_phy)
        if [ -z "$MT76_PHY" ]; then
            log_msg "ERROR: MT7612U not found (no mt76x2u phy)"
            return 1
        fi
    fi
    log_msg "Found MT7612U on $MT76_PHY"

    # Identify the STA interface (kernel may name it wlx... or wlan0)
    REAL_STA_IF=$(find_mt76_iface "$MT76_PHY")
    if [ -z "$REAL_STA_IF" ]; then
        log_msg "ERROR: No network interface found on $MT76_PHY"
        return 1
    fi

    # If the interface isn't named wlan0, rename it for consistency
    if [ "$REAL_STA_IF" != "$WLAN_IF" ]; then
        ip link set "$REAL_STA_IF" down 2>/dev/null
        ip link set "$REAL_STA_IF" name "$WLAN_IF" 2>/dev/null
        if [ $? -ne 0 ]; then
            log_msg "WARN: Could not rename $REAL_STA_IF to $WLAN_IF, using $REAL_STA_IF"
            WLAN_IF="$REAL_STA_IF"
        fi
    fi
    log_msg "STA interface: $WLAN_IF"

    # --- Create virtual AP interface ---
    if [ -d "/sys/class/net/$AP_IF" ]; then
        log_msg "AP interface $AP_IF already exists, removing"
        iw dev "$AP_IF" del 2>/dev/null
    fi
    iw dev "$WLAN_IF" interface add "$AP_IF" type __ap
    if [ $? -ne 0 ]; then
        log_msg "ERROR: Failed to create AP interface $AP_IF"
        return 1
    fi
    log_msg "Created AP interface: $AP_IF"

    # --- Bring up STA ---
    ip link set "$WLAN_IF" up
    if [ -f "$WPA_CONF" ]; then
        # Check if there are configured networks (not just comments)
        if grep -q "^[[:space:]]*ssid=" "$WPA_CONF" 2>/dev/null; then
            wpa_supplicant -B -i "$WLAN_IF" -c "$WPA_CONF" -P "$WPA_PID"
            log_msg "wpa_supplicant started on $WLAN_IF"

            # Request DHCP on STA interface
            udhcpc -i "$WLAN_IF" -p /var/run/udhcpc_wlan0.pid -b -q 2>/dev/null &
            log_msg "DHCP client requested on $WLAN_IF"
        else
            log_msg "WARN: No networks configured in $WPA_CONF, STA not started"
        fi
    else
        log_msg "WARN: $WPA_CONF not found, STA not started"
    fi

    # --- Bring up AP ---
    ip link set "$AP_IF" up
    ip addr add "${AP_IP}/24" dev "$AP_IF" 2>/dev/null

    if [ -f "$HOSTAPD_CONF" ]; then
        hostapd -B -P "$HOSTAPD_PID" "$HOSTAPD_CONF"
        if [ $? -eq 0 ]; then
            log_msg "hostapd started on $AP_IF"
        else
            log_msg "ERROR: hostapd failed to start"
            return 1
        fi
    else
        log_msg "ERROR: $HOSTAPD_CONF not found"
        return 1
    fi

    # --- Start DHCP server for AP clients ---
    if [ -f "$DNSMASQ_CONF" ]; then
        dnsmasq --conf-file="$DNSMASQ_CONF" --pid-file="$DNSMASQ_PID"
        log_msg "dnsmasq DHCP server started for $AP_IF"
    else
        log_msg "WARN: $DNSMASQ_CONF not found, AP clients won't get DHCP"
    fi

    # --- Enable IP forwarding and NAT (STA → AP routing) ---
    echo 1 > /proc/sys/net/ipv4/ip_forward
    iptables -t nat -C POSTROUTING -o "$WLAN_IF" -j MASQUERADE 2>/dev/null || \
        iptables -t nat -A POSTROUTING -o "$WLAN_IF" -j MASQUERADE
    iptables -C FORWARD -i "$AP_IF" -o "$WLAN_IF" -j ACCEPT 2>/dev/null || \
        iptables -A FORWARD -i "$AP_IF" -o "$WLAN_IF" -j ACCEPT
    iptables -C FORWARD -i "$WLAN_IF" -o "$AP_IF" -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || \
        iptables -A FORWARD -i "$WLAN_IF" -o "$AP_IF" -m state --state RELATED,ESTABLISHED -j ACCEPT
    log_msg "NAT/forwarding enabled between $AP_IF and $WLAN_IF"

    log_msg "AP+STA mode started successfully"
    return 0
}

do_stop() {
    log_msg "Stopping AP+STA mode"

    # Remove NAT rules
    iptables -t nat -D POSTROUTING -o "$WLAN_IF" -j MASQUERADE 2>/dev/null
    iptables -D FORWARD -i "$AP_IF" -o "$WLAN_IF" -j ACCEPT 2>/dev/null
    iptables -D FORWARD -i "$WLAN_IF" -o "$AP_IF" -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null

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

    # Stop wpa_supplicant
    if [ -f "$WPA_PID" ]; then
        kill "$(cat "$WPA_PID")" 2>/dev/null
        rm -f "$WPA_PID"
        log_msg "wpa_supplicant stopped"
    fi

    # Kill DHCP client
    if [ -f /var/run/udhcpc_wlan0.pid ]; then
        kill "$(cat /var/run/udhcpc_wlan0.pid)" 2>/dev/null
        rm -f /var/run/udhcpc_wlan0.pid
    fi

    # Remove AP interface
    if [ -d "/sys/class/net/$AP_IF" ]; then
        ip link set "$AP_IF" down 2>/dev/null
        iw dev "$AP_IF" del 2>/dev/null
        log_msg "Removed $AP_IF"
    fi

    # Bring down STA
    ip link set "$WLAN_IF" down 2>/dev/null

    log_msg "AP+STA mode stopped"
    return 0
}

do_status() {
    echo "=== WiFi AP+STA Status ==="
    MT76_PHY=$(find_mt76_phy)
    if [ -z "$MT76_PHY" ]; then
        echo "MT7612U: NOT DETECTED"
        return 1
    fi
    echo "MT7612U phy: $MT76_PHY"

    if [ -d "/sys/class/net/$WLAN_IF" ]; then
        echo "STA ($WLAN_IF): UP"
        ip addr show "$WLAN_IF" 2>/dev/null | grep "inet "
        if [ -f "$WPA_PID" ] && kill -0 "$(cat "$WPA_PID")" 2>/dev/null; then
            echo "wpa_supplicant: RUNNING"
        else
            echo "wpa_supplicant: STOPPED"
        fi
    else
        echo "STA ($WLAN_IF): DOWN"
    fi

    if [ -d "/sys/class/net/$AP_IF" ]; then
        echo "AP ($AP_IF): UP"
        ip addr show "$AP_IF" 2>/dev/null | grep "inet "
        if [ -f "$HOSTAPD_PID" ] && kill -0 "$(cat "$HOSTAPD_PID")" 2>/dev/null; then
            echo "hostapd: RUNNING"
        else
            echo "hostapd: STOPPED"
        fi
    else
        echo "AP ($AP_IF): DOWN"
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
        sleep 1
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
