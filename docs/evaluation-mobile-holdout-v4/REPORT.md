# Mobile model evaluation: frozen v4 first pass

Version 1.1.2 preserves specific event subjects and keeps a secondary explanation from replacing a transaction title. This report evaluates those changes with **240 newly authored inputs: Chinese 120 and English 120**, 30 per workflow per language.

Inputs, expected answers, production sources and scoring were [frozen at `f6778cc`](https://github.com/lana0323/Lifehub-Plus/commit/f6778cc05714257dee2a885484dd5c852afff7cc) before inference. Production revision: `dad48d7082110cd55df4b9a591b68793c53822b6`. Every input received one attempt, with no retries, manual answer repair or changes during the run.

## Results

| Metric | Chinese | English | Combined |
|---|---|---|---|
| Routing after validation | 89/120 (74.17%) | 99/120 (82.50%) | 188/240 (78.33%) |
| Required fields | 200/216 (92.59%) | 198/216 (91.67%) | 398/432 (92.13%) |
| All specified fields | 69/120 (57.50%) | 84/120 (70.00%) | 153/240 (63.75%) |
| Supported draft requests | 59/90 (65.56%) | 64/90 (71.11%) | 123/180 (68.33%) |
| Expected clarifications | 10/30 (33.33%) | 20/30 (66.67%) | 30/60 (50.00%) |
| Date fields, including expected blanks | 62/72 (86.11%) | 66/72 (91.67%) | 128/144 (88.89%) |
| Raw model routing, invoked inputs only | 81/117 (69.23%) | 80/107 (74.77%) | 161/224 (71.88%) |

**224 model calls**, 16 preflight clarifications, 7 processing errors, and 23 expected-clarification inputs returned as drafts. A draft still requires user confirmation; these counts do not describe automatic database writes.

| Workflow group | All specified fields |
|---|---|
| Memo | 36/60 (60.00%) |
| Finance | 39/60 (65.00%) |
| Schedule | 36/60 (60.00%) |
| Health | 42/60 (70.00%) |

## Findings and next priorities

This first pass exposes substantial gaps outside the earlier regression wording. The old 99.2% seen-set score does not describe these new inputs. The first-pass evidence is preserved before further fixes.

1. **Request boundaries and negation.** Only 4/16 multiple-action inputs and 1/6 negation inputs passed every check. Some balance/calendar queries, record edits and external-action requests became drafts. The four `ValueError` cases contain multiple JSON action objects instead of one clarification; three other outputs failed the model-output contract.
2. **Health vocabulary.** Supported paraphrases such as application usage records and opening the existing Health page sometimes receive `health_unsupported` or enter another module. Conversely, a request about another person's usage may open this device's page. No third-party statistics are accessed.
3. **Date and time grounding.** Sixteen annotated date checks failed. Bare weekdays, Chinese written-number dates/times, noon, uncertain dates and date words inside quoted names need broader handling.
4. **Finance and titles.** Eight of 48 type checks and five category checks failed. Ambiguity about one field can incorrectly clear another known field. Six title checks still failed, including a jacket reduced to Shopping and a Portuguese class reduced to Class. The repaired umbrella-collection and croissant-receipt scenarios both passed in this new set.

These counts describe the frozen first pass. No rule or prompt changes were made after inspecting these outputs. Generic title checks can still accept imperfect wording; use the raw examples for qualitative review.

## What the figures measure

- Application scores include model output, preflight checks and deterministic validation. Raw model routing is reported separately and excludes preflight-only inputs.
- Required fields use a micro average. Missing or ambiguous values are correct only when the frozen answer specifies a blank. Optional memo dates/priorities and schedule times are included in all-specified-field checks.
- Supported-draft success means all annotated fields matched for a reviewable draft. It does not measure successful UI entry or database persistence.
- Titles use predeclared keyword alternatives. Passing that check does not guarantee an ideal title or full semantic equivalence.
- The 180 draft requests and 60 clarification requests include colloquial wording, corrected typos, negation, mixed languages, invalid amounts, missing fields, quoted dates and calendar boundaries.
- The set was authored internally by the development assistant. Chinese and English scenario families overlap. Lexical screening against three prior dataset files found no exact repeats or pairs above the declared 0.78 threshold; this does not establish semantic or statistical independence.
- This is an untouched first pass for these inputs. If its failures guide further changes, subsequent runs on this set must be called regression tests. Earlier first-pass and regression artifacts remain unchanged.

## Runtime and reproducibility

- Model: **litert-community/Qwen2.5-1.5B-Instruct int8 ekv4096**, revision `19edb84c69a0212f29a6ef17ba0d6f278b6a1614`.
- Model SHA-256: `faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9`.
- Runtime: Windows JVM LiteRT-LM 0.10.2, CPU, JDK 21.0.7, Python 3.11.7, tzdata 2026.2.
- Machine: Intel Core i5-12400F, 32 GiB RAM, Windows 11. Inference uses CPU; no paid service or fallback.
- Sampling: top-k 1, top-p 1.0, temperature 0, seed 42, context 4096. One engine, fresh conversation per input, concurrency 1.
- Fixed per-input clocks and timezones are part of the dataset. These are host inference measurements, separate from Android integration and APK checks.
- Dataset SHA-256: `e11a2fccc1ab2edc40ef7a2d1694c905ecdb1c76492350d712c8fab8ae0d3c3f`. Source hashes are in [protocol.json](protocol.json).
- No latency statistics are reported.

Use the freeze revision and a fresh output directory:

```powershell
git checkout f6778cc05714257dee2a885484dd5c852afff7cc
.\scripts\mobile-evaluation\run_frozen.ps1 -ModelPath "C:/models/mobile-qwen2.5.litertlm"
```

The runner verifies the frozen source and dataset hashes before generation. Raw and validated outputs are retained locally. [results.json](results.json) contains every captured model answer, validation result, check and error. [run.json](run.json) records capture hashes.

## Separate application checks

| Check | Result | Scope |
|---|---|---|
| Python | 107/107 | Validation, title recovery, date rules and API contracts |
| Android/JVM unit | 20/20 | Serialization, money, dates, usage aggregation and utilities |
| Android integration | 23/23 | Packaged title repair, confirmation, duplicate prevention, cancellation, account isolation and database read-back |

Android checks use an isolated API 34 emulator and the separate QA package. The signed release APK is checked for signature, update installation and login launch. [Evidence and commands](reliability.json) are separate from model accuracy.

## Earlier title failures

The two previously observed title failures are fixed: “修鞋取件” retains the repair subject, and a sandwich purchase no longer becomes “Lost Receipt”. A validation-only replay of the previous 240 raw outputs passes 240/240 all-specified-field checks. This replay performs **no fresh inference** and is not included in the new holdout score. See [title-replay.json](title-replay.json).

## Failed inputs (87)

Failures remain in the denominator. The expected fields below were frozen before inference. Full raw outputs are available in the per-language CSVs and JSON.

### mh4-schedule-zh-27

我明天有什么安排，列给我看看。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "9465112f-7cd3-4187-bc4a-2010c34e8ce6",
      "title": "明天的安排",
      "notes": "",
      "date": "2026-10-07",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-finance-en-03

Shopping expense: a jacket today, 245 yuan, charged to my credit card.

Failed checks: **title**.

```json
{
  "expected": {
    "status": "draft",
    "module": "finance",
    "title": {
      "containsAny": [
        "jacket"
      ]
    },
    "amount": "245",
    "kind": "expense",
    "category": "Shopping",
    "account": "Credit Card",
    "date": "2026-10-06"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "3c32d3ce-1729-473a-91e3-3eee4160e124",
      "title": "Shopping",
      "notes": "a jacket",
      "date": "2026-10-06",
      "time": null,
      "amount": "245",
      "kind": "expense",
      "category": "Shopping",
      "account": "Credit Card",
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-health-en-26

Show today's phone usage and make a task to stop scrolling at 10pm.

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "73965f74-0a41-4502-ad09-0bead2a9fe23",
      "title": "Show today's phone usage and make a task to stop scrolling at 10pm.",
      "notes": "",
      "dueDate": "2026-10-06",
      "timezone": "America/Los_Angeles",
      "priority": null,
      "dateText": "today",
      "warnings": [
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-schedule-en-17

Sunday 10:15pm video call with José about the Montréal trip ✈️. Schedule it.

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "José",
        "Montréal",
        "trip"
      ]
    },
    "date": "2026-10-11",
    "time": "22:15"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "f5230da2-e9e3-4f7e-bf59-360fa9b0ecd6",
      "title": "Video Call with José",
      "notes": "Montréal trip",
      "date": null,
      "time": "22:15",
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-memo-zh-13

这周一之前贴好行李标签，先照原话加到待办。

Failed checks: **title**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "行李",
        "标签"
      ]
    },
    "date": null,
    "priority": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "2e211fc3-f0e3-4a1b-9628-b13ae6545f8b",
      "title": "先照原话加到待办",
      "notes": "先照原话加到待办",
      "dueDate": null,
      "timezone": "Asia/Shanghai",
      "priority": null,
      "dateText": "这周一",
      "warnings": [
        "past_date",
        "date_needs_review",
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-finance-zh-28

先记今天9元早餐，再记18元晚饭，两笔都用微信。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": null,
  "error": "ValueError"
}
```

### mh4-health-en-13

Not my bank balance: I want to see the time I spent using this phone today.

Failed checks: **module**.

```json
{
  "expected": {
    "status": "draft",
    "module": "health"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "c648b74f-07e0-47f1-8aab-996c889aca09",
      "title": "查看手机使用时长",
      "notes": "",
      "date": "2026-10-06",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-health-en-06

Show me the application usage records on this phone.

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "draft",
    "module": "health"
  },
  "actual": {
    "status": "clarification",
    "reason": "health_unsupported",
    "module": null,
    "draft": null,
    "fields": null
  },
  "error": null
}
```

### mh4-finance-zh-27

把今天所有娱乐支出加起来给我看。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "39e34f0a-897c-4810-b7b4-060cfe64d6cd",
      "title": "娱乐支出",
      "notes": "",
      "date": "2026-10-06",
      "time": null,
      "amount": null,
      "kind": "expense",
      "category": "Entertainment",
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-finance-zh-24

今天给宽带续费99元，银行卡支付。

Failed checks: **category**.

```json
{
  "expected": {
    "status": "draft",
    "module": "finance",
    "title": {
      "containsAny": [
        "宽带"
      ]
    },
    "amount": "99",
    "kind": "expense",
    "category": "Bills",
    "account": "Bank Card",
    "date": "2026-10-07"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "e2fb6209-f625-422f-85bc-c5b5f9430797",
      "title": "宽带续费",
      "notes": "",
      "date": "2026-10-07",
      "time": null,
      "amount": "99",
      "kind": "expense",
      "category": "Others",
      "account": "Bank Card",
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-health-zh-07

去Health，看看我今天的screen time。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "draft",
    "module": "health"
  },
  "actual": {
    "status": "clarification",
    "reason": "health_unsupported",
    "module": null,
    "draft": null,
    "fields": null
  },
  "error": null
}
```

### mh4-finance-zh-17

今天咖啡花了十四元，现金，金额不确定可以让我补。

Failed checks: **kind**.

```json
{
  "expected": {
    "status": "draft",
    "module": "finance",
    "title": {
      "containsAny": [
        "咖啡"
      ]
    },
    "amount": null,
    "kind": "expense",
    "category": "Food & Drinks",
    "account": "Cash",
    "date": "2026-10-06"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "50f28672-1526-4b0b-abb6-f3a80b9e8919",
      "title": "咖啡",
      "notes": "金额不确定",
      "date": "2026-10-06",
      "time": null,
      "amount": null,
      "kind": null,
      "category": "Food & Drinks",
      "account": "Cash",
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-health-zh-17

能不能展示这部手机今日的软件用时排名？

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "draft",
    "module": "health"
  },
  "actual": {
    "status": "clarification",
    "reason": "health_unsupported",
    "module": null,
    "draft": null,
    "fields": null
  },
  "error": null
}
```

### mh4-health-zh-22

替我把游戏应用锁定到明天。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "b0513126-295b-487a-ad19-ebbc220c45e7",
      "title": "游戏应用锁定",
      "notes": "明天",
      "date": "2026-10-07",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-finance-en-12

Lunch today was 27 yuan; the payment account escapes me.

Failed checks: **kind**.

```json
{
  "expected": {
    "status": "draft",
    "module": "finance",
    "title": {
      "containsAny": [
        "lunch"
      ]
    },
    "amount": "27",
    "kind": "expense",
    "category": "Food & Drinks",
    "account": null,
    "date": "2026-10-06"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "5ba2b57a-4496-4740-bff6-1a22449841b5",
      "title": "Lunch",
      "notes": "",
      "date": "2026-10-06",
      "time": null,
      "amount": "27",
      "kind": null,
      "category": "Food & Drinks",
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-health-zh-18

进入已有的Health界面，不要新增任何记录。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "draft",
    "module": "health"
  },
  "actual": {
    "status": "clarification",
    "reason": "health_unsupported",
    "module": null,
    "draft": null,
    "fields": null
  },
  "error": null
}
```

### mh4-schedule-zh-12

不是设待办，请直接做日程草稿：周六早上八点半骑行集合。

Failed checks: **module, date, time**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "骑行"
      ]
    },
    "date": "2026-10-10",
    "time": "08:30"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "936669ef-f6a2-4813-a981-fc02806bb905",
      "title": "不是设待办，请直接做日程草稿：周六早上八点半骑行集合",
      "notes": "",
      "dueDate": null,
      "timezone": "Asia/Shanghai",
      "priority": null,
      "dateText": "周六",
      "warnings": [
        "date_needs_review",
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-memo-zh-23

周四上午去补牙，这件事只放在我选择的模块。

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "补牙"
      ]
    },
    "date": "2026-10-08",
    "priority": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "cb451f6a-6c21-4023-8e25-0e9baba43424",
      "title": "周四上午去补牙，这件事只放在我选择的模块",
      "notes": "",
      "dueDate": null,
      "timezone": "Asia/Shanghai",
      "priority": null,
      "dateText": "周四",
      "warnings": [
        "date_needs_review",
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-health-zh-23

查询我同学今天的微信使用时长。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "health",
    "draft": null,
    "fields": {
      "id": "06385e7e-6cbb-427d-bfc2-948fa475508f",
      "title": "查看手机使用情况",
      "notes": "",
      "date": "2026-10-06",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-finance-zh-13

今天花了20元现金，买的啥记不清，分类先不选。

Failed checks: **category**.

```json
{
  "expected": {
    "status": "draft",
    "module": "finance",
    "title": {
      "containsAny": [
        "支出",
        "消费",
        "现金",
        "20"
      ]
    },
    "amount": "20",
    "kind": "expense",
    "category": null,
    "account": "Cash",
    "date": "2026-10-06"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "474efedc-b749-4975-955d-233b5eb80757",
      "title": "今天花了20元现金，买的啥记不清，分类先不选。",
      "notes": "",
      "date": "2026-10-06",
      "time": null,
      "amount": "20",
      "kind": "expense",
      "category": "Others",
      "account": "Cash",
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-schedule-en-28

Add an interview tomorrow morning and then a second interview the following afternoon.

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "7326e55f-2d1b-4e61-adcc-ae902672bb7a",
      "title": "Interviews",
      "notes": "Add an interview tomorrow morning and then a second interview the following afternoon",
      "date": "2026-10-07",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-finance-en-21

Already paid, not a shopping task: coffee 今天 23元 using 微信 ☕.

Failed checks: **module, amount, kind, category, account**.

```json
{
  "expected": {
    "status": "draft",
    "module": "finance",
    "title": {
      "containsAny": [
        "coffee",
        "咖啡"
      ]
    },
    "amount": "23",
    "kind": "expense",
    "category": "Food & Drinks",
    "account": "WeChat",
    "date": "2026-10-06"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "55e98e67-4be8-43d9-9d3e-350c5dee4d8c",
      "title": "Already paid, not a shopping task: coffee 今天 23元 using 微信 ☕.",
      "notes": "",
      "dueDate": "2026-10-06",
      "timezone": "America/Los_Angeles",
      "priority": null,
      "dateText": "今天",
      "warnings": [
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-finance-en-26

What is the current balance of my cash account?

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "a1ed22c0-c87f-49b0-bee5-904fa7b85a50",
      "title": "Current Balance",
      "notes": "Cash Account",
      "date": null,
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": "Cash",
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-finance-en-19

Lunch was 44 yuan today. Maybe Alipay, maybe WeChat; leave the account for me to check.

Failed checks: **kind**.

```json
{
  "expected": {
    "status": "draft",
    "module": "finance",
    "title": {
      "containsAny": [
        "lunch"
      ]
    },
    "amount": "44",
    "kind": "expense",
    "category": "Food & Drinks",
    "account": null,
    "date": "2026-10-06"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "7851151f-dc7a-48de-b9f8-247e71f60407",
      "title": "Lunch",
      "notes": "44 yuan",
      "date": "2026-10-06",
      "time": null,
      "amount": "44",
      "kind": null,
      "category": "Food & Drinks",
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-finance-en-06

Today a scholarship payment of 950 yuan reached my bank card.

Failed checks: **kind**.

```json
{
  "expected": {
    "status": "draft",
    "module": "finance",
    "title": {
      "containsAny": [
        "scholarship"
      ]
    },
    "amount": "950",
    "kind": "income",
    "category": "Scholarship",
    "account": "Bank Card",
    "date": "2026-10-06"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "8e0c803a-ceae-4ad7-9756-562dc9744f45",
      "title": "Scholarship Payment",
      "notes": "",
      "date": "2026-10-06",
      "time": null,
      "amount": "950",
      "kind": null,
      "category": "Scholarship",
      "account": "Bank Card",
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-memo-en-27

Make a task to mend my backpack, then add an expense of 8 yuan for thread today.

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "e559eeea-cb51-4d90-baa7-b7f982fd55c1",
      "title": "Mend Backpack",
      "notes": "",
      "dueDate": "2026-10-06",
      "timezone": "America/Los_Angeles",
      "priority": null,
      "dateText": "today",
      "warnings": [
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-schedule-zh-01

日历占个位置：明天下午四点，牙套复诊。

Failed checks: **time**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "牙套",
        "复诊"
      ]
    },
    "date": "2026-10-07",
    "time": "16:00"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "b312084c-3213-4716-971a-882828fb6a7e",
      "title": "牙套复诊",
      "notes": "",
      "date": "2026-10-07",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-memo-zh-07

不是要加日程，我只想记住烤箱预热要十分钟。

Failed checks: **module, priority**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "烤箱",
        "预热"
      ]
    },
    "date": null,
    "priority": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "a3ab3f28-31c6-4a00-bb08-68e5951ac768",
      "title": "烤箱预热",
      "notes": "十分钟",
      "date": null,
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-schedule-en-30

Send a calendar invitation to everyone attending the seminar.

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "92d1595c-7395-468f-b73e-20fa270f8700",
      "title": "Calendar Invitation",
      "notes": "Everyone attending the seminar",
      "date": null,
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-finance-zh-21

不是待办，是已经买了：今天cash付18元买coffee☕。

Failed checks: **module, amount, kind, category, account**.

```json
{
  "expected": {
    "status": "draft",
    "module": "finance",
    "title": {
      "containsAny": [
        "coffee",
        "咖啡"
      ]
    },
    "amount": "18",
    "kind": "expense",
    "category": "Food & Drinks",
    "account": "Cash",
    "date": "2026-10-06"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "2f237d45-9b81-4460-a4c9-07d0702934ce",
      "title": "不是待办，是已经买了：今天cash付18元买coffee☕",
      "notes": "今天cash付18元买coffee☕",
      "dueDate": "2026-10-06",
      "timezone": "Asia/Shanghai",
      "priority": null,
      "dateText": "今天",
      "warnings": [
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-schedule-en-14

I have a Portugese, sorry Portuguese, class this Wednesday at 6pm.

Failed checks: **title**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "Portuguese",
        "Portugese"
      ]
    },
    "date": "2026-10-07",
    "time": "18:00"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "94723475-e3df-4932-bda2-0fe2abdf1bf5",
      "title": "Class",
      "notes": "",
      "date": "2026-10-07",
      "time": "18:00",
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-finance-en-13

Record 60 yuan spent in cash today. I do not recall what it was for, leave category empty.

Failed checks: **kind, category**.

```json
{
  "expected": {
    "status": "draft",
    "module": "finance",
    "title": {
      "containsAny": [
        "expense",
        "spen",
        "cash",
        "60"
      ]
    },
    "amount": "60",
    "kind": "expense",
    "category": null,
    "account": "Cash",
    "date": "2026-10-06"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "3cd3fd1c-55d5-4fd0-ac92-31fbae453a75",
      "title": "60 yuan spent in cash today. I do not recall what it was for, leave category empty.",
      "notes": "",
      "date": "2026-10-06",
      "time": null,
      "amount": "60",
      "kind": null,
      "category": "Others",
      "account": "Cash",
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-memo-en-23

Thursday at 11am: violin lesson. Keep this as a memo in my chosen module.

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "violin"
      ]
    },
    "date": "2026-10-08",
    "priority": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "451e6e69-7eb5-4aba-a5b8-5602d6c709a5",
      "title": "Violin Lesson",
      "notes": "Thursday at 11am",
      "dueDate": null,
      "timezone": "America/Los_Angeles",
      "priority": null,
      "dateText": "Thursday",
      "warnings": [
        "date_needs_review",
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-health-zh-26

显示今天手机用时，再建一个今晚10点放下手机的待办。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": null,
  "error": "ValueError"
}
```

### mh4-health-zh-27

先打开健康使用统计，然后记我今天的早餐支出12元。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "health",
    "draft": null,
    "fields": {
      "id": "cca95ff8-4d52-42f3-a476-70f4f37ad81f",
      "title": "查看手机使用情况",
      "notes": "12元",
      "date": "2026-10-06",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-finance-zh-29

今天公交花4元，再帮我创建一个周日查路线的待办。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "81692ba5-ac6a-4045-85ec-15f6a84c5965",
      "title": "再帮我创建一个周日查路线的待办",
      "notes": "再帮我创建一个周日查路线的待办",
      "dueDate": "2026-10-06",
      "timezone": "Asia/Shanghai",
      "priority": null,
      "dateText": "今天",
      "warnings": [
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-schedule-zh-30

直接给所有会议参与者发送日历邀请。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "b22f725c-57ab-477a-bc0a-4365ba77615c",
      "title": "发送日历邀请",
      "notes": "所有会议参与者",
      "date": null,
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-memo-zh-15

记住除霜冰箱这件事，紧急程度还没定。

Failed checks: **priority**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "冰箱",
        "除霜"
      ]
    },
    "date": null,
    "priority": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "a1b98f8b-5daa-4ca7-8118-5d30df9bbd07",
      "title": "除霜冰箱",
      "notes": "紧急程度还没定",
      "dueDate": null,
      "timezone": "Asia/Shanghai",
      "priority": "urgent",
      "dateText": null,
      "warnings": [
        "date_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-schedule-en-04

Make an event for the house inspection today at noon.

Failed checks: **time**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "inspection"
      ]
    },
    "date": "2026-10-06",
    "time": "12:00"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "f8369b2c-094c-4b10-a138-3f3480c28218",
      "title": "House Inspection",
      "notes": "",
      "date": "2026-10-06",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-schedule-zh-21

今天晚上九点远程答辩，添加到日程。

Failed checks: **time**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "答辩"
      ]
    },
    "date": "2026-10-07",
    "time": "21:00"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "4ae4c4f5-2012-415b-9587-b1caef17cee2",
      "title": "答辩",
      "notes": "",
      "date": "2026-10-07",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-memo-en-22

Add oiling the squeaky hinge to the list. It is not urgent at all.

Failed checks: **module, priority**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "hinge"
      ]
    },
    "date": null,
    "priority": "normal"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "b9c812c8-a1f3-427b-b667-53d4c5280592",
      "title": "Oiling the squeaky hinge",
      "notes": "not urgent",
      "date": null,
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-finance-zh-20

昨天还是前天吃面付了19元现金，日期记混了。

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "finance",
    "title": {
      "containsAny": [
        "面"
      ]
    },
    "amount": "19",
    "kind": "expense",
    "category": "Food & Drinks",
    "account": "Cash",
    "date": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "22d39a31-e166-4ecf-9fc3-001278b3b1b9",
      "title": "面",
      "notes": "19元",
      "date": "2026-10-05",
      "time": null,
      "amount": "19",
      "kind": "expense",
      "category": "Food & Drinks",
      "account": "Cash",
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-memo-zh-27

帮我加一个整理书架的待办，然后再记今天购买书架花了90元。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": null,
  "error": "ValueError"
}
```

### mh4-health-en-04

No reminder to use my phone less, just show today's usage statistics.

Failed checks: **module**.

```json
{
  "expected": {
    "status": "draft",
    "module": "health"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "18385d38-d45c-406a-aac1-6cb48800a104",
      "title": "No reminder to use my phone less, just show today's usage statistics.",
      "notes": "",
      "dueDate": "2026-10-06",
      "timezone": "America/Los_Angeles",
      "priority": null,
      "dateText": "today",
      "warnings": [
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-finance-en-30

Pay the landlord 2500 yuan from my bank card right now.

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "6b000891-bc5f-4d49-a3a7-e4a229f87fc2",
      "title": "Pay rent",
      "notes": "",
      "date": null,
      "time": null,
      "amount": "2500",
      "kind": null,
      "category": "Bills",
      "account": "Bank Card",
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-schedule-en-27

List all the appointments currently on my calendar for tomorrow.

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "89e409c1-8fea-4a8e-9c80-0d153ee07bb1",
      "title": "Appointments",
      "notes": "",
      "date": "2026-10-07",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-health-zh-11

健康页面打开一下，我自己看。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "draft",
    "module": "health"
  },
  "actual": {
    "status": "clarification",
    "reason": "health_unsupported",
    "module": null,
    "draft": null,
    "fields": null
  },
  "error": null
}
```

### mh4-schedule-zh-17

周日22:15跟Zoë视频聊留学申请🌍，记为日程。

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "Zoë",
        "留学"
      ]
    },
    "date": "2026-10-11",
    "time": "22:15"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "f44bcbaf-db95-472d-aeac-a47d4444deff",
      "title": "视频聊留学申请",
      "notes": "周日22:15",
      "date": null,
      "time": "22:15",
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-memo-en-07

No calendar booking please. Just jot down that the lift code ends in 48.

Failed checks: **module, priority**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "lift",
        "code"
      ]
    },
    "date": null,
    "priority": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "f39e6f33-920a-4277-86ae-021e23cd1bf0",
      "title": "Lift Code",
      "notes": "Lift code ends in 48",
      "date": null,
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-health-en-14

