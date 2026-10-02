"""Prepare and report the bilingual v2 replay without any additional inference."""
import argparse, csv, hashlib, json, math
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'docs/evaluation-v2'

def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def save(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def ratio(values):
    values=list(values)
    return {'passed':sum(values),'total':len(values),'rate':sum(values)/len(values) if values else None}
def fmt(value):
    return f"{value['passed']}/{value['total']} ({100*value['rate']:.1f}%)" if value['rate'] is not None else 'N/A'
def latency(rows):
    values=sorted(r['latencyMs'] for r in rows)
    return {'count':len(values),'p50':values[math.ceil(.5*len(values))-1],'p95':values[math.ceil(.95*len(values))-1]} if values else None

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--prepare-replay',action='store_true');args=parser.parse_args()
    case_path=ROOT/'backend/evaluation/holdout_v2.json';cases=read(case_path)
    lock=read(case_path.with_suffix('.lock.json'));model=read(OUT/'model-results.json')
    assert model['complete'] and len(model['results'])==len(cases)==240
    assert hashlib.sha256(case_path.read_bytes()).hexdigest()==model['datasetSha256']==lock['datasetSha256']
    for f,h in lock['sourceHashes'].items():assert hashlib.sha256((ROOT/f).read_bytes()).hexdigest()==h,f
    meta={c['id']:c for c in cases};rows={r['id']:r for r in model['results']}
    assert len(rows)==240 and set(rows)==set(meta)
    if args.prepare_replay:
        fixture={'datasetSha256':lock['datasetSha256'],'cases':[dict(r,text=meta[r['id']]['text'],supported=meta[r['id']]['expected']['status']=='draft') for r in model['results']]}
        save(ROOT/'app/src/androidTest/assets/holdout-v2-replay.json',fixture)
        print('Prepared 240 responses for Android replay; no new inference.');return
    ui=read(OUT/'android-results.json');ui_map={r['id']:r for r in ui}
    assert len(ui)==len(ui_map)==240 and set(ui_map)==set(meta)
    reliability=read(OUT/'reliability-results.json')
    def combined(selected):
        ids=[c['id'] for c in selected];supported=[c['id'] for c in selected if c['expected']['status']=='draft'];clarify=[c['id'] for c in selected if c['expected']['status']=='clarification']
        return {'cases':len(ids),'routing':ratio(rows[i]['routeCorrect'] for i in ids),
            'requiredFields':ratio(rows[c['id']]['checks'].get(k,False) for c in selected for k in c['requiredFields']),
            'backendAllChecks':ratio(rows[i]['allChecksCorrect'] for i in ids),
            'workflowEntry':ratio(ui_map[i]['success'] for i in supported),
            'clarification':ratio(ui_map[i]['success'] for i in clarify),
            'allOutcomes':ratio(ui_map[i]['success'] for i in ids),
            'latencyAll':latency(rows[i] for i in ids),
            'latencyModelCalled':latency(rows[i] for i in ids if rows[i].get('modelCalled')),
            'modelErrors':sum('error' in rows[i] for i in ids),
            'additionalUiFailures':[i for i in ids if rows[i]['allChecksCorrect'] and not ui_map[i]['success']]}
    result={'datasetSha256':lock['datasetSha256'],'overall':combined(cases),'byLanguage':{l:combined([c for c in cases if c['language']==l]) for l in ['zh','en']},
        'byWorkflowLanguage':{l:{g:combined([c for c in cases if c['language']==l and c['group']==g]) for g in ['memo','finance','schedule','health']} for l in ['zh','en']},'reliability':reliability}
    result['fieldChecksByLanguage']={l:{k:ratio(rows[c['id']]['checks'][k] for c in cases if c['language']==l and k in c['expected']) for k in ['title','amount','kind','category','account','date','time','priority']} for l in ['zh','en']}
    save(OUT/'summary.json',result)
    for name,selected in [('all-cases',cases),('chinese-120',[c for c in cases if c['language']=='zh']),('english-120',[c for c in cases if c['language']=='en'])]:
        with (OUT/(name+'.csv')).open('w',encoding='utf-8-sig',newline='') as f:
            w=csv.writer(f);w.writerow(['id','pair_id','language','workflow','input','routing_pass','required_pass','required_total','backend_all_checks','android_outcome','latency_ms','model_called','failed_fields','error','ui_failure','expected','actual'])
            for c in selected:
                r=rows[c['id']];u=ui_map[c['id']]
                w.writerow([c['id'],c['pairId'],c['language'],c['group'],c['text'],r['routeCorrect'],sum(r['checks'].get(k,False) for k in c['requiredFields']),len(c['requiredFields']),r['allChecksCorrect'],u['success'],r['latencyMs'],r.get('modelCalled'),','.join(k for k,v in r['checks'].items() if not v),r.get('error',''),u.get('reason',''),json.dumps(c['expected'],ensure_ascii=False),json.dumps(r.get('actual'),ensure_ascii=False)])
    table='\n'.join(f"| {l.upper()} | {v['cases']} | {fmt(v['routing'])} | {fmt(v['requiredFields'])} | {fmt(v['workflowEntry'])} | {fmt(v['clarification'])} | {v['latencyAll']['p50']/1000:.3f} / {v['latencyAll']['p95']/1000:.3f} s |" for l,v in result['byLanguage'].items())
    groups='\n'.join(f"| {l.upper()} | {g.title()} | {fmt(v['routing'])} | {fmt(v['requiredFields'])} | {fmt(v['workflowEntry'])} |" for l,values in result['byWorkflowLanguage'].items() for g,v in values.items())
    failures='\n'.join(f"| {c['id']} | {','.join(k for k,v in rows[c['id']]['checks'].items() if not v)} | {rows[c['id']].get('error','')} |" for c in cases if not rows[c['id']]['allChecksCorrect'])
    pairs={}
    for c in cases:pairs.setdefault(c['pairId'],{})[c['language']]=ui_map[c['id']]['success']
    paired={'both_pass':sum(p['en'] and p['zh'] for p in pairs.values()),'en_only':sum(p['en'] and not p['zh'] for p in pairs.values()),'zh_only':sum(p['zh'] and not p['en'] for p in pairs.values()),'neither':sum(not p['en'] and not p['zh'] for p in pairs.values())}
    save(OUT/'paired-outcomes.json',paired)
    e=model['environment'];s=result['overall']
    report=f'''# Lifehub Plus — Bilingual Holdout v2

240 inputs: **120 Chinese and 120 English**, with 30 per workflow in each language. Each language contains 98 supported requests and 22 expected clarifications. This is a newly authored internal set, frozen before inference and evaluated once per input, with no retries and no production changes during the run.

Language here refers to the user's input, not a full audit of every English/Chinese UI translation.
All case inputs are synthetic; they are not exported user financial, health or memo records.

![Language comparison](overview.png)

## Results by language

| Language | Inputs | Routing | Required-field checks | Supported workflow entry | Clarification | Latency P50 / P95 |
|---|---:|---:|---:|---:|---:|---:|
{table}

Overall: routing **{fmt(s['routing'])}**, required fields **{fmt(s['requiredFields'])}**, supported Android workflow entry **{fmt(s['workflowEntry'])}**, all expected Android outcomes **{fmt(s['allOutcomes'])}**. Model/backend errors: **{s['modelErrors']}**, retained in all relevant denominators. Additional Android failures among backend-correct cases: **{len(s['additionalUiFailures'])}**.

| Language | Workflow | Routing (30 inputs) | Required fields | Supported workflow entry |
|---|---|---:|---:|---:|
{groups}

## What the metrics mean

- Routing requires both the expected module and response status. Clarifications have no destination module.
- Clarification outcomes check the response type and a nonempty UI message, not the exact wording or correctness of every explanation. The separate reliability suite checks selected reason-specific messages.
- Required fields use the predeclared per-case field list. Expected nulls count only when the field is present and null; a pass does not mean an incomplete draft can be saved. This micro average weights fields, not modules. Health has no record-creation fields.
- Workflow entry requires every frozen backend field check to pass, then the recorded response to pass the actual Android ViewModel and enter the appropriate review form/Health page. UI checks cover representative fields and navigation, not every rendered control. Incorrect backend results stay failures even if a page could open.
- Android consumes each first-pass response through a test API adapter. This is **not** a new inference, per-case database insertion, full mobile network measurement, or certification of Android screen-time measurement accuracy. Database confirmation, isolation and duplicate prevention are checked separately.
- English title keywords are case-insensitive; Chinese title keywords are literal. This is bounded keyword matching, not full semantic grading. Finite money values compare as exact decimals, so equivalent formatting such as 19.90 and 19.9 is accepted. That money policy was frozen before inference and differs from v1 string matching.

## Separate reliability checks

Android reliability: **{reliability['passed']}/{reliability['total']} passed**, using the isolated QA package. Coverage includes specific clarification messages and retained editable input, cancellation, stale responses, Activity recreation, account isolation, duplicate confirmations and persistence/read-back. Backend offline verification: **59/59 passed**. These results are not percentages of natural-language accuracy.

## Frozen protocol and environment

- Dataset SHA-256: `{lock['datasetSha256']}`; frozen at `{lock['frozenAtUtc']}`.
- Model: `{e['model']}`; Ollama `{e['ollama']['version']}`. Exact model digest, quantization, source hashes and machine snapshot are in the linked artifacts.
- Hardware: Intel Core i5-12400F / NVIDIA RTX 3060 Ti, Windows 11; Python {e['python']}. See [machine.json](machine.json) for recorded details.
- Settings: temperature 0, seed 42, context 4096, generation cap 700, thinking disabled, keep-alive 10 minutes, concurrency 1.
- One sequential measured pass over all 240 inputs in fixed shuffled order; one excluded warmup ({e['warmupMs']/1000:.3f} seconds); no per-case retry. Rule-only clarifications do not invoke the model. Actual measured model calls: {sum(r.get('modelCalled',False) for r in model['results'])}.
- P50/P95 use nearest rank and include backend validation plus local model inference where invoked. Separate model-called latency is in [summary.json](summary.json). Android UI time, user editing and saving are excluded.
- No Gradle build or automated emulator tests were initiated during the model pass. They ran afterward. Other background OS/user workload was uncontrolled. One pass does not estimate run-to-run variability.
- All inference was local. No paid model service was called.
- Android replay environment, locale, animation scales and APK hashes are recorded in [android-environment.json](android-environment.json). Replay runs in an isolated QA installation and does not modify the user's production records.

## Interpretation and limitations

There are **120 paired bilingual scenarios**, not 240 statistically independent scenarios. Semantically matched inputs are not always literal translations. Timezone-boundary cases deliberately have different local dates. Pair outcomes: both pass {paired['both_pass']}, English only {paired['en_only']}, Chinese only {paired['zh_only']}, neither {paired['neither']}. These are descriptive counts, not evidence of general language superiority.

The set is internally authored with knowledge of the application's supported contract and earlier weaknesses; it is not externally independent. Exact prompt deduplication against prior sets passed, but some scenario families overlap. Both easy and adversarial cases are included; explicit workflow cues remain common. After these outcomes are inspected, v2 must be treated as regression data for future development.

This score should not be compared directly with v1 as an isolated improvement estimate: the examples, language balance, edge-case mix and money scoring differ. Original v1 and subsequent development results are retained separately. No failure was repaired by changing this dataset or production code during the evaluation.

## Failed backend cases

The 19 failed backend cases fall into four practical groups:

- **Health wording:** four English usage queries were rejected by the bounded supported-usage vocabulary; one Chinese query with a negated finance reference was classified as unclear by the model.
- **Action titles:** six outputs use a generic command as the title (for example, a calendar-entry command) instead of the actual activity. Some details remain elsewhere in the response; the frozen title requirement still fails.
- **Schedule times:** two Chinese clock expressions retain a date prefix or the word for early morning, so the clock parser leaves the time blank. A separate sports-health lecture is misrouted toward Health instead of Schedule.
- **Other intent/category errors:** two single memo requests are incorrectly clarified. Three financial categories remain blank when the same input says that the account is absent or uncertain; the item itself still gives a category clue.

These findings describe the observed errors, not changes made during this evaluation.

All raw model responses, outputs, expected fields and errors are preserved for review. A failed keyword check can represent a title/notes allocation issue rather than lost content; an error may be a protective grounding rejection. Neither is removed from the denominator.

| Case | Failed checks | Error |
|---|---|---|
{failures}

## Reproduce and audit

The runner refuses to overwrite a first-pass report and verifies frozen source/dataset hashes. Use a separate checkout matching the lock and a new output path for an additional run; later runs on these seen cases are regressions.

```sh
python backend/evaluate_holdout.py --live --cases backend/evaluation/holdout_v2.json --output NEW-RESULTS.json --machine docs/evaluation-v2/machine.json
python scripts/report-holdout-v2.py --prepare-replay
```

Build the isolated Android QA/test APKs with `-PisolatedTests=true`, then run `com.lifeHub.ai.HoldoutWorkflowTest` with instrumentation arguments `runHoldoutReplay=true` and `replayFixture=holdout-v2-replay.json`. Pull QA `files/holdout-ui-results.json` into `android-results.json` here; run `python scripts/report-holdout-v2.py` to summarize. The replay preparation reads the preserved first-pass report, not a second model run.

Downloads: [Chinese 120 cases](chinese-120.csv), [English 120 cases](english-120.csv), [all cases](all-cases.csv), [raw model results](model-results.json), [Android outcomes](android-results.json), [reliability](reliability-results.json), [frozen dataset](../../backend/evaluation/holdout_v2.json), [freeze lock](../../backend/evaluation/holdout_v2.lock.json).
'''
    (OUT/'REPORT.md').write_text(report,encoding='utf-8')
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    import numpy as np
    fig,ax=plt.subplots(figsize=(11,5),facecolor='#f4f8f7');ax.set_facecolor('#f4f8f7')
    keys=['routing','requiredFields','workflowEntry','clarification'];x=np.arange(4)
    for offset,lang,color in [(-.18,'zh','#18796d'),(.18,'en','#638bba')]:
        values=[result['byLanguage'][lang][k] for k in keys]
        bars=ax.bar(x+offset,[100*v['rate'] for v in values],.34,label='Chinese (120)' if lang=='zh' else 'English (120)',color=color)
        for bar,v in zip(bars,values):ax.text(bar.get_x()+bar.get_width()/2,bar.get_height()+1.5,f"{v['passed']}/{v['total']}",ha='center',fontsize=10)
    ax.set_xticks(x,['Routing','Required fields','Workflow entry','Clarification']);ax.set_ylim(0,115);ax.set_ylabel('Percent');ax.legend(loc='lower right')
    ax.spines[['top','right']].set_visible(False);ax.set_title('Lifehub Plus · Chinese / English holdout v2',loc='left',fontsize=19,pad=20)
    fig.text(.08,.015,'Internally authored paired scenarios · One frozen first pass · Workflow entry is not per-case database insertion',fontsize=9,color='#53635f')
    fig.tight_layout(rect=[0,.04,1,1]);fig.savefig(OUT/'overview.png',dpi=170);plt.close(fig)
    save(OUT/'artifact-hashes.json',{str(p.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in [case_path,case_path.with_suffix('.lock.json'),OUT/'model-results.json',OUT/'android-results.json',OUT/'reliability-results.json']})
    print(json.dumps(result['byLanguage'],indent=2))

if __name__=='__main__':main()
