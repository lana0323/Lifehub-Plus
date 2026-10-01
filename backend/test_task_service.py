import json
import threading
import unittest
from datetime import date, datetime, timezone
from unittest.mock import patch
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from http.server import ThreadingHTTPServer

from task_service import ServiceError, create_draft, resolve_date, request_context, validate_extraction, call_cloud_model as call_model
from task_service import call_model as dispatch_model, local_settings, call_local_model
from server import Handler
from evaluate import score

TODAY = date(2026, 9, 29)
NOW = datetime(2026, 9, 30, 1, tzinfo=timezone.utc)


def extraction(**changes):
    return dict(dict(intent="single_task", title="完成机器学习作业", notes="",
                     date_text="下周五", priority="important"), **changes)


class DateTests(unittest.TestCase):
    def test_relative_calendar_dates(self):
        for phrase, expected in [("下周五", "2026-10-09"), ("next Friday", "2026-10-09"),
                                 ("本周五前", "2026-10-02"), ("明天", "2026-09-30"),
                                 ("in 3 days", "2026-10-02"), ("2026年10月2日", "2026-10-02")]:
            with self.subTest(phrase=phrase):
                self.assertEqual(resolve_date(phrase, TODAY).isoformat(), expected)

    def test_ambiguous_invalid_and_time_remain_missing(self):
        for phrase in [None, "最近", "soon", "明天或后天", "明天晚上8点", "2026-02-30", "Friday"]:
            with self.subTest(phrase=phrase):
                self.assertIsNone(resolve_date(phrase, TODAY))

    def test_year_and_leap_boundary(self):
        self.assertEqual(resolve_date("明天", date(2026, 12, 31)), date(2027, 1, 1))
        self.assertEqual(resolve_date("tomorrow", date(2028, 2, 28)), date(2028, 2, 29))

    def test_timezone_and_dst(self):
        self.assertEqual(request_context({"text": "a", "timezone": "America/Los_Angeles"}, NOW)[2], TODAY)
        self.assertEqual(request_context({"text": "a", "timezone": "Asia/Shanghai"}, NOW)[2], date(2026, 9, 30))
        dst = datetime(2026, 3, 8, 9, 30, tzinfo=timezone.utc)
        local = request_context({"text": "a", "timezone": "America/Los_Angeles"}, dst)[2]
        self.assertEqual(resolve_date("tomorrow", local), date(2026, 3, 9))


