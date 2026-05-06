这是一份基于现有 `Usque` 项目源码和前面的讨论，为您整理的 Android 客户端详细设计文档。

---

# Usque Android 客户端详细设计文档

## 1. 项目简介
本项目旨在开发一个 Android 应用程序，封装现有的开源项目 `Usque`（基于 Go 语言的 Cloudflare WARP MASQUE 协议客户端）。该 App 将提供图形化用户界面，使用户能够在 Android 设备上轻松注册账户、管理配置，并以“本地代理（SOCKS5/HTTP）”或“全局 VPN”模式连接到 WARP 网络。

## 2. 总体架构设计
系统采用分层架构，主要分为：**UI 展现层**、**Android 服务层**、**JNI 桥接层 (Gomobile)** 和 **Go 核心业务层**。

* **UI 展现层 (Kotlin + Jetpack Compose/XML):** 负责用户交互，如点击连接、模式切换、查看日志和注册账户。
* **Android 服务层 (Kotlin/Java):** 包含 `Foreground Service`（用于后台保活代理）和 `VpnService`（用于接管全局网络流量）。
* **JNI 桥接层 (Gomobile):** 编译自 Go 源码的 `.aar` 库。这一层包含专门为 Android 编写的 Wrapper 函数，处理 Java 基本类型与 Go 复杂类型之间的转换。
* **Go 核心业务层 (Forked Usque):** 包含了 MASQUE 协议的实现、QUIC 连接管理、DNS 解析等原始项目的核心逻辑。

## 3. 核心模块设计

### 3.1 Go 移动端适配模块 (`mobile/` 包)
正如分析所述，必须 Fork 源码并新增一个 `mobile` 包。该包暴露出符合 Gomobile 规范的扁平化 API：
* `func RegisterAccount(model, locale string) (string, error)`：调用原始的 `api.Register` 和 `api.EnrollKey`，将返回的账号配置序列化为 JSON 字符串返回给 Android。
* `func StartProxy(configJson string, port int, mode string) error`：解析 JSON 配置，启动 HTTP 或 SOCKS5 服务。
* `func StartVPN(configJson string, tunFd int) error`：接收 Android `VpnService` 传递过来的文件描述符 (FD)，启动全局隧道。

### 3.2 账户与配置管理模块 (Android 侧)
* **配置解析与存储：** Usque 的原始配置包含私钥（`PrivateKey`）、API 令牌（`AccessToken`）以及 IPv4/IPv6 地址等敏感信息。在 Android 中，这些数据不应以明文文件存储，而应使用 `EncryptedSharedPreferences` 或 Android Keystore 系统进行安全加密存储。
* **设备更新：** 对应 `models.DeviceUpdate`，支持在 App 内重新生成密钥并 Enroll。

### 3.3 代理服务模块 (Proxy Service)
* 提供免 Root 的应用级代理。
* 利用 Android 的 `Service` 并在通知栏显示常驻通知（Foreground Notification）以防止被系统杀后台。
* 底层调用 Go 核心的 SOCKS5/HTTP 逻辑。

### 3.4 全局 VPN 服务模块 (VPN Service) - **核心难点**
* 继承 Android 的 `android.net.VpnService`。
* **参数配置：** 使用 `VpnService.Builder` 设置拦截的 IP 段（通常为 `0.0.0.0/0` 和 `::/0`），设置 MTU（需与 Go 层的配置一致，建议 1280），并添加 DNS 服务器（如作者在 Android 下硬编码的 Cloudflare DNS）。
* **FD 握手协议：** `Builder.establish()` 会返回一个 `ParcelFileDescriptor`。通过 `fd.detachFd()` 提取整型 FD 传递给 Go 层 `mobile.StartVPN(fd)`。
* **自定义 TunnelDevice：** 在 Go 层实现 `api.TunnelDevice` 接口。使用 `os.NewFile(uintptr(fd), "tun")` 将 FD 包装为 Go 的文件对象，并在其上实现 `ReadPacket` 和 `WritePacket` 方法。

## 4. 关键技术方案与解决路径

### 4.1 网络切换与重连处理 (Network Connectivity)
Android 设备的网络环境变化频繁（如 Wi-Fi 切换至 5G）。
* **问题：** QUIC 连接可能因为底层网络接口的改变而超时。
* **方案：** 在 Android 侧注册 `ConnectivityManager.NetworkCallback`。当检测到 `onAvailable` 或 `onLost` 时，通过 JNI 接口主动通知 Go 层中断当前连接，触发 Usque 内置的 `MaintainTunnel` 循环进行重连。

### 4.2 API 错误处理
Usque 包含预定义的 API 错误常量，例如公钥无效 (`InvalidPublicKey`)。Go 层的适配器应当将 `models.APIError` 解析并翻译为状态码返回给 Android，由 Android 弹出 Dialog 提示用户（例如：“密钥失效，是否重新 Enroll？”）。

### 4.3 性能优化
* **内存池管理：** 原始代码中已经实现了 `NetBuffer` sync.Pool 机制来降低内存分配开销。在 Android 这种内存受限的环境中，这尤其重要。
* **CGO 开销：** 网络数据包（Packet）的读写**绝不能**在 Java 和 Go 之间频繁跨越 JNI 边界。必须将 FD 传入 Go 侧，让数据包的读、写、加密、解密完全在 Go 的 Native 线程中闭环完成。

## 5. UI 与交互设计 (UI/UX)
1.  **启动与授权页：** 首次启动请求 VpnService 权限；如果没有配置，显示“一键注册/登录 ZeroTrust”界面。
2.  **主界面 (Main Activity)：**
    * 中心的大型状态指示器/连接按钮。
    * 当前分配的内部 IP (`IPv4` / `IPv6`) 显示。
    * 下方的模式选择器（Radio Group：SOCKS5 / 全局 VPN）。
3.  **设置页 (Settings)：**
    * 允许修改 SNI 绕过探测。
    * 自定义远端 Endpoint IP（对应 `endpoint_v4` / `endpoint_v6`）。
    * 设置强制 HTTP/2 模式降级开关。

## 6. 系统权限声明 (AndroidManifest.xml)
App 必须声明以下权限：
* `<uses-permission android:name="android.permission.INTERNET" />`：访问网络。
* `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`：启动前台服务保活。
* `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />`：Android 14+ 对 Vpn 相关的服务类型要求。
* 在 `<application>` 中声明 VPN 服务组件，并绑定 `android.permission.BIND_VPN_SERVICE` 权限。