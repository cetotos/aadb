/*
 * Minimal cutils sockets implementation for adb host builds.
 */

#include "cutils/sockets.h"

#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <netdb.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include <cstring>
#include <string>

namespace {

std::string make_local_path(const char* name, int namespace_id) {
    if (namespace_id == ANDROID_SOCKET_NAMESPACE_ABSTRACT) {
        return std::string(name);
    }
    if (namespace_id == ANDROID_SOCKET_NAMESPACE_RESERVED) {
        return std::string("/dev/socket/") + name;
    }
    return std::string(name);
}

int set_nonblocking(int fd, bool enabled) {
    int flags = fcntl(fd, F_GETFL);
    if (flags == -1) return -1;
    if (enabled) {
        flags |= O_NONBLOCK;
    } else {
        flags &= ~O_NONBLOCK;
    }
    return fcntl(fd, F_SETFL, flags);
}

int connect_with_timeout(int fd, const struct sockaddr* addr, socklen_t addrlen, int timeout_ms) {
    if (timeout_ms <= 0) {
        return connect(fd, addr, addrlen);
    }

    if (set_nonblocking(fd, true) != 0) {
        return -1;
    }

    int rc = connect(fd, addr, addrlen);
    if (rc == 0) {
        set_nonblocking(fd, false);
        return 0;
    }
    if (errno != EINPROGRESS) {
        return -1;
    }

    fd_set wfds;
    FD_ZERO(&wfds);
    FD_SET(fd, &wfds);

    struct timeval tv;
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;

    rc = select(fd + 1, nullptr, &wfds, nullptr, &tv);
    if (rc <= 0) {
        if (rc == 0) {
            errno = ETIMEDOUT;
        }
        return -1;
    }

    int so_error = 0;
    socklen_t len = sizeof(so_error);
    if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &so_error, &len) != 0) {
        return -1;
    }
    if (so_error != 0) {
        errno = so_error;
        return -1;
    }

    set_nonblocking(fd, false);
    return 0;
}

}  // namespace

int socket_inaddr_any_server(int port, int type) {
    int fd = socket(AF_INET, type, 0);
    if (fd < 0) return -1;

    int on = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &on, sizeof(on));

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons(static_cast<uint16_t>(port));

    if (bind(fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) != 0) {
        close(fd);
        return -1;
    }
    if (type == SOCK_STREAM || type == SOCK_SEQPACKET) {
        if (listen(fd, SOMAXCONN) != 0) {
            close(fd);
            return -1;
        }
    }
    return fd;
}

int socket_local_client(const char* name, int namespace_id, int type) {
    int fd = socket(AF_UNIX, type, 0);
    if (fd < 0) return -1;

    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;

    if (namespace_id == ANDROID_SOCKET_NAMESPACE_ABSTRACT) {
        addr.sun_path[0] = '\0';
        std::strncpy(addr.sun_path + 1, name, sizeof(addr.sun_path) - 2);
    } else {
        std::string path = make_local_path(name, namespace_id);
        std::strncpy(addr.sun_path, path.c_str(), sizeof(addr.sun_path) - 1);
    }

    if (connect(fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) != 0) {
        close(fd);
        return -1;
    }
    return fd;
}

int socket_local_server(const char* name, int namespace_id, int type) {
    int fd = socket(AF_UNIX, type, 0);
    if (fd < 0) return -1;

    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;

    if (namespace_id == ANDROID_SOCKET_NAMESPACE_ABSTRACT) {
        addr.sun_path[0] = '\0';
        std::strncpy(addr.sun_path + 1, name, sizeof(addr.sun_path) - 2);
    } else {
        std::string path = make_local_path(name, namespace_id);
        std::strncpy(addr.sun_path, path.c_str(), sizeof(addr.sun_path) - 1);
        unlink(addr.sun_path);
    }

    if (bind(fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) != 0) {
        close(fd);
        return -1;
    }
    if (type == SOCK_STREAM || type == SOCK_SEQPACKET) {
        if (listen(fd, SOMAXCONN) != 0) {
            close(fd);
            return -1;
        }
    }
    return fd;
}

int socket_get_local_port(int s) {
    sockaddr_storage addr{};
    socklen_t len = sizeof(addr);
    if (getsockname(s, reinterpret_cast<sockaddr*>(&addr), &len) != 0) {
        return -1;
    }
    if (addr.ss_family == AF_INET) {
        return ntohs(reinterpret_cast<sockaddr_in*>(&addr)->sin_port);
    }
    if (addr.ss_family == AF_INET6) {
        return ntohs(reinterpret_cast<sockaddr_in6*>(&addr)->sin6_port);
    }
    return -1;
}

int socket_network_client_timeout(const char* host, int port, int type, int timeout,
                                  int* getaddrinfo_error) {
    if (getaddrinfo_error) *getaddrinfo_error = 0;
    if (!host) {
        errno = EINVAL;
        return -1;
    }

    char port_str[16];
    snprintf(port_str, sizeof(port_str), "%d", port);

    addrinfo hints{};
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = type;
    hints.ai_flags = AI_ADDRCONFIG;

    addrinfo* res = nullptr;
    int gai = getaddrinfo(host, port_str, &hints, &res);
    if (gai != 0) {
        if (getaddrinfo_error) *getaddrinfo_error = gai;
        errno = EHOSTUNREACH;
        return -1;
    }

    int fd = -1;
    for (addrinfo* ai = res; ai != nullptr; ai = ai->ai_next) {
        fd = socket(ai->ai_family, ai->ai_socktype, ai->ai_protocol);
        if (fd < 0) continue;
        if (connect_with_timeout(fd, ai->ai_addr, ai->ai_addrlen, timeout) == 0) {
            break;
        }
        close(fd);
        fd = -1;
    }

    freeaddrinfo(res);
    return fd;
}