Which apps have taken up my time today? Display this device's usage.

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "draft",
    "module": "health"
  },
  "actual": {
    "status": "clarification",
    "reason": "health_unsupported",
    "module": null,
    "draft": null,
    "fields": null
  },
  "error": null
}
```

### mh4-memo-zh-29

直接给李师傅发短信，说我晚到十分钟。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "b9a901db-1bf1-4e4b-8dfc-a25c97a6c182",
      "title": "发短信",
      "notes": "晚到十分钟",
      "date": null,
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-memo-zh-12

截止2026-12-19，把老家地址寄给快递员。

Failed checks: **module, title, priority**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "地址"
      ]
    },
    "date": "2026-12-19",
    "priority": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "dcecebdd-177f-4842-b7df-9fb874e570c4",
      "title": "寄给快递员",
      "notes": "老家地址",
      "date": "2026-12-19",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-memo-en-10

In 4 days, return the rented microphone; put it on my checklist.

Failed checks: **module, priority**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "microphone"
      ]
    },
    "date": "2026-10-10",
    "priority": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "823fbd22-1670-40fe-b1db-b77e04572b36",
      "title": "Return rented microphone",
      "notes": "put it on my checklist",
      "date": "2026-10-10",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-schedule-en-24

Rehearsal with the band "Next Tuesday" tomorrow at 5pm; the band name is not a date.

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "rehearsal",
        "band"
      ]
    },
    "date": "2026-10-07",
    "time": "17:00"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "63dc984a-51b6-426d-b380-582f747c4f56",
      "title": "Rehearsal with the band",
      "notes": "",
      "date": null,
      "time": "17:00",
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-schedule-zh-26

把原定周四的驾校课挪到周五。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "e7e65da7-2cb9-48d5-befd-71512fc5b367",
      "title": "驾校课",
      "notes": "挪到周五",
      "date": null,
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-memo-en-18

Do not log an expense; just make a task to check how tuition instalments work.

Failed checks: **title**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "tuition",
        "instalment"
      ]
    },
    "date": null,
    "priority": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "3c552c57-428a-4130-88c9-2e5307bd7b90",
      "title": "Do not log an expense",
      "notes": "Do not log an expense",
      "dueDate": null,
      "timezone": "America/Los_Angeles",
      "priority": null,
      "dateText": null,
      "warnings": [
        "date_missing",
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-finance-zh-19

今天午餐36元，可能用支付宝也可能微信，忘记了。

Failed checks: **kind**.

```json
{
  "expected": {
    "status": "draft",
    "module": "finance",
    "title": {
      "containsAny": [
        "午餐"
      ]
    },
    "amount": "36",
    "kind": "expense",
    "category": "Food & Drinks",
    "account": null,
    "date": "2026-10-06"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "ab3b88fd-a39f-4087-ab7a-f6a7ee425708",
      "title": "午餐",
      "notes": "",
      "date": "2026-10-06",
      "time": null,
      "amount": "36",
      "kind": null,
      "category": "Food & Drinks",
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-health-zh-01

我想核对今天花在手机上的总时数，打开相关用时界面。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "draft",
    "module": "health"
  },
  "actual": {
    "status": "clarification",
    "reason": "health_unsupported",
    "module": null,
    "draft": null,
    "fields": null
  },
  "error": null
}
```

### mh4-schedule-zh-29

周日9点加个登山日程，另记今天购买登山杖花费70元。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": null,
  "error": "ValueError"
}
```

