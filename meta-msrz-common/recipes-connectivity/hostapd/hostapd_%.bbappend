# Disable the stock hostapd init script auto-start.
# The wifi-apsta recipe manages hostapd directly using our custom
# /etc/hostapd-ap0.conf config and startup sequence (S92).
# The stock init script (S20) would grab wlan0 first with the
# default /etc/hostapd.conf and block our wifi-apsta script.
INITSCRIPT_PARAMS = "disable"
