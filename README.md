# JP Native Android

这是独立的日文 OCR 原生 Android 应用。

当前实现：
- 原生 Android Java
- 单桌面图标
- 可拖拽悬浮取词按钮
- 视频风格半透明悬浮搜索页
- MediaProjection 屏幕捕获
- ML Kit 日文 OCR
- 区域 OCR 取词
- Intent 快速词条弹窗
- PROCESS_TEXT / SEND
- 桌面搜索 Widget

## Android CPU 架构 / ABI 说明

这里的 v7、v8 指的是 Android 设备的 CPU 指令集架构（ABI），不是 Android 系统版本。

| ABI | 简称 | 位数 | 主要设备 / 用途 |
| --- | --- | --- | --- |
| `armeabi-v7a` | v7 | 32 位 | 老 Android 手机、旧平板 |
| `arm64-v8a` | v8 | 64 位 | 现在绝大多数 Android 手机 |
| `x86` | x86 | 32 位 | 老模拟器、少量旧设备 |
| `x86_64` | x86_64 | 64 位 | 模拟器、部分 Chromebook / 特殊设备 |

## APK 下载建议

每次已配置正式签名的 GitHub Actions 构建会一次生成 4 个 APK：

| APK | 包含架构 | 建议 |
| --- | --- | --- |
| `jp-native-android-arm64-v8a.apk` | `arm64-v8a` | 推荐给现在绝大多数 Android 手机，体积最小 |
| `jp-native-android-armeabi-v7a.apk` | `armeabi-v7a` | 老旧 32 位 ARM 手机 / 平板 |
| `jp-native-android-arm.apk` | `arm64-v8a + armeabi-v7a` | 手机通用集合版，兼容新旧 ARM 手机 |
| `jp-native-android-universal.apk` | `arm64-v8a + armeabi-v7a + x86 + x86_64` | 全架构版，兼容性最高，体积最大 |

普通用户建议：

- 新款 Android 手机：下载 `arm64-v8a`
- 不确定新旧 ARM 架构：下载 `arm`
- 老旧 32 位设备：下载 `armeabi-v7a`
- 模拟器 / Chromebook / 特殊设备 / 完全不确定：下载 `universal`

> `arm64-v8a` 和 `armeabi-v7a` 是当前手机端最重要的两种 ARM ABI。x86 / x86_64 主要用于模拟器和少量特殊设备，所以不单独提供 x86 APK，它们统一包含在 universal 全架构版中。

## 固定 Release 签名

正式 APK 使用固定 Release 证书。公开证书保存在：

`signing/jp-native-android-release-cert.pem`

证书 SHA-256：

`CB:8B:97:98:9B:FE:78:94:E7:96:84:2C:E5:B2:E3:E3:B2:AA:B4:8A:A7:F0:F7:A9:7C:1E:CE:04:AC:18:42:52`

私钥 / JKS 不提交到仓库。GitHub Actions 读取以下 Repository Secrets：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

如果这 4 个 Secrets 尚未配置，Workflow 只生成临时 Debug Artifact，不发布正式 Release。Secrets 配好后，重新运行 Workflow 即可生成固定签名的 4 个正式 APK。

GitHub Actions 的 `versionCode` 使用 `github.run_number` 自动递增，`versionName` 使用 `0.4.<run_number>`，方便后续直接覆盖升级。

## 本地构建

Debug 一次构建全部 4 个 ABI：

```bash
gradle --no-daemon \
  :app:assembleUniversalDebug \
  :app:assembleArmDebug \
  :app:assembleArm64Debug \
  :app:assembleArmv7Debug
```

正式 Release 构建需要先提供固定签名环境变量，然后运行：

```bash
gradle --no-daemon \
  :app:assembleUniversalRelease \
  :app:assembleArmRelease \
  :app:assembleArm64Release \
  :app:assembleArmv7Release
```

GitHub Actions 会自动重命名 APK、验证签名、计算 SHA-256、上传 Artifact，并创建 GitHub Release。
