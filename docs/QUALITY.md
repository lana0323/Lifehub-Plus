# 开发验证

## 验证与评估

| 验证范围 | 已记录结果 | 证据 |
|---|---|---|
| 本轮设备回归 | **20 项不同用例通过**，覆盖隔离、取消、重建及保存流程 | [测试过程与结果](ACCOUNT_WAITING_TEST_REPORT.md) |
| JVM 单元测试 | **17 项通过** | [测试报告](ACCOUNT_WAITING_TEST_REPORT.md) |
| 后端离线测试 | **44 项通过**（此前验证记录） | [开发测试记录](TEST_REPORT.md) |
| 本地模型字段评估 | **28/40** 样例的全部已列检查项通过 | [原始评估结果](evaluation-four-modules-fields.json) |

模型评估使用固定开发回归集，**不是独立保留集，也不是通用准确率**。标题使用关键词检查，未列字段和完整语义不计分；财务分类仍是弱项，因此保留人工审核。JSON 可解析不代表内容正确。

```powershell
# 离线验证：不需要模型或 API Key
python -m unittest discover -s backend -v
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug

# 已启动模拟器时运行本轮回归用例
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.lifeHub.ai.AccountAndWaitingTest,com.lifeHub.ai.PortfolioReliabilityTest,com.lifeHub.ai.DatabaseRegressionTest,com.lifeHub.ai.RepeatedReviewTest,com.lifeHub.ai.TaskConfirmationTest,com.lifeHub.finance.FinanceRefreshTest'

# 已启动本地模型及后端时执行字段评估
python backend/evaluate_actions.py --live
```

设备测试使用独立的 `com.lifeHub.qa` 包，避免卸载演示应用。GitHub Actions 配置仅运行离线测试与构建，不下载模型或调用模型 API。

