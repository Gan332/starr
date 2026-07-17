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
            CC="$NDK_HOME/toolchains/llvm/prebuilt/$PREBUILT/bin/aarch64-linux-android${ANDROID_API}-clang${HOST_EXT}"
            ;;
        armv7-linux-androideabi)
            CC="$NDK_HOME/toolchains/llvm/prebuilt/$PREBUILT/bin/armv7a-linux-androideabi${ANDROID_API}-clang${HOST_EXT}"
            ;;
        x86_64-linux-android)
            CC="$NDK_HOME/toolchains/llvm/prebuilt/$PREBUILT/bin/x86_64-linux-android${ANDROID_API}-clang${HOST_EXT}"
            ;;
        i686-linux-android)
            CC="$NDK_HOME/toolchains/llvm/prebuilt/$PREBUILT/bin/i686-linux-android${ANDROID_API}-clang${HOST_EXT}"
            ;;
    esac

    if [ ! -x "$CC" ] && [ ! -f "$CC" ]; then
        echo "Error: Compiler not found at $CC" >&2
        exit 1
    fi

    export "CARGO_TARGET_${target//-/_}_LINKER=$CC"

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
