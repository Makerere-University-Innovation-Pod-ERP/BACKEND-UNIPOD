#!/usr/bin/env python3
"""AI pull-request review for BACKEND-UNIPOD.

Two independent reviewers are supported, each backed by a free-tier API key
stored in repository secrets:

  * gemini -> GEMINI_API_KEY
  * groq   -> PRODUCTION_TESTER

Each reviewer inspects only the changed files in the pull request, posts (or
updates) a PR comment with actionable findings and a prompt to fix them, and
records a result file that the final ``--summary`` step uses to pass or fail the
workflow.

The script is dependency-free (standard library only) so it runs on a stock
GitHub-hosted runner.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

PROVIDERS = {
    "gemini": {
        "label": "Gemini",
        "secret_env": "GEMINI_API_KEY",
        "model_env": "GEMINI_MODEL",
        "default_models": [
            "gemini-3.8-flash",
            "gemini-3.6-flash",
            "gemini-2.5-flash",
        ],
        "max_diff_chars": 100_000,
        "max_spec_chars": 30_000,
    },
    "groq": {
        "label": "Groq",
        "secret_env": "PRODUCTION_TESTER",
        "model_env": "GROQ_MODEL",
        "default_models": ["llama-3.3-70b-versatile", "llama-3.1-8b-instant"],
        "max_diff_chars": 32_000,
        "max_spec_chars": 8_000,
    },
}

OPENAPI_META = {"label": "OpenAPI Contract", "marker": "openapi"}

SEVERITIES = ("blocker", "major", "minor")
BLOCKING_SEVERITIES = {"blocker", "major"}
CATEGORIES = (
    "correctness",
    "clean-code",
    "backend-principles",
    "performance",
    "api-contract",
    "security",
    "tests-and-migrations",
)

MAX_TOTAL_DIFF_CHARS = 100_000
MAX_FILE_DIFF_CHARS = 15_000
MAX_SPEC_FILE_CHARS = 200_000
MAX_COMMENT_CHARS = 60_000
RESULTS_DIR = Path("review-results")

OPENAPI_SPEC_PATTERN = re.compile(
    r"(^|/)(openapi|swagger)[^/]*\.(ya?ml|json)$", re.I
)

SKIP_PATTERNS = [
    re.compile(r"(^|/)target/"),
    re.compile(r"(^|/)build/"),
    re.compile(r"(^|/)\.mvn/"),
    re.compile(r"(^|/)\.idea/"),
    re.compile(r"(^|/)\.git/"),
    re.compile(r"(^|/)review-results/"),
    re.compile(r"(^|/)mvnw(\.cmd)?$"),
    re.compile(r"(^|/)package-lock\.json$"),
    re.compile(r"(^|/)yarn\.lock$"),
    re.compile(r"\.(png|jpe?g|gif|ico|pdf|jar|war|class|zip|gz|tgz|lock|bin)$", re.I),
]

PROMPT_HEADER = """\
You are a strict principal-level Java/Spring Boot reviewer and API architect for a
university ERP backend. The frontend team consumes this API and generates types
from the OpenAPI document, so both the code and the contract must be production
grade.

Stack: Java 21, Spring Boot 4.1, Spring MVC, Spring Data JPA, PostgreSQL, Flyway,
Spring Security + JWT, Lombok, Maven. Context path is /api.

Review ONLY the changed lines in the pull request diff below. Use unchanged
context only when needed to judge the change. The full OpenAPI contract in the
repository is included as reference; compare it against the changed controllers,
DTOs, validation, security and status codes.

Score the change against every dimension below. Reporting "no findings" for a
dimension is fine when it is genuinely clean.

1. Correctness and robustness
   - Broken logic, wrong edge cases, null/empty handling, date/time and timezone
     bugs, integer/overflow issues, UUID handling, idempotency.
   - Transaction boundaries, optimistic/pessimistic locking, concurrent updates,
     lost updates, race conditions, partial writes.

2. Clean code and maintainability
   - Naming, single responsibility, small cohesive methods, no dead or commented
     code, no duplication, no magic numbers/strings, meaningful constants.
   - Proper layering: controller -> service -> repository. No business logic,
     persistence or HTTP concerns in the wrong layer. No JPA entities leaking to
     the API; DTOs/mappers used consistently.
   - Exceptions: specific exceptions, centralised handling, no swallowed errors,
     no stack traces or internals leaked to clients.

3. Backend engineering principles
   - Constructor injection, immutable dependencies, no field injection.
   - Validation at the boundary (@Valid, constraints, custom validators), clear
     error messages, consistent problem+json error format.
   - Configuration externalised, no secrets/defaults in code, profiles correct.
   - Logging: structured, no sensitive data, correct levels, no logging in loops.
   - Flyway migrations: forward-only, safe for existing data, constraints, indexes,
     no destructive changes without a migration path, names/ordering correct.
   - Tests: unit/integration coverage for new behaviour and error paths, no
     brittle or meaningless tests.

