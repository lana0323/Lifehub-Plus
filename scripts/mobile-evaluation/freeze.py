"""Record the mobile regression configuration before inference; never alter cases."""
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
files = [
    "backend/evaluation/holdout_v2.json", "backend/action_service.py",
    "backend/action_rules.py", "backend/task_service.py", "backend/evaluate_actions.py",
    "backend/evaluate_holdout.py", "app/src/main/python/mobile_bridge.py",
    "app/src/main/python/mobile_rules.py", "scripts/mobile-evaluation/NativeRunner.java",
    "scripts/mobile-evaluation/prepare_and_validate.py", "scripts/summarize-mobile-evaluation.py",
]
hashes = {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in files}
out = ROOT / "docs/evaluation-mobile/protocol.json"
assert not out.exists(), "Refusing to overwrite a frozen protocol"
assert hashes[files[0]] == "c152175c5faa9304bbfa5da7dd343215c8ee5dcfe264350731fae465b952abd4"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps({
    "frozenAtUtc": datetime.now(timezone.utc).isoformat(),
    "datasetRole": "Previously used bilingual regression set, not a new unseen holdout",
    "cases": 240, "languages": {"zh": 120, "en": 120},
    "runtime": "Windows JVM LiteRT-LM 0.10.2, CPU, JDK 21",
    "machine": json.loads((ROOT / "docs/evaluation-v2/machine.json").read_text(encoding="utf-8-sig")),
    "model": "litert-community/Qwen2.5-1.5B-Instruct int8 ekv4096",
    "modelRevision": "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
    "modelSha256": "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9",
    "sampling": {"topK": 1, "topP": 1.0, "temperature": 0.0, "seed": 42, "contextTokens": 4096},
    "concurrency": 1, "runsPerCase": 1, "retries": 0,
    "execution": "One host engine, fresh conversation per case; exact mobile prompt and validation adapter. App uses Android native binaries and initializes its engine per request.",
    "scope": "Model plus deterministic validation draft contract; Android UI/persistence checks are separate.",
    "sourceSha256": hashes,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(out)
