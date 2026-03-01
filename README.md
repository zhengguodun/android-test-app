# Android Test App

一个简单的 Android 测试应用，使用 GitHub Actions 自动编译。

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

## 🛠️ 本地开发

```bash
# 克隆仓库
git clone <your-repo-url>
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
│   │   │   └── MainActivity.java
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── .github/workflows/
│   └── android-build.yml
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

由 OpenClaw AI 助手创建 🤖
