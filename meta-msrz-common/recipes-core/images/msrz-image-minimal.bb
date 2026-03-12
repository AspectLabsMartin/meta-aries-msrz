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
    usb-update-service \
    web-server \
    can-flash-tool \
    database-logger \
    "
