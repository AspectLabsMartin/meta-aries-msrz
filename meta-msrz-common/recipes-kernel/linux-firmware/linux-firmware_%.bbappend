# Add MediaTek MT76x2 (MT7612U) firmware package
# The upstream linux-firmware recipe only splits out mt7601u.
# We need mt7662.bin and mt7662_rom_patch.bin for the MT7612U dongle.

PACKAGES =+ "${PN}-mt76x2 ${PN}-mt76x2-license"

LICENSE_${PN}-mt76x2 = "Firmware-ralink_a_mediatek_company_firmware"
LICENSE_${PN}-mt76x2-license = "Firmware-ralink_a_mediatek_company_firmware"

FILES_${PN}-mt76x2-license = ""
FILES_${PN}-mt76x2 = " \
  ${nonarch_base_libdir}/firmware/mediatek/mt7662.bin \
  ${nonarch_base_libdir}/firmware/mediatek/mt7662_rom_patch.bin \
"

RDEPENDS_${PN}-mt76x2 += "${PN}-mt76x2-license ${PN}-mt7601u-license"
