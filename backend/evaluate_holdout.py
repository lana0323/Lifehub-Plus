"""Frozen local holdout runner. Does not tune prompts or repair expected answers."""
import json,hashlib,time,math,platform,sys
from pathlib import Path
from datetime import datetime,timezone
from urllib.request import Request,build_opener,ProxyHandler
from evaluate_actions import score
from action_service import create_action,ACTION_SCHEMA,ACTION_SYSTEM
from task_service import call_local_model,local_settings
ROOT=Path(__file__).resolve().parent.parent

def ratio(values):
    values=list(values)
    return {'passed':sum(values),'total':len(values),'rate':sum(values)/len(values) if values else None}
def summary(cases,rows):
    mapped={r['id']:r for r in rows}
    selected=[(c,mapped[c['id']]) for c in cases if c['id'] in mapped]
    latency=sorted(r['latencyMs'] for _,r in selected)
    return {'cases':len(selected),'routingOverall':ratio(r['routeCorrect'] for _,r in selected),
      'routingSupported':ratio(r['routeCorrect'] for c,r in selected if c['expected']['status']=='draft'),
      'clarificationOutcome':ratio(r['routeCorrect'] for c,r in selected if c['expected']['status']=='clarification'),
      'requiredFieldMicro':ratio(r['checks'].get(k,False) for c,r in selected for k in c['requiredFields']),
      'allSpecifiedFields':ratio(r['allChecksCorrect'] for _,r in selected),
      'supportedDraftContract':ratio(r['allChecksCorrect'] for c,r in selected if c['expected']['status']=='draft'),
      'requiredFieldsOnCompleteInputs':ratio(r['checks'].get(k,False) for c,r in selected if c['tags']==['complete'] for k in c['requiredFields']),
      'latencyMs':{'p50':latency[math.ceil(.5*len(latency))-1],'p95':latency[math.ceil(.95*len(latency))-1],'min':min(latency),'max':max(latency)},
      'errors':sum('error' in r for _,r in selected)}
def main():
    if '--live' not in sys.argv:raise SystemExit('Use --live for local model calls')
    out=ROOT/'docs/evaluation/holdout-v1-results.json'
    if out.exists():raise SystemExit('First-pass results already exist; refusing to overwrite')
    source=ROOT/'backend/evaluation/holdout_v1.json';lock=json.loads((source.with_name('holdout_v1.lock.json')).read_text())
    assert hashlib.sha256(source.read_bytes()).hexdigest()==lock['datasetSha256']
    for f,h in lock['sourceHashes'].items():assert hashlib.sha256((ROOT/f).read_bytes()).hexdigest()==h,f
    cases=json.loads(source.read_text(encoding='utf-8'));base,model=local_settings()
    opener=build_opener(ProxyHandler({}))
    def api(path,data=None):
        req=Request(base+path,data=json.dumps(data).encode() if data else None,headers={'Content-Type':'application/json'})
        with opener.open(req,timeout=30) as r:return json.load(r)
    env={'machine':json.loads((ROOT/'docs/evaluation/machine.json').read_text(encoding='utf-8-sig')),'python':platform.python_version(),'ollama':api('/api/version'),'models':api('/api/tags'),'model':model,'modelDetails':api('/api/show',{'model':model}),'options':{'temperature':0,'seed':42,'num_ctx':4096,'num_predict':700,'think':False,'keep_alive':'10m'},'concurrency':1,'runsPerCase':1,'retries':0,'noCloud':True}
    env['modelDetails'].pop('modelfile', None)  # Contains a private local blob path.
    # Do not include any local machine name or model path.
    started=datetime.now(timezone.utc).isoformat();warm=time.perf_counter()
    call_local_model('Add a task to sharpen pencils tomorrow, normal priority.',ACTION_SCHEMA,ACTION_SYSTEM+'\nRequested module hint: auto')
    env['warmupMs']=round((time.perf_counter()-warm)*1000);env['loadedModel']=api('/api/ps')
    rows=[]
    for c in cases:
        raw_capture={};begin=time.perf_counter()
        def provider(text):
            raw,usage=call_local_model(text,ACTION_SCHEMA,ACTION_SYSTEM+'\nRequested module hint: auto')
            raw_capture.update(raw=raw,usage=usage);return raw,usage
        row={'id':c['id'],'checks':{k:False for k in c['expected']}}
        try:
            actual=create_action({k:c[k] for k in ('text','timezone','module')},provider,datetime.fromisoformat(c['now']))
            row.update(actual=actual,checks=score(c,actual))
        except Exception as e:row['error']=getattr(e,'code',type(e).__name__)
        row.update(raw_capture);row['latencyMs']=round((time.perf_counter()-begin)*1000)
        row['routeCorrect']=row['checks'].get('status',False) and row['checks'].get('module',False)
        row['allChecksCorrect']=all(row['checks'].values());rows.append(row)
        report={'startedAtUtc':started,'updatedAtUtc':datetime.now(timezone.utc).isoformat(),'datasetSha256':lock['datasetSha256'],'protocol':lock['protocol'],'environment':env,'summary':summary(cases,rows),'results':rows,'complete':len(rows)==len(cases)}
        if report['complete']:
            report['byWorkflow']={g:summary([c for c in cases if c['group']==g],rows) for g in ['memo','finance','schedule','health']}
            report['byLanguage']={l:summary([c for c in cases if c['language']==l],rows) for l in ['en','zh']}
        out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(f'{len(rows)}/120 {c["id"]} route={row["routeCorrect"]} fields={row["allChecksCorrect"]} {row["latencyMs"]}ms',flush=True)
    assert hashlib.sha256(source.read_bytes()).hexdigest()==lock['datasetSha256']
    print(json.dumps(report['summary'],indent=2))
if __name__=='__main__':main()
