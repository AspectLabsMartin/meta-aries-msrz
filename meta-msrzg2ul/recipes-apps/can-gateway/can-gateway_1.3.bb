SUMMARY = "CAN Gateway application (SocketCAN-based)"
PR = "r9"
DESCRIPTION = "Routes and maps CAN messages across two buses with configuration files."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://README.md;beginline=1;endline=20;md5=a03705619c9eb45e35fe0fa33dffb90b"

# Note: do NOT inherit update-rc.d. This service is started exclusively by
# firmware-update-service after the startup-compliance boot gate passes
# (see sc_start_managed_services in applications/firmware-update-service).
# Auto-starting via SysVinit would race the gate and let can-gateway pump
# CAN-A traffic during the DSP self-heal flash session, breaking PREPARE
# response collection. The init.d script is still installed for fwupd to
# invoke via /etc/init.d/ae400 start.
inherit externalsrc systemd

# Use the external source tree in this repo
EXTERNALSRC = "${TOPDIR}/../applications/can-gateway/src"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

SRC_URI = "file://ae400-can-gateway.service"

# Ensure proper build environment
# sqlite3: ae400 owns /var/lib/ae400/store.db (energy counters, history, alarms)
# openssl: libcrypto SHA-256 for the EM-50 meter digest authentication (spec 025)
DEPENDS = "sqlite3 openssl"

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
    # AIT-11: system-fault (trip-active power-on refused) chime (legacy verbose)
    install -m 0644 ${S}/../support-files/SystemFaultCannotPowerOn.wav ${D}${sysconfdir}/ae400/sounds/SystemFaultCannotPowerOn.wav
    # AIT-60: BMS-disconnected warning chime (legacy verbose)
    install -m 0644 ${S}/../support-files/BMSDisconnected.wav ${D}${sysconfdir}/ae400/sounds/BMSDisconnected.wav
    # Status/error cue WAVs unified with web UI assets so the SoT cue name
    # (breaker_open / system_fault / bms_disconnected) plays the same
    # recording on the SOM speaker and the browser.
    install -m 0644 ${S}/../support-files/breaker_open.wav ${D}${sysconfdir}/ae400/sounds/breaker_open.wav
    install -m 0644 ${S}/../support-files/system_fault.wav ${D}${sysconfdir}/ae400/sounds/system_fault.wav
    install -m 0644 ${S}/../support-files/bms_disconnected.wav ${D}${sysconfdir}/ae400/sounds/bms_disconnected.wav
    # Install remaining support-files sounds so baseline rootfs matches the
    # OTA `ae400-support` archive (avoids first-boot remediation drift).
    install -m 0644 ${S}/../support-files/UnsupportedCardDetected.wav ${D}${sysconfdir}/ae400/sounds/UnsupportedCardDetected.wav
    install -m 0644 ${S}/../support-files/UpdateAbortedSystemRunning.wav ${D}${sysconfdir}/ae400/sounds/UpdateAbortedSystemRunning.wav
    # AIT-70 status announcement cues
    install -m 0644 ${S}/../support-files/ae400_status.wav ${D}${sysconfdir}/ae400/sounds/ae400_status.wav
    install -m 0644 ${S}/../support-files/connected.wav ${D}${sysconfdir}/ae400/sounds/connected.wav
    install -m 0644 ${S}/../support-files/island.wav ${D}${sysconfdir}/ae400/sounds/island.wav
    install -m 0644 ${S}/../support-files/passthrough.wav ${D}${sysconfdir}/ae400/sounds/passthrough.wav
    install -m 0644 ${S}/../support-files/off.wav ${D}${sysconfdir}/ae400/sounds/off.wav
    # Short-circuit warning, and the core-button "power state change request"
    # prompt (spec 024 T091): fresh images had them only via the OTA
    # ae400-support archive, which a first boot does not install
    install -m 0644 ${S}/../support-files/short_circuit.wav ${D}${sysconfdir}/ae400/sounds/short_circuit.wav
    install -m 0644 ${S}/../support-files/power_change_request.wav ${D}${sysconfdir}/ae400/sounds/power_change_request.wav

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

RDEPENDS:${PN} = "bash alsa-utils libsqlite3 libcrypto"

# Prevent RPM %dir ownership of shared system directories (/etc/init.d,
# /etc/modprobe.d, etc.).  Without this, cleansstate rebuilds trigger
# "file /etc/init.d conflicts between attempted installs" at do_rootfs.
DIRFILES = "${sysconfdir}/ae400 ${sysconfdir}/ae400/sounds"

FILES:${PN} += "${bindir}/ae400 \
                ${sysconfdir}/ae400 \
                ${sysconfdir}/init.d/ae400 \
                ${systemd_system_unitdir}/ae400-can-gateway.service"
