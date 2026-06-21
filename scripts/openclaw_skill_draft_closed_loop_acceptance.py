#!/usr/bin/env python3
import json
import subprocess
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8081/jeecg-boot"


def request(method, path, body=None, token=None, timeout=300):
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


def ok(resp):
    return bool(resp and resp.get("success"))


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


def login(results):
    check_key = str(int(time.time() * 1000)) + "closedloop"
    status, image = request("GET", f"/sys/randomImage/{check_key}")
    results.append({"step": "captcha", "http": status, "success": ok(image)})
    status, login_resp = request("POST", "/sys/login", {
        "username": "admin",
        "password": "123456",
        "captcha": redis_code(),
        "checkKey": check_key,
    })
    token = (login_resp or {}).get("result", {}).get("token") or (login_resp or {}).get("result", {}).get("tokenValue")
    results.append({"step": "login", "http": status, "success": ok(login_resp) and bool(token), "message": (login_resp or {}).get("message")})
    return token


def save_file(draft_id, token, path, content):
    return request("POST", f"/openclaw/skill/draft/{draft_id}/file", {"path": path, "content": content}, token)


def create_draft(token, name, slug_prefix, description):
    slug = slug_prefix + "-" + str(int(time.time()))
    status, draft = request("POST", "/openclaw/skill/draft/add", {
        "draftName": name,
        "skillSlug": slug,
        "description": description,
    }, token)
    return status, draft, ((draft or {}).get("result") or {}).get("id"), slug


def echo_flow(token):
    results = []
    status, draft, draft_id, slug = create_draft(token, "Closed Loop Echo Skill", "closed-loop-echo", "Echo JSON text exactly.")
    results.append({"step": "echo-create-draft", "http": status, "success": ok(draft) and bool(draft_id), "draftId": draft_id, "slug": slug})
    if not draft_id:
        return results
    files = {
        "SKILL.md": """# Echo JSON Skill

## Purpose

Return the input JSON text field unchanged.

## When to use

Use this Skill for draft runtime smoke tests that must not call external services.

## Inputs

- JSON object with `text`.

## Outputs

- JSON object with the same `text`.

## Examples

Input: {"text":"hello"}
Output: {"text":"hello"}

## Safety

No file, network, credential, database, or shell access is required.
""",
        "main.py": "import json\n\ndef run(input_text: str) -> str:\n    data = json.loads(input_text)\n    return json.dumps({'text': data.get('text', '')}, ensure_ascii=False, separators=(',', ':'))\n",
        "requirements.txt": "# no dependencies\n",
        "README.md": "# Echo JSON Skill\n",
        "examples/test_prompt.md": "{\"text\":\"hello\"}\n",
    }
    for path, content in files.items():
        status, response = save_file(draft_id, token, path, content)
        results.append({"step": "echo-save-file", "path": path, "http": status, "success": ok(response)})
    status, versions = request("GET", f"/openclaw/skill/draft/{draft_id}/versions", token=token)
    version_rows = (versions or {}).get("result") or []
    latest_version = version_rows[0] if version_rows else {}
    first_version_no = version_rows[-1].get("versionNo") if version_rows else None
    results.append({
        "step": "echo-version-list-after-save",
        "http": status,
        "success": ok(versions) and len(version_rows) >= len(files),
        "count": len(version_rows),
        "latestVersionNo": latest_version.get("versionNo"),
        "latestSource": latest_version.get("sourceType"),
    })
    status, lint = request("POST", f"/openclaw/skill/draft/{draft_id}/lint", token=token)
    lint_result = (lint or {}).get("result") or {}
    results.append({"step": "echo-lint", "http": status, "success": ok(lint), "passed": lint_result.get("passed"), "status": lint_result.get("status")})
    status, versions_after_lint = request("GET", f"/openclaw/skill/draft/{draft_id}/versions", token=token)
    latest_after_lint = (((versions_after_lint or {}).get("result") or [{}])[0] or {})
    results.append({
        "step": "echo-version-lint-bound",
        "http": status,
        "success": ok(versions_after_lint) and latest_after_lint.get("lintStatus") == "lint_passed",
        "versionNo": latest_after_lint.get("versionNo"),
        "lintStatus": latest_after_lint.get("lintStatus"),
    })
    expected = '{"text":"hello"}'
    status, test = request("POST", f"/openclaw/skill/draft/{draft_id}/test", {
        "prompt": expected,
        "expectedOutput": expected,
        "localExecution": True,
    }, token, timeout=300)
    test_result = (test or {}).get("result") or {}
    test_run_id = test_result.get("id")
    results.append({"step": "echo-test", "http": status, "success": ok(test), "runStatus": test_result.get("status"), "containsExpected": expected in (test_result.get("outputSummary") or ""), "testRunId": test_run_id, "agentKey": test_result.get("agentKey")})
    if test_run_id:
        status, report = request("GET", f"/openclaw/skill/draft/{draft_id}/tests/{test_run_id}/report", token=token)
        report_result = (report or {}).get("result") or {}
        results.append({"step": "echo-report", "http": status, "success": ok(report), "status": report_result.get("status"), "lintStatus": report_result.get("lintStatus"), "gatewayStatus": report_result.get("gatewayStatus"), "agentKey": report_result.get("agentKey"), "draftVersionNo": report_result.get("draftVersionNo")})
        if report_result.get("draftVersionNo"):
            status, detail = request("GET", f"/openclaw/skill/draft/{draft_id}/versions/{report_result.get('draftVersionNo')}", token=token)
            detail_result = (detail or {}).get("result") or {}
            results.append({
                "step": "echo-version-detail",
                "http": status,
                "success": ok(detail) and bool(detail_result.get("files")) and bool(detail_result.get("testReport")),
                "versionNo": detail_result.get("versionNo"),
                "testStatus": detail_result.get("testStatus"),
                "hasFiles": bool(detail_result.get("files")),
                "hasReport": bool(detail_result.get("testReport")),
            })
    status, versions_after_test = request("GET", f"/openclaw/skill/draft/{draft_id}/versions", token=token)
    latest_after_test = (((versions_after_test or {}).get("result") or [{}])[0] or {})
    results.append({
        "step": "echo-version-test-bound",
        "http": status,
        "success": ok(versions_after_test) and latest_after_test.get("testStatus") == "success",
        "versionNo": latest_after_test.get("versionNo"),
        "testStatus": latest_after_test.get("testStatus"),
    })
    if first_version_no:
        status, diff = request("GET", f"/openclaw/skill/draft/{draft_id}/versions/diff?fromVersionNo={first_version_no}", token=token)
        diff_result = (diff or {}).get("result") or {}
        results.append({
            "step": "echo-version-diff-current",
            "http": status,
            "success": ok(diff) and len(diff_result.get("diffs") or []) > 0,
            "fromVersionNo": first_version_no,
            "diffCount": len(diff_result.get("diffs") or []),
        })
        status, rollback = request("POST", f"/openclaw/skill/draft/{draft_id}/versions/{first_version_no}/rollback", token=token)
        rollback_result = (rollback or {}).get("result") or {}
        results.append({
            "step": "echo-version-rollback",
            "http": status,
            "success": ok(rollback) and rollback_result.get("sourceType") == "rollback" and rollback_result.get("lintStatus") is None and rollback_result.get("testStatus") is None,
            "rollbackVersionNo": rollback_result.get("versionNo"),
            "sourceType": rollback_result.get("sourceType"),
        })
        status, relint = request("POST", f"/openclaw/skill/draft/{draft_id}/lint", token=token)
        relint_result = (relint or {}).get("result") or {}
        results.append({"step": "echo-version-rollback-lint", "http": status, "success": ok(relint), "passed": relint_result.get("passed")})
    return results


