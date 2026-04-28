SUMMARY = "AE400 Hostname Configuration Service"
PR = "r3"
DESCRIPTION = "Sets the system hostname to AE400-{MAC-suffix} at boot so the \
unit is uniquely identifiable in DHCP client lists (AIT-15)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit externalsrc update-rc.d

# Point at the external source tree in the repo
EXTERNALSRC = "${TOPDIR}/../applications/ae400-hostname"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

SRC_URI = "file://ae400-hostname.service"

# SysVinit — run very early, before can-gateway (85) and web-server (90)
INITSCRIPT_NAME = "ae400-hostname"
INITSCRIPT_PARAMS = "defaults 10 90"

do_compile() {
    # Nothing to compile — shell script only
    :
}

do_install() {
    # Install the hostname script
    install -d ${D}${bindir}
    install -m 0755 ${S}/ae400-set-hostname ${D}${bindir}/ae400-set-hostname

    # Install SysVinit script
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${S}/init.d/ae400-hostname.sh ${D}${sysconfdir}/init.d/ae400-hostname

    # Install systemd service (if systemd is enabled)
    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -d ${D}${systemd_system_unitdir}
        install -m 0644 ${WORKDIR}/ae400-hostname.service ${D}${systemd_system_unitdir}/
    fi
}

# Conditionally inherit systemd class
SYSTEMD_SERVICE:${PN} = "ae400-hostname.service"
SYSTEMD_AUTO_ENABLE = "enable"

# Prevent %dir ownership of shared system dirs (/etc/init.d).
DIRFILES = ""

FILES:${PN} += " \
    ${bindir}/ae400-set-hostname \
    ${sysconfdir}/init.d/ae400-hostname \
    ${systemd_system_unitdir}/ae400-hostname.service \
"
