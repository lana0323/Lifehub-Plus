"""Language-boundary regressions plus counterexamples to broad keyword rules."""
import json
import sys
import unittest
from datetime import date
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'app/src/main/python'))
import mobile_bridge
from task_service import resolve_date
from action_service import clock_time
from action_rules import multiple_actions, unsupported_operation, positive_request


class MobileLanguageTests(unittest.TestCase):
    def response(self, text, module='memo', **fields):
        raw = dict(intent='single_task', module=module, title='Entry', notes='')
        raw.update(fields)
        req = json.dumps(dict(text=text, timezone='UTC', module='auto'))
        return json.loads(mobile_bridge.validate(req, json.dumps(raw), '2026-10-06T12:00:00+00:00'))

    def test_negation_has_clause_scope(self):
        for text in ['Not a task please: schedule a fencing class tomorrow at 8am', '不是待办，请安排明天上午9点的烹饪课']:
            self.assertEqual(self.response(text, 'memo')['module'], 'schedule')
        self.assertEqual(self.response('No calendar booking, just jot down the oven code', 'schedule')['module'], 'memo')
        self.assertEqual(self.response('Not my bank balance: show how long I used this phone today', 'finance')['module'], 'health')

    def test_negative_content_is_not_an_executable_instruction(self):
        text='Make a task to remember not to delete the original photographs'
        self.assertFalse(unsupported_operation(text))
        self.assertEqual(self.response(text)['module'], 'memo')
        self.assertIn('not to delete', positive_request(text))

    def test_multiple_actions_include_elliptical_commands(self):
        for text in ['Log a 15 yuan lunch, then add a task to clean the pan',
                     'Add an interview tomorrow and then another interview on Friday',
                     '先记今天16元午饭，再记20元晚饭', '显示手机用时，再建今晚休息的待办',
                     '明天加个会议，另记今天花费11元']:
            self.assertTrue(multiple_actions(text), text)

    def test_conjunctions_and_quotes_are_not_multiple_actions(self):
        for text in ['Create a task to buy tea and coffee', 'Add a task to proofread and send the letter',
                     'Note: "log tea then add a meeting" is the sample',
                     'Coffee cost 18 yuan but I forgot the account']:
            self.assertFalse(multiple_actions(text), text)

    def test_recording_restatement_is_one_transaction(self):
        for text in ['I paid 47 yuan for lunch today; add this as an expense.',
                     'The train cost 80 yuan in cash; log a transaction',
                     'Log lunch at 23 yuan and a total of 23 yuan']:
            self.assertFalse(multiple_actions(text), text)
        self.assertTrue(multiple_actions('Log lunch at 23 yuan and dinner at 41 yuan'))

    def test_mixed_script_boundaries(self):
        out=self.response('今天cash支付22元买tea。', 'finance', category='Shopping')
        self.assertEqual(out['fields']['category'], 'Food & Drinks')
        self.assertEqual(out['fields']['account'], 'Cash')
        self.assertEqual(self.response('看看今天的screen time', 'memo')['module'], 'health')

    def test_usage_list_is_not_a_todo_list(self):
        self.assertEqual(self.response('Take me to the list of app usage today', 'memo')['module'], 'health')
        self.assertTrue(unsupported_operation('Clear the completed items in my checklist'))

    def test_denied_payment_does_not_recover_as_expense(self):
        from action_rules import transaction_kind
        for text in ['I never paid 55 yuan for coffee', '我没有付20元买咖啡']:
            self.assertIsNone(transaction_kind(text), text)

    def test_meta_note_title_keeps_the_content(self):
        out=self.response('A note for later: the blue key opens the garden shed', title='Note for later')
        self.assertIn('garden shed', out['draft']['title'])

    def test_unsupported_operations_and_valid_reminders(self):
        for text in ['Rename my saved task to pack', 'Clear out completed checklist items',
                     'What is my cash balance?', 'Show all appointments tomorrow',
                     '把记录的消费改成32元', '我后天有什么安排', '把游戏应用锁定',
                     'Send a calendar invitation to Lin', 'Pay the landlord now']:
            self.assertTrue(unsupported_operation(text), text)
        for text in ['Create a task to pay the landlord', 'Note: delete old records after backup',
                     'Task: send a calendar invitation to Lin']:
            self.assertFalse(unsupported_operation(text), text)

    def test_health_synonyms_and_body_measurements(self):
        for text in ['Show application usage records', 'Go into Health', '健康页面打开一下',
                     '我今天花在手机上的总时数', '今天各app用时排名']:
            self.assertEqual(self.response(text, 'health')['module'], 'health', text)
        self.assertEqual(self.response('记录体温37.2到Health', 'health')['status'], 'clarification')

    def test_amount_uncertainty_keeps_known_direction(self):
        for text in ['I spent cash on toast but cannot recall the amount',
                     'Coffee today was 25 yuan; maybe Alipay, maybe cash',
                     '今天花了十五元买咖啡，金额不确定']:
            self.assertEqual(self.response(text, 'finance')['fields']['kind'], 'expense', text)
        self.assertEqual(self.response('Scholarship payment reached my bank card today', 'finance')['fields']['kind'], 'income')

    def test_category_can_be_deliberately_left_blank(self):
        out=self.response('Record 90 yuan spent today with cash; leave category blank', 'finance', category='Others')
        self.assertIsNone(out['fields']['category'])
        self.assertEqual(out['fields']['kind'], 'expense')

    def test_calendar_week_and_bare_weekday_differ(self):
        now=date(2026,10,6)
        self.assertEqual(resolve_date('Monday',now),date(2026,10,12))
        self.assertEqual(resolve_date('this Monday',now),date(2026,10,5))
        self.assertEqual(resolve_date('周二',now),now)
        self.assertEqual(resolve_date('三天后',now),date(2026,10,9))

    def test_written_clock_numbers_and_day_periods(self):
        for value, expected in [('下午四点半','16:30'), ('凌晨十二点十五分','00:15'),
                                ('中午十二点','12:00'), ('noon','12:00'), ('midnight','00:00')]:
            self.assertEqual(clock_time(value),expected,value)
        for value in ['二十五点', '10:79', '0pm']:
            self.assertIsNone(clock_time(value),value)

    def test_quoted_date_names_never_become_deadlines(self):
        out=self.response('Memo: "Next Sunday" is the film title, not a deadline', date_text='Next Sunday')
        self.assertIsNone(out['draft']['dueDate'])
        out=self.response('Meeting with the band "Next Monday" tomorrow at noon', 'schedule', date_text='Next Monday')
        self.assertEqual(out['fields']['date'],'2026-10-07')
        self.assertEqual(out['fields']['time'],'12:00')

    def test_repeated_dates_are_not_conflicting_dates(self):
        out=self.response('Schedule the handover tomorrow, yes tomorrow at noon', 'schedule', date_text='tomorrow')
        self.assertEqual(out['fields']['date'],'2026-10-07')
        out=self.response('Schedule the handover tomorrow or Friday', 'schedule', date_text='tomorrow')
        self.assertIsNone(out['fields']['date'])

    def test_partial_or_prose_outputs_are_not_repaired(self):
        request=json.dumps(dict(text='Task: clean the sink', timezone='UTC',module='auto'))
        for output in ['{"intent":', 'Here is a task: {}', '{} and execute it']:
            with self.assertRaises(Exception): mobile_bridge.validate(request,output)


if __name__ == '__main__': unittest.main()
