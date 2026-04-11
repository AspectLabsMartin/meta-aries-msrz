SUMMARY = "AE400 WiFi AP+STA concurrent mode configuration"
DESCRIPTION = "Configuration files and init script for running simultaneous \
WiFi Access Point and Station (client) on a MediaTek MT7612U USB dongle."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://wifi-apsta.sh \
    file://hostapd-ap0.conf \
    file://wpa_supplicant-wlan0.conf \
    file://dnsmasq-ap0.conf \
"

inherit update-rc.d

# Start after network (85=can-gateway, 90=web-server) but in same runlevel
INITSCRIPT_NAME = "wifi-apsta"
INITSCRIPT_PARAMS = "defaults 92 08"

do_install() {
    # Init script
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/wifi-apsta.sh ${D}${sysconfdir}/init.d/wifi-apsta

    # Configuration files
    install -d ${D}${sysconfdir}
    install -m 0644 ${WORKDIR}/hostapd-ap0.conf ${D}${sysconfdir}/hostapd-ap0.conf
    install -m 0600 ${WORKDIR}/wpa_supplicant-wlan0.conf ${D}${sysconfdir}/wpa_supplicant-wlan0.conf
    install -m 0644 ${WORKDIR}/dnsmasq-ap0.conf ${D}${sysconfdir}/dnsmasq-ap0.conf
}

RDEPENDS_${PN} = " \
    iw \
    wpa-supplicant \
    hostapd \
    dnsmasq \
    iptables \
    linux-firmware-mt76x2 \
    kernel-module-mt76x2u \
"

CONFFILES_${PN} = " \
    ${sysconfdir}/hostapd-ap0.conf \
    ${sysconfdir}/wpa_supplicant-wlan0.conf \
    ${sysconfdir}/dnsmasq-ap0.conf \
"

FILES_${PN} = " \
    ${sysconfdir}/init.d/wifi-apsta \
    ${sysconfdir}/hostapd-ap0.conf \
    ${sysconfdir}/wpa_supplicant-wlan0.conf \
    ${sysconfdir}/dnsmasq-ap0.conf \
"
