"""Audit the published unseen first pass without inference or result mutation."""
import hashlib
import json
import zipfile
from pathlib import Path

from score_holdout import ROOT, frozen_score, raw_route, summarize

DOC = ROOT / 'docs/evaluation-unseen-v5'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    publication = read_json(DOC / 'publication.json')
    for name, digest in publication['artifactSha256'].items():
        require(sha((ROOT / name).read_bytes()) == digest, 'Changed artifact: ' + name)
    protocol = read_json(DOC / 'protocol.json')
    require(sha((DOC / 'protocol.json').read_bytes()) == (DOC / 'protocol.sha256').read_text().split()[0], 'Protocol hash mismatch')
    archive = DOC / 'frozen-snapshot.zip'
    require(sha(archive.read_bytes()) == (DOC / 'frozen-snapshot.sha256').read_text().split()[0], 'Snapshot hash mismatch')

    with zipfile.ZipFile(archive) as snapshot:
        for name, digest in protocol['sourceSha256'].items():
            require(sha(snapshot.read(name)) == digest, 'Snapshot source mismatch: ' + name)
            if name.startswith('.local/'):
                continue  # Original host-specific authoring scripts remain in the frozen archive.
            mapped = publication['pathMapping'].get(name, name)
            require(sha((ROOT / mapped).read_bytes()) == digest, 'Changed source: ' + mapped)

    documentation_updates = publication.get('documentationUpdates', {})
    require(set(documentation_updates) <= {'docs/evaluation-mobile-v4-regression/REPORT.md'}, 'Unexpected documentation override')
    for name, digest in protocol['preservedRegressionSha256'].items():
        if name == 'README.md':
            continue  # The current README adds this publication; it is not an inference/scoring input.
        if name in documentation_updates:
            update = documentation_updates[name]
            require(update['originalSha256'] == digest, 'Original documentation hash mismatch')
            require(sha((ROOT / update['originalArchive']).read_bytes()) == digest, 'Changed archived regression report')
            require(sha((ROOT / name).read_bytes()) == update['updatedSha256'], 'Changed report navigation')
            continue  # Only the report's navigation wording changed; its original bytes remain available.
        require(sha((ROOT / name).read_bytes()) == digest, 'Changed regression evidence: ' + name)

    dataset = ROOT / publication['pathMapping'][protocol['dataset']]
    require(sha(dataset.read_bytes()) == protocol['datasetSha256'], 'Dataset hash mismatch')
    cases = read_json(dataset)
    report = read_json(DOC / 'results.json')
    captured = report['results']
    require(len(cases) == len(captured) == 120, 'Expected exactly 120 inputs and outputs')
    require(len({c['id'] for c in cases}) == 120, 'Duplicate input IDs')
    require([c['id'] for c in cases] == [r['id'] for r in captured], 'Input/output order mismatch')
    rows = {}
    for case, recorded in zip(cases, captured):
        row = dict(recorded)
        checks = frozen_score(case, row.get('actual', {}), decimal_amounts=True)
        if 'error' in row:
            checks = {k: False for k in case['expected']}
        invoked = 'raw' in row or 'error' in row
        route = raw_route(row) if invoked else None
        recomputed = dict(checks=checks, routeCorrect=checks['status'] and checks['module'],
                          allChecksCorrect=all(checks.values()), modelInvoked=invoked,
                          rawRoute=route, rawRouteCorrect=route == {k: case['expected'][k] for k in ('status', 'module')} if invoked else None)
        for key, expected in recomputed.items():
            require(row[key] == expected, f'Scoring mismatch: {case["id"]} {key}')
        rows[case['id']] = row
    require(summarize(cases, rows) == report['summary'], 'Overall summary mismatch')
    for language in ('zh', 'en'):
        selected = [c for c in cases if c['language'] == language]
        require(len(selected) == 60, 'Language split mismatch')
        require(summarize(selected, rows) == report['byLanguage'][language], 'Language summary mismatch')
    for group in ('memo', 'finance', 'schedule', 'health'):
        selected = [c for c in cases if c['group'] == group]
        require(len(selected) == 30, 'Workflow split mismatch')
        require(summarize(selected, rows) == report['byWorkflow'][group], 'Workflow summary mismatch')
    for tag in {t for c in cases for t in c['tags']}:
        require(summarize([c for c in cases if tag in c['tags']], rows) == report['byTag'][tag], 'Tag summary mismatch')
    print('Verified 120 frozen inputs, original outputs, all field checks and aggregates, source snapshot, and unchanged 240-case regression evidence.')
    print('This is an audit of recorded results, not a new model run.')


if __name__ == '__main__':
    main()
