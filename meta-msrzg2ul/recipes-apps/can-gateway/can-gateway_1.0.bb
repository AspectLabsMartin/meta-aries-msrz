SUMMARY = "CAN Gateway application (SocketCAN-based)"
DESCRIPTION = "Routes and maps CAN messages across two buses with configuration files."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://README.md;beginline=1;endline=20;md5=71b74b9ff2ff69bff6e64bfd04d99717"

inherit externalsrc

# Use the external source tree in this repo
EXTERNALSRC = "${TOPDIR}/../applications/can-gateway/src"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

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
    # Optional: Install default configs
    install -d ${D}${sysconfdir}/can-gateway
    if [ -f ${S}/config_user.json ]; then
        install -m 0644 ${S}/config_user.json ${D}${sysconfdir}/can-gateway/config_user.json
    fi
    if [ -f ${S}/config_factory.json ]; then
        install -m 0644 ${S}/config_factory.json ${D}${sysconfdir}/can-gateway/config_factory.json
    fi
    # Install audio files
    install -d ${D}${sysconfdir}/ae400/sounds
    install -m 0644 ${S}/../support-files/startup.wav ${D}${sysconfdir}/ae400/sounds/startup.wav
    install -m 0644 ${S}/../support-files/poweron.wav ${D}${sysconfdir}/ae400/sounds/poweron.wav
    install -m 0644 ${S}/../support-files/poweroff.wav ${D}${sysconfdir}/ae400/sounds/poweroff.wav
}

RDEPENDS_${PN} = "bash alsa-utils"

FILES_${PN} += "${bindir}/ae400 ${sysconfdir}/can-gateway ${sysconfdir}/ae400"
