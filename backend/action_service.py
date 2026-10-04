"""One local-model request routes to a reviewable module action, never a DB write."""
import re
import uuid
from decimal import Decimal
from task_service import (SCHEMA, SYSTEM, ServiceError, request_context, resolve_date,
                          validate_extraction, call_local_model, is_smalltalk)
from action_rules import multiple_actions, date_evidence, finance_evidence, search, USAGE, HEALTH_PAGE, unsupported_operation
from text_evidence import clock_value

CATEGORIES = ["Food & Drinks", "Transport", "Shopping", "Entertainment", "Bills", "Salary", "Scholarship", "Part-time Job", "Gift", "Others"]
ACCOUNTS = ["Cash", "Bank Card", "Credit Card", "Alipay", "WeChat", "Others"]
PROPERTIES = dict(SCHEMA["properties"], module={"type":"string", "enum":["memo","finance","schedule","health","unclear"]},
    amount={"type":["string","null"]}, kind={"type":["string","null"],"enum":["expense","income",None]},
    category={"type":["string","null"],"enum":CATEGORIES+[None]},
    account={"type":["string","null"],"enum":ACCOUNTS+[None]}, time_text={"type":["string","null"]})
ACTION_SCHEMA = {"type":"object", "additionalProperties":False,"properties":PROPERTIES,"required":list(PROPERTIES)}
ACTION_SYSTEM = SYSTEM + """
Route ONE requested action. memo = notes, to-dos, deadlines. finance = recording an
expense or income already incurred, not a future plan to spend. schedule = appointments,
meetings, calendar events with a date/time. health = view phone screen-time/app usage ONLY.
Health is not exercise, water, sleep or medical records. Unsupported requests => unclear.
Never split one request into writes to several modules. Multiple actions => multiple_tasks.
A requested module hint may disambiguate, but incompatible actions must be unclear.
When the user explicitly selects memo, finance or schedule, use that destination and
extract the fields that are available. Leave missing fields null for manual completion.
For finance amount copy positive numeric amount without currency, kind expense or income
only when explicitly clear; category can be inferred from the purchase; account null if absent.
Keep finance description in title. Preserve the full date phrase in date_text.
Always extract explicit today/Today/TODAY/今天 as date_text, including for transactions.
For schedule time_text copy the complete explicit clock-time phrase, otherwise null.
Set amount/kind/category/account/time_text null when inapplicable. Do not invent amounts,
accounts, dates or clock times. Health title should describe viewing phone usage, not advice.
"""


def clock_time(value):
    return clock_value(value)


def create_action(body, provider=None, now=None):
    if not isinstance(body, dict) or set(body) != {"text","timezone","module"}:
        raise ServiceError("invalid_request",400)
    hint = body["module"]
    if hint not in ("auto","memo","finance","schedule","health"): raise ServiceError("invalid_module",400)
    text, zone, today = request_context({k:body[k] for k in ("text","timezone")}, now)
    unclear = {"status":"clarification","reason":"unclear","module":None,"draft":None,"fields":None}
    if is_smalltalk(text): return unclear
    if multiple_actions(text): return dict(unclear, reason="multiple_tasks")
    if unsupported_operation(text): return dict(unclear, reason="unsupported_action")
    raw, _ = (provider(text) if provider else call_local_model(
        text, ACTION_SCHEMA, ACTION_SYSTEM + "\nRequested module hint: " + hint))
    if not isinstance(raw,dict) or set(raw) != set(PROPERTIES): raise ServiceError("invalid_model_output",502)
    if raw["intent"] not in ("single_task","multiple_tasks","unclear") or raw["module"] not in ("memo","finance","schedule","health","unclear"):
        raise ServiceError("invalid_model_output",502)
    for key in PROPERTIES:
        if raw[key] is not None and (not isinstance(raw[key],str) or len(raw[key]) > 2000): raise ServiceError("invalid_model_output",502)
    if raw["kind"] not in ("expense","income",None) or raw["category"] not in CATEGORIES+[None] or raw["account"] not in ACCOUNTS+[None]:
        raise ServiceError("invalid_model_output",502)
    if raw["intent"] != "single_task" or raw["module"] == "unclear":
        return dict(unclear, reason=raw["intent"])
    module = raw["module"]
    if hint in ("memo", "finance", "schedule"):
        module = hint  # Explicit user correction chooses the destination, never writes data.
    elif hint != "auto" and module != hint:
        return unclear
    if module == "finance" and re.search(r"[$€£]|美元|美金|欧元|英镑|日元|港币|港元|\b(?:USD|EUR|GBP|JPY|HKD|dollars?|euros?|pounds?|yen)\b", text, re.IGNORECASE):
        return dict(unclear, reason="unsupported_currency")
    if module == "health" and not search(USAGE, text):
        # Opening Health is allowed; health logging/advice is not implemented.
        if not search(HEALTH_PAGE, text):
            return dict(unclear, reason="health_unsupported")
    if module == "health" and re.search(r"昨天|前天|上周|上个月|昨日|yesterday|last week|last month", text, re.IGNORECASE):
        return dict(unclear, reason="health_today_only")
    if module == "finance": raw = finance_evidence(text, raw)
    if not raw["title"] or len(raw["title"]) > 100 or raw["notes"] is None: raise ServiceError("invalid_model_output",502)
    raw = dict(raw, date_text=date_evidence(text, raw["date_text"], today))
    if module == "memo":
        result = validate_extraction({k:raw[k] for k in SCHEMA["required"]},text,zone,today)
        return dict(result,module=module,fields=None)
    date_text, time_text = raw["date_text"], raw["time_text"]
    for phrase in (date_text,time_text):
        if phrase is not None and (not phrase or phrase not in text): raise ServiceError("ungrounded_field",502)
    # A time suffix may be extracted together with the date; remove only that exact suffix.
    date_phrase = date_text
    if date_phrase and time_text and date_phrase.endswith(time_text):
        date_phrase = date_phrase[:-len(time_text)].strip()
        date_phrase = re.sub(r"\s+at$", "", date_phrase).strip()
    date_value = resolve_date(date_phrase,today)
    # Calendar records may be historical, just like manually entered events.
    # Preserve an explicit date for review; never silently move it into next week.
    amount = None
    if raw["amount"] is not None:
        value = raw["amount"]
        if re.fullmatch(r"\d{1,9}(?:\.\d{1,2})?",value) and re.search(r"(?<![\d.,+\-−])"+re.escape(value)+r"(?![\d.]|,\d)",text):
            if Decimal(value) > 0: amount = value
    fields = {"id":str(uuid.uuid4()),"title":raw["title"],"notes":raw["notes"],
        "date":date_value.isoformat() if date_value else None,"time":clock_time(time_text),
        "amount":amount,"kind":raw["kind"],"category":raw["category"],"account":raw["account"],"timezone":zone}
    return {"status":"draft","reason":None,"module":module,"draft":None,"fields":fields}
