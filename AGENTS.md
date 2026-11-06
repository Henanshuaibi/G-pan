# G pan

- Android package cn.g.pan; visible name G 盘. Preserve signing identity and bump versionCode for updates.
- Do not describe recognition, unofficial adapter implementation or fixtures as proof of a successful real cloud share download.
- No promise to bypass provider server limits, subscriptions or captcha. Keep concrete failure and browser fallback behavior.
- No third-party parser server or credential uploads. Keep WebView without JavaScript interfaces and local-file access.
- Download parts must validate range, total size and validator before being merged. Stop workers before reset; do not overwrite existing final files.
- Extraction must remain local, bounded and protected against traversal. Passwords must not be persisted.
- Build: sh build.sh. Core checks: sh tests/check.sh. ART/APK core smoke plus live HTTPS: sh tests/runtime.sh.
- SDK, dependency jars, build output, credentials, signing keys/passwords and runtime/user data must remain outside Git and source archives.
- Release only to a private repository, after source/archive/APK secret checks. Preserve local-only signing backup.