def invoice_ai_edit_flow(token):
    results = []
    status, draft, draft_id, slug = create_draft(token, "Closed Loop Invoice AI Edit", "closed-loop-invoice", "Generic document extraction draft.")
    results.append({"step": "invoice-create-draft", "http": status, "success": ok(draft) and bool(draft_id), "draftId": draft_id, "slug": slug})
    if not draft_id:
        return results
    save_file(draft_id, token, "SKILL.md", "# Generic Document Skill\n\n## Purpose\n\nExtract structured fields from documents.\n\n## Inputs\n\nDocument text.\n\n## Outputs\n\nJSON.\n\n## Safety\n\nDo not access secrets.\n")
    status, preview = request("POST", f"/openclaw/skill/draft/{draft_id}/ai-edit/preview", {"instruction": "把这个 Skill 改成发票抽取 Skill"}, token, timeout=300)
    result = (preview or {}).get("result") or {}
    results.append({"step": "invoice-ai-edit-preview", "http": status, "success": ok(preview), "recordId": result.get("recordId"), "fileCount": len(result.get("files") or []), "warningCount": len(result.get("warnings") or []), "hasSummary": bool(result.get("summary"))})
    if result.get("recordId"):
        status, applied = request("POST", f"/openclaw/skill/draft/{draft_id}/ai-edit/apply", {"recordId": result.get("recordId"), "reason": result.get("summary")}, token)
        results.append({"step": "invoice-ai-edit-apply", "http": status, "success": ok(applied)})
        status, readback = request("GET", f"/openclaw/skill/draft/{draft_id}/file?path=SKILL.md", token=token)
        content = (((readback or {}).get("result") or {}).get("content") or "")
        results.append({"step": "invoice-readback", "http": status, "success": ok(readback), "mentionsInvoice": "发票" in content or "invoice" in content.lower()})
        status, lint = request("POST", f"/openclaw/skill/draft/{draft_id}/lint", token=token)
        lint_result = (lint or {}).get("result") or {}
        results.append({"step": "invoice-lint", "http": status, "success": ok(lint), "passed": lint_result.get("passed")})
    return results


