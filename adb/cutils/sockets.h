/*
 * Minimal cutils sockets API for adb host builds.
 */

#pragma once

#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef int cutils_socket_t;

#ifndef INVALID_SOCKET
#define INVALID_SOCKET (-1)
#endif

#define ANDROID_SOCKET_NAMESPACE_ABSTRACT   0
#define ANDROID_SOCKET_NAMESPACE_RESERVED   1
#define ANDROID_SOCKET_NAMESPACE_FILESYSTEM 2

int socket_inaddr_any_server(int port, int type);
int socket_local_client(const char* name, int namespace_id, int type);
int socket_local_server(const char* name, int namespace_id, int type);
int socket_get_local_port(int s);
int socket_network_client_timeout(const char* host, int port, int type, int timeout,
                                  int* getaddrinfo_error);

#ifdef __cplusplus
}
#endif
