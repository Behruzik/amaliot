#!/usr/bin/env python3
"""Exercise the running local demo through HTTP. Python 3 standard library only.

Usage: python scripts/smoke_demo.py --base http://127.0.0.1:8080
Writes synthetic PDF examples and OpenAPI to docs/examples/. Never prints JWTs.
"""
import argparse
import json
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='http://127.0.0.1:8080')
    args = parser.parse_args()
    base = args.base.rstrip('/')
    if urlparse(base).hostname not in {'localhost', '127.0.0.1', '::1'}:
        raise SystemExit('This smoke check is only for a local disposable demo.')
    output = Path(__file__).resolve().parents[1] / 'docs' / 'examples'
    output.mkdir(parents=True, exist_ok=True)
    checks = []

    def call(method, path, payload=None, token=None, expected=200, pdf=False):
        headers = {'Accept': 'application/pdf' if pdf else 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        data = None
        if payload is not None:
            data = json.dumps(payload).encode('utf-8')
            headers['Content-Type'] = 'application/json'
        request = Request(base + path, data=data, headers=headers, method=method)
        try:
            response = urlopen(request, timeout=30)
        except HTTPError as error:
            response = error
        with response:
            content = response.read()
            status = response.status
            response_headers = dict(response.headers)
        if status != expected:
            raise AssertionError(f'{method} {path}: expected {expected}, got {status}: {content[:350]!r}')
        checks.append({'method': method, 'path': path, 'status': status})
        if pdf and status == 200:
            assert content.startswith(b'%PDF-'), 'Missing PDF signature'
            assert 'no-store' in response_headers.get('Cache-Control', '')
            return content, response_headers
        return json.loads(content) if content else None

    call('GET', '/actuator/health')
    spec = call('GET', '/v3/api-docs')
    assert spec['components']['securitySchemes']['bearerAuth']['scheme'] == 'bearer'
    login = call('POST', '/api/auth/login', {'email': 'student.demo@gmail.com', 'password': 'DemoUser123!'})
    student = login['accessToken']
    profile = call('GET', '/api/profile', token=student)
    assert profile['professionName'].startswith('Демо:'), 'The running database is not the supplied demo'
    result = next((value for value in call('GET', '/api/tests/results?size=100', token=student)['content']
                   if value['correctAnswers'] == 38 and value['totalQuestions'] == 40), None)
    assert result is not None, 'Missing seeded 38/40 result; restart the disposable demo'
    result_id = result['id']
    pdf, _ = call('GET', f'/api/tests/results/{result_id}/pdf', token=student, pdf=True)
    (output / 'demo-result.pdf').write_bytes(pdf)
    cert = call('GET', '/api/certificates', token=student)[0]
    number = cert['certificateNumber']
    pdf, headers = call('GET', f'/api/certificates/{number}/pdf?download=true', token=student, pdf=True)
    assert headers['Content-Disposition'].startswith('attachment')
    (output / 'demo-certificate.pdf').write_bytes(pdf)
    assert call('GET', f'/api/certificates/verify/{number}')['valid'] is True
    call('GET', f'/api/tests/results/{result_id}/pdf', expected=401, pdf=True)
    call('GET', '/api/admin/dashboard', token=student, expected=403)

    started = call('POST', f"/api/tests/categories/{profile['professionId']}/attempts", token=student)
    attempt_id = started['id']
    question = next(q for q in started['questions'] if q['answerType'] == 'SINGLE')
    option = question['options'][0]['id']
    path = f"/api/tests/attempts/{attempt_id}/questions/{question['id']}/answer"
    call('PUT', path, {'selectedOptionIds': [option]}, token=student)
    saved = call('GET', f'/api/tests/attempts/{attempt_id}', token=student)
    saved_question = next(q for q in saved['questions'] if q['id'] == question['id'])
    assert saved_question['selectedOptionIds'] == [option]
    call('POST', f'/api/tests/attempts/{attempt_id}/save', token=student)
    resumed = call('POST', f'/api/tests/attempts/{attempt_id}/resume', token=student)
    assert resumed['deadlineAt'] == started['deadlineAt'], 'Save/resume must not extend the deadline'
    call('PUT', path, {'selectedOptionIds': [9223372036854775807]}, token=student, expected=400)
    call('GET', f'/api/tests/results/{attempt_id}/pdf', token=student, expected=409, pdf=True)
    finished = call('POST', f'/api/tests/attempts/{attempt_id}/finish', token=student)
    repeated = call('POST', f'/api/tests/attempts/{attempt_id}/finish', token=student)
    assert finished == repeated, 'Finishing twice must preserve the exact result'
    call('PUT', path, {'selectedOptionIds': [option]}, token=student, expected=409)

    admin = call('POST', '/api/auth/login', {'email': 'admin.demo@example.test', 'password': 'DemoAdmin123!'})['accessToken']
    call('GET', '/api/admin/dashboard', token=admin)
    call('GET', f'/api/tests/results/{result_id}/pdf', token=admin, expected=404, pdf=True)
    call('POST', '/api/auth/logout', token=student)
    call('GET', '/api/profile', token=student, expected=401)
    (output / 'demo-result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    (output / 'openapi.json').write_text(json.dumps(spec, ensure_ascii=False, indent=2), encoding='utf-8')
    (output / 'smoke-checks.json').write_text(json.dumps(checks, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'PASS: {len(checks)} HTTP checks. PDF examples and OpenAPI: {output}')


if __name__ == '__main__':
    main()
