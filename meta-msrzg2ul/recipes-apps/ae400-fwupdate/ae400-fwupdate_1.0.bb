SUMMARY = "AE400 Firmware Update Service"
PR = "r7"
DESCRIPTION = "Daemon for orchestrating DSP and system firmware updates via CAN transport and IPC"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://README.md;beginline=1;endline=20;md5=9cc280e9f7a3d249c321e9b15d304035"

inherit externalsrc update-rc.d

# Use the external source tree in this repo
EXTERNALSRC = "${TOPDIR}/../applications/firmware-update-service"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

# Build-time dependencies - OpenSSL for bundle signing/encryption (Ed25519, X25519, AES-256-GCM)
DEPENDS += "openssl"

AE400_IMAGE_EMBEDDED_BUNDLE ?= "${TOPDIR}/../build/firmware-packages/dsp-application/latest.tar.gz"

# Runtime dependencies - needs can-flash-tool for DSP updates, openssl for libcrypto
RDEPENDS:${PN} = "can-flash-tool libcrypto"

# Init script configuration
INITSCRIPT_NAME = "firmware-update-service"
INITSCRIPT_PARAMS = "defaults 90 10"

# Prevent RPM directory ownership conflicts with base-system packages
# (sysvinit owns /etc/init.d, base-files owns /var/lib, alsa-utils owns
# /usr/share/sounds).  With DIRFILES set, only empty or explicitly listed
# directories get %dir entries in the RPM spec.
DIRFILES = "/var/lib/firmware-updates /var/lib/firmware-updates/history"

# Pass CC and LDFLAGS from Yocto, let Makefile's CFLAGS += handle include paths
EXTRA_OEMAKE = "CC='${CC}' LDFLAGS='${LDFLAGS}'"

do_compile() {
    oe_runmake clean
    oe_runmake ${EXTRA_OEMAKE}
}

do_install() {
    SIGNING_PUBKEY="${TOPDIR}/../config/security/firmware-signing-key.pub"
    ENCRYPTION_PRIVKEY="${TOPDIR}/../config/security/firmware-encryption-key.pem"
    ENCRYPTION_PUBKEY="${TOPDIR}/../config/security/firmware-encryption-key.pub"

    if [ ! -f "${SIGNING_PUBKEY}" ]; then
        bbfatal "Signing public key missing: ${SIGNING_PUBKEY}"
    fi

    if [ ! -f "${ENCRYPTION_PRIVKEY}" ]; then
        bbfatal "Encryption private key missing: ${ENCRYPTION_PRIVKEY}"
    fi

    if [ ! -f "${ENCRYPTION_PUBKEY}" ]; then
        bbfatal "Encryption public key missing: ${ENCRYPTION_PUBKEY}"
    fi

    oe_runmake install DESTDIR=${D}

    if [ ! -f "${AE400_IMAGE_EMBEDDED_BUNDLE}" ]; then
        bbfatal "Embedded firmware bundle missing: ${AE400_IMAGE_EMBEDDED_BUNDLE}"
    fi

    install -d ${D}/usr/share/ae400/embedded-bundles
    install -m 0644 "${AE400_IMAGE_EMBEDDED_BUNDLE}" ${D}/usr/share/ae400/embedded-bundles/image-default.tar.gz
}

# Package the daemon, library, headers, init script, and storage dirs
FILES:${PN} = " \
    ${bindir}/firmware-update-service \
    ${bindir}/bundle-crypt \
    ${sysconfdir}/init.d/firmware-update-service \
    ${sysconfdir}/ae400/firmware-signing-key.pub \
    ${sysconfdir}/ae400/firmware-encryption-key.pem \
    ${sysconfdir}/ae400/firmware-encryption-key.pub \
    /usr/share/ae400/embedded-bundles/image-default.tar.gz \
    /usr/share/sounds/system-update-started.wav \
    /usr/share/sounds/system-update-aborted.wav \
    /usr/share/sounds/system-update-finished.wav \
    /usr/share/sounds/card-mismatch.wav \
    /usr/share/sounds/system-file-mismatch.wav \
    /var/lib/firmware-updates \
    /var/lib/firmware-updates/history \
"

FILES:${PN}-dev = " \
    ${libdir}/libupdate_ipc.a \
    ${includedir}/firmware-update/*.h \
"
