SUMMARY = "AE400 Firmware Update Service"
DESCRIPTION = "Daemon for orchestrating DSP and system firmware updates via CAN transport and IPC"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://README.md;beginline=1;endline=20;md5=9cc280e9f7a3d249c321e9b15d304035"

inherit externalsrc update-rc.d

# Use the external source tree in this repo
EXTERNALSRC = "${TOPDIR}/../applications/firmware-update-service"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

# Runtime dependencies - needs can-flash-tool for DSP updates
RDEPENDS:${PN} = "can-flash-tool"

# Init script configuration
INITSCRIPT_NAME = "firmware-update-service"
INITSCRIPT_PARAMS = "defaults 90 10"

# Pass CC and LDFLAGS from Yocto, let Makefile's CFLAGS += handle include paths
EXTRA_OEMAKE = "CC='${CC}' LDFLAGS='${LDFLAGS}'"

do_compile() {
    oe_runmake clean
    oe_runmake ${EXTRA_OEMAKE}
}

do_install() {
    oe_runmake install DESTDIR=${D}
}

# Package the daemon, library, headers, init script, and storage dirs
FILES:${PN} = " \
    ${bindir}/firmware-update-service \
    ${sysconfdir}/init.d/firmware-update-service \
    /usr/share/sounds/usb-update-running.wav \
    /usr/share/sounds/usb-update-aborted.wav \
    /usr/share/sounds/usb-update-finished.wav \
    /var/lib/firmware-updates \
    /var/lib/firmware-updates/history \
"

FILES:${PN}-dev = " \
    ${libdir}/libupdate_ipc.a \
    ${includedir}/firmware-update/*.h \
"
