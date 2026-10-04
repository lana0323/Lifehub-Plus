"""Verify frozen inference/scoring sources plus explicitly pinned report-only changes."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPORTING_PATHS = {
    'scripts/mobile-evaluation/score_v4_regression.py',
    'scripts/mobile-evaluation/run_v4_regression.ps1',
    'scripts/mobile-evaluation/score_holdout.py',
}


def verify(protocol_path):
    protocol = json.loads(protocol_path.read_text(encoding='utf-8'))
    manifest = json.loads(protocol_path.with_name('reproduction.json').read_text(encoding='utf-8'))
    assert hashlib.sha256(protocol_path.read_bytes()).hexdigest() == manifest['recordedProtocolSha256'], 'Recorded protocol changed'
    overrides = manifest['reportingSourceSha256']
    assert set(overrides) == REPORTING_PATHS, 'Only report formatting and CLI entry points may have post-run overrides'
    assert set(manifest['additionalSourceSha256']) == {'scripts/mobile-evaluation/verify_sources.py'}, 'Unexpected additional source'
    hashes = {**protocol['sourceSha256'], **overrides, **manifest['additionalSourceSha256']}
    for name, digest in hashes.items():
        assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest, 'Changed source: ' + name
    assert hashlib.sha256((ROOT / protocol['dataset']).read_bytes()).hexdigest() == protocol['datasetSha256'], 'Dataset changed'
    return protocol


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--protocol', type=Path, default=ROOT / 'docs/evaluation-mobile-v4-regression/protocol.json')
    verify(parser.parse_args().protocol)
    print('Frozen production, inputs and scoring helpers verified; report-only maintenance is separately pinned.')
