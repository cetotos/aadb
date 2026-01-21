cmake_minimum_required(VERSION 3.22)

project(adb_host_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(ADB_WIRELESS_ONLY "Disable USB and mDNS dependencies for host builds" ON)
option(ADB_DISABLE_MDNS "Disable mDNS discovery/bonjour backends" ON)
option(ADB_BUILD_CLI "Build adb CLI binary (host-only)" ON)
option(ADB_DISABLE_COMPRESSION "Disable brotli/lz4/zstd compression support" ON)

set(ADB_ROOT ${CMAKE_CURRENT_LIST_DIR})

set(ADB_CORE_SRCS
    adb.cpp
    adb_io.cpp
    adb_listeners.cpp
    adb_mdns.cpp
    adb_trace.cpp
    adb_unique_fd.cpp
    adb_utils.cpp
    apacket_reader.cpp
    fdevent/fdevent.cpp
    services.cpp
    sockets.cpp
    socket_spec.cpp
    sysdeps/env.cpp
    sysdeps/errno.cpp
    transport.cpp
    transport_fd.cpp
    types.cpp
    cutils/sockets.cpp
    sysdeps_unix.cpp
    sysdeps/posix/network.cpp
)

if(ANDROID OR CMAKE_SYSTEM_NAME STREQUAL "Linux")
    list(APPEND ADB_CORE_SRCS fdevent/fdevent_epoll.cpp)
else()
    list(APPEND ADB_CORE_SRCS fdevent/fdevent_poll.cpp)
endif()

set(ADB_HOST_SRCS
    client/adb_client.cpp
    client/adb_wifi.cpp
    client/auth.cpp
    client/bugreport.cpp
    client/commandline.cpp
    client/console.cpp
    client/detach.cpp
    client/file_sync_client.cpp
    client/line_printer.cpp
    client/mdns_utils.cpp
    client/pairing/pairing_client.cpp
    client/transport_emulator.cpp
    shell_service_protocol.cpp
    client/adb_install.cpp
)

set(ADB_WIRELESS_STUBS
    client/usb_stub.cpp
    client/transport_usb_stub.cpp
    client/adb_mdns_stub.cpp
    client/transport_mdns_stub.cpp
    client/fastdeploy_stub.cpp
    client/incremental_stub.cpp
)

set(ADB_CRYPTO_SRCS
    crypto/key.cpp
    crypto/rsa_2048_key.cpp
    crypto/x509_generator.cpp
)

set(ADB_PAIRING_SRCS
    pairing_auth/aes_128_gcm.cpp
    pairing_auth/pairing_auth.cpp
    pairing_connection/pairing_connection.cpp
    pairing_connection/pairing_server.cpp
)

set(ADB_TLS_SRCS
    tls/adb_ca_list.cpp
    tls/tls_connection.cpp
)

set(ADB_LIBBASE_ROOT ${ADB_ROOT}/third_party/libbase)
set(ADB_LIBBASE_SRCS
    ${ADB_LIBBASE_ROOT}/chrono_utils.cpp
    ${ADB_LIBBASE_ROOT}/cmsg.cpp
    ${ADB_LIBBASE_ROOT}/errors_unix.cpp
    ${ADB_LIBBASE_ROOT}/file.cpp
    ${ADB_LIBBASE_ROOT}/hex.cpp
    ${ADB_LIBBASE_ROOT}/logging.cpp
    ${ADB_LIBBASE_ROOT}/mapped_file.cpp
    ${ADB_LIBBASE_ROOT}/parsebool.cpp
    ${ADB_LIBBASE_ROOT}/parsenetaddress.cpp
    ${ADB_LIBBASE_ROOT}/posix_strerror_r.cpp
    ${ADB_LIBBASE_ROOT}/process.cpp
    ${ADB_LIBBASE_ROOT}/properties.cpp
    ${ADB_LIBBASE_ROOT}/stringprintf.cpp
    ${ADB_LIBBASE_ROOT}/strings.cpp
    ${ADB_LIBBASE_ROOT}/threads.cpp
)

set(ADB_CRYPTO_UTILS_ROOT ${ADB_ROOT}/third_party/system_core/libcrypto_utils)
set(ADB_CRYPTO_UTILS_SRCS
    ${ADB_CRYPTO_UTILS_ROOT}/android_pubkey.cpp
)

set(ADB_PROTO_FILES
    proto/adb_host.proto
    proto/adb_known_hosts.proto
    proto/app_processes.proto
    proto/key_type.proto
    proto/pairing.proto
)

find_package(Protobuf REQUIRED)
set(PROTOBUF_IMPORT_DIRS ${ADB_ROOT}/proto)
protobuf_generate_cpp(ADB_PROTO_SRCS ADB_PROTO_HDRS ${ADB_PROTO_FILES})

add_library(adb_libbase STATIC ${ADB_LIBBASE_SRCS})
target_include_directories(adb_libbase
    PUBLIC
        ${ADB_LIBBASE_ROOT}/include
)
target_compile_definitions(adb_libbase
    PRIVATE
        LIBBASE_DISABLE_BIONIC_PROPERTIES=1
)

add_library(adb_crypto_utils STATIC ${ADB_CRYPTO_UTILS_SRCS})
target_include_directories(adb_crypto_utils
    PUBLIC
        ${ADB_CRYPTO_UTILS_ROOT}/include
)

add_library(adb_host_wireless STATIC
    ${ADB_CORE_SRCS}
    ${ADB_HOST_SRCS}
    ${ADB_WIRELESS_STUBS}
    ${ADB_CRYPTO_SRCS}
    ${ADB_PAIRING_SRCS}
    ${ADB_TLS_SRCS}
    ${ADB_PROTO_SRCS}
    ${ADB_PROTO_HDRS}
)

set(ADB_EXTRA_INCLUDE_DIRS "" CACHE STRING "Extra include dirs for adb_host_wireless (semicolon-separated)")

target_include_directories(adb_host_wireless
    PRIVATE
        ${ADB_ROOT}
        ${ADB_ROOT}/client
        ${ADB_ROOT}/crypto/include
        ${ADB_ROOT}/pairing_auth/include
        ${ADB_ROOT}/pairing_connection/include
        ${ADB_ROOT}/tls/include
        ${ADB_ROOT}/libs/adbconnection/include
        ${ADB_ROOT}/proto
        ${ADB_LIBBASE_ROOT}/include
        ${ADB_CRYPTO_UTILS_ROOT}/include
        ${Protobuf_INCLUDE_DIR}
        ${CMAKE_CURRENT_BINARY_DIR}
        ${ADB_EXTRA_INCLUDE_DIRS}
)

target_compile_definitions(adb_host_wireless
    PRIVATE
        ADB_HOST=1
)

if(ADB_WIRELESS_ONLY)
    target_compile_definitions(adb_host_wireless
        PRIVATE
            ADB_WIRELESS_ONLY=1
    )
endif()

if(ADB_DISABLE_MDNS)
    target_compile_definitions(adb_host_wireless
        PRIVATE
            ADB_DISABLE_MDNS=1
    )
endif()

if(ADB_DISABLE_COMPRESSION)
    target_compile_definitions(adb_host_wireless
        PRIVATE
            ADB_DISABLE_COMPRESSION=1
    )
endif()

target_link_libraries(adb_host_wireless
    PRIVATE
        ${Protobuf_LIBRARIES}
        adb_libbase
        adb_crypto_utils
)

if(ANDROID)
    find_library(log-lib log)
    target_link_libraries(adb_host_wireless PRIVATE ${log-lib})
endif()

set(ADB_BORINGSSL_INCLUDE_DIR "" CACHE PATH "BoringSSL include directory")
set(ADB_BORINGSSL_SSL_LIB "" CACHE FILEPATH "Path to libssl.a from BoringSSL")
set(ADB_BORINGSSL_CRYPTO_LIB "" CACHE FILEPATH "Path to libcrypto.a from BoringSSL")

if(NOT ADB_BORINGSSL_INCLUDE_DIR OR NOT ADB_BORINGSSL_SSL_LIB OR NOT ADB_BORINGSSL_CRYPTO_LIB)
    message(FATAL_ERROR "Set ADB_BORINGSSL_INCLUDE_DIR, ADB_BORINGSSL_SSL_LIB, and ADB_BORINGSSL_CRYPTO_LIB")
endif()

target_include_directories(adb_host_wireless PRIVATE ${ADB_BORINGSSL_INCLUDE_DIR})
target_link_libraries(adb_host_wireless PRIVATE ${ADB_BORINGSSL_SSL_LIB} ${ADB_BORINGSSL_CRYPTO_LIB})

target_include_directories(adb_crypto_utils PRIVATE ${ADB_BORINGSSL_INCLUDE_DIR})

set(ADB_EXTRA_LIBS "" CACHE STRING "Extra libs to link for adb_host_wireless (space-separated)")
if(ADB_EXTRA_LIBS)
    target_link_libraries(adb_host_wireless PRIVATE ${ADB_EXTRA_LIBS})
endif()

if(ADB_BUILD_CLI)
    add_executable(adb_wireless client/main.cpp)
    target_include_directories(adb_wireless
        PRIVATE
            ${ADB_ROOT}
            ${ADB_ROOT}/client
            ${ADB_ROOT}/crypto/include
            ${ADB_ROOT}/pairing_auth/include
            ${ADB_ROOT}/pairing_connection/include
            ${ADB_ROOT}/tls/include
            ${ADB_ROOT}/libs/adbconnection/include
            ${ADB_ROOT}/proto
            ${ADB_LIBBASE_ROOT}/include
            ${ADB_CRYPTO_UTILS_ROOT}/include
            ${Protobuf_INCLUDE_DIR}
            ${CMAKE_CURRENT_BINARY_DIR}
            ${ADB_BORINGSSL_INCLUDE_DIR}
            ${ADB_EXTRA_INCLUDE_DIRS}
    )
    target_link_libraries(adb_wireless PRIVATE adb_host_wireless)
    target_compile_definitions(adb_wireless PRIVATE ADB_HOST=1)
    if(ADB_WIRELESS_ONLY)
        target_compile_definitions(adb_wireless PRIVATE ADB_WIRELESS_ONLY=1)
    endif()
    if(ADB_DISABLE_MDNS)
        target_compile_definitions(adb_wireless PRIVATE ADB_DISABLE_MDNS=1)
    endif()
    if(ADB_DISABLE_COMPRESSION)
        target_compile_definitions(adb_wireless PRIVATE ADB_DISABLE_COMPRESSION=1)
    endif()
endif()
