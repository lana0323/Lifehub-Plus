"""Recover a lost action subject from source text, without changing other fields."""
import re


def recover_title(text, title, module, date_text=None, time_text=None):
    if not isinstance(title, str) or not title.strip():
        return title
    # An explicit user title is intentional, even if it is a generic noun.
    if re.search(r'\b(?:title|titled|named|called)\s*[:：]?\s*["“]|(?:标题|名称|名字)(?:为|是|叫)?\s*[:：]?\s*["“]', text, re.I):
        return title

    if module == "finance":
        # A secondary explanation (lost receipt, forgotten price, etc.) must
        # not replace what was bought/received. Retain a concise grounded title
        # if it already refers to the main transaction clause.
        clauses = re.split(r"\s+\b(?:but|however|although)\b\s+|[;；。]|但是|不过|但(?=我|是|没|忘|不|收据|发票)", text, maxsplit=1, flags=re.I)
        primary = clauses[0].strip(" ,，.。")
        transaction = re.search(r"\b(?:bought|purchased|paid|spent|received|earned|cost)\b|买了|购买|花了|花费|支出|收入|收到|支付|付了", primary, re.I)
        if len(clauses) == 1 or not transaction:
            return title
        if title.casefold() in primary.casefold():
            return title
        if re.search(r"[\u4e00-\u9fff]", title):
            grounded = any(token in primary for token in re.findall(r"[\u4e00-\u9fff]{2,}", title))
        else:
            ignored = {"a", "an", "the", "for", "of", "to", "my", "i", "on", "in", "with", "and", "expense", "transaction", "payment", "purchase"}
            terms = set(re.findall(r"[a-z]+", title.casefold())) - ignored
            grounded = bool(terms & set(re.findall(r"[a-z]+", primary.casefold())))
        if not grounded:
            return bounded_source(primary)

    if module == "schedule" and re.fullmatch(r"取件|领取|会议|课程|预约|面试|活动|聚会|体检", title):
        # A generic event noun can be the suffix of a specific event. Remove
        # only grounded date/time prefixes, then preserve its source modifier.
        for clause in re.split(r"[,，;；。]", text):
            if title not in clause:
                continue
            source = clause
            for phrase in (date_text, time_text):
                if phrase and phrase in source:
                    source = source.replace(phrase, " ", 1)
            source = re.sub(r"^\s*(?:请|帮我|给我)?\s*(?:安排|添加|新增|创建|记录|填|保存)?\s*(?:一个|一次|一场)?\s*的?", "", source)
            match = re.search(r"([\u4e00-\u9fff]{1,24}" + re.escape(title) + r")", source)
            if match:
                candidate = match.group(1).rsplit("的", 1)[-1]
                if len(candidate) > len(title):
                    return candidate
    return title


def bounded_source(text):
    """The title limit is 100; avoid cutting an English word when possible."""
    if len(text) <= 100:
        return text
    prefix = text[:100]
    if " " in prefix and not re.search(r"[\u4e00-\u9fff]", prefix):
        prefix = prefix.rsplit(" ", 1)[0]
    return prefix.rstrip(" ,，;；.。")
