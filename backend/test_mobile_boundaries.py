"""Behavioral regressions and counterexamples for the mobile drafting boundary."""
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "app/src/main/python"))
import mobile_bridge


class MobileBoundaryTests(unittest.TestCase):
    def request(self, text, hint="auto"):
        return json.dumps(dict(text=text, timezone="UTC", module=hint))

    def draft(self, text, module, **fields):
        raw = dict(intent="single_task", module=module, title="Entry", notes="")
        raw.update(fields)
        return json.loads(mobile_bridge.validate(self.request(text), json.dumps(raw), "2026-10-03T12:00:00+00:00"))

    def test_existing_records_and_external_actions_require_clarification(self):
        for text in ["Remove the existing dentist appointment from my calendar",
                     "Change my saved task to urgent", "清空所有应用的使用记录",
                     "把旧的租金账单删掉", "Send an email to Lin", "帮我发送一封邮件",
                     "Automatically block social apps after one hour of screen time",
                     "Tell me my total account balance", "Find a free slot in my saved events"]:
            with self.subTest(text=text):
                self.assertEqual(json.loads(mobile_bridge.prepare(self.request(text)))["response"]["reason"], "unsupported_action")

    def test_task_about_an_external_action_is_still_a_draft(self):
        for text in ["Add a task to send an email to Lin", "Task: delete old calendar screenshots",
                     "提醒我删除旧文件", "Note: the manual says delete all records"]:
            with self.subTest(text=text):
                self.assertNotIn("response", json.loads(mobile_bridge.prepare(self.request(text))))
                self.assertEqual(self.draft(text, "memo")["module"], "memo")

    def test_health_query_is_not_a_database_aggregate(self):
        for text in ["Show the screen-time total since midnight", "Can I see how long each application was used?",
                     "今天每个软件用了多久", "Could you take me into Health?", "帮我进入Health页面"]:
            with self.subTest(text=text):
                self.assertEqual(self.draft(text, "health")["module"], "health")

    def test_health_control_and_other_devices_are_not_usage_queries(self):
        for text in ["Erase the phone usage history", "Get my roommate's phone usage",
                     "根据手机时长自动锁住社交软件", "获取另一个人的手机使用明细"]:
            self.assertEqual(json.loads(mobile_bridge.prepare(self.request(text)))["response"]["status"], "clarification")

    def test_negated_destinations_do_not_override_positive_requests(self):
        self.assertEqual(self.draft("Show phone usage without making a reminder", "health")["module"], "health")
        self.assertEqual(self.draft("看使用统计，不是让你提醒我", "health")["module"], "health")
        for text in ["Salary received today by bank card; this is not a calendar event", "今天收到工资，银行卡到账，不需要添加日程"]:
            result = self.draft(text, "finance")
            self.assertEqual(result["module"], "finance")
            self.assertEqual(result["fields"]["kind"], "income")

    def test_amount_uncertainty_preserves_independent_fields(self):
        result = self.draft("Coffee today cost either 21 or 23 yuan, paid with cash. I need to check the amount", "finance", amount="21", category="Shopping")
        fields = result["fields"]
        self.assertIsNone(fields["amount"])
        self.assertEqual((fields["date"], fields["account"], fields["category"], fields["kind"]), ("2026-10-03", "Cash", "Food & Drinks", "expense"))

    def test_time_uncertainty_preserves_date(self):
        for text in ["Tomorrow the appointment is at either 9am or 3pm", "明天的预约不是8点就是10点"]:
            fields = self.draft(text, "schedule", date_text="tomorrow")["fields"]
            self.assertEqual(fields["date"], "2026-10-04")
            self.assertIsNone(fields["time"])

    def test_uncertain_dates_stay_unset(self):
        for text in ["The workshop might be tomorrow", "课程可能明天举行", "Meeting tomorrow or next Tuesday", "Meeting tomorrow; keep the date tentative"]:
            self.assertIsNone(self.draft(text, "schedule", date_text="tomorrow")["fields"]["date"])

    def test_named_dates_and_actual_dates_are_distinct(self):
        fields = self.draft('The play titled "Yesterday" starts on 2026-10-25 at 19:00', "schedule", date_text="2026-10-25")["fields"]
        self.assertEqual(fields["date"], "2026-10-25")
        fields = self.draft('今天午餐花了8元，店名叫“下周五”', "finance", date_text="今天")["fields"]
        self.assertEqual(fields["date"], "2026-10-03")

    def test_hyphenated_and_ambiguous_priorities(self):
        self.assertEqual(self.draft("Create a normal-priority task to wash dishes", "memo")["draft"]["priority"], "normal")
        self.assertIsNone(self.draft("Make a task to wash dishes. I cannot decide between high and low priority", "memo")["draft"]["priority"])
        self.assertEqual(self.draft("I need to scan a document. Please mark the task important", "schedule")["draft"]["priority"], "important")

    def test_clock_suffix_adjacent_to_chinese_is_preserved(self):
        fields = self.draft("日历：明天12:30am和老师视频", "schedule")["fields"]
        self.assertEqual(fields["time"], "00:30")

    def test_missing_money_is_recovered_only_when_unambiguous(self):
        self.assertEqual(self.draft("今天记一笔38元支出，现金", "finance")["fields"]["amount"], "38")
        for value in ["-38", "0", "38.123", "1,038", "38 or 39"]:
            self.assertIsNone(self.draft("Paid " + value + " yuan today", "finance")["fields"]["amount"])

    def test_notes_do_not_route_a_transaction_to_memo(self):
        result = self.draft('Add 20 yuan parking today, paid in cash, with the note "receipt in drawer"', "finance", remark="receipt in drawer")
        self.assertEqual(result["module"], "finance")
        self.assertIn("receipt in drawer", result["fields"]["notes"])

    def test_grounded_file_metadata_is_preserved_but_unknown_actions_rejected(self):
        result = self.draft("Task: send café.docx today", "memo", file="café.docx")
        self.assertIn("café.docx", result["draft"]["notes"])
        for fields in [dict(file="invented.docx"), dict(execute="send mail"), dict(remark="invented")]:
            with self.assertRaises(Exception): self.draft("Task: write a report", "memo", **fields)

    def test_only_one_complete_json_object_is_accepted(self):
        request = self.request("Task: wash dishes")
        raw = '{"intent":"single_task","module":"memo","title":"Wash dishes"}'
        self.assertEqual(json.loads(mobile_bridge.validate(request, raw + "}"))["status"], "draft")
        self.assertEqual(json.loads(mobile_bridge.validate(request, raw + raw))["reason"], "multiple_tasks")
        for output in [raw[:-1], 'Here is the answer: ' + raw]:
            with self.assertRaises(Exception): mobile_bridge.validate(request, output)

    def test_quoted_commands_are_not_split_into_actions(self):
        self.assertNotIn("response", json.loads(mobile_bridge.prepare(self.request('Note: "record lunch then add a meeting" is the example text'))))
        self.assertEqual(json.loads(mobile_bridge.prepare(self.request("先记下打印证件，再记一笔今天的10元工本费")))["response"]["reason"], "multiple_tasks")


if __name__ == "__main__": unittest.main()