### mh4-finance-zh-25

将之前那笔披萨消费从50改成45元。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": null,
  "error": "invalid_model_output"
}
```

### mh4-schedule-zh-16

日程名称是“领取”，内容为明天下午两点去拿补办的证件。

Failed checks: **time**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "领取"
      ]
    },
    "date": "2026-10-07",
    "time": "14:00"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "20954f89-7743-4669-afc0-daded93474b7",
      "title": "领取证件",
      "notes": "",
      "date": "2026-10-07",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-finance-zh-30

现在直接从支付宝给房东转2000元。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "a248d48f-6198-48a1-bb48-7dfdc3d16f76",
      "title": "现在直接从支付宝给房东转2000元",
      "notes": "给房东",
      "date": null,
      "time": null,
      "amount": "2000",
      "kind": "expense",
      "category": "Others",
      "account": "Alipay",
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-health-en-01

How much of today have I burned on my phone? Take me to the stats.

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "draft",
    "module": "health"
  },
  "actual": {
    "status": "clarification",
    "reason": "health_unsupported",
    "module": null,
    "draft": null,
    "fields": null
  },
  "error": null
}
```

### mh4-schedule-en-20

Tomorrow at noon we have a welcome lunch; put it on the calendar.

Failed checks: **time**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "welcome",
        "lunch"
      ]
    },
    "date": "2027-09-01",
    "time": "12:00"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "8d029643-3590-4275-9223-55f67f6ca513",
      "title": "Welcome Lunch",
      "notes": "",
      "date": "2027-09-01",
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-memo-zh-26

