#!/usr/bin/env python3
"""Scroll-and-tap: pick.py <serial> <regex> [max_scrolls] — swipes up until the
label is on screen, then taps it. Exits 1 if never found."""
import re
import subprocess
import sys
import time

HERE = "/Users/ukreitner/botc/tools/emu"


def run(args):
    return subprocess.run(args, capture_output=True, text=True, cwd=HERE)


def main():
    serial, pattern = sys.argv[1], sys.argv[2]
    limit = int(sys.argv[3]) if len(sys.argv) > 3 else 12
    for i in range(limit):
        r = run(["./ui.py", serial, "tap", pattern])
        if r.returncode == 0:
            print(r.stdout.strip())
            return
        run(["./ui.py", serial, "swipe", "up", "600"])
        time.sleep(0.5)
    print("NOT FOUND: %s" % pattern)
    sys.exit(1)


main()
