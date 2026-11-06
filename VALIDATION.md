# G 盘 0.1 验证记录

日期：2026-11-06；环境：本机 Termux。

## 已完成

- sh tests/check.sh：63 项断言通过。
- 固定 HTTP 服务器实际传输 6 MiB+ 文件，对比所有输出字节：4 连接、单连接、暂停后恢复、Range 拒绝回退、版本变化、无版本标识、302 跳转。
- 登录 HTML 被拒绝作为文件；不覆盖已有文件；静态页面下载链接发现；跨来源不转发手动 Cookie/Authorization。
- ZIP 中文路径、AES 密码、错误密码、7z、RAR4 存储格式、RAR5 明确拒绝、路径穿越、重复路径、取消和已有输出目录保护。
- sh build.sh：APK 构建成功；签名 v2/v3 和 zipalign 通过。包名 cn.g.pan，版本 0.1，最低 Android 8。
- sh tests/runtime.sh：Android app_process / ART 直接加载交付 APK 的类，5 项检查通过：AES ZIP、7z、RAR4、网盘识别和实际 HTTPS 下载。
- ART 实际下载 Maven Central 的 zip4j 2.11.5 JAR：210027 字节；SHA-256 为 fee9a941f286166d4a19719d818f20e9f3addb3cbd7291c08b67f0e0ce75a9fd，与锁定依赖一致。
- 第三方许可证放入 APK 的 assets/licenses。
- 本地签名材料另有本地备份，未进入源码归档或 Git。

## 未验证及限制

- 尚未安装 App 后手动点击界面。当前没有可用的 ADB 设备连接。
- 尚未使用用户实际网盘分享链接、登录账号、验证码、会员状态进行端到端验证。蓝奏/123 的协议适配不等于已经验证真实分享成功。
- 没有“所有网盘不限速”的证据或承诺；实际速度取决于网盘服务器和网络。
- RAR4 测试为存储格式样本；RAR5 不支持。复杂分卷和大内存 7z 等未全面测试。
- Android 后台系统策略、长时间熄屏、系统文件导出和不同厂商 WebView 尚需安装后验证。

首次 ART 启动被 Android 的“可写 DEX 不允许加载”保护拒绝，已将测试副本改成只读。最终测试退出码为 0。该问题仅涉及测试加载方式，不改变安装包签名。

## 发布门槛

scripts/prepare-release.py 使用源码白名单、扫描源码归档和 APK 展开内容，并核对真实本地签名密码未进入交付物。GitHub 发布需再次核对私有状态、远端提交、Release 的 uploaded 状态、大小与 SHA-256。
