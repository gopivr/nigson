#!/usr/bin/env python3
"""Select a complete release from the latest successful environment image build."""
import argparse
import json
import os
import re
import urllib.error
import urllib.parse
import urllib.request


class GitHub:
    def __init__(self, repository, token):
        if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository) or not token:
            raise ValueError('Repository and read-only Actions token are required')
        self.base = 'https://api.github.com/repos/' + repository + '/'
        self.token = token

    def get(self, path):
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *args, **kwargs):
                return None
        request = urllib.request.Request(self.base + path, headers={
            'Authorization': 'Bearer ' + self.token, 'Accept': 'application/vnd.github+json',
            'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'Nigson-backend-release'})
        try:
            with urllib.request.build_opener(NoRedirect()).open(request, timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            raise ValueError('GitHub Actions lookup failed: HTTP ' + str(error.code)) from None


def select_release(get, environment, branch, require_operations=False):
    if environment not in {'stage', 'prod'} or not branch:
        raise ValueError('Select stage or prod and the default branch')
    # Reusable publisher runs belong to their bootstrap caller. Merge all pages
    # before selecting so an older caller run cannot outrank a newer direct build.
    indexed = {}
    for workflow in ['backend-images-publish.yml', 'backend-aws-infrastructure.yml']:
        for page in range(1, 11):
            query = urllib.parse.urlencode({'branch': branch, 'event': 'workflow_dispatch',
                                           'status': 'success', 'per_page': 100, 'page': page})
            runs = get('actions/workflows/' + workflow + '/runs?' + query).get('workflow_runs', [])
            for run in runs:
                run_id = str(run.get('id', ''))
                if not run_id.isdigit():
                    raise ValueError('Invalid publication run ID returned by GitHub')
                indexed[run_id] = run
            if len(runs) < 100:
                break
    for run in sorted(indexed.values(), key=lambda r: int(r['id']), reverse=True):
        if (run.get('conclusion') != 'success' or run.get('status') != 'completed' or
                run.get('event') != 'workflow_dispatch' or run.get('head_branch') != branch):
            continue
        commit, run_id, attempt = run.get('head_sha', ''), str(run['id']), str(run.get('run_attempt', 1))
        if not re.fullmatch(r'[a-f0-9]{40}', commit) or not attempt.isdigit():
            raise ValueError('Invalid publication provenance returned by GitHub')
        current = f'backend-release-v2-{environment}-{commit}-{attempt}'
        legacy = f'backend-release-{environment}-{commit}-{attempt}'
        expected = {current} if require_operations else {current, legacy}
        matches = []
        artifact_page = 1
        while True:
            artifacts = get(f'actions/runs/{run_id}/artifacts?per_page=100&page={artifact_page}').get('artifacts', [])
            matches += [a for a in artifacts if a.get('name') in expected]
            if len(artifacts) < 100:
                break
            artifact_page += 1
        if not matches:
            continue
        if any(a.get('name') == current for a in matches):
            matches = [a for a in matches if a.get('name') == current]
        if len(matches) != 1 or matches[0].get('expired'):
            raise ValueError('Latest environment release artifact is expired or ambiguous; publish images again')
        artifact_id = str(matches[0].get('id', ''))
        if not artifact_id.isdigit():
            raise ValueError('Invalid immutable artifact ID')
        return {'artifact_id': artifact_id, 'run_id': run_id,
                'source_commit': commit, 'source_run_attempt': attempt}
    if require_operations:
        raise ValueError('No successful v2 image release including the scan-qualified operations image exists for ' + environment
                         + '. Run Backend — Build and Publish Images on the current default branch, wait for all scans and artifact upload to succeed, then start a NEW runtime/deploy run. Rerunning an old failed publication retains its old workflow.')
    raise ValueError('No complete successful release for this environment; run Backend — Build and Publish Images with the updated workflow first')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('environment', choices=['stage', 'prod'])
    parser.add_argument('--require-operations', action='store_true')
    args = parser.parse_args()
    try:
        github = GitHub(os.environ.get('GITHUB_REPOSITORY', ''), os.environ.get('GH_TOKEN', ''))
        result = select_release(github.get, args.environment, os.environ.get('DEFAULT_BRANCH', ''), require_operations=args.require_operations)
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            for key, value in result.items():
                output.write(f'{key}={value}\n')
        print('Selected successful image publication ' + result['run_id'] + ' at ' + result['source_commit'])
    except (ValueError, OSError) as error:
        raise SystemExit(str(error)) from None
