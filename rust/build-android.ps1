# Build script for Rust core library targeting Android (Windows)
# Prerequisites:
#   1. Rust toolchain: rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
#   2. Android NDK installed
#   3. Set $env:NDK_HOME to NDK path

param(
    [string]$Arch = "all"
)

$ErrorActionPreference = "Stop"

if (-not $env:NDK_HOME) {
    Write-Error "NDK_HOME is not set. Example: `$env:NDK_HOME = `$env:ANDROID_HOME\ndk\26.1.10909125"
    exit 1
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AndroidApi = 26

$Targets = @{
    "aarch64-linux-android"   = "arm64-v8a"
    "armv7-linux-androideabi" = "armeabi-v7a"
    "x86_64-linux-android"    = "x86_64"
    "i686-linux-android"      = "x86"
}

$Prebuilt = "$env:NDK_HOME\toolchains\llvm\prebuilt\windows-x86_64\bin"
$OutputDir = Join-Path $ScriptDir "..\app\src\main\jniLibs"
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

foreach ($Target in $Targets.Keys) {
    if ($Arch -ne "all" -and $Target -ne $Arch) { continue }
    $Abi = $Targets[$Target]

    # Select the correct clang compiler
    $TargetClean = $Target -replace '-', '_' -replace '.', '_'
    switch ($Target) {
        "aarch64-linux-android"    { $ClangPrefix = "aarch64-linux-android" }
        "armv7-linux-androideabi"  { $ClangPrefix = "armv7a-linux-androideabi" }
        "x86_64-linux-android"     { $ClangPrefix = "x86_64-linux-android" }
        "i686-linux-android"       { $ClangPrefix = "i686-linux-android" }
    }

    $CC   = "$Prebuilt\$ClangPrefix$AndroidApi-clang.cmd"
    $CXX  = "$Prebuilt\$ClangPrefix$AndroidApi-clang++.cmd"
    $AR   = "$Prebuilt\llvm-ar.cmd"

    if (-not (Test-Path $CC))   { throw "Compiler not found at $CC" }
    if (-not (Test-Path $AR))   { throw "Archiver not found at $AR" }

    # CARGO_TARGET_<TRIPLE>_LINKER: tells cargo/rustc which linker to use.
    # CC_<TRIPLE> / CXX_<TRIPLE> / AR_<TRIPLE>: tells the `cc` crate (used by
    # build scripts like libsqlite3-sys) which C/C++ compiler and archiver to
    # use. Without these, `cc` searches PATH for `<triple>-clang` which does
    # not exist in the NDK (only the API-suffixed variant does).
    [Environment]::SetEnvironmentVariable("CARGO_TARGET_${TargetClean}_LINKER", $CC,   "Process")
    [Environment]::SetEnvironmentVariable("CC_${TargetClean}",                   $CC,   "Process")
    [Environment]::SetEnvironmentVariable("CXX_${TargetClean}",                  $CXX,  "Process")
    [Environment]::SetEnvironmentVariable("AR_${TargetClean}",                   $AR,   "Process")

    Write-Host "Building for $Target ($Abi)..." -ForegroundColor Cyan
    cargo build --target $Target --release --manifest-path "$ScriptDir\Cargo.toml"

    $SoFile = "$ScriptDir\target\$Target\release\libauth2fa_core.so"
    $Dest = "$OutputDir\$Abi"
    New-Item -ItemType Directory -Force -Path $Dest | Out-Null
    Copy-Item $SoFile -Destination "$Dest\" -Force
    Write-Host "  -> Copied to $Dest\libauth2fa_core.so" -ForegroundColor Green
}

Write-Host ""
Write-Host "All Android targets built successfully!" -ForegroundColor Green
Write-Host "Output: $OutputDir\"
