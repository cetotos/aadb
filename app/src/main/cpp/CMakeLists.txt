cmake_minimum_required(VERSION 3.22)

project(adbhost_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED ADB_ROOT)
    message(FATAL_ERROR "ADB_ROOT is required")
endif()
if(NOT DEFINED ADB_BUILD_DIR_ARM64)
    message(FATAL_ERROR "ADB_BUILD_DIR_ARM64 is required")
endif()
if(NOT DEFINED ADB_BUILD_DIR_X86_64)
    message(FATAL_ERROR "ADB_BUILD_DIR_X86_64 is required")
endif()

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(ADB_BUILD_DIR ${ADB_BUILD_DIR_ARM64})
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(ADB_BUILD_DIR ${ADB_BUILD_DIR_X86_64})
else()
    message(FATAL_ERROR "Unsupported ANDROID_ABI: ${ANDROID_ABI}")
endif()

add_library(adbhost_jni SHARED
    adbhost_jni.cpp
    ${ADB_ROOT}/client/main.cpp
)

target_include_directories(adbhost_jni
    PRIVATE
        ${ADB_ROOT}
        ${ADB_ROOT}/client
        ${ADB_ROOT}/crypto/include
        ${ADB_ROOT}/pairing_auth/include
        ${ADB_ROOT}/pairing_connection/include
        ${ADB_ROOT}/tls/include
        ${ADB_ROOT}/libs/adbconnection/include
        ${ADB_ROOT}/proto
        ${ADB_ROOT}/third_party/libbase/include
        ${ADB_ROOT}/third_party/system_core/libcrypto_utils/include
        ${ADB_ROOT}/third_party/boringssl/include
        ${ADB_ROOT}/third_party/protobuf-3.21.12/src
        ${ADB_BUILD_DIR}
)

target_compile_definitions(adbhost_jni
    PRIVATE
        ADB_HOST=1
        ADB_WIRELESS_ONLY=1
        ADB_DISABLE_MDNS=1
        ADB_DISABLE_COMPRESSION=1
)

target_link_options(adbhost_jni
    PRIVATE
        -Wl,-z,max-page-size=16384
        -Wl,-z,common-page-size=16384
)

add_library(adb_host_wireless STATIC IMPORTED)
set_target_properties(adb_host_wireless PROPERTIES IMPORTED_LOCATION
    ${ADB_BUILD_DIR}/libadb_host_wireless.a)

add_library(adb_libbase STATIC IMPORTED)
set_target_properties(adb_libbase PROPERTIES IMPORTED_LOCATION
    ${ADB_BUILD_DIR}/libadb_libbase.a)

add_library(adb_crypto_utils STATIC IMPORTED)
set_target_properties(adb_crypto_utils PROPERTIES IMPORTED_LOCATION
    ${ADB_BUILD_DIR}/libadb_crypto_utils.a)

add_library(adb_protobuf STATIC IMPORTED)
set_target_properties(adb_protobuf PROPERTIES IMPORTED_LOCATION
    ${ADB_BUILD_DIR}/protobuf/libprotobuf.a)

add_library(adb_boringssl_ssl STATIC IMPORTED)
set_target_properties(adb_boringssl_ssl PROPERTIES IMPORTED_LOCATION
    ${ADB_BUILD_DIR}/boringssl/libssl.a)

add_library(adb_boringssl_crypto STATIC IMPORTED)
set_target_properties(adb_boringssl_crypto PROPERTIES IMPORTED_LOCATION
    ${ADB_BUILD_DIR}/boringssl/libcrypto.a)

find_library(log-lib log)
find_library(z-lib z)

set(ANDROID_LIBS ${log-lib} ${z-lib})

# Link order matters for static libs.
target_link_libraries(adbhost_jni
    PRIVATE
        adb_host_wireless
        adb_libbase
        adb_crypto_utils
        adb_protobuf
        adb_boringssl_ssl
        adb_boringssl_crypto
        ${ANDROID_LIBS}
)
