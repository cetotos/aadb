/*
 * Minimal USB stubs for wireless-only host builds.
 */

#include "client/usb.h"

#include <errno.h>

#if defined(ADB_WIRELESS_ONLY)

void usb_init() {}

void usb_cleanup() {}

int usb_write(usb_handle*, const void*, int) {
    errno = ENOTSUP;
    return -1;
}

int usb_read(usb_handle*, void*, int) {
    errno = ENOTSUP;
    return -1;
}

int usb_close(usb_handle*) {
    return 0;
}

void usb_reset(usb_handle*) {}

void usb_kick(usb_handle*) {}

size_t usb_get_max_packet_size(usb_handle*) {
    return 4096;
}

bool is_adb_interface(int, int, int) {
    return false;
}

bool is_libusb_enabled() {
    return false;
}

#endif  // ADB_WIRELESS_ONLY
