# AI 等待体验与本地账号隔离测试

本次使用 Android Emulator API 36.1，测试包 `com.lifeHub.qa`，正式演示包 `com.lifeHub`。所有模型/网络请求使用本地方案或受控测试替身，不产生云模型调用费。

| 检查 | 操作/过程 | 结果 |
|---|---|---|
| 账号数据库隔离 | A 建立 Memo / Finance / Schedule；B 初始为空；B 使用相同数字 ID、草稿键建记录并删改；再回 A | 通过，A 保留原数据 |
| 后台延迟写入 | 切到 B 后，先前绑定 A 的 Memo DAO 完成写入 | 通过，只写入 A |
| 草稿与分类隔离 | A 保存 AI 输入和自定义分类；切 B 检查，再切 A | 通过 |
| 旧数据归属 | 模拟升级时已登录与未登录；之后切换账号 | 通过，归属只冻结一次；未登录历史不分给后来用户 |
| 旧编辑页 | 带旧 session 的账单页面在新账号下启动 | 通过，转到登录页 |
| 取消与重试 | 请求 1 取消后发请求 2，再返回请求 1 的迟到结果 | 通过，输入保留，迟到结果不覆盖请求 2 |
| 页面切换与重建 | 请求期间 AI → Home，重建 Activity | 通过，保持同一 ViewModel、输入、模块、请求，不重发 |
| 账号切换时 AI 返回 | A 生成中切 B，然后返回 A 响应 | 通过，B 草稿为空，A 持久状态不被旧响应改写 |
| 中断恢复 | 读取正在生成的持久状态创建新 ViewModel | 通过，恢复输入，停止加载，提示重新生成；非模拟器强杀进程测试 |
| 原功能回归 | PortfolioReliabilityTest 5、DatabaseRegressionTest 4、RepeatedReviewTest 2、TaskConfirmationTest 1、FinanceRefreshTest 2 | 14 项通过 |
| JVM 单元测试 | 日期、金额、Health 时间线等现有测试 | 17 项通过，0 失败/跳过 |
| 正式包升级 | install -r 前备份，启动后核对归属及原数据库文件 | 8 个数据库及辅助文件完全一致，归属匹配原登录账号 |

## 可复现命令

先设置 `ANDROID_HOME` 并启动模拟器。在项目根目录运行（本机也可使用缓存中的 Gradle 8.13）：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest :app:testDebugUnitTest '-Pkotlin.incremental=false' '-Pandroid.testInstrumentationRunnerArguments.class=com.lifeHub.ai.AccountAndWaitingTest,com.lifeHub.ai.PortfolioReliabilityTest,com.lifeHub.ai.DatabaseRegressionTest,com.lifeHub.ai.RepeatedReviewTest,com.lifeHub.ai.TaskConfirmationTest,com.lifeHub.finance.FinanceRefreshTest'
.\gradlew.bat :app:assembleDebug '-Pkotlin.incremental=false'
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.lifeHub/.main.ui.OpenPage
```

实际分两轮执行：第一轮原 4 项新测试 + 14 项回归 = 18/18；增加边界测试后再跑 AccountAndWaitingTest = 6/6。共 20 项不同设备用例，重复执行的 4 项不重复计数。JVM 17/17；正式包构建成功。

测试源码：`app/src/androidTest/java/com/lifeHub/ai/AccountAndWaitingTest.kt`。升级校验公开摘要：`account-isolation-upgrade-verification.json`。私人备份位于 Git 忽略的 `.local/verification/account-isolation/`，不上传。

## 范围说明

取消是取消客户端等待并拒收旧结果，不保证已进入本地 Ollama 的推理立即终止。隔离针对同一设备上的本地账号业务记录；Health 仍是设备统计。Python 后端无状态，不保存业务记录；没有新增云端认证或跨设备同步。旧共享数据库的更早逐条归属无法追溯，所以升级时归属当前账号，不猜测拆分。
