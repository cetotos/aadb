/*
 * Minimal USB transport stubs for wireless-only host builds.
 */

#include "transport.h"

#if ADB_HOST && defined(ADB_WIRELESS_ONLY)
void init_usb_transport(atransport*, usb_handle*) {}
#endif
