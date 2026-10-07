# JP Native Android

这是独立的日文 OCR 原生 Android 应用。

## 工程规则唯一来源（Single Source of Truth）

**根目录 `README.md` 是本工程唯一工程规则源（Single Source of Truth, SSOT）。**

以后进行任何以下工作前，都必须先读取本文件：

- 开发
- 修复
- 重构
- 测试
- 构建
- 发布

如果其他 feature README、子目录 README、旧文档、历史说明、代码注释或其他规则与根目录 `README.md` 冲突，**一律以根目录 `README.md` 为准**。

工程规则发生变化时，应优先更新本文件，避免在多个文档中维护互相冲突的规则。

## 版本规则

对外版本号固定格式：

```text
YY.MM.DD-NNNNNX
```

示例：

```text
26.10.07-00001A
26.10.07-00002D
26.10.07-00003B
26.10.07-00004T
26.10.07-00005C
26.10.07-00006R
26.10.07-00007P
26.10.07-00008H
```

阶段字母固定为：

| 字母 | 阶段 | 含义 |
| --- | --- | --- |
| `A` | Alpha | 原型阶段，可无构建产物 |
| `D` | Development | 开发调试 |
| `B` | Build | 成功构建 |
| `T` | Test | 测试 |
| `C` | Candidate | 发布候选 |
| `R` | Release | 正式发行构建 |
| `P` | Production | 正式生产版本 |
| `H` | Hotfix | 生产环境紧急修复 |

### 流水号规则

`NNNNN` 为 5 位十进制流水号。

同一天：

- 所有阶段共用同一条流水号序列。
- 阶段字母变化时，流水号**不归零**。
- 每产生一个新版本，`NNNNN` 继续递增。

例如：

```text
26.10.07-00001A
26.10.07-00002D
26.10.07-00003B
26.10.07-00004T
```

换一天：

- 使用新的当天日期。
- 当天流水号重新从 `00001` 开始。

例如：

```text
26.10.07-00008H
26.10.08-00001D
```

### Android / iOS 内部构建号

对外版本号与平台内部构建号分离。

- Android `versionCode` 必须保持**纯数字、永久递增**。
- iOS build number 必须保持**纯数字、永久递增**。
- 不允许直接把带日期、连字符或阶段字母的 `YY.MM.DD-NNNNNX` 硬塞进 Android `versionCode` 或 iOS build number。
- 平台内部构建号跨日期、跨阶段都不能回退或重复。

## APK 命名规则

APK 文件名固定格式：

```text
名字-YY.MM.DD-NNNNNX-ABI.apk
```

示例：

```text
名字-26.10.07-00004T-arm64-v8a.apk
```

其中版本部分必须严格使用本 README 定义的 `YY.MM.DD-NNNNNX` 规则。

当前 Android 构建所使用的架构 / 变体后缀包括：

- `arm64-v8a`
- `armeabi-v7a`
- `arm`
- `universal`

因此构建产物命名应遵循同一规则，例如：

```text
名字-26.10.07-00004T-arm64-v8a.apk
名字-26.10.07-00004T-armeabi-v7a.apk
名字-26.10.07-00004T-arm.apk
名字-26.10.07-00004T-universal.apk
```

## 当前实现

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

每次已配置正式签名的 GitHub Actions 构建会一次生成 4 个 APK。实际文件名必须使用上面的版本化 APK 命名规则。

| 架构 / 变体 | 包含架构 | 建议 |
| --- | --- | --- |
| `arm64-v8a` | `arm64-v8a` | 推荐给现在绝大多数 Android 手机，体积最小 |
| `armeabi-v7a` | `armeabi-v7a` | 老旧 32 位 ARM 手机 / 平板 |
| `arm` | `arm64-v8a + armeabi-v7a` | 手机通用集合版，兼容新旧 ARM 手机 |
| `universal` | `arm64-v8a + armeabi-v7a + x86 + x86_64` | 全架构版，兼容性最高，体积最大 |

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

> 注意：GitHub Actions 的具体实现必须服从本 README 的版本与 APK 命名规则。Android `versionCode` 仍需使用独立的纯数字永久递增序列。

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

GitHub Actions 会自动重命名 APK、验证签名、计算 SHA-256、上传 Artifact，并创建 GitHub Release。所有产物名称和版本号必须遵守本 README 的规则。
