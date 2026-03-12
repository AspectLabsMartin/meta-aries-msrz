SUMMARY = "USB Headless Firmware Update Service"
DESCRIPTION = "Monitors USB mounts for firmware bundles and triggers automatic updates"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://README.md;beginline=1;endline=10;md5=65812394c7f2117c5abd2b63bf7a9dec"

inherit externalsrc update-rc.d

# Use the external source tree in this repo
EXTERNALSRC = "${TOPDIR}/../applications/usb-update-service"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

# Runtime dependencies - needs can-flash-tool for DSP updates
RDEPENDS:${PN} = "can-flash-tool"

# Init script configuration
INITSCRIPT_NAME = "usb-update-service"
INITSCRIPT_PARAMS = "defaults 95 05"

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
    ${bindir}/usb-update-service \
    ${sysconfdir}/init.d/usb-update-service \
"
