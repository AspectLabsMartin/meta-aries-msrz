SUMMARY = "CAN Flash Tool for DSP firmware updates"
PR = "r3"
DESCRIPTION = "Flash encrypted firmware images to TMS320F280049 DSP via CAN bootloader protocol."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://README.md;beginline=1;endline=20;md5=a132bff7f95da09c8a8ecfefd4ebe389"

inherit externalsrc

# Use the external source tree in this repo
EXTERNALSRC = "${TOPDIR}/../applications/can-flash-tool"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

# Build dependencies
DEPENDS = ""

# Build using the Makefile with proper cross-compilation flags
# CC is set by Yocto, which triggers flat BUILD_DIR mode in Makefile
EXTRA_OEMAKE = "CC='${CC}' CFLAGS='${CFLAGS} -I${S}/include' LDFLAGS='${LDFLAGS}' BUILD_DIR='${S}/build/yocto'"

do_compile() {
    oe_runmake clean BUILD_DIR="${S}/build/yocto"
    oe_runmake ${EXTRA_OEMAKE}
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/build/yocto/can-flash-tool ${D}${bindir}/can-flash-tool
}

FILES:${PN} = "${bindir}/can-flash-tool"
