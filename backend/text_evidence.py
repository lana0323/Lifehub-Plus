"""Small shared grammars for source evidence; no model calls or record writes."""
import re

WEEKDAY = r"monday|tuesday|wednesday|thursday|friday|saturday|sunday"
NUMBER = r"[0-9]{1,3}|[零〇一二两三四五六七八九十]{1,3}"
QUOTED = r'"[^"\n]*"|“[^”\n]*”|「[^」\n]*」'


def mask_quotes(text):
    # Keep offsets so a retained date can still be copied verbatim.
    return re.sub(QUOTED, lambda m: " " * len(m.group()), text)


def number_value(value):
    if re.fullmatch(r"[0-9]{1,3}", value):
        return int(value)
    digits = dict(zip("零〇一二两三四五六七八九", (0, 0, 1, 2, 2, 3, 4, 5, 6, 7, 8, 9)))
    if value in digits:
        return digits[value]
    if "十" in value and value.count("十") == 1:
        a, b = value.split("十")
        if (not a or a in digits) and (not b or b in digits):
            return (digits.get(a, 1) * 10) + digits.get(b, 0)
    return None


TIME_PATTERN = (r"(?<!\d)\d{1,2}:\d{2}(?:\s*[ap]m(?![a-z]))?(?!\d)"
                r"|(?<!\d)\d{1,2}\s*[ap]m(?![a-z])"
                r"|(?:凌晨|上午|下午|晚上|早上|早晨|中午)?(?:" + NUMBER + r")点(?:半|(?:" + NUMBER + r")分?)?"
                r"|\b(?:noon|midnight)\b")


def clock_value(value):
    if value is None:
        return None
    s = value.strip().lower()
    if s in ("noon", "midnight"):
        return "12:00" if s == "noon" else "00:00"
    m = re.fullmatch(r"(\d{1,2})(?::(\d{2}))?\s*(am|pm)?", s)
    if m and (m[2] is not None or m[3]):
        h, minute, ap = int(m[1]), int(m[2] or 0), m[3]
        if minute > 59 or (ap and not 1 <= h <= 12) or (not ap and h > 23):
            return None
        if ap:
            h = h % 12 + (12 if ap == "pm" else 0)
        return f"{h:02}:{minute:02}"
    m = re.fullmatch(r"(凌晨|上午|下午|晚上|早上|早晨|中午)?(" + NUMBER + r")点(?:(半)|(" + NUMBER + r")分?)?", s)
    if m:
        h = number_value(m[2]); minute = 30 if m[3] else number_value(m[4] or "0")
        if h is None or minute is None or not 0 <= h <= 23 or not 0 <= minute <= 59:
            return None
        if m[1] in ("下午", "晚上", "中午") and h < 12:
            h += 12
        elif m[1] in ("凌晨", "上午", "早上", "早晨") and h == 12:
            h = 0
        return f"{h:02}:{minute:02}"
    return None
