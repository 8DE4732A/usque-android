# Usque Android

Cloudflare WARP MASQUE 协议的 Android 客户端，基于 [usque](https://github.com/Diniboy1123/usque) 构建。

支持 **VPN 模式**（全局流量）和 **SOCKS5 代理模式**（本地代理），同时支持个人账户和 ZeroTrust 团队账户。

## 下载

从 [Releases](../../releases) 页面下载最新 APK 安装。

## 功能

- 全局 VPN 模式（基于 Android VpnService）
- SOCKS5 本地代理（默认 `127.0.0.1:1080`）
- 自动注册 Cloudflare WARP 账户
- ZeroTrust 团队账户支持（WebView 内完成授权）
- 网络切换自动重连
- HTTP/2 与 HTTP/3（QUIC）可选
- 自定义 SNI、DNS、MTU、Endpoint

## 构建

### 前置依赖

- Android Studio（含 JDK 17）
- Go 1.25.5+
- Android NDK（通过 SDK Manager 安装）
- gomobile

```bash
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
gomobile init
```

### 步骤

```bash
git clone --recurse-submodules https://github.com/8DE4732A/usque-android.git
cd usque-android

# 构建 Go 原生库
bash scripts/build-aar.sh

# 构建 Debug APK
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

### Release 签名构建

```bash
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=/path/to/release.jks \
  -Pandroid.injected.signing.store.password=<store_password> \
  -Pandroid.injected.signing.key.alias=<key_alias> \
  -Pandroid.injected.signing.key.password=<key_password>
```

## 发布

推送 tag 后 GitHub Actions 自动构建并发布 Release APK：

```bash
git tag v1.0.0
git push origin v1.0.0
```

需要在仓库 **Settings → Secrets and variables → Actions** 配置以下 secret：

| Secret | 说明 |
|--------|------|
| `KEYSTORE_BASE64` | `base64 -i release.jks` 的输出 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key 密码 |

## 项目结构

```
usque-android/
├── app/                        Android 应用模块
│   └── src/main/java/.../
│       ├── data/               配置存储（EncryptedSharedPreferences）
│       ├── nativebridge/       Go JNI 桥接层
│       ├── service/            VpnService / SocksProxyService
│       └── ui/                 Compose UI（screens / viewmodel）
├── third_party/usque/          Go 核心库（git submodule）
│   └── mobile/                 gomobile 导出层
└── scripts/build-aar.sh        AAR 构建脚本
```

## 免责声明

本项目仅供学习和研究用途。使用前请确保符合当地法律法规及 Cloudflare 服务条款。