能把清单里已经完成的三项一次移除吗？

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "dfa38cfc-842c-44e4-815f-db585467e14c",
      "title": "移除已完成的三项",
      "notes": "已完成的三项",
      "dueDate": null,
      "timezone": "Asia/Shanghai",
      "priority": null,
      "dateText": null,
      "warnings": [
        "date_missing",
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-schedule-en-12

Not a task please: calendar event for rowing practice on Saturday at 8am.

Failed checks: **module, date, time**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "rowing"
      ]
    },
    "date": "2026-10-10",
    "time": "08:00"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "32c35997-feb2-4d67-8fcb-0c180a1bda12",
      "title": "Not a task please: calendar event for rowing practice on Saturday at 8am.",
      "notes": "",
      "dueDate": null,
      "timezone": "America/Los_Angeles",
      "priority": null,
      "dateText": "Saturday",
      "warnings": [
        "date_needs_review",
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-memo-zh-10

帮我记个任务：两天后把门垫洗了。

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "门垫"
      ]
    },
    "date": "2026-10-08",
    "priority": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "b5757635-9abe-4c6e-9dcf-0f77ab0ccf4e",
      "title": "洗门垫",
      "notes": "",
      "dueDate": null,
      "timezone": "Asia/Shanghai",
      "priority": null,
      "dateText": "两天后",
      "warnings": [
        "date_needs_review",
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-schedule-zh-05

两天后上午10点要做年度体检。

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "年度",
        "体检"
      ]
    },
    "date": "2026-10-08",
    "time": "10:00"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "7c3e45e2-21ac-4c5a-bad3-960569068f79",
      "title": "年度体检",
      "notes": "2 days later, 10:00 AM",
      "date": null,
      "time": "10:00",
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-memo-zh-25

