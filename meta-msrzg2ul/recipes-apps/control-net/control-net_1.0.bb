SUMMARY = "AE400 control network (second Ethernet port)"
PR = "r0"
DESCRIPTION = "Gives the second Ethernet port its fixed address and runs the \
DHCP server for the energy meters and other site equipment on it (spec 025, \
AC-coupled PV). Idle until ae400 creates the enable flag from the user setting \
control_network_enabled. The same script is the control-net app of the \
ae400-core snap on Ubuntu Core."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit externalsrc update-rc.d

# Use the external source tree in this repo
EXTERNALSRC = "${TOPDIR}/../applications/control-net"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

# dnsmasq  -> DHCP server (run as a foreground child with its own config; the
#             stock /etc/init.d/dnsmasq instance is not used and serves no DHCP)
# iproute2 -> ip link / ip addr on the port
RDEPENDS:${PN} = "dnsmasq iproute2"

# After networking (01) and before firmware-update-service (90) starts ae400,
# so meters already have their leases when ae400 first looks for them.
INITSCRIPT_NAME = "control-net"
INITSCRIPT_PARAMS = "defaults 80 20"

# Own only our own directory: /etc/ae400 belongs to can-gateway (0775,
# group ae400web) and /etc/init.d to the base system.
DIRFILES = "${sysconfdir}/ae400/control-net"

do_compile() {
    :
}

do_install() {
    oe_runmake install DESTDIR=${D}
}

FILES:${PN} = " \
    ${bindir}/control-net.sh \
    ${sysconfdir}/ae400/control-net/dnsmasq-eth1.conf \
    ${sysconfdir}/init.d/control-net \
"
