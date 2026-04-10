SUMMARY = "CAN Gateway application (SocketCAN-based)"
DESCRIPTION = "Routes and maps CAN messages across two buses with configuration files."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://README.md;beginline=1;endline=20;md5=71b74b9ff2ff69bff6e64bfd04d99717"

inherit externalsrc systemd update-rc.d

# Use the external source tree in this repo
EXTERNALSRC = "${TOPDIR}/../applications/can-gateway/src"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

SRC_URI = "file://ae400-can-gateway.service"

# SysVinit configuration — start early (before web server at 90)
INITSCRIPT_NAME = "ae400"
INITSCRIPT_PARAMS = "defaults 85 15"

# Ensure proper build environment
DEPENDS = ""

# The app builds via a local Makefile using CC/CFLAGS from environment
do_compile() {
    cd ${S}
    oe_runmake clean
    oe_runmake
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/ae400 ${D}${bindir}/ae400
    # Optional: Install default configs to /etc/ae400/
    # 0775 + group ae400web so the web server can create temp files for atomic writes
    install -d -m 0775 ${D}${sysconfdir}/ae400
    if [ -f ${S}/config_user.json ]; then
        install -m 0664 ${S}/config_user.json ${D}${sysconfdir}/ae400/config_user.json
    fi
    if [ -f ${S}/config_factory.json ]; then
        install -m 0644 ${S}/config_factory.json ${D}${sysconfdir}/ae400/config_factory.json
    fi
    # Install audio files
    install -d ${D}${sysconfdir}/ae400/sounds
    install -m 0644 ${S}/../support-files/startup.wav ${D}${sysconfdir}/ae400/sounds/startup.wav
    install -m 0644 ${S}/../support-files/poweron.wav ${D}${sysconfdir}/ae400/sounds/poweron.wav
    install -m 0644 ${S}/../support-files/poweroff.wav ${D}${sysconfdir}/ae400/sounds/poweroff.wav
    install -m 0644 ${S}/../support-files/Cannotpoweroncheckbreaker.wav ${D}${sysconfdir}/ae400/sounds/Cannotpoweroncheckbreaker.wav

    # Install SysVinit script for auto-start at boot
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${S}/init.d/ae400.sh ${D}${sysconfdir}/init.d/ae400

    # Install systemd service (if systemd is enabled)
    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -d ${D}${systemd_system_unitdir}
        install -m 0644 ${WORKDIR}/ae400-can-gateway.service ${D}${systemd_system_unitdir}/
    fi
}

SYSTEMD_SERVICE:${PN} = "ae400-can-gateway.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "bash alsa-utils"

FILES:${PN} += "${bindir}/ae400 \
                ${sysconfdir}/ae400 \
                ${sysconfdir}/init.d/ae400 \
                ${systemd_system_unitdir}/ae400-can-gateway.service"