把叫“浇花”的旧任务改名为“修剪枝叶”。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "516d9c79-1261-485b-8227-54ad98575f37",
      "title": "修剪枝叶",
      "notes": "把叫“浇花”的旧任务改名为",
      "dueDate": null,
      "timezone": "Asia/Shanghai",
      "priority": null,
      "dateText": null,
      "warnings": [
        "date_missing",
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-memo-zh-16

备忘：“下周日”是我们读书会的书名。只是书名，不是截止日期。

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "下周日",
        "读书会"
      ]
    },
    "date": null,
    "priority": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "d0334b4a-42c6-41d9-adf2-f9ade69e65d7",
      "title": "读书会书名",
      "notes": "下周日",
      "dueDate": "2026-10-18",
      "timezone": "Asia/Shanghai",
      "priority": null,
      "dateText": "下周日",
      "warnings": [
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-finance-en-28

Log breakfast at 12 yuan and then dinner at 30 yuan today, both with cash.

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "aa7a6a4c-5a16-4f0b-b141-53a910e75729",
      "title": "Breakfast and Dinner",
      "notes": "12 yuan, 30 yuan",
      "date": "2026-10-06",
      "time": null,
      "amount": null,
      "kind": null,
      "category": "Food & Drinks",
      "account": "Cash",
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-memo-en-03

A note for future me: the spare duvet lives in the attic trunk.

Failed checks: **title**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "duvet",
        "attic",
        "trunk"
      ]
    },
    "date": null,
    "priority": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "b160f79f-4979-4f0e-ab74-987abf62e220",
      "title": "Note for future me",
      "notes": "the spare duvet lives in the attic trunk",
      "dueDate": null,
      "timezone": "America/Los_Angeles",
      "priority": null,
      "dateText": null,
      "warnings": [
        "date_missing",
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-memo-en-25

Rename my existing "vacuum" task to "mop the hallway".

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": null,
  "error": "invalid_model_output"
}
```

### mh4-memo-zh-22

待办：换浴室灯泡，不急，哪天都行。

Failed checks: **priority**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "灯泡"
      ]
    },
    "date": null,
    "priority": "normal"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "59f09ae8-77c6-4ef0-bd45-c290dc4810fb",
      "title": "换浴室灯泡",
      "notes": "不急，哪天都行",
      "dueDate": null,
      "timezone": "Asia/Shanghai",
      "priority": null,
      "dateText": null,
      "warnings": [
        "date_missing",
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-memo-zh-02

麻烦放进清单，明天检查一下灭火器到期没有，优先度高。

Failed checks: **priority**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "灭火器"
      ]
    },
    "date": "2026-10-07",
    "priority": "important"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "e7899f69-5584-4210-9c97-ae9f940a1f4f",
      "title": "检查灭火器到期",
      "notes": "明天",
      "dueDate": "2026-10-07",
      "timezone": "Asia/Shanghai",
      "priority": null,
      "dateText": "明天",
      "warnings": [
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-memo-en-26

Clear out the completed items currently in my checklist.

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "136a47a1-850b-4633-ac10-10e9386679a4",
      "title": "Clear out completed items",
      "notes": "",
      "dueDate": null,
      "timezone": "America/Los_Angeles",
      "priority": null,
      "dateText": null,
      "warnings": [
        "date_missing",
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-finance-en-20

I paid 24 yuan cash for noodles, yesterday or the day before, the date is hazy.

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "finance",
    "title": {
      "containsAny": [
        "noodle"
      ]
    },
    "amount": "24",
    "kind": "expense",
    "category": "Food & Drinks",
    "account": "Cash",
    "date": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "finance",
    "draft": null,
    "fields": {
      "id": "bb03ae35-490f-4f5e-9611-8a5043a128fd",
      "title": "Noodles",
      "notes": "",
      "date": "2026-10-05",
      "time": null,
      "amount": "24",
      "kind": "expense",
      "category": "Food & Drinks",
      "account": "Cash",
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-schedule-zh-28

加明天上午的面试，再加后天下午的复试。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "d65bf162-0372-4de1-be9d-a8337e51ca60",
      "title": "面试",
      "notes": "",
      "date": null,
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-health-zh-10

我想确认今天是不是刷短视频太久了，显示各app用时就好。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "draft",
    "module": "health"
  },
  "actual": {
    "status": "clarification",
    "reason": "health_unsupported",
    "module": null,
    "draft": null,
    "fields": null
  },
  "error": null
}
```

