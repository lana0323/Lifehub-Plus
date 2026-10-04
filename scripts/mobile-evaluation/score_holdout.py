"""Score one frozen first pass; no inference, answer repair or production changes."""
import argparse
import csv
import hashlib
import json
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'backend'))
from evaluate_actions import flatten
from evaluate_holdout import frozen_score


def ratio(correct):
    values = list(correct)
    return dict(passed=sum(values), total=len(values), rate=sum(values)/len(values) if values else None)


def raw_route(row):
    """Only interpret the model's emitted intent/module, before app corrections."""
    try:
        text = row['raw'].strip()
        if text.startswith('```json') and text.endswith('```'):
            text = text[7:-3].strip()
        elif text.startswith('```') and text.endswith('```'):
            text = text[3:-3].strip()
        raw = json.loads(text)
        intent, module = raw.get('intent'), raw.get('module')
        if intent in ('unclear', 'multiple_tasks') or module == 'unclear':
            return dict(status='clarification', module=None)
        if intent == 'single_task' and module in ('memo','finance','schedule','health'):
            return dict(status='draft', module=module)
    except (KeyError, TypeError, ValueError, AttributeError):
        pass
    return dict(status=None, module=None)


def summarize(cases, rows):
    selected = [(c, rows[c['id']]) for c in cases]
    generated = [(c, r) for c, r in selected if r['modelInvoked']]
    return dict(
        cases=len(selected),
        routing=ratio(r['routeCorrect'] for c,r in selected),
        requiredFields=ratio(r['checks'].get(k,False) for c,r in selected for k in c['requiredFields']),
        allSpecifiedFields=ratio(r['allChecksCorrect'] for c,r in selected),
        supportedDraft=ratio(r['allChecksCorrect'] for c,r in selected if c['expected']['status']=='draft'),
        clarification=ratio(r['routeCorrect'] for c,r in selected if c['expected']['status']=='clarification'),
        dates=ratio(r['checks'].get('date',False) for c,r in selected if 'date' in c['expected']),
        rawModelRouting=ratio(r['rawRouteCorrect'] for c,r in generated),
        modelCalls=len(generated), preflight=len(selected)-len(generated),
        errors=sum('error' in r for c,r in selected),
        unsafeDrafts=sum(c['expected']['status']=='clarification' and r.get('actual',{}).get('status')=='draft' for c,r in selected),
        fields={k:ratio(r['checks'][k] for c,r in selected if k in r['checks']) for k in sorted({k for c,r in selected for k in r['checks']})},
    )


def write_csv(path, cases, rows):
    with path.open('w',encoding='utf-8-sig',newline='') as f:
        writer=csv.writer(f)
        writer.writerow(['id','language','module_group','tag','input','timezone','fixed_now','expected','actual','model_output','model_invoked','raw_route_correct','routing_correct','required_correct','required_total','all_fields_correct','failed_checks','error'])
        for c in cases:
            r=rows[c['id']]
            writer.writerow([c['id'],c['language'],c['group'],c['tags'][0],c['text'],c['timezone'],c['now'],
                json.dumps(c['expected'],ensure_ascii=False),json.dumps(r.get('actual'),ensure_ascii=False),r.get('raw',''),
                r['modelInvoked'],r['rawRouteCorrect'] if r['modelInvoked'] else '',r['routeCorrect'],
                sum(r['checks'][k] for k in c['requiredFields']),len(c['requiredFields']),r['allChecksCorrect'],
                '; '.join(k for k,v in r['checks'].items() if not v),r.get('error','')])


if __name__ == '__main__':
    from score_v4_regression import main
    main()
