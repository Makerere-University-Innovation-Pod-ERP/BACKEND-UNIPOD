import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import pr_ai_review


class ReviewOutputTests(unittest.TestCase):
    def test_gemini_defaults_use_supported_models(self):
        models = pr_ai_review.PROVIDERS["gemini"]["default_models"]

        self.assertEqual(models[0], "gemini-3.8-flash")
        self.assertEqual(models, ["gemini-3.8-flash", "gemini-3.6-flash"])

    def test_gemini_review_uses_first_configured_model(self):
        response = {
            "candidates": [
                {"content": {"parts": [{"text": '{"verdict":"pass","findings":[]}'}]}}
            ]
        }
        with patch.object(
            pr_ai_review, "http_json", return_value=(200, response, "")
        ) as request:
            model, _ = pr_ai_review.call_gemini(
                "test-key",
                pr_ai_review.PROVIDERS["gemini"]["default_models"],
                "prompt",
            )

        self.assertEqual(model, "gemini-3.8-flash")
        self.assertIn(
            "/models/gemini-3.8-flash:generateContent",
            request.call_args.args[0],
        )

    def test_groq_request_uses_configured_api_key(self):
        response = {
            "choices": [{"message": {"content": '{"verdict":"pass","findings":[]}'}}]
        }
        with patch.object(pr_ai_review, "http_json", return_value=(200, response, "")) as request:
            pr_ai_review.call_groq("test-key", ["test-model"], "prompt")

        self.assertEqual(
            request.call_args.args[2]["Authorization"], "Bearer test-key"
        )

    def test_github_request_uses_configured_token(self):
        with patch.object(pr_ai_review, "http_json", return_value=(200, [], "")) as request:
            pr_ai_review.github_api("GET", "/repos/example/repo/pulls/1", "test-token")

        self.assertEqual(
            request.call_args.args[2]["Authorization"], "Bearer test-token"
        )

    def test_changed_lines_maps_added_lines_to_new_file_locations(self):
        diff = """\
diff --git a/src/Example.java b/src/Example.java
index 1234567..abcdefg 100644
--- a/src/Example.java
+++ b/src/Example.java
@@ -1,2 +1,3 @@
 existing line
+added line
 next line
"""

        self.assertEqual(
            pr_ai_review.changed_lines(diff),
            {("src/Example.java", 2)},
        )

    def test_findings_on_changed_lines_are_posted_as_inline_review_comments(self):
        diff = """\
diff --git a/src/Example.java b/src/Example.java
index 1234567..abcdefg 100644
--- a/src/Example.java
+++ b/src/Example.java
@@ -1 +1,2 @@
 existing line
+added line
"""
        review = {
            "findings": [
                {
                    "severity": "major",
                    "title": "Missing validation",
                    "detail": "Invalid input is accepted.",
                    "evidence": "added line",
                    "suggestion": "Validate the input.",
                    "file": "src/Example.java",
                    "line": "2",
                }
            ]
        }
        with patch.object(
            pr_ai_review,
            "github_api",
            side_effect=[
                (200, {"head": {"sha": "test-commit"}}),
                (201, {"id": 1}),
            ],
        ) as request:
            pr_ai_review.post_inline_review(
                "test-token", "example/repo", "1", review, diff
            )

        payload = request.call_args_list[1].args[3]
        self.assertEqual(payload["event"], "COMMENT")
        self.assertEqual(
            payload["comments"][0]["path"], "src/Example.java"
        )
        self.assertEqual(payload["comments"][0]["line"], 2)
        self.assertIn("Suggested fix", payload["comments"][0]["body"])

    def test_finding_comment_includes_fix_prompt(self):
        review = {
            "verdict": "fail",
            "summary": "A validation issue was found.",
            "findings": [
                {
                    "severity": "major",
                    "category": "correctness",
                    "file": "src/Example.java",
                    "line": "12",
                    "title": "Missing validation",
                    "detail": "Invalid input is accepted.",
                    "evidence": "save(request)",
                    "suggestion": "Validate the request before saving.",
                }
            ],
        }

        comment = pr_ai_review.render_comment("gemini", review, "test-model")

        self.assertIn("Missing validation", comment)
        self.assertIn("Fix: Validate the request before saving.", comment)
        self.assertIn("### Prompt to fix these findings", comment)
        self.assertIn("add or update regression tests", comment)


if __name__ == "__main__":
    unittest.main()
