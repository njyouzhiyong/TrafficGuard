# 流量管家（TrafficGuard）

一个按应用实时显示上行 / 下行流量，并能禁止指定应用联网、关闭应用的 Android App。界面参考了桌面端流量监控列表的深色样式：顶部显示当前被禁程序数、右侧「联网管理」、列表按「软件/进程名 · 上传速度 · 下载速度」三列排布，表头可点击排序。

- 包名：`com.liuliang.trafficguard`
- 最低系统：Android 8.0（API 26），编译 SDK 37，目标 SDK 35
- 语言：Kotlin + ViewBinding + AndroidX 传统 View（无网络请求、无第三方 SDK、无数据上传）

## 下载

预编译的 debug APK 放在本仓库的 [Releases](../../releases) 页面，不随源码一起进 git 历史。直接下载 `TrafficGuard-debug.apk` 安装即可。

> debug 签名仅供自用与测试。自行上架或长期分发请按 `## 构建` 一节生成自己的 release 签名。

## 功能

| 功能 | 说明 |
| --- | --- |
| 实时速率 | 每个应用的上传速度 / 下载速度（KB/s、MB/s），默认 2 秒刷新，可切 1 秒 / 5 秒 |
| 今日累计流量 | 点击应用行，在底部操作面板查看今日上传 / 下载 / 合计 |
| 排序与筛选 | 点击表头按上传、下载、名称排序；支持按名称/包名搜索、隐藏系统应用、仅看当前有流量的应用 |
| 禁止联网 | 单应用点击操作面板切换，或在「联网管理」页用开关批量控制 |
| 关闭应用 | 结束后台进程；跳转系统「强行停止」；已 Root 设备可直接 `am force-stop` |
| 状态提示 | 顶部显示已被禁网的程序数、防护状态、总上传/总下载速率、最后更新时间 |

## 首次使用

1. 安装 APK 后打开，若顶部出现橙色提示条，点「去授权」会打开授权帮助（含各品牌路径、ADB 命令和「打开应用信息」入口），按里面的说明在系统「使用情况访问权限 / Usage Access」列表中打开「流量管家」的开关，返回后 App 会自动重新检测。
   - **开关是灰的、点不动？** 这是 Android 13+ 对手动安装（旁加载）APK 的「受限设置」保护，Usage Access 属于被限制的能力。先到「设置 → 应用 → 流量管家 → 右上角 ⋮ → 允许受限设置」（可能需验证锁屏密码），再回列表打开开关。
   - 也可以完全绕开系统设置，在电脑终端执行（无需 Root，一次即可）：
     ```
     adb shell appops set com.liuliang.trafficguard GET_USAGE_STATS allow
     ```
     执行后点 App 内「重新检测」。验证是否生效：`adb shell appops get com.liuliang.trafficguard GET_USAGE_STATS`。
   - 若系统始终不给权限，App 会尝试无权限的兼容通道（`/proc/net/xt_qtaguid/stats`、`TrafficStats`），能读到的设备会显示「兼容模式」，此时累计流量以 `≈` 标注、为开机以来估算值；Android 9+ 多数设备读不到，属系统限制。
2. 点「开启防护」（或在应用行里直接点「禁止该应用联网」），系统会弹出一次 VPN 连接授权，点确定。这是本地 VPN，仅用于拦截流量。
3. 点击任意应用行 → 「禁止该应用联网」；或点右上「联网管理」批量开关。
4. 「关闭该应用」在操作面板里：优先用「结束后台进程」；需要彻底停止时点「强行停止（系统设置）」，在系统详情页手动点强行停止；Root 设备会额外出现「强行停止（Root）」按钮。

## 实现原理

**流量统计**：`NetworkStatsManager.querySummary()` 一次取出全部 UID 当天的累计 rx/tx 字节；两次采样做差再除以时间间隔，得到每个应用的实时上下行速率。多应用共享同一 UID 时总量只计一次；部分 ROM 上 `querySummary` 无数据会自动回退到逐 UID 的 `queryDetailsForUid`。这是无 Root、无抓包权限时 Android 官方支持的按应用流量统计方式，速率精度取决于系统统计的更新粒度，并非抓包级实时值。

**禁止联网**：`VpnService` 建立本地 VPN 后，把「允许联网」的应用全部 `addDisallowedApplication()` 让它们直连真实网络；只有被禁止的应用会把数据包送进 tun 接口，服务读取后直接丢弃、不写回、不转发，这些应用便无法收发数据。同时覆盖 IPv4/IPv6 路由（IPv6 建立失败会自动回退纯 IPv4）。全程不连外部服务器、不上传任何数据。禁止名单变化时会自动重建 VPN 规则。

