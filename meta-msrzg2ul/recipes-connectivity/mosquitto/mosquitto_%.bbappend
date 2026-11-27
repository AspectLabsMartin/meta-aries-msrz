# AE400 (spec 027): only libmosquitto is used, by mqtt-bridge; no broker runs
# on the unit. Build with TLS only: the default "dlt websockets" options pull
# in dlt-daemon and libwebsockets, which nothing here needs.
PACKAGECONFIG = "ssl"