### mh4-memo-zh-28

记得还足球，再安排星期六下午的球赛日程。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "7c345ed9-edee-4b33-9500-4cce3ac82c9e",
      "title": "安排球赛",
      "notes": "记得还足球",
      "date": null,
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-memo-zh-09

今晚之前把reading notes上传，high priority，放备忘。

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "memo",
    "title": {
      "containsAny": [
        "reading",
        "笔记",
        "notes"
      ]
    },
    "date": "2026-10-06",
    "priority": "important"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "9e9e799d-7888-423b-a6ff-17214c7c7ab6",
      "title": "reading notes",
      "notes": "upload",
      "dueDate": null,
      "timezone": "Asia/Shanghai",
      "priority": "important",
      "dateText": "今晚之前",
      "warnings": [
        "date_needs_review"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-health-en-11

Go into Health; I will read the figures there myself.

Failed checks: **module**.

```json
{
  "expected": {
    "status": "draft",
    "module": "health"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "965f7fa6-9b97-41e9-bafa-682924ae06f1",
      "title": "Go into Health",
      "notes": "",
      "dueDate": null,
      "timezone": "America/Los_Angeles",
      "priority": null,
      "dateText": null,
      "warnings": [
        "date_missing",
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

### mh4-schedule-en-11

Friday dinner gathering, either 6pm or 7pm, still checking the time. Add an event.

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "dinner"
      ]
    },
    "date": "2026-10-09",
    "time": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "92deff66-fea9-4801-8cd4-7c6f9eaf3f83",
      "title": "Friday dinner gathering",
      "notes": "6pm or 7pm",
      "date": null,
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "America/Los_Angeles"
    }
  },
  "error": null
}
```

### mh4-schedule-zh-24

明天和“下周二”乐队排练，下午5点，只把明天作为日期。

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "乐队",
        "排练"
      ]
    },
    "date": "2026-10-07",
    "time": "17:00"
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "4b68cac3-b998-44d4-96da-aa12ee3a33b2",
      "title": "乐队排练",
      "notes": "下午5点",
      "date": null,
      "time": "17:00",
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-health-zh-21

