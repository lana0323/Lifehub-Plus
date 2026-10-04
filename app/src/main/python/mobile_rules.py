"""Ground small-model fields in user text before the shared strict validator.

These rules repair representation and clear unsupported values. They do not write
records or use evaluation IDs/expected answers.
"""
import re
from action_rules import search, USAGE, HEALTH_PAGE, CATEGORY_WORDS, UNCERTAIN, finance_evidence, positive_request, explicit_draft, ACCOUNT_WORDS, transaction_kind
from text_evidence import mask_quotes, TIME_PATTERN
from task_service import priority_is_grounded, priority_needs_review
from mobile_titles import recover_title

TASK = r"\b(?:task|reminder|finish|complete|submit|review|revise|organize|read|write|buy|remind|todo|to-do|wash|clean|sort|archive|upload|checklist|jot)\b|\b(?:remember to|I should|I need to|a note|to the list)\b|任务|完成|复习|整理|交作业|提交|读书|写报告|提醒|备忘|待办|别忘|记住|清单|截止|擦窗|洗衣|打扫|寄出"
EVENT = r"\b(?:class|meeting|appointment|lecture|conference|interview|calendar|event|tutorial|lesson|seminar|workshop)\b|开会|会议|组会|分享会|评审会|交流会|体验课|培训|上课|课程|安排[^，。；]*课|讲座|面试|预约|日程|日历|辅导|研讨会"
FINANCE = r"\b(?:spent|paid|cost|received|earned)\b|\b(?:record|log|add)\b.*\b(?:expense|income|transaction|purchase)\b|花了|花费|支出|收入|收到|记账|记一笔"
FUTURE = r"\b(?:will|plan to|going to|remind|budget|need to)\b|计划|打算|准备|提醒|预算"
UNCERTAIN_MONEY = r"\b(?:not|maybe|perhaps)\b|不是|不确定|可能|没花|没付|未支付"
TIME = TIME_PATTERN


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
        routing_text = mask_quotes(positive_request(text))
        task = bool(search(TASK, routing_text))
        explicit_task = explicit_draft(routing_text) or search(r"\b(?:task|memo|reminder|todo|to-do|remind|checklist)\b|\b(?:remember to|a note for)\b|任务|提醒|备忘|待办|别忘|清单|截止", routing_text)
        if explicit_task:
            result["module"] = "memo"
        elif search(USAGE, routing_text) or search(HEALTH_PAGE, routing_text):
            result["module"] = "health"
        elif search(EVENT, routing_text) and (raw["module"] == "schedule" or search(r"\b(?:calendar|schedule|event)\b|日历|日程|安排|分享会", routing_text)):
            result["module"] = "schedule"
        elif search(FINANCE, routing_text):
            result["module"] = "finance"
        elif task and not search(r"\b(?:schedule|calendar)\b|安排.*(?:会议|上课|日程)", routing_text):
            result["module"] = "memo"
        elif search(EVENT, routing_text):
            result["module"] = "schedule"

    # A copied phrase may differ in case; unrelated dates/times become unset.
    # The shared date_evidence rule then recovers a unique date from the source.
    for key in ("date_text", "time_text"):
        value = result.get(key)
        if isinstance(value, str):
            match = re.search(re.escape(value), text, re.IGNORECASE)
            result[key] = match.group() if match else None
    if result["module"] == "schedule":
        times = list(re.finditer(TIME, mask_quotes(positive_request(text)), re.IGNORECASE))
        # Preserve AM/PM; accepting only a substring can change noon to midnight.
        result["time_text"] = times[0].group() if len(times) == 1 else None

    if not priority_needs_review(text):
        priorities = [p for p in ("normal", "important", "urgent") if priority_is_grounded(text, p)]
        result["priority"] = priorities[0] if len(priorities) == 1 else None
    else:
        result["priority"] = None

    if result["module"] == "finance":
        finance_text = positive_request(text)
        result = finance_evidence(finance_text, result)
        categories = [name for name, pattern in CATEGORY_WORDS.items() if search(pattern, finance_text)]
        uncertain_category = any(search(r"(?:not|maybe|perhaps|不是|可能)\s*$", finance_text[max(0,m.start()-16):m.start()])
                                 for p in CATEGORY_WORDS.values() for m in re.finditer(p,finance_text,re.IGNORECASE))
        unspecified_category = search(r"(?:leave|keep) (?:the )?category (?:empty|blank|unset)|(?:分类|类别).*(?:不选|留空|没定|未定)", finance_text)
        if unspecified_category:
            result["category"] = None
        elif len(categories) == 1 and not uncertain_category:
            result["category"] = categories[0]
        if result.get("category") not in (*CATEGORY_WORDS, "Others", None):
            result["category"] = None
        result["kind"] = transaction_kind(finance_text)
        if isinstance(result.get("amount"), (int, float)) and not isinstance(result["amount"], bool):
            result["amount"] = str(result["amount"])
        # Recover a missing amount only from one complete explicit currency
        # phrase. Alternatives, signs, grouping and overprecision stay unset.
        money = list(re.finditer(r"(?<![\d.,+\-−])\d{1,9}(?:\.\d{1,2})?\s*(?:元|yuan\b|rmb\b)", finance_text, re.IGNORECASE))
        if search(r"\d+(?:\.\d+)?(?:元)?\s*(?:or|还是|或者)\s*\d", finance_text):
            result["amount"] = None
        if result.get("amount") is None and len(money) == 1 and not search(r"\d\s*(?:or|还是|或者)", finance_text):
            result["amount"] = re.match(r"\d+(?:\.\d+)?", money[0].group()).group()
        result["time_text"] = None
        # A generic transaction verb loses the purchase description. Preserve
        # source wording so the user can still review what the record means.
        if isinstance(result.get("title"), str) and re.fullmatch(r"消费|支出|收入|购买|购物|付款|expense|income|purchase|payment|transaction", result["title"], re.IGNORECASE):
            result["title"] = re.sub(r"^(?:记录|记一笔|记账|record|log|add)\s*", "", text, flags=re.IGNORECASE)[:100]
    else:
        for key in ("amount", "kind", "category", "account"):
            result[key] = None
    if result["module"] in ("memo", "health"):
        result["time_text"] = None
    if result["module"] == "memo" and raw["module"] != "memo":
        source = re.sub(r"^(?:memo|note|task)\s*:\s*|^(?:待办|备忘|任务)[：:]\s*", "", positive_request(text), flags=re.IGNORECASE)
        source = re.split(r"[;；。]|(?<!\w)\.\s|\.\s+(?=[A-Z])", source, maxsplit=1)[0].strip()
        if 0 < len(source) <= 100:
            result["title"] = source
    if result["module"] == "memo" and isinstance(result.get("title"), str) and search(r"备忘(?:录)?$|待办$|任务$|\b(?:memo|reminder|task)$", result["title"]):
        # Replace a generic module label with an exact action clause from the
        # input. Specific generated titles remain untouched.
        notes = result.get("notes")
        if isinstance(notes, str) and 0 < len(notes) <= 100 and notes in text and not search(r"^(?:普通|重要|紧急)?(?:备忘录?|待办|任务)$", notes):
            result["title"] = notes
        else:
            parts = re.split(r"[,，]", text, maxsplit=1)
            if len(parts) == 2 and search(r"(?:创建|新增|添加).*?(?:备忘录|待办|任务)", parts[0]) and 0 < len(parts[1].strip()) <= 100:
                result["title"] = parts[1].strip(" 。.")
            elif 0 < len(parts[0].strip()) <= 100:
                result["title"] = parts[0].strip(" 。.")
    if result["module"] == "health":
        # This workflow opens a fixed system-usage screen; its label should not
        # inherit a translated or invented model description.
        result["title"] = "查看手机使用情况" if re.search(r"[\u4e00-\u9fff]", text) else "View phone usage"
    result["title"] = recover_title(text, result.get("title"), result["module"],
                                    result.get("date_text"), result.get("time_text"))
    return result
