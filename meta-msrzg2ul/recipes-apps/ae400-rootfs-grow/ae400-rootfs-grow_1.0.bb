SUMMARY = "AE400 first-boot root filesystem grow"
PR = "r0"
DESCRIPTION = "Grows the root partition and ext4 filesystem to fill the SD \
card on first boot. The image is written as a fixed 1 GB root partition; \
units ship on 8 GB cards (022-rootfs-first-boot-grow)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit externalsrc update-rc.d

# Point at the external source tree in the repo
EXTERNALSRC = "${TOPDIR}/../applications/ae400-rootfs-grow"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

# SysVinit — rcS, right after checkroot.sh (S06) has remounted / read-write
# and before the volatile/service scripts.
INITSCRIPT_NAME = "ae400-rootfs-grow"
INITSCRIPT_PARAMS = "start 07 S ."

RDEPENDS:${PN} = " \
    util-linux-sfdisk \
    util-linux-partx \
    util-linux-lsblk \
    e2fsprogs-resize2fs \
"

do_compile() {
    # Nothing to compile — shell script only
    :
}

do_install() {
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${S}/ae400-rootfs-grow.sh ${D}${sysconfdir}/init.d/ae400-rootfs-grow
    install -d ${D}${localstatedir}/lib/ae400-rootfs-grow
}

# Prevent %dir ownership of shared system dirs (/etc/init.d).
DIRFILES = ""

FILES:${PN} += " \
    ${sysconfdir}/init.d/ae400-rootfs-grow \
    ${localstatedir}/lib/ae400-rootfs-grow \
"
