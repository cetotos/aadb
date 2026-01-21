/*
 * Fastdeploy stubs for wireless-only host builds.
 */

#include "fastdeploy.h"

#if defined(ADB_WIRELESS_ONLY)

void fastdeploy_set_agent_update_strategy(FastDeploy_AgentUpdateStrategy) {}

int get_device_api_level() {
    return 0;
}

std::optional<com::android::fastdeploy::APKMetaData> extract_metadata(const char*) {
    return std::nullopt;
}

unique_fd install_patch(int, const char**) {
    return unique_fd();
}

unique_fd apply_patch_on_device(const char*) {
    return unique_fd();
}

int stream_patch(const char*, com::android::fastdeploy::APKMetaData, unique_fd) {
    return -1;
}

#endif  // ADB_WIRELESS_ONLY
