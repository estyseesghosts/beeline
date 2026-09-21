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

    def run_audit_files(self, files):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for rel, body in files.items():
                path = root / rel
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(body, encoding="utf-8")
            return audit.audit(root)

    def test_deterministic_and_extracts_metrics(self):
        body = "fun f(a: Int, b: Int) { if (a > b) { println(a) } }"
        one, two = self.run_audit(body), self.run_audit(body)
        self.assertEqual(json.dumps(one, sort_keys=True), json.dumps(two, sort_keys=True))
        self.assertEqual(one["functionMetrics"][0]["params"], 2)

    def test_java_and_kotlin_roots_are_discovered(self):
        report = self.run_audit_files({
            "app/src/main/java/me/foxtails/palustris/ui/feed/Feed.kt":
                source("fun x() = Unit", "me.foxtails.palustris.ui.feed"),
            "app/src/main/kotlin/me/foxtails/palustris/ui/feed/Extra.kt":
                source("fun y() = Unit", "me.foxtails.palustris.ui.feed"),
        })
        self.assertEqual(
            sorted(report["fileMetrics"]),
            ["app/src/main/java/me/foxtails/palustris/ui/feed/Feed.kt",
             "app/src/main/kotlin/me/foxtails/palustris/ui/feed/Extra.kt"],
        )
        self.assertFalse([x for x in report["findings"] if x["kind"] == "package-path-mismatch"])

    def test_kotlin_root_package_mismatch_uses_kotlin_root(self):
        report = self.run_audit_files({
            "app/src/main/kotlin/me/foxtails/palustris/ui/feed/Extra.kt":
                source("fun y() = Unit", "me.foxtails.palustris.ui"),
        })
        mismatch = next(x for x in report["findings"] if x["kind"] == "package-path-mismatch")
        self.assertIn("me.foxtails.palustris.ui.feed", mismatch["detail"])

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

    def test_root_ui_global_allowlist_exempts_shared_files(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "app/src/main/java/me/foxtails/palustris/ui/NewShared.kt"
            path.parent.mkdir(parents=True)
            path.write_text(source("fun x() = Unit", "me.foxtails.palustris.ui"), encoding="utf-8")
            self.assertTrue(any(x["kind"] == "root-ui-feature-file" for x in audit.audit(root)["findings"]))
            allowed = audit.audit(root, allowlists={"rootUiGlobalFiles": ["NewShared"]})
            self.assertFalse(any(x["kind"] == "root-ui-feature-file" for x in allowed["findings"]))

    def test_default_construction_and_ownership_class(self):
        report = self.run_audit("class SampleOwner(private val cache: Cache = Cache())")
        kinds = {x["kind"] for x in report["findings"]}
        self.assertIn("ownership-class", kinds)
        self.assertIn("private-owner-construction", kinds)

    def test_mutation_owner_construction_is_exempt_but_authority_defaults_are_flagged(self):
        exempt = self.run_audit("class PostViewModel(val owner: PostInteractionMutationOwner = PostInteractionMutationOwner())")
        self.assertNotIn("private-owner-construction", {x["kind"] for x in exempt["findings"]})
        for construction in ("CapabilityCache()", "DraftWriteAuthority()", "ExecutionAuthority()", "HttpClientPool()"):
            report = self.run_audit(f"class SampleOwner(val value: Any = {construction})")
            self.assertIn("private-owner-construction", {x["kind"] for x in report["findings"]})
        bare = self.run_audit("class SampleOwner(val value: Any = Owner())")
        self.assertIn("private-owner-construction", {x["kind"] for x in bare["findings"]})
        lowercase = self.run_audit("class SampleOwner(val value: Any = cache())")
        self.assertNotIn("private-owner-construction", {x["kind"] for x in lowercase["findings"]})

    def test_multiline_default_owner_construction_is_flagged(self):
        report = self.run_audit("""
            class SampleOwner(
                private val cache: CapabilityCache = CapabilityCache(
                    maxSize = 10
                )
            )
        """)
        self.assertIn("private-owner-construction", {x["kind"] for x in report["findings"]})

    def test_class_body_construction_is_not_a_constructor_default(self):
        plain = self.run_audit("class Plain { val cache = CapabilityCache() }")
        self.assertNotIn("private-owner-construction", {x["kind"] for x in plain["findings"]})
        single = self.run_audit("class Foo(private val cache: Any = CapabilityCache())")
        self.assertIn("private-owner-construction", {x["kind"] for x in single["findings"]})
        multiline = self.run_audit("""
            class Foo(
                private val cache: Any = CapabilityCache(
                    maxSize = 10
                )
            )
        """)
        self.assertIn("private-owner-construction", {x["kind"] for x in multiline["findings"]})

    def test_later_construction_is_not_attributed_to_first_class(self):
        later_function = self.run_audit("""
            class First(val x: String)
            fun make(): Any = CapabilityCache()
        """)
        self.assertNotIn("private-owner-construction", {x["kind"] for x in later_function["findings"]})
        second_body = self.run_audit("""
            class First(val x: String)
            class Second {
                val cache = CapabilityCache()
            }
        """)
        self.assertNotIn("private-owner-construction", {x["kind"] for x in second_body["findings"]})
        attributed = self.run_audit("""
            class First(val x: String)
            class Second(val cache: Any = CapabilityCache())
        """)
        self.assertIn("private-owner-construction", {x["kind"] for x in attributed["findings"]})

    def test_composition_roots_skip_dependency_direction(self):
        body = "import me.foxtails.palustris.ui.profile.Profile\nfun x() = Unit"
        di_report = self.run_audit_files({
            "app/src/main/java/me/foxtails/palustris/di/AppModule.kt":
                source(body, "me.foxtails.palustris.di"),
        })
        self.assertNotIn("dependency-direction", {x["kind"] for x in di_report["findings"]})
        feature_report = self.run_audit(body)
        self.assertIn("dependency-direction", {x["kind"] for x in feature_report["findings"]})

    def test_composition_root_allowlist_exempts_a_feature_file(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            rel = "app/src/main/java/me/foxtails/palustris/ui/feed/Example.kt"
            path = root / rel
            path.parent.mkdir(parents=True)
            path.write_text(
                source("import me.foxtails.palustris.ui.profile.Profile\nfun x() = Unit"),
                encoding="utf-8",
            )
            report = audit.audit(root, allowlists={"compositionRootFiles": [rel]})
            self.assertNotIn("dependency-direction", {x["kind"] for x in report["findings"]})

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

    def test_symbol_level_retention_rule_exempts_only_named_map(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "app/src/main/java/me/foxtails/palustris/ui/feed/Example.kt"
            path.parent.mkdir(parents=True)
            path.write_text(source("val kept = HashMap()\nval dropped = HashMap()"), encoding="utf-8")
            rel = path.relative_to(root).as_posix()
            report = audit.audit(root, {f"{rel}:kept": "documented owner lifetime"})
            maps = {x["detail"] for x in report["findings"] if x["kind"] == "unretained-long-lived-map"}
            self.assertEqual(maps, {"dropped"})

    def test_duplicate_paging_stub_flags_viewmodel_cursor_tracking(self):
        clean = self.run_audit_files({
            "app/src/main/java/me/foxtails/palustris/ui/profile/ProfileViewModel.kt":
                source("fun x() = Unit", "me.foxtails.palustris.ui.profile"),
        })
        self.assertNotIn("duplicate-paging-ownership", {x["kind"] for x in clean["findings"]})
        regressed = self.run_audit_files({
            "app/src/main/java/me/foxtails/palustris/ui/profile/ProfileViewModel.kt":
                source("private val pageJobs = mutableMapOf<String, String>()", "me.foxtails.palustris.ui.profile"),
        })
        violation = next(x for x in regressed["findings"] if x["kind"] == "duplicate-paging-ownership")
        self.assertEqual(violation["symbol"], "pageJobs")
        self.assertTrue(audit.classify(regressed, {})["regressions"])

    def test_execution_authority_stub_flags_non_canonical_construction(self):
        canonical = self.run_audit_files({
            "app/src/main/java/me/foxtails/palustris/ui/session/ConnectedSessionHost.kt":
                source("val authority = PostInteractionExecutionAuthority()", "me.foxtails.palustris.ui.session"),
        })
        self.assertNotIn("post-execution-duplication", {x["kind"] for x in canonical["findings"]})
        regressed = self.run_audit_files({
            "app/src/main/java/me/foxtails/palustris/ui/feed/FeedViewModel.kt":
                source("val authority = PostInteractionExecutionAuthority()", "me.foxtails.palustris.ui.feed"),
        })
        self.assertIn("post-execution-duplication", {x["kind"] for x in regressed["findings"]})
        self.assertTrue(audit.classify(regressed, {})["regressions"])

    def test_duplicate_paging_full_gate_flags_all_tracking_symbols(self):
        clean = self.run_audit_files({
            "app/src/main/java/me/foxtails/palustris/ui/profile/ProfileViewModel.kt":
                source("fun x() = Unit", "me.foxtails.palustris.ui.profile"),
        })
        self.assertNotIn("duplicate-paging-ownership", {x["kind"] for x in clean["findings"]})
        for symbol, body in (
            ("pageJobs", "private val pageJobs = mutableMapOf<String, String>()"),
            ("requestedCursors", "private val requestedCursors = mutableMapOf<String, String>()"),
            ("loadPage", "private fun loadPage(cursor: String?) = Unit"),
            ("publishPageFailure", "private fun publishPageFailure(error: String) = Unit"),
        ):
            regressed = self.run_audit_files({
                "app/src/main/java/me/foxtails/palustris/ui/profile/ProfileViewModel.kt":
                    source(body, "me.foxtails.palustris.ui.profile"),
            })
            violation = next(x for x in regressed["findings"] if x["kind"] == "duplicate-paging-ownership" and x["symbol"] == symbol)
            self.assertEqual(violation["symbol"], symbol)
            self.assertTrue(audit.classify(regressed, {})["regressions"])
        pager = self.run_audit_files({
            "app/src/main/java/me/foxtails/palustris/ui/profile/ProfileTimelinePager.kt":
                source("private val pageJobs = mutableMapOf<String, String>()", "me.foxtails.palustris.ui.profile"),
        })
        self.assertNotIn("duplicate-paging-ownership", {x["kind"] for x in pager["findings"]})

    def test_execution_authority_allows_single_canonical_rejects_duplicates(self):
        canonical = self.run_audit_files({
            "app/src/main/java/me/foxtails/palustris/ui/session/ConnectedSessionHost.kt":
                source("val authority = PostInteractionExecutionAuthority()", "me.foxtails.palustris.ui.session"),
        })
        self.assertNotIn("post-execution-duplication", {x["kind"] for x in canonical["findings"]})
        duplicate_canonical = self.run_audit_files({
            "app/src/main/java/me/foxtails/palustris/ui/session/ConnectedSessionHost.kt":
                source(
                    "val first = PostInteractionExecutionAuthority()\nval second = PostInteractionExecutionAuthority()",
                    "me.foxtails.palustris.ui.session",
                ),
        })
        self.assertIn("post-execution-duplication", {x["kind"] for x in duplicate_canonical["findings"]})
        self.assertTrue(audit.classify(duplicate_canonical, {})["regressions"])
        surfaces = [
            "app/src/main/java/me/foxtails/palustris/ui/feed/FeedViewModel.kt",
            "app/src/main/java/me/foxtails/palustris/ui/profile/ProfileViewModel.kt",
            "app/src/main/java/me/foxtails/palustris/ui/thread/PostThreadViewModel.kt",
            "app/src/main/java/me/foxtails/palustris/ui/saved/SavedPostsViewModel.kt",
            "app/src/main/java/me/foxtails/palustris/ui/search/SearchController.kt",
            "app/src/main/java/me/foxtails/palustris/ui/notifications/NotificationDetailScreen.kt",
            "app/src/main/java/me/foxtails/palustris/ui/posts/SinglePostScreen.kt",
        ]
        for path in surfaces:
            package = "me.foxtails.palustris." + ".".join(Path(path).parts[6:-1])
            regressed = self.run_audit_files({
                path: source("val authority = PostInteractionExecutionAuthority()", package),
            })
            self.assertIn("post-execution-duplication", {x["kind"] for x in regressed["findings"]}, path)
        default = self.run_audit_files({
            "app/src/main/java/me/foxtails/palustris/ui/feed/FeedViewModel.kt":
                source(
                    "class FeedViewModel(val authority: Any = PostInteractionExecutionAuthority())",
                    "me.foxtails.palustris.ui.feed",
                ),
        })
        self.assertIn("post-execution-duplication", {x["kind"] for x in default["findings"]})
        declaration = self.run_audit_files({
            "app/src/main/java/me/foxtails/palustris/ui/posts/PostInteractionExecutionAuthority.kt":
                source("class PostInteractionExecutionAuthority", "me.foxtails.palustris.ui.posts"),
        })
        self.assertNotIn("post-execution-duplication", {x["kind"] for x in declaration["findings"]})

    def test_remembered_state_maps_require_retention(self):
        plain = self.run_audit(
            "import androidx.compose.runtime.mutableStateMapOf\n"
            "import androidx.compose.runtime.remember\n"
            "val revealed = remember { mutableStateMapOf<String, String>() }"
        )
        self.assertEqual(
            {x["detail"] for x in plain["findings"] if x["kind"] == "unretained-long-lived-map"},
            {"revealed"},
        )
        keyed = self.run_audit(
            "import androidx.compose.runtime.mutableStateMapOf\n"
            "import androidx.compose.runtime.remember\n"
            "val keyed = remember(key) { mutableStateMapOf<String, String>() }"
        )
        self.assertEqual(
            {x["detail"] for x in keyed["findings"] if x["kind"] == "unretained-long-lived-map"},
            {"keyed"},
        )
        retained = self.run_audit(
            "import androidx.compose.runtime.mutableStateMapOf\n"
            "import androidx.compose.runtime.remember\n"
            "fun retained() {\n"
            " val cache = remember { mutableStateMapOf<String, String>() }\n"
            " cache.clear()\n"
            "}"
        )
        self.assertNotIn("unretained-long-lived-map", {x["kind"] for x in retained["findings"]})
        self.assertTrue(audit.classify(plain, {})["regressions"])

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

    def test_all_warning_kinds_never_fail_check(self):
        # Each warning kind reports a signal but never fails --check alone.
        bodies = [
            "class SampleOwner(val x: String)",
            "import me.foxtails.palustris.ui.profile.Profile\nfun x() = Unit",
            "\n".join(["val value = 1"] * 701),
        ]
        for body in bodies:
            report = self.run_audit(body)
            result = audit.classify(report, {})
            self.assertFalse(result["regressions"], body[:60])
            self.assertTrue(all(x["classification"] == "warning" for x in result["findings"] if x["kind"] in audit.WARNING_KINDS))
        synthetic = {
            "findings": [
                {"id": "function-size-warning:f:long", "kind": "function-size-warning", "file": "f", "detail": "long", "classification": "warning"},
                {"id": "function-parameter-warning:f:wide", "kind": "function-parameter-warning", "file": "f", "detail": "wide", "classification": "warning"},
                {"id": "function-nesting-warning:f:deep", "kind": "function-nesting-warning", "file": "f", "detail": "deep", "classification": "warning"},
            ],
            "fileMetrics": {},
            "functionMetrics": [],
        }
        result = audit.classify(synthetic, {})
        self.assertFalse(result["regressions"])
        self.assertTrue(all(x["classification"] == "warning" for x in result["findings"]))

    def test_new_owner_class_reports_without_failing(self):
        report = self.run_audit("class SampleOwner(val x: String)")
        owned = [x for x in report["findings"] if x["kind"] == "ownership-class"]
        self.assertTrue(owned)
        result = audit.classify(report, {})
        self.assertFalse(result["regressions"])
        self.assertEqual(result["findings"][0]["classification"], "warning")

    def test_new_private_construction_fails_but_baselined_passes(self):
        report = self.run_audit("class Foo(private val cache: Any = CapabilityCache())")
        violation = next(x for x in report["findings"] if x["kind"] == "private-owner-construction")
        failed = audit.classify(report, {})
        self.assertIn(violation["id"], {x["id"] for x in failed["regressions"]})
        passed = audit.classify(self.run_audit("class Foo(private val cache: Any = CapabilityCache())"), {"existingFindings": [violation["id"]]})
        self.assertFalse(passed["regressions"])
        self.assertEqual(next(x for x in passed["findings"] if x["id"] == violation["id"])["classification"], "existing-baseline")

    def test_new_unretained_map_fails_but_retained_passes(self):
        report = self.run_audit("val cache = HashMap()")
        violation = next(x for x in report["findings"] if x["kind"] == "unretained-long-lived-map")
        self.assertTrue(audit.classify(report, {})["regressions"])
        cleared = self.run_audit("fun retained() {\n val cache = HashMap()\n cache.clear()\n}")
        self.assertNotIn("unretained-long-lived-map", {x["kind"] for x in cleared["findings"]})

    def test_existing_resolved_and_new_classification(self):
        report = self.run_audit("fun x() = Unit", "me.foxtails.palustris.ui", "PostRow.kt")
        violation = next(x for x in report["findings"] if x["kind"] == "package-path-mismatch")
        new_result = audit.classify(report, {})
        self.assertEqual(next(x for x in new_result["findings"] if x["id"] == violation["id"])["classification"], "regression")
        existing_result = audit.classify(self.run_audit("fun x() = Unit", "me.foxtails.palustris.ui", "PostRow.kt"), {"existingFindings": [violation["id"]]})
        self.assertEqual(next(x for x in existing_result["findings"] if x["id"] == violation["id"])["classification"], "existing-baseline")
        self.assertFalse(existing_result["regressions"])
        resolved_result = audit.classify(self.run_audit("fun x() = Unit", "me.foxtails.palustris.ui", "PostRow.kt"), {"resolvedFindings": [violation["id"]]})
        self.assertEqual(next(x for x in resolved_result["findings"] if x["id"] == violation["id"])["classification"], "regression-returned-resolved")
        self.assertTrue(resolved_result["regressions"])

    def test_file_growth_needs_percentage_and_absolute(self):
        # Small file: +49 lines is not material even at +49 percent.
        small = audit.classify(
            {"findings": [], "fileMetrics": {"a.kt": {"lines": 149}}, "functionMetrics": []},
            {"fileMetrics": {"a.kt": {"lines": 100}}},
        )
        self.assertFalse(small["regressions"])
        # Small file: +50 lines and +50 percent is material.
        material = audit.classify(
            {"findings": [], "fileMetrics": {"a.kt": {"lines": 150}}, "functionMetrics": []},
            {"fileMetrics": {"a.kt": {"lines": 100}}},
        )
        self.assertIn("hotspot-growth", {x["kind"] for x in material["regressions"]})
        # Large file: +150 lines is not material below 20 percent.
        percent = audit.classify(
            {"findings": [], "fileMetrics": {"a.kt": {"lines": 1150}}, "functionMetrics": []},
            {"fileMetrics": {"a.kt": {"lines": 1000}}},
        )
        self.assertFalse(percent["regressions"])
        # Large file: exactly +20 percent is not material; above it is.
        exact = audit.classify(
            {"findings": [], "fileMetrics": {"a.kt": {"lines": 1200}}, "functionMetrics": []},
            {"fileMetrics": {"a.kt": {"lines": 1000}}},
        )
        self.assertFalse(exact["regressions"])
        over = audit.classify(
            {"findings": [], "fileMetrics": {"a.kt": {"lines": 1201}}, "functionMetrics": []},
            {"fileMetrics": {"a.kt": {"lines": 1000}}},
        )
        self.assertIn("hotspot-growth", {x["kind"] for x in over["regressions"]})
        # Unknown files never count as hotspot growth.
        unknown = audit.classify(
            {"findings": [], "fileMetrics": {"b.kt": {"lines": 5000}}, "functionMetrics": []},
            {"fileMetrics": {"a.kt": {"lines": 100}}},
        )
        self.assertFalse(unknown["regressions"])

    def test_function_growth_is_deterministic_and_material(self):
        baseline = {"functionMetrics": [{"file": "a.kt", "name": "f", "lines": 70, "params": 6, "nesting": 4}]}
        tiny = {"findings": [], "fileMetrics": {}, "functionMetrics": [{"file": "a.kt", "name": "f", "lines": 71, "params": 6, "nesting": 4}]}
        self.assertFalse(audit.classify(dict(tiny), baseline)["regressions"])
        small_params = {"findings": [], "fileMetrics": {}, "functionMetrics": [{"file": "a.kt", "name": "f", "lines": 70, "params": 7, "nesting": 4}]}
        self.assertFalse(audit.classify(dict(small_params), baseline)["regressions"])
        material_nesting = {"findings": [], "fileMetrics": {}, "functionMetrics": [{"file": "a.kt", "name": "f", "lines": 70, "params": 6, "nesting": 5}]}
        grown = audit.classify(dict(material_nesting), baseline)
        self.assertIn("function-complexity-growth", {x["kind"] for x in grown["regressions"]})
        repeated = audit.classify(dict(material_nesting), baseline)
        self.assertEqual(json.dumps(grown, sort_keys=True), json.dumps(repeated, sort_keys=True))
        unknown = {"findings": [], "fileMetrics": {}, "functionMetrics": [{"file": "a.kt", "name": "other", "lines": 200, "params": 20, "nesting": 20}]}
        self.assertFalse(audit.classify(unknown, baseline)["regressions"])

    def test_dependency_violation_reported_but_composition_root_exempt(self):
        body = "import me.foxtails.palustris.ui.profile.Profile\nfun x() = Unit"
        feature = self.run_audit(body)
        self.assertIn("dependency-direction", {x["kind"] for x in feature["findings"]})
        # Dependency signals warn without failing the check.
        self.assertFalse(audit.classify(feature, {})["regressions"])
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            rel = "app/src/main/java/me/foxtails/palustris/ui/feed/Example.kt"
            path = root / rel
            path.parent.mkdir(parents=True)
            path.write_text(source(body), encoding="utf-8")
            exempt = audit.audit(root, allowlists={"compositionRootFiles": [rel]})
            self.assertNotIn("dependency-direction", {x["kind"] for x in exempt["findings"]})

    def test_record_baseline_is_deterministic(self):
        report = self.run_audit("fun f(a: Int) { println(a) }")
        previous = {
            "schemaVersion": 2,
            "thresholds": dict(audit.DEFAULT_THRESHOLDS),
            "existingFindings": ["b", "a"],
            "enforcedFindings": [],
            "resolvedFindings": [],
            "retentionRules": {"z.kt": "reason"},
            "allowlists": {"rootUiGlobalFiles": ["B", "A"]},
        }
        one, two = audit.build_baseline(report, previous), audit.build_baseline(report, previous)
        self.assertEqual(json.dumps(one, sort_keys=True), json.dumps(two, sort_keys=True))
        self.assertEqual(one["parserVersion"], audit.PARSER_VERSION)
        self.assertEqual(one["existingFindings"], ["a", "b"])
        self.assertEqual(one["allowlists"]["rootUiGlobalFiles"], ["A", "B"])
        self.assertEqual(list(one["fileMetrics"]), sorted(one["fileMetrics"]))
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "baseline.json"
            audit.write_baseline(path, one)
            self.assertEqual(json.loads(path.read_text(encoding="utf-8")), one)


    def test_record_baseline_cli_refreshes_versions_and_preserves_manual_sections(self):
        # Record refreshes old versions but keeps manual review sections.
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "root"
            rel = "app/src/main/java/me/foxtails/palustris/ui/feed/Example.kt"
            path = root / rel
            path.parent.mkdir(parents=True)
            path.write_text(source("fun f(a: Int) { println(a) }"), encoding="utf-8")
            baseline_path = Path(temp) / "previous.json"
            baseline_path.write_text(json.dumps({
                "schemaVersion": 1,
                "parserVersion": "1",
                "thresholds": dict(audit.DEFAULT_THRESHOLDS),
                "existingFindings": ["keep-existing"],
                "enforcedFindings": ["keep-enforced"],
                "resolvedFindings": ["keep-resolved"],
                "retentionRules": {"keep.kt": "keep reason"},
                "allowlists": {"rootUiGlobalFiles": ["KeepGlobal"]},
                "fileMetrics": {},
                "functionMetrics": [],
            }), encoding="utf-8")
            out_path = Path(temp) / "recorded.json"
            result = audit.main([str(root), "--baseline", str(baseline_path),
                                 "--record-baseline", str(out_path)])
            self.assertEqual(result, 0)
            recorded = json.loads(out_path.read_text(encoding="utf-8"))
            self.assertEqual(recorded["schemaVersion"], 2)
            self.assertEqual(recorded["schemaVersion"], audit.SCHEMA_VERSION)
            self.assertEqual(recorded["parserVersion"], audit.PARSER_VERSION)
            self.assertEqual(recorded["existingFindings"], ["keep-existing"])
            self.assertEqual(recorded["enforcedFindings"], ["keep-enforced"])
            self.assertEqual(recorded["resolvedFindings"], ["keep-resolved"])
            self.assertEqual(recorded["retentionRules"], {"keep.kt": "keep reason"})
            self.assertEqual(recorded["allowlists"], {"rootUiGlobalFiles": ["KeepGlobal"]})
            self.assertEqual(recorded["thresholds"], dict(audit.DEFAULT_THRESHOLDS))
            self.assertIn(rel, recorded["fileMetrics"])
            raw = out_path.read_text(encoding="utf-8")
            self.assertEqual(raw, json.dumps(recorded, indent=2, sort_keys=True) + "\n")

    def test_check_without_record_flag_unchanged(self):
        # Check without record keeps pass and fail behavior.
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "root"
            rel = "app/src/main/java/me/foxtails/palustris/ui/feed/Example.kt"
            path = root / rel
            path.parent.mkdir(parents=True)
            path.write_text(source("val cache = HashMap()"), encoding="utf-8")
            self.assertEqual(audit.main([str(root), "--check"]), 1)
            baseline_path = Path(temp) / "baseline.json"
            report = audit.audit(root)
            violation = next(x["id"] for x in report["findings"] if x["kind"] == "unretained-long-lived-map")
            baseline_path.write_text(json.dumps({
                "schemaVersion": 2,
                "parserVersion": audit.PARSER_VERSION,
                "thresholds": dict(audit.DEFAULT_THRESHOLDS),
                "existingFindings": [violation],
                "enforcedFindings": [],
                "resolvedFindings": [],
                "retentionRules": {},
                "allowlists": {},
                "fileMetrics": {},
                "functionMetrics": [],
            }), encoding="utf-8")
            self.assertEqual(audit.main([str(root), "--baseline", str(baseline_path), "--check"]), 0)
            record_path = Path(temp) / "must-not-exist.json"
            self.assertFalse(record_path.exists())
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "root"
            path = root / "app/src/main/java/me/foxtails/palustris/ui/feed/Warning.kt"
            path.parent.mkdir(parents=True)
            path.write_text(source("class SampleOwner(val x: String)"), encoding="utf-8")
            self.assertEqual(audit.main([str(root), "--check"]), 0)


    def test_distant_top_level_use_does_not_exempt_map(self):
        report = self.run_audit("""
            val distant = HashMap()
            val a = 1
            val b = 2
            val c = 3
            val d = 4
            distant.clear()
        """)
        maps = {x["detail"] for x in report["findings"] if x["kind"] == "unretained-long-lived-map"}
        self.assertEqual(maps, {"distant"})

    def test_blank_line_stops_top_level_scope(self):
        report = self.run_audit("val solo = HashMap()\n\nsolo.clear()")
        maps = {x["detail"] for x in report["findings"] if x["kind"] == "unretained-long-lived-map"}
        self.assertEqual(maps, {"solo"})

    def test_map_after_function_body_uses_top_level_scope(self):
        report = self.run_audit("""
            fun helper() {
                println(1)
            }
            val after = HashMap()
            after.clear()
        """)
        self.assertNotIn("unretained-long-lived-map", {x["kind"] for x in report["findings"]})

    def test_consecutive_maps_stay_isolated(self):
        # Consecutively declared maps keep single-line scopes, so the
        # trailing use exempts neither map. Both stay flagged.
        report = self.run_audit("""
            val first = HashMap()
            val second = HashMap()
            second.clear()
        """)
        maps = {x["detail"] for x in report["findings"] if x["kind"] == "unretained-long-lived-map"}
        self.assertEqual(maps, {"first", "second"})

    def test_check_with_record_baseline_exits_zero(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "root"
            path = root / "app/src/main/java/me/foxtails/palustris/ui/feed/Example.kt"
            path.parent.mkdir(parents=True)
            path.write_text(source("val cache = HashMap()"), encoding="utf-8")
            out_path = Path(temp) / "recorded.json"
            self.assertEqual(audit.main([str(root), "--check", "--record-baseline", str(out_path)]), 0)
            self.assertTrue(out_path.exists())

    def test_all_owner_suffixes_warn_without_failing(self):
        # Slice 19 new-owner rule: each Owner-like suffix reports a review
        # warning but never fails --check alone. The six documentation
        # items stay a human review duty, not an automated gate.
        for suffix in ("Owner", "Authority", "Manager", "Controller", "Coordinator"):
            report = self.run_audit(f"class Sample{suffix}(val x: String)")
            owned = [x for x in report["findings"] if x["kind"] == "ownership-class"]
            self.assertTrue(owned, suffix)
            result = audit.classify(report, {})
            self.assertFalse(result["regressions"], suffix)
            self.assertTrue(all(x["classification"] == "warning" for x in owned), suffix)

    def test_slice19_size_policy_allows_large_cohesive_file(self):
        # Slice 19 size policy: a large cohesive single-domain file warns
        # but never fails. Only material hotspot growth fails the check.
        self.assertTrue(
            {"ownership-class", "dependency-direction", "file-size-warning",
             "function-size-warning", "function-parameter-warning",
             "function-nesting-warning"} <= set(audit.WARNING_KINDS)
        )
        report = self.run_audit("\n".join(["val value = 1"] * 800))
        self.assertEqual({x["kind"] for x in report["findings"]}, {"file-size-warning"})
        self.assertFalse(audit.classify(report, {})["regressions"])
        rel = next(iter(report["fileMetrics"]))
        lines = report["fileMetrics"][rel]["lines"]
        self.assertGreater(lines, 700)
        grown = audit.classify(
            {"findings": [], "fileMetrics": {rel: {"lines": lines}}, "functionMetrics": []},
            {"fileMetrics": {rel: {"lines": 100}}},
        )
        self.assertIn("hotspot-growth", {x["kind"] for x in grown["regressions"]})
        stable = audit.classify(
            {"findings": [], "fileMetrics": {rel: {"lines": lines}}, "functionMetrics": []},
            {"fileMetrics": {rel: {"lines": lines - 10}}},
        )
        self.assertFalse(stable["regressions"])


if __name__ == "__main__":
    unittest.main()
