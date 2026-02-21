SUMMARY = "A console-only image that fully supports the target device hardware."

IMAGE_FEATURES += "splash"

LICENSE = "MIT"

inherit core-image

IMAGE_INSTALL += " \
    packagegroup-msrz-base \
    packagegroup-msrz-bringup \
    tzdata \
    iproute2 \
    can-gateway \
    ae400-fwupdate \
    web-server \
    can-flash-tool \
    database-logger \
    "

# Explicitly remove legacy standalone USB update daemon from the image.
# USB and Web update orchestration is handled by firmware-update-service.
IMAGE_INSTALL:remove = " \
    usb-update-service \
    "
