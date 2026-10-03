"""Use the APK's exact prompt and validation adapter around captured native outputs."""
import argparse
import json
import sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT / "backend"), str(ROOT / "app/src/main/python")]
import mobile_bridge

parser = argparse.ArgumentParser()
parser.add_argument("mode", choices=["prepare", "validate"])
parser.add_argument("cases", type=Path)
parser.add_argument("output", type=Path)
parser.add_argument("--raw", type=Path)
args = parser.parse_args()
cases = json.loads(args.cases.read_text(encoding="utf-8"))
request = lambda c: json.dumps(dict(text=c["text"], timezone=c["timezone"], module=c.get("module", "auto")), ensure_ascii=False)
if args.mode == "prepare":
    result = [dict(id=c["id"], prepared=json.loads(mobile_bridge.prepare(request(c)))) for c in cases]
else:
    by_id = {c["id"]:c for c in cases}
    result = json.loads(args.raw.read_text(encoding="utf-8"))
    for row in result:
        if "actual" in row or "error" in row:
            continue
        c = by_id[row["id"]]
        try:
            row["actual"] = json.loads(mobile_bridge.validate(request(c), row["raw"], c["now"]))
        except Exception as error:
            row["error"] = getattr(error, "code", type(error).__name__)
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
