"""Ground small-model fields in user text before the shared strict validator.

These rules repair representation and clear unsupported values. They do not write
records or use evaluation IDs/expected answers.
"""
import re
from action_rules import search, USAGE, CATEGORY_WORDS, UNCERTAIN, finance_evidence
from task_service import priority_is_grounded, priority_needs_review

TASK = r"\b(?:task|reminder|finish|complete|submit|review|revise|organize|read|write|buy|remind|todo|to-do)\b|\bremember to\b|任务|完成|复习|整理|交作业|提交|读书|写报告|提醒|备忘|待办"
EVENT = r"\b(?:class|meeting|appointment|lecture|conference|interview|calendar|event|tutorial|lesson|seminar|workshop)\b|开会|会议|组会|评审会|上课|课程|讲座|面试|预约|日程|日历|辅导|研讨会"
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
        routing_text = re.sub(r"\b(?:without|not)\s+(?:creating|adding|create|add)\s+(?:(?:an?|any)\s+)?(?:task|reminder|memo)\b|不(?:创建|添加|生成)(?:待办|任务|备忘录)", "", text, flags=re.IGNORECASE)
        task = bool(search(TASK, routing_text))
        explicit_task = search(r"\b(?:task|reminder|todo|to-do|remind)\b|\bremember to\b|任务|提醒|备忘|待办", routing_text)
        if explicit_task:
            result["module"] = "memo"
        elif search(USAGE, text):
            result["module"] = "health"
        elif search(EVENT, text) and (raw["module"] == "schedule" or search(r"\b(?:calendar|schedule|event)\b|日历|日程|安排", text)):
            result["module"] = "schedule"
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
        category_context = " ".join(clause for clause in re.split(r"[,，;；。]", text)
                                    if any(search(pattern, clause) for pattern in CATEGORY_WORDS.values()))
        if len(categories) == 1 and not search(UNCERTAIN, category_context):
            result["category"] = categories[0]
        if result.get("category") not in (*CATEGORY_WORDS, "Others", None):
            result["category"] = None
        direction_text = re.sub(r"(?:account|payment method)\s+(?:is\s+)?not (?:recorded|specified|provided)|(?:I\s+)?(?:did\s+)?not\s+(?:specify|record|provide)\s+(?:an?\s+)?(?:account|payment method)|(?:未|没|没有)(?:记录|提供|指定)(?:付款|支付)?(?:账户|方式)", "", text, flags=re.IGNORECASE)
        if not search(UNCERTAIN_MONEY, direction_text):
            income = search(r"\b(?:received|earned|income|salary|wages|paycheck)\b|收入|收到|工资|薪水|奖学金", text)
            expense_text = re.sub(r"\bpaid\s+(?:into|to me)\b", "", text, flags=re.IGNORECASE)
            expense = search(r"\b(?:spent|paid|cost|expense|bought|purchase|ticket|bill|fare|fee)\b|花了|花费|支出|支付|付款|付的|买了|购买|消费|门票|车票|车费", expense_text)
            result["kind"] = "income" if income and not expense else "expense" if expense and not income else None
        else:
            result["kind"] = None
        if isinstance(result.get("amount"), (int, float)) and not isinstance(result["amount"], bool):
            result["amount"] = str(result["amount"])
        result["time_text"] = None
        # A generic transaction verb loses the purchase description. Preserve
        # source wording so the user can still review what the record means.
        if isinstance(result.get("title"), str) and re.fullmatch(r"消费|支出|收入|购买|付款|expense|income|purchase|payment|transaction", result["title"], re.IGNORECASE):
            result["title"] = re.sub(r"^(?:记录|记一笔|记账|record|log|add)\s*", "", text, flags=re.IGNORECASE)[:100]
    else:
        for key in ("amount", "kind", "category", "account"):
            result[key] = None
    if result["module"] in ("memo", "health"):
        result["time_text"] = None
    if result["module"] == "memo" and isinstance(result.get("title"), str) and search(r"备忘录$|待办$|任务$|\b(?:memo|reminder|task)$", result["title"]):
        # Replace a generic module label with an exact action clause from the
        # input. Specific generated titles remain untouched.
        notes = result.get("notes")
        if isinstance(notes, str) and 0 < len(notes) <= 100 and notes in text:
            result["title"] = notes
        else:
            parts = re.split(r"[,，]", text, maxsplit=1)
            if len(parts) == 2 and search(r"(?:创建|新增|添加).*?(?:备忘录|待办|任务)", parts[0]) and 0 < len(parts[1].strip()) <= 100:
                result["title"] = parts[1].strip(" 。.")
    if result["module"] == "health":
        # This workflow opens a fixed system-usage screen; its label should not
        # inherit a translated or invented model description.
        result["title"] = "查看手机使用情况" if re.search(r"[\u4e00-\u9fff]", text) else "View phone usage"
    return result
