#!/bin/bash
# Build script for Rust core library targeting Android.
# Prerequisites:
#   - Rust toolchain with android targets: rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
#   - Android NDK installed (via Android Studio or standalone)
#   - Set NDK_HOME environment variable

set -e

if [ -z "$NDK_HOME" ]; then
    echo "Error: NDK_HOME is not set. Please set it to your Android NDK path."
    echo "Example: export NDK_HOME=\$ANDROID_HOME/ndk/26.1.10909125"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_API=26

# Detect host platform and pick the correct prebuilt directory.
# NDK toolchain layout: $NDK_HOME/toolchains/llvm/prebuilt/<HOST_TAG>/bin
HOST_OS="$(uname -s)"
case "$HOST_OS" in
    Linux*)  PREBUILT="linux-x86_64"; HOST_EXT="" ;;
    Darwin*)
        # NDK historically ships darwin-x86_64 even on Apple Silicon (runs via Rosetta).
        PREBUILT="darwin-x86_64"
        HOST_EXT=""
        ;;
    MINGW*|MSYS*|CYGWIN*)
        PREBUILT="windows-x86_64"
        HOST_EXT=".cmd"   # NDK ships .cmd wrappers on Windows
        ;;
    *)
        echo "Error: Unsupported host OS: $HOST_OS"
        exit 1
        ;;
esac

# Target triples and their ABIs
declare -A TARGETS=(
    ["aarch64-linux-android"]="arm64-v8a"
    ["armv7-linux-androideabi"]="armeabi-v7a"
    ["x86_64-linux-android"]="x86_64"
    ["i686-linux-android"]="x86"
)

OUTPUT_DIR="$SCRIPT_DIR/../app/src/main/jniLibs"
mkdir -p "$OUTPUT_DIR"

for target in "${!TARGETS[@]}"; do
    abi="${TARGETS[$target]}"
    case $target in
        aarch64-linux-android)
            CLANG_PREFIX="aarch64-linux-android"
            ;;
        armv7-linux-androideabi)
            CLANG_PREFIX="armv7a-linux-androideabi"
            ;;
        x86_64-linux-android)
            CLANG_PREFIX="x86_64-linux-android"
            ;;
        i686-linux-android)
            CLANG_PREFIX="i686-linux-android"
            ;;
    esac

    BIN_DIR="$NDK_HOME/toolchains/llvm/prebuilt/$PREBUILT/bin"
    CC="$BIN_DIR/${CLANG_PREFIX}${ANDROID_API}-clang${HOST_EXT}"
    CXX="$BIN_DIR/${CLANG_PREFIX}${ANDROID_API}-clang++${HOST_EXT}"
    AR="$BIN_DIR/llvm-ar${HOST_EXT}"

    if [ ! -f "$CC" ]; then
        echo "Error: Compiler not found at $CC" >&2
        exit 1
    fi
    if [ ! -f "$AR" ]; then
        echo "Error: Archiver not found at $AR" >&2
        exit 1
    fi

    # CARGO_TARGET_<TRIPLE>_LINKER: tells cargo/rustc which linker to use.
    # CC_<TRIPLE> / CXX_<TRIPLE> / AR_<TRIPLE>: tells the `cc` crate (used by
    # build scripts like libsqlite3-sys) which C/C++ compiler and archiver to
    # use. Without these, `cc` searches PATH for `<triple>-clang` which does
    # not exist in the NDK (only the API-suffixed variant does).
    export "CARGO_TARGET_${target//-/_}_LINKER=$CC"
    export "CC_${target//-/_}=$CC"
    export "CXX_${target//-/_}=$CXX"
    export "AR_${target//-/_}=$AR"

    echo "Building for $target ($abi)..."
    cargo build --target "$target" --release --manifest-path "$SCRIPT_DIR/Cargo.toml"

    # Copy the built .so file
    SO_FILE="$SCRIPT_DIR/target/$target/release/libauth2fa_core.so"
    DEST="$OUTPUT_DIR/$abi"
    mkdir -p "$DEST"
    cp "$SO_FILE" "$DEST/"
    echo "  -> Copied to $DEST/libauth2fa_core.so"
done

echo ""
echo "All Android targets built successfully!"
echo "Output: $OUTPUT_DIR/"