4. Performance and optimisation
   - N+1 queries, missing fetch joins/entity graphs, loading full tables,
     unbounded result sets, missing pagination, missing database indexes.
   - Inefficient JPQL/SQL, repeated queries, unnecessary object creation/copies,
     needless serialisation, expensive work inside loops or transactions.
   - Caching, batching, streaming, connection pool and query-plan considerations
     where relevant. Flag premature optimisation only if it adds real complexity.

5. API contract and frontend consumption (critical)
   - REST semantics, correct HTTP methods, status codes (201 + Location, 200,
     204, 400, 401, 403, 404, 409, 422, 500) and consistent response envelopes.
   - Request/response schemas: required fields, nullability, enums, formats,
     constraints, ISO-8601 date-times, stable IDs, no leaking internal fields.
   - Pagination/filtering/sorting contracts defined and applied.
   - Validation error shape and field paths that a frontend can display.
   - Versioning, CORS, content types, idempotency for retried writes.
   - OpenAPI document accuracy: every changed or added endpoint, parameter,
     request body, response, status code, security requirement, enum value and
     required field must match the code. Missing operationId, undocumented
     fields, wrong types/status codes or stale examples are contract defects.
     Assume the frontend will break if the contract is ambiguous or wrong.

6. Security
   - Authentication and authorization on every sensitive endpoint, IDOR, mass
     assignment, injection (SQL/JPQL/SpEL), unsafe deserialization, SSRF.
   - Input size limits, rate limiting, CORS, CSRF where relevant, secrets handling,
     sensitive data in logs/responses, JWT validation and claim handling.

Reporting rules:
- Report only genuine, actionable defects. Do not invent issues or pad the list.
- Do not praise the code or summarise it generally.
- severity "blocker": build break, data loss/corruption, security hole, or a
  guaranteed runtime/API failure.
- severity "major": incorrect behaviour, broken/ambiguous API contract that the
  frontend cannot consume safely, missing validation/authorization, unsafe
  migration, or a real performance problem.
- severity "minor": clean-code, naming, duplication, readability, missing tests,
  small optimisation.
- "category" must be one of: correctness, clean-code, backend-principles,
  performance, api-contract, security, tests-and-migrations.
- "file" must be the repository-relative path. "line" is the line in the NEW file
  when it can be determined from the diff, otherwise an empty string.
- "evidence" must be a short exact snippet copied from the diff or spec.
- verdict is "fail" if there is at least one blocker or major finding, otherwise
  "pass".

Return STRICT JSON only, no markdown fences, matching this schema:
{
  "verdict": "pass" | "fail",
  "summary": "one to three sentences",
  "findings": [
    {
      "severity": "blocker" | "major" | "minor",
      "category": "correctness",
      "file": "relative/path",
      "line": "42",
      "title": "short issue title",
      "detail": "what is wrong and why it matters",
      "evidence": "exact snippet from the diff",
      "suggestion": "concrete fix"
    }
  ]
}

