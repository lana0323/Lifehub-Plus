import unittest
from evaluate_holdout import summary, frozen_score
class HoldoutScoringTests(unittest.TestCase):
    def test_frozen_decimal_policy_accepts_formatting_but_not_different_amounts(self):
        case={'expected':{'amount':'19.90'}}
        result={'status':'draft','module':'finance','fields':{'amount':'19.9'}}
        self.assertFalse(frozen_score(case,result)['amount'])
        self.assertTrue(frozen_score(case,result,True)['amount'])
        for value in ('19.99','NaN','Infinity','invalid'):
            result['fields']['amount']=value
            self.assertFalse(frozen_score(case,result,True)['amount'])
    def test_errors_stay_in_denominator_and_clarifications_are_separate(self):
        cases=[{'id':'a','expected':{'status':'draft'},'requiredFields':['title','date'],'tags':['complete']},
               {'id':'b','expected':{'status':'draft'},'requiredFields':['title','date'],'tags':['complete']},
               {'id':'c','expected':{'status':'clarification'},'requiredFields':[],'tags':['clarification']}]
        rows=[{'id':'a','routeCorrect':True,'allChecksCorrect':False,'checks':{'title':True,'date':False},'latencyMs':10},
              {'id':'b','routeCorrect':False,'allChecksCorrect':False,'checks':{},'latencyMs':100,'error':'timeout'},
              {'id':'c','routeCorrect':True,'allChecksCorrect':True,'checks':{},'latencyMs':20}]
        result=summary(cases,rows)
        self.assertEqual({'passed':1,'total':2,'rate':.5},result['routingSupported'])
        self.assertEqual(.25,result['requiredFieldMicro']['rate'])
        self.assertEqual(0,result['supportedDraftContract']['passed'])
        self.assertEqual(1,result['clarificationOutcome']['passed'])
        self.assertEqual(20,result['latencyMs']['p50']);self.assertEqual(100,result['latencyMs']['p95'])
    def test_missing_execution_cannot_be_counted_as_pass(self):
        cases=[{'id':'a','expected':{'status':'draft'},'requiredFields':[],'tags':[]}]
        rows=[{'id':'a','routeCorrect':False,'allChecksCorrect':False,'checks':{},'latencyMs':90000,'error':'timeout'}]
        result=summary(cases,rows)
        self.assertEqual(0,result['routingOverall']['passed']);self.assertIsNone(result['requiredFieldMicro']['rate'])
