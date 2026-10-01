import unittest
from datetime import datetime, timezone
from action_service import create_action, clock_time, PROPERTIES
from task_service import ServiceError

NOW = datetime(2026, 9, 30, 12, tzinfo=timezone.utc)

def raw(**changes):
    return dict(dict.fromkeys(PROPERTIES), intent="single_task", module="finance", title="Coffee", notes="", **changes)

class ActionTests(unittest.TestCase):
    def test_unsupported_currency_and_historical_health_are_explicit(self):
        self.assertEqual("unsupported_currency",self.action("Coffee costs $12",raw(amount="12"))["reason"])
        data=raw();data.update(module="health")
        self.assertEqual("health_today_only",self.action("Show yesterday screen time",data)["reason"])

    def test_account_must_appear_in_input(self):
        self.assertIsNone(self.action("Coffee cost 12 today", raw(amount="12",account="Cash"))["fields"]["account"])
    def action(self, text, data, module="auto"):
        return create_action(dict(text=text, timezone="UTC", module=module), lambda _: (data,{}), NOW)

    def test_finance_extracts_past_date_and_grounded_amount(self):
        result = self.action("Coffee yesterday cost 12.50", raw(amount="12.50",date_text="yesterday",kind="expense"))
        self.assertEqual("2026-09-29",result["fields"]["date"])
        self.assertEqual("12.50",result["fields"]["amount"])
        self.assertIsNone(result["fields"]["account"])

    def test_invented_negative_or_overprecise_amount_left_empty(self):
        for amount in ("99", "-12", "12.345", "NaN"):
            result = self.action("Coffee cost 12",raw(amount=amount))
            self.assertIsNone(result["fields"]["amount"])

    def test_schedule_date_time_and_missing(self):
        data = raw(); data.update(module="schedule", title="Meeting",date_text="tomorrow at 3pm",time_text="3pm")
        result = self.action("Meeting tomorrow at 3pm",data)
        self.assertEqual("2026-10-01",result["fields"]["date"])
        self.assertEqual("15:00",result["fields"]["time"])
        data.update(date_text=None,time_text=None)
        self.assertIsNone(self.action("Meeting",data)["fields"]["date"])

    def test_health_routes_without_fabricating_statistics(self):
        data=raw();data.update(module="health",title="View screen time")
        result=self.action("View screen time",data)
        self.assertEqual("health",result["module"])
        self.assertNotIn("usage",result["fields"])

    def test_memo_uses_existing_validation(self):
        data=raw();data.update(module="memo",title="Read",date_text="tomorrow",priority=None)
        result=self.action("Read tomorrow",data)
        self.assertEqual("draft",result["status"])
        self.assertEqual("2026-10-01",result["draft"]["dueDate"])

    def test_user_can_correct_destination_but_multiple_actions_need_clarification(self):
        self.assertEqual("schedule",self.action("Buy coffee",raw(),"schedule")["module"])
        data=raw();data["intent"]="multiple_tasks"
        self.assertEqual("multiple_tasks",self.action("Pay bills and plan a meeting",data)["reason"])

    def test_today_missing_from_model_uses_user_timezone_for_each_write_module(self):
        for module in ("memo", "finance", "schedule"):
            data = raw(); data["module"] = module
            result = create_action(dict(text="Coffee TODAY",timezone="America/Los_Angeles",module="auto"),
                lambda _: (data,{}),datetime(2026,9,30,1,tzinfo=timezone.utc))
            self.assertEqual("2026-09-29",result["draft"]["dueDate"] if module == "memo" else result["fields"]["date"])

    def test_today_case_and_ambiguity(self):
        self.assertEqual("2026-09-30",self.action("Coffee TODAY",raw(date_text="today"))["fields"]["date"])
        for text in ("Not today", "Today or tomorrow", "Maybe today", "今天或者明天", "Meeting today about next Friday"):
            self.assertIsNone(self.action(text,raw())["fields"]["date"])

    def test_rejects_invalid_and_ungrounded_output(self):
        for data in ({},raw(date_text="tomorrow"),raw(category="Anything")):
            with self.assertRaises(ServiceError): self.action("Coffee",data)

    def test_this_tuesday_is_preserved_even_after_tuesday(self):
        for extracted in (None, "Tuesday", "this Tuesday"):
            with self.subTest(extracted=extracted):
                data = raw(date_text=extracted); data.update(module="schedule", title="Class")
                result = self.action("i have a class this Tuesday", data)
                self.assertEqual("2026-09-29", result["fields"]["date"])
                self.assertIsNone(result["fields"]["time"])

    def test_weekday_recovery_respects_calendar_week_and_timezone(self):
        cases = [
            ("Class this Tuesday", "2026-09-28T12:00:00+00:00", "UTC", "2026-09-29"),
            ("Class this Tuesday", "2026-09-29T12:00:00+00:00", "UTC", "2026-09-29"),
            ("Class NEXT Tuesday", "2026-10-01T12:00:00+00:00", "UTC", "2026-10-06"),
            ("Class this   Tuesday", "2026-10-01T12:00:00+00:00", "UTC", "2026-09-29"),
            ("这周二上课", "2026-10-01T12:00:00+00:00", "UTC", "2026-09-29"),
            ("Class this Tuesday", "2026-10-05T01:00:00+00:00", "America/Los_Angeles", "2026-09-29"),
            ("Class this Tuesday", "2026-10-05T01:00:00+00:00", "UTC", "2026-10-06"),
        ]
        for text, instant, zone, expected in cases:
            with self.subTest(text=text, instant=instant, zone=zone):
                data = raw(); data["module"] = "schedule"
                result = create_action(dict(text=text, timezone=zone, module="auto"),
                    lambda _: (data, {}), datetime.fromisoformat(instant))
                self.assertEqual(expected, result["fields"]["date"])

    def test_weekday_recovery_keeps_ambiguous_dates_unset(self):
        for text in ("Class maybe this Tuesday", "Class not this Tuesday", "Class this Tuesday or next Tuesday",
                     "Class this Tuesday or Wednesday", "Class this Tuesday, rescheduled from 2026-09-28"):
            with self.subTest(text=text):
                data = raw(); data["module"] = "schedule"
                self.assertIsNone(self.action(text, data)["fields"]["date"])

    def test_clock_time_boundaries(self):
        for text, expected in [("下午3点","15:00"),("12:30 am","00:30"),("23:59","23:59"),("25:00",None),("sometime",None)]:
            self.assertEqual(expected,clock_time(text))

if __name__ == "__main__": unittest.main()
