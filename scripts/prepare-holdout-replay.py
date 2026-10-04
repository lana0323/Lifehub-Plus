"""Prepare opt-in Android replay without calling the model again."""
import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parent.parent
case_path=root/'backend/evaluation/mobile_holdout_v4.json'
cases=json.loads(case_path.read_text(encoding='utf-8'))
report=json.loads((root/'docs/evaluation-mobile-v4-regression/results.json').read_text(encoding='utf-8'))
assert report['complete'] and len(report['results'])==len(cases)==240
assert hashlib.sha256(case_path.read_bytes()).hexdigest()==report['datasetSha256']
lookup={c['id']:c for c in cases}
fixture={'datasetSha256':report['datasetSha256'],'cases':[dict(r,text=lookup[r['id']]['text'],supported=lookup[r['id']]['expected']['status']=='draft') for r in report['results']]}
path=root/'app/src/androidTest/assets/holdout-replay.json';path.parent.mkdir(parents=True,exist_ok=True)
path.write_text(json.dumps(fixture,ensure_ascii=False),encoding='utf-8')
print('Prepared',len(cases),'recorded responses; no inference performed.')
