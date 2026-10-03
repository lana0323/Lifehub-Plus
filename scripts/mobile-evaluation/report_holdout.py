"""Render a completed frozen holdout without changing its scores or answers."""
import csv
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/evaluation-mobile-holdout'
read=lambda p:json.loads(p.read_text(encoding='utf-8'))
protocol=read(OUT/'protocol.json')
report=read(OUT/'results.json')
run=read(OUT/'run.json')
cases=read(ROOT/protocol['dataset'])
assert report['complete'] and len(report['results'])==240
rows={r['id']:r for r in report['results']}
invoked=[r for r in report['results'] if r['modelInvoked']]
corrected=sum(not r['rawRouteCorrect'] and r['routeCorrect'] for r in invoked)
regressed=sum(r['rawRouteCorrect'] and not r['routeCorrect'] for r in invoked)
assert report['datasetSha256']==protocol['datasetSha256']

def fmt(value):
    return f"{value['passed']}/{value['total']} ({value['rate']:.1%})" if value['total'] else 'Not applicable'

metrics=[('Routing after validation','routing'),('Required fields','requiredFields'),('All specified fields','allSpecifiedFields'),
         ('Supported draft contract','supportedDraft'),('Clarification outcome','clarification'),('Date fields, including expected nulls','dates')]
lines=['# New mobile-model holdout: first-pass results','',
    '**Model:** Qwen2.5-1.5B-Instruct int8 (ekv4096), LiteRT-LM 0.10.2. **Execution:** Windows CPU, Intel i5-12400F, JDK 21.0.7, Python 3.11.7, tzdata 2026.2. The model file, prompt and validation match the frozen mobile implementation. These are host inference measurements; Android UI/database checks remain separate.','',
    '## Freeze and scope','',
    f"The 240 new inputs and expected answers were frozen at `{protocol['frozenAtUtc']}` and published in commit `1f8eb6b` before model inference. The evaluated production baseline is `eb798d5`. Each language has 120 inputs, with 30 per workflow group. In total, 180 inputs expect a reviewable draft and 60 expect clarification.",'',
    'This is a **new internally authored holdout**, not a third-party benchmark or a user-study sample. The development assistant authored and checked the expectations before inference. Scenario families overlap between languages. Exact/lexical deduplication found no matches above the declared threshold against the earlier inputs; that does not prove semantic independence.','',
    '**No model or production-rule tuning was performed during the run.** Each input was attempted once, with no retry or manual correction. Failed cases remain in the denominator. These are first-pass results, not the earlier seen-set validation replay.','',
    f"**Execution interruptions:** {len(run['interruptions'])} native-process crashes were recorded as failed inputs. The original completed prefix was preserved each time. A new process continued with the next input; the interrupted input was not retried. This recovery changes the initial single-process execution plan, not the model settings, frozen expectations or scoring rules. [Run provenance](run.json) records the affected IDs and evidence hashes.",'',
    '## Application-pipeline accuracy','',
    '| Metric | Chinese | English | Combined |','|---|---|---|---|']
for name,key in metrics:
    lines.append(f"| {name} | {fmt(report['byLanguage']['zh'][key])} | {fmt(report['byLanguage']['en'][key])} | {fmt(report['summary'][key])} |")
lines += ['',
    '- Routing requires the correct response status and module. A clarification incorrectly turned into a draft is a failure, even if saving still requires confirmation.',
    '- Required-field accuracy uses predeclared field annotations. Correctly leaving an ambiguous or missing field unset counts as correct; it does not mean the draft is ready to save.',
    '- All-specified-field accuracy includes optional fields that were annotated, such as memo priority and schedule time. Titles use fixed keyword alternatives; unannotated semantics are not exhaustively checked.',
    '- Supported draft contract includes only the 180 inputs expected to produce drafts. It does not measure opening an Android form or actually writing records.',
    '- Date accuracy includes both exact expected calendar dates and explicitly expected null values. Memo deadlines in the past stay unset; historical Schedule dates remain reviewable.','',
    '## Before application corrections','',
    '| Metric | Chinese | English | Combined |','|---|---|---|---|',
    f"| Raw model status/module | {fmt(report['byLanguage']['zh']['rawModelRouting'])} | {fmt(report['byLanguage']['en']['rawModelRouting'])} | {fmt(report['summary']['rawModelRouting'])} |",'',
    f"The model was invoked for **{report['summary']['modelCalls']}** inputs. The app's frozen preflight handled **{report['summary']['preflight']}** inputs without model generation. The raw-routing denominator contains only model-invoked cases, including failures; it is not directly comparable to the 240-input pipeline denominator. Raw routing reads only the model's emitted intent/module, before deterministic corrections.",'',
    f"On that same model-invoked subset, final routing passed **{sum(r['routeCorrect'] for r in invoked)}/{len(invoked)}**. Validation corrected **{corrected}** raw routing mistakes and changed **{regressed}** initially correct routes into failures. This paired diagnostic includes validation errors and is separate from the full 240-input primary score.",'',
    f"Pipeline errors: **{report['summary']['errors']}**. Inputs requiring clarification that instead received a draft: **{report['summary']['unsafeDrafts']}**. These are draft-level mistakes, not executed actions or automatic database writes.",'',
    '## Workflow groups','',
    '| Group | Routing | Required fields | All specified fields |','|---|---|---|---|']
