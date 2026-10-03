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


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--validated',type=Path,required=True)
    p.add_argument('--protocol',type=Path,default=ROOT/'docs/evaluation-mobile-holdout/protocol.json')
    p.add_argument('--output',type=Path,default=ROOT/'docs/evaluation-mobile-holdout/results.json')
    args=p.parse_args()
    assert not args.output.exists(), 'Refusing to overwrite a first-pass result'
    protocol=json.loads(args.protocol.read_text(encoding='utf-8'))
    for name,digest in protocol['sourceSha256'].items():
        assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest, 'Frozen file changed: '+name
    case_file=ROOT/protocol['dataset']
    assert hashlib.sha256(case_file.read_bytes()).hexdigest()==protocol['datasetSha256']
    cases=json.loads(case_file.read_text(encoding='utf-8'))
    captured=json.loads(args.validated.read_text(encoding='utf-8'))
    assert len(captured)==len(cases)==240, 'A complete first pass is required'
    assert [r['id'] for r in captured]==[c['id'] for c in cases], 'Wrong run/order'
    rows={}
    for c,original in zip(cases,captured):
        row={k:v for k,v in original.items() if k!='latencyMs'}
        checks=frozen_score(c,row.get('actual',{}),decimal_amounts=True)
        # Error rows cannot silently earn credit from an expected null.
        if 'error' in row: checks={k:False for k in c['expected']}
        row['checks']=checks
        row['routeCorrect']=checks.get('status',False) and checks.get('module',False)
        row['allChecksCorrect']=all(checks.values())
        row['modelInvoked']='raw' in row or 'error' in row
        row['rawRoute']=raw_route(row) if row['modelInvoked'] else None
        row['rawRouteCorrect']=row['rawRoute']=={k:c['expected'][k] for k in ('status','module')} if row['modelInvoked'] else None
        rows[c['id']]=row
    report=dict(model=protocol['model'],runtime=protocol['runtime'],datasetSha256=protocol['datasetSha256'],
        datasetRole='New internally authored holdout, frozen before this first pass; no tuning during the run.',
        complete=True,summary=summarize(cases,rows),
        byLanguage={lang:summarize([c for c in cases if c['language']==lang],rows) for lang in ('zh','en')},
        byWorkflow={g:summarize([c for c in cases if c['group']==g],rows) for g in ('memo','finance','schedule','health')},
        byTag={tag:summarize([c for c in cases if tag in c['tags']],rows) for tag in sorted({t for c in cases for t in c['tags']})},
        results=list(rows.values()))
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    write_csv(args.output.with_suffix('.csv'),cases,rows)
    for lang,name in [('zh','chinese-120.csv'),('en','english-120.csv')]:
        write_csv(args.output.parent/name,[c for c in cases if c['language']==lang],rows)
    print(json.dumps(report['summary'],ensure_ascii=False,indent=2))


if __name__=='__main__': main()
