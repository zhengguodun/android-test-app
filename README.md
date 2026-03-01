# Android Test App - SYNC 蓝牙热点自动共享

一个智能 Android 应用，当手机蓝牙连接到名为 "SYNC" 的设备时，自动开启 WiFi 热点共享。

## ✨ 功能特性

### 1. 蓝牙设备监控
- 🔍 自动扫描已配对的蓝牙设备
- 📡 实时监控蓝牙连接状态
- 🎯 专门监听名为 "SYNC" 的设备

### 2. 自动热点共享
- ✅ 连接到 SYNC 设备时自动开启 WiFi 热点
- ❌ 断开连接后自动关闭热点（延迟 5 秒）
- 🔄 智能状态管理

### 3. HarmonyOS 4.2.0 兼容
- 📱 支持华为/荣耀设备
- 🔧 适配 HarmonyOS 4.2.0（基于 Android 10/11）
- 🛠️ 华为专用 API fallback 处理

## 📋 权限说明

应用需要以下权限：

| 权限 | 用途 |
|------|------|
| BLUETOOTH / BLUETOOTH_ADMIN | 蓝牙基础功能 |
| BLUETOOTH_CONNECT / BLUETOOTH_SCAN | Android 12+ 蓝牙权限 |
| CHANGE_WIFI_STATE | 控制 WiFi 热点开关 |
| ACCESS_WIFI_STATE | 获取 WiFi 状态 |
| ACCESS_FINE_LOCATION | 蓝牙扫描需要（Android 要求） |

## 🚀 自动编译

每次 push 代码到 GitHub 后，会自动编译生成 APK。

### 下载 APK：

1. 进入 GitHub 仓库页面
2. 点击 **Actions** 标签
3. 选择最近的构建
4. 在 **Artifacts** 区域下载 `app-debug.zip`
5. 解压后得到 `app-debug.apk`

## 📱 安装到手机

1. 下载 APK 到手机
2. 允许"未知来源"安装
3. 点击安装
4. 打开应用，授予所有权限
5. 确保蓝牙已开启
6. 配对并连接名为 "SYNC" 的蓝牙设备
7. 热点将自动开启！

## ⚠️ 注意事项

### HarmonyOS 4.2.0 特殊说明

1. **权限授予**：首次启动时需要授予所有权限，特别是位置权限（蓝牙扫描必需）

2. **热点限制**：
   - 某些 HarmonyOS 版本可能限制第三方应用控制热点
   - 如果自动开启失败，请手动开启热点
   - 可能需要额外的系统权限

3. **电池优化**：建议将应用加入电池优化白名单，确保后台能持续监控蓝牙

4. **自启动管理**：在华为手机管家 → 应用启动管理 中允许应用自启动

## 🛠️ 本地开发

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
│   │   │   └── MainActivity.java      # 主逻辑：蓝牙监控 + 热点控制
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml  # UI 布局
│   │   │   └── values/
│   │   │       └── strings.xml        # 字符串资源
│   │   └── AndroidManifest.xml        # 权限配置
│   └── build.gradle
├── .github/workflows/
│   └── android-build.yml              # GitHub Actions 配置
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 🔧 自定义配置

### 修改目标设备名称

编辑 `MainActivity.java`：
```java
private static final String TARGET_DEVICE_NAME = "SYNC";  // 改为你想要的设备名
```

### 修改热点延迟

```java
// 连接后延迟开启（毫秒）
handler.postDelayed(() -> {
    enableHotspot(true);
}, 2000);  // 2 秒

// 断开后延迟关闭（毫秒）
handler.postDelayed(() -> {
    enableHotspot(false);
}, 5000);  // 5 秒
```

## 🐛 故障排除

### 热点无法自动开启

1. 检查是否授予了所有权限
2. 手动测试能否开启热点
3. 查看应用日志（`adb logcat -s MainActivity`）
4. 某些 ROM 限制第三方应用控制热点

### 蓝牙设备检测不到

1. 确保设备已配对
2. 确保蓝牙已开启
3. 检查位置权限是否授予
4. 重启应用重试

### HarmonyOS 特定问题

1. 在设置中允许应用所有权限
2. 关闭电池优化
3. 允许自启动和后台运行
4. 如仍有问题，查看华为开发者文档

## 📝 更新日志

### v2.0-sync-hotspot
- ✅ 新增蓝牙设备监控功能
- ✅ 自动开启/关闭 WiFi 热点
- ✅ 支持 HarmonyOS 4.2.0
- ✅ 华为设备专用 API 适配

### v1.0
- 🎉 初始版本

---

由 OpenClaw AI 助手创建 🤖
