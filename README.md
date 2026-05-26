# AutoClick - Android Auto Clicker

一个 Android 自动连点器应用，通过无障碍服务实现自动化点击操作。

> **当前版本仅支持自动领取汽水音乐的免费听歌时长。** 后续会扩展更多场景。

## 下载安装

前往 [release](./release/) 文件夹下载 `auto-clicker.apk`，安装到 Android 手机即可使用。

## 功能特性

- **坐标点击**：手动设置点击坐标，精确点击目标位置
- **倒计时监控**：自动检测屏幕上的倒计时文本（如"16秒后可领取奖励"），等待倒计时结束后执行操作
- **悬浮窗控制**：悬浮窗显示实时状态、倒计时读秒，支持开始/暂停/停止
- **坐标录制**：在悬浮窗中直接录制屏幕坐标，设置后自动保存，重启 App 无需重新设置
- **无限循环**：支持循环执行任务，自动重复整个流程

## 使用方法

### 1. 设置坐标

1. 安装 APK，打开应用，授予**无障碍服务权限**和**悬浮窗权限**
2. 打开汽水音乐，进入领取免费听歌时长的页面
3. 在悬浮窗中点击「设坐标:领取」，然后在屏幕上点击"领取"按钮的位置
4. 点击「设坐标:奖励」，然后在屏幕上点击弹窗中"奖励"按钮的位置
5. 坐标会自动保存到本地，下次无需重新设置

### 2. 运行任务

1. 点击悬浮窗中的「开始」按钮
2. 程序会自动：
   - 监控屏幕倒计时，显示实时读秒（如 `⏳ 16秒`）
   - 倒计时结束后自动点击"领取"坐标
   - 弹窗出现后自动点击"奖励"坐标
   - 循环执行以上流程

### 3. 控制

- **暂停/恢复**：暂停或恢复任务执行
- **停止**：停止当前任务
- **最小化**：将悬浮窗最小化为小圆点

## 项目结构

```
app/src/main/java/com/example/autoclick/
├── config/
│   ├── CoordinateStore.kt    # 坐标持久化存储
│   ├── PresetTemplates.kt    # 预设任务模板
│   └── TaskConfig.kt         # 任务配置数据类
├── service/
│   ├── AutoClickService.kt   # 无障碍服务核心（查找文本、手势点击）
│   └── FloatingWindowService.kt  # 悬浮窗服务（UI、坐标录制）
├── task/
│   ├── ClickStep.kt          # 步骤数据模型和类型枚举
│   ├── TaskScheduler.kt      # 任务调度器（执行逻辑、倒计时等待）
│   └── StepExecutor.kt       # 步骤执行结果
├── ui/screens/
│   ├── HomeScreen.kt
│   ├── ConfigScreen.kt
│   └── LogScreen.kt
├── util/
│   ├── PermissionHelper.kt
│   └── RandomUtil.kt         # 随机延迟和坐标偏移
└── MainActivity.kt
```

## 步骤类型

| 类型 | 说明 |
|------|------|
| `COORDINATE_CLICK` | 单次坐标点击 |
| `REPEAT_COORDINATE_CLICK` | 重复坐标点击直到超时 |
| `FIND_AND_CLICK` | 查找文本并点击 |
| `FIND_AND_CLICK_ANY` | 查找多个文本中的任一并点击 |
| `WAIT` | 等待指定时间 |
| `WAIT_FOR_TEXT` | 等待指定文本出现 |
| `WAIT_COUNTDOWN` | 等待倒计时结束（监控屏幕倒计时文本消失） |
| `WAIT_AND_CLICK` | 等待文本出现后立即点击 |
| `SWIPE` | 滑动操作 |
| `BACK` | 返回操作 |

## 权限要求

- **无障碍服务权限**：用于读取屏幕内容和执行手势点击
- **悬浮窗权限**：用于显示控制悬浮窗
- **前台服务权限**：保持服务在后台运行

## 技术栈

- **语言**：Kotlin
- **UI**：Compose + XML 混合
- **异步**：Kotlin Coroutines + Flow
- **存储**：SharedPreferences
- **最低版本**：Android 7.0 (API 24)

## License

MIT License