Changed files in this pull request:
"""


# ---------------------------------------------------------------------------
# Diff collection
# ---------------------------------------------------------------------------


def _git(*args: str) -> str:
    result = subprocess.run(
        ["git", *args], capture_output=True, text=True, check=False
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"git {' '.join(args)} failed:\n{result.stderr.strip()}"
        )
    return result.stdout


def _is_skipped(path: str) -> bool:
    return any(pattern.search(path) for pattern in SKIP_PATTERNS)


def collect_diff(base_ref: str, max_chars: int = MAX_TOTAL_DIFF_CHARS) -> tuple[str, list[str]]:
    _git("fetch", "--no-tags", "origin", base_ref)
    raw = _git("diff", "--unified=5", f"origin/{base_ref}...HEAD")
    return filter_diff(raw, max_chars)


def filter_diff(raw: str, max_chars: int = MAX_TOTAL_DIFF_CHARS) -> tuple[str, list[str]]:
    blocks = [block for block in re.split(r"(?m)^(?=diff --git )", raw) if block]
    kept: list[str] = []
    files: list[str] = []
    total = 0
    truncated = False

    for block in blocks:
        first_line = block.splitlines()[0]
        match = re.match(r"diff --git a/(.+?) b/(.+)$", first_line)
        path = match.group(2).strip('"') if match else first_line
        if _is_skipped(path):
            continue

        if len(block) > MAX_FILE_DIFF_CHARS:
            block = (
                block[:MAX_FILE_DIFF_CHARS]
                + "\n... [this file's diff was truncated] ...\n"
            )
            truncated = True

        if total + len(block) > max_chars:
            remaining = max_chars - total
            if remaining <= 0:
                truncated = True
                break
            block = block[:remaining] + "\n... [diff truncated] ...\n"
            truncated = True

        kept.append(block)
        files.append(path)
        total += len(block)
        if truncated and total >= max_chars:
            break

    diff = "".join(kept)
    if truncated:
        diff += "\n\n[Note: the diff was truncated to fit the review limit.]\n"
    return diff, files


def find_openapi_specs() -> list[tuple[str, str]]:
    specs: list[tuple[str, str]] = []
    for path in sorted(Path(".").rglob("*")):
        if not path.is_file():
            continue
        relative = path.as_posix()
        if relative.startswith("./"):
            relative = relative[2:]
        if _is_skipped(relative) or not OPENAPI_SPEC_PATTERN.search(relative):
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        if len(text) > MAX_SPEC_FILE_CHARS:
            text = text[:MAX_SPEC_FILE_CHARS] + "\n... [spec truncated] ...\n"
        specs.append((relative, text))
    return specs


def build_prompt(
    diff: str, files: list[str], specs: list[tuple[str, str]], spec_limit: int
) -> str:
    listing = "\n".join(f"- {name}" for name in files) or "- (no reviewable changes)"
    prompt = f"{PROMPT_HEADER}{listing}\n\nDiff:\n{diff}\n"

    if specs:
        prompt += "\n\nOpenAPI contract currently in the repository (reference; compare it with the code):\n"
        remaining = spec_limit
        for path, text in specs:
            if remaining <= 0:
                prompt += "\n[additional spec files omitted due to size]\n"
                break
            chunk = text[:remaining]
            remaining -= len(chunk)
            prompt += f"\n### {path}\n```yaml\n{chunk}\n```\n"
    else:
        prompt += (
            "\n\nNo OpenAPI document was found in the repository. If this change adds or "
            "modifies HTTP endpoints, flag the missing contract as a major api-contract issue.\n"
        )
    return prompt


# ---------------------------------------------------------------------------
# HTTP helpers and provider clients
# ---------------------------------------------------------------------------


def http_json(
    url: str,
    payload: dict | None = None,
    headers: dict[str, str] | None = None,
    method: str = "POST",
) -> tuple[int, dict | list | None, str]:
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            body = response.read().decode("utf-8", "replace")
            return response.status, _maybe_json(body), body
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", "replace")
        return error.code, _maybe_json(body), body
    except urllib.error.URLError as error:
        return 0, None, str(error)


def _maybe_json(text: str):
    try:
        return json.loads(text)
    except (json.JSONDecodeError, TypeError):
        return None


def call_gemini(api_key: str, models: list[str], prompt: str) -> tuple[str, str]:
    last_error = ""
    for model in models:
        url = (
            "https://generativelanguage.googleapis.com/v1beta/models/"
            f"{model}:generateContent?key={api_key}"
        )
        payload = {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {
                "temperature": 0.15,
                "responseMimeType": "application/json",
            },
        }
        status, data, body = http_json(url, payload)
        if status == 200 and isinstance(data, dict):
            try:
                text = data["candidates"][0]["content"]["parts"][0]["text"]
                return model, text
            except (KeyError, IndexError, TypeError):
                last_error = f"{model}: unexpected response shape"
                continue
        last_error = f"{model}: HTTP {status} {body[:300]}"
        if status not in (400, 404, 429, 500, 503):
            break
        time.sleep(1)
    raise RuntimeError(f"Gemini API failed. {last_error}")


def call_groq(api_key: str, models: list[str], prompt: str) -> tuple[str, str]:
    last_error = ""
    for model in models:
        payload = {
            "model": model,
            "temperature": 0.15,
            "messages": [{"role": "user", "content": prompt}],
            "response_format": {"type": "json_object"},
        }
        status, data, body = http_json(
            "https://api.groq.com/openai/v1/chat/completions",
            payload,
            {"Authorization": f"Bearer {api_key}"},
        )
        if status == 200 and isinstance(data, dict):
            try:
                text = data["choices"][0]["message"]["content"]
                return model, text
            except (KeyError, IndexError, TypeError):
                last_error = f"{model}: unexpected response shape"
                continue
        last_error = f"{model}: HTTP {status} {body[:300]}"
        if status not in (400, 404, 429, 500, 503):
            break
        time.sleep(1)
    raise RuntimeError(f"Groq API failed. {last_error}")


# ---------------------------------------------------------------------------
# Review parsing and rendering
# ---------------------------------------------------------------------------


def extract_json(text: str) -> dict:
    cleaned = text.strip()
    fence = re.search(r"```(?:json)?\s*(.*?)```", cleaned, re.S)
    if fence:
        cleaned = fence.group(1).strip()
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        pass
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start != -1 and end > start:
        return json.loads(cleaned[start : end + 1])
    raise ValueError("model response did not contain valid JSON")


def normalize_review(raw: dict) -> dict:
    findings: list[dict] = []
    for item in raw.get("findings") or []:
        if not isinstance(item, dict):
            continue
        severity = str(item.get("severity", "minor")).strip().lower()
        if severity not in SEVERITIES:
            severity = "minor"
        category = str(item.get("category", "")).strip().lower()
        if category not in CATEGORIES:
            category = ""
        findings.append(
            {
                "severity": severity,
                "category": category,
                "file": str(item.get("file", "")).strip(),
                "line": str(item.get("line", "")).strip(),
                "title": str(item.get("title", "Issue")).strip(),
                "detail": str(item.get("detail", "")).strip(),
                "evidence": str(item.get("evidence", "")).strip(),
                "suggestion": str(item.get("suggestion", "")).strip(),
            }
        )

    verdict = str(raw.get("verdict", "")).strip().lower()
    blocking = [f for f in findings if f["severity"] in BLOCKING_SEVERITIES]
    failed = verdict == "fail" or bool(blocking)

    return {
        "verdict": "fail" if failed else "pass",
        "summary": str(raw.get("summary", "")).strip(),
        "findings": findings,
    }


def count_by_severity(findings: list[dict]) -> dict[str, int]:
    counts = {severity: 0 for severity in SEVERITIES}
    for finding in findings:
        counts[finding["severity"]] += 1
    return counts


def _truncate(text: str, limit: int) -> str:
    return text if len(text) <= limit else text[: limit - 3] + "..."


def render_comment(
    provider: str,
    review: dict,
    model: str,
    label: str | None = None,
    marker: str | None = None,
    metadata: list[str] | None = None,
) -> str:
    default_meta = PROVIDERS.get(provider, {"label": provider})
    meta_label = label or default_meta["label"]
    heading = f"## {label}" if label else f"## AI Code Review - {meta_label}"
    marker = marker or f"<!-- ai-pr-review:{provider} -->"
    counts = count_by_severity(review["findings"])
    verdict = review["verdict"]
    headline = "FAIL - changes required" if verdict == "fail" else "PASS - no blocking issues"

    lines = [
        marker,
        heading,
        "",
        f"**Verdict:** {headline}",
        "",
        f"**Findings:** {counts['blocker']} blocker, {counts['major']} major, "
        f"{counts['minor']} minor",
        "",
    ]
    if review["summary"]:
        lines.extend([review["summary"], ""])

    if not review["findings"]:
        lines.extend(["No issues found in the changed files.", ""])
    else:
        for severity in SEVERITIES:
            group = [f for f in review["findings"] if f["severity"] == severity]
            if not group:
                continue
            lines.append(f"### {severity.capitalize()} ({len(group)})")
            lines.append("")
            for index, finding in enumerate(group, start=1):
                location = finding["file"] or "(unknown file)"
                if finding["line"]:
                    location += f":{finding['line']}"
                category = finding.get("category")
                tag = f" `[{category}]`" if category else ""
                lines.append(
                    f"{index}. **`{location}` - {finding['title']}**{tag}"
                )
                if finding["detail"]:
                    lines.append(f"   - {_truncate(finding['detail'], 1500)}")
                if finding["evidence"]:
                    snippet = _truncate(finding["evidence"], 600).replace("`", "'")
                    lines.append(f"   - Evidence: `{snippet}`")
                if finding["suggestion"]:
                    lines.append(
                        f"   - Fix: {_truncate(finding['suggestion'], 1500)}"
                    )
                lines.append("")
            lines.append("")
        lines.extend(
            [
                "### Prompt to fix these findings",
                "",
                "Check each finding against the current code. Fix every valid issue "
                "with a minimal change, add or update regression tests, and run the "
                "relevant checks. If a finding is not valid, explain why instead of "
                "making an unrelated change.",
                "",
            ]
        )

    if metadata is None:
        metadata = [
            f"- Reviewer: {meta_label} (`{model}`)",
            "- Scope: changed files, plus the current OpenAPI contract as reference",
            "- Dimensions: correctness, clean code, backend principles, performance, "
            "API contract, security, tests/migrations",
        ]
    lines.extend(
        [
            "<details><summary>Review metadata</summary>",
            "",
            *metadata,
            "- This is an automated review. A human reviewer still makes the final call.",
            "",
            "</details>",
        ]
    )
    return _truncate("\n".join(lines), MAX_COMMENT_CHARS)


# ---------------------------------------------------------------------------
# GitHub API
# ---------------------------------------------------------------------------


def github_api(
    method: str, path: str, token: str, payload: dict | None = None
) -> tuple[int, dict | list | None]:
    base = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    status, data, _ = http_json(
        f"{base}{path}",
        payload,
        {
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
        method=method,
    )
    return status, data


def upsert_comment(token: str, repo: str, pr_number: str, body: str, marker: str) -> None:
    status, data = github_api(
        "GET", f"/repos/{repo}/issues/{pr_number}/comments?per_page=100", token
    )
    if status == 200 and isinstance(data, list):
        for comment in data:
            if marker in (comment.get("body") or ""):
                comment_id = comment.get("id")
                patch_status, _ = github_api(
                    "PATCH",
                    f"/repos/{repo}/issues/comments/{comment_id}",
                    token,
                    {"body": body},
                )
                if patch_status in (200, 201):
                    return
                break

    post_status, post_data = github_api(
        "POST", f"/repos/{repo}/issues/{pr_number}/comments", token, {"body": body}
    )
    if post_status not in (200, 201):
        raise RuntimeError(f"could not post PR comment (HTTP {post_status}): {post_data}")


def changed_lines(diff: str) -> set[tuple[str, int]]:
    locations: set[tuple[str, int]] = set()
    path = ""
    new_line = 0
    in_hunk = False

    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            path = line[6:]
            in_hunk = False
            continue
        hunk = re.match(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@", line)
        if hunk:
            new_line = int(hunk.group(1))
            in_hunk = True
            continue
        if not in_hunk:
            continue
        if line.startswith("+"):
            locations.add((path, new_line))
            new_line += 1
        elif line.startswith(" "):
            new_line += 1
        elif not line.startswith("-") and not line.startswith("\\"):
            in_hunk = False
    return locations


def post_inline_review(
    token: str, repo: str, pr_number: str, review: dict, diff: str
) -> None:
    locations = changed_lines(diff)
    comments = []
    for finding in review["findings"]:
        file = finding["file"]
        line = finding["line"]
        if not line.isdigit() or (file, int(line)) not in locations:
            continue
        body = [
            f"**[{finding['severity'].upper()}] {finding['title']}**",
            "",
            finding["detail"],
        ]
        if finding["evidence"]:
            body.extend(["", f"Evidence: `{_truncate(finding['evidence'], 500)}`"])
        if finding["suggestion"]:
            body.extend(["", f"**Suggested fix:** {finding['suggestion']}"])
        comments.append({"path": file, "line": int(line), "side": "RIGHT", "body": "\n".join(body)})

    if not comments:
        return

    status, pull_request = github_api(
        "GET", f"/repos/{repo}/pulls/{pr_number}", token
    )
    if status != 200 or not isinstance(pull_request, dict):
        raise RuntimeError(f"could not get pull request details for inline review (HTTP {status})")
    head = pull_request.get("head")
    commit_id = head.get("sha") if isinstance(head, dict) else None
    if not commit_id:
        raise RuntimeError("pull request response did not include a head commit")

    status, data = github_api(
        "POST",
        f"/repos/{repo}/pulls/{pr_number}/reviews",
        token,
        {
            "commit_id": commit_id,
            "event": "COMMENT",
            "body": "Actionable findings from the automated code review.",
            "comments": comments[:50],
        },
    )
    if status not in (200, 201):
        raise RuntimeError(f"could not post inline review comments (HTTP {status}): {data}")


# ---------------------------------------------------------------------------
# Result files and summary
# ---------------------------------------------------------------------------


def write_result(name: str, result: dict) -> None:
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    (RESULTS_DIR / f"{name}.json").write_text(
        json.dumps(result, indent=2), encoding="utf-8"
    )


def run_review(provider: str, base_ref: str) -> int:
    meta = PROVIDERS[provider]
    secret = os.environ.get(meta["secret_env"], "").strip()
    pr_number = os.environ.get("PR_NUMBER", "").strip()
    repository = os.environ.get("GITHUB_REPOSITORY", "").strip()
    token = os.environ.get("GITHUB_TOKEN", "").strip()

    if not secret:
        message = (
            f"{meta['secret_env']} is not set for this run (fork pull requests do "
            "not receive repository secrets). Skipping this reviewer."
        )
        print(message)
        write_result(
            provider,
            {
                "provider": provider,
                "label": meta["label"],
                "status": "skipped",
                "reason": message,
                "verdict": "pass",
                "counts": {severity: 0 for severity in SEVERITIES},
            },
        )
        return 0

    diff, files = collect_diff(base_ref, meta["max_diff_chars"])
    if not diff.strip():
        print("No reviewable changes in this pull request; skipping.")
        write_result(
            provider,
            {
                "provider": provider,
                "label": meta["label"],
                "status": "skipped",
                "reason": "no reviewable changes",
                "verdict": "pass",
                "counts": {severity: 0 for severity in SEVERITIES},
            },
        )
        return 0

    models = []
    override = os.environ.get(meta["model_env"], "").strip()
    if override:
        models.append(override)
    models.extend(m for m in meta["default_models"] if m not in models)

    specs = find_openapi_specs()
    prompt = build_prompt(diff, files, specs, meta["max_spec_chars"])
    try:
        if provider == "gemini":
            model, text = call_gemini(secret, models, prompt)
        else:
            model, text = call_groq(secret, models, prompt)
        review = normalize_review(extract_json(text))
        if token and repository and pr_number:
            post_inline_review(token, repository, pr_number, review, diff)
            body = render_comment(provider, review, model)
            upsert_comment(token, repository, pr_number, body, f"<!-- ai-pr-review:{provider} -->")
        else:
            print("Missing PR context or token; not posting a comment.")
    except Exception as error:  # noqa: BLE001 - surface any failure to the PR
        print(f"Review failed: {error}", file=sys.stderr)
        write_result(
            provider,
            {
                "provider": provider,
                "label": meta["label"],
                "status": "error",
                "reason": str(error),
                "verdict": "fail",
                "counts": {severity: 0 for severity in SEVERITIES},
            },
        )
        if token and repository and pr_number:
            try:
                upsert_comment(
                    token,
                    repository,
                    pr_number,
                    (
                        f"<!-- ai-pr-review:{provider} -->\n"
                        f"## AI Code Review - {meta['label']}\n\n"
                        "The automated reviewer could not complete this run.\n\n"
                        f"```\n{_truncate(str(error), 1500)}\n```"
                    ),
                    f"<!-- ai-pr-review:{provider} -->",
                )
            except Exception as comment_error:  # noqa: BLE001
                print(f"Could not post failure comment: {comment_error}", file=sys.stderr)
        return 1

    counts = count_by_severity(review["findings"])
    write_result(
        provider,
        {
            "provider": provider,
            "label": meta["label"],
            "status": "ok",
            "model": model,
            "verdict": review["verdict"],
            "counts": counts,
            "summary": review["summary"],
        },
    )

    print(
        f"{meta['label']} review: {review['verdict']} "
        f"({counts['blocker']} blocker, {counts['major']} major, "
        f"{counts['minor']} minor) using {model}"
    )

    return 0


HTTP_METHODS = {"get", "post", "put", "patch", "delete", "head", "options", "trace"}


def _make_finding(
    severity: str,
    category: str,
    file: str,
    title: str,
    detail: str,
    suggestion: str,
    line: str = "",
) -> dict:
    return {
        "severity": severity,
        "category": category,
        "file": file,
        "line": line,
        "title": title,
        "detail": detail,
        "evidence": "",
        "suggestion": suggestion,
    }


def _load_spec(path: str, text: str) -> dict:
    try:
        import yaml
    except ImportError as error:
        raise RuntimeError(
            "PyYAML is not installed; cannot parse the OpenAPI document"
        ) from error
    try:
        data = yaml.safe_load(text)
    except Exception as error:  # noqa: BLE001 - report parse errors verbatim
        raise ValueError(f"{path}: YAML/JSON parse error: {error}") from error
    if not isinstance(data, dict):
        raise ValueError(f"{path}: OpenAPI document root must be a mapping")
    return data


def _schema_validation_errors(spec: dict) -> list[str]:
    try:
        from openapi_spec_validator import validate_spec
    except ImportError:
        try:
            from openapi_spec_validator import validate_v3_spec as validate_spec
        except ImportError:
            return []
    try:
        validate_spec(spec)
    except Exception as error:  # noqa: BLE001 - validator raises many error types
        messages = [line.strip() for line in str(error).splitlines() if line.strip()]
        return messages[:10] or ["OpenAPI specification validation failed"]
    return []


def _openapi_heuristics(path: str, spec: dict) -> list[dict]:
    findings: list[dict] = []
    info = spec.get("info")
    if not isinstance(info, dict) or not info.get("title") or not info.get("version"):
        findings.append(
            _make_finding(
                "major",
                "api-contract",
                path,
                "Missing API metadata",
                "info.title and info.version must be set so generated clients can identify the API.",
                "Add info.title and info.version.",
            )
        )

    paths = spec.get("paths")
    if not isinstance(paths, dict) or not paths:
        findings.append(
            _make_finding(
                "major",
                "api-contract",
                path,
                "No API paths documented",
                "The OpenAPI document exposes no endpoints, so the frontend cannot generate a client.",
                "Document every HTTP endpoint under paths.",
            )
        )
        return findings

    operation_ids: dict[str, str] = {}
    for url, item in paths.items():
        if not isinstance(item, dict):
            continue
        shared_params = [
            param for param in item.get("parameters", []) if isinstance(param, dict)
        ]
        for method, operation in item.items():
            if method.lower() not in HTTP_METHODS or not isinstance(operation, dict):
                continue
            location = f"{method.upper()} {url}"

            operation_id = operation.get("operationId")
            if not operation_id:
                findings.append(
                    _make_finding(
                        "major",
                        "api-contract",
                        path,
                        f"Missing operationId for {location}",
                        "Frontend client generators require a unique operationId; without one the generated method names are unstable.",
                        "Add a unique camelCase operationId.",
                    )
                )
            elif operation_id in operation_ids:
                findings.append(
                    _make_finding(
                        "major",
                        "api-contract",
                        path,
                        f"Duplicate operationId '{operation_id}'",
                        f"'{operation_id}' is used by both {operation_ids[operation_id]} and {location}.",
                        "Give every operation a unique operationId.",
                    )
                )
            else:
                operation_ids[operation_id] = location

            if not operation.get("summary"):
                findings.append(
                    _make_finding(
                        "minor",
                        "api-contract",
                        path,
                        f"Missing summary for {location}",
                        "A summary improves generated client docs and frontend discoverability.",
                        "Add a short summary.",
                    )
                )

            params = shared_params + [
                param
                for param in operation.get("parameters", [])
                if isinstance(param, dict)
            ]
            has_parameter_ref = any("$ref" in param for param in params)
            declared = {param.get("name") for param in params}
            for name in re.findall(r"\{([^}]+)\}", url):
                if not has_parameter_ref and name not in declared:
                    findings.append(
                        _make_finding(
                            "major",
                            "api-contract",
                            path,
                            f"Path parameter '{name}' is not declared",
                            f"{location} uses '{{{name}}}' in the URL but does not define it, so generated clients cannot call the endpoint.",
                            "Declare the path parameter with name, in: path, required: true and a schema.",
                        )
                    )

            responses = operation.get("responses")
            if not isinstance(responses, dict) or not responses:
                findings.append(
                    _make_finding(
                        "major",
                        "api-contract",
                        path,
                        f"Missing responses for {location}",
                        "Every operation must document at least one response so the frontend knows what to expect.",
                        "Document success and error responses.",
                    )
                )
                continue

            if not any(str(code).startswith("2") for code in responses):
                findings.append(
                    _make_finding(
                        "major",
                        "api-contract",
                        path,
                        f"No success response for {location}",
                        "The operation documents no 2xx response.",
                        "Document the success status code and body.",
                    )
                )
            if not any(
                str(code).startswith("4") or str(code) == "default"
                for code in responses
            ):
                findings.append(
                    _make_finding(
                        "minor",
                        "api-contract",
                        path,
                        f"No client-error response for {location}",
                        "Documenting 4xx responses lets the frontend handle validation and auth failures correctly.",
                        "Document the expected 4xx responses.",
                    )
                )

            for code, response in responses.items():
                if not isinstance(response, dict) or "$ref" in response:
                    continue
                content = response.get("content")
                if not isinstance(content, dict):
                    continue
                for media_type, media in content.items():
                    if not isinstance(media, dict) or "schema" in media:
                        continue
                    severity = "major" if str(code).startswith("2") else "minor"
                    findings.append(
                        _make_finding(
                            severity,
                            "api-contract",
                            path,
                            f"Response '{code}' has no schema ({location})",
                            f"The {media_type} response for '{code}' declares no schema, so the frontend cannot generate a type.",
                            "Add a schema (or a $ref to a component schema).",
                        )
                    )
    return findings


def render_openapi_comment(
    findings: list[dict], specs: list[str]
) -> tuple[dict, str]:
    counts = count_by_severity(findings)
    blocking = [f for f in findings if f["severity"] in BLOCKING_SEVERITIES]
    review = {
        "verdict": "fail" if blocking else "pass",
        "summary": (
            f"Validated {len(specs)} OpenAPI document(s): "
            f"{counts['blocker']} blocker, {counts['major']} major, "
            f"{counts['minor']} minor."
        ),
        "findings": findings,
    }
    metadata = [
        "- Specs: " + (", ".join(f"`{spec}`" for spec in specs) or "none"),
        "- Validation: OpenAPI schema validation plus contract consistency heuristics",
    ]
    body = render_comment(
        "openapi",
        review,
        "static validation",
        label="OpenAPI Contract Check",
        marker="<!-- ai-pr-review:openapi -->",
        metadata=metadata,
    )
    return review, body


def validate_openapi() -> int:
    pr_number = os.environ.get("PR_NUMBER", "").strip()
    repository = os.environ.get("GITHUB_REPOSITORY", "").strip()
    token = os.environ.get("GITHUB_TOKEN", "").strip()

    specs = find_openapi_specs()
    if not specs:
        message = "No OpenAPI document found in the repository."
        print(message)
        write_result(
            "openapi",
            {
                "provider": "openapi",
                "label": OPENAPI_META["label"],
                "status": "skipped",
                "reason": message,
                "verdict": "pass",
                "counts": {severity: 0 for severity in SEVERITIES},
            },
        )
        return 0

    findings: list[dict] = []
    validated: list[str] = []
    for path, text in specs:
        try:
            spec = _load_spec(path, text)
        except Exception as error:  # noqa: BLE001 - surface parse failures
            findings.append(
                _make_finding(
                    "blocker",
                    "api-contract",
                    path,
                    "OpenAPI document cannot be parsed",
                    str(error),
                    "Fix the YAML/JSON so the document parses.",
                )
            )
            continue

        version = spec.get("openapi") or spec.get("swagger") or "unknown"
        validated.append(f"{path} ({version})")
        for message in _schema_validation_errors(spec):
            findings.append(
                _make_finding(
                    "blocker",
                    "api-contract",
                    path,
                    "Invalid OpenAPI specification",
                    message,
                    "Fix the specification so it passes OpenAPI validation.",
                )
            )
        findings.extend(_openapi_heuristics(path, spec))

    review, body = render_openapi_comment(findings, validated)
    counts = count_by_severity(findings)
    write_result(
        "openapi",
        {
            "provider": "openapi",
            "label": OPENAPI_META["label"],
            "status": "ok",
            "verdict": review["verdict"],
            "counts": counts,
            "specs": validated,
        },
    )
    print(
        f"OpenAPI check: {review['verdict']} "
        f"({counts['blocker']} blocker, {counts['major']} major, "
        f"{counts['minor']} minor)"
    )

    if token and repository and pr_number:
        upsert_comment(
            token,
            repository,
            pr_number,
            body,
            "<!-- ai-pr-review:openapi -->",
        )
    else:
        print("Missing PR context or token; not posting a comment.")

    return 0


def write_summary() -> int:
    lines = ["## AI PR Review Summary", ""]
    blocked = False
    infra_failure = False
    found_any = False

    for path in sorted(RESULTS_DIR.glob("*.json")):
        found_any = True
        try:
            result = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            infra_failure = True
            lines.append(f"- {path.stem}: could not read result file")
            continue

        label = result.get("label", result.get("provider", path.stem))
        status = result.get("status", "unknown")
        verdict = result.get("verdict", "pass")
        counts = result.get("counts", {})
        detail = (
            f"{counts.get('blocker', 0)} blocker, "
            f"{counts.get('major', 0)} major, "
            f"{counts.get('minor', 0)} minor"
        )

        if status == "error":
            infra_failure = True
            lines.append(f"- **{label}:** review failed - {result.get('reason', '')}")
        elif status == "skipped":
            lines.append(f"- {label}: skipped ({result.get('reason', 'no reason')})")
        else:
            lines.append(f"- **{label}:** {verdict.upper()} ({detail})")
            if verdict == "fail":
                blocked = True

    if not found_any:
        lines.append("- No reviewer results were produced.")

    if blocked:
        lines.extend(
            [
                "",
                "### Action required",
                "",
                "Blocking issues were found. Read the review comments above, fix the "
                "code, and push the fixes to this branch. The review runs again "
                "automatically on every push.",
            ]
        )
    elif infra_failure:
        lines.extend(
            [
                "",
                "A reviewer failed to run. This is usually a missing secret or an "
                "API quota/outage, not a problem with the code.",
            ]
        )
    else:
        lines.extend(["", "No blocking issues were found."])

    summary = "\n".join(lines) + "\n"
    print(summary)
    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a", encoding="utf-8") as handle:
            handle.write(summary)

    return 1 if (blocked or infra_failure) else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--provider", choices=sorted(PROVIDERS))
    parser.add_argument("--base-ref", default=os.environ.get("BASE_REF", "main"))
    parser.add_argument(
        "--summary",
        action="store_true",
        help="Aggregate results and exit non-zero when the PR should be blocked.",
    )
    parser.add_argument(
        "--validate-openapi",
        action="store_true",
        help="Validate the repository OpenAPI document(s) and check contract quality.",
    )
    args = parser.parse_args()

    if args.summary:
        return write_summary()
    if args.validate_openapi:
        return validate_openapi()
    if not args.provider:
        parser.error(
            "--provider is required unless --summary or --validate-openapi is used"
        )
    return run_review(args.provider, args.base_ref)


if __name__ == "__main__":
    sys.exit(main())