class ValidationTests(unittest.TestCase):
    def test_invented_priority_is_cleared_before_display(self):
        for priority in ("normal", "important", "urgent"):
            result = validate_extraction(extraction(date_text=None, priority=priority), "Read chapter 3", "UTC", TODAY)
            self.assertIsNone(result["draft"]["priority"])

    def test_smalltalk_is_not_a_task_but_greetings_in_tasks_are_allowed(self):
        for value in ("Thank you!", "谢谢！", "Hello", "thanks a lot."):
            result = validate_extraction(extraction(title=value, date_text=None), value, "UTC", TODAY)
            self.assertEqual("clarification", result["status"])
        result = validate_extraction(extraction(title="Send a thank you note", date_text=None),
                                     "Send a thank you note", "UTC", TODAY)
        self.assertEqual("draft", result["status"])

    def test_negated_urgency_requires_user_choice(self):
        for value in ("整理书架，不着急", "Organise books, not urgent", "Organise books, no rush"):
            result = validate_extraction(extraction(date_text=None, priority="normal"), value, "UTC", TODAY)
            self.assertIsNone(result["draft"]["priority"])
            self.assertIn("priority_missing", result["draft"]["warnings"])
        result = validate_extraction(extraction(date_text=None, priority="important"),
                                     "整理书架，不着急，但是优先级高", "UTC", TODAY)
        self.assertEqual("important", result["draft"]["priority"])

    def test_default_is_local_even_with_cloud_credentials(self):
        with patch.dict("os.environ", {"AI_API_KEY": "unused"}, clear=True), \
             patch("task_service.call_local_model", return_value=({}, {})) as local, \
             patch("task_service.call_cloud_model") as cloud:
            dispatch_model("task")
            local.assert_called_once()
            cloud.assert_not_called()

    def test_cloud_requires_explicit_opt_in(self):
        with patch.dict("os.environ", {"AI_PROVIDER": "openai"}, clear=True), self.assertRaises(ServiceError):
            dispatch_model("task")

    def test_local_only_rejects_remote_and_cloud_models(self):
        for changes in ({"OLLAMA_BASE_URL": "https://ollama.com"},
                        {"OLLAMA_BASE_URL": "http://127.0.0.1@evil.example"},
                        {"OLLAMA_MODEL": "qwen3:cloud"}):
            with patch.dict("os.environ", changes, clear=True), self.assertRaises(ServiceError):
                local_settings()

    def test_local_schema_request_and_usage(self):
        with patch.dict("os.environ", {}, clear=True), patch("task_service.build_opener") as opener:
            opener.return_value.open.return_value.__enter__.return_value.read.return_value = json.dumps({
                "done": True, "done_reason": "stop", "message": {"content": json.dumps(extraction())},
                "prompt_eval_count": 50, "eval_count": 25}).encode()
            raw, usage = call_local_model("下周五完成作业")
            request = opener.return_value.open.call_args.args[0]
            self.assertFalse(json.loads(request.data)["think"])
            self.assertEqual(raw, extraction())
            self.assertEqual(usage["completion_tokens"], 25)

    def test_draft_contract_and_metrics(self):
        result = create_draft({"text": "下周五完成机器学习作业", "timezone": "Asia/Shanghai"},
                              lambda _: (extraction(), {"prompt_tokens": 50, "completion_tokens": 20}), NOW)
        self.assertEqual(result["draft"]["dueDate"], "2026-10-09")
        self.assertEqual(result["metrics"]["inputTokens"], 50)

    def test_missing_fields_are_not_invented(self):
        result = validate_extraction(extraction(date_text=None, priority=None), "复习", "UTC", TODAY)
        self.assertIsNone(result["draft"]["dueDate"])
        self.assertIsNone(result["draft"]["priority"])
        self.assertIn("priority_missing", result["draft"]["warnings"])

    def test_past_date_requires_review(self):
        result = validate_extraction(extraction(date_text="昨天"), "昨天交作业", "UTC", TODAY)
        self.assertIsNone(result["draft"]["dueDate"])
        self.assertIn("past_date", result["draft"]["warnings"])

    def test_multiple_tasks_never_become_one(self):
        result = validate_extraction(extraction(intent="multiple_tasks"), "写报告和买牛奶", "UTC", TODAY)
        self.assertEqual(result["status"], "clarification")
        self.assertIsNone(result["draft"])

    def test_reject_invalid_model_fields(self):
        for changes in [{"priority": "super high"}, {"title": "x" * 101}, {"notes": None},
                        {"date_text": "明天"}, {"intent": "execute"}, {"title": 3}]:
            with self.subTest(changes=changes), self.assertRaises(ServiceError):
                validate_extraction(extraction(**changes), "下周五写作业", "UTC", TODAY)

    def test_input_boundaries(self):
        for body in [{}, [], {"text": " ", "timezone": "UTC"}, {"text": "a", "timezone": "Mars/X"},
                     {"text": "x" * 2001, "timezone": "UTC"}]:
            with self.subTest(body=str(body)[:80]), self.assertRaises(ServiceError):
                request_context(body)

    def test_missing_provider_configuration(self):
        with patch.dict("os.environ", {}, clear=True), self.assertRaises(ServiceError) as error:
            call_model("task")
        self.assertEqual(error.exception.code, "not_configured")

    def test_semantic_evaluation_rejects_parseable_wrong_task(self):
        case = {"status": "draft", "titles": ["Buy milk"], "dueDate": "2026-09-30", "priority": "normal"}
        result = {"status": "draft", "draft": {"title": "Send report", "dueDate": "2026-09-30",
                                               "priority": "urgent", "notes": ""}}
        fields = score(case, result)
        self.assertTrue(fields["dueDate"])
        self.assertFalse(fields["title"])
        self.assertFalse(fields["priority"])

    def test_provider_timeout(self):
        with patch.dict("os.environ", {"AI_API_KEY": "test", "AI_MODEL": "test"}), \
             patch("task_service.urlopen", side_effect=TimeoutError), self.assertRaises(ServiceError) as error:
            call_model("task")
        self.assertEqual(error.exception.status, 504)

    def test_provider_refusal_and_truncated_output(self):
        for choice, expected in [({"message": {"refusal": "no"}, "finish_reason": "stop"}, "model_refused"),
                                 ({"message": {"content": "{}"}, "finish_reason": "length"}, "incomplete_output")]:
            with patch.dict("os.environ", {"AI_API_KEY": "test", "AI_MODEL": "test"}), \
                 patch("task_service.urlopen") as mocked, self.assertRaises(ServiceError) as error:
                mocked.return_value.__enter__.return_value.read.return_value = json.dumps({"choices": [choice]}).encode()
                call_model("task")
            self.assertEqual(error.exception.code, expected)


class HttpTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = f"http://127.0.0.1:{cls.server.server_port}/v1/task-drafts"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def post(self, data):
        return urlopen(Request(self.url, data, {"Content-Type": "application/json"}), timeout=2)

    def test_http_success_contract(self):
        with patch("server.create_draft", return_value={"status": "clarification", "draft": None}):
            with self.post(b'{"text":"task","timezone":"UTC"}') as response:
                self.assertEqual(json.load(response)["status"], "clarification")

    def test_http_malformed_json(self):
        with self.assertRaises(HTTPError) as error:
            self.post(b"not json")
        self.assertEqual(error.exception.code, 400)

    def test_provider_failure_is_recoverable(self):
        with patch("server.create_draft", side_effect=ServiceError("provider_timeout", 504)):
            with self.assertRaises(HTTPError) as error:
                self.post(b'{}')
            self.assertEqual(error.exception.code, 504)
            self.assertEqual(json.load(error.exception)["error"], "provider_timeout")


if __name__ == "__main__":
    unittest.main()
