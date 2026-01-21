#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ADB_ROOT="$ROOT_DIR/adb"

NDK_HOME="${ANDROID_NDK_HOME:-${NDK_HOME:-}}"
if [[ -z "$NDK_HOME" ]]; then
  echo "ANDROID_NDK_HOME (or NDK_HOME) is not set." >&2
  echo "Set it to your NDK path, e.g. ANDROID_NDK_HOME=~/Android/Sdk/ndk/27.0.12077973" >&2
  exit 1
fi

TOOLCHAIN="$NDK_HOME/build/cmake/android.toolchain.cmake"
if [[ ! -f "$TOOLCHAIN" ]]; then
  echo "NDK toolchain not found at $TOOLCHAIN" >&2
  exit 1
fi

PROTOC_BIN="${PROTOC_BIN:-$(command -v protoc || true)}"
if [[ -z "$PROTOC_BIN" ]]; then
  echo "protoc not found in PATH. Install protobuf compiler (protoc)." >&2
  exit 1
fi

build_abi() {
  local abi="$1"
  local build_dir="$2"

  local bssl_dir="$build_dir/boringssl"
  local proto_src="$ADB_ROOT/third_party/protobuf-3.21.12"
  local proto_build="$build_dir/protobuf"

  cmake -S "$ADB_ROOT/third_party/boringssl" -B "$bssl_dir"     -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN"     -DANDROID_ABI="$abi"     -DANDROID_PLATFORM=android-30     -DBUILD_SHARED_LIBS=OFF     -DBUILD_TESTING=OFF     -DCMAKE_POSITION_INDEPENDENT_CODE=ON

  cmake --build "$bssl_dir" --target ssl crypto --clean-first

  cmake -S "$proto_src" -B "$proto_build"     -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN"     -DANDROID_ABI="$abi"     -DANDROID_PLATFORM=android-30     -Dprotobuf_BUILD_TESTS=OFF     -Dprotobuf_BUILD_CONFORMANCE=OFF     -Dprotobuf_BUILD_EXAMPLES=OFF     -Dprotobuf_BUILD_PROTOC_BINARIES=OFF     -Dprotobuf_BUILD_SHARED_LIBS=OFF     -Dprotobuf_WITH_ZLIB=OFF     -DCMAKE_POSITION_INDEPENDENT_CODE=ON

  cmake --build "$proto_build"

  cmake -S "$ADB_ROOT" -B "$build_dir"     -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN"     -DANDROID_ABI="$abi"     -DANDROID_PLATFORM=android-30     -DADB_WIRELESS_ONLY=ON     -DADB_DISABLE_MDNS=ON     -DADB_DISABLE_COMPRESSION=ON     -DADB_BORINGSSL_INCLUDE_DIR="$ADB_ROOT/third_party/boringssl/include"     -DADB_BORINGSSL_SSL_LIB="$bssl_dir/libssl.a"     -DADB_BORINGSSL_CRYPTO_LIB="$bssl_dir/libcrypto.a"     -DProtobuf_PROTOC_EXECUTABLE="$PROTOC_BIN"     -DProtobuf_INCLUDE_DIR="$proto_src/src"     -DProtobuf_LIBRARIES="$proto_build/libprotobuf.a"

  cmake --build "$build_dir" --target adb_host_wireless
}

build_abi "arm64-v8a" "$ADB_ROOT/build-android"
build_abi "x86_64" "$ADB_ROOT/build-android-x86_64"

echo "ADB Android libs built successfully."
