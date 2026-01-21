/*
 * Incremental install stubs for wireless-only host builds.
 */

#include "incremental.h"
#include "incremental_server.h"

#if defined(ADB_WIRELESS_ONLY)

namespace incremental {

bool can_install(const Files&) {
    return false;
}

std::optional<Process> install(const Files&, const Args&, bool) {
    return std::nullopt;
}

Result wait_for_installation(int) {
    return Result::Failure;
}

bool serve(int, int, int, const char**) {
    return false;
}

}  // namespace incremental

#endif  // ADB_WIRELESS_ONLY
