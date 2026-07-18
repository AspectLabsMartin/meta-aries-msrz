KERNEL_URL = "git://github.com/ARIES-Embedded/rz_linux-cip.git"
BRANCH = "${@oe.utils.conditional("IS_RT_BSP", "1", "rz-5.10-cip54-rt23", "msrz-5.10-cip54",d)}"
SRCREV = "${@oe.utils.conditional("IS_RT_BSP", "1", "ad4ffa82dac9b3037784aa10d366e1272bc58fc7", "370cd3ea4904c7814087eff3d0ac058c5b422f04",d)}"

FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

# Add ARIES device tree files for RT kernel build (missing in upstream RT branch)
SRC_URI_append = "${@oe.utils.conditional("IS_RT_BSP", "1", " file://aries/", "",d)}"

# Add kernel config fragment for audio support
SRC_URI_append = " file://audio.cfg"

# Add kernel config fragments for USB WiFi (MT7612U AP+STA)
SRC_URI_append = " file://wifi-usb.cfg"
SRC_URI_append = " file://mt76-wifi.cfg"
SRC_URI_append = " file://netfilter.cfg"

# Add kernel config fragment for the RZ/G2UL hardware watchdog (/dev/watchdog)
SRC_URI_append = " file://watchdog.cfg"

# Add kernel config fragment for lockup/hang crash forensics + auto-recovery.
# Enables the softlockup/hung-task/workqueue detectors and panic-on-oops so a
# silent SOM freeze becomes a loud panic backtrace on the (off-board-logged)
# serial console, followed by an auto-reboot. See crash-hardening.cfg header.
SRC_URI_append = " file://crash-hardening.cfg"

# EXPERIMENT (2026-07-12): disable PREEMPT_RT to test whether the silent hard
# lockups are an RT-specific driver/lock issue. Flips only the preemption model
# on the same branch/DTS for a clean single-variable soak. See nonrt-preempt.cfg.
SRC_URI_append = " file://nonrt-preempt.cfg"

do_configure_append() {
    if [ "${IS_RT_BSP}" = "1" ]; then
        # Copy ARIES device tree files to kernel source for RT build
        cp -r ${WORKDIR}/aries ${S}/arch/arm64/boot/dts/
    fi
}

