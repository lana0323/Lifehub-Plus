# LifeHub 本地运行验证

## 2026-09-30：头像、AI 状态恢复、金额精度与四模块评测

| 验证 | 指令/过程 | 最终结果 |
|---|---|---|
| 后端离线测试 | `python -m unittest discover -s backend -q`；新增评测器验证正确模块不能掩盖错误字段、缺失与 null 区分、失败计入分母 | 44/44 通过 |
| Android 单元测试 | `:app:testDebugUnitTest`；新增金额解析、HALF_UP 舍入、非法数值/溢出拒绝、连续一分钱账单汇总 | 17/17 通过 |
| PortfolioReliabilityTest | 头像副本在源文件删除后仍可恢复，坏图片不覆盖旧头像；模块确认在重建及新 ViewModel 中保留；v4→v5 金额/旧值/ID/草稿键/自增序号；非法旧值迁移回滚；过多小数不能入库 | 5/5 通过 |
| 数据库及原流程回归 | DatabaseRegressionTest 4 项、RepeatedReviewTest 2 项、TaskConfirmationTest 1 项、FinanceRefreshTest 2 项 | 9/9 通过；设备测试合计 14/14 |
| 真实本机模型评测 | `python backend/evaluate_actions.py --live`；40 条中英文固定开发用例，按模块/字段及全部检查项计分 | 28/40 全部检查项通过，3 条服务端校验错误；不是 40/40 |

