"""Conservative input evidence for model drafts; these rules never write records."""
import re
from task_service import resolve_date

DATE_PATTERN = (r"\b(?:this|next)\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"
                r"|[本这下](?:周|星期)[一二三四五六日天]|\bthe day after tomorrow\b"
                r"|\bin \d{1,3} days?\b|\d{1,3}天后|\d{4}年\d{1,2}月\d{1,2}日?"
                r"|\b\d{4}-\d{2}-\d{2}\b|\bthis (?:morning|afternoon|evening)\b|\btoday\b|\btomorrow\b|\byesterday\b|后天|今天|今日|明天|昨天|昨日")
UNCERTAIN = r"\b(?:maybe|perhaps|possibly|might|not|or|undecided|tentative|tbd)\b|可能|也许|或许|或者|待定|不确定|不是|不在|别在|暂定"
USAGE = (r"\b(?:screen[ -]?time|(?:phone|app|device|foreground) usage|(?:phone|app|device) use|digital wellbeing|usage page)\b"
         r"|\b(?:how much time|total duration)\b.*\b(?:phone|apps?|device)\b"
         r"|\busage time\b.*\b(?:phone|apps?|device)\b"
         r"|\b(?:how long|time spent)\b.*\b(?:phone|apps?|device)\b"
         r"|\bhow long\b.*\b(?:applications?|software)\b|\bscreen\b.*\b(?:been on|on time)\b"
         r"|屏幕.*(?:使用|用了|亮了|时长|时间)|(?:手机|应用|软件|设备|apps?).*(?:时长|时间|使用|用了|多久)|使用(?:时长|统计)")
HEALTH_PAGE = r"\b(?:open|show|enter|take me (?:to|into)|go to)\b.*\bhealth\b|(?:打开|进入|跳转到)(?:健康|Health)(?:页面|模块)?"


def positive_request(text):
    """Ignore clauses which explicitly deny a destination, not arbitrary 'not'."""
    patterns = (
        r"(?:\b(?:this is |it is )?not (?:an? )?(?:calendar event|transaction|expense|income)\b[^,;.!]*)",
        r"\b(?:do not|don't|without|not)\s+(?:creating|adding|making|create|add|make)\s+(?:(?:an?|any)\s+)?(?:task|reminder|memo|transaction|event)\b[^,;.!]*",
        r"(?:不需要|不要|不是让你|不)(?:再)?(?:创建|添加|生成|记录|记|提醒)[^，,；;。.!]*",
    )
    for pattern in patterns:
        text = re.sub(pattern, " ", text, flags=re.IGNORECASE)
    return text


def explicit_draft(text):
    return search(r"\b(?:add|create|make|write|save)\s+(?:me\s+)?(?:an?|the|new)\s+(?:new\s+)?(?:task|memo|note|reminder)\b|\b(?:task|memo|note|todo|to-do)\s*:|\bremind me\b|\b(?:on|to) my (?:to-do )?list\b|(?:添加|创建|新增|写|记)(?:一条|一个|个)?(?:待办|任务|备忘|提醒)|(?:待办|备忘|笔记|任务)[：:]|提醒我", positive_request(text))


def unsupported_operation(text):
    """Only draft creation and viewing this device's usage are implemented.

    A task *about* deleting/sending is valid; actually editing existing records,
    querying balances/calendars, controlling apps or sending messages is not.
    Quoted memo content is data, never an executable instruction.
    """
    text = positive_request(text)
    if explicit_draft(text):
        return False
    scoped = re.sub(r'"[^"\n]*"|“[^”\n]*”', " item ", text)
    if search(r"\b(?:delete|remove|erase|clear|edit|change|update|move|reschedule|rearrange|cancel)\b.*\b(?:tasks?|memos?|notes?|reminders?|list|calendar|events?|appointments?|lesson|transactions?|expense|income|usage|records?|history)\b|\bmark\b.*\b(?:completed|done)\b", scoped):
        return True
    if search(r"(?:删掉|删除|清空|取消|改到|修改|更新|重新排期|标成完成|标记.*完成)", scoped) and search(r"任务|待办|日程|日历|例会|课|账单|账|记录|使用|支出|收入", scoped):
        return True
    if search(r"\b(?:send|email|text)\b.*\b(?:sms|e-?mail|message)\b|(?:发送|发一封|发个).*(?:邮件|短信|消息)", scoped):
        return True
    if search(r"\b(?:block|lock|limit|restrict|uninstall)\b.*\b(?:apps?|applications?|screen|phone)\b|(?:锁住|锁定|禁用|卸载|限制).*(?:软件|应用|手机)", scoped):
        return True
    if search(USAGE, scoped) and search(r"\b(?:roommate|another person|someone else|other people|friend)'?s?\b|另一个人|别人|室友|他人", scoped):
        return True
    if not search(USAGE, scoped) and search(r"\b(?:look through|search|find|tell me|show|calculate|predict)\b.*\b(?:saved events?|free|available|balance|largest|total|savings|transactions)\b|(?:看看|查询|查看|搜索).*(?:日历|日程|账单|余额)|(?:余额|空闲|存多少钱).*(?:算|预测)?", scoped):
        return True
    return False


def search(pattern, text):
    return re.search(pattern, text, re.IGNORECASE)


