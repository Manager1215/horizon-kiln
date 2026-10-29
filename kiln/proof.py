"""Kiln in a running modded game, with Kiln's runtime and two Java mods in <game folder>/kiln/: the spike's mod and
the speedrun timer, the same jar Gradle builds against Minecraft 26.3. Entering a new world starts Java and both
mods; the timer starts by itself; advancements granted by command, trips between dimensions and a pause make its
splits, the dragon's advancement finishes the run, and the finish is a personal best (screenshots and chat).

    sh tools/build-java.sh
    cd .deps/horizon-mods && python3 native/tools/native.py proof --resources PATH kiln
"""
import json
import re
import shutil
import time
from pathlib import Path

from game import Game, difference

BUILT = Path(__file__).resolve().parent.parent / "target" / "kiln"
# The timer says each split in chat (`chat_splits`), so the proof can read them.
SETTINGS = "corner=top_right\nchat_splits=true\n"
TOP_RIGHT = (0.75, 0.0, 1.0, 0.45)


class KilnGame(Game):
    """Game, with Kiln's folder copied into the fresh game folder before the game starts."""

    def run(self, *args):
        if args[:1] == ("launch",) and not (self.home / "kiln").exists():
            shutil.copytree(BUILT, self.home / "kiln")
            settings = self.home / "kiln" / "config" / "kiln_timer" / "settings.properties"
            settings.parent.mkdir(parents=True)
            settings.write_text(SETTINGS)
        return super().run(*args)


def chat(g):
    """The timer's and Kiln's chat lines, oldest first."""
    text = json.dumps(g.run("messages", "60"), ensure_ascii=False)
    return re.findall(r"(\[Kiln Timer\][^\"\\]*|Kiln: [^\"\\]*)", text)


def step(g, *commands, wait=1.5):
    for c in commands:
        g.run("command", c)
    time.sleep(wait)


def run(ctl, resources, out):
    assert (BUILT / "kiln-runtime.jar").is_file(), "run sh tools/build-java.sh first"
    with KilnGame(ctl, resources, out) as g:
        g.run("wait-ready", "60")
        deadline = time.time() + 30
        log = ""
        while time.time() < deadline and "Kiln: started Kiln Speedrun Timer" not in log:
            time.sleep(1)
            log = json.dumps(g.run("logs", "--lines", "400"))
        time.sleep(2)
        g.shot("1-running")
        step(g, "advancement grant @s only minecraft:story/enter_the_nether")
        step(g, "execute in minecraft:the_nether run tp @s 0 100 0", wait=3)
        step(g, "advancement grant @s only minecraft:nether/find_bastion",
             "advancement grant @s only minecraft:nether/find_fortress")
        step(g, "execute in minecraft:overworld run tp @s 0 120 0", wait=3)
        step(g, "advancement grant @s only minecraft:story/follow_ender_eye")
        g.run("pause")
        time.sleep(2)
        g.run("resume")
        g.play()
        step(g, "execute in minecraft:the_end run tp @s 0 100 0", wait=3)
        g.shot("2-splits")
        step(g, "advancement grant @s only minecraft:end/kill_dragon", wait=2)
        finished = g.shot("3-finished")
        # The timer's hide key (U), heard by Kiln and handed to the mod's KeyMapping: the panel goes.
        g.run("key", "u", "tap")
        time.sleep(1.5)
        hidden = g.shot("4-hidden")
        lines = chat(g)
        records = g.home / "kiln" / "config" / "kiln_timer" / "records.txt"
        final = next((l for l in lines if "Final IGT" in l), "")
        times = re.findall(r"(\d+):(\d\d)\.(\d\d\d)", final)
        seconds = [int(m) * 60 + int(s) + int(ms) / 1000 for m, s, ms in times]
        results = {
            "both_mods_started": "Kiln: 2 of 2 Java mods running" in " ".join(lines),
            "chat": lines,
            "splits_said": [s for s in ("Nether", "Bastion", "Fortress", "Blind", "Stronghold", "End")
                            if any(l.startswith(f"[Kiln Timer] {s} ") for l in lines)],
            "final": final,
            "rta_minus_igt": round(seconds[1] - seconds[0], 3) if len(seconds) == 2 else None,
            "records_saved": records.is_file() and "pb.FINISH=" in records.read_text(),
            "panel_hidden_by_key": round(difference(finished, hidden, TOP_RIGHT), 1),
            # A first run has no personal best to compare to: no deltas.
            "deltas_on_first_run": [l for l in lines if l.startswith("[Kiln Timer]") and "(" in l],
        }
        print(json.dumps(results, indent=2, ensure_ascii=False))
        assert results["both_mods_started"], results
        assert results["splits_said"] == ["Nether", "Bastion", "Fortress", "Blind", "Stronghold", "End"], results
        assert "New personal best" in final, results
        assert results["rta_minus_igt"] is not None and results["rta_minus_igt"] >= 1.5, results
        assert results["records_saved"], results
        assert not results["deltas_on_first_run"], results
        assert results["panel_hidden_by_key"] > 4, results
        print(json.dumps({"proof": "kiln", "ok": True}, indent=2))