设备回归命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.lifeHub.ai.PortfolioReliabilityTest,com.lifeHub.ai.DatabaseRegressionTest,com.lifeHub.ai.RepeatedReviewTest,com.lifeHub.ai.TaskConfirmationTest,com.lifeHub.finance.FinanceRefreshTest'
.\gradlew.bat :app:assembleDebug '-Pkotlin.incremental=false'
```

测试环境：Android 16 / API 36.1 模拟器，独立 QA 包。首次尝试时设备尚未启动完成；设备在线后首轮 12/13，失败是新迁移测试夹具的 SQL 占位符数量写错。修正夹具并补充金额表单测试后，最终完整重跑 14/14。这里没有把未连接设备或测试夹具错误当作业务测试通过。

交付核对：普通 Debug 包覆盖安装成功；用户模拟器实际旧库为 v3，已链式升级至 v5。对安装前后数据库副本逐条比较，1 条原记录保留，ID、分类、备注、时间、账户及原始金额一致，按分总额核对通过。无私人内容的核对结果见 `docs/money-migration-verification.json`；原始备份仅在被 Git 忽略的 `.local/verification/`。

金额规则：人民币分用 long 存储；新输入只接受 0.01–999,999,999.99，至多两位小数。旧数据按十进制 HALF_UP 逐笔换算，原始 double 存在 nullable `legacyAmount` 供核对，不参与新汇总。Room 迁移事务保留全部现有记录字段与自增序号，复制后核对笔数/总额，异常回滚。图表比例和绘制坐标仍可使用浮点数，账单及汇总金额使用整数分。

头像迁移只能导入仍有读取权限的旧图片；失去权限时保留原设置并提示重选。已导入副本不再依赖外部照片。AI 测试覆盖 Activity 重建与新的 ViewModel 从偏好存储恢复，未模拟所有强杀/断电时间点。

本次 Qwen3 4B 实测：Memo 7/10、Finance 3/10、Schedule 10/10、Health 5/6、澄清请求 3/4 满足全部检查项；整体 70%，P50 1596 ms、P95 3036 ms。Finance 多项失败是分类留空；还观察到模块混淆、日期/字段依据校验失败、多任务漏判。样例预期与实际输出均保留在 `docs/evaluation-four-modules-fields.json`，数据集为 `backend/eval_actions_fields.json`。标题用关键词检查，未列出的字段不计分；不是完整语义正确率，也不是独立测试集，不可宣称所有自然语言输入达到 70%。全部使用本机推理，模型 API 调用费为 0。


## 2026-09-30：Health 每日统计修复

问题：旧版逐日调用 `queryUsageStats(INTERVAL_BEST, start, end)`，却把可能扩展到整段时间范围的汇总值直接当作当天时长；按包名最后一段匹配还可能混淆不同应用。今日概览另用 DAILY 汇总、旧 AI 摘要查询过去 24 小时且只统计 LifeHub，读不到时还会回退到两小时，统计口径不一致。

修复：统一读取 `queryEvents`，按完整包名、活动类、设备时区的午夜边界计算前台区间；切换应用不重叠累计，锁屏/息屏关闭区间，重启时丢弃无法确定结束时间的开放区间。今日摘要、单应用图表、全部应用图表和旧 AI Health 摘要共享计算。查询在后台线程执行，页面退出时不回填过期结果。标题改成“最近 7 天”，七行全部显示日期，无事件记录的日期明确显示“无记录”；权限不足单独提示，不伪装为零时长。

| 验证 | 过程 | 结果 |
|---|---|---|
| UsageTimelineTest | 不同日期不同用量、跨午夜拆分、范围裁剪、进行中会话、过期暂停事件、App 切换、息屏/锁屏/后台服务、正常与异常重启、同后缀包名、重复事件、缺失记录、夏令时 23/25 小时日期 | 新增 8/8 通过 |
| HealthUsageTest | 拒绝权限时隐藏统计并阻止旧缓存/默认两小时；真实 Settings 前台会话；七个日期标签、选择 App 与页面重建 | 3/3 通过 |
| 实际计时 | 打开 Settings 约 3.5 秒后回桌面，比较查询前后该包的今日时长增量 | 记录增量 3523 ms；未累计回桌面后的等待 |
| 原有 JVM 回归 | 日期、Finance 图表、密码哈希及原有示例 | 6/6 通过；本轮 JVM 合计 14/14 |
| 安装与画面 | 普通 Debug 包覆盖安装，进入 Health 概览及最近 7 天详情 | 成功；保留用户数据，截图见下 |

```powershell
.\gradlew.bat :app:testDebugUnitTest '-Pkotlin.incremental=false'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.lifeHub.usage.HealthUsageTest'
.\gradlew.bat :app:assembleDebug '-Pkotlin.incremental=false'
```

实际使用本机缓存 Gradle 8.13，Android 16 / API 36.1 模拟器，设备测试使用 `com.lifeHub.qa`。编译时发现公开 API 不提供活动实例 ID，已改为活动类匹配；测试文件的一处 Kotlin 集合属性拼写已修复，以上为最终通过结果。

截图：`docs/screenshots/health-today-fixed.png`、`docs/screenshots/health-week-fixed.png`。

统计边界：这是**本设备已记录的前台应用时长**，不是触摸操作时长，也不包含后台播放音频。模拟器亮屏、应用一直留在前台也会累计，不能当作电脑或真实手机的使用量。设备时区决定“今天”，与电脑时区可不同。系统只保留有限天数的事件；无事件、被裁剪的历史或系统漏报不能恢复为完整七天，不能与系统数字健康宣称完全一致。多窗口采用最近恢复到前台的应用归属、避免总量重复累加；同包同类多实例无法区分。未做实体手机或所有厂商系统验证，不持久收集应用使用历史。

依据：[Android UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)、[UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event)。


## 2026-09-30：启动、中英文提示、AI 防重复入库

本轮最终结果：16 项设备用例均已有通过记录，6 项 JVM 单元测试通过。首轮设备测试 15/16；启动测试因 ActivityScenario 返回时短开屏已结束而失败，改为生命周期监听触发真实后台/恢复操作后，该测试类 2/2 通过。最终结果来自这两轮，不是一次全量 16/16 重跑。

| 测试类 | 过程 | 结果 |
|---|---|---|
| DatabaseRegressionTest | Finance v1/v2/v3 升级 v4；Schedule 升级 v3；旧记录保留、日期边界与密码迁移 | 4/4 |
| IdempotentRecordsTest | 8 次并发同键提交；数据库关闭重开后重试；新草稿与手工记录独立；非法写入回滚后重试 | 3/3 |
| RepeatedReviewTest | Finance/Schedule 确认前无记录；表单重建、编辑保存、同一草稿再次确认；读回核对原字段与唯一记录 | 2/2 |
| TaskConfirmationTest | Memo 编辑后确认，确认前不入库、确认后读回 | 1/1 |
| RefinementTest | 日期/时间弹窗对比度、Finance 年份切换、分类选择、语言切换与重建 | 4/4 |
| StartupAndLocalizationTest | 开屏转后台不跳转、恢复前台只跳一次且无固定 5 秒等待；中英文显示与分类存储值分离 | 2/2（修正测试时序后） |
| JVM 单测 | 日期解析、图表月年聚合、密码哈希和原有示例测试 | 6/6 |

可复现指令（需要 Android SDK、JDK 与已启动设备）：

```powershell
.\gradlew.bat :app:testDebugUnitTest '-Pkotlin.incremental=false'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.lifeHub.ai.DatabaseRegressionTest,com.lifeHub.ai.IdempotentRecordsTest,com.lifeHub.ai.RepeatedReviewTest,com.lifeHub.ai.TaskConfirmationTest,com.lifeHub.ui.RefinementTest,com.lifeHub.ai.StartupAndLocalizationTest'
# 首轮启动测试的定向复测
.\gradlew.bat :app:connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.lifeHub.ai.StartupAndLocalizationTest'
# 单独构建用户安装包，避免使用设备测试的 QA 包名
.\gradlew.bat :app:assembleDebug '-Pkotlin.incremental=false'
```

实际使用本机缓存的 Gradle 8.13 执行上述任务。设备测试使用独立 `com.lifeHub.qa` 包；没有清空用户应用数据。环境为 Android 16 / API 36.1 模拟器、软件渲染、关闭音频。

边界：500 ms 是主动开屏等待，不是完整冷启动耗时承诺。已测试数据库重开、页面重建和再次打开表单，未模拟所有进程被杀或断电场景。去重针对同一草稿 ID；重新生成的草稿是新请求，删除记录后也不保留永久去重凭证。Health 是查询/导航，无业务记录需要去重。模型生成内容、用户自定义分类和历史笔记不自动翻译。本轮未调用收费模型。

交付检查：普通 Debug 包构建成功，`adb install -r` 覆盖安装成功；冷启动命令返回 Status: ok，随后截图确认进入用户 App 首页（`docs/screenshots/reliability-final.png`）。安装未执行卸载或清空数据。


测试日期：2026-09-29（America/Los_Angeles）。范围为当前工作区版本，尚未提交 Git commit。

结论：已验证的核心业务流程、持久化及真实 AI 创建链路通过；不能宣称整个应用在所有设备、所有输入下都符合预期。下文保留初轮结果；后续确认流程与模型修复结果见本节。


## UI 精简、语言与年度收支

- 日期弹窗采用深绿强调色，提高头部与选中日期对比度；主页面隐藏重复 Home/AI/Profile 标题，日程标题简化。
- 模块与表单选择器采用圆角单选弹窗，保留已选标记；AI 模块标签缩为标题，输入提示覆盖四个模块。
- Memo 完成状态与优先级分组；Finance 分类旁使用加号按钮。
- Profile 支持跟随系统、English、简体中文，使用 AndroidX 应用语言保存机制。
- Finance 月/年切换，点日期标题选择年份；年视图查询全年记录、分类占比及 12 个月收支，不改数据库 schema。

| 验证 | 结果 |
|---|---|
| RefinementTest：年度切换、前一年、页面重建、年份选择；分类弹窗选项；中文切换及重建保留；日期/时间弹窗按钮对比度 | 4/4 通过 |
| FinanceRefreshTest：分类增加/校验/持久化、月度图表回归 | 2/2 通过 |
| TaskConfirmationTest：编辑、确认前不入库、确认后读回 | 1/1 通过 |
| FinanceChartDataTest：月度聚合/空状态、年度 12 个月分桶与相邻年份排除 | 3/3 通过 |

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.lifeHub.ui.RefinementTest,com.lifeHub.finance.FinanceRefreshTest,com.lifeHub.ai.TaskConfirmationTest'
```

