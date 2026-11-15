package com.banbiao.app.data

internal data class ChangelogEntry(
    val version: String,
    val current: Boolean = false,
    val updates: List<String> = emptyList(),
    val fixes: List<String> = emptyList(),
)

internal val CADENCE_CHANGELOG: List<ChangelogEntry> = listOf(
    ChangelogEntry(
        version = AppInfo.version,
        current = true,
        updates = listOf(
            "更新弹窗只展示版本更新内容，不再带附件和安装说明",
            "设置新增鸣谢，链接作者与 mochi-compose",
            "开源许可证调整为 Apache-2.0，图标字体改用 OFL Pacifico",
        ),
    ),
    ChangelogEntry(
        version = "2.5.2",
        updates = listOf(
            "更新提示拆分为可用更新弹窗和下载进度弹窗",
            "下载进度弹窗支持随时取消",
            "版本号改为单行并垂直居中",
            "检查更新 loading 至少显示 3 秒",
        ),
        fixes = listOf("明确区分网络不可用、检查失败和已是最新版本"),
    ),
    ChangelogEntry(
        version = "2.5.1",
        updates = listOf(
            "设置版本行支持点击检查更新，检查时显示加载动画",
            "更新弹窗增加 Mochi 下载进度条",
            "下载完成后自动调起系统安装器",
        ),
    ),
    ChangelogEntry(
        version = "2.5.0",
        updates = listOf(
            "启动时检查 GitHub Release，可在应用内下载并安装更新",
            "上班前与休息时进度逐渐充满，下班时逐渐减少",
            "休息日显示距离下次上班的倒计时",
        ),
        fixes = listOf("更新下载和安装不再跳转浏览器"),
    ),
    ChangelogEntry(
        version = "2.4.20",
        updates = listOf(
            "应用图标只保留单字母 C",
            "C 使用粗体连笔手写设计",
        ),
    ),
    ChangelogEntry(
        version = "2.4.19",
        updates = listOf(
            "应用图标改为黑底渐变灰",
            "图标上层改为粗体连笔 Cadence 字标",
        ),
    ),
    ChangelogEntry(
        version = "2.4.18",
        updates = listOf(
            "首页倒计时位置下移到半圆视觉中心附近",
            "倒计时冒号改为上下两个圆点",
        ),
    ),
    ChangelogEntry(
        version = "2.4.17",
        updates = listOf(
            "超级岛焦点协议适配 HyperOS 4",
            "优化小米设备焦点通知参数下发",
        ),
        fixes = listOf("焦点通知未开通时不再误判为不上岛"),
    ),
    ChangelogEntry(
        version = "2.4.16",
        updates = listOf(
            "超级岛接入小米 miui.focus.param 焦点通知",
            "上岛显示上班中的下班倒计时",
        ),
        fixes = listOf("系统支持焦点通知时不再只显示普通通知"),
    ),
    ChangelogEntry(
        version = "2.4.15",
        fixes = listOf("周期选择块改为未选灰、选中白"),
    ),
    ChangelogEntry(
        version = "2.4.14",
        updates = listOf(
            "自动轮班分段底色按亮暗模式适配",
            "周期第几天选中块改为灰色",
            "设置增加超级岛开关，显示下班倒计时",
        ),
        fixes = listOf("通知权限未授予时不会误上岛"),
    ),
    ChangelogEntry(
        version = "2.4.13",
        updates = listOf(
            "恢复原字体，数字改用等宽数字特性",
            "自动轮班分段底色改为更接近父容器的浅色",
        ),
        fixes = listOf(
            "修复字体过宽导致部分容器文字看不清",
            "避免数字宽度变化把容器撑开",
        ),
    ),
    ChangelogEntry(
        version = "2.4.12",
        updates = listOf(
            "添加班次改为四周留白的玻璃按钮",
            "自动轮班每 X 天垂直居中，周期摘要移到容器下方",
            "自动轮班分段和周期选择块匹配容器圆角，底色减淡",
            "全局改用等宽字体，按钮统一液态玻璃",
        ),
    ),
    ChangelogEntry(
        version = "2.4.11",
        updates = listOf(
            "休假设置中增加取消休假",
            "已休假状态与休假标题垂直居中对齐",
            "自动轮班周期摘要移到规则容器下方",
        ),
    ),
    ChangelogEntry(
        version = "2.4.10",
        fixes = listOf("修复日期选择器展开时的漂移错位"),
    ),
    ChangelogEntry(
        version = "2.4.9",
        updates = listOf(
            "休假日期选择器改为确认后生效",
            "日期选择器按钮统一使用玻璃设计",
            "标题模糊半径回退，模糊力度加强",
        ),
        fixes = listOf("未确认前不再提前写入休假"),
    ),
    ChangelogEntry(
        version = "2.4.8",
        updates = listOf(
            "休假日期选择器改用 MochiListDisclosure 同级展开动画",
            "自动轮班每一段增加浅色底和段间距",
        ),
        fixes = listOf("移除日期选择器多余的外层容器"),
    ),
    ChangelogEntry(
        version = "2.4.7",
        updates = listOf(
            "标题渐变模糊改为一直显示，不再等滚动",
            "标题边界模糊范围加大",
        ),
        fixes = listOf("修复标题背景偶尔不模糊"),
    ),
    ChangelogEntry(
        version = "2.4.6",
        updates = listOf(
            "点击休假后直接展开日期范围选择器",
            "日期选择确认按钮移动到页面右上角",
            "关闭按钮移动到页面左上角",
        ),
        fixes = listOf("移除多余的多选日期按钮"),
    ),
    ChangelogEntry(
        version = "2.4.5",
        updates = listOf(
            "标题改为透明覆盖，滚动内容可从标题背后通过",
            "标题背景改为渐变与边缘模糊，露出后方内容",
        ),
        fixes = listOf("标题不再常驻铺一块底色"),
    ),
    ChangelogEntry(
        version = "2.4.4",
        updates = listOf(
            "批量休假按钮改为独立空白间距，左右边距加大",
            "标题滚动时增加边缘模糊渐显",
        ),
        fixes = listOf(
            "修复关闭按钮贴住标题容器底部",
        ),
    ),
    ChangelogEntry(
        version = "2.4.3",
        updates = listOf(
            "休假改为起止日期范围选择器",
            "自动轮班开关移到日期详情",
            "自动轮班班次改为单行方块选择",
        ),
        fixes = listOf(
            "移除自动轮班多余分割线",
            "全屏面板标题改为随滚动渐显，不再常驻渐变底",
        ),
    ),
    ChangelogEntry(
        version = "2.4.2",
        updates = listOf("全屏展开的底部页面顶部标题加入渐变模糊过渡"),
        fixes = listOf("修复日历日期点击偶发无法打开编辑"),
    ),
    ChangelogEntry(
        version = "2.4.1",
        updates = listOf(
            "日历支持多选日期批量设为休假",
            "自动轮班可暂停/恢复，暂停时保留规则",
        ),
        fixes = listOf(
            "批量休假会清理对应日期的手动排班",
        ),
    ),
    ChangelogEntry(
        version = "2.4.0",
        updates = listOf(
            "设置页新增树状更新日志",
            "亮暗切换改为按钮点击",
            "应用图标改为纯色背景一笔画",
            "日期可设为休假，轮班自动暂停并在假期后续接",
        ),
        fixes = listOf("应用内版本号与构建版本保持同步"),
    ),
    ChangelogEntry(
        version = "2.3.9",
        updates = listOf("汽水液面改为剩余倒计时比例"),
        fixes = listOf("倒计时减少时汽水背景不减少"),
    ),
    ChangelogEntry(
        version = "2.3.8",
        updates = listOf(
            "加入汽水波浪、气泡与 6dp 模糊边界",
            "日历支持左右滑动切换月份",
            "移除日历顶部左右箭头",
        ),
        fixes = listOf("边界模糊过大、半圆形状不清"),
    ),
    ChangelogEntry(
        version = "2.3.7",
        updates = listOf(
            "主页移除卡片外框",
            "倒计时字体随背景混色",
        ),
        fixes = listOf("主页盒中盒的割裂感"),
    ),
    ChangelogEntry(
        version = "2.3.6",
        updates = listOf("时间改回冒号并保持居中"),
        fixes = listOf("圆点分隔和文字偏移"),
    ),
    ChangelogEntry(
        version = "2.3.5",
        updates = listOf("完整半圆、时间贴在半圆中心"),
        fixes = listOf("半圆被裁切"),
    ),
    ChangelogEntry(
        version = "2.3.4",
        updates = listOf("玫瑰色主题、整块羽化填充"),
        fixes = listOf("硬切边和黄色配色"),
    ),
    ChangelogEntry(
        version = "2.3.3",
        updates = listOf("柔和径向渐变背景"),
        fixes = listOf("半圆边缘过硬"),
    ),
    ChangelogEntry(
        version = "2.3.2",
        updates = listOf("半圆整块进度、缩小今日时间"),
        fixes = listOf("时间胶囊过大"),
    ),
    ChangelogEntry(
        version = "2.3.1",
        updates = listOf("倒计时融入日历顶部"),
        fixes = listOf("倒计时与日历两张卡片割裂"),
    ),
    ChangelogEntry(
        version = "2.3.0",
        updates = listOf("落日倒计时与日历合并"),
        fixes = listOf("主页信息层级不清"),
    ),
    ChangelogEntry(
        version = "2.2.2",
        updates = listOf("自动轮班排版统一"),
        fixes = listOf("按钮贴边、周期格大小不一"),
    ),
    ChangelogEntry(
        version = "2.2.1",
        updates = listOf("整块进度、三档灰度班次"),
        fixes = listOf("状态栏遮挡、元素间距拥挤"),
    ),
    ChangelogEntry(
        version = "2.2.0",
        updates = listOf(
            "Cadence 品牌、三 Tab 悬浮底栏",
            "自定义班次与独立设置页",
            "主页只保留倒计时和日历",
        ),
        fixes = listOf("日历多色、选中外框"),
    ),
    ChangelogEntry(
        version = "2.1.1",
        updates = listOf("线条日历图标"),
        fixes = listOf("旧图标辨识度不足"),
    ),
    ChangelogEntry(
        version = "2.1.0",
        updates = listOf(
            "iOS 液态玻璃与 MochiCounterText",
            "主题揭开过渡",
        ),
        fixes = listOf("数字和主题瞬切"),
    ),
    ChangelogEntry(
        version = "2.0.0",
        updates = listOf(
            "Compose + Mochi 原生重写",
            "Rust JNI 排班/倒计时核心",
            "旧 WebView 数据迁移",
        ),
        fixes = listOf("WebView 性能与旧界面问题"),
    ),
    ChangelogEntry(
        version = "1.3.2",
        updates = listOf("WebView 稳定基线版本"),
    ),
)
