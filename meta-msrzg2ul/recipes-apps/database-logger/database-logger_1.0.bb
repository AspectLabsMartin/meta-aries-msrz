SUMMARY = "AE400 Database Logger Service"
PR = "r4"
DESCRIPTION = "Daemon for logging operational events and telemetry data to SQLite databases"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://Makefile;beginline=1;endline=10;md5=f2211fcfa57fcdbdbf88f58e06b25dc7"

# Note: do NOT inherit update-rc.d. This service is started exclusively by
# firmware-update-service after the startup-compliance boot gate passes
# (sc_start_managed_services). The init.d script is still installed so
# fwupd can invoke /etc/init.d/database-logger start.
inherit externalsrc

# Use the external source tree in this repo
EXTERNALSRC = "${TOPDIR}/../applications/database-logger"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

# Build dependency - need sqlite3 headers and library at build time
DEPENDS = "sqlite3"

# Runtime dependencies - need sqlite3 shared library on target
RDEPENDS:${PN} = "sqlite3"

# Prevent RPM directory ownership conflicts with base-system packages
DIRFILES = "/var/lib/database-logger /var/lib/database-logger/spool /etc/database-logger /etc/ae400/config_registry.d"

# Pass CC and LDFLAGS from Yocto
EXTRA_OEMAKE = "CC='${CC}' LDFLAGS='${LDFLAGS}'"

do_compile() {
    oe_runmake clean
    oe_runmake ${EXTRA_OEMAKE}
}

do_install() {
    oe_runmake install DESTDIR=${D}
}

FILES:${PN} = " \
    ${bindir}/database-logger \
    ${sysconfdir}/init.d/database-logger \
    ${sysconfdir}/database-logger \
    ${sysconfdir}/ae400/config_registry.d \
    /var/lib/database-logger \
    /var/lib/database-logger/spool \
"
