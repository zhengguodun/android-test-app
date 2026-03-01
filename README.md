# Android Test App - 蓝牙热点自动共享

一个智能 Android 应用，当手机蓝牙连接到指定设备时，自动开启 WiFi 热点共享。**支持后台持续监控**。

## ✨ 功能特性

### 1. 后台持续监控 🔄
- ✅ **前台服务** - 24 小时持续监控蓝牙连接
- 🔔 **状态栏通知** - 实时显示监控状态
- 🛡️ **防杀进程** - 系统不会轻易关闭服务
- 🔄 **自动重启** - 服务意外停止后自动重启

### 2. 可配置参数 ⚙️
- 📝 **设备名称** - 自定义要监控的蓝牙设备名称（默认：SYNC）
- ⏱️ **关闭延迟** - 自定义断开后关闭热点的延迟时间（默认：60 秒）
- 💾 **持久保存** - 设置保存后永久生效

### 3. 自动热点共享 📶
- ✅ 连接到目标设备时自动开启 WiFi 热点
- ❌ 断开连接后延迟自动关闭热点
- 🔄 智能状态管理，避免频繁开关

### 4. HarmonyOS 兼容 📱
- ✅ 支持 HarmonyOS 4.2.0
- ✅ 支持 HarmonyOS 4.3.0
- 📱 支持华为/荣耀设备
- 🔧 多种热点开启策略 fallback

## 📋 权限说明

应用需要以下权限：

| 权限 | 用途 |
|------|------|
| BLUETOOTH / BLUETOOTH_ADMIN | 蓝牙基础功能 |
| BLUETOOTH_CONNECT / BLUETOOTH_SCAN | Android 12+ 蓝牙权限 |
| CHANGE_WIFI_STATE | 控制 WiFi 热点开关 |
| ACCESS_WIFI_STATE | 获取 WiFi 状态 |
| ACCESS_FINE_LOCATION | 蓝牙扫描需要（Android 要求） |
| FOREGROUND_SERVICE | 后台服务运行 |
| POST_NOTIFICATIONS | Android 13+ 通知权限 |

## 🚀 快速开始

### 下载 APK：

1. 进入 GitHub 仓库页面
2. 点击 **Actions** 标签
3. 选择最近的构建（#7 成功）
4. 在 **Artifacts** 区域下载 `app-debug.zip`
5. 解压后得到 `app-debug.apk`

### 安装与配置：

1. **安装 APK** 到手机
2. **打开应用**，授予所有权限
3. **设置设备名称**（例如：SYNC）
4. **设置关闭延迟**（例如：60 秒）
5. **点击"保存设置"**
6. **点击"启动后台监控服务"**
7. ✅ 通知栏显示"正在监控蓝牙设备..."

### 配对并连接：

1. 在蓝牙设置中**配对目标设备**
2. **连接设备**（名称与设置一致）
3. 🎉 应用自动开启 WiFi 热点！

## ⚠️ HarmonyOS 热点开启失败解决方案

### 问题原因

HarmonyOS 系统对第三方应用开启热点有严格限制，可能无法自动开启。

### 解决方法

#### 方法 1：首次手动开启（推荐）

1. 打开应用，启动监控服务
2. **手动开启一次热点**：
   ```
   设置 → 移动网络 → 个人热点 → 开启
   ```
3. 连接蓝牙设备
4. 之后应用应该可以自动控制

#### 方法 2：使用 ADB 授权（需要电脑）

```bash
# 连接手机到电脑，开启 USB 调试
adb shell pm grant com.example.testapp android.permission.WRITE_SECURE_SETTINGS
```

#### 方法 3：查看日志定位问题

1. 开启 USB 调试
2. 连接电脑运行：
   ```bash
   adb logcat -s BluetoothMonitorService
   ```
3. 连接蓝牙设备，查看日志输出
4. 日志会显示使用了哪种方法，以及失败原因

#### 方法 4：热点开启失败时的备选方案

如果自动开启始终失败，可以：

1. 保持应用在后台运行
2. 当通知栏显示"设备已连接 | ❌ 热点开启失败"时
3. **手动下拉通知栏，点击通知进入应用**
4. 手动开启热点
5. 断开时应用会自动关闭热点（这个通常可以成功）

### HarmonyOS 4.3.0 特殊说明

HarmonyOS 4.3.0 可能使用了新的 API 或增加了更多限制：

1. **必须授予所有权限**，特别是位置权限
2. **首次使用必须手动开启一次热点**
3. **建议关闭电池优化**
4. **锁定应用**防止被清理

## 🔧 本地开发

```bash
# 克隆仓库
git clone https://github.com/zhengguodun/android-test-app.git
cd android-test-app

# 用 Android Studio 打开
# 或者命令行编译
./gradlew assembleDebug

# APK 位置
app/build/outputs/apk/debug/app-debug.apk
```

## 📦 项目结构

```
android-test-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/testapp/
│   │   │   ├── MainActivity.java           # 主界面：设置 + 服务控制
│   │   │   └── BluetoothMonitorService.java # 后台监控服务
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml       # UI 布局
│   │   │   └── values/
│   │   │       └── strings.xml             # 字符串资源
│   │   └── AndroidManifest.xml             # 权限和服务配置
│   └── build.gradle
├── .github/workflows/
│   └── android-build.yml                   # GitHub Actions 配置
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 🐛 故障排除

### 服务无法启动

1. 检查是否授予了所有权限
2. Android 13+ 需要授予通知权限
3. 查看日志：`adb logcat -s BluetoothMonitorService`

### 热点无法自动开启（常见问题）

**原因：** HarmonyOS 系统限制第三方应用控制热点

**解决方案：**
1. ✅ **首次手动开启一次热点**（最重要！）
2. ✅ 检查是否授予了所有权限
3. ✅ 查看日志确认使用了哪种方法
4. ✅ 尝试 ADB 授权 WRITE_SECURE_SETTINGS
5. ⚠️ 如果仍然失败，使用手动开启备选方案

### 后台服务被杀死

**HarmonyOS 用户必须完成：**
1. ✅ 允许自启动
2. ✅ 关闭电池优化
3. ✅ 锁定应用（多任务界面）
4. ✅ 允许后台活动

### 连接设备后没有反应

1. 检查设备名称是否与设置一致（区分大小写）
2. 确保设备已配对并连接
3. 重启服务后重新连接设备
4. 查看日志确认连接事件

## 📝 更新日志

### v3.1-harmonyos-4.3-support (当前版本)
- ✅ 添加 HarmonyOS 4.3.0 兼容支持
- ✅ 新增多种热点开启策略 fallback
- ✅ 添加详细调试日志
- ✅ 优化热点开启失败处理
- ✅ 支持先关闭 WiFi 再开启热点

### v3.0-background-service
- ✅ 后台监控服务（前台服务）
- ✅ 自定义蓝牙设备名称
- ✅ 自定义热点关闭延迟
- ✅ 状态栏通知显示实时状态

### v2.0-sync-hotspot
- ✅ 蓝牙设备监控功能
- ✅ 自动开启/关闭 WiFi 热点

### v1.0
- 🎉 初始版本

## 💡 提示

- **电量消耗**：后台服务会略微增加耗电，建议充电时使用
- **通知栏**：通知无法完全隐藏（系统要求），但可以最小化
- **首次使用**：建议先测试短时间延迟（如 10 秒）确认功能正常
- **长期运行**：建议定期重启服务确保稳定性
- **热点失败**：HarmonyOS 用户首次必须手动开启一次热点

---

由 OpenClaw AI 助手创建 🤖