def failed_repair_flow(token):
    results = []
    status, draft, draft_id, slug = create_draft(token, "Closed Loop Failed Repair", "closed-loop-repair", "Repair acceptance draft.")
    results.append({"step": "repair-create-draft", "http": status, "success": ok(draft) and bool(draft_id), "draftId": draft_id, "slug": slug})
    if not draft_id:
        return results
    files = {
        "SKILL.md": """# Broken Echo Skill

## Purpose

Echo JSON text, but the implementation is intentionally broken for repair acceptance.

## When to use

Use for AI Repair smoke tests.

## Inputs

- JSON object with `text`.

## Outputs

- JSON object with the same `text`.

## Examples

Input: {"text":"hello"}
Output: {"text":"hello"}

## Safety

Do not access secrets or external services.
""",
        "main.py": "def run(input_text: str) -> str:\n    raise RuntimeError('intentional repair smoke failure')\n",
        "requirements.txt": "# no dependencies\n",
        "README.md": "# Broken Echo Skill\n",
    }
    for path, content in files.items():
        status, response = save_file(draft_id, token, path, content)
        results.append({"step": "repair-save-file", "path": path, "http": status, "success": ok(response)})
    status, lint = request("POST", f"/openclaw/skill/draft/{draft_id}/lint", token=token)
    lint_result = (lint or {}).get("result") or {}
    results.append({"step": "repair-lint-before-test", "http": status, "success": ok(lint), "passed": lint_result.get("passed")})
    expected = '{"text":"hello"}'
    status, test = request("POST", f"/openclaw/skill/draft/{draft_id}/test", {"prompt": expected, "expectedOutput": expected, "localExecution": True}, token, timeout=300)
    test_result = (test or {}).get("result") or {}
    test_run_id = test_result.get("id")
    results.append({"step": "repair-failed-test", "http": status, "success": ok(test), "runStatus": test_result.get("status"), "testRunId": test_run_id, "error": (test_result.get("errorMessage") or "")[:300]})
    if test_run_id:
        status, repair = request("POST", f"/openclaw/skill/draft/{draft_id}/repair", {
            "testRunId": test_run_id,
            "instruction": "Fix the failing local Python Skill so it returns the same JSON text field.",
        }, token, timeout=300)
        repair_result = (repair or {}).get("result") or {}
        results.append({"step": "repair-preview", "http": status, "success": ok(repair), "recordId": repair_result.get("recordId"), "testRunId": repair_result.get("testRunId"), "fileCount": len(repair_result.get("files") or []), "hasSummary": bool(repair_result.get("summary")), "beforeStatus": repair_result.get("repairBeforeStatus")})
        if repair_result.get("recordId"):
            status, applied = request("POST", f"/openclaw/skill/draft/{draft_id}/repair/apply", {"recordId": repair_result.get("recordId"), "reason": repair_result.get("summary")}, token)
            applied_result = (applied or {}).get("result") or {}
            results.append({"step": "repair-apply", "http": status, "success": ok(applied), "afterStatus": applied_result.get("repairAfterStatus")})
            status, relint = request("POST", f"/openclaw/skill/draft/{draft_id}/lint", token=token)
            relint_result = (relint or {}).get("result") or {}
            results.append({"step": "repair-lint-after-apply", "http": status, "success": ok(relint), "passed": relint_result.get("passed")})
    return results


def gateway_log_check():
    try:
        output = subprocess.check_output(["journalctl", "-u", "openclaw-gateway", "--since", "20 minutes ago", "--no-pager"], text=True, errors="replace")
    except Exception as exc:
        return {"step": "gateway-log-check", "success": False, "message": str(exc)}
    markers = ["unknown agent id", "1006", "abnormal", "FailoverError"]
    hits = [marker for marker in markers if marker in output]
    return {"step": "gateway-log-check", "success": not hits, "hits": hits}


def main():
    results = []
    token = login(results)
    if not token:
        print(json.dumps({"success": False, "results": results}, ensure_ascii=False, indent=2))
        return
    results.extend(invoice_ai_edit_flow(token))
    results.extend(echo_flow(token))
    results.extend(failed_repair_flow(token))
    results.append(gateway_log_check())
    success = all(item.get("success") is not False for item in results)
    print(json.dumps({"success": success, "results": results}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