首轮设备测试 5/6，通过语言切换后暴露旧测试硬编码英文 Memo；改为选项数据定位，并修正测试结束后的语言恢复，复测 6/6，补充日期与时间弹窗按钮颜色检查后最终 7/7。JVM 单元测试共 6/6 通过。实际验证环境为 Android 16 模拟器；未实测低版本应用语言恢复或完整中文翻译质量。

## 全页面统一 UI 与记账底部窗口

范围：登录、注册、启动页、首页、Memo/Schedule/Finance/Health、个人页、AI 与添加/编辑表单；共享配色、输入框、按钮、卡片和线条图标。长表单支持滚动。Finance 使用 BottomSheetBehavior，向下可收起/关闭；内容未保存时需确认退出，旋转后保留输入。Health 未授权时显示权限说明，不再显示空白统计卡片。

最终功能回归：**10/10 通过**（Android 16 / API 36.1，软件渲染冷启动，截图导出关闭）。

| 测试类 | 过程 | 最终结果 |
|---|---|---|
| UnifiedUiTest | 底部窗口隐藏保护、继续编辑、旋转保留金额；逐页打开巡检 | 2/2 |
| AppFlowTest | 注册登录/错误密码，Memo 和日程创建编辑删除，账单零金额拒绝/合法金额读回，导航和 AI 输入恢复 | 5/5 |
| FinanceRefreshTest | 图表月份/收入标签、自定义分类校验及重建持久化 | 2/2 |
| TaskConfirmationTest | 编辑草稿、确认前不入库、确认后读回修改字段 | 1/1 |

