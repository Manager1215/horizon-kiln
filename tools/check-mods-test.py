"""Tests of tools/check-mods.py without the game or a download: the jars a try hands the game.

    python3 -I tools/check-mods-test.py
"""
import importlib.util
import types
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("check_mods", HERE / "check-mods.py")
check_mods = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check_mods)


def jar(name):
    return {"url": f"https://cdn.example/{name}.jar", "sha256": name * 4}


def fake_fetch(j, cache):
    return Path("/cache") / (j["url"].rsplit("/", 1)[-1])


class Requires(unittest.TestCase):
    def setUp(self):
        self.real_fetch = check_mods.fetch
        check_mods.fetch = fake_fetch
        self.libraries = {
            "cloth-config": {"id": "cloth-config", "library": True, "jar": jar("cloth"), "proof": {}},
            "advancedchatcore": {"id": "advancedchatcore", "library": True, "jar": jar("acc"),
                                 "proof": {"requires": ["malilib"]}},
            "malilib": {"id": "malilib", "library": True, "jar": jar("malilib"), "proof": {}},
        }
        self.args = types.SimpleNamespace(libraries=self.libraries, cache=Path("/cache"))

    def tearDown(self):
        check_mods.fetch = self.real_fetch

    def env(self, proof):
        entry = {"id": "m", "jar": jar("m"), "proof": proof}
        return check_mods.proof_env("m", entry, Path("/cache/m.jar"), self.args).get("KILN_PROOF_MODS")

    def test_no_requires_no_with_jar_leaves_the_mods_alone(self):
        self.assertIsNone(self.env({}))

    def test_with_jar_alone_is_the_mod(self):
        self.assertEqual(self.env({"with_jar": True}), "/cache/m.jar")

    def test_requires_by_id_adds_the_library_after_the_mod(self):
        self.assertEqual(self.env({"requires": ["cloth-config"]}).split(":"), ["/cache/m.jar", "/cache/cloth.jar"])

    def test_requires_follows_the_librarys_own_requires_once_each(self):
        got = self.env({"with_jar": True, "requires": ["advancedchatcore", "malilib", {"url": "https://x/acc.jar",
                                                                                       "sha256": "acc" * 4}]})
        self.assertEqual(got.split(":"), ["/cache/m.jar", "/cache/malilib.jar", "/cache/acc.jar"])

    def test_requires_a_jar_and_with_keep_their_order(self):
        got = self.env({"with_jar": True, "with": [jar("addon")], "requires": [jar("lib")]})
        self.assertEqual(got.split(":"), ["/cache/m.jar", "/cache/addon.jar", "/cache/lib.jar"])

    def test_an_unknown_library_is_an_error(self):
        with self.assertRaisesRegex(RuntimeError, "requires nope"):
            self.env({"requires": ["nope"]})

    def test_a_library_has_no_try_of_its_own(self):
        self.libraries["cloth-config"]["proof"] = {"with_jar": True, "try": {"steps": []}}
        self.libraries["creativefly"] = {"id": "creativefly", "proof": {"requires": ["cloth-config"]}}
        result = check_mods.run_proof("cloth-config", self.libraries["cloth-config"], Path("/c.jar"), self.args, None)
        self.assertIs(result["passed"], None)
        self.assertIn("creativefly", result["error"])

    def test_the_catalogue_names_only_libraries_it_has(self):
        libraries = check_mods.library_entries()
        for e in libraries.values():
            for item in (e.get("proof") or {}).get("requires", []):
                if isinstance(item, str):
                    self.assertIn(item, libraries, f"{e['id']} requires {item}")
                else:
                    self.assertTrue(item.get("sha256"), f"{e['id']}: a required jar no SHA-256 pins")


if __name__ == "__main__":
    unittest.main()
