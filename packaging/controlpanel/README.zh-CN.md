# 个人 fork Release 构建

本目录仅处理个人控制面板发行打包，不改变官方包的功能配置。

运行 `gradlew assembleRelease --init-script packaging/controlpanel/personal-release.init.gradle`。
使用原有 release 构建类型，保留 R8 和资源压缩；独立包名为
`org.kde.kdeconnect_tp.controlpanel`，版本为 `1.37.0-controlpanel-0.1.0-alpha.3`，
最低 Android 6.0。它与官方版及 `.debug` 调试版共存，不继承它们的配对数据。

输出默认未签名；用 Android SDK apksigner 和个人发布密钥离线签名，再执行
`apksigner verify --verbose --print-certs` 和 zipalign 检查。密钥、密码、私钥、
本机配置和设备配对身份都不得提交到源码或 Release 附件。

本次个人密钥保存在 D:/KDEBuild/private-signing。密码通过 Windows 当前账户
DPAPI 加密保存，仅该账户可解密。应另行在可信离线位置备份密钥和密码；
只有加密密码文件而没有原 Windows 账户凭据不能保证在另一台机器恢复。
未来更新必须复用该密钥并提高 versionCode；丢失密钥将无法覆盖升级。

Release 指优化构建类型，不代表全部功能已经通过正式验收。现有 Lint 错误、
真机/多端联网及完整功能验收的限制仍见 docs/control-panel/BUILD_STATUS.zh-CN.md。
安装手机需要用户自行确认来源并允许该来源安装应用；本次不自动安装实体手机。

签名工具说明：https://developer.android.com/tools/apksigner
