"""The Android adapter must preserve validation and must never contact a model server."""
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "app/src/main/python"))
import mobile_bridge
from action_service import PROPERTIES


class MobileBridgeTests(unittest.TestCase):
    def request(self, text, module="auto"):
        return json.dumps(dict(text=text, timezone="Asia/Shanghai", module=module))

    def test_smalltalk_and_multiple_requests_need_no_model(self):
        for text in ["Hello", "记录午餐28元，然后安排明天开会"]:
            result = json.loads(mobile_bridge.prepare(self.request(text)))
            self.assertEqual(result["response"]["status"], "clarification")

    def test_shared_validation_resolves_dates_and_preserves_confirmation_contract(self):
        raw = dict.fromkeys(PROPERTIES)
        raw.update(intent="single_task", module="finance", title="午餐", notes="", amount="28", kind="expense", date_text="今天")
        with patch("task_service.call_local_model", side_effect=AssertionError("Network is forbidden")):
            actual = json.loads(mobile_bridge.validate(self.request("今天午餐28元，支付宝支付"), json.dumps(raw), "2026-10-02T12:00:00+00:00"))
        self.assertEqual(actual["fields"]["date"], "2026-10-02")
        self.assertEqual(actual["fields"]["category"], "Food & Drinks")
        self.assertEqual(actual["fields"]["account"], "Alipay")
        self.assertEqual(actual["status"], "draft")

    def test_malformed_or_unbounded_output_is_rejected(self):
        for output in ["not JSON", "{}", "x" * 16001]:
            with self.assertRaises(Exception):
                mobile_bridge.validate(self.request("Buy milk"), output)

    def test_ambiguous_date_is_left_for_user_review(self):
        raw = dict.fromkeys(PROPERTIES)
        raw.update(intent="single_task", module="schedule", title="Meeting", notes="", date_text="tomorrow")
        actual = json.loads(mobile_bridge.validate(self.request("Meeting tomorrow or next Friday"), json.dumps(raw), "2026-10-02T12:00:00+00:00"))
        self.assertIsNone(actual["fields"]["date"])

    def test_absent_optional_fields_remain_empty_without_invention(self):
        raw = {"intent": "single_task", "module": "schedule", "title": "Call"}
        actual = json.loads(mobile_bridge.validate(self.request("Schedule a call"), json.dumps(raw)))
        self.assertIsNone(actual["fields"]["date"])
        self.assertIsNone(actual["fields"]["time"])
        self.assertEqual(actual["fields"]["notes"], "")

    def test_schema_echo_and_unknown_keys_are_rejected(self):
        for raw in [
            {"type": "object", "properties": {}},
            {"intent": "single_task", "module": "schedule", "title": "Call", "execute": True},
        ]:
            with self.assertRaises(Exception):
                mobile_bridge.validate(self.request("Schedule a call"), json.dumps(raw))

    def test_unrelated_date_is_removed_for_manual_completion(self):
        raw = {"intent": "single_task", "module": "schedule", "title": "Call", "date_text": "2035-01-01"}
        result = json.loads(mobile_bridge.validate(self.request("Schedule a call"), json.dumps(raw)))
        self.assertIsNone(result["fields"]["date"])

    def test_deadline_is_not_an_appointment_and_optional_nulls_are_safe(self):
        raw = {"intent":"single_task", "module":"schedule", "title":"Finish essay", "date_text":"tomorrow", "priority":"null", "kind":"appointment"}
        result = json.loads(mobile_bridge.validate(self.request("Finish essay tomorrow, high priority"), json.dumps(raw)))
        self.assertEqual(result["module"], "memo")
        self.assertEqual(result["draft"]["priority"], "important")

    def test_source_corrects_finance_category_but_not_ambiguous_account(self):
        raw = {"intent":"single_task", "module":"finance", "title":"Tea", "amount":"9", "kind":"income", "category":"Shopping", "account":"Cash"}
        result = json.loads(mobile_bridge.validate(self.request("Paid 9 for tea by cash or bank card"), json.dumps(raw)))
        self.assertIsNone(result["fields"]["account"])
        self.assertEqual(result["fields"]["kind"], "expense")

    def test_future_purchase_and_explicit_module_choice_are_preserved(self):
        raw = {"intent":"single_task", "module":"finance", "title":"Buy a pen", "amount":"5", "kind":"expense"}
        result = json.loads(mobile_bridge.validate(self.request("Remind me to buy a pen tomorrow, budget 5 yuan"), json.dumps(raw)))
        self.assertEqual(result["module"], "memo")
        result = json.loads(mobile_bridge.validate(self.request("Paid 5 yuan for a pen", "memo"), json.dumps(raw)))
        self.assertEqual(result["module"], "memo")

    def test_clock_time_retains_full_suffix_and_rejects_alternatives(self):
        raw = {"intent":"single_task", "module":"schedule", "title":"Call", "time_text":"12:30"}
        result = json.loads(mobile_bridge.validate(self.request("A call tomorrow at 12:30 am"), json.dumps(raw)))
        self.assertEqual(result["fields"]["time"], "00:30")
        result = json.loads(mobile_bridge.validate(self.request("A call at 12:30 or 15:00"), json.dumps(raw)))
        self.assertIsNone(result["fields"]["time"])


if __name__ == "__main__":
    unittest.main()