for group,stats in report['byWorkflow'].items():
    lines.append(f"| {group.title()} | {fmt(stats['routing'])} | {fmt(stats['requiredFields'])} | {fmt(stats['allSpecifiedFields'])} |")
lines += ['', '## Field breakdown','', '| Field | Passed checks |', '|---|---|']
for name,stats in report['summary']['fields'].items():
    lines.append(f'| {name} | {fmt(stats)} |')
lines += ['', '## Improvement priorities from this run', '',
    '1. **Native text handling.** Four inputs containing emoji ended in a Windows native C++ exception. Keep these failures separate from language-understanding errors. The exact runtime cause needs investigation before changing input handling.',
    '2. **Unsupported operations.** Some requests to delete/update saved records, control apps or query unavailable data produced new drafts. Detect the requested operation as well as its destination module, and clarify unsupported operations.',
    '3. **Overbroad corrections.** The validator changed a correctly extracted salary transaction into Schedule after seeing “not a calendar event”, and changed a usage query into Memo after seeing a negated reminder. Negation and clause context need to constrain module overrides.',
    '4. **Ambiguity and paraphrases.** A clearly stated date can disappear when only the amount or appointment time is uncertain. Common Health phrasings such as “each application” or “每个软件” are also rejected despite correct model routing.',
    '5. **Extraction format and priority.** The first pass includes five schema-validation failures and two JSON-decoding failures in addition to the four native crashes. Memo priority passed 33/48 annotated checks. These need targeted development tests without rewriting this first-pass score.', '']
lines += ['', '## Failure cases','',
    'All failed inputs, expected fields and actual responses are retained in [failures.csv](failures.csv). The table below lists counts by the predeclared scenario tag; the categories were not chosen after seeing the model outputs.','',
    '| Scenario tag | Inputs | Failed all-field checks |','|---|---|---|']
for tag,stats in report['byTag'].items():
    failed=stats['allSpecifiedFields']['total']-stats['allSpecifiedFields']['passed']
    if failed: lines.append(f"| {tag} | {stats['cases']} | {failed} |")
lines += ['', '## Reproduction and files','',
    '- [Frozen inputs and expectations](../../backend/evaluation/mobile_holdout_v3.json)',
    '- [Protocol, source hashes and fixed settings](protocol.json)',
    '- [Excel workbook](Lifehub-Plus-New-Holdout.xlsx)',
    '- [Chinese 120 cases](chinese-120.csv), [English 120 cases](english-120.csv), [all outputs and checks](results.json)',
    '- [Recorded run provenance](run.json)', '',
    'Use the pinned model and runtime in the [installation guide](../ANDROID_INSTALL.md). From the repository root:', '',
    '```powershell',
    r".\scripts\mobile-evaluation\run_holdout.ps1 -ModelPath 'C:/models/mobile-qwen2.5.litertlm' -OutputDirectory '.local/new-holdout-reproduction'",
    '```', '',
    'The runner verifies frozen source/data hashes, refuses an existing raw output, and uses a fresh conversation per input. Its supervisor preserves native crashes as failures before restarting the engine for remaining inputs. Output goes to the supplied private directory. The app initializes an engine per request. For the exact evaluation snapshot, use the commits referenced above plus the recorded source hashes; changing production code requires a separately labeled run.', '',
    'The frozen unsupported-action policy expects clarification for requests to send messages or manipulate existing records. Some bare imperative wording can also be read as a description of a future task; this boundary is a limitation of the internally authored labels. Primary scores retain the frozen expectations. Title keywords and manually chosen categories likewise do not substitute for independent human semantic review.', '',
    'The [earlier 240-case seen-set regression](../evaluation-mobile/REPORT.md) is retained separately. Its optimized score is not a substitute for this first pass, and the two datasets differ in difficulty and outcome mix. Existing Android reliability results were not rerun as part of this model evaluation. After these failures inform future improvements, this set must also be treated as regression data; a fresh frozen set is needed for another unseen claim.', '']
(OUT/'REPORT.md').write_text('\n'.join(lines),encoding='utf-8')
with (OUT/'results.csv').open(encoding='utf-8-sig',newline='') as f:
    reader=csv.DictReader(f);headers=reader.fieldnames;source=list(reader)
with (OUT/'failures.csv').open('w',encoding='utf-8-sig',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=headers);writer.writeheader()
    writer.writerows(row for row in source if row['all_fields_correct']=='False')
print('First-pass report and failure table exported')
