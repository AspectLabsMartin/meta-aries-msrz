# Add MediaTek MT76x2 (MT7612U) firmware package
# The upstream linux-firmware recipe only splits out mt7601u.
# We need mt7662.bin and mt7662_rom_patch.bin for the MT7612U dongle.
# The license file (LICENCE.ralink_a_mediatek_company_firmware) is already
# shipped by the mt7601u-license package, so we reuse that dependency.

PACKAGES =+ "${PN}-mt76x2"

LICENSE_${PN}-mt76x2 = "Firmware-ralink_a_mediatek_company_firmware"

FILES_${PN}-mt76x2 = " \
  ${nonarch_base_libdir}/firmware/mediatek/mt7662.bin \
  ${nonarch_base_libdir}/firmware/mediatek/mt7662_rom_patch.bin \
  ${nonarch_base_libdir}/firmware/mt7662.bin \
  ${nonarch_base_libdir}/firmware/mt7662_rom_patch.bin \
"

RDEPENDS_${PN}-mt76x2 += "${PN}-mt7601u-license"

# Kernel 5.10 mt76x2u requests firmware without the mediatek/ prefix.
# Create symlinks so both paths work.
do_install_append() {
    ln -sf mediatek/mt7662.bin ${D}${nonarch_base_libdir}/firmware/mt7662.bin
    ln -sf mediatek/mt7662_rom_patch.bin ${D}${nonarch_base_libdir}/firmware/mt7662_rom_patch.bin
}
