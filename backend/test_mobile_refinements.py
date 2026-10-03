import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "app/src/main/python"))
import mobile_bridge


class MobileRefinementTests(unittest.TestCase):
    def validate(self, text, module, title="Entry", **fields):
        request = json.dumps(dict(text=text, timezone="UTC", module="auto"))
        raw = dict(intent="single_task", module=module, title=title, notes="", **fields)
        return json.loads(mobile_bridge.validate(request, json.dumps(raw), "2026-10-02T12:00:00+00:00"))

    def test_explicit_tasks_win_over_calendar_nouns(self):
        result = self.validate("Add a task to check the conference notes, high priority", "schedule", "Check conference notes")
        self.assertEqual(result["module"], "memo")
        self.assertEqual(result["draft"]["priority"], "important")

    def test_negated_expense_does_not_steal_calendar_event(self):
        result = self.validate("日历添加明天10:00的预算评审会，这不是支出", "schedule", "预算评审会", date_text="明天", time_text="10:00")
        self.assertEqual(result["module"], "schedule")
        self.assertEqual(result["fields"]["time"], "10:00")

    def test_account_uncertainty_is_not_a_reminder(self):
        result = self.validate("Paid 13 yuan for tea by cash or WeChat; I cannot remember which", "finance", "Tea", amount="13", kind="expense", account="Cash")
        self.assertEqual(result["module"], "finance")
        self.assertIsNone(result["fields"]["account"])
        self.assertEqual(result["fields"]["kind"], "expense")

    def test_missing_account_does_not_erase_expense_direction(self):
        result = self.validate("Log 44 yuan for a cinema ticket, account not recorded", "finance", "Cinema ticket", amount="44", kind="expense")
        self.assertEqual(result["fields"]["kind"], "expense")
        self.assertIsNone(result["fields"]["account"])

    def test_health_query_variants_keep_an_english_label(self):
        for text in ["How much time did I spend on my phone today?", "Show usage time for each app", "Open the usage page"]:
            result = self.validate(text, "health", "查看手机使用时长")
            self.assertEqual(result["module"], "health")
            self.assertEqual(result["fields"]["title"], "View phone usage")

    def test_unsupported_health_logging_is_rejected_before_generation(self):
        for text in ["Record a swim in Health", "Log my body weight of 75 kg"]:
            request = json.dumps(dict(text=text, timezone="UTC", module="auto"))
            self.assertEqual(json.loads(mobile_bridge.prepare(request))["response"]["reason"], "health_unsupported")
            self.assertEqual(json.loads(mobile_bridge.validate(request, '{"weight":"75 kg"}'))["status"], "clarification")

    def test_grounded_event_metadata_becomes_notes(self):
        result = self.validate("Schedule lunch with Alex at 12:00", "schedule", "Lunch", to="Alex", event_type="event", time_text="12:00")
        self.assertIn("Alex", result["fields"]["notes"])
        with self.assertRaises(Exception):
            self.validate("Schedule lunch", "schedule", "Lunch", to="Invented Person")

    def test_bakery_purchase_preserves_description(self):
        result = self.validate("记录今天在面包店消费26元", "finance", "消费", amount="26", kind="expense", category="Shopping")
        self.assertEqual(result["fields"]["category"], "Food & Drinks")
        self.assertEqual(result["fields"]["kind"], "expense")
        self.assertIn("面包店", result["fields"]["title"])

    def test_health_lecture_is_a_calendar_event_not_health_logging(self):
        result = self.validate("安排明天14:00的运动健康讲座", "schedule", "健康讲座", date_text="明天", time_text="14:00")
        self.assertEqual(result["module"], "schedule")

    def test_recording_an_exercise_event_is_not_body_tracking(self):
        result = self.validate("Record a running workshop tomorrow as an event", "schedule", "Running workshop", date_text="tomorrow")
        self.assertEqual(result["module"], "schedule")

    def test_negated_task_creation_does_not_override_usage_query(self):
        for text in ["Show screen time without creating a task", "显示屏幕使用数据，不创建待办"]:
            self.assertEqual(self.validate(text, "health")["module"], "health")

    def test_incoming_payment_and_fare_direction(self):
        result = self.validate("Scholarship income 2500 yuan paid into my bank card", "finance", "Scholarship", amount="2500", kind="income")
        self.assertEqual(result["fields"]["kind"], "income")
        result = self.validate("Log a bus fare of 8 yuan; I did not specify an account", "finance", "Bus fare", amount="8", kind="expense")
        self.assertEqual(result["fields"]["kind"], "expense")
        self.assertIsNone(result["fields"]["account"])

    def test_chinese_creation_commands_are_multiple_actions(self):
        request = json.dumps(dict(text="创建备忘录记录花园计划，并且安排明天的聚会", timezone="UTC", module="auto"))
        self.assertEqual(json.loads(mobile_bridge.prepare(request))["response"]["reason"], "multiple_tasks")


if __name__ == "__main__":
    unittest.main()
