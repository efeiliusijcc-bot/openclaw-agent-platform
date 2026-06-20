#!/usr/bin/env python3
import json
import os
import subprocess
import time
import urllib.error
import urllib.request

BASE = os.environ.get("BASE", "http://127.0.0.1:8081/jeecg-boot")


def request(method, path, body=None, token=None, timeout=240):
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["X-Access-Token"] = token
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw)
        except Exception:
            parsed = {"raw": raw[:1000]}
        return exc.code, parsed


def norm(value):
    value = (value or "").strip()
    try:
        parsed = json.loads(value)
        if isinstance(parsed, str):
            return parsed.strip()
    except Exception:
        pass
    return value.strip('"')


def redis_code():
    keys = subprocess.check_output(["redis-cli", "--raw", "keys", "*"], text=True).splitlines()
    candidates = []
    for key in keys:
        try:
            ttl = int(subprocess.check_output(["redis-cli", "--raw", "ttl", key], text=True).strip() or "0")
        except Exception:
            ttl = 0
        if 0 < ttl <= 60:
            typ = subprocess.check_output(["redis-cli", "--raw", "type", key], text=True).strip()
            if typ != "string":
                continue
            val = norm(subprocess.check_output(["redis-cli", "--raw", "get", key], text=True))
            if val and len(val) == 4 and val.isalnum():
                candidates.append((ttl, val))
    if not candidates:
        raise RuntimeError("captcha redis key not found")
    candidates.sort(reverse=True)
    return candidates[0][1]


def save_file(draft_id, token, path, content):
    return request("POST", f"/openclaw/skill/draft/{draft_id}/file", {"path": path, "content": content}, token, timeout=120)


def main():
    results = []
    check_key = str(int(time.time() * 1000)) + "echo"
    status, image = request("GET", f"/sys/randomImage/{check_key}")
    results.append({"step": "captcha", "http": status, "success": bool(image and image.get("success"))})

    status, login = request("POST", "/sys/login", {
        "username": "admin",
        "password": "123456",
        "captcha": redis_code(),
        "checkKey": check_key,
    })
    token = (login or {}).get("result", {}).get("token") or (login or {}).get("result", {}).get("tokenValue")
    results.append({"step": "login", "http": status, "success": bool(login and login.get("success") and token), "message": (login or {}).get("message")})
    if not token:
        print(json.dumps({"results": results}, ensure_ascii=False, indent=2))
        return

    slug = "codex-echo-skill-" + str(int(time.time()))
    status, draft = request("POST", "/openclaw/skill/draft/add", {
        "draftName": "Codex Echo Skill",
        "skillSlug": slug,
        "description": "Minimal echo skill draft for runtime acceptance.",
    }, token)
    draft_obj = (draft or {}).get("result") or {}
    draft_id = draft_obj.get("id")
    results.append({"step": "create-draft", "http": status, "success": bool(draft and draft.get("success") and draft_id), "draftId": draft_id, "slug": slug, "message": (draft or {}).get("message")})
    if not draft_id:
        print(json.dumps({"results": results}, ensure_ascii=False, indent=2))
        return

    skill_md = """# Codex Echo Skill

## Purpose

Echo back the exact text requested by the user.

## When to use

Use this Skill when the user asks for an echo/smoke response.

## Inputs

- `text`: text to return exactly.

## Outputs

The same text, without commentary.

## Usage

If the prompt contains `ECHO_TEXT: <value>`, return exactly `<value>` and no other text.

## Examples

- Input: `ECHO_TEXT: hello`
- Output: `hello`

## Safety

No file, network, credential, database, or shell access is required.
"""
    files = {
        "SKILL.md": skill_md,
        "main.py": 'def run(input_text: str) -> str:\n    marker = "ECHO_TEXT:"\n    if marker in input_text:\n        return input_text.split(marker, 1)[1].strip().splitlines()[0].strip()\n    return input_text.strip().splitlines()[0].strip()\n',
        "requirements.txt": "# no dependencies\n",
        "README.md": "# Codex Echo Skill\n\nMinimal echo skill for draft runtime acceptance.\n",
        "examples/test_prompt.md": "ECHO_TEXT: ECHO_OK\n",
    }
    for path, content in files.items():
        status, response = save_file(draft_id, token, path, content)
        results.append({"step": "save-file", "path": path, "http": status, "success": bool(response and response.get("success")), "message": (response or {}).get("message")})

    status, lint = request("POST", f"/openclaw/skill/draft/{draft_id}/lint", token=token, timeout=120)
    lint_obj = (lint or {}).get("result") or {}
    results.append({"step": "lint", "http": status, "success": bool(lint and lint.get("success")), "passed": lint_obj.get("passed"), "errors": lint_obj.get("errors"), "warnings": lint_obj.get("warnings"), "message": (lint or {}).get("message")})

    expected = "ECHO_OK_" + str(int(time.time()))
    prompt = "Use the Codex Echo Skill. ECHO_TEXT: " + expected + "\nReturn exactly the echo text and no other words."
    status, test = request("POST", f"/openclaw/skill/draft/{draft_id}/test", {"prompt": prompt, "expectedOutput": expected, "localExecution": True}, token, timeout=300)
    test_obj = (test or {}).get("result") or {}
    output = test_obj.get("outputSummary") or ""
    error = test_obj.get("errorMessage") or ""
    results.append({
        "step": "test",
        "http": status,
        "success": bool(test and test.get("success")),
        "runStatus": test_obj.get("status"),
        "testRunId": test_obj.get("id"),
        "agentRunId": test_obj.get("agentRunId"),
        "containsExpected": expected in output,
        "output": output[:500],
        "error": error[:500],
        "message": (test or {}).get("message"),
    })

    status, records = request("GET", f"/openclaw/skill/draft/{draft_id}/tests?pageNo=1&pageSize=5", token=token)
    rec_obj = (records or {}).get("result") or {}
    results.append({"step": "test-history", "http": status, "success": bool(records and records.get("success")), "total": rec_obj.get("total")})
    print(json.dumps({"draftId": draft_id, "slug": slug, "expected": expected, "results": results}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
