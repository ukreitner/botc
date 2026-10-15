#!/usr/bin/env python3
"""Seed one synthetic complex-night case on emulator-5556, never physical hardware.

Only setup is synthetic. Night choices and confirmations must be driven by UI.
"""
import argparse
import json
import pathlib
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("case", choices=["gambler", "engineer", "alhadikhia", "courtier", "imp", "general"])
parser.add_argument("--focused", action="store_true", help="Mark unrelated ability rows done so the target opens directly")
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parent
saved = json.loads((root / "fixtures" / "complex-night.json").read_text())
game = saved["game"]
game["id"] = "qa-complex-" + args.case
game["players"][0]["characterId"] = args.case
if args.case == "alhadikhia":
    game["players"][2]["alive"] = False
    game["players"][6]["characterId"] = "chef"
if args.case == "imp":
    game["players"][6]["characterId"] = "chef"
game["nightStepsDone"] = ["DUSK"]
if args.focused:
    game["nightStepsDone"] += ["poisoner#5", "imp#7", "empath#3"]
adb = "/opt/homebrew/share/android-commandlinetools/platform-tools/adb"
package = "com.clocktower.grimoire"

def run(*cmd, **kwargs):
    return subprocess.run([adb, "-s", "emulator-5556", *cmd], check=True, **kwargs)

run("shell", "am", "force-stop", package)
run("shell", "run-as", package, "mkdir", "-p", "files/datastore")
run("exec-in", "run-as", package, "tee", "files/datastore/grimoire.json", input=json.dumps(saved).encode(), stdout=subprocess.DEVNULL)
run("shell", "am", "start", "-n", package + "/.MainActivity")
print("Seeded synthetic " + args.case + " on emulator-5556")
