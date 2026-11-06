# 交付物范围与扫描

- 源码归档和 Git 使用显式白名单：源码、资源、构建与测试脚本、说明、第三方许可证、依赖摘要、公开签名证书摘要。
- 排除 tools、build、SDK、依赖 JAR、本地运行数据、私钥、keystore、签名密码、私有配置和构建日志。
- scripts/prepare-release.py 会扫描源码文件、源码 ZIP 展开内容和 APK 展开内容。发现疑似实际令牌、私钥、本地签名密码或用户私有目录后立即停止。
- 测试源码中的 secret、session=private、Bearer fixture-not-real 等是固定测试字符串，不是真实凭证。
- signing-certificate.sha256 是公开证书摘要，不是私钥。
- 许可证和公开测试下载地址不是运行期用户数据。依赖 SHA-256 锁文件用于重建校验。
- 精确结果记录在本地 build/source-audit.json。签名密钥只保存在本机及本地专用备份中。
