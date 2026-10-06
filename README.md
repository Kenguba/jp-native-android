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

构建：
```bash
gradle :app:assembleDebug
```

APK：
`app/build/outputs/apk/debug/app-debug.apk`
