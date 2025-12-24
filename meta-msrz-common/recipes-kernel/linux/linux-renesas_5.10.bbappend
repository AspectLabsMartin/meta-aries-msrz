KERNEL_URL = "git://github.com/ARIES-Embedded/rz_linux-cip.git"
BRANCH = "${@oe.utils.conditional("IS_RT_BSP", "1", "rz-5.10-cip54-rt23", "msrz-5.10-cip54",d)}"
SRCREV = "${@oe.utils.conditional("IS_RT_BSP", "1", "ad4ffa82dac9b3037784aa10d366e1272bc58fc7", "370cd3ea4904c7814087eff3d0ac058c5b422f04",d)}"

FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

# Add ARIES device tree files for RT kernel build (missing in upstream RT branch)
SRC_URI_append = "${@oe.utils.conditional("IS_RT_BSP", "1", " file://aries/", "",d)}"

# Add kernel config fragment for audio support
SRC_URI_append = " file://audio.cfg"

do_configure_append() {
    if [ "${IS_RT_BSP}" = "1" ]; then
        # Copy ARIES device tree files to kernel source for RT build
        cp -r ${WORKDIR}/aries ${S}/arch/arm64/boot/dts/
    fi
}