复现命令（Windows；先启动 Android 模拟器）：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.lifeHub.ui.UnifiedUiTest,com.lifeHub.ai.AppFlowTest,com.lifeHub.finance.FinanceRefreshTest,com.lifeHub.ai.TaskConfirmationTest'
.\gradlew.bat :app:assembleDebug '-Pkotlin.incremental=false'
```

连接测试自动使用隔离包 `com.lifeHub.qa`，普通 assembleDebug 使用 `com.lifeHub`。界面截图取自隔离测试环境；不包含用户记录。首轮有一项测试在底部窗口动画完成前查询提示框而失败，改为有超时的等待后复测。其后一次完整回归因模拟器系统崩溃中止（INSTRUMENTATION_ABORTED: System has crashed）；重启后首次过早运行又被用户存储尚未解锁阻止，待启动完成后重新执行。自动测试将窗口状态设为隐藏验证关闭保护；真实手势另行检查，不将两者混同。截图导出为可选参数 `-Pandroid.testInstrumentationRunnerArguments.captureScreenshots=true`，默认页面巡检只打开页面；批量截图期间出现模拟器卡住后，改用软件渲染冷启动，再执行功能回归。

截图：（开发过程截图未纳入本次公开快照）、（开发过程截图未纳入本次公开快照）、[AI](screenshots/ui-ai.png)、（开发过程截图未纳入本次公开快照）、（开发过程截图未纳入本次公开快照）、（开发过程截图未纳入本次公开快照）。

验证限制：仅 Android 16 模拟器；未覆盖全部屏幕尺寸、大字体及完整深色模式。此前模型评测结果未因 UI 更新而重新测量。


## Finance 图表、分类与视觉更新（最新）

| 指令/测试 | 过程 | 结果 |
|---|---|---|
| `:app:testDebugUnitTest` | 图表按月过滤、每日汇总、收入负数转正、分类排序、空月份，及原日期/密码测试 | 5/5 通过 |
| `FinanceRefreshTest` | 添加分类、自动选中、重复/空白/超长校验、收入支出分离、页面重建；月份前后切换、收入标签、图表显示 | 2/2 通过 |
| `FourModuleFlowTest`（未开模型选项） | 草稿关闭、四个手动入口、记账确认入库、日程确认入库 | 4 项通过；2 项真实模型测试按配置跳过 |
| 图表真实渲染专项 | 独立 QA 库写入合成账单 25/75 支出与 200 收入，显示分类图与每日图；结束后清理测试行 | 通过 |

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.lifeHub.finance.FinanceRefreshTest,com.lifeHub.ai.FourModuleFlowTest'
```

首次界面测试误将总览中的 Income 文字与收入标签同时匹配；测试定位改为 TabLayout 内的标签后通过，未改变业务逻辑。新分类保存在独立偏好设置，原 Finance 数据库 schema 不变。图表为原生 Canvas，包含分类金额/占比文字和每日金额读屏说明；不引入付费或联网图表组件。

图表截图来自隔离 QA 库中的合成数据：（开发过程截图未纳入本次公开快照）、[每日收支](screenshots/finance-charts-qa.png)。这些示例账单不会加入演示版用户数据库。

设计范围为首页、Finance、AI 输入卡、全局配色/导航图标和 220ms 页面过渡。系统关闭动画时跳过过渡。未验证全部机型、大字体、完整深色模式和大量记录性能；现有账单金额仍使用 double。模型提取仍使用内置分类，用户新增分类可在确认账单时手动选择。

## 分类与手动入口修正（追加）

