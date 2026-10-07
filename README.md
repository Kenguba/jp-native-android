# JP Native Android

这是支持中文、英文、日文、韩文 OCR 和 Groq 多语种查询的原生 Android 应用。

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

## 架构、代码质量与体积硬约束

以下规则属于工程级硬约束，适用于新增功能、Bug 修复、重构、性能优化、测试、构建与发布。

### 总体开发原则

- **轻量优先**：优先使用平台已有能力和项目现有能力，避免为很小的问题引入大依赖、大框架或重复实现。
- **高内聚、低耦合**：同一功能的状态、规则和行为尽量聚合在同一 feature / 模块内；跨模块通过清晰接口交互，避免互相直接操作内部实现。
- **多复用**：重复 UI、业务规则、网络/存储逻辑、格式化与平台能力应抽成可复用组件、工具或服务，禁止复制粘贴式扩展。
- **可扩展**：新增功能优先通过扩展点、接口、组合和配置完成，避免不断向单一超大类追加分支。
- **最小范围修改**：修复问题时优先定位根因，只修改完成目标所必须的代码；禁止顺手大面积改名、重排、重写无关模块。
- **兼容优先**：已有可用行为、公开入口、数据格式和用户习惯不得因为无关重构被破坏。
- **依赖克制**：新增第三方依赖前必须确认项目现有能力无法低成本解决；能用小依赖解决时不得引入重型框架。
- **避免过度设计**：设计模式只用于降低耦合、提高复用或建立稳定扩展点，不为“使用模式”而制造额外层级和样板代码。

### 架构设计要求

工程架构思路需要与 **Flutter 主流可维护架构思想**保持一致，但当前工程仍是原生 Android Java，**不得为了形式上的“符合 Flutter”而额外引入 Flutter Runtime 或无必要框架**。

默认采用以下等价原则：

- 优先 **feature-first** 组织：按功能聚合代码，而不是把所有页面、所有服务、所有工具无限堆进少数大文件。
- 复杂功能按需拆分为 `presentation / domain / data / platform` 等边界；简单功能不得为了分层而机械增加文件。
- UI / 展示层只负责展示、交互和轻量状态协调，不承载可复用的核心业务规则。
- 业务规则尽量独立于具体 Activity、Service、网络库、数据库和系统 API。
- 数据访问优先通过稳定边界封装；需要时使用 Repository / Adapter / Facade，避免上层直接依赖底层实现细节。
- 状态来源要清晰，避免同一状态在多个对象中重复保存、互相同步。
- 平台能力、网络、AI、存储、OCR 等外部能力需要可替换、可测试，避免业务代码和具体 SDK 强绑定。
- 优先组合而不是继承；优先小接口而不是“大而全”接口。
- 新增抽象必须能实际减少重复、降低耦合或提供明确扩展点，否则不新增抽象层。

允许并推荐按场景使用的设计模式包括但不限于：

- Repository：隔离数据来源与业务层。
- Strategy：替换搜索、解析、AI、OCR、构建策略等可变行为。
- Adapter：隔离第三方 SDK / 平台 API。
- Factory：统一创建具有多实现的对象。
- Facade：为复杂平台能力提供轻量统一入口。
- Observer / 状态驱动：处理 UI 与状态变化。
- Dependency Inversion：高层逻辑依赖抽象，不直接绑死具体实现。

任何模式都必须服务于实际问题，**禁止过度工程化**。

### 文件与类的控制

- 禁止继续制造职责混杂的超大 Activity、Service、Manager 或 Utils。
- 一个类出现多个独立变化原因时，应优先拆分职责。
- 公共工具必须有明确边界，禁止形成“万能 Utils”。
- 新功能优先复用已有组件；若需要新增公共组件，应保证命名、职责和 API 足够稳定。
- 重构优先小步进行，保持每一步可构建、可验证、可回退。

### 代码体积上限

最终工程的 **Git 跟踪的项目自有代码、资源与配置总量不得超过 35 MB**。

统一按以下规则理解：

- 上限按 `35 × 1024 × 1024 bytes` 计算。
- 统计 Git 跟踪的工程自有文件。
- 不统计 `.git`、Gradle/Maven 缓存、Android SDK、构建缓存、临时文件和下载的外部依赖。
- 不允许通过把大量代码或资源改成压缩包、Base64、生成文件等方式规避限制。
- 接近上限前必须先做去重、资源压缩、依赖裁剪和结构优化，而不是直接放宽限制。

### APK 体积上限

**任何最终面向用户发布的单个 APK 都不得超过 30 MB。**

统一按以下规则理解：

- 上限按 `30 × 1024 × 1024 bytes` 计算。
- `arm64-v8a`、`armeabi-v7a`、`arm` 等所有正式发布 APK 都必须分别满足限制。
- `universal` 只有在自身也满足 30 MB 上限时才能作为正式发布产物。
- 如果 `universal` 因包含多个 ABI 超过 30 MB，必须停止发布该 universal APK，优先使用 ABI Split / 分架构 APK，而不是突破体积上限。
- 新依赖、新资源、新模型加入前必须评估对 APK 体积的影响。
- 发布前必须检查实际 APK 文件大小；超过上限视为发布阻断问题。

### 修改优先级

当“快速实现”和上述规则冲突时，优先级固定为：

1. 正确性与稳定性
2. 最小范围修改
3. 轻量 / 高内聚 / 低耦合
4. 复用与可扩展性
5. 性能与体积
6. 实现速度

除非用户明确要求修改本 README 的规则，否则不得为了赶进度临时绕过这些约束。

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

当前 Android 常规发布使用的架构 / 变体后缀包括：

- `arm64-v8a`
- `armeabi-v7a`
- `arm`