今天体温37.1，帮我记录到Health。

Failed checks: **status, module**.

```json
{
  "expected": {
    "status": "clarification",
    "module": null
  },
  "actual": null,
  "error": "invalid_model_output"
}
```

### mh4-schedule-zh-11

周五的晚餐聚会，要么18点要么19点，时间还不确定。

Failed checks: **date**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "晚餐",
        "聚会"
      ]
    },
    "date": "2026-10-09",
    "time": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "module": "schedule",
    "draft": null,
    "fields": {
      "id": "5c488fad-1efc-412c-bc5b-6c8b9945671f",
      "title": "晚餐聚会",
      "notes": "周五",
      "date": null,
      "time": null,
      "amount": null,
      "kind": null,
      "category": null,
      "account": null,
      "timezone": "Asia/Shanghai"
    }
  },
  "error": null
}
```

### mh4-schedule-zh-07

明天一早有个读书分享会，几点还没定。

Failed checks: **module, time**.

```json
{
  "expected": {
    "status": "draft",
    "module": "schedule",
    "title": {
      "containsAny": [
        "读书",
        "分享"
      ]
    },
    "date": "2026-10-07",
    "time": null
  },
  "actual": {
    "status": "draft",
    "reason": null,
    "draft": {
      "id": "46223a9d-767a-4214-9b0a-8264c239115b",
      "title": "明天一早有个读书分享会，几点还没定",
      "notes": "",
      "dueDate": "2026-10-07",
      "timezone": "Asia/Shanghai",
      "priority": null,
      "dateText": "明天",
      "warnings": [
        "priority_missing"
      ]
    },
    "module": "memo",
    "fields": null
  },
  "error": null
}
```

## Files

[Excel workbook](Lifehub-Plus-Holdout-v4.xlsx) · [Chinese 120](chinese-120.csv) · [English 120](english-120.csv) · [All results](results.csv) · [Failures only](failures.csv) · [Frozen protocol](protocol.json)