- 从布局中删除 Start a new request 按钮及代码绑定；Continue 也没有保留为隐藏控件。保存后可以直接修改原始输入继续生成。
- 模块选择前增加说明；默认自动分类，生成后展示预选模块，用户改选时按所选模块重新提取，再进入可编辑草稿。账单分类仍可在账单表单中修改。
- 自动模式下手动填写先选 Memo、Finance、Schedule 或 Health；指定模块时直接进入对应页面。Health 是查看入口，不创建伪造健康记录。
- TODAY 大小写统一匹配原文；模型漏提取单一明确 today/今天 等日期时，后端按请求时区补解析。含否定、多个日期、或者等不确定描述不使用该补全规则。

后端命令 `python -m unittest discover -s backend -p 'test_*.py'`：**40/40 通过**。新增测试覆盖模型漏提取 today、三个写入模块、洛杉矶/UTC 日期边界、大小写、含糊日期和手动纠正模块。

设备测试：组合回归 **7/7** 通过，另单独执行真实模型模块纠正专项 **1/1** 通过，无跳过。覆盖四模块手动入口、TODAY 自动填日期、先自动识别 Schedule 再手动改为 Memo、草稿编辑和确认入库；测试包仍与演示包隔离。沿用下文 Gradle 命令可运行当前全部 8 项。

## 四模块与关闭交互（此前更新）

本节取代下文历史版本的“暂存并返回/继续编辑”交互。任务草稿现在使用右上角 ×：空草稿直接关闭，有内容时提示放弃并退出或返回修改；放弃后不入库、不显示 Continue，保留原始请求并回到 Generate。仅用户未主动放弃的任务草稿支持恢复。

新接口 `/v1/action-drafts` 使用本地 Ollama，将单一请求路由至 Memo、Finance、Schedule 或 Health。前三个模块必须在可编辑表单中确认后写入；Health 按用户选择打开现有今日手机使用统计页，需要系统使用情况访问权限，不新增健康记录。Finance 保存后定位账单月份，Schedule 保存后定位日期。新接口没有云模型回退。

| 指令或测试类 | 过程 | 结果 |
|---|---|---|
| `python -m unittest discover -s backend -p 'test_*.py'` | 字段校验、日期时区、四模块、缺失金额/账户、外币拒绝、历史使用时长提示、HTTP 错误及连续请求 | 38/38 通过 |
| `python backend/evaluate_actions.py` | 固定日期与时区，8 条中英文输入调用本地 Qwen3 4B，比较预期模块并保存完整字段 | 8/8 路由匹配；不是通用内容准确率 |

最终组合回归 **6/6 通过，无跳过**，运行于独立测试包 `com.lifeHub.qa`。此后补充兼容旧版暂存草稿的恢复：升级后自动打开仍未保存的草稿，避免没有 Continue 入口时卡住；该兼容项通过演示包实际界面检查，未再重跑整组测试。最终演示版 Debug 构建、覆盖安装成功。

