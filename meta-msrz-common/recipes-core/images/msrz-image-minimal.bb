SUMMARY = "A console-only image that fully supports the target device hardware."

IMAGE_FEATURES += "splash"

LICENSE = "MIT"

inherit core-image

ROOTFS_POSTPROCESS_COMMAND += "verify_ae400_required_artifacts;"

verify_ae400_required_artifacts() {
    local manifest_file="${IMAGE_ROOTFS}${sysconfdir}/ae400/image-artifacts.manifest"

    install -d "${IMAGE_ROOTFS}${sysconfdir}/ae400"
    cat > "${manifest_file}" << 'EOF'
/etc/ae400/config_user.json
/etc/ae400/config_factory.json
/etc/ae400/sounds/startup.wav
/etc/ae400/sounds/poweron.wav
/etc/ae400/sounds/poweroff.wav
/etc/ae400/sounds/Cannotpoweroncheckbreaker.wav
/etc/ae400/sounds/SystemFaultCannotPowerOn.wav
/usr/share/ae400/config/schema/config_user.schema.json
/usr/share/sounds/system-update-started.wav
/usr/share/sounds/system-update-aborted.wav
/usr/share/sounds/system-update-finished.wav
EOF

    while IFS= read -r relpath; do
        [ -z "${relpath}" ] && continue
        if [ ! -e "${IMAGE_ROOTFS}${relpath}" ]; then
            bbfatal "Missing required AE400 image artifact: ${relpath}"
        fi
    done < "${manifest_file}"
}

IMAGE_INSTALL += " \
    packagegroup-msrz-base \
    packagegroup-msrz-bringup \
    tzdata \
    iproute2 \
    ae400-hostname \
    can-gateway \
    ae400-fwupdate \
    usb-update-service \
    web-server \
    can-flash-tool \
    database-logger \
    iw \
    wpa-supplicant \
    hostapd \
    dnsmasq \
    iptables \
    wireless-regdb \
    rfkill \
    linux-firmware-mt76x2 \
    wifi-apsta \
    usb-modeswitch \
    usb-modeswitch-data \
    "
