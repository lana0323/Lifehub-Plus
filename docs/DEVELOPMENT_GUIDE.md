# LifeHub · Local AI Assistant

Android 个人生活管理项目：任务、日程、账单和使用时长统计。本地 AI 将自然语言路由到 Memo、Finance、Schedule 的可编辑表单，用户确认后写入原有数据库；Health 查询打开设备使用时长页面。

**默认使用 Ollama + Qwen3 4B 在自己的电脑运行，不需要 API Key，不产生云端模型调用费。** 模型运行仍消耗本机显存、内存、电力和首次下载流量。此项目支持同一设备上不同本地登录账号的数据隔离，定位为本地开发与作品展示，不宣称已提供云端多用户生产服务。

## 功能与架构

```mermaid
flowchart LR
    A[Android 输入] --> B[Python 本地后端]
    B --> C[Ollama / Qwen3 4B]
    C --> D[结构校验与日期计算]
    D --> E[可编辑任务草稿]
    E --> F[用户确认]
    F --> G[Room 事务与唯一索引]
    G --> H[原任务列表]
```

- AI 每次只处理一项任务；多任务要求拆分，不自动修改既有数据。
- 日期由后端根据服务器当前时间和用户 IANA 时区计算。含糊、过去或不支持的日期保留为空。
- 日期是日历日期，不是提醒时间。“下周五 / next Friday”指下一个周一开始的自然周的周五。
- 优先级沿用原任务模块：普通/低、重要/高、紧急。未提及优先级时由用户选择。
- 输入和未主动关闭的任务草稿可以恢复；失败可重试；三个写入模块的事务和唯一草稿 ID 防止重复确认创建。
- 模型不能直接写数据库，也不会读取已有账单、健康或日程数据。
- 草稿标题、备注、日期和优先级均可修改；也可修改原始描述重新生成。模型不可用时，可直接手动填写草稿。
- 生成后在独立弹窗中展示可编辑草稿，点击“确认创建”才写入；右上角 × 关闭草稿，有内容时确认是否放弃，退出后回到生成页，保留原始输入而不保留草稿。
- 带截止日期的任务也显示在日程对应日期下，标记为任务截止，点击打开同一条任务；不向日程库重复插入副本。没有日期的任务只显示在任务列表。
- 保存后弹窗关闭，显示成功提示和查看入口；“在日程中查看”直接定位到截止日期。
- 保存事务内读回标题、备注、日期、时区、优先级和草稿 ID，核对成功后显示任务编号与查看入口。相同草稿不同内容的重试不会覆盖或重复新增，而是显示已有任务。
- 对纯问候/感谢和否定紧急程度的常见表达增加保守校验；优先级不符合受支持词汇时留空让用户选择。这些规则不保证理解所有表达。
- 本地模型失败不会自动切换云服务。旧任务接口的云适配器仅在手动设置 `AI_PROVIDER=openai` **且** `ENABLE_PAID_AI=true` 后才能使用。

## 四模块联动

| 模块 | 示例 | 确认与结果 |
|---|---|---|
| Memo | 下周五前完成机器学习作业，优先级高 | 弹窗修改标题、备注、日期、优先级，确认后存入任务库；有日期则同步显示在日程 |
| Finance | 今天午餐花了28元，用支付宝支付 | 打开预填账单表单，补充或修改金额、分类、账户、日期和收支类型，确认后定位到对应月份 |
| Schedule | 明天下午3点开组会 | 打开可编辑日程表单，确认后定位到对应日期 |
| Health | 查看今天的手机使用时长 | 打开现有 Health 页面，由系统读取真实统计；需要使用情况访问权限，不写入新记录 |

生成后先展示 AI 自动识别的模块，可改选后重新提取，再进入对应草稿。模块下拉框有文字说明。手动填写在自动模式下先选择 Memo / Finance / Schedule / Health，指定模块时直接进入对应表单；Health 仅查看系统统计。旧的 Start a new request / Continue 按钮及绑定已删除，保存后仍能直接编辑输入并生成下一份草稿。

