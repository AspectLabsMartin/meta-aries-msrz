SUMMARY = "AE400 MQTT bridge"
PR = "r0"
DESCRIPTION = "Publishes the AE400's operational data to an MQTT broker and, at a \
control access level, relays broker commands to the controller (spec 027). \
Idle until MQTT is enabled in the settings."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://Makefile;beginline=1;endline=10;md5=e5c5074ee8cdeaf7085f0cf173c05997"

# Note: do NOT inherit update-rc.d. /etc/init.d/ae400 starts the bridge with
# the gateway (start_companions), after the startup-compliance gate at boot and
# after a bundle install. Units updated by bundle get the bridge inside
# ae400-support (no rc link there either), so both paths start it the same way.
inherit externalsrc

# Use the external source tree in this repo
EXTERNALSRC = "${TOPDIR}/../applications/mqtt-bridge"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

# libmosquitto (meta-networking); ca-certificates for brokers with a public CA
DEPENDS = "mosquitto"
RDEPENDS:${PN} = "libmosquitto1 ca-certificates"

# Prevent RPM directory ownership conflicts with base-system packages
DIRFILES = ""

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
    ${bindir}/mqtt-bridge \
    ${sysconfdir}/init.d/mqtt-bridge \
"
