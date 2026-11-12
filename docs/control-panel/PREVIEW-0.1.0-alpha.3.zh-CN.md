# 控制面板 0.1.0-alpha.3 功能体验版

版本标签：controlpanel-v0.1.0-alpha.3。日期：2026-11-12。个人 fork 预览版，不是 KDE 官方发行，不代表完整开发及验收完成。旧 alpha.1 / alpha.2 的标签和附件不覆盖。

## 本版包含什么

延续横屏 6 x 3 控制屏、最多十页、应用跟随、按钮及图标同步、背景/标签/音量栏设置，以及数字、指针、天气、信息、汽车五种空闲表盘。程序、文件、网站、快捷键、系统动作等入口需要配套桌面端；功能已实现不表示全部平台边界已经验收。

相对 alpha.2，本版修复窄屏及两倍字号下的表盘文字裁切，提高信息表盘次要文字和浅色离线提示对比度。配套 Windows 内部包另修复新配置目录首次自检的身份就绪竞态，以及 Windows PowerShell 5.1 模块加载失败。运行代码和检查范围详见[阶段二十二报告](https://github.com/xingxing7290/kdeconnect-android/blob/18af27712af623c222b76dcf94aa2d4cb50f53e7/docs/control-panel/MILESTONE-22-CONTRAST-STARTUP.zh-CN.md)。

## 下载和使用

- [Android APK、校验文件和版本资料](https://github.com/xingxing7290/kdeconnect-android/releases/tag/controlpanel-v0.1.0-alpha.3)。
- Android 文件：kdeconnect-controlpanel-0.1.0-alpha.3-android-debug.apk。最低 Android 6.0 / API 23，应用 ID 为 org.kde.kdeconnect_tp.debug，上游版本名仍为 1.37.0-debug。alpha.3 是个人 fork 的预览版本标签，不是 APK 内部版本名。
- 调试包使用仓库调试密钥，可与官方正式版并存；手工安装会更新同 ID、同签名的旧调试版。请自行决定是否安装，本次没有向实体手机安装。
- Windows 内部 ZIP 仅本地提供；解压整个目录后运行 Start-ControlPanel.cmd，不只复制 EXE。第三方发行声明及对应源码材料尚未完成，不公开上传 Windows 二进制。本版桌面源码提交暂留本地，GitHub 推送等待仓库要求的用户确认。
- 两端在可信网络完成 KDE 配对，Android 对应电脑页打开控制面板，Windows 在桌面控制中编辑。官方客户端没有新增面板插件，需使用本 fork 配套两端。

## 核验和限制

本版使用已验证的阶段二十二产物，仅补版本资料，不重编译、不改变运行逻辑。Android 45 套件 / 298 项通过，Windows 22/22 套通过；APK v1/v2 签名通过。Windows 新目录、重启、中文及空格配置路径三轮无窗口自检通过，身份就绪函数另有四项回归。

Lint 重新生成后仍有 493 错误、211 警告、1 提示，不算通过。普通 LAN、多设备长时、全部原生动作/焦点、真实音频写入/回滚、完整视觉/无障碍、原 KDE 全功能及干净 Windows 安装卸载仍待验收。本版不是生产发行，不用于要求可靠性保证的环境。本次新 APK 没有重新进行模拟器双端配对；历史联调证据不冒充当前 APK 的完整实机验收。

## 产物及来源

| 文件 | 字节数 | SHA-256 |
| --- | --- | --- |
| Android APK | 30872225 | 9C46A2FA4ADBBBFD40C85D6C8129300904DAC208D8D051E3DFF43EF1EAE3F064 |
| Windows 本地内部 ZIP | 181514179 | 6329A91B15AF0F42B0D30B4BE4051C7E520ACE31A5BAE77E6D70B8319761E777 |

Android 运行代码 f8df64ce1b61b756714f5bed877e8f1dff4f7a97；Windows 应用代码 440d1dd97decc8d130d23749f999acd4751844af，启动器代码 cf9c74401c59fbe46bb1d5e1c4326adebbf64385。之后只改文档。Android 同名标签保留源码；不上传配对身份、私钥、证书或本地测试 profile。随附 JSON 明确区分公开 APK 与不上传的 Windows 内部包。