入口支持自动识别或手动指定模块，每次仅一项操作。`POST /v1/action-drafts` 始终使用本机 Ollama。金额暂按现有模块的人民币处理，明确外币请求会提示暂不支持。未识别出的必填字段需手动补充；Health AI 入口查询今天，Health 页面另可查看系统保留的最近 7 天前台事件，不生成虚构统计。Finance/Schedule 复用原生表单，只有点击确认才写入；新增草稿键通过保留旧数据的数据库迁移升级。Memo、Finance、Schedule 均通过草稿唯一 ID 和数据库事务防止同一草稿重复入库；重复确认返回首次记录，不覆盖首次确认的字段。新生成的草稿与普通手动记录不按内容去重。

## Finance 与视觉更新

Finance 支持月度/年度总览、分类占比环形图和每日/每月收支柱状图，点击日期标题可选择年份；左右箭头切换月份，收入/支出标签同时筛选分类图和记录。图表只使用该月实际账单，空月份显示空状态。收入为负数的既有存储约定在图表层转换为正向收入金额。分类图附金额/百分比文字，趋势图提供读屏数据说明。

在新增账单页面点击“添加分类”，输入 1–24 字名称；收入和支出分类分开保存，重复名称不允许，创建后自动选中。分类目录保存在本地偏好设置，不改动已有账单数据库 schema。AI 当前仍优先识别内置分类，自定义分类可在账单确认表单中手动选择。

视觉基于现有 Material 组件更新：暖白背景、墨绿总览卡、统一矢量线条图标、首页模块卡、AI 输入卡及 220ms 页面淡入。动画遵守系统“关闭动画”设置。登录/注册、首页、Finance、Memo、Schedule、Health、个人页、AI 及各模块添加/编辑表单均使用统一的配色、圆角卡片、输入框和按钮。Add Finance 使用可拖动的底部窗口：向下收起/关闭，未保存内容退出前需确认；长表单可滚动。没有新增付费图表依赖。

## 从 GitHub 克隆后运行

需要 Android Studio / Android SDK 34、JDK 21、Python 3.11+、Ollama，以及足够磁盘空间。默认 Qwen3 4B 模型约 2.5GB；短上下文适合从 8GB 显存显卡开始测试，速度以实测为准。

### 1. 准备本地模型

