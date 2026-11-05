#!/usr/bin/env python3
"""AI pull-request review for BACKEND-UNIPOD.

Two independent reviewers are supported, each backed by a free-tier API key
stored in repository secrets:

  * gemini -> GEMINI_API_KEY
  * groq   -> PRODUCTION_TESTER

Each reviewer inspects only the changed files in the pull request, posts (or
updates) a single PR comment with actionable findings, and records a result file
that the final ``--summary`` step uses to pass or fail the workflow.

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
        "default_models": ["gemini-2.5-flash", "gemini-2.0-flash"],
    },
    "groq": {
        "label": "Groq",
        "secret_env": "PRODUCTION_TESTER",
        "model_env": "GROQ_MODEL",
        "default_models": ["llama-3.3-70b-versatile", "llama-3.1-8b-instant"],
    },
}

SEVERITIES = ("blocker", "major", "minor")
BLOCKING_SEVERITIES = {"blocker", "major"}

MAX_TOTAL_DIFF_CHARS = 100_000
MAX_FILE_DIFF_CHARS = 15_000
MAX_COMMENT_CHARS = 60_000
RESULTS_DIR = Path("review-results")

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
You are a strict senior Java/Spring Boot code reviewer for a university ERP backend.

Review ONLY the changed lines in the pull request diff below. Ignore unchanged
context unless it is required to understand the change.

What to look for:
- Real bugs, broken logic, null/empty handling, concurrency or transaction issues.
- Security problems: SQL/JPQL injection, missing authorization checks, leaked
  secrets or credentials, unsafe deserialization, missing input validation.
- Spring correctness: wrong bean wiring, missing @Transactional, JPA N+1 queries,
  Flyway migration problems (irreversible/destructive DDL, missing constraints).
- API correctness: wrong status codes, missing error handling, inconsistent DTOs.
- Cleanliness: dead code, duplicated logic, misleading names, missing tests for
  behaviour changes, dead configuration.

Rules:
- Report only genuine issues. Do not invent problems and do not pad the review.
- Do not praise or summarise the code generally.
- severity "blocker": breaks the build, data loss, security hole, or guaranteed
  runtime failure.
- severity "major": incorrect behaviour, missing validation/authorization, risky
  migration, or a bug that will surprise users.
- severity "minor": clean-code, readability, naming, duplication, missing tests.
- "line" is the line number in the NEW file when it can be determined from the
  diff, otherwise an empty string.
- "evidence" must be a short exact snippet copied from the diff.
- verdict is "fail" if there is at least one blocker or major finding, otherwise
  "pass".

Return STRICT JSON only, no markdown fences, matching this schema:
{
  "verdict": "pass" | "fail",
  "summary": "one to three sentences",
  "findings": [
    {
      "severity": "blocker" | "major" | "minor",
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


def collect_diff(base_ref: str) -> tuple[str, list[str]]:
    _git("fetch", "--no-tags", "origin", base_ref)
    raw = _git("diff", "--unified=5", f"origin/{base_ref}...HEAD")
    return filter_diff(raw)


def filter_diff(raw: str) -> tuple[str, list[str]]:
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

        if total + len(block) > MAX_TOTAL_DIFF_CHARS:
            remaining = MAX_TOTAL_DIFF_CHARS - total
            if remaining <= 0:
                truncated = True
                break
            block = block[:remaining] + "\n... [diff truncated] ...\n"
            truncated = True

        kept.append(block)
        files.append(path)
        total += len(block)
        if truncated and total >= MAX_TOTAL_DIFF_CHARS:
            break

    diff = "".join(kept)
    if truncated:
        diff += "\n\n[Note: the diff was truncated to fit the review limit.]\n"
    return diff, files


def build_prompt(diff: str, files: list[str]) -> str:
    listing = "\n".join(f"- {name}" for name in files) or "- (no reviewable changes)"
    return f"{PROMPT_HEADER}{listing}\n\nDiff:\n{diff}\n"


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
        findings.append(
            {
                "severity": severity,
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


def render_comment(provider: str, review: dict, model: str) -> str:
    meta = PROVIDERS[provider]
    counts = count_by_severity(review["findings"])
    verdict = review["verdict"]
    headline = "FAIL - changes required" if verdict == "fail" else "PASS - no blocking issues"

    lines = [
        f"<!-- ai-pr-review:{provider} -->",
        f"## AI Code Review - {meta['label']}",
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
                lines.append(f"{index}. **`{location}` - {finding['title']}**")
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
            "<details><summary>Review metadata</summary>",
            "",
            f"- Reviewer: {meta['label']} (`{model}`)",
            "- Scope: changed files only",
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

    diff, files = collect_diff(base_ref)
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

    prompt = build_prompt(diff, files)
    try:
        if provider == "gemini":
            model, text = call_gemini(secret, models, prompt)
        else:
            model, text = call_groq(secret, models, prompt)
        review = normalize_review(extract_json(text))
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

    if token and repository and pr_number:
        body = render_comment(provider, review, model)
        upsert_comment(token, repository, pr_number, body, f"<!-- ai-pr-review:{provider} -->")
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
    args = parser.parse_args()

    if args.summary:
        return write_summary()
    if not args.provider:
        parser.error("--provider is required unless --summary is used")
    return run_review(args.provider, args.base_ref)


if __name__ == "__main__":
    sys.exit(main())
