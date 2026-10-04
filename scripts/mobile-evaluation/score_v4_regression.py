"""Score the seen v4 regression with the unchanged frozen scoring functions."""
import argparse
import hashlib
import json
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from score_holdout import ROOT, frozen_score, raw_route, summarize, write_csv
from verify_sources import verify


def fraction(value):
    if not value['total']:
        return 'N/A'
    percent = (Decimal(value['passed']) * 100 / value['total']).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
    return f"{value['passed']}/{value['total']} ({percent}%)"


def write_report(output, report, cases, protocol):
    summary = report['summary']
    lines = ['# Lifehub Plus 1.1.3 evaluation', '',
        'This report shows the latest **fresh model run on a seen regression set**. These inputs informed development, so the scores are regression evidence rather than unseen-model accuracy. The recorded outputs, expected answers and field-scoring rules have not changed.', '',
        '## Method', '',
        '- 120 Chinese and 120 English inputs, 30 per workflow per language; 180 draft requests and 60 clarification requests.',
        '- Qwen2.5-1.5B-Instruct int8 ekv4096, LiteRT-LM 0.10.2, Windows CPU. Same model, prompt, asynchronous Unicode-safe transport and Python validation as the mobile implementation.',
        '- One attempt per input, fresh conversation, no retry or manual answer repair. Preflight responses and raw model routing are counted separately. The source hashes and configuration were frozen before inference.',
        '- These metrics evaluate reviewable drafts, not Android inference, UI completion or database writes. Titles are checked against predeclared keyword alternatives. Internally authored Chinese/English scenario families overlap; this is not an independent benchmark.',
        '- No latency statistics are reported. See [protocol.json](protocol.json) and [separate application checks](reliability.json).', '',
        '## Results', '', '| Metric | Version 1.1.3 |', '|---|---|']
    for key, label in [('routing','Routing after validation'),('requiredFields','Required fields'),('allSpecifiedFields','All specified fields'),('supportedDraft','Supported draft checks'),('clarification','Clarification handling'),('dates','Dates, including expected blanks')]:
        lines.append(f"| {label} | {fraction(summary[key])} |")
    lines += ['', '| Language | Routing | Required fields | All specified fields |', '|---|---|---|---|']
    for lang, name in [('zh','Chinese'),('en','English')]:
        row=report['byLanguage'][lang]
        lines.append(f"| {name} | {fraction(row['routing'])} | {fraction(row['requiredFields'])} | {fraction(row['allSpecifiedFields'])} |")
    lines += ['', '| Workflow | Routing | All specified fields |', '|---|---|---|']
    for name, row in report['byWorkflow'].items():
        lines.append(f"| {name.title()} | {fraction(row['routing'])} | {fraction(row['allSpecifiedFields'])} |")
    lines += ['', '## Run integrity', '',
        f"- {summary['modelCalls']} model calls and {summary['preflight']} preflight responses; {summary['errors']} pipeline errors. All inputs remain in the denominator.",
        f"- {summary['unsafeDrafts']} expected-clarification inputs returned as drafts; drafts still require user confirmation.",
        f"- Raw model routing on the invoked subset: **{fraction(summary['rawModelRouting'])}**. Final pipeline accuracy includes deterministic application rules; preflight-only responses are outside the raw-model denominator.", '',
        '## Implementation changes', '',
        '- Scope negated destinations to their own clause. Recognize mixed Chinese/English word boundaries, Health navigation and usage paraphrases.',
        '- Detect separate actions, including repeated events and separately priced purchases; keep a purchase followed by a request to record it as one action. Tasks about sending/deleting remain supported, while direct execution requests require clarification.',
        '- Keep explicit transaction direction when amount or account is unknown. Preserve deliberately blank categories and reject denied payments as evidence of spending.',
        '- Share date/time parsing between Android and the backend: calendar-week weekdays, next-occurrence bare weekdays, Chinese number dates/clocks, noon and midnight. Quoted names are not date evidence; conflicting dates stay unset.',
        '- Recover the subject of generic titles and content behind a meta title such as "Note for later". Multiple complete JSON objects produce a clarification; partial output, prose and unsupported keys remain invalid.', '',
        '## Remaining failures', '', '| Input | Failed checks |', '|---|---|']
    by_id={c['id']:c for c in cases}
    for row in report['results']:
        if not row['allChecksCorrect']:
            lines.append('| '+by_id[row['id']]['text'].replace('|','\\|')+' | '+', '.join(k for k,v in row['checks'].items() if not v)+' |')
    lines += ['', 'The frozen set interprets "not urgent / 不急" as normal priority. Production conservatively leaves priority unset because a non-urgent task can still be important. These disagreements are retained as failures; the expected answers were not changed.', '',
        'This set informed development. A future untouched holdout is needed to assess generalization. The review screen remains the final place to correct fields before confirmation.', '',
        '## Files and reproduction', '',
        '[Excel results](Lifehub-Plus-V4-Regression.xlsx) · [All results](results.csv) · [Chinese 120](chinese-120.csv) · [English 120](english-120.csv) · [Failures](failures.csv) · [Raw responses and checks](results.json) · [Recorded protocol](protocol.json)', '',
        '```powershell', '.\\scripts\\mobile-evaluation\\run_v4_regression.ps1 -ModelPath C:/models/mobile-qwen2.5.litertlm -Python python -JavaHome $env:JAVA_HOME -OutputDirectory .local/reproduced-v4-regression', '```', '',
        'Use a fresh directory. The script checks source/data/model hashes and preserves failures without retrying. The recorded protocol and raw results remain byte-for-byte unchanged. Report formatting was simplified after the run; [reproduction.json](reproduction.json) pins those reporting-only changes separately. Historical paths in the recorded protocol are provenance, not dependencies of this runner.', '']
    (output/'REPORT.md').write_text('\n'.join(lines), encoding='utf-8')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--validated', type=Path, required=True)
    parser.add_argument('--protocol', type=Path, default=ROOT/'docs/evaluation-mobile-v4-regression/protocol.json')
    parser.add_argument('--output', type=Path, required=True)
    args=parser.parse_args()
    if args.output.exists(): raise ValueError('Recorded runs are immutable; choose a new output')
    protocol=verify(args.protocol)
    dataset=ROOT/protocol['dataset']
    assert hashlib.sha256(dataset.read_bytes()).hexdigest()==protocol['datasetSha256']
    cases=json.loads(dataset.read_text(encoding='utf-8'))
    captured=json.loads(args.validated.read_text(encoding='utf-8'))
    assert len(cases)==len(captured)==240
    assert [r['id'] for r in captured]==[c['id'] for c in cases]
    rows={}
    for case,original in zip(cases,captured):
        row={k:v for k,v in original.items() if k!='latencyMs'}
        checks=frozen_score(case,row.get('actual',{}),decimal_amounts=True)
        if 'error' in row: checks={k:False for k in case['expected']}
        row.update(checks=checks,routeCorrect=checks['status'] and checks['module'],allChecksCorrect=all(checks.values()),modelInvoked='raw' in row or 'error' in row)
        row['rawRoute']=raw_route(row) if row['modelInvoked'] else None
        row['rawRouteCorrect']=row['rawRoute']=={k:case['expected'][k] for k in ('status','module')} if row['modelInvoked'] else None
        rows[case['id']]=row
    report=dict(model=protocol['model'],runtime=protocol['runtime'],completedAtUtc=datetime.now(timezone.utc).isoformat(),datasetSha256=protocol['datasetSha256'],datasetRole=protocol['datasetRole'],execution=protocol['execution'],complete=True,summary=summarize(cases,rows),byLanguage={lang:summarize([c for c in cases if c['language']==lang],rows) for lang in ('zh','en')},byWorkflow={group:summarize([c for c in cases if c['group']==group],rows) for group in ('memo','finance','schedule','health')},results=list(rows.values()))
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    write_csv(args.output.with_suffix('.csv'),cases,rows)
    for lang,name in [('zh','chinese-120.csv'),('en','english-120.csv')]:
        write_csv(args.output.parent/name,[c for c in cases if c['language']==lang],rows)
    write_csv(args.output.parent/'failures.csv',[c for c in cases if not rows[c['id']]['allChecksCorrect']],rows)
    write_report(args.output.parent,report,cases,protocol)
    print(json.dumps(report['summary'],indent=2))


if __name__=='__main__': main()
