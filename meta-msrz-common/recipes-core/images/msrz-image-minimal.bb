SUMMARY = "A console-only image that fully supports the target device hardware."

IMAGE_FEATURES += "splash"

LICENSE = "MIT"

inherit core-image

# Fix host-uid contamination from externalsrc recipes. The `install -m` calls
# in externalsrc do_install do not set ownership, so files inherit the host
# uid/gid (1000:1000). Individual recipes try to chown in pkg_postinst but
# `chown ae400web` fails silently there because pseudo resolves names against
# the host passwd (which has no ae400web user). This postprocess step resolves
# uids numerically from the target rootfs and corrects ownership + modes on
# all AE400 paths.
ROOTFS_POSTPROCESS_COMMAND += "fix_ae400_ownership;"
ROOTFS_POSTPROCESS_COMMAND += "verify_ae400_required_artifacts;"

fix_ae400_ownership() {
    local passwd="${IMAGE_ROOTFS}${sysconfdir}/passwd"
    local group="${IMAGE_ROOTFS}${sysconfdir}/group"

    if [ ! -f "${passwd}" ] || [ ! -f "${group}" ]; then
        bbfatal "fix_ae400_ownership: missing ${passwd} or ${group}"
    fi

    local ae400web_uid=$(awk -F: '$1=="ae400web"{print $3}' "${passwd}")
    local ae400web_gid=$(awk -F: '$1=="ae400web"{print $3}' "${group}")

    if [ -z "${ae400web_uid}" ] || [ -z "${ae400web_gid}" ]; then
        bbfatal "fix_ae400_ownership: could not resolve ae400web uid/gid from rootfs passwd/group"
    fi

    bbnote "fix_ae400_ownership: ae400web uid=${ae400web_uid} gid=${ae400web_gid}"

    local etc_ae400="${IMAGE_ROOTFS}${sysconfdir}/ae400"

    if [ -d "${etc_ae400}" ]; then
        # Factory image must not ship a pre-seeded auth database.
        # The web-server bootstraps /etc/ae400/users.json on first boot.
        if [ -f "${etc_ae400}/users.json" ]; then
            bbwarn "fix_ae400_ownership: removing pre-seeded /etc/ae400/users.json from image"
            rm -f "${etc_ae400}/users.json"
        fi

        # Directory: root:ae400web 0775 so ae400web can create tmp/bak files
        # for atomic writes (configuration save, archive, etc.)
        chown 0:${ae400web_gid} "${etc_ae400}"
        chmod 0775 "${etc_ae400}"

        # Recursively normalise everything UNDER /etc/ae400 (not the dir itself)
        # to root-owned first, then override specific paths below.
        find "${etc_ae400}" -mindepth 1 -exec chown 0:0 {} \;

        # Directories under /etc/ae400: 0755 (config_registry.d, sounds)
        find "${etc_ae400}" -mindepth 1 -type d -exec chmod 0755 {} \;

        # User-writable config: ae400web:ae400web 0664
        if [ -f "${etc_ae400}/config_user.json" ]; then
            chown ${ae400web_uid}:${ae400web_gid} "${etc_ae400}/config_user.json"
            chmod 0664 "${etc_ae400}/config_user.json"
        fi

        # Factory config: root-readable 0644
        if [ -f "${etc_ae400}/config_factory.json" ]; then
            chown 0:0 "${etc_ae400}/config_factory.json"
            chmod 0644 "${etc_ae400}/config_factory.json"
        fi

        # Config signing private key: root:ae400web 0640 so web-server (dropped
        # to ae400web) can sign export bundles.
        if [ -f "${etc_ae400}/config-signing-key" ]; then
            chown 0:${ae400web_gid} "${etc_ae400}/config-signing-key"
            chmod 0640 "${etc_ae400}/config-signing-key"
        fi
        if [ -f "${etc_ae400}/config-signing-key.pub" ]; then
            chown 0:0 "${etc_ae400}/config-signing-key.pub"
            chmod 0644 "${etc_ae400}/config-signing-key.pub"
        fi

        # Firmware encryption private key: root:root 0600. Read by firmware-
        # update-service (root) for embedded bundle decryption.
        if [ -f "${etc_ae400}/firmware-encryption-key.pem" ]; then
            chown 0:0 "${etc_ae400}/firmware-encryption-key.pem"
            chmod 0600 "${etc_ae400}/firmware-encryption-key.pem"
        fi
        if [ -f "${etc_ae400}/firmware-encryption-key.pub" ]; then
            chown 0:0 "${etc_ae400}/firmware-encryption-key.pub"
            chmod 0644 "${etc_ae400}/firmware-encryption-key.pub"
        fi
        if [ -f "${etc_ae400}/firmware-signing-key.pub" ]; then
            chown 0:0 "${etc_ae400}/firmware-signing-key.pub"
            chmod 0644 "${etc_ae400}/firmware-signing-key.pub"
        fi
    fi

    # Web server home: ae400web-owned 0750.
    if [ -d "${IMAGE_ROOTFS}/var/lib/ae400-webserver" ]; then
        chown -R ${ae400web_uid}:${ae400web_gid} "${IMAGE_ROOTFS}/var/lib/ae400-webserver"
        chmod 0750 "${IMAGE_ROOTFS}/var/lib/ae400-webserver"
    fi

    # Config archive spool: ae400web-writable.
    if [ -d "${IMAGE_ROOTFS}/var/lib/database-logger/spool/config-archive" ]; then
        chown -R ${ae400web_uid}:${ae400web_gid} "${IMAGE_ROOTFS}/var/lib/database-logger/spool/config-archive"
    fi

    # Web root and binaries: root:root, executables 0755, data 0644.
    if [ -d "${IMAGE_ROOTFS}/var/www" ]; then
        find "${IMAGE_ROOTFS}/var/www" -exec chown 0:0 {} \;
        find "${IMAGE_ROOTFS}/var/www" -type d -exec chmod 0755 {} \;
        find "${IMAGE_ROOTFS}/var/www" -type f -exec chmod 0644 {} \;
    fi

    # AE400 binaries in /usr/bin: root-owned 0755.
    for bin in ae400 ae400-webserver database-logger can-flash-tool usb-update-service \
               ae400-hostname can-gateway bundle-crypt firmware-update-service; do
        if [ -f "${IMAGE_ROOTFS}/usr/bin/${bin}" ]; then
            chown 0:0 "${IMAGE_ROOTFS}/usr/bin/${bin}"
            chmod 0755 "${IMAGE_ROOTFS}/usr/bin/${bin}"
        fi
    done

    # Shared data: root-owned 0644.
    if [ -d "${IMAGE_ROOTFS}/usr/share/ae400" ]; then
        find "${IMAGE_ROOTFS}/usr/share/ae400" -exec chown 0:0 {} \;
        find "${IMAGE_ROOTFS}/usr/share/ae400" -type d -exec chmod 0755 {} \;
        find "${IMAGE_ROOTFS}/usr/share/ae400" -type f -exec chmod 0644 {} \;
    fi

    # Init scripts: root:root 0755.
    for init in ae400 ae400-webserver database-logger firmware-update-service usb-update-service; do
        if [ -f "${IMAGE_ROOTFS}${sysconfdir}/init.d/${init}" ]; then
            chown 0:0 "${IMAGE_ROOTFS}${sysconfdir}/init.d/${init}"
            chmod 0755 "${IMAGE_ROOTFS}${sysconfdir}/init.d/${init}"
        fi
    done

    # Schema files under /etc/database-logger: root-owned 0644.
    if [ -d "${IMAGE_ROOTFS}${sysconfdir}/database-logger" ]; then
        find "${IMAGE_ROOTFS}${sysconfdir}/database-logger" -exec chown 0:0 {} \;
        find "${IMAGE_ROOTFS}${sysconfdir}/database-logger" -type f -exec chmod 0644 {} \;
    fi

    bbnote "fix_ae400_ownership: complete"
}

