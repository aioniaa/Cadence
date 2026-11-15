# Cadence

按日期排班的极简班表。Android 2.6.5 使用原生 Kotlin + Jetpack Compose，界面组件来自 [mochi-compose 0.2.0](https://github.com/feice11/mochi-compose)，排班投影与倒计时核心用 Rust 实现。macOS 1.4.1 保留桌面版和桌面小组件。

> **技术说明**：mochi-compose 是 Kotlin / Jetpack Compose 组件库，无法从 Rust 直接调用。因此采用 **UI = Kotlin + Compose + Mochi，核心 = Rust + JNI**。这样既能严格使用组件库，又能把日期投影、自动轮班和倒计时放进 Rust。


### 应用图标

<p align="center">
  <img src="screenshots/banbiao-android-icon.png" width="150" alt="Cadence Android 图标">
</p>

黑色到深灰的渐变背景 + 单个粗体连笔 C，代表 Cadence；没有额外图形、文字或拼接。Android 使用自适应图标、单色主题图标和旧版 PNG 图标。

## Android 2.6.5

### 四个页面 + 悬浮底栏

- **主页**：倒计时、日历和本月工资入口；没有设置入口、时间按钮或折叠大标题，页面本身不滚动，拖动不会改变界面结构。
- **班次**：添加、改名、删除自定义班次；修改开始/结束时间；开启和编辑自动轮班。
- **工资**：累计工资、按月/按年查询和年度明细；可在首页底部记录当月到手工资。
- **设置**：按钮点击切换浅色/深色、应用信息、树状更新日志、超级岛开关。每次发布必须同步追加更新日志。
- **应用内更新**：启动时读取 GitHub Releases 最新版本；设置里点版本行可手动检查。检查中版本号旁显示加载动画，发现更新后用 MochiDialog 提示，带下载进度条，下载完自动调起系统安装器。
- **字体**：恢复原字体，数字启用 tnum 等宽特性，避免宽度跳动；按钮统一液态玻璃。
- **全屏页面**：日详情、班次、自动轮班、更新日志的标题始终带半透明渐变和边缘模糊，内容可从标题背后通过。
- **休假**：日期详情用 `MochiListDisclosure` 在同一容器内展开 MochiCalendar 日期范围选择，带展开动画；未点右上角确认前不会写入休假。确认在右上角、关闭在左上角，按钮使用玻璃设计。
- 底部使用 `MochiTabScaffold` 的悬浮玻璃标签栏；关闭了左右滑切换和滚动收起，避免拖动导致页面结构变化。

### 主页：半圆落日倒计时日历

倒计时和日历合并成一张卡片，倒计时嵌在卡片顶部的半圆落日里：

- **无卡片主页**：倒计时和日历直接铺在页面背景上，不再套一层 `MochiListCard`，不会出现“盒子里装小盒子”的割裂感。
- **汽水进度容器**：固定半圆就是进度容器。上班前/休息时进度会随着倒计时逐渐充满；上班中则随着下班倒计时逐渐减少。液面带小波浪和上升气泡，只对半圆边界做 6dp 高斯模糊。
- **状态位置**：“距离上班/下班/距离下次上班”固定在半圆内部最上方居中，不在容器外。休息日显示到下一次上班的倒计时。
- **时间位置**：倒计时贴在半圆视觉正中间，水平居中。
- **倒计时覆盖**：`MochiCounterText` 直接盖在半圆进度区域中央，时、分、秒之间使用上下两个圆点，整串等宽居中；字体颜色随进度在 `onSurface` 与玫瑰强调色之间混色过渡，贴合背景。
- **日历**：同一张页面上的 42 格月历；去掉顶部左右箭头，改为左右滑动切换月份，保留今天按钮和本月天数。
- **今日时间**：夜班/白班时间放在日历下方，用 `labelSmall` 两行小字 + 很淡的浅灰胶囊底，不抢倒计时。
- **选中状态**：没有外框；在班次灰度底色上叠加动态玫瑰色背景。
- **今天**：玫瑰色日期数字、淡玫瑰底色和底部短线。
- **顶部安全区**：主页使用 `statusBarsPadding()`，内容不会顶到系统任务栏/状态栏。
- **灰度日历**：白班/中班/夜班用三档深浅灰 + 格内文字区分，休息用最浅灰。强调色只给今天、选中和半圆倒计时。

### 自定义班次

- 每个班次包含：名称、开始时间、结束时间、是否全天休息。
- 支持添加任意班次，删除内置或自定义班次；删除时会清理日期覆盖和轮班规则中的引用。
- 自动轮班按班次 ID 保存，所以改名、改时间不会破坏已有规则。
- 旧的 `shiftTimes` / `morning|middle|night|rest` 数据会自动迁移到新的自定义班次模型。

### 自动轮班页面

- 段落卡片内统一 16dp 内边距，标题、班次选择、持续天数之间留出 10–16dp 间距。
- 班次选择改成两列等宽 `MochiChip`，左右边缘和卡片内容对齐。
- “今天是周期第几天”改成七列等宽卡片，与“接下来 7 天”的排版一致；数字和“天”都居中，不再大小不一。
- “添加一段”、保存、关闭按钮左右各留 16dp，不再贴着父容器。

### 工资记录

- **首页入口**：日历底部右侧显示「本月工资」；未记录时显示「记录本月工资」，点击后用 `MochiDialog` + `MochiDialogTextField` 输入金额。输入 0 可以清除本月记录。
- **累计工资**：工资页顶部用 `MochiCounterText` 展示所有月份合计，金额变化时逐位滚动。
- **按年月查询**：`MochiSegmentedControl` 在「按月 / 按年」之间切换；年份和月份都用 `MochiDropdown` 下拉选择，可直接跳到具体年月，左右按钮仍可逐月/逐年浏览。
- **回到本月**：当查看的不是当前年月时，操作区显示「回到本月」，点击立即切回按月模式并定位到当前月份。
- **年度明细**：列出所选年份每个已记录月份，点击任意月份可以直接修改；明细卡片显示 `¥` 金额并带千分位。
- **弹窗动画**：工资记录弹窗保存、取消或返回时先播放 `MochiDialog` 退场动画，再移除弹窗，不再瞬间消失。
- **卡片裁切**：累计工资的背景渐变直接按列表卡片的 `SectionCorner` 裁切，圆角和底色边缘保持一致。
- **存储**：金额按「年-月」以分为单位存进 `AppState.salaries`，只写入本机状态文件；不联网、不上传，卸载应用会一并删除。
- **金额格式**：统一 `¥12,345.67`，所有入口和明细始终保留两位小数，整元也显示 `.00`。

### 配色

- 全应用最多 3 类颜色：**玫瑰强调色 + 黑色/深灰 + 白色/浅灰**。
- 日历、卡片、列表、图标、Tab 栏只使用灰度；只有今天、选中、倒计时、主操作使用玫瑰。
- 液态玻璃材质来自 `iosMaterials`，但配色由自定义 `MochiColorScheme` 提供，避免 iOS 默认蓝色破坏三色约束。

### Mochi 接入

- `MochiTheme` + 自定义 `MochiColorScheme`
- `MochiDialog` / `MochiDialogTextField`（工资输入）
- `MochiSegmentedControl`（工资按月/按年）
- `MochiDropdown` / `MochiItem`（工资年/月跳转）
- `iosMaterials`（mochi-ios v0.2.0 源码接入）
- `ProvideMochiOverscroll`
- `MochiNavHost`
- `MochiTabScaffold` / `MochiTabItem`（悬浮底栏）
- `MochiLazyLargeTitleScaffold`（班次、设置页）
- `MochiListSection` / `MochiListCard` / `MochiListRow` / `MochiListButton`
- `MochiListTextFieldRow` / `MochiDialogTextField`
- `MochiSheet` / `MochiSheetHeader` / `MochiSheetContent`
- `MochiButton` / `MochiButtonStyle.Glass / ProminentGlass / ClearGlass`
- `MochiSegmentedControl` / `MochiStepper` / `MochiChipGroup` / `MochiChip`
- `MochiTimeField`
- `MochiProgressRing`
- `MochiCounterText` / `MochiBlurText`
- `MochiToastHost` / `MochiDialog`
- `MochiIonicon`

月历格是自绘的：组件库没有“按天显示班次背景”的日历组件；圆角、字体、主题色、间距和选中动效都使用 Mochi 的 token 与交互规范。

### Rust 核心

`android-mochi/rust/banbiao_core` 通过 JNI 提供：

- `projectMonth(stateJson, year, month)`：42 格月历、手动覆盖、自动轮班投影
- `projectDays(stateJson, startKey, count)`：7 天预览
- `countdown(stateJson, todayKey, nowSeconds)`：跨天班次、上班前/工作中/休息/未排班/已下班、剩余比例

Rust 动态读取 `shiftTypes`，支持任意班次 ID、名称和时间；旧版无 `shiftTypes` 的状态仍会回退到内置班次和时间。release 开启 LTO、单 codegen unit、strip；Android 侧使用 R8、资源收缩和 Compose Lazy 列表。

## 安装

### Android

1. 安装 `Cadence-2.6.5-Android.apk`
2. Android 8.0（API 26）及以上
3. 覆盖安装旧版即可；首次启动会迁移旧 WebView 排班数据

APK 使用原 `com.banbiao.app` 包名和原 release keystore 签名，versionCode 52，可直接覆盖 2.1.x / 2.0.x / 1.3.2。

### macOS

仍为 1.4.1：

1. 打开 `班表-1.4.1-macOS-arm64.dmg`
2. 拖入「应用程序」覆盖安装

## 从源码构建

### Android

需要 JDK 17、Android SDK Platform 36 / Build-Tools 36、NDK 28.2、Rust stable 和两个 Android target。

```bash
./scripts/build-android-mochi.sh
```

脚本会编译 Rust 两个 ABI、复制 `.so`、构建 release APK，并输出：

```text
dist/Cadence-2.6.5-Android.apk
```

如果 Rust 尚未安装：

```bash
curl https://sh.rustup.rs -sSf | sh
rustup target add aarch64-linux-android x86_64-linux-android
```

并设置 `ANDROID_NDK_HOME` 指向 NDK 28.2。

### macOS

```bash
cd macos && npm install && cd ..
./scripts/build-macos.sh
```

## 项目结构

```text
android-mochi/          Android 2.6 原生应用
  app/src/main/java/com/banbiao/app/
    MainActivity.kt     旧 WebView 数据迁移 + 入口
    core/NativeCore.kt  Rust JNI 桥
    data/State.kt       自定义班次、自动轮班、主题、工资
    ui/CadenceApp.kt    主题、底栏、共享组件
    ui/HomePage.kt      倒计时 + 日历 + 本月工资入口
    ui/ShiftsPage.kt    班次添加/编辑/删除 + 轮班规则
    ui/SalaryPage.kt    累计工资 + 年月查询 + 年度明细
    ui/SettingsPage.kt  外观与关于
    ui/feice/mochi/ios/ mochi-ios v0.2.0 源码接入
  rust/banbiao_core/    Rust 排班/倒计时核心
  app/src/main/jniLibs/ 编译产物
android/                1.3.2 WebView 版源码（历史/迁移参考）
web/                    macOS 共享界面资源
macos/                  Electron 桌面版 + 原生桌面小组件
scripts/                构建脚本
screenshots/            截图与图标
dist/                   安装包
```

## 测试

- Android 2.6.5 release 在 Android 15 模拟器安装运行；验证 4 个 Tab、悬浮底栏、无卡片主页、上班前充能/下班递减进度、休息日下次上班倒计时、混色倒计时、左右滑动月历、主题和 Rust JNI。
- 工资：实际验证首页入口、MochiDialog 输入、按月/按年汇总、跨月累计、年度明细、修改和输入 0 清除；强制停止后重新打开数据仍在。
- 工资金额：2.6.5 实测首页、累计、按月、明细均保留两位小数，整元显示为 `¥1,000.00`。
- 工资年月跳转：2.6.5 实测年份下拉切到 2025 年、月份下拉切到 5 月，点「回到本月」恢复 2026 年 11 月；保存/取消/返回键均先播放关闭动画再移除弹窗。
- 自定义班次：实际添加 `Early 09:00–18:00`，在日期上排班，主页倒计时显示 `Early · 09:00–18:00`。
- 休假：日期详情可设置/取消休假，选中后可用日期范围选择器批量设置；Rust 测试验证休假暂停、假期续接和自动轮班暂停。
- 日历点击：实际点选 11 月 15 日能打开日期详情并修改班次；批量休假入口保留在日历底部。
- 旧版 1.3.2 → 2.x 迁移：实际导入夜班、自动轮班、`21:00–09:00` 自定义时间和深色主题，主页倒计时正常。
- Rust 单元测试：`cargo test`（跨天倒计时、自定义班次、月历 42 格起点）。
- macOS 1.4.1 安装包已实际启动，验证桌面小组件、全屏提醒、班次时间和手动深浅色。

## Mochi 接入验收

- [x] 根部只接一次 `MochiTheme`，页面内不写外观分支
- [x] `ProvideMochiOverscroll`、`MochiNavHost`、`MochiTabScaffold`、`MochiLazyLargeTitleScaffold`
- [x] 底栏使用 `MochiTabItem` + Mochi 玻璃材质，关闭拖动切页/滚动收起
- [x] 列表使用 `MochiListSection/Card/Row`，编辑使用 `MochiSheet`
- [x] 数字倒计时使用 `MochiCounterText`，状态文字使用 `MochiBlurText`
- [x] 主题切换使用 `MochiThemeRevealHost`
- [x] 破坏性删除使用 `MochiDialog`
- [x] 图标全部来自 `MochiIonicon`，无 Material Icons
- [x] 圆角只从 `MochiTheme.shapes` 取，无 `RoundedCornerShape`
- [x] 颜色最多三套语义：玫瑰强调 + 深灰/黑 + 浅灰/白
- [x] 主页不包含设置、时间编辑、自动轮班等次级功能；全部移到班次/设置页

第三方源码与许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 开源许可证

Cadence 使用 **Apache License 2.0**，与 mochi-compose 上游许可证保持一致。
