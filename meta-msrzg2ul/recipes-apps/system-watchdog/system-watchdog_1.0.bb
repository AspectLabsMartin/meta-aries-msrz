SUMMARY = "AE400 system watchdog + black-box recorder"
PR = "r0"
DESCRIPTION = "Pets the SoC hardware watchdog and records persistent resource \
telemetry to diagnose and auto-recover from unattended SOM lockups."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://README.md;beginline=1;endline=12;md5=fe5286e6f240b680013072c7096eb28a"

inherit externalsrc update-rc.d

# Use the external source tree in this repo
EXTERNALSRC = "${TOPDIR}/../applications/system-watchdog"
EXTERNALSRC_BUILD = "${EXTERNALSRC}"
S = "${EXTERNALSRC}"
B = "${S}"

# procps  -> ps with RSS sorting for top-process capture
# findutils -> find for the open-fd leak counter
RDEPENDS:${PN} = "procps findutils"

# Init script configuration: start very early (08) and stop late (92) so the
# recorder/watchdog covers as much of the runtime window as possible.
INITSCRIPT_NAME = "system-watchdog"
INITSCRIPT_PARAMS = "defaults 08 92"

# Prevent RPM directory ownership conflicts with base-system packages
DIRFILES = ""

do_compile() {
    :
}

do_install() {
    oe_runmake install DESTDIR=${D}
}

FILES:${PN} = " \
    ${bindir}/system-watchdog.sh \
    ${sysconfdir}/init.d/system-watchdog \
"
