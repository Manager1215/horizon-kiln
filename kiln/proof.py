"""Kiln in a running modded game: with Kiln's runtime and the spike's Java mod in <game folder>/kiln/, entering a
world starts Java, the mod starts, its line shows in Kiln's box (screenshot) and its log lines reach the game's log.

    sh tools/build-java.sh
    cd .deps/horizon-mods && python3 native/tools/native.py proof --resources PATH kiln
"""
import json
import shutil
import time
from pathlib import Path

from game import Game

BUILT = Path(__file__).resolve().parent.parent / "target" / "kiln"


class KilnGame(Game):
    """Game, with Kiln's folder copied into the fresh game folder before the game starts."""

    def run(self, *args):
        if args[:1] == ("launch",) and not (self.home / "kiln").exists():
            shutil.copytree(BUILT, self.home / "kiln")
        return super().run(*args)


def run(ctl, resources, out):
    assert (BUILT / "kiln-runtime.jar").is_file(), "run sh tools/build-java.sh first"
    with KilnGame(ctl, resources, out) as g:
        g.run("wait-ready", "60")
        deadline = time.time() + 30
        log = ""
        while time.time() < deadline and "Kiln: started Kiln Hello" not in log:
            time.sleep(1)
            log = json.dumps(g.run("logs", "--lines", "400"))
        time.sleep(2)
        shot = g.shot("1-kiln-hud")
        messages = g.run("messages", "40")
        results = {
            "java_ready": "Kiln: Java " in log and " ready in " in log,
            "mod_started": "Kiln: started Kiln Hello" in log,
            "mod_logged": "Hello mod: started on Java" in log,
            "chat": [line for line in json.dumps(messages).split('"') if line.startswith("Kiln:")],
            "screenshot": str(shot),
        }
        print(json.dumps(results, indent=2))
        assert results["java_ready"] and results["mod_started"] and results["mod_logged"], results
        print(json.dumps({"proof": "kiln", "ok": True}, indent=2))