def multiple_actions(text):
    # A conjunction alone is not enough: "buy tea and coffee" is one action.
    # Require a new command and evidence for two workflows, or two explicit commands.
    commands = r"(?:record|log|add|schedule|show|check|open|tell me|remind|create|记录|记下|记一笔|记账|添加|创建|新增|生成|安排|查看|查询|打开|提醒)"
    text = re.sub(r'"[^"\n]*"|“[^”\n]*”', " quoted content ", text)
    parts = re.split(r"(?:\band\b|\bthen\b|[;；]|(?:，|,)?\s*(?:再|然后|并且))\s*(?=" + commands + r")", text, flags=re.IGNORECASE)
    if len(parts) < 2:
        return False
    first = parts[0].strip()
    return bool(search(r"^(?:please\s+|先)?" + commands + r"|(?:安排|记录|记下|记一笔|记账|查看|查询|添加)|" + USAGE, first))


def date_evidence(text, extracted, today):
    """Recover one grounded date, including equivalent Chinese/ISO formatting.

    Ambiguity in the user's input wins even if the model omitted its qualifier.
    An unrelated invented date is deliberately left for the grounding validator.
    """
    # Date-like names are not temporal instructions. Keep the original source
    # for exact grounding, but mask explicitly named titles/stores for discovery.
    evidence = re.sub(r'(?:\b(?:called|named|titled)\s+|(?:名叫|店名叫|名为))(["“]).*?["”]', " ", text, flags=re.IGNORECASE)
    references = list(re.finditer(DATE_PATTERN, evidence, re.IGNORECASE))
    # Uncertainty must concern the date. Alternative times/amounts/accounts do
    # not erase an explicit day elsewhere in the sentence.
    uncertain_date = any(search(r"(?:maybe|perhaps|possibly|might(?: be)?|not|可能|也许|或许|不是|不在|别在|暂定)\s*$", evidence[max(0,m.start()-24):m.start()])
                         or search(r"^(?:\s+(?:is\s+)?(?:tentative|undecided|tbd)\b|(?:可能|待定|不确定|暂定))", evidence[m.end():m.end()+24])
                         for m in references)
    uncertain_date = uncertain_date or bool(search(r"\b(?:day|date) (?:is |remains |was )?(?:tentative|undecided|tbd)\b|\bkeep the (?:day|date) tentative\b|(?:日期|哪天).*(?:待定|不确定|没定)", evidence))
    if references and (len(references) > 1 or uncertain_date):
        return text  # Unsupported full phrase keeps the date unset and reviewable.
    if extracted:
        match = search(re.escape(extracted), text)
        if match:
            extracted = match.group()
    if len(references) == 1:
        original = references[0].group()
        remainder = re.sub(DATE_PATTERN, " ", evidence, flags=re.IGNORECASE)
        if search(r"\b(?:next|last|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b|[上下本这]周|\d{1,2}月|\d{1,2}/\d{1,2}", remainder):
            return text
        value = resolve_date(original, today)
        if extracted is None or (extracted in text and resolve_date(extracted, today) is None):
            return original
        if value is not None and resolve_date(extracted, today) == value:
            return original
    return extracted


CATEGORY_WORDS = {
    "Food & Drinks": r"\b(?:breakfast|lunch|dinner|meal|coffee|tea|juice|snack|groceries|restaurant|pizza|sandwich|bakery|bread|cake|milk|yogurt|noodles?|salad|sushi)\b|早餐|午餐|晚餐|吃饭|餐费|咖啡|奶茶|茶叶|红茶|绿茶|买茶(?!几)|杯茶|果汁|点心|零食|买菜|面包|蛋糕|牛奶|酸奶|面条|餐厅|饮料|寿司",
    "Transport": r"\b(?:transport|taxi|bus|train|subway|metro|fare|parking|petrol|gasoline)\b|交通|打车|公交|地铁|车票|停车|汽油",
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
    text = positive_request(text)
    categories = [name for name, pattern in CATEGORY_WORDS.items() if search(pattern, text)]
    # Respect model's specific category; fill only missing/generic classifications.
    if len(categories) == 1 and raw["category"] in (None, "Others"):
        result["category"] = categories[0]
    accounts = [name for name, pattern in ACCOUNT_WORDS.items() if search(pattern, text)]
    uncertain_account = any(search(r"(?:not|maybe|perhaps|possibly|不是|不确定|可能|也许)\s*(?:by |with |是)?$", text[max(0,m.start()-20):m.start()])
                            for pattern in ACCOUNT_WORDS.values() for m in re.finditer(pattern,text,re.IGNORECASE))
    result["account"] = accounts[0] if len(accounts) == 1 and not uncertain_account else None
    generic = r"(?:record|log|add)(?: an?| the)?(?: expense| income| transaction| purchase| entry)?|记账|记录|记一笔|记录支出|记录收入"
    if re.fullmatch(generic, (raw["title"] or "").strip(" .。:："), re.IGNORECASE):
        # Keep the user's actual transaction description, not an empty command title.
        description = re.sub(r"^(?:记账|记录|记一笔)\s*[:：]?\s*", "", text).strip()
        result["title"] = description[:100]
        if len(description) > 100 and not raw["notes"]:
            result["notes"] = text
    return result
