import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "app/src/main/python"))
import mobile_bridge
from mobile_titles import recover_title


class MobileTitleTests(unittest.TestCase):
    def test_specific_event_subject_is_preserved(self):
        for subject in ("修鞋", "修伞", "相机维修"):
            with self.subTest(subject=subject):
                text = "选中日程模块后，填明天上午11点的" + subject + "取件。"
                self.assertEqual(recover_title(text, "取件", "schedule", "明天", "上午11点"), subject + "取件")

    def test_named_generic_event_is_not_expanded(self):
        self.assertEqual(recover_title('安排明天的活动，标题为“会议”', "会议", "schedule", "明天"), "会议")

    def test_specific_model_title_is_not_replaced(self):
        self.assertEqual(recover_title("安排明天上午的团队课程", "团队课程", "schedule", "明天"), "团队课程")

    def test_secondary_receipt_problem_is_not_transaction_title(self):
        text = "I bought a sandwich today with cash but lost the receipt and cannot remember the price."
        self.assertEqual(recover_title(text, "Lost Receipt", "finance"), "I bought a sandwich today with cash")
        text = "I paid for a train ticket today but forgot the price."
        self.assertEqual(recover_title(text, "Forgot Price", "finance"), "I paid for a train ticket today")

    def test_receipt_can_be_the_actual_purchase(self):
        text = "I paid 6 yuan for a receipt replacement today but forgot the account."
        self.assertEqual(recover_title(text, "Receipt replacement", "finance"), "Receipt replacement")

    def test_refund_and_valid_purchase_titles_are_kept(self):
        self.assertEqual(recover_title("I received a shoe refund today but lost the receipt", "Shoe refund", "finance"), "Shoe refund")
        self.assertEqual(recover_title("I bought lunch but lost the receipt", "Lunch", "finance"), "Lunch")

    def test_title_repair_does_not_invent_missing_amount(self):
        text = "I bought a sandwich today with cash but lost the receipt and cannot remember the price."
        request = json.dumps(dict(text=text, timezone="UTC", module="auto"))
        raw = dict(intent="single_task", module="finance", title="Lost Receipt", amount=None, kind="expense", category="Food & Drinks", account="Cash", date_text="today")
        result = json.loads(mobile_bridge.validate(request, json.dumps(raw), "2026-10-03T12:00:00+00:00"))
        self.assertIn("sandwich", result["fields"]["title"])
        self.assertIsNone(result["fields"]["amount"])
        self.assertEqual(result["fields"]["account"], "Cash")

    def test_memo_and_health_titles_are_not_reinterpreted(self):
        for module in ("memo", "health"):
            self.assertEqual(recover_title("I bought lunch but lost the receipt", "Lost receipt", module), "Lost receipt")


if __name__ == "__main__": unittest.main()
