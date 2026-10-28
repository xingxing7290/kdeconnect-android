# KDE Connect - Android app

## KDE 控制台开发分支

本 fork 正在开发与桌面端同步的横屏控制面板：固定 6 x 3 槽位、多页面、应用跟随、音量及空闲表盘。当前处于开发阶段，尚未完成交付或完整测试。

- [需求与参考分析](docs/control-panel/REQUIREMENTS.zh-CN.md)
- [技术方案](docs/control-panel/ARCHITECTURE.zh-CN.md)
- [开发计划与验收进度](docs/control-panel/PLAN.zh-CN.md)
- [控制面板协议](docs/control-panel/PROTOCOL.zh-CN.md)
- [构建与测试记录](docs/control-panel/BUILD_STATUS.zh-CN.md)
- [双端初版与真实联调记录](docs/control-panel/MILESTONE-02.zh-CN.md)
- [智能跟随阶段记录](docs/control-panel/MILESTONE-03.zh-CN.md)
- [Windows 智能跟随使用说明](docs/control-panel/FOLLOW.zh-CN.md)
- [配套桌面端](https://github.com/xingxing7290/kdeconnect-kde)

原 KDE Connect 功能与上游说明保留如下。

KDE Connect is a multi-platform app that allows your devices to communicate (eg: your phone and your computer).

## (Some) Features
- **Shared clipboard**: copy and paste between your phone and your computer (or any other device).
- **Notification sync**: Read and reply to your Android notifications from the desktop.
- **Share files and URLs** instantly from one device to another.
- **Multimedia remote control**: Use your phone as a remote for Linux media players.
- **Virtual touchpad**: Use your phone screen as your computer's touchpad and keyboard.

All this without wires, over the already existing Wi-Fi network, and using TLS encryption.

## About this app

This is a native Android port of the KDE Connect Qt app. You will find a more complete readme about KDE Connect [here](https://invent.kde.org/network/kdeconnect-kde/).

## How to install this app

You can install this app from the [Play Store](https://play.google.com/store/apps/details?id=org.kde.kdeconnect_tp) as well as [F-Droid](https://f-droid.org/repository/browse/?fdid=org.kde.kdeconnect_tp). Note you will also need to install the [desktop app](https://invent.kde.org/network/kdeconnect-kde) for it to work.

## Contributing

A lot of useful information, including how to get started working on KDE Connect and how to connect with the current developers, is on our [KDE Community Wiki page](https://community.kde.org/KDEConnect)

For bug reporting, please use [KDE's Bugzilla](https://bugs.kde.org). Please do not use the issue tracker in GitLab since we want to keep everything in one place.

To contribute patches, use [KDE Connect's Gitlab](https://invent.kde.org/network/kdeconnect-android/).
On Gitlab (as well as on our [old Phabricator](https://phabricator.kde.org/tag/kde_connect/)) you can find a task list with stuff to do and links to other relevant resources.
It is a good idea to also subscribe to the [KDE Connect mailing list](https://mail.kde.org/mailman/listinfo/kdeconnect).

Please know that all translations for all KDE apps are handled by the [localization team](https://l10n.kde.org/). If you would like to submit a translation, that should be done by working with the proper team for that language.

## License
[GNU GPL v2](https://www.gnu.org/licenses/gpl-2.0.html) and [GNU GPL v3](https://www.gnu.org/licenses/gpl-3.0.html)

If you are reading this from GitHub, you should know that this is just a mirror of the [KDE Project repo](https://invent.kde.org/network/kdeconnect-android/).
