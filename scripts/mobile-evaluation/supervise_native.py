"""Continue after a native crash, counting the interrupted input as a failed attempt."""
import argparse
import hashlib
import json
import re
import subprocess
from datetime import datetime,timezone
from pathlib import Path


def run(args):
    prepared=json.loads(args.prepared.read_text(encoding='utf-8'))
    args.logs.mkdir(parents=True,exist_ok=True)
    if args.raw.exists() and not args.resume:
        raise SystemExit('Raw output already exists. Refusing to repeat or overwrite a first pass.')
    manifest=args.logs/'native-interruptions.json'
    events=json.loads(manifest.read_text(encoding='utf-8')) if manifest.exists() else []
    while True:
        rows=json.loads(args.raw.read_text(encoding='utf-8')) if args.raw.exists() else []
        if [r['id'] for r in rows]!=[c['id'] for c in prepared[:len(rows)]]:
            raise SystemExit('Captured output does not match the prepared prefix')
        if len(rows)==len(prepared):
            print('All inputs accounted for; no interrupted input was retried',flush=True)
            return
        segment=1
        while (args.logs/f'native-{segment:02}.log').exists(): segment+=1
        log=args.logs/f'native-{segment:02}.log'
        command=[args.java,'-cp',args.classpath,'NativeRunner',str(args.model),str(args.prepared),str(args.raw),str(args.cache)]
        with log.open('wb') as stream:
            completed=subprocess.run(command,stdout=stream,stderr=subprocess.STDOUT,
                creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        if completed.returncode==0:
            done=json.loads(args.raw.read_text(encoding='utf-8'))
            if len(done)!=len(prepared): raise SystemExit('Native runner ended with incomplete results')
            print('Completed all inputs',flush=True)
            return
        output=log.read_text(encoding='utf-8',errors='replace')
        match=re.search(r'hs_err_pid\d+\.log',output)
        if not match: raise SystemExit(f'Unidentified runner failure; outputs preserved, see {log}')
        crash=Path.cwd()/match.group()
        message=crash.read_text(encoding='utf-8',errors='replace')
        if 'nativeSendMessage' not in message:
            raise SystemExit('Crash outside a uniquely identified inference attempt; inspect before resuming')
        rows=json.loads(args.raw.read_text(encoding='utf-8')) if args.raw.exists() else []
        if len(rows)>=len(prepared) or 'system' not in prepared[len(rows)]['prepared']:
            raise SystemExit('Cannot safely attribute this native failure to an input')
        case_id=prepared[len(rows)]['id']
        exception=next((line.strip('# ').strip() for line in message.splitlines() if 'EXCEPTION_' in line),'native inference failure')
        prefix=args.raw.read_bytes() if args.raw.exists() else b'[]'
        backup=args.logs/f'prefix-before-crash-{len(events)+1:02}.json'
        if backup.exists(): raise SystemExit('Crash prefix already archived')
        backup.write_bytes(prefix)
        events.append(dict(id=case_id,recordedAtUtc=datetime.now(timezone.utc).isoformat(),retried=False,
            completedBeforeCrash=len(rows),exception=exception,prefixSha256=hashlib.sha256(prefix).hexdigest(),
            crashLogSha256=hashlib.sha256(crash.read_bytes()).hexdigest(),nativeLog=log.name))
        rows.append(dict(id=case_id,error='native_process_crash: inference aborted without a completed response; case not retried',latencyMs=0,interrupted=True))
        partial=args.raw.with_suffix('.recovery.partial')
        partial.write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        partial.replace(args.raw)
        manifest.write_text(json.dumps(events,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(f'Native crash recorded as failure for {case_id}; continuing at the next input',flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('java','classpath'): p.add_argument('--'+name,required=True)
    for name in ('model','prepared','raw','cache','logs'): p.add_argument('--'+name,required=True,type=Path)
    p.add_argument('--resume',action='store_true',help='Resume an explicitly preserved prefix; never repeat completed inputs')
    run(p.parse_args())
