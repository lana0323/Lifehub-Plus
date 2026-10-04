# Lifehub Plus 1.1.3 unseen holdout

First pass on 120 newly authored inputs: 60 Chinese and 60 English, 15 per workflow per language. The existing 240-case regression suite was preserved and was not rerun or combined with this score.

The inputs, expected answers, scoring code, model configuration and production source hashes were frozen before preparation or inference. One attempt per input, fresh conversation, no retries, no prompt/rule edits, and no result-driven changes to expected answers. Errors stay in the denominator.

This holdout was authored internally by the same development assistant. Available prior fixtures were checked for exact and lexical overlap before freezing. It was not used for application tuning before this first pass; it is not an independently collected benchmark, and unseen here does not mean absent from model pretraining.

## Results

| Metric | First pass |
|---|---|
| Final pipeline routing | 104/120 (86.67%) |
| Required fields (micro average) | 197/216 (91.20%) |
| All annotated checks per input | 85/120 (70.83%) |
| Supported draft checks | 72/96 (75.00%) |
| Expected clarification handling | 13/24 (54.17%) |
| Date fields, including annotated blanks | 63/72 (87.50%) |
| Raw model routing, invoked subset only | 89/111 (80.18%) |

| Language | Routing | Required fields | All checks |
|---|---|---|---|
| Chinese | 50/60 (83.33%) | 98/108 (90.74%) | 40/60 (66.67%) |
| English | 54/60 (90.00%) | 99/108 (91.67%) | 45/60 (75.00%) |

| Workflow | Routing | All checks |
|---|---|---|
| Memo | 27/30 (90.00%) | 18/30 (60.00%) |
| Finance | 26/30 (86.67%) | 21/30 (70.00%) |
| Schedule | 26/30 (86.67%) | 21/30 (70.00%) |
| Health | 25/30 (83.33%) | 25/30 (83.33%) |

## Interpretation

- Qwen2.5-1.5B-Instruct int8 ekv4096, LiteRT-LM 0.10.2 on Windows CPU. The model, prompt, Unicode-safe transport and Python adapter are shared with the mobile implementation. No latency statistics are reported.
- The final pipeline includes deterministic application rules. Raw model routing is reported separately and excludes inputs handled before model invocation.
- 96 requests are expected to produce reviewable drafts and 24 to require clarification. An annotated blank is correct when information is missing, ambiguous or invalid. A correctly incomplete draft still needs user input before saving.
- Titles use frozen keyword alternatives, so passing is not proof of perfect wording or full semantic equivalence. Field checks cover only annotated fields.
- These scores measure draft preparation and validation. They do not measure Android inference, user confirmation, database writes, or real Health usage-duration accuracy. No new device/reliability checks were run.
- 111 model invocations, 9 preflight responses, 2 pipeline errors, 10 expected-clarification requests returned as drafts. All 120 inputs remain accounted for.
- Once these results are used to modify the application, this set becomes additional regression material and must not be presented as another unseen first pass.

## Failed cases