安装 [Ollama](https://ollama.com/download/windows)，关闭已运行的 Ollama 托盘进程后，在项目根目录 PowerShell 中运行：

```powershell
$env:OLLAMA_NO_CLOUD = '1'
$env:OLLAMA_HOST = '127.0.0.1:11434'
$env:OLLAMA_MODELS = "$PWD\.local\models"
ollama serve
```

另开终端下载模型（只下载一次）：

```powershell
ollama pull qwen3:4b
```

下载请求由上一步服务处理，权重存在 `.local/models`。也支持把官方便携版解压到 `.local/ollama`；把 `ollama` 命令换成 `.\.local\ollama\ollama.exe` 即可。`.local/` 整体被 Git 忽略。

### 2. 启动后端

```powershell
python -m pip install -r backend/requirements.txt
.\scripts\start-local.ps1
```

脚本固定使用本地模型并关闭付费开关。已有 Ollama 服务必须自行确保以 `OLLAMA_NO_CLOUD=1` 启动；脚本不能改变别的进程已经继承的环境变量。不使用脚本时可运行 `python backend/server.py`，默认同样连接 `http://127.0.0.1:11434` 的 `qwen3:4b`。

`backend/.env.example` 仅是说明，不会自动加载。后端默认只监听 `127.0.0.1:8080`，保持终端运行。首次加载模型可能较慢。

### 3. 运行 Android

Android Studio 打开项目，同步后运行 debug 版。模拟器默认访问 `http://10.0.2.2:8080/`，无需修改地址。

USB 真机调试可用 `adb reverse tcp:8080 tcp:8080`，并通过 `-PAI_BACKEND_URL=http://127.0.0.1:8080/` 构建。release 仅允许 HTTPS；当前开发后端不是公网生产服务。

进入 **AI**，输入“下周五前完成机器学习作业，优先级高”，生成草稿，核对字段，再确认创建。未配置或未启动本地模型会显示可恢复的错误，不会生成假结果。

## 测试与模型评测

```powershell
python -m unittest discover -s backend -v
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
# 启动模拟器后执行数据库回归及界面测试：
.\gradlew.bat :app:connectedDebugAndroidTest
# 后端运行时，包含真实 AI 端到端测试：
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.runLocalAi=true
# 停止后端后，单独验证断网恢复（不要和上一条同时运行）：
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lifeHub.ai.LocalAiFailureTest -Pandroid.testInstrumentationRunnerArguments.runLocalFailure=true
# 启动本地模型后执行真实推理，无 API 调用费：
python backend/evaluate.py --live
# 四模块字段回归评测（40 条，实际调用本地模型，保留失败样例）：
python backend/evaluate_actions.py --live
python backend/evaluate.py --live --cases backend/eval_holdout.json --output backend/eval-results-holdout.json
```

固定用例在 `backend/eval_cases.json`，包含中英文、时区边界、相对日期、跨年闰日、模糊表达、过去日期、多任务和非任务输入。报告分别计算字段正确率、整条任务正确率、错误数、P50/P95 延迟和 token 用量；本地 API 费用记为 0，不包括电费。默认输出 `backend/eval-results.json`，不会自动提交。

标题采用人工预设候选集合匹配，备注只校验必要片段；这是小规模回归基准，不是通用语义能力证明，也不是独立保留测试集。不要把 JSON 合法率写成任务理解准确率。真实验证记录见 [测试报告](TEST_REPORT.md)。

GitHub Actions 只运行离线后端测试及 Android 单测/编译，不下载模型、不调用付费 API。公开仓库运行仍以 GitHub 账户适用的 Actions 额度政策为准；本地执行上述命令不需要云端 CI。

命令行 `connected…` 测试自动使用独立包 `com.lifeHub.qa`，因为测试安装器会卸载被测应用。演示版仍为 `com.lifeHub`。Android Studio 单独运行测试时请配置 `-PisolatedTests=true`；不要在有真实数据的演示包上运行会卸载应用的测试。普通 `assembleDebug` 构建演示版。

## 数据库与可靠性

| 模块 | 存储 | 迁移与校验 |
|---|---|---|
| 任务 | Room，`memo_database` v2 | v1→v2 保留旧任务；新增截止日期、时区和唯一 `aiDraftId` |
| 账单 | Room，`finance.db` v5 | 金额按整数分存储；旧值保留，迁移核对行数/金额；时间及草稿唯一索引 |
| 日程 | SQLite，`schedule.db` v3 | v1→v2 只新增日期/时间索引，不再删表重建 |
| 本地账号 | SharedPreferences | 加盐 PBKDF2 校验；旧明文凭据在成功登录后迁移 |

Room 当前 schema 导出到 `app/schemas/`，便于审查后续迁移。账单 v1 的兼容迁移支持缺少 `account_type` 的旧结构；不匹配的未知结构应报错，不能静默清空。旧密码在下次成功登录前仍保留原格式，迁移不冒充全量历史清理。

详细审查和后续改进优先级见 [工程审查](ENGINEERING_REVIEW.md)。

Profile 的 Language 设置支持跟随系统、English 和简体中文，选择后应用会重建页面并保存语言偏好。

## GitHub 展示

- 提交：应用/后端源代码、数据库 schema、自动化测试、固定评测用例、经复核的测量结果和演示截图。
- 不提交：模型权重、运行程序、API 密钥、`.env`、个人数据库、IDE 配置、构建产物和签名密钥。
- 新用户按上述步骤下载模型，不需要仓库附带几 GB 权重。不能部署本地环境的读者也能通过截图和测试报告了解项目。
- 这是在既有 LifeHub 上扩展的个人作品，应区分原项目贡献与本次扩展；Ollama 本地推理不等同于自行训练模型。

## 技术参考

[Ollama 结构化输出](https://docs.ollama.com/capabilities/structured-outputs) · [Ollama 本地模式](https://docs.ollama.com/faq#how-do-i-disable-ollama-cloud-features) · [Qwen3 4B](https://ollama.com/library/qwen3:4b)

## 启动与确认可靠性

开屏使用 App 图标，人为等待为 500 ms（不代表总冷启动耗时）；暂停/销毁时移除跳转回调，旋转重建不重新等待 5 秒。中英文显示通过资源文件和显示层分类映射实现，已有分类/账户值和自定义名称不被翻译覆盖。

Finance v3→v4 和 Schedule v2→v3 新增可空 `aiDraftId` 唯一索引；旧记录保持 NULL。事务内查找、插入、读回。同一草稿重试返回首次记录和实际保存日期；手动 AI 表单在打开时生成 ID 并随页面状态保存。数据库记录被用户删除后，同一草稿可重新创建；这不是永久请求历史账本。重复生成会产生新草稿，不在此次内容去重范围内。

## 头像、确认状态与金额精度

头像会在后台复制并缩放到应用私有目录，保存与读取统一使用同一账号键；原照片或临时授权失效不会影响已导入的副本。可读取的旧头像会自动迁移，无法读取时提示重新选择。支持最大 20 MB 的图片，副本最长边 512 px。

AI 模块确认窗口使用可恢复的 DialogFragment；生成结果、草稿 ID 与已选模块在用户确认/取消前保持持久化。窗口重建与新 ViewModel 从持久状态恢复已有设备测试。取消保留原始输入。

Finance v5 用 `long amountMinor` 存储人民币分，新增输入范围 0.01–999,999,999.99，最多两位小数；收入为负、支出为正的约定保留。汇总先按分计算，仅图形比例和坐标转为浮点数。迁移旧 `double` 采用十进制 HALF_UP 逐笔舍入，保存 `legacyAmount` 原值、记录 ID、草稿键及自增序号，并在删除旧表前核对笔数与舍入后总额；异常或溢出导致事务回滚，不清空数据。

四模块字段评测见 [真实运行报告](evaluation-four-modules-fields.json)：40 条固定开发回归样例，全部检查项通过 28/40，分类仍是明显弱项。它不是独立保留测试集；标题只检查关键词，未列入的字段/完整语义不计分。失败和超时保留在分母中，按模块分别报告字段正确率、全部检查项通过率、P50/P95 延迟与 token 用量。


## AI 等待体验与本地账号隔离

- AI 请求保存在 Activity 级 ViewModel：切换 Home / AI、旋转或重建页面不会重发同一请求。输入和模块选择保持一致；返回 AI 后继续等待或审核结果。
- 等待时显示秒数，15 秒后提示本地模型可能较慢。可取消等待或转为手动填写，输入保留。取消会取消客户端请求并丢弃迟到结果；已经进入 Ollama 的推理可能继续运行，繁忙时会提示稍后重试。
- 进程退出后不自动重发请求：恢复输入，提示重新生成。确认入库期间不能用“取消等待”中断保存。
- Memo、Finance、Schedule 使用账号专属数据库；AI 输入/草稿、自定义财务分类使用账号专属偏好。数据库实例在创建时绑定账号，旧后台任务不会写入新账号的数据库。草稿 ID 的去重范围也随账号隔离。
- 升级首次运行时，把原共享数据固定归属给当时已登录的账号。若当时没有登录，原数据保留但不自动分给后来登录的用户。旧数据库无法证明更早的逐条归属，因此不猜测拆分历史数据。
- 退出登录会清空页面栈；旧会话的编辑页恢复时转到登录页。账号数据库、偏好及头像不参与 Android 自动云备份/设备迁移，防止只恢复业务数据而丢失账号身份。
- Health 是 Android 提供的设备使用统计，所有本地账号看到同一设备统计。Python 后端只负责无状态文本解析，不存业务数据；本次隔离不等于服务端认证或跨设备云同步。

验证与命令见 [账号隔离和等待体验测试](ACCOUNT_WAITING_TEST_REPORT.md)。
