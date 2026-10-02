"""Conservative input evidence for model drafts; these rules never write records."""
import re
from task_service import resolve_date

DATE_PATTERN = (r"\b(?:this|next)\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"
                r"|[本这下](?:周|星期)[一二三四五六日天]|\bthe day after tomorrow\b"
                r"|\bin \d{1,3} days?\b|\d{1,3}天后|\d{4}年\d{1,2}月\d{1,2}日?"
                r"|\b\d{4}-\d{2}-\d{2}\b|\btoday\b|\btomorrow\b|\byesterday\b|后天|今天|今日|明天|昨天")
UNCERTAIN = r"\b(?:maybe|perhaps|possibly|not|or|undecided|tentative|tbd)\b|可能|也许|或许|或者|待定|不确定|不是|不在|别在|暂定"
USAGE = (r"\b(?:screen[ -]?time|(?:phone|app|device|foreground) usage|digital wellbeing)\b"
         r"|\b(?:how long|time spent)\b.*\b(?:phone|apps?|device)\b"
         r"|屏幕使用|(?:手机|应用|设备|apps?).*(?:时长|时间|使用|多久)|使用时长")


def search(pattern, text):
    return re.search(pattern, text, re.IGNORECASE)


def multiple_actions(text):
    # A conjunction alone is not enough: "buy tea and coffee" is one action.
    # Require a new command and evidence for two workflows, or two explicit commands.
    commands = r"(?:record|log|add|schedule|show|check|open|tell me|remind|create|记录|记一笔|记账|添加|安排|查看|查询|打开|提醒)"
    parts = re.split(r"(?:\band\b|\bthen\b|[;；]|(?:，|,)?\s*(?:再|然后|并且))\s*(?=" + commands + r")", text, flags=re.IGNORECASE)
    if len(parts) < 2:
        return False
    first = parts[0].strip()
    return bool(search(r"^(?:please\s+)?" + commands + r"|(?:安排|记录|记一笔|记账|查看|查询|添加)|" + USAGE, first))


def date_evidence(text, extracted, today):
    """Recover one grounded date, including equivalent Chinese/ISO formatting.

    Ambiguity in the user's input wins even if the model omitted its qualifier.
    An unrelated invented date is deliberately left for the grounding validator.
    """
    references = list(re.finditer(DATE_PATTERN, text, re.IGNORECASE))
    clauses = re.split(r"[,，;；.!。！]", text)
    uncertain_date = any(search(UNCERTAIN, clause) and
                         (search(DATE_PATTERN, clause) or re.fullmatch(UNCERTAIN, clause.strip(), re.IGNORECASE))
                         for clause in clauses)
    if references and (len(references) > 1 or uncertain_date):
        return text  # Unsupported full phrase keeps the date unset and reviewable.
    if extracted:
        match = search(re.escape(extracted), text)
        if match:
            extracted = match.group()
    if len(references) == 1:
        original = references[0].group()
        remainder = re.sub(DATE_PATTERN, " ", text, flags=re.IGNORECASE)
        if search(r"\b(?:next|last|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b|[上下本这]周|\d{1,2}月|\d{1,2}/\d{1,2}", remainder):
            return text
        value = resolve_date(original, today)
        if extracted is None or (extracted in text and resolve_date(extracted, today) is None):
            return original
        if value is not None and resolve_date(extracted, today) == value:
            return original
    return extracted


CATEGORY_WORDS = {
    "Food & Drinks": r"\b(?:breakfast|lunch|dinner|meal|coffee|tea|juice|snack|groceries|restaurant|pizza|sandwich)\b|早餐|午餐|晚餐|吃饭|餐费|咖啡|奶茶|果汁|点心|零食|买菜",
    "Transport": r"\b(?:taxi|bus|train|subway|metro|fare|parking|petrol|gasoline)\b|打车|公交|地铁|车票|停车|汽油",
    "Shopping": r"\b(?:jacket|coat|shirt|shoes|clothes|notebook|pencil|stationery|headphones|book)\b|外套|衣服|鞋|笔记本|铅笔|文具|耳机|买书",
    "Entertainment": r"\b(?:cinema|movie|concert|game|theatre)\b|电影|演唱会|游戏|剧院",
    "Bills": r"\b(?:rent|electricity|utilities|water bill|internet bill|phone bill)\b|房租|水费|电费|网费|话费",
    "Salary": r"\b(?:salary|wages|paycheck)\b|工资|薪水",
    "Scholarship": r"\bscholarship\b|奖学金",
    "Part-time Job": r"\b(?:part[ -]time|freelance)\b|兼职",
    "Gift": r"\bgift\b|礼物|红包",
}
ACCOUNT_WORDS = {"Cash": r"现金|\bcash\b", "Bank Card": r"银行卡|借记卡|\b(?:bank|debit) card\b",
                 "Credit Card": r"信用卡|\bcredit card\b", "Alipay": r"支付宝|\balipay\b",
                 "WeChat": r"微信|\bwechat\b", "Others": r"其他账户|\bother account\b"}


def finance_evidence(text, raw):
    result = dict(raw)
    uncertain = search(r"\b(?:not|maybe|perhaps|or)\b|不是|不确定|可能|或者", text)
    categories = [name for name, pattern in CATEGORY_WORDS.items() if search(pattern, text)]
    # Respect model's specific category; fill only missing/generic classifications.
    if not uncertain and len(categories) == 1 and raw["category"] in (None, "Others"):
        result["category"] = categories[0]
    accounts = [name for name, pattern in ACCOUNT_WORDS.items() if search(pattern, text)]
    result["account"] = accounts[0] if len(accounts) == 1 and not uncertain else None
    generic = r"(?:record|log|add)(?: an?| the)?(?: expense| income| transaction| purchase| entry)?|记账|记录|记一笔|记录支出|记录收入"
    if re.fullmatch(generic, (raw["title"] or "").strip(" .。:："), re.IGNORECASE):
        # Keep the user's actual transaction description, not an empty command title.
        description = re.sub(r"^(?:记账|记录|记一笔)\s*[:：]?\s*", "", text).strip()
        result["title"] = description[:100]
        if len(description) > 100 and not raw["notes"]:
            result["notes"] = text
    return result
