"""Exercise dependency compliance without network or Gradle."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class DependencyGateTest(unittest.TestCase):
    def run_gate(self, report, exit_code=0, allowlist=None):
        with tempfile.TemporaryDirectory(prefix="thwart-dependency-test-") as directory:
            root = Path(directory)
            ci = root / "tools" / "ci"
            ci.mkdir(parents=True)
            shutil.copyfile(Path(__file__).with_name("check-dependencies.sh"), ci / "check-dependencies.sh")
            (ci / "proprietary-coordinates.txt").write_text("com.google.firebase\n", encoding="utf-8")
            if allowlist:
                (ci / "proprietary-allowed.txt").write_text(allowlist + "\n", encoding="utf-8")
            (root / "report.txt").write_text(report, encoding="utf-8")
            wrapper = root / "gradlew"
            wrapper.write_text(f"#!/usr/bin/env bash\ncat report.txt\nexit {exit_code}\n", encoding="utf-8", newline="\n")
            wrapper.chmod(0o755)
            bash = os.environ.get("BASH_EXECUTABLE", "bash")
            return subprocess.run([bash, "tools/ci/check-dependencies.sh"], cwd=root,
                                  capture_output=True, text=True, check=False).returncode

    def test_partial_output_does_not_hide_gradle_failure(self):
        self.assertNotEqual(0, self.run_gate("+--- example:library:1.0\n", 1))

    def test_unresolved_dependency_is_rejected_even_with_zero_exit(self):
        self.assertNotEqual(0, self.run_gate("+--- example:library:1.0 FAILED\n"))

    def test_unresolved_dependency_at_start_of_large_report_is_rejected(self):
        report = "+--- example:broken:1.0 FAILED\n" + "+--- example:library:1.0\n" * 10000
        self.assertNotEqual(0, self.run_gate(report))

    def test_empty_report_is_rejected(self):
        self.assertNotEqual(0, self.run_gate(""))

    def test_free_dependency_passes(self):
        self.assertEqual(0, self.run_gate("+--- org.jetbrains.kotlin:kotlin-stdlib:2.4.10\n"))

    def test_blocked_dependency_fails(self):
        self.assertNotEqual(0, self.run_gate("+--- com.google.firebase:firebase-analytics:1.0\n"))

    def test_exact_allowlist_is_honoured(self):
        self.assertEqual(0, self.run_gate("+--- com.google.firebase:firebase-analytics:1.0\n",
                                       allowlist="com.google.firebase:firebase-analytics"))


if __name__ == "__main__":
    unittest.main()
