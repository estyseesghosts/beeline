import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1] / "scripts"))
import architecture_audit as audit


def source(body, package="me.foxtails.palustris.ui.feed"):
    return f"package {package}\n\n{body}\n"


class ArchitectureAuditTest(unittest.TestCase):
    def run_audit(self, body, package="me.foxtails.palustris.ui.feed", name="Example.kt", raw=False):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "app/src/main/java/me/foxtails/palustris/ui/feed" / name
            path.parent.mkdir(parents=True)
            path.write_text(body if raw else source(body, package), encoding="utf-8")
            return audit.audit(root)

    def test_deterministic_and_extracts_metrics(self):
        body = "fun f(a: Int, b: Int) { if (a > b) { println(a) } }"
        one, two = self.run_audit(body), self.run_audit(body)
        self.assertEqual(json.dumps(one, sort_keys=True), json.dumps(two, sort_keys=True))
        self.assertEqual(one["functionMetrics"][0]["params"], 2)

    def test_findings_and_metrics_are_sorted_directly(self):
        report = self.run_audit("""
            fun z() = Unit
            fun a() = Unit
            val cache = HashMap()
        """)
        self.assertEqual(report["findings"], sorted(report["findings"], key=lambda row: row["id"]))
        self.assertEqual(report["functionMetrics"], sorted(report["functionMetrics"], key=lambda row: (row["file"], row["name"])))

    def test_multiline_function_nested_lambda_and_threshold_findings(self):
        body = """fun long(
            a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, h: Int
        ) {
            listOf(1).forEach { if (a > 0) { if (b > 0) { if (c > 0) { if (d > 0) { if (e > 0) { if (f > 0) { println(it) } } } } } } }
        """ + "\n" * 80 + "}" 
        report = self.run_audit(body)
        kinds = {row["kind"] for row in report["findings"]}
        self.assertIn("function-size-warning", kinds)
        self.assertIn("function-parameter-warning", kinds)
        self.assertIn("function-nesting-warning", kinds)

    def test_file_size_warning(self):
        report = self.run_audit("\n".join(["val value = 1"] * 701))
        self.assertIn("file-size-warning", {row["kind"] for row in report["findings"]})

    def test_package_mismatch_and_missing_package(self):
        report = self.run_audit("fun x() = Unit", "me.foxtails.palustris.ui", "PostRow.kt")
        self.assertTrue(any(x["kind"] == "package-path-mismatch" for x in report["findings"]))
        missing = self.run_audit("fun x() = Unit", name="Missing.kt", raw=True)
        mismatch = next(x for x in missing["findings"] if x["kind"] == "package-path-mismatch")
        self.assertIn("<missing>", mismatch["detail"])

    def test_root_ui_detection(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "app/src/main/java/me/foxtails/palustris/ui/AppFeature.kt"
            path.parent.mkdir(parents=True)
            path.write_text(source("fun x() = Unit", "me.foxtails.palustris.ui"), encoding="utf-8")
            self.assertTrue(any(x["kind"] == "root-ui-feature-file" for x in audit.audit(root)["findings"]))

    def test_default_construction_and_ownership_class(self):
        report = self.run_audit("class SampleOwner(private val cache: Cache = Cache())")
        kinds = {x["kind"] for x in report["findings"]}
        self.assertIn("ownership-class", kinds)
        self.assertIn("private-owner-construction", kinds)

    def test_generic_and_non_generic_maps_require_scoped_retention(self):
        report = self.run_audit("""
            val generic = mutableMapOf<String, String>()
            private val plain: Map<String, String> = LinkedHashMap()
            val hash = HashMap()
        """)
        maps = [x for x in report["findings"] if x["kind"] == "unretained-long-lived-map"]
        self.assertEqual({x["detail"] for x in maps}, {"generic", "plain", "hash"})

    def test_retention_evidence_stays_in_the_same_scope(self):
        report = self.run_audit("""
            fun retained() {
                val cache = HashMap()
                cache.clear()
            }
            fun unrelated() {
                val other = HashMap()
                other.clear()
            }
            fun isolated() {
                val missing = HashMap()
            }
        """)
        maps = {x["detail"] for x in report["findings"] if x["kind"] == "unretained-long-lived-map"}
        self.assertEqual(maps, {"missing"})

    def test_owner_body_retention_is_available_to_top_level_property(self):
        report = self.run_audit("""
            object CacheOwner {
                val cache = HashMap()
                fun release() {
                    cache.clear()
                }
            }
        """)
        self.assertNotIn("unretained-long-lived-map", {x["kind"] for x in report["findings"]})

    def test_top_level_maps_do_not_use_unrelated_file_evidence(self):
        report = self.run_audit("""
            val cache = HashMap()
            fun cleanup() {
                cache.clear()
            }
            val unrelated = HashMap()
            unrelated.clear()
        """)
        maps = {x["detail"] for x in report["findings"] if x["kind"] == "unretained-long-lived-map"}
        self.assertEqual(maps, {"cache"})

    def test_top_level_map_comment_can_name_retention_policy(self):
        report = self.run_audit("""
            // cache has a bounded retention policy.
            val cache = HashMap()
            val unrelated = HashMap()
            unrelated.clear()
        """)
        maps = {x["detail"] for x in report["findings"] if x["kind"] == "unretained-long-lived-map"}
        self.assertEqual(maps, {"unrelated"})

    def test_retention_baseline_rule_allows_existing_map(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "app/src/main/java/me/foxtails/palustris/ui/feed/Example.kt"
            path.parent.mkdir(parents=True)
            path.write_text(source("val cache = HashMap()"), encoding="utf-8")
            report = audit.audit(root, {path.relative_to(root).as_posix(): "documented owner lifetime"})
            self.assertNotIn("unretained-long-lived-map", {x["kind"] for x in report["findings"]})

    def test_dependency_and_retention_rules(self):
        report = self.run_audit("import me.foxtails.palustris.ui.profile.Profile\nval values = mutableMapOf<String, String>()")
        kinds = {x["kind"] for x in report["findings"]}
        self.assertIn("dependency-direction", kinds)
        self.assertIn("unretained-long-lived-map", kinds)

    def test_real_resolved_return_has_stable_id(self):
        # Slice 0 keeps this resolved-return check in a fixture; it must not change production Kotlin runtime behavior.
        report = self.run_audit("fun x() = Unit", "me.foxtails.palustris.ui", "PostRow.kt")
        violation = next(x for x in report["findings"] if x["kind"] == "package-path-mismatch")
        repeated = self.run_audit("fun x() = Unit", "me.foxtails.palustris.ui", "PostRow.kt")
        same = next(x for x in repeated["findings"] if x["kind"] == "package-path-mismatch")
        self.assertEqual(violation["id"], same["id"])
        classified = audit.classify(report, {"resolvedFindings": [violation["id"]]})
        returned = next(x for x in classified["findings"] if x["id"] == violation["id"])
        self.assertEqual(returned["classification"], "regression-returned-resolved")

    def test_classification_for_new_violations_and_existing_baseline(self):
        mismatch = self.run_audit("fun x() = Unit", "me.foxtails.palustris.ui", "PostRow.kt")
        mismatch_id = next(x["id"] for x in mismatch["findings"] if x["kind"] == "package-path-mismatch")
        self.assertTrue(audit.classify(mismatch, {})["regressions"])
        self.assertFalse(audit.classify(mismatch, {"existingFindings": [mismatch_id]})["regressions"])
        map_report = self.run_audit("val cache = HashMap()")
        self.assertTrue(audit.classify(map_report, {})["regressions"])
        owner_report = self.run_audit("class NewOwner(private val cache: Cache = Cache())")
        self.assertTrue(audit.classify(owner_report, {})["regressions"])

    def test_warnings_do_not_fail(self):
        report = self.run_audit("\n".join(["val value = 1"] * 701))
        result = audit.classify(report, {})
        self.assertFalse(result["regressions"])

    def test_hotspot_and_function_growth_are_regressions(self):
        grown = {"findings": [], "fileMetrics": {"a.kt": {"lines": 200}}, "functionMetrics": []}
        grown = audit.classify(grown, {"fileMetrics": {"a.kt": {"lines": 100}}})
        self.assertIn("hotspot-growth", {x["kind"] for x in grown["regressions"]})
        report = {"findings": [], "fileMetrics": {}, "functionMetrics": [{"file": "a.kt", "name": "f", "lines": 90, "params": 8, "nesting": 6}]}
        baseline = {"functionMetrics": [{"file": "a.kt", "name": "f", "lines": 70, "params": 6, "nesting": 4}]}
        result = audit.classify(report, baseline)
        self.assertIn("function-complexity-growth", {x["kind"] for x in result["regressions"]})


if __name__ == "__main__":
    unittest.main()
