/*
 * Minimal transport mDNS stubs for wireless-only host builds.
 */

#include "transport.h"

#if ADB_HOST && defined(ADB_DISABLE_MDNS)
void init_mdns_transport_discovery() {}

bool using_bonjour(void) {
    return false;
}
#endif  // ADB_HOST && ADB_DISABLE_MDNS