`universal` 仅在最终 APK 本身不超过 30 MB 时允许发布。

因此构建产物命名应遵循同一规则，例如：

```text
名字-26.10.07-00004T-arm64-v8a.apk
名字-26.10.07-00004T-armeabi-v7a.apk
名字-26.10.07-00004T-arm.apk
```

如果未来 universal APK 优化到 30 MB 以内，则可使用：

```text
名字-26.10.07-00004T-universal.apk
```

## 当前实现

- 原生 Android Java
- 单桌面图标
- 可拖拽悬浮取词按钮
- 视频风格半透明悬浮搜索页
- MediaProjection 屏幕捕获
- ML Kit 中 / 英 / 日 / 韩多语种 OCR（Latin / Chinese / Japanese / Korean，Play services 动态模型）
- 区域 OCR 取词
- Intent 快速词条弹窗
- PROCESS_TEXT / SEND
- 桌面搜索 Widget
- 交互固定：轻点悬浮球进入桌面全局搜索；拖拽悬浮球进行区域 OCR，识别成功后必须直接打开查词弹窗，不得先进入或回落到桌面全局搜索层
- 全局悬浮搜索应保留当前桌面或应用的系统状态栏，不重新绘制或侵占系统状态栏；OCR 查词结果必须通过显式 Intent 触发 `FloatingService.ACTION_SHOW_LOOKUP_OVERLAY`，由 `WindowManager.TYPE_APPLICATION_OVERLAY` 直接显示在当前桌面或前台 App 上方，禁止为了 OCR 查词启动 `QuickLookupActivity`、切换 Activity 或切换任务栈
- OCR 查词 WindowManager 浮层必须允许用户自主调整：拖动顶部标题区移动窗口，拖动右下角手柄自由缩放；窗口位置与宽高需持久化，并在重新打开时恢复，同时限制在屏幕安全可见范围内
- OCR 层仅负责识别文字和返回尽量完整的原始识别文本，禁止只提取首个词、提前截取 40 字、强制判定语种或改写原文；OCR 四种文字脚本的结果选取属于识别任务，不属于语言语义判断
- OCR 的 Latin / Chinese / Japanese / Korean `TextRecognizer` 必须跟随 `ScreenCaptureService` 生命周期复用，禁止每次框选识别都重新创建并立即销毁；Service 退出时统一释放，仍在执行的识别完成后再安全关闭
- Groq 接收原始 OCR 文本作为用户消息，独立判断输入属于中文、日文、韩文、英文、混合文本或不确定语种；按实际语种解释，不得预设为日语或强制翻译成日语
- 简短汉字、模糊词语及语言重叠情形允许报告不确定性，不能假装能准确区别中文和日文

## Android CPU 架构 / ABI 说明

这里的 v7、v8 指的是 Android 设备的 CPU 指令集架构（ABI），不是 Android 系统版本。

| ABI | 简称 | 位数 | 主要设备 / 用途 |
| --- | --- | --- | --- |
| `armeabi-v7a` | v7 | 32 位 | 老 Android 手机、旧平板 |
| `arm64-v8a` | v8 | 64 位 | 现在绝大多数 Android 手机 |
| `x86` | x86 | 32 位 | 老模拟器、少量旧设备 |
| `x86_64` | x86_64 | 64 位 | 模拟器、部分 Chromebook / 特殊设备 |

## APK 下载建议

正式发布 APK 必须同时满足上面的版本化命名规则和 **单 APK ≤ 30 MB** 的体积硬限制。

| 架构 / 变体 | 包含架构 | 建议 |
| --- | --- | --- |
| `arm64-v8a` | `arm64-v8a` | 推荐给现在绝大多数 Android 手机，体积最小 |
| `armeabi-v7a` | `armeabi-v7a` | 老旧 32 位 ARM 手机 / 平板 |
| `arm` | `arm64-v8a + armeabi-v7a` | 手机通用集合版，兼容新旧 ARM 手机 |
| `universal` | `arm64-v8a + armeabi-v7a + x86 + x86_64` | 仅在最终文件 ≤ 30 MB 时允许发布 |

普通用户建议：

- 新款 Android 手机：下载 `arm64-v8a`
- 不确定新旧 ARM 架构：下载 `arm`
- 老旧 32 位设备：下载 `armeabi-v7a`
- 模拟器 / Chromebook / 特殊设备：优先使用对应 ABI 构建；不得为了兼容而发布超过 30 MB 的 universal APK。

> `arm64-v8a` 和 `armeabi-v7a` 是当前手机端最重要的两种 ARM ABI。x86 / x86_64 如需支持，应继续采用分架构产物或其他不突破 30 MB 上限的方案。

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

如果这 4 个 Secrets 尚未配置，Workflow 只生成临时 Debug Artifact，不发布正式 Release。Secrets 配好后，重新运行 Workflow 即可生成固定签名的正式 APK。

> 注意：GitHub Actions 的具体实现必须服从本 README 的版本与 APK 命名规则。Android `versionCode` 仍需使用独立的纯数字永久递增序列。

## 本地构建

为遵守单 APK ≤ 30 MB 的硬限制，默认只构建当前可发布的 3 个变体；universal 不作为默认发布产物。

Debug：

```bash
gradle --no-daemon \
  :app:assembleArmDebug \
  :app:assembleArm64Debug \
  :app:assembleArmv7Debug
```

正式 Release 构建需要先提供固定签名环境变量，然后运行：

```bash
gradle --no-daemon \
  :app:assembleArmRelease \
  :app:assembleArm64Release \
  :app:assembleArmv7Release
```

GitHub Actions 会自动重命名 APK、验证签名、计算 SHA-256、上传 Artifact，并创建 GitHub Release。所有产物名称和版本号必须遵守本 README 的规则。