**关闭应用**：Android 不允许普通第三方应用调用 `force-stop` 其它应用。本项目使用 `ActivityManager.killBackgroundProcesses()` 结束后台进程，或跳转到系统应用详情页由用户确认强行停止；检测到 Root 时额外提供 `su -c am force-stop <pkg>`。

## 权限

| 权限 | 用途 |
| --- | --- |
| `PACKAGE_USAGE_STATS` | 按应用读取流量（需用户在系统设置中手动开启） |
| `BIND_VPN_SERVICE` | 声明 VpnService，弹系统授权框 |
| `QUERY_ALL_PACKAGES` | Android 11+ 列出全部已安装应用 |
| `KILL_BACKGROUND_PROCESSES` | 结束其它应用的后台进程 |
| `READ_PHONE_STATE` | 可选，用于移动网络订阅标识；拒绝时仍统计 WiFi |
| `INTERNET` / `ACCESS_NETWORK_STATE` | VpnService 正常工作所需 |

> 上架 Google Play 时 `QUERY_ALL_PACKAGES` 需要额外声明用途；仅在本地/内部分发无需处理。

## 已知限制

- 同一时间系统只允许一个 VPN 连接。开启本应用防护会顶掉其它 VPN，反之亦然；被顶掉时防护开关会自动复位。
- 无 Root 时无法直接强制停止其它应用（系统限制），只能结束后台进程或引导到系统设置页。
- 速率来自系统统计差分，1~5 秒刷新；个别深度定制 ROM 可能不返回按 UID 的统计，此时数值会保持 0。
- 「禁止联网」对本应用自身无效（自身始终直连，避免自锁）。
- 当前版本只做「允许 / 禁止联网」，未做按应用限速。

## 构建

环境：JDK 17+（本机使用 Android Studio 自带 JBR 25）、Android SDK Platform 37、Build-Tools 36/37、Gradle 9.8.2（wrapper 已包含）。

```bash
./gradlew assembleDebug          # Windows 用 gradlew.bat
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

> 首次构建需要 `local.properties` 指向本机的 Android SDK（`sdk.dir=...`），该文件已被 git 忽略；用 Android Studio 打开工程会自动生成。

安装到已开启 USB 调试的手机：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开本目录，点 Run 运行。

## 验证记录

本机 Android 模拟器（320x640 窄屏）实测：

- `./gradlew assembleDebug` 构建成功，产出可安装的 debug APK。
- 安装启动无崩溃；主界面、搜索、筛选、排序、底部统计条显示正常；320dp 窄屏的列宽已做权重自适应，应用名不再被挤成省略号。
- 授予使用情况访问权限后，点击 Chrome 显示「今日上传 15.4 KB / 今日下载 112.3 KB / 合计 127.7 KB」，确认按 UID 的统计链路可用（模拟器空闲时实时速率为 0 属正常）。
- 在「联网管理」打开一个开关会弹出系统 VPN 连接请求；确认后系统日志出现 `Established by com.liuliang.trafficguard on tun0`，防护状态、开关、计数同步更新；点「关闭防护」后 VPN 正常断开。
- 操作面板改为可滚动，小屏下底部说明文字可完整查看。
- 用 `adb shell appops set com.liuliang.trafficguard GET_USAGE_STATS ignore` 复现「未授权」：橙色提示条出现且列表正常显示；「去授权」弹出可滚动帮助面板，ADB 命令与 4 个按钮完整可见；「打开使用情况访问列表」跳到系统**应用列表页**（不是置灰的直达详情页）；改用 `appops set ... allow` 授权后返回，提示条自动消失。
- 顺带修复：未授权时逐 UID 发起数百次无效统计调用导致列表空白、AlertDialog 帮助在小屏被按钮遮挡。

界面截图见 `screenshots/`（主界面、操作面板、联网管理、流量详情、授权帮助、系统列表页、授权后主界面）。

## 目录

```
app/src/main/
├── AndroidManifest.xml
├── java/com/liuliang/trafficguard/
│   ├── MainActivity.kt          主界面：实时列表、排序、筛选、总速率
│   ├── ManageActivity.kt        联网管理页：批量开关
│   ├── AppActionSheet.kt        应用操作面板（禁网 / 关闭应用 / 应用信息）
│   ├── TrafficRepository.kt     NetworkStatsManager 差分测速
│   ├── FirewallVpnService.kt    本地 VPN 拦截
│   ├── AppRepository.kt         应用信息 / 图标缓存
│   ├── AppControl.kt            结束进程 / Root 强停 / 应用详情
│   ├── VpnController.kt         VPN 授权与防护开关
│   ├── Prefs.kt / Format.kt     配置 / 格式化
│   └── TrafficAdapter.kt / ManageAdapter.kt
└── res/                         布局、配色、图标（深色主题）
```
