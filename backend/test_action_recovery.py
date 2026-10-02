import unittest
from datetime import datetime, timezone
from action_service import create_action, PROPERTIES
from task_service import ServiceError


class RecoveryTests(unittest.TestCase):
    def action(self, text, module="finance", **changes):
        raw = dict.fromkeys(PROPERTIES)
        raw.update(intent="single_task", module=module, title="Entry", notes="")
        raw.update(changes)
        return create_action(dict(text=text, timezone="UTC", module="auto"),
                             lambda _: (raw, {}), datetime(2026, 10, 1, 12, tzinfo=timezone.utc))

    def test_finance_categories_and_accounts_recover_from_explicit_evidence(self):
        for text, category, account in [
            ("Paid for pizza with Alipay", "Food & Drinks", "Alipay"),
            ("Bought shoes using a debit card", "Shopping", "Bank Card"),
            ("今天薪水到账银行卡", "Salary", "Bank Card"),
            ("现金支付电费", "Bills", "Cash"),
        ]:
            with self.subTest(text=text):
                result = self.action(text)["fields"]
                self.assertEqual(category, result["category"])
                self.assertEqual(account, result["account"])
        self.assertIsNone(self.action("Bought tea and shoes")["fields"]["category"])
        self.assertIsNone(self.action("Not cash, maybe Alipay")["fields"]["account"])
        self.assertIsNone(self.action("Cash or credit card")["fields"]["account"])

    def test_generic_finance_title_preserves_purchase_description(self):
        result = self.action("记账：今天午餐花了28元", title="记账")["fields"]
        self.assertIn("午餐", result["title"])

    def test_ambiguous_input_overrides_confident_model_date(self):
        for module in ("memo", "schedule", "finance"):
            for text in ("Maybe tomorrow", "Perhaps tomorrow", "也许明天", "不是明天", "Tomorrow or next Monday"):
                result = self.action(text, module, date_text="tomorrow" if "tomorrow" in text.lower() else "明天")
                fields = result.get("draft") or result["fields"]
                self.assertIsNone(fields.get("dueDate") if module == "memo" else fields["date"])

    def test_date_format_recovery_does_not_accept_invented_date(self):
        result = self.action("2026年10月14日会议", "schedule", date_text="2026-10-14")
        self.assertEqual("2026-10-14", result["fields"]["date"])
        result = self.action("Meeting in 4 days", "schedule", date_text="4 days")
        self.assertEqual("2026-10-05", result["fields"]["date"])
        with self.assertRaises(ServiceError):
            self.action("2026年10月14日会议", "schedule", date_text="2026-10-15")
        self.assertEqual("2026-10-06", self.action("下周二开预算会，不是记账", "schedule", date_text="下周二")["fields"]["date"])

    def test_multiple_commands_rejected_before_inference(self):
        for text in ("Show screen time and record a bus fare", "明天安排开会，再记一笔午餐支出"):
            def never(_):
                self.fail("Multiple commands should not call a model")
            result = create_action(dict(text=text, timezone="UTC", module="auto"), never)
            self.assertEqual("multiple_tasks", result["reason"])
        self.assertEqual("draft", self.action("Buy tea and coffee", "memo")["status"])
        self.assertEqual("draft", self.action("Add a meeting about finance", "schedule")["status"])

    def test_health_does_not_turn_unsupported_logging_into_usage_page(self):
        for text in ("Log a swim in Health", "记录昨天睡了8小时", "Record 800 ml water"):
            result = self.action(text, "health", date_text="invented")
            self.assertEqual("health_unsupported", result["reason"])
        self.assertEqual("draft", self.action("Open Health", "health")["status"])
        self.assertEqual("draft", self.action("查看应用使用时长", "health")["status"])
        self.assertEqual("draft", self.action("Show how long I used my phone today", "health")["status"])
        self.assertEqual("draft", self.action("查询今天各个app使用情况", "health")["status"])

    def test_invalid_amount_cannot_be_repaired_by_dropping_sign_or_precision(self):
        for text, amount in [("Spent -45 yuan", "45"), ("花了−45元", "45"),
                             ("Spent 45.678 yuan", "45"), ("Spent 1,045 yuan", "045")]:
            self.assertIsNone(self.action(text, amount=amount)["fields"]["amount"])

    def test_chinese_unsupported_currencies_are_explicit(self):
        for currency in ("欧元", "美元", "英镑", "日元", "港币"):
            self.assertEqual("unsupported_currency", self.action("咖啡12" + currency)["reason"])

    def test_amount_followed_by_punctuation_is_not_a_thousands_group(self):
        self.assertEqual("27", self.action("Paid 27, using cash", amount="27")["fields"]["amount"])
        self.assertIsNone(self.action("Paid 27,000 yuan", amount="27")["fields"]["amount"])


if __name__ == "__main__":
    unittest.main()
