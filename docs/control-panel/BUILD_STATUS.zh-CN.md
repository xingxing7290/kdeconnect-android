# 构建与测试记录

2026-11-16 天气日期边界只读复核：针对未来夏令时变化是否应逐日切换偏移，重新核对 [Open-Meteo 接口说明](https://open-meteo.com/en/docs)：其本次响应中的 `utc_offset_seconds` 在整份响应中固定，日边界也采用该固定偏移，包括跨夏令时变化；Unix 时间按该偏移还原接口日期。因此当前预报日期校验不应仅因真实时区稍后切换夏令时就改用设备时区或逐日偏移。本次未联网查询真实天气、未修改运行代码；已运行的固定偏移模拟测试不能代替真实跨夏令时集成验收。

2026-11-16 三种定位权限组合纳入完整回归：`testDebugUnitTest` 成功，51 套件、343 项、0 失败/错误/跳过，原始 XML 保存于 `D:/KDEBuild/controlpanel-e2e-current/reports/android-permission-three-full-29c0c1790f144bc08b7327fae2948e96`。此次只改测试并运行单元测试，未重新构建 APK 或运行 Lint；此前 13702 候选运行代码不因这次测试调整而变化。真实 GPS、双端和完整验收仍未完成。

2026-11-16 定位权限组合定向补充：将回调模拟为同时包含粗略与精确权限结果，并分别覆盖全部拒绝、仅粗略允许、仅精确允许；SDK 28/37 共 6 项通过，XML 位于 `D:/KDEBuild/controlpanel-e2e-current/reports/weather-three-permissions-85fa77e4e46442d0acacde0af31eb7a0`。仅测试代码变化，没有重跑完整套件，之前的 341 项完整结果是此前测试集合；模拟的精确权限组合不代表各 Android 版本都会在真实授权界面产生该组合，也不验证真实 GPS 或网络。

2026-11-16 新候选模拟器升级：独立 ADB 5038 指定 `emulator-5580`，核对 AVD 名为 `KDEPanelPublishedUpgradeE`，Wi-Fi 和移动数据均为关闭；升级前已安装 13701，其 APK SHA-256 为 `02FE8EE493828F9A33643F503CBE80EAF96DF33212FA94CE162F15B6C87A79F4`。安装本地签名 13702 成功，安装后版本、APK SHA-256 `669E2853ECA82002AEE7FF01872349171334C4F21AD1722F0174015FBC90424A` 和原通知权限保留均已核对；显式启动 MainActivity 后进程存活，并保存设备列表 UI 树与截图至 `E:/KDEConnectTestData/upgrade-13702-evidence-a46a179d0c2240588a5fb886387f1230`。测试结束关闭该模拟器；ADB 列表中另有离线 `emulator-5584`，未操作，因而未关闭共享 ADB 服务。此检查仅覆盖 13701 到 13702 的升级、通知权限与主界面启动，不验证配对数据、真实位置、控制面板或双端；没有安装实体手机。

2026-11-16 当前源码 Android Release 本地候选：在 E 盘独立目录 `E:/KDEConnectTestData/current-release-20261116-build` 离线构建优化版本，`assembleRelease` 成功（4 分 24 秒）。使用既有个人密钥签名后，候选为 `E:/KDEConnectTestData/kdeconnect-controlpanel-local-13702-20261116.apk`，包名 `org.kde.kdeconnect_tp.controlpanel`、versionCode 13702、版本名 `1.37.0-controlpanel-local-test`，SHA-256 `669E2853ECA82002AEE7FF01872349171334C4F21AD1722F0174015FBC90424A`。apksigner 验证 v1/v2/v3 签名成功，证书 SHA-256 为 `DFB388D6FA81CE65B8FBB0B2895C765DBCA4342A9003961E65E00B56E4E29058`，zipalign 4 字节检查通过；apksigner 同时对若干 META-INF 条目报告 v1 签名覆盖警告。该包包含本地权限回调修复，尚未安装或升级测试、未进行真实双端验证、未公开发布；当前 Lint 错误仍未解决，13702 是本地候选版本并非正式发行号。

2026-11-16 权限回调修复后重跑 `lintDebug`：491 Error、20 Fatal、214 Warning、1 Hint；按 issue ID 与消息比较此前天气日期修复后的报告，没有条目差异。错误级问题包括 490 MissingTranslation、20 ExtraTranslation 和 1 MissingLeanbackLauncher。报告保存在 `D:/KDEBuild/controlpanel-e2e-current/reports/lint-permission-current-506116eaf3424e5b9a55530f009718db`。项目设置 `abortOnError=false`，因此 Gradle 成功不表示 Lint 通过；未修改翻译或其他模块。

2026-11-16 定位权限回调补充回归：在 SDK 28 和 37 上分别模拟允许与拒绝两种权限回调，验证天气从关闭切换到开启后的本地缓存刷新；不访问真实定位或天气网络。完整 `testDebugUnitTest assembleDebug` 成功，51 套件、341 项，0 失败/错误/跳过，XML 保存于 `D:/KDEBuild/controlpanel-e2e-current/reports/android-permission-both-full-dadab36dcb31431db397c9a5b252b6d6`；Debug APK SHA-256 仍为 `CB0E6A4D254C83F3FFAC6A53C05F89D71C124CDF3E9953610C9B5644C4B9B458`。此前 Release 候选不包含权限修复，Lint 未重跑；真实定位、实际双端及发行验收尚未完成。

2026-11-16 自动定位权限回调刷新修复：天气初始关闭再开启时，原布尔状态已为 true，权限结果回调再次赋 true 不触发刷新，可能等待 15 分钟。改为每次回调递增刷新版本，不改变权限范围或其他插件。最终相同测试夹具对照旧逻辑在 SDK 28/37 均失败（`D:/KDEBuild/controlpanel-e2e-current/reports/weather-permission-confirmed-before-f135e379a73b48cbb2186c010d919508`），新逻辑定向通过（`reports/weather-permission-passing-ac082cea814240059c708960c4db28d5`）；中间一次修复后测试因夹具未等待重组失败保留在 `reports/weather-permission-retry-fbe7d43f13f54d2594795c110d3b2831`。完整 `testDebugUnitTest assembleDebug` 为 51 套件、339 项、0 失败/错误/跳过，XML 位于 `reports/android-weather-permission-full-026814e0edd94fa190f2d02e3aa2e516`；新 Debug APK SHA-256 `CB0E6A4D254C83F3FFAC6A53C05F89D71C124CDF3E9953610C9B5644C4B9B458`。测试模拟权限拒绝回调及本地缓存，不等于真实授权/GPS 或双端验证；之前的 Release 候选和其模拟器升级不包含本修复，Lint 尚未重跑。

2026-11-16 已公开版本到本地候选的升级补充：D 盘空间不足后，在 E 盘 `E:/KDEConnectTestData/published-upgrade-20261116` 新建独立 AVD `KDEPanelPublishedUpgradeE`，构建/日志仍用 D 盘。只读查询 GitHub Release API，确认公开 alpha.3 APK 摘要与本地原包一致（`77BC5B59EA7D8ABF1ABF0DBB50C69B23B1459D93A334066F5347CCB48FB7F0E2`）。核对独立 ADB 5038、模拟器身份及目标包未安装，关闭 Wi-Fi/移动数据后安装原包 13700；设置 Dark 和模拟器通知权限，再以同签名本地 13701 候选升级，安装成功，哈希 `02FE8EE493828F9A33643F503CBE80EAF96DF33212FA94CE162F15B6C87A79F4` 一致，通知权限及 Dark 主题保留，主界面进程存活。升级前后 UI 树分别为 `ui-49fbfc4cc05f419889f53f8f73b12c54.xml` 和 `ui-ecd45592ef1a43019052370b9920d7ec.xml`，位于 `D:/KDEBuild/controlpanel-e2e-current/published-upgrade-e-be23c3b4943842a09de25618ae5eec67/ui`。此验证覆盖公开包到候选包的安装、授权及全局主题保留，不含已配对设备、私有配置迁移或真实双端；模拟器和独立 ADB 已关闭，E 盘测试数据保留，未操作实体手机。

2026-11-16 从已公开 alpha.3 升级的专用模拟器准备未完成：原 `avdmanager.bat` 因找不到 findstr/JDK 检查失败，直接调用已核对的 JDK 21 和 SDK 工具创建独立 AVD 后，冷启动连续报告 D 盘约 3187 MiB 可用、创建数据分区需要约 7373 MiB；指定较小 `-partition-size` 和调整该新 AVD 的测试配置后仍被同一检查拒绝。失败日志分别在 `D:/KDEBuild/controlpanel-e2e-current/published-upgrade-smoke-eb095281eae645228faef1425ab36567`、`published-upgrade-retry-7fd6ac8171d54b839dcc9109725895e1`、`published-upgrade-small-75574a5cc10842c5a74aa794a78d65d3`。失败的测试目录及不完整 AVD 保留，未删除用户文件；模拟器未启动、独立 ADB 已关闭，未安装公开版，不能声称公开版升级通过。需要在 D 盘额外腾出约 5 GiB 后再以新目录重试，或由用户指定有足够空间的其他盘。

2026-11-16 本地 Release 升级验证：个人 fork 构建入口增加可选 versionCode/标签；无参数保留原默认值，显式 code 需大于项目基础值；code 13700 的负向配置检查被拒。使用 13701、`local-test` 独立构建并以同一个人密钥签名，证书与旧包一致，APK SHA-256 `02FE8EE493828F9A33643F503CBE80EAF96DF33212FA94CE162F15B6C87A79F4`。在专用 `KDEPanelCurrentE2E4`、独立 ADB 5038 中先核对已安装包哈希和 13700，设置 Dark 主题，再安装升级；安装后为 13701、APK 哈希一致、通知权限和 Dark 主题仍在，主界面进程存活。截图与 UI 树在 `D:/KDEBuild/controlpanel-e2e-current/release-upgrade-smoke-be3daeb7eee84d8caeff60884a3ae157/ui`。旧的 13700 测试包本身也是本轮当前代码，不是已公开 alpha.3 原始 APK，因此此检查仅证明本地候选之间的升级及上述设置保留，不证明从公开版跨版本升级或配对数据迁移。专用模拟器及独立 ADB 已关闭，未操作实体手机；候选未发布，正式版本号仍需确认并高于实际已公开版本。

2026-11-16 最新 Release 本地签名与模拟器启动：用既有个人 fork 密钥对独立未签名候选签名，APK v1/v2/v3 验证通过（工具仍报告部分 META-INF 项的 v1 警告），证书 SHA-256 为 `DFB388D6FA81CE65B8FBB0B2895C765DBCA4342A9003961E65E00B56E4E29058`；本地签名 APK 和模拟器安装文件 SHA-256 均为 `9635FAA13177F7A25813CCB81D76FBF446927FB3697155DE05FF656A579EBE07`。核对独立 ADB 5038、`emulator-5580` 的 `KDEPanelCurrentE2E4` 身份及目标包未安装后，关闭该模拟器 Wi-Fi/移动数据，不使用 `-r` 安装；主界面进程启动、设备列表显示 No devices。首次尝试用 UI 辅助脚本选择系统权限弹窗被其应用包过滤保护拒绝，未输入；随后仅在该模拟器为候选包授予通知权限并重启，截图和 UI 树位于 `D:/KDEBuild/controlpanel-e2e-current/signed-release-smoke-aa9c1998390d48299530217ff2a4fef3/ui`。模拟器与独立 ADB 已关闭，未操作实体手机；测试 Release 包保留在专用 AVD 中，网络开关已关闭。此包仍为旧 versionCode/版本名的本地候选，不覆盖公开版，不证明配对、控制面板、双端或发布验收通过。

2026-11-16 当前源码 Release 优化构建：离线运行现有个人 fork Release 配置并保留 R8/资源压缩，首次把构建目录直接指向 D 盘时，KSP 因源码与生成目录跨盘符失败；改为 E 盘临时目录联接到 D 盘实际目录后构建成功（4 分 52 秒）。临时联接已移除，D 盘产物保留。未签名 APK 位于 `D:/KDEBuild/controlpanel-e2e-current/release-candidate-20261116/build-v2/outputs/apk/release/kdeconnect-android-release-unsigned.apk`，SHA-256 `96FF53432C1383B14F02DC85BFD5D052CD09DA05B56B8F3F43D286278AA98DB4`，zipalign 4 字节检查通过。它仍带现有 `versionCode=13700` 和 alpha.3 版本名，不能作为新版本发布或直接安装；未使用发布密钥、未覆盖公开附件、未进行安装或双端验证。资源编译仍报告其他语言格式及缺默认值的历史警告，Lint 511 项错误级问题不因此通过。

2026-11-16 本地天气日期测试补充：以模拟响应覆盖 UTC-12、UTC-5、UTC、UTC+8 和 UTC+14，检查正常连续日期被接受、重复第二天被拒；SDK 28/37 的 `PanelLocalWeatherCacheTest` 定向 6 项通过，XML 在 `D:/KDEBuild/controlpanel-e2e-current/reports/weather-offsets-0bac4df6551640a79b653548a18d03d4`。仅扩展测试；此前全量 337 项不包含本次扩充的偏移场景，也不代表真实天气服务或夏令时切换已经实测。

2026-11-16 天气日期修复后重新执行 `lintDebug`：报告仍为 491 Error、20 Fatal、214 Warning、1 Hint；错误仍为 490 MissingTranslation、20 ExtraTranslation 和 1 MissingLeanbackLauncher。按 issue ID 与消息对照此前 `lint-post-compact`，未发现条目变化；新 XML/HTML/SARIF 等保存在 `D:/KDEBuild/controlpanel-e2e-current/reports/lint-weather-date-75d4b754bd8947cb9048ba5cb1ecca05`。Gradle 因 `abortOnError=false` 返回成功不代表 Lint 通过，相关问题尚未解决。

2026-11-16 手机本地天气日期修复：以前仅验证服务返回的第一天日期，重复的第二天可能被错误标为下一天；现在逐项验证服务日期，异常时保留有效当前天气但不显示该三日预报。SDK 28/37 的模拟响应测试先复现失败，记录在 `D:/KDEBuild/controlpanel-e2e-current/reports/weather-dates-first-23c41655fee242b09661d2ac10b3528f`；修复后定向测试通过，全量 `testDebugUnitTest assembleDebug` 为 50 套件、337 项、0 失败/错误/跳过，XML 保存于 `reports/android-weather-dates-full-3488ab6a03a84ce08289d5397e3af0f6`。新 Debug APK SHA-256 为 `7A39A1E65AA3A79FF416D3CEDB174A67F7D091B066B574911C791D8469ECE1D9`，尚未安装模拟器或实体手机。最近 Lint 报告早于此运行代码修改，未重新执行；真实天气、跨时区和双端验收仍未完成。

2026-11-16 Lint 存量分类（读取此前 `reports/lint-post-compact/lint-results-debug.xml`，未重新执行 Lint）：511 条错误级问题中，490 条 MissingTranslation 按资源项计，其中 75 条定位到控制面板资源或名称、415 条为其他资源；另外有 20 条 ExtraTranslation 和 1 条 MissingLeanbackLauncher。控制面板英文默认及简体中文资源存在，但报告显示还缺多种项目支持的语言；这些数字是问题条数，不是待翻译语言或具体翻译数量。未将默认英文复制成其他语言、未关闭检查，也未修改其他插件；多语言质量校对和全项目 Lint 清理仍待完成。

2026-11-16 新增进度界面与图标附件测试后的全量复跑：离线执行 `testDebugUnitTest assembleDebug`，以 D 盘临时 init 脚本设置本次测试 JVM 上限 2 GB；50 套件、335 项、0 失败、0 错误、0 跳过。原始 XML 保存在 `D:/KDEBuild/controlpanel-e2e-current/reports/android-final-local-f33da8ebae4a49268a0f8b58fe04d550`。Debug APK 未重新生成；本次未重跑 Lint、实际双端和发行验收。

2026-11-16 图标接收真实回环套接字超时测试：构造只连接但不发送附件的本机 TCP 服务，检查读取超时后连接关闭、触发重试且面板会话保持 READY；`PanelAssetTransfersTest` 定向 6 项通过，XML 在 `D:/KDEBuild/controlpanel-e2e-current/reports/android-asset-socket-4f2c0089165c4602a9834fa7414b8ffb`。这是本机套接字与模拟会话，不是 TLS 真实双端联调；未重新全量运行。

2026-11-16 图标附件成功路径补充：生成 256x256 PNG，通过实际 `PanelAssetTransfers` 异步读取、摘要校验及 `PanelAssetCache` 落盘和解码；定向 5 项通过。首次默认 Robolectric 图形模式测试未写入缓存，失败 XML 在 `D:/KDEBuild/controlpanel-e2e-current/reports/android-asset-first-3efaa2b47f5c42baab3e209f0104691c`；改用已有缓存测试所用的 SDK 28 原生图形模式后通过，最终 XML 在 `reports/android-asset-final-432e530667794ddab7e4cfe449a350c8`。此轮只新增测试，尚未重新全量运行或验证真实网络附件传输。

2026-11-16 进度会话到界面的本地联动补充：在 SDK 28/37 Robolectric Compose 中，通过实际 `PanelSession` 接收模拟线上格式进度，检查旧任务、配置修订后的 waiting、无百分比的新任务及断线提示。`PanelInfoProgressUiTest` 定向 8 项通过，XML 保存在 `D:/KDEBuild/controlpanel-e2e-current/reports/android-progress-session-ui-f8fdcb8b5ae4440bb650d30240b9c285`。仅新增测试；以下 331 项全量结果早于此用例，不代表当前新增用例已纳入全量复跑，也不等同真实双端网络测试。

2026-11-16 进度配置切换测试加入后的全量复跑：使用 D 盘临时 init 脚本将本次测试 JVM 堆上限设置为 2 GB，离线执行 `testDebugUnitTest assembleDebug` 成功；50 套件、331 项、0 失败、0 错误、0 跳过。完整 XML 另存于 `D:/KDEBuild/controlpanel-e2e-current/reports/android-progress-full-32a25563a12a4dbc8fee4d9b500ef88e`。仅新增测试，Debug APK 任务为 UP-TO-DATE，SHA-256 仍为 `5ACB3C2F8A5E94EE9CDC4254C1FEB216C4F4B39D1B8CA663C0676F852E104104`；本次未重跑 Lint 或双端联调。

2026-11-16 进度配置切换定向测试：模拟当前修订、更新配置、接收新序号的 unavailable/waiting 和后续进度，检查拒绝旧修订及旧序号；`PanelSessionTest` 14 项通过。首轮夹具使用 Android 序列化输出的显式 null percent，与桌面实际省略该字段不同，导致 waiting 被拒；改成桌面线上格式后通过，没有修改 Android 运行代码。首次 Gradle 配置还因继承 `ANDROID_SDK_HOME` 失败，专用进程中移除该变量后完成测试。此轮只运行定向测试，之前 330 项全量报告不包含新用例；真实双端仍未验证。

2026-11-16 当前 Debug 包模拟器启动复核：使用独立 ADB 5038 核对 `emulator-5580` 的 AVD 名称 `KDEPanelCurrentE2E4` 和模拟器属性后安装；APK 本地及安装后的 SHA-256 均为 `5ACB3C2F8A5E94EE9CDC4254C1FEB216C4F4B39D1B8CA663C0676F852E104104`。禁用该专用模拟器 Wi-Fi 后启动主界面，进程存活，界面显示设备列表和 No devices；截图与 UI 树在 `D:/KDEBuild/controlpanel-e2e-current/startup-c702b2403eab45d4b143929dfbd731b8/ui`。结束后关闭该模拟器及独立 ADB 服务；未操作实体手机。该检查未配对、未进入控制面板，也不能证明实际双端或完整视觉验收通过；该 AVD 的 Wi-Fi 已关闭，后续联调需在确认隔离后重新启用。

2026-11-16 紧凑表盘大字体测试加入后全量复跑：继续使用 D 盘临时 init 脚本设置测试 JVM 2 GB 堆，`testDebugUnitTest` 和 `assembleDebug` 成功，50 套件、330 项、0 失败、0 跳过；APK 未因纯测试修改重新生成。完整 XML 保存在 `D:/KDEBuild/controlpanel-e2e-current/reports/android-full-compact-font`。首次默认堆内存不足的历史失败记录仍保留，实际双端、模拟器新包和完整无障碍验收未完成。

2026-11-16 紧凑表盘字体补充：在 SDK 28/37、640dp 宽、180dp 高、实际 2 倍字体和长阶段文本下，读取 Compose 文本布局，验证离线提示在省略号之前且进度行高度未裁切。相关 `PanelInfoProgressUiTest` 定向 6/6 通过，报告保存在 `D:/KDEBuild/controlpanel-e2e-current/reports/android-compact-font`。此轮只增加测试，完整套件 328 项是新增用例之前的结果；真实模拟器画面及其他语言仍需验证。

2026-11-16 紧凑表盘修改后重新运行 `lintDebug`，报告仍为 511 errors、214 warnings、1 hint（490 MissingTranslation、20 ExtraTranslation、1 MissingLeanbackLauncher）。新报告与修改前报告分别保存于 `D:/KDEBuild/controlpanel-e2e-current/reports/lint-post-compact` 和 `lint-pre-compact`。由于项目关闭了 `abortOnError`，任务成功不代表 Lint 通过；未修改其他插件或全语言翻译。

2026-11-16 紧凑表盘修复后全量回归：首次运行 328 项中 `SystemVolumePluginTest` 有 3 项因 Java heap space 失败，报告保存在 `D:/KDEBuild/controlpanel-e2e-current/reports/android-compact-full-first`。未修改音量代码；关闭专用模拟器后使用本机临时 init 脚本 `D:/KDEBuild/controlpanel-e2e-current/android-test-memory.gradle`，仅将本次测试 JVM 堆上限设为 2 GB，不更改仓库配置或 APK 逻辑。重跑 50 套件、328 项、0 失败、0 跳过，Debug 构建成功，报告保存在 `reports/android-compact-full-retry`。新 APK SHA-256 为 `5ACB3C2F8A5E94EE9CDC4254C1FEB216C4F4B39D1B8CA663C0676F852E104104`，尚未安装到模拟器；Lint 报告早于紧凑布局修改，仍需重新生成。不能将首次失败归因于系统物理内存或声称未发生失败。

2026-11-16 紧凑信息表盘修复：低高度布局此前忽略 `showForecast=false`，并把离线进度统一标为过期、未收到进度时不显示等待。现按配置隐藏预报，区分等待、离线和过期，并优先显示状态。SDK 28/37 的普通及紧凑 Compose 定向测试共 4 项通过；首次紧凑断言因未启用子串匹配失败，报告保存在 `D:/KDEBuild/controlpanel-e2e-current/reports/android-compact-first`，最终报告在 `android-compact-final`。修改后完整套件、Lint、APK 和模拟器尚未重新验证；此前 326 项及已安装 APK 不包含此修复。

2026-11-16 信息表盘手机 UI 测试加入后的完整复测：`testDebugUnitTest` 与 `assembleDebug` 成功，50 套件、326 项、0 失败、0 跳过；XML 保存于 `D:/KDEBuild/controlpanel-e2e-current/reports/android-full-info-ui`。本轮仅新增测试，APK 任务为 UP-TO-DATE。先前 324 项是历史结果；实际双端、真实无障碍和整体验收仍未完成。

2026-11-16 信息表盘手机 UI 补充：Robolectric Compose 在 SDK 28/37 验证两条备注、隐藏预报、进度百分比缺省、过期、离线和不可用状态，定向 2/2 通过。首轮两项失败及诊断重试是测试按不带项目符号的完整文本匹配造成节点未找到；改为包含实际符号的文本后检查可见性通过，未据此放宽可见性断言。报告分别在 `D:/KDEBuild/controlpanel-e2e-current/reports/android-info-ui-first`、`android-info-ui-second`、`android-info-ui-final`。仅本地 Compose 测试，不等于模拟器配对后的实际画面或双端链路通过；新测试加入后完整套件尚未重跑，之前 324 项结果仍保存在独立目录。

2026-11-16 后续完整复测：天气响应异常保护和本地模拟响应测试加入后，`testDebugUnitTest` 与 `assembleDebug` 成功；49 套件、324 项、0 失败、0 跳过。完整 XML 已另存 `D:/KDEBuild/controlpanel-e2e-current/reports/android-full-20261116`，定向报告另存 `reports/weather-targeted`。本次源码重跑 Lint 仍为 511 errors、214 warnings、1 hint，错误组成不变；新旧报告分别保存于 `reports/lint-current` 和 `reports/lint-previous`。下面的 322 项是前一轮历史结果。

随后仅向已核对的 `KDEPanelCurrentE2E4` 模拟器更新当前 Debug APK；本地和安装包 SHA-256 均为 `5B298826BC84A46F15CCC3E481AA74BF10C6E5295C18E81A40DB1B40C396902D`。重启后进程存活，深色空设备列表截图 `screen-c174f234bddc4482b2dcd6e9eb13c971.png`。未配对或执行控制面板，不能以此视为双端联调通过。

2026-11-16 拉取后本地验证（基线 `b36947d79`，包含尚未提交的工作区修改）：

- 修正新代码中的绘图扩展导入、布局参数、JSON 导入和主题方法签名冲突。首次两次构建因上述编译问题失败，修正后完整重跑。
- 控制面板天气缓存按请求城市隔离，指定城市失败时不切换为当前位置；仅自动定位请求定位权限，并明确处理权限撤销。WorkBuddy 进度按连接会话重置序号比较，离线显示仍保留最后快照。
- 最终 `testDebugUnitTest` 和 `assembleDebug` 通过：49 套件、322 项、0 失败、0 跳过。该结果包含上述权限处理修改；Debug 包未在实体手机安装。
- `lintDebug` 最终报告为 511 errors、214 warnings、1 hint：490 MissingTranslation、20 ExtraTranslation（Fatal）、1 MissingLeanbackLauncher。定位权限问题已消除；任务返回成功是因为 `abortOnError = false`，不是 Lint 通过。报告在本机构建目录 `build/reports/lint-results-debug.html`。
- 未做最新源码的实际双端联调、普通 LAN、多设备长时、真机、完整视觉/辅助技术、最终 Release 安装验证。已公开 alpha.3 和桌面 alpha.4 不含本轮新修改，不应作为当前源码的安装包。
- 后续双端联调准备：旧天气脚本固定使用此前桌面包和证据目录，不能直接代表本轮源码。2026-11-16 核查独立 ADB 5038 时没有设备连接；检查后关闭了本次新启动的 5038 服务，未操作实体手机。应先为当前版本建立新的隔离证据目录、确认模拟器身份及实际加载的桌面插件，再运行联调，不能覆盖历史证据。
- 后续新建独立 Android 34 模拟器 `KDEPanelCurrentE2E4`，使用独立 ADB 5038，核对模拟器属性和名称后安装当前 Debug APK 并启动；允许模拟器通知权限后，设备列表可见且为空。证据在 `D:/KDEBuild/controlpanel-e2e-current/ui`；这只是启动及空设备列表检查，尚未配对或完成双端联调。正常桌面 LAN 后端仍会进行 mDNS 公告和发现，不能仅靠关闭 UDP 广播声称网络隔离。
- 同一模拟器的全局设置页手动切换 Dark 和 Light，截图分别确认深色和浅色；强制停止并重新启动调试应用后，设置页仍显示 Light。截图分别为 `screen-5fd3fdd76df34290baa7a388b3fdab45.png`、`screen-f122c9b6ece64c22941b46b17e9c1db1.png`、`screen-dc68a0dfbb8543848328d83a92c6ea93.png`，位于上述证据目录。这只覆盖全局设置页，不代表独立控制面板主题、所有 Compose 页面或实体手机验证完成。
- 在上述模拟器的空设备配对列表页，Light 与 Dark 两种全局选择分别呈现浅色及深色界面，截图为 `screen-91da08ad97a24702ba7783bc4705c061.png` 和 `screen-d81469c32c73467594f4f0e7c62afdea.png`。仅检查无设备状态，没有发起配对；控制面板本身和其他 Compose 页面仍需单独验证。
- 随后天气模块新增对服务响应 JSON 类型异常的保护，避免异常终止本地天气刷新；`assembleDebug` 再次成功，但这次修改后尚未重跑 322 项测试或 Lint，也没有重新安装模拟器。异常响应和同城缓存回退仍需专门测试；上面的模拟器与全量测试证据针对修改前版本。
- 随后以注入的本地 JSON 响应覆盖错误地理编码、错误天气结构、同城缓存回退和跨城市隔离；定向执行 `PanelLocalWeatherCacheTest` 在 SDK 28/37 共 4 项通过，没有访问外部天气服务。定向运行覆盖了构建目录中的全量测试结果文件，不应把当前测试目录当成 322 项的完整报告；最终提交前仍需全量重跑并保存报告。
- 桌面对应工作区在 `kdeconnect-kde`；其本轮 25/25 CTest 记录见该仓库 `docs/control-panel/BUILD_STATUS.zh-CN.md`。两端本轮改动尚未提交或推送，切换电脑前需先按仓库规则完成同步。

2026-11-15 当前接续见[换机指南](DEVELOPER-HANDOFF.zh-CN.md)：签名 Release alpha.3、配套 Windows alpha.4 已公开，源码已同步个人 GitHub。本次仅整理文档，没有重跑构建/功能测试；下方“仅本地/未发行”是对应历史阶段状态。

当前 Android 结果见[阶段二十三](MILESTONE-23-PANEL-CONTRAST.zh-CN.md)：主控制页九种背景及五种状态的文字检查通过；拥挤布局支持滚动，实测标签高度保留 48dp 图标。保留旧文字/裁切失败及新增图标断言后两项失败，最终 45 套件/304 项通过，Lint 重新生成仍为 493 错误。alpha.3 标签及附件不更新；桌面自选背景、实际辅助技术与原整体验收继续保留。下方旧“当前”均为历史节点。

当前结果见[阶段二十二](MILESTONE-22-CONTRAST-STARTUP.zh-CN.md)：双端表盘文字对比度修复、Android 45 套件/298 项、Windows 22/22 与新包三轮无窗口自检通过。修复前颜色不足及 r1/r2 首次启动失败均保留，r3 包含限时身份等待和旧版 PowerShell 内置模块来源修复。Lint 新报告仍为 493 错误；这里只收口当前纯色表盘和交付自检子项，主控制页自定义背景、完整辅助技术与原整体验收不因此通过。alpha.2 不覆盖，下方旧状态为历史记录。

最新见[阶段二十一](MILESTONE-21-DIAL-ACCESSIBILITY.zh-CN.md)：仅 Android 表盘行高/响应式排布修复，45 套件/294 项通过，新内部 APK 的实际两倍字号对照与五表盘唤醒通过。Lint 本轮重新生成仍为 493 错误；TalkBack、颜色对比度及原整体验收范围继续保留。Windows 运行代码未改、全量未重跑；下方旧“当前”与测试数为历史节点，alpha.2 维持原版。

阶段二十补齐[真实双端天气重连对照](MILESTONE-20-WEATHER-E2E.zh-CN.md)：旧包错误已复现，新包同缓存立即显示；新包五表盘空闲/唤醒全部通过。未改运行代码或发布包，未重跑全量或 Lint；阶段十九 274/22 与 493 错误仍为原报告。下方“天气重推未验收”的历史描述由本轮子项证据补充，完整时区、大字体/无障碍、普通 LAN 和整体验收不因此勾选。

当前为 [阶段十九](MILESTONE-19-WEATHER-ORDER.zh-CN.md) 与 [0.1.0-alpha.2](PREVIEW-0.1.0-alpha.2.zh-CN.md)：Android 44 套件/274 项、Windows 22/22 通过，两端构建及 Windows 新内部包无窗口自检通过。Lint 报告本次复用，实际仍为 493 错误，不算通过；新桌面包完整双端/普通 LAN 与发行验收未完成。下方阶段十八及更早的“最新”是历史记录。

阶段十八当前记录见 MILESTONE-18-VISUAL-DIALS.zh-CN.md：裁切失败保留，修复后 Android 44 套件/273 项通过；Windows 全部 21/21 与额外真实快照显示检查通过。新 APK 已在专用 AVD 复跑五表盘，真实温度范围完整可见；Lint 493 错误仍未通过，天气重推时序和原完整验收仍保留。下述旧“最新”不覆盖本轮。

最新检查见 [阶段十七](MILESTONE-17-E2E-FONT.zh-CN.md)：先保留大字体裁切失败，再修复和完整复跑，Android 271 项通过，Windows 21/21 通过；当前包隔离双端联调及实际在线/离线 2.0 字体复核已执行。Lint 493 错误仍未通过，全部平台、普通 LAN、视觉和交付验收仍未完成。下文旧“最新”与测试数字属于对应历史阶段。

更新：2026-11-12。本记录是开发里程碑，不是完整交付验收。

最新为 [阶段十六统一验证](MILESTONE-16-UNIFIED.zh-CN.md)：两端编译、Android 269 项及 APK、Windows 两轮 21/21 和新内部包无窗口启动通过。Lint 本轮为 493 错误 / 211 警告 / 1 提示，不按任务退出值认定通过。功能复核见 DEVELOPMENT-GATE-16.zh-CN.md；后续完整平台、双端、视觉和发行验收仍待完成。

用户要求先输出小版本时，为 [0.1.0-alpha.1](PREVIEW-0.1.0-alpha.1.zh-CN.md) 做了最小包核验。随后 [表盘/外观及焦点边界](DEVELOPMENT-14-DIAL-OPTIONS.zh-CN.md) 和 [连接/多设备](DEVELOPMENT-15-CONNECTIONS.zh-CN.md) 当时只开发不运行；现已纳入阶段十六统一验证第一批。下述旧数字和阶段声明保留历史上下文，不代表当前整体验收通过。

阶段十二、十三当时按用户要求先开发后统一测试，仅静态检查，未构建或生成新包。最近一次完整测试历史节点见 [阶段十一](MILESTONE-11-RESTORE.zh-CN.md)：Android 257 项、Windows 21 套件通过，Lint 仍有 477 项错误。这些历史结果不覆盖当前原生动作、跟随反馈及三角色音量代码，开发内容见 [阶段十三](DEVELOPMENT-13-NATIVE.zh-CN.md)。下述早期“最新”文字仅保留历史上下文。

最新功能记录见 [阶段七 B：信息表盘与三日预报](MILESTONE-07-INFO.zh-CN.md)：Android 200 项单测与 APK、中文五类渲染、SDK 23/28/37 电量/语言检查通过；Windows 20/20 与新候选包 5 项生命周期检查通过。Lint 首轮 490 项错误，修复本模块 4 项代码错误后仍有 486 项错误、212 项警告、1 项提示，不按任务退出 0 认定通过。其余完整回归与发行仍未完成。

最新打包进度见 [阶段七 A：Windows 独立目录候选包](MILESTONE-07-PACKAGING.zh-CN.md)：脱离 Craft 搜索路径的原生/无窗口启动、实际 DLL 来源和五项生命周期检查通过，包含中文/空格路径。没有改动已有运行模块，也不是干净 Windows 或全部功能验收；最终安装、发行依赖声明及完整回归仍待完成。下述阶段六数字保留为原构建环境的历史测试结果。

最新进度见 [阶段六：基础表盘与天气](MILESTONE-06.zh-CN.md)：Android 189 项单测及 APK 通过，Windows 完整构建及 CTest 20/20 通过。图标见阶段五、音量见阶段四、智能跟随见阶段三、双端初版见阶段二。下方保留历史基线记录；同路径 APK/报告可能被后续构建覆盖，当前版本和校验值以最新阶段记录为准。

## 本阶段实现

- 两端配置模型与同一份 JSON 样本：18 个固定槽位、1..10 页、稳定 ID、模式/外观/空闲参数、修订号和大小/深度边界校验。
- 桌面公开配置白名单投影：不向 Android 下发私有路径、动作 target 或未知私有字段。
- 桌面原子存储、上次有效备份、修订计数、并发编辑冲突检测、跨进程文件变更监听、显式恢复并保留损坏文件。
- Android 会话状态：离线/同步时禁止动作、不缓存离线点击、拒绝旧修订、重复点击等待确认、超时释放、断线清空待执行状态。
- 修复已有全屏命令页面在官方 Kotlin 单例改动后的 3 处 Java 调用。

网络插件、动作执行、控制面板界面、图标传输、智能跟随和表盘尚未接入；不能从本阶段测试推断这些功能已经完成。

## Android：通过

环境：JDK 17.0.14、Gradle Wrapper 9.6.0、Android SDK 37、AGP 9.4.1。

执行任务：`testDebugUnitTest assembleDebug`（未禁用或跳过测试）。

- 143 项单元测试：失败 0、错误 0、跳过 0。
- 其中新增 PanelConfigTest 6 项、PanelSessionTest 6 项。
- 调试 APK 构建成功，applicationId 为 org.kde.kdeconnect_tp.debug。
- 本阶段 APK 尚未安装到手机，不是最终控制台交付包。

本机证据：

- 日志：D:/KDEBuild/logs/android-baseline-retry5.log
- 测试报告：E:/git/kdeconnect-android/build/reports/tests/testDebugUnitTest/index.html
- XML：E:/git/kdeconnect-android/build/test-results/testDebugUnitTest/
- APK：E:/git/kdeconnect-android/build/outputs/apk/debug/kdeconnect-android-debug.apk
- APK SHA-256：B80D1801A1BEE671A3AFCE869EBF24193043932F6161559BCC5079351BBD71C9

首轮发现的真实失败及修复：

1. 原全屏命令 Java 页面直接调用 getInstance，官方 Kotlin companion 未导出静态方法；改为 Companion.getInstance。
2. JSON 库允许字符串布尔值，未知字段的深层嵌套未被自动限制；新增显式原始类型和容器深度检查，保留失败用例后重跑全部单元测试。

完整日志还含上游弃用 API/翻译资源警告；此处没有将警告描述为全部解决。

## Windows 控制面板模块：通过

环境：Windows x64、MSVC 19.35、Windows SDK 10.0.22000.0、Craft Qt 6.12.0。

使用本机独立 CMake 入口编译仓库实际 controlpanel 源码及 tests/paneldocumenttest.cpp、tests/panelstoretest.cpp。这不是整个 KDE Connect 的构建。

- 编译成功。
- CTest：2/2 测试套件通过，单项超时 60 秒。
- 再次直接执行并生成 JUnit XML：PanelDocumentTest 20 项、PanelStoreTest 6 项，失败/错误均为 0；计数包含各套件的初始化与清理项。
- 覆盖：共享样本、类型/版本/ID/大小/深度错误、私有字段隔离、并发修改、原子替换监听、损坏配置恢复。

本机证据：

- D:/KDEBuild/logs/panel-tests-configure.log
- D:/KDEBuild/logs/panel-tests-build4.log
- D:/KDEBuild/logs/panel-tests-ctest3.log
- D:/KDEBuild/logs/paneldocumenttest.xml
- D:/KDEBuild/logs/panelstoretest.xml
- 本机测试入口：E:/git/kdeconnect-tooling/controlpanel-tests/CMakeLists.txt
- 构建目录：D:/KDEBuild/panel-tests

首轮独立编译发现变量名 slots 与 Qt 宏冲突，已改名并重编译测试。完整项目中的 tests/CMakeLists.txt 也注册了这两个测试，待其余桌面依赖安装完成后从完整工程再次运行。

新增 C++ 文件按 ECM 的 C++ 格式规则整理并通过格式检查。Craft 提供的 clang-format 20 在本机启动异常，故使用已有 Visual Studio clang-format 15 和相同的 ECM C++ 配置段；未修改第三方工具文件。

## 存储位置与环境修复

源码在 E 盘，不在 C 盘开发：

- E:/git/kdeconnect-android
- E:/git/kdeconnect-kde

Gradle 缓存 D:/gradle；SDK D:/Android/SDK；Craft D:/KDECraft；工具缓存、Java/Android 用户目录、临时文件和构建日志 D:/KDEBuild。Android build 产物目前在 E 盘。

本机辅助入口 E:/git/kdeconnect-tooling/build-android.ps1 和 run-craft.cmd 只调整子进程环境，不修改系统全局环境变量。Android 旧 ANDROID_SDK_HOME 与新 ANDROID_USER_HOME 冲突已在构建进程中消除。Craft 日志默认写 C 盘的问题已改为 D 盘，先前任务日志已保留到 D 盘。Windows SDK 是本机已有系统组件，只读取，不迁移或删除。

C 盘剩余空间仍紧张，项目目录迁移不能控制操作系统和其他程序的写入；未删除其他文件。

## 待完成验证

- 桌面其余 KF/多媒体等依赖仍在安装，尚未完成完整 CMake 构建、完整 CTest、打包或安装。
- Android Lint、真机安装/界面、横屏/生命周期/无障碍测试尚未完成。
- 双端协议、真实动作、断网重连、音量、智能跟随、全部表盘及视觉对照尚未完成。
- 最终包、完整 README 和需求逐项验收继续按 PLAN 执行。
