SUMMARY = "AE400 Inverter Web Server"
PR = "r15"
DESCRIPTION = "Single-threaded embedded HTTP server for AE400 inverter system with UI"
LICENSE = "CLOSED"

# Note: do NOT inherit update-rc.d. This service is started exclusively by
# firmware-update-service after the startup-compliance boot gate passes
# (sc_start_managed_services), or in diagnostic-only mode via
# sc_start_diagnostic_ui when the gate is BLOCK_DIAGNOSTIC.
inherit externalsrc systemd useradd

# Use the external source tree in this repo
EXTERNALSRC = "${TOPDIR}/../applications/web-server/src/server"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

SRC_URI = "file://ae400-webserver.service"

# Ensure proper build environment
DEPENDS = "sqlite3 openssl"

# Service account: ae400web (FR-040 least-privilege)
USERADD_PACKAGES = "${PN}"
GROUPADD_PARAM:${PN} = "--system ae400web"
USERADD_PARAM:${PN} = "--system --no-create-home --home-dir /var/lib/ae400-webserver \
                        --shell /bin/false --gid ae400web ae400web"

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

    # /etc/ae400 directory is owned by can-gateway package.
    # Permissions are set in pkg_postinst below.

    # Create config archive spool directory (FR-010)
    install -d ${D}/var/lib/database-logger/spool/config-archive
    chown ae400web:ae400web ${D}/var/lib/database-logger/spool/config-archive
    chmod 0755 ${D}/var/lib/database-logger/spool/config-archive

    # Install config schema for the configuration UI
    install -d ${D}/usr/share/ae400/config/schema
    if [ -f ${TOPDIR}/../shared/config/schema/config_user.schema.json ]; then
        install -m 0644 ${TOPDIR}/../shared/config/schema/config_user.schema.json \
            ${D}/usr/share/ae400/config/schema/config_user.schema.json
    fi

    # Install AspectMon symbol table for the web UI.
    # Fail closed if it is missing: shipping an image without symbols.json (or
    # with a stale one) silently breaks AspectMon variable resolution. The table
    # is regenerated and provenance-verified against the DSP binary earlier in
    # the SD-image build (build-sd-image-full.sh step 5b).
    install -d ${D}/usr/share/ae400/aspectmon
    if [ ! -f ${TOPDIR}/../config/aspectmon/symbols.json ]; then
        bbfatal "config/aspectmon/symbols.json missing — rebuild the DSP application (scripts/build-dsp.sh) to regenerate it before packaging web-server"
    fi
    install -m 0644 ${TOPDIR}/../config/aspectmon/symbols.json \
        ${D}/usr/share/ae400/aspectmon/symbols.json

    # Install web root directory (read-only for service)
    install -d ${D}/var/www
    chown root:root ${D}/var/www

    # Install UI files (from web-server/src/ui/_site if they exist)
    if [ -d ${TOPDIR}/../applications/web-server/src/ui/_site ]; then
        cp -r ${TOPDIR}/../applications/web-server/src/ui/_site/* ${D}/var/www/ || true
    fi

    # Create service home directory
    install -d ${D}/var/lib/ae400-webserver
    chown ae400web:ae400web ${D}/var/lib/ae400-webserver
    chmod 0750 ${D}/var/lib/ae400-webserver

    # Install systemd service
    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -d ${D}${systemd_system_unitdir}
        install -m 0644 ${WORKDIR}/ae400-webserver.service ${D}${systemd_system_unitdir}/
    fi

    # Install sysvinit script (for non-systemd systems)
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${EXTERNALSRC}/init.d/server.sh ${D}${sysconfdir}/init.d/ae400-webserver

    # Portable configuration signing keypair (AIT-59)
    # Private key: mode 0640, group ae400web so the web server can sign
    # exports after privilege drop. Public key: world-readable.
    CONFIG_SIGNING_PRIVKEY="${TOPDIR}/../config/security/config-signing-key.pem"
    CONFIG_SIGNING_PUBKEY="${TOPDIR}/../config/security/config-signing-key.pub"
    if [ ! -f "$CONFIG_SIGNING_PRIVKEY" ]; then
        bbfatal "Portable config signing private key missing: $CONFIG_SIGNING_PRIVKEY"
    fi
    if [ ! -f "$CONFIG_SIGNING_PUBKEY" ]; then
        bbfatal "Portable config signing public key missing: $CONFIG_SIGNING_PUBKEY"
    fi
    install -d ${D}${sysconfdir}/ae400
    install -m 0640 "$CONFIG_SIGNING_PRIVKEY" ${D}${sysconfdir}/ae400/config-signing-key
    install -m 0644 "$CONFIG_SIGNING_PUBKEY" ${D}${sysconfdir}/ae400/config-signing-key.pub
}

SYSTEMD_SERVICE:${PN} = "ae400-webserver.service"
SYSTEMD_AUTO_ENABLE = "enable"

# Do NOT list ${sysconfdir}/ae400 here — can-gateway owns that directory.
# Only list concrete files we install.
#
# DIRFILES limits which directories this package %dir-owns — without this
# the RPM spec auto-claims every parent directory (including /etc/init.d,
# /var/lib) which collides with sysvinit/iproute2 during do_rootfs.
DIRFILES = "/var/lib/ae400-webserver /var/www /usr/share/ae400 /usr/share/ae400/config /usr/share/ae400/config/schema /usr/share/ae400/aspectmon"

FILES:${PN} += "${bindir}/ae400-webserver \
                ${sysconfdir}/init.d/ae400-webserver \
                ${sysconfdir}/ae400/config-signing-key \
                ${sysconfdir}/ae400/config-signing-key.pub \
                /var/www \
                /var/lib/ae400-webserver \
                /var/lib/database-logger/spool/config-archive \
                /usr/share/ae400 \
                ${systemd_system_unitdir}/ae400-webserver.service"

RDEPENDS:${PN} = "libsqlite3"

# Set /etc/ae400 permissions and fix config ownership at first boot.
# The directory itself is installed by can-gateway; we only adjust permissions.
pkg_postinst:${PN}() {
    if [ -d $D${sysconfdir}/ae400 ]; then
        chgrp ae400web $D${sysconfdir}/ae400
        chmod 0775 $D${sysconfdir}/ae400
    fi
    if [ -f $D${sysconfdir}/ae400/config_user.json ]; then
        chown ae400web:ae400web $D${sysconfdir}/ae400/config_user.json
    fi
    # Portable config signing private key: group-readable by ae400web.
    if [ -f $D${sysconfdir}/ae400/config-signing-key ]; then
        chown root:ae400web $D${sysconfdir}/ae400/config-signing-key
        chmod 0640 $D${sysconfdir}/ae400/config-signing-key
    fi
    if [ -f $D${sysconfdir}/ae400/config-signing-key.pub ]; then
        chown root:root $D${sysconfdir}/ae400/config-signing-key.pub
        chmod 0644 $D${sysconfdir}/ae400/config-signing-key.pub
    fi
}
