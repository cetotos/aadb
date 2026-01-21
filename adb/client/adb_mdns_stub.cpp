/*
 * Minimal mDNS stubs for wireless-only host builds.
 */

#include "adb_mdns.h"

#if ADB_HOST && defined(ADB_DISABLE_MDNS)

bool adb_secure_connect_by_service_name(const std::string&) {
    return false;
}

void mdns_cleanup() {}

std::string mdns_check() {
    return "mdns disabled";
}

std::string mdns_list_discovered_services() {
    return "mdns disabled";
}

std::optional<MdnsInfo> mdns_get_connect_service_info(const std::string&) {
    return std::nullopt;
}

std::optional<MdnsInfo> mdns_get_pairing_service_info(const std::string&) {
    return std::nullopt;
}

AdbMdnsResponderFuncs StartMdnsResponderDiscovery() {
    return {
            .mdns_check = nullptr,
            .mdns_list_discovered_services = nullptr,
            .mdns_get_connect_service_info = nullptr,
            .mdns_get_pairing_service_info = nullptr,
            .mdns_cleanup = nullptr,
            .adb_secure_connect_by_service_name = nullptr,
    };
}

#endif  // ADB_HOST && ADB_DISABLE_MDNS
