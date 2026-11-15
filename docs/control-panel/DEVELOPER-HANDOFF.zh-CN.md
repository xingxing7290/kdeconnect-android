# Android 换机开发与接续

更新：2026-11-15。两端总体入口、Windows 环境与协作规则见
[桌面开发接续指南](https://github.com/xingxing7290/kdeconnect-kde/blob/master/docs/control-panel/DEVELOPER-HANDOFF.zh-CN.md)。
需求、协议和历史验证均已在 docs/control-panel 中，不需复制原机目录才能阅读。

## 当前基线

- 日常开发克隆本仓库最新 master；本次只补文档，不改应用运行逻辑。
- 已公开签名版本：[controlpanel-release-v0.1.0-alpha.3](https://github.com/xingxing7290/kdeconnect-android/releases/tag/controlpanel-release-v0.1.0-alpha.3)，
  源码提交 b7c569ad500d601b636c7ccac03bcdad2ad97c43。
- 配套 Windows：[controlpanel-release-v0.1.0-alpha.4](https://github.com/xingxing7290/kdeconnect-kde/releases/tag/controlpanel-release-v0.1.0-alpha.4)。
- Android 最新功能证据：[阶段二十三](MILESTONE-23-PANEL-CONTRAST.zh-CN.md)。45 套件/304 项单元测试通过；
  Lint 仍有 493 错误，不因 Gradle 退出 0 就算通过。Release 在专用模拟器安装、启动通过，未安装实体手机。
- 当前 Release 的包名为 org.kde.kdeconnect_tp.controlpanel；调试包为 org.kde.kdeconnect_tp.debug。
  它们与官方版、彼此之间的配对数据不共享，换包后需重新配对。

## 新电脑构建

克隆两个个人 fork，按桌面指南建功能分支。使用 JDK 21、仓库自带 Gradle Wrapper
（当前 9.6.0）、Android SDK 37；当前 compileSdk/targetSdk=37、minSdk=23。
不要用系统的其他 Gradle 代替 Wrapper，不上传或复制旧 local.properties。

以下路径只是示例。在新专用 PowerShell 终端中改成新电脑实际值；不设置旧机的固定代理：

```powershell
$workRoot = 'E:/git'
$buildRoot = 'D:/KDEBuild'
$env:JAVA_HOME = 'D:/Java/jdk-21'
$env:ANDROID_HOME = 'D:/Android/SDK'
$env:PATH = "$env:JAVA_HOME/bin;" + $env:PATH
$env:GRADLE_USER_HOME = Join-Path $buildRoot 'gradle'
$env:ANDROID_USER_HOME = Join-Path $buildRoot 'android-home/.android'
$env:TEMP = Join-Path $buildRoot 'temp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Path $env:TEMP,$env:ANDROID_USER_HOME -Force | Out-Null
$taskJavaHome = Join-Path $buildRoot 'android-home'
$taskKotlinRun = Join-Path $buildRoot 'kotlin-daemon'
New-Item -ItemType Directory -Path $taskKotlinRun -Force | Out-Null
$env:JAVA_TOOL_OPTIONS = '--add-opens=java.base/jdk.internal.access=ALL-UNNAMED' +
  ' -Duser.home="' + $taskJavaHome + '" -Djava.io.tmpdir="' + $env:TEMP + '"' +
  ' -Dkotlin.daemon.custom.run.files.path.for.tests="' + $taskKotlinRun + '" -Duser.language=en -Duser.country=US'
Set-Location "$workRoot/kdeconnect-android"
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain --max-workers=4 `
  --project-cache-dir "$buildRoot/android-project-cache"
```

若终端已有遗留 ANDROID_SDK_HOME，先在该专用终端移除，避免与 ANDROID_USER_HOME 冲突。
首次下载 SDK/Gradle/依赖需要网络；代理由你按新机环境配置，不能照搬原机端口。
每个命令检查退出码；超过十分钟无明确进展应停止当前步骤并记录状态，而不是不断重试或清空缓存。
本次没有在新电脑实测这些命令，也没有重跑已有 304 项功能测试。

## 修改与测试入口

- 功能主目录：src/main/java/org/kde/kdeconnect/plugins/controlpanel/。
- UI：ControlPanelScreen.kt、PanelDialScreen.kt、PanelInformationDial.kt；其他模型/缓存文件在同目录。
- 本地测试：src/test/java/org/kde/kdeconnect/plugins/controlpanel/。
- 跨端字段与样例：[协议](PROTOCOL.zh-CN.md)、fixtures/config-v1.json。
- 优先阅读：[需求](REQUIREMENTS.zh-CN.md)、[架构](ARCHITECTURE.zh-CN.md)、[计划](PLAN.zh-CN.md)、[构建记录](BUILD_STATUS.zh-CN.md)。

先按目标功能开发并增加对应测试，完成后统一回归。不要改共享附件默认超时来解决局部图标问题；
已批准的 Payload 按次超时只供图标模块使用，其他插件保持原行为。
Robolectric 本地 UI 测试不等于真机 TalkBack、所有手势或原生动作通过。

## Release 与私密数据

[Release 构建入口](../../packaging/controlpanel/README.zh-CN.md)保留 R8/资源压缩和个人包名。
普通开发使用 Debug，不必获取正式发布密钥。原发布密钥、密码及 DPAPI 文件不在仓库，
Git clone 无法迁移它们；后续覆盖升级必须安全迁移原密钥和可恢复密码，并提升 versionCode。
不要直接复制 DPAPI 密码文件到另一台 Windows 就假设能解密，也不要把密码粘贴到 GitHub/聊天。

不提交 SDK/Gradle 缓存、build、local.properties、设备 profile 或配对私钥。
不要覆盖公开标签或旧附件。此轮仅向个人仓库提交开发资料，不发布新 APK，不安装实体手机。