| ID | Input | Failed checks |
|---|---|---|
| U5-EN-FINANCE-13 | Correct my last grocery transaction from 48 yuan to 43 yuan. | status, module |
| U5-ZH-SCHEDULE-08 | 把“Friday 摄影展”加入明天上午十点的日程。 | status, module, title, date, time (error: invalid_model_output) |
| U5-ZH-MEMO-15 | 把我最紧急的三条待办找出来给我看。 | status, module |
| U5-EN-SCHEDULE-01 | Calendar entry: orchestra rehearsal on November 27 at 6:20 pm. | date |
| U5-EN-HEALTH-09 | Where can I check my screeen time for today? Open that page. | status, module |
| U5-EN-SCHEDULE-14 | Two separate calendar items, please: a ferry departure tomorrow at 8 am, then a parent-teacher conference the day after tomorrow at 3 pm. | status, module (error: JSONDecodeError) |
| U5-ZH-FINANCE-01 | 今天早餐豆浆和烧饼一共花了14.50元，支付宝付的，记一笔餐饮支出。 | title |
| U5-EN-MEMO-11 | Task for tomorrow: pack the pottery pieces for storage. | title |
| U5-ZH-HEALTH-05 | 今天一整天花了多少时间在这部手机的应用上？ | module |
| U5-EN-MEMO-06 | Don’t delete any files. Add a task called delete duplicate microscope images. | title |
| U5-ZH-FINANCE-10 | 今天午饭总共花了28元，不是收入，是用现金付的餐饮支出。 | kind |
| U5-ZH-MEMO-04 | 在 memo 记一下：11月30日之前退还实验室门禁卡。 | date |
| U5-EN-FINANCE-15 | I never bought the 260-yuan desk lamp; the payment was rejected. Record a purchase anyway. | status, module |
| U5-EN-SCHEDULE-06 | Book a calendar event for the astronomy livestream at midnight on November 22. | date |
| U5-ZH-FINANCE-15 | 支付宝给物业转600元，现在就转过去。 | status, module |
| U5-ZH-SCHEDULE-10 | 安排明天的读书会，时间写25:10，稍后我再核对。 | module, time |
| U5-EN-MEMO-01 | Add a normal-priority to-do to label the freezer containers today. | title |
| U5-EN-FINANCE-11 | Log 180 yuan spent on a replacement keyboard today. Category Shopping. I used a bank card, not cash. | account |
| U5-ZH-FINANCE-13 | 查一下我银行卡现在还剩多少钱。 | status, module |
| U5-ZH-SCHEDULE-15 | 看看明天下午有没有空档可以放个会议。 | status, module |
| U5-ZH-MEMO-03 | 周五之前交停车证申请，这个很重要，放入待办。 | title |
| U5-ZH-HEALTH-14 | 我弟手机今天玩游戏多久，直接帮我查一下他的。 | status, module |
| U5-EN-SCHEDULE-07 | Add "Tomorrow Festival" to the calendar on December 5 at 19:00. | title, date |
| U5-EN-MEMO-15 | How many of my tasks are still unfinished? | status, module |
| U5-ZH-MEMO-08 | 帮我存个待办：续借图书馆那本《Tomorrow》，没有截止时间。 | date |
| U5-EN-MEMO-14 | Make a memo to collect the dry cleaning, and also record 46 yuan spent on dinner. | status, module |
| U5-ZH-MEMO-10 | 建一个任务，明天把阳台的花盆搬进屋；不要设成高优先级，普通就行。 | priority |
| U5-ZH-HEALTH-15 | 打开用机统计，再帮我新建一个明天跑步的备忘。 | status, module |
| U5-ZH-FINANCE-02 | 昨天坐轮渡花了6元现金，记到交通费。 | title |
| U5-EN-MEMO-04 | Memo please: check the tent poles before November 24. | title, date |
| U5-ZH-SCHEDULE-01 | 日历新建：11月21日14:30参加陶艺体验课。 | date |
| U5-ZH-HEALTH-06 | 我想对比本周每天的用机时长，带我去对应页面。 | status, module |
| U5-EN-MEMO-10 | Create a memo: check the wheelchair tyre pressure tomorrow, normal rather than high priority. | priority |
| U5-ZH-SCHEDULE-07 | 新建活动：12月31日23:15在广场看跨年灯光秀。 | date |
| U5-ZH-FINANCE-09 | 记录一下：今天买橡皮花了2.345元现金，购物支出。 | title, category |

## Dataset and evidence

[All rows](results.csv) · [Chinese 60](chinese-60.csv) · [English 60](english-60.csv) · [Failures](failures.csv) · [Captured model outputs and checks](results.json) · [Frozen cases](../../backend/evaluation/mobile_unseen_v5.json) · [Protocol](protocol.json) · [Input audit](input-audit.json)

The [frozen source snapshot](frozen-snapshot.zip) contains the original cases, protocol, scoring code and application source bytes. Model weights and runtime binaries are excluded; their hashes are recorded in the protocol. The [publication manifest](publication.json) maps the original local paths to repository paths without changing frozen data. Statements about local-only storage in the protocol and run-integrity file describe the original evaluation stage, before publication was authorized.

See the [dataset guide](README.md) for coverage, scoring and verification commands, and the [findings](FINDINGS.md) for the remaining issues. Documentation is in English; Chinese test inputs and captured responses retain their original language.