verify_ae400_required_artifacts() {
    local manifest_file="${IMAGE_ROOTFS}${sysconfdir}/ae400/image-artifacts.manifest"

    install -d "${IMAGE_ROOTFS}${sysconfdir}/ae400"
    cat > "${manifest_file}" << 'EOF'
/etc/ae400/config_user.json
/etc/ae400/config_factory.json
/etc/ae400/firmware-signing-key.pub
/etc/ae400/firmware-encryption-key.pem
/etc/ae400/firmware-encryption-key.pub
/etc/ae400/sounds/startup.wav
/etc/ae400/sounds/poweron.wav
/etc/ae400/sounds/poweroff.wav
/etc/ae400/sounds/Cannotpoweroncheckbreaker.wav
/etc/ae400/sounds/SystemFaultCannotPowerOn.wav
/etc/ae400/sounds/BMSDisconnected.wav
/etc/ae400/sounds/breaker_open.wav
/etc/ae400/sounds/system_fault.wav
/etc/ae400/sounds/bms_disconnected.wav
/etc/ae400/sounds/ae400_status.wav
/etc/ae400/sounds/connected.wav
/etc/ae400/sounds/island.wav
/etc/ae400/sounds/passthrough.wav
/etc/ae400/sounds/off.wav
/usr/share/ae400/config/schema/config_user.schema.json
/usr/share/ae400/embedded-bundles/image-default.tar.gz
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

    if [ -e "${IMAGE_ROOTFS}${sysconfdir}/ae400/users.json" ]; then
        bbfatal "Factory image must not contain /etc/ae400/users.json; bootstrap creates it on first boot"
    fi

    # Ownership regression guard: /etc/ae400 MUST NOT be owned by a host uid.
    # After fix_ae400_ownership runs, the directory must be root-owned with
    # gid=ae400web. If host-uid contamination re-appears, fail the build
    # instead of shipping a broken image.
    local etc_ae400_uid=$(stat -c '%u' "${IMAGE_ROOTFS}${sysconfdir}/ae400")
    local etc_ae400_gid=$(stat -c '%g' "${IMAGE_ROOTFS}${sysconfdir}/ae400")
    local ae400web_gid=$(awk -F: '$1=="ae400web"{print $3}' "${IMAGE_ROOTFS}${sysconfdir}/group")
    if [ "${etc_ae400_uid}" != "0" ] || [ "${etc_ae400_gid}" != "${ae400web_gid}" ]; then
        bbfatal "Ownership regression: /etc/ae400 is ${etc_ae400_uid}:${etc_ae400_gid}, expected 0:${ae400web_gid}"
    fi

    local cfg_user_uid=$(stat -c '%u' "${IMAGE_ROOTFS}${sysconfdir}/ae400/config_user.json")
    local ae400web_uid=$(awk -F: '$1=="ae400web"{print $3}' "${IMAGE_ROOTFS}${sysconfdir}/passwd")
    if [ "${cfg_user_uid}" != "${ae400web_uid}" ]; then
        bbfatal "Ownership regression: /etc/ae400/config_user.json is uid ${cfg_user_uid}, expected ${ae400web_uid} (ae400web)"
    fi
    if [ -f "${IMAGE_ROOTFS}${sysconfdir}/ae400/config-signing-key" ]; then
        local sk_uid=$(stat -c '%u' "${IMAGE_ROOTFS}${sysconfdir}/ae400/config-signing-key")
        local sk_gid=$(stat -c '%g' "${IMAGE_ROOTFS}${sysconfdir}/ae400/config-signing-key")
        if [ "${sk_uid}" != "0" ] || [ "${sk_gid}" != "${ae400web_gid}" ]; then
            bbfatal "Ownership regression: /etc/ae400/config-signing-key is ${sk_uid}:${sk_gid}, expected 0:${ae400web_gid}"
        fi
    fi
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
