"""Ground small-model fields in user text before the shared strict validator.

These rules repair representation and clear unsupported values. They do not write
records or use evaluation IDs/expected answers.
"""
import re
from action_rules import search, USAGE, CATEGORY_WORDS, UNCERTAIN, finance_evidence
from task_service import priority_is_grounded, priority_needs_review

TASK = r"\b(?:finish|complete|submit|review|revise|organize|read|write|buy|remember|remind|todo|to-do)\b|完成|复习|整理|交作业|提交|读书|写报告|提醒|备忘|待办"
EVENT = r"\b(?:class|meeting|appointment|lecture|conference|interview|calendar|event)\b|开会|会议|组会|上课|课程|讲座|面试|预约|日程"
FINANCE = r"\b(?:spent|paid|cost|received|earned)\b|\b(?:record|log|add)\b.*\b(?:expense|income|transaction|purchase)\b|花了|花费|支出|收入|收到|记账|记一笔"
FUTURE = r"\b(?:will|plan to|going to|remind|budget|need to)\b|计划|打算|准备|提醒|预算"
UNCERTAIN_MONEY = r"\b(?:not|maybe|perhaps)\b|不是|不确定|可能|没花|没付|未支付"
TIME = r"(?<!\d)\d{1,2}:\d{2}(?:\s*[ap]m\b)?(?!\d)|(?<!\d)\d{1,2}\s*[ap]m\b|(?:上午|下午|晚上|早上)?\d{1,2}点(?:半|\d{1,2}分?)?"


def ground_fields(text, raw, hint):
    result = dict(raw)
    for key in ("date_text", "time_text", "priority", "amount", "kind", "category", "account"):
        if result.get(key) in ("null", "None", ""):
            result[key] = None
    if raw.get("intent") != "single_task" or raw.get("module") not in ("memo", "finance", "schedule", "health"):
        return result

    # Explicit user corrections take precedence. Automatic overrides require
    # concrete action vocabulary, never a date alone.
    if hint in ("memo", "finance", "schedule"):
        result["module"] = hint
    elif hint == "auto":
        task = bool(search(TASK, text))
        if search(USAGE, text) and not task:
            result["module"] = "health"
        elif search(FINANCE, text) and not search(FUTURE, text) and not task:
            result["module"] = "finance"
        elif task and not search(r"\b(?:schedule|calendar)\b|安排.*(?:会议|上课|日程)", text):
            result["module"] = "memo"
        elif search(EVENT, text):
            result["module"] = "schedule"

    # A copied phrase may differ in case; unrelated dates/times become unset.
    # The shared date_evidence rule then recovers a unique date from the source.
    for key in ("date_text", "time_text"):
        value = result.get(key)
        if isinstance(value, str):
            match = re.search(re.escape(value), text, re.IGNORECASE)
            result[key] = match.group() if match else None
    if result["module"] == "schedule":
        times = list(re.finditer(TIME, text, re.IGNORECASE))
        # Preserve AM/PM; accepting only a substring can change noon to midnight.
        result["time_text"] = times[0].group() if len(times) == 1 else None

    if not priority_needs_review(text):
        priorities = [p for p in ("normal", "important", "urgent") if priority_is_grounded(text, p)]
        result["priority"] = priorities[0] if len(priorities) == 1 else None
    else:
        result["priority"] = None

    if result["module"] == "finance":
        result = finance_evidence(text, result)
        categories = [name for name, pattern in CATEGORY_WORDS.items() if search(pattern, text)]
        if len(categories) == 1 and not search(UNCERTAIN, text):
            result["category"] = categories[0]
        if result.get("category") not in (*CATEGORY_WORDS, "Others", None):
            result["category"] = None
        if not search(UNCERTAIN_MONEY, text):
            income = search(r"\b(?:received|earned|income|salary|wages|paycheck)\b|收入|收到|工资|薪水|奖学金", text)
            expense = search(r"\b(?:spent|paid|cost|expense|bought)\b|花了|花费|支出|支付|买了", text)
            result["kind"] = "income" if income and not expense else "expense" if expense and not income else None
        else:
            result["kind"] = None
        if isinstance(result.get("amount"), (int, float)) and not isinstance(result["amount"], bool):
            result["amount"] = str(result["amount"])
        result["time_text"] = None
    else:
        for key in ("amount", "kind", "category", "account"):
            result[key] = None
    if result["module"] in ("memo", "health"):
        result["time_text"] = None
    return result
