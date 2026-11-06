# G 盘 0.1

Android 网盘链接下载与免费本地解压工具。包名 cn.g.pan，Android 8.0 及以上。

## 已实现

- 粘贴完整分享文字，识别 24 类常见网盘及普通 HTTP/HTTPS 链接，提取分享码。
- 蓝奏云、123 云盘公开分享的自动解析适配；支持文件列表及 123 文件夹浏览。
- 普通文件直链、静态网页中的下载链接、部分 Dropbox / Google Drive 分享。
- 内置原站浏览器：由用户登录、输入验证码并点击下载后，接管可用的 HTTP/HTTPS 文件地址。
- 最多 4 连接分段下载、暂停继续、失败重试、前台通知和实时速度。
- 校验 Content-Range、远端版本标识和分段大小；远端版本变化或服务器拒绝分段时重新用单连接下载。
- ZIP（包括 AES 加密）、7z、RAR4 免费本地解压，使用系统文件选择器导入压缩包。
- 路径越界、重复路径、输出空间和解压内存保护。失败不发布半成品解压目录。
- 本地文件打开、分享，以及保存到系统下载目录。没有广告、付费入口或第三方解析服务器。

## 真实边界

**不保证“所有网盘都能下载”或解除原站服务器限速。** 识别出网盘名称不等于已拿到下载权限。

蓝奏/123 为非官方分享接口适配，可能随原站更新失效。未获得实际有效分享链接之前，不应把适配代码或模拟测试称为真实网盘验证。百度、夸克、阿里等需要登录、验证码或会员权限的功能仍取决于原站。在原站需要会员才能下载的文件，App 不能保证取得地址。

RAR5、部分分卷、blob URL、DRM、动态生成且未暴露 HTTP 文件地址的下载暂不支持。7z 解码内存限制 64 MiB、单次解压输出上限 20 GiB、最多 100000 项。文件并行下载需要额外的合并空间，按约两倍文件大小预留。没有可靠远端版本标识或 Range 支持时重新下载，不冒险拼接不同版本。

本地解压必须先取得压缩包，不能代替网盘服务器的会员在线解压。

## 使用

1. 安装 G-pan-0.1.apk。
2. 复制分享文字，在首页点击“粘贴并开始”；或从其他 App 的分享菜单选择 G 盘。
3. 自动解析成功会加入任务；多个文件则点击想下载的文件。
4. 需原站验证时点击“打开原站并接管下载”，登录后点原站下载按钮。部分网站可尝试“电脑”网页模式。
5. 在“传输任务”查看进度、暂停和继续。地址过期时“重新获取链接”会返回分享文字，重新解析后新建任务。
6. 本地文件或选择压缩包 → 免费解压。有密码则填写；没有则留空。

下载及解压文件默认保存在 App 专属目录。需要保留时点击文件 → “保存到系统下载目录”，Android 10+ 导出到 Download/G盘。Android 8/9 使用系统“另存为”。卸载 App 会删除尚未导出的 App 文件。

Termux 中将安装包复制到手机 Download：

    cp ~/G-pan-0.1.apk ~/storage/downloads/

## 隐私

仅联网访问用户提供的链接、网盘原站及下载服务器，不使用公共解析中转站或分析 SDK。任务记录、分享地址和原站登录 Cookie 仅存本地。Cookie 不写入任务 JSON；网页登录状态由系统 WebView 管理。压缩密码不持久化。设置中可清除网页登录状态。

仅访问用户主动选择的文件，未申请全盘存储、无障碍或悬浮窗权限。HTTP 链接可下载，但其传输不加密；不允许 HTTPS 自动降级到 HTTP，不跨来源转发手动 Cookie/Authorization。

## 构建与恢复

Termux 环境需要 OpenJDK 21、clang、aapt2、zipalign、Python。SDK 文件 android.jar、android-res.jar 和 r8.jar 放在相邻 pulse-player/tools，或设置 GPAN_SDK_DIR。

    python scripts/fetch-deps.py
    sh tests/check.sh
    sh build.sh
    sh tests/runtime.sh

tests/check.sh 还需要 tools/json.jar（org.json:json:20240303）。下载依赖校验以 dependencies.json 中 SHA-256 为准。

构建还需要本地 tools/g-pan.keystore、tools/signing-password，别名 gpan，证书须匹配 signing-certificate.sha256。**源码和 GitHub Release 不含私钥或密码**，恢复覆盖更新必须同时恢复单独保存的本地签名备份。新签名不能覆盖原安装。

scripts/java-env.sh 在本项目子进程中使用小型共享内存兼容库，避免此设备上 OpenJDK 间接加载 Android framework JNI 导致崩溃，不修改系统 Java，也不会将兼容库打入 APK。

## 验证

详见 VALIDATION.md。构建、固定测试、APK 代码在 Android ART 中的检查、真实 HTTP 下载、真实网盘账号和安装后界面测试分别记录。
