import unittest
from evaluate_actions import score, summarize

class ActionScoringTests(unittest.TestCase):
    def test_correct_route_does_not_hide_wrong_amount_or_date(self):
        case={"expected":{"status":"draft","module":"finance","amount":"12.50","date":"2026-10-01"}}
        result={"status":"draft","module":"finance","fields":{"amount":"99","date":"2026-10-02"}}
        checks=score(case,result)
        self.assertTrue(checks["module"]);self.assertFalse(checks["amount"]);self.assertFalse(checks["date"])
    def test_missing_field_is_not_the_same_as_explicit_null(self):
        case={"expected":{"amount":None}}
        self.assertFalse(score(case,{"status":"draft","fields":{"title":"Coffee"}})["amount"])
        self.assertTrue(score(case,{"status":"draft","fields":{"title":"Coffee","amount":None}})["amount"])
    def test_memo_date_and_title_keyword_are_scored(self):
        result={"status":"draft","module":"memo","draft":{"title":"Review notes","dueDate":"2026-10-01"}}
        self.assertTrue(all(score({"expected":{"date":"2026-10-01","title":{"containsAny":["notes"]}}},result).values()))
    def test_failures_stay_in_field_denominators(self):
        cases=[{"expected":{"module":"finance","amount":"12"}},{"expected":{"module":"finance","amount":"12"}}]
        rows=[{"correct":True,"checks":{"module":True,"amount":True},"latencyMs":10},{"correct":False,"error":"timeout","checks":{},"latencyMs":100}]
        report=summarize(cases,rows)
        self.assertEqual(.5,report["fieldAccuracy"]["amount"]["rate"])
        self.assertEqual(.5,report["allCheckedFieldsAccuracy"])
        self.assertEqual(100,report["latencyP95Ms"])
