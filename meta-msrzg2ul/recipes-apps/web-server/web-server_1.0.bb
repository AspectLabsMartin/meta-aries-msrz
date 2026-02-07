SUMMARY = "AE400 Inverter Web Server"
DESCRIPTION = "Single-threaded embedded HTTP server for AE400 inverter system with UI"
LICENSE = "CLOSED"

inherit externalsrc systemd update-rc.d

# Use the external source tree in this repo
EXTERNALSRC = "${TOPDIR}/../applications/web-server/src/server"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

SRC_URI = "file://ae400-webserver.service"

# SysVinit configuration
INITSCRIPT_NAME = "ae400-webserver"
INITSCRIPT_PARAMS = "defaults 90 10"

# Ensure proper build environment
DEPENDS = ""

# Build using the Makefile with proper cross-compilation flags
EXTRA_OEMAKE = "CC='${CC}' CFLAGS='${CFLAGS}' LDFLAGS='${LDFLAGS}'"

# The app builds via a local Makefile using CC/CFLAGS from environment
do_compile() {
    oe_runmake ${EXTRA_OEMAKE}
}

do_install() {
    # Install the server binary
    install -d ${D}${bindir}
    install -m 0755 server ${D}${bindir}/ae400-webserver

    # Install configuration
    install -d ${D}${sysconfdir}/ae400
    if [ -f config/inverter.json ]; then
        install -m 0644 config/inverter.json ${D}${sysconfdir}/ae400/inverter.json
    fi

    # Install web root directory
    install -d ${D}/var/www

    # Install UI files (from web-server/src/ui/_site if they exist)
    if [ -d ${TOPDIR}/../applications/web-server/src/ui/_site ]; then
        cp -r ${TOPDIR}/../applications/web-server/src/ui/_site/* ${D}/var/www/ || true
    fi

    # Install systemd service
    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -d ${D}${systemd_system_unitdir}
        install -m 0644 ${WORKDIR}/ae400-webserver.service ${D}${systemd_system_unitdir}/
    fi

    # Install sysvinit script (for non-systemd systems)
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${EXTERNALSRC}/init.d/server.sh ${D}${sysconfdir}/init.d/ae400-webserver
}

SYSTEMD_SERVICE:${PN} = "ae400-webserver.service"
SYSTEMD_AUTO_ENABLE = "enable"

# Avoid owning the shared directory to prevent RPM conflicts with can-gateway
# List only the concrete files we install under /etc/ae400
FILES:${PN} += "${bindir}/ae400-webserver \
                ${sysconfdir}/ae400/inverter.json \
                ${sysconfdir}/init.d/ae400-webserver \
                /var/www \
                ${systemd_system_unitdir}/ae400-webserver.service"

RDEPENDS:${PN} = ""