模拟器专项类为 `FourModuleFlowTest`、`LocalAiFlowTest`、`TaskConfirmationTest`。前者覆盖 × 关闭、退出不保留草稿、记账修改后入库、日程确认前无记录及确认后读回、真实模型连续跳转 Finance/Schedule/Health；后两者覆盖 Memo 真实生成、重建、修改、确认、读回及日历显示。

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.lifeHub.ai.FourModuleFlowTest,com.lifeHub.ai.LocalAiFlowTest,com.lifeHub.ai.TaskConfirmationTest' '-Pandroid.testInstrumentationRunnerArguments.runLocalAi=true'
.\gradlew.bat :app:assembleDebug '-Pkotlin.incremental=false'
```

中途发现连续 HTTP 请求复用已关闭连接导致 EOF，后端增加显式 `Connection: close` 并补回归测试。任务优先级选择也出现状态更新时序问题，改为由选择回调独立更新优先级，文本编辑不覆盖它。保留这些发现，避免把首轮失败写成始终通过。

限制：只验证一台 Android 16 模拟器；未证明真实手机统计准确性。Finance/Schedule 当前复用既有存储，没有 Memo 的跨进程草稿唯一键保障。旧任务接口的 20+10 条历史结果不能直接当成新四模块提示词的评测成绩。[四模块样例输出](evaluation-four-modules.json) 为合成数据，可公开展示。

## 后续改进：编辑、最终确认与保存核对

### UI 与日程显示修复（更新）

之前 AI 只写入任务库，日程页只读取日程库，因此不会显示 AI 任务。现改为日程页同时观察截止日期对应的任务，标记“任务截止”，点击打开原任务，不复制到第二个数据库。没有日期的任务仍只在任务列表。

主页面缩短说明、降低手动入口的视觉强调；生成后打开可编辑草稿弹窗。弹窗的“确认创建”是唯一保存动作，不再叠加第二个确认弹窗。“暂存并返回”保留编辑而不入库。保存后关闭弹窗，显示成功 Toast 和常驻成功说明，提供任务详情及定位日程入口。

本轮 11 项回归首次执行 10 项通过，草稿弹窗中的优先级控件出现焦点问题；改为选择对话框后，该流程通过。异步生成测试另补了明确等待弹窗的窗口匹配。最终两个专项均通过：手动草稿暂存/继续编辑/保存后在对应日期日程中找到并打开任务，以及真实模型生成/页面重建/保存/详情。原登录、任务、日程、记账、导航和数据库防重测试已在本轮首组通过。

另在测试日志发现安装器会卸载被测包。命令行 connected 测试现自动使用 `com.lifeHub.qa`，避免后续影响演示版 `com.lifeHub`；隔离包上再次执行草稿暂存、确认、日程查看测试，1/1 通过。历史记录中“清理自己的测试行”只描述测试代码，不能涵盖此前安装器的卸载行为；此前模拟器中的演示数据可能受其影响。

同日补充实现并验证：

- AI 草稿可以修改标题、备注、截止日期和优先级，也可返回修改原始输入重新生成；无有效 AI 结果时可手动填写草稿。
- 最终确认窗口展示完整内容、时区和未保存状态。取消或返回修改不写数据库，只有确认创建才提交。
- 数据库事务内读回并比较所有业务字段。新插入内容不符会回滚；同一草稿已保存但内容不同则返回已有任务，界面明确提示冲突并提供查看/编辑入口，不重复创建或覆盖。
- 保存成功消息包含任务编号，只有核对成功后显示；失败保留草稿。未选优先级、空标题或非法日期不能提交。
- 纯问候/感谢、否定紧急程度和缺少支持词汇的优先级输出增加服务端保守校验；未知表达仍需用户确认。这是有限词汇规则，不是完整自然语言解析器。

验证结果：后端 **27/27**；本次针对改动执行的模拟器测试 **6/6**（无跳过）。包括手动草稿取消/返回修改、修改后逐字段读回、真实本地 AI 最终确认、10 次并发确认防重、不同内容重试冲突、注入数据库字段变化后事务回滚及任务旧库迁移。最终 Android debug 构建通过。原其他模块测试结果在下文保留，未宣称本轮全部重新运行。

最终真实模型回归：[原 20 条](evaluation-qwen3-4b-grounded.json) **20/20**，P50/P95 为 453/578 ms；[追加 10 条复测](evaluation-qwen3-4b-grounded-extra.json) **10/10**，454/578 ms。全部本机推理，API 费用为 0。这 10 条现在已参与修复，不能继续作为独立保留测试集报告准确率。

中间复测也暴露过新的多任务误判和一次未指定优先级却填为普通。最终保留此前提示词、加入后处理校验后上述样例通过。模型即使设置 seed/temperature 也不应视为保证永远相同；测试集以外仍可能遗漏或误解。因此可编辑草稿与人工确认是必要产品流程，不能移除。后续仍需新的未参与调试样例和真实设备测试。

## 初轮测试记录（历史结果）

## 环境

- Windows、JDK 21、Gradle 8.13、Python 3.11。
- Android 模拟器：Medium_Phone_API_36.1，Android 16；测试实例禁用音频。
- 本机 NVIDIA RTX 3060 Ti 8GB，Ollama 0.35.0，Qwen3 4B / Q4_K_M。
- 模型 digest：`359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7`。
- `OLLAMA_NO_CLOUD=1`，`AI_PROVIDER=ollama`，`ENABLE_PAID_AI=false`；未调用收费 API。

## 自动化结果

| 检查 | 结果 | 实际覆盖 |
|---|---|---|
| Python unittest | 24 / 24 通过 | 日期/时区、缺失及非法字段、模型拒绝/超时、默认本地与付费开关、HTTP 错误/并发限制/恢复 |
| Android JVM 单测 | 3 / 3 通过 | 日期合法性、密码哈希及原有算术样例 |
| Android 设备测试第一组 | 13 / 13 通过，无跳过 | 下列业务/迁移/AI 流程 |
| 后端停止时设备测试 | 1 / 1 通过 | 显示网络错误、输入保留、页面重建后保留、重试按钮可用、未新增任务 |
| Debug 编译及测试 APK | 通过 | 实际安装到模拟器后执行测试 |
| Android Lint Debug | 0 errors，178 warnings，1 hint | 主要为未用资源、依赖、硬编码文本、自动填充和界面可访问性建议；未全部消除 |

设备测试验证：

1. 注册新账号、拒绝错误密码、正确登录；旧明文密码仅在成功验证后迁移。
2. 手动任务空标题校验、创建、编辑、重新打开、删除，截止日期保存。
3. 日程创建、编辑及删除。
4. 账单零金额拒绝、合法金额保存并读回。
5. 健康页面、AI 页面、个人页面导航；AI 输入在 Activity 重建后恢复。
6. 任务 v1→v2、账单兼容 v1/v2→v3、日程 v1→v2 数据保留；账单时间查询不包含下一月起点。
7. 同一 AI 草稿 10 次并发确认只写一条记录。
8. Android → Python HTTP → 本地 Ollama → 草稿展示 → 修改标题 → Activity 重建 → 确认写入 Room → 打开任务详情；确认前数据库没有该草稿，保存后确认按钮禁用。

测试仅创建合成记录并清理对应记录；隔离迁移测试使用临时数据库。页面重建测试不等于设备断电或所有进程终止场景的验证。

## 真实模型评测

| 数据集 | 整条正确 | 状态 | 标题 | 日期 | 优先级 | 延迟 P50 / P95 |
|---|---|---|---|---|---|---|
| 初始 20 条回归集 | 8/20 | 100% | 94.1% | 100% | 29.4% | 469 / 7500 ms |
| 修正提示词后的同一回归集 | 20/20 | 100% | 100% | 100% | 100% | 422 / 485 ms |
| 提示词冻结后新增 10 条样例，首次运行 | 8/10 | 90% | 100% | 100% | 87.5% | 469 / 735 ms |

所有三组均无接口错误，本地模型 API 费用为 0；不包括电费。延迟来自后端计时，不含客户端渲染。初始运行包含模型冷加载，后续为热模型，不能把差异全归因于提示词优化。字段分母：回归集任务字段 17 条，追加集 8 条；状态字段覆盖所有样例。备注检查均通过，但只检查预设必要片段，不能证明所有备注无遗漏。

主要修正是明确要求未提优先级时返回 JSON null，并给出少量结构化示例、要求标题去除截止日期短语。原回归集参与了调整，20/20 不是泛化准确率。追加 10 条由项目内编写，并非第三方盲测；其结果没有用于再次调参。

追加样例的已知失败：

- `Thank you!` 被返回为任务草稿，预期应要求澄清。
- `明天或者周末整理书架，不着急` 的模糊日期正确留空，但优先级返回普通；本评测按保守约定要求留空，因为“不紧急”不能唯一确定普通或重要。

这两类输入需要用户核对草稿；模型输出从不自动入库。原始结果：[初始回归](evaluation-qwen3-4b-baseline.json)、[修正后回归](evaluation-qwen3-4b.json)、[新增样例首次结果](evaluation-qwen3-4b-holdout.json)。全部为合成输入，无个人任务数据。

## 本轮发现并修正

- 原仪器测试断言旧包名 `com.example.cw`，改为实际应用包名 `com.lifeHub`。
- 模型把缺失优先级填为普通；调整提示词后原回归集通过，新增集的语义边界如上保留。
- 健康页权限判断依据“最近有无记录”不可靠，改为权限状态检查；未授权时显示说明，由用户点击进入设置。
- 中断留下的损坏模型下载和构建产物已隔离/重新生成；模型下载完成，干净构建后执行测试。

## 尚未验证或尚未解决

- 未覆盖真实手机、不同 Android 版本、中文系统完整界面、所有屏幕尺寸及完整无障碍体验。
- 未验证真实手机健康统计准确度、头像跨重启持久化、后台长时间存活、进程被杀后所有表单恢复及长期大数据性能。
- 账号共享业务数据库、账单 double 金额、日程主线程 SQL 等结构限制见 [工程审查](ENGINEERING_REVIEW.md)。
- Lint 警告仍在；GitHub Actions 配置已提供，但未在远端实际运行，尚未验证全新机器从零克隆构建。
- 此轮是功能回归与小规模模型评测，不是上线验收或安全审计。复现命令见根目录 README。
