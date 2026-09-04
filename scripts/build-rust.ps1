param(
    [string]$AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$NdkVersion = "27.0.12077973"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$rustRoot = Join-Path $projectRoot "core-rust"
$toolchain = Join-Path $AndroidSdk "ndk\$NdkVersion\toolchains\llvm\prebuilt\windows-x86_64\bin"
$jniRoot = Join-Path $projectRoot "app\src\main\jniLibs"

$targets = @(
    @{ Rust = "aarch64-linux-android"; Abi = "arm64-v8a"; Linker = "aarch64-linux-android21-clang.cmd"; Environment = "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER" },
    @{ Rust = "armv7-linux-androideabi"; Abi = "armeabi-v7a"; Linker = "armv7a-linux-androideabi21-clang.cmd"; Environment = "CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER" },
    @{ Rust = "i686-linux-android"; Abi = "x86"; Linker = "i686-linux-android21-clang.cmd"; Environment = "CARGO_TARGET_I686_LINUX_ANDROID_LINKER" },
    @{ Rust = "x86_64-linux-android"; Abi = "x86_64"; Linker = "x86_64-linux-android21-clang.cmd"; Environment = "CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER" }
)

foreach ($target in $targets) {
    $linkerPath = Join-Path $toolchain $target.Linker
    if (-not (Test-Path -LiteralPath $linkerPath)) {
        throw "NDKリンカーが見つかりません: $linkerPath"
    }

    Set-Item -Path "Env:$($target.Environment)" -Value $linkerPath
    cargo build --manifest-path (Join-Path $rustRoot "Cargo.toml") --release --target $target.Rust
    if ($LASTEXITCODE -ne 0) {
        throw "Rustのビルドに失敗しました: $($target.Rust)"
    }

    $destination = Join-Path $jniRoot $target.Abi
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $rustRoot "target\$($target.Rust)\release\libessential_core.so") -Destination (Join-Path $destination "libessential_core.so") -Force
}

Write-Output "Rust JNIライブラリを4 ABI向けに生成しました。"
