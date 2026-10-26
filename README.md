# FloatingPet · Android 悬浮桌宠（源码起始版）

这是一个可在 Android Studio 中打开的 Kotlin 原生 Android 项目，带透明 PNG 分层示例角色。

## 当前实现
- 悬浮窗服务：在其他应用上层显示，支持拖动。
- 分层角色：`app/src/main/assets/mascot_head.png` 与 `mascot_body.png`。
- 待机呼吸动画、头部轻摇、触摸时头部倾斜、快速双击身体跳跃。
- 前台服务通知与关闭入口。
- 媒体通知监听服务骨架，以及可选的辅助功能事件监听骨架。

## 尚未完成/限制
- 当前媒体监听是接入骨架，不会直接分析任意播放器输出的音频波形，也未实现真正的鼓点检测。
- 当前键盘辅助功能服务只留了状态检测接线点；尚未把键盘出现事件连接到动画。它不读取输入文字。
- 尚未完成设置页、角色导入器、音频节拍算法、动画状态机、跨厂商保活测试。
- 此工作环境没有 Android SDK/Gradle 可执行环境，故未能在这里编译或签名 APK；不要把源码 ZIP 误认为已编译 APK。

## 构建
1. 安装 Android Studio（建议较新的稳定版）与 Android SDK Platform 35。
2. 用 Android Studio 打开本目录，等待 Gradle Sync。
3. 如需本地测试，选择 Build > Build APK(s)。
4. 调试 APK 通常会生成于 `app/build/outputs/apk/debug/app-debug.apk`。
5. 发布版本需要创建自己的签名密钥并通过 Build > Generate Signed Bundle / APK 签名。

## 首次运行权限
1. “显示在其他应用上层”：必须，用于悬浮窗。
2. 通知访问：可选，用于读取支持的播放器媒体会话信息；用户在系统设置中手动开启。
3. 辅助功能：可选，用于判断输入/键盘界面状态；当前仅为接线骨架，启用前请阅读系统提示。
4. 通知权限：Android 13+ 可能需要，用于显示前台服务通知。
5. 未请求麦克风权限。当前版本不会录音，也不能凭空分析其他应用正在播放的音频。

## 目录
- `app/src/main/java/...`：Kotlin 源码
- `app/src/main/assets/`：透明 PNG 分层示例角色
- `app/src/main/res/`：应用资源与辅助功能配置

## 仅用手机的云端构建（GitHub Actions）
项目已附 `.github/workflows/android.yml` 工作流。把解压后的项目文件上传到 GitHub 仓库的 `main` 分支后，进入仓库的 **Actions** 页面，选择 **Build FloatingPet APK**，点击 **Run workflow**。构建成功后打开该次运行，在 Artifacts 区域下载 `FloatingPet-debug-apk`，解压得到 `app-debug.apk`。云端构建仍可能暴露源码给你所选的托管平台；不要上传密钥、密码或隐私资料。

注意：这只会尝试编译当前原型源码；若构建报错，需要根据 Actions 日志修复后重新运行。构建成功也不代表未完成的音乐节拍与键盘动画已实现。
