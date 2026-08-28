#!/usr/bin/env python3
"""scenario.py — run a scripted sequence of ui.py steps against one emulator.

    ./scenario.py <serial> scenarios/new_game.py
    ./scenario.py <serial> new_game            # 'scenarios/' and '.py' are implied

A scenario file is plain Python defining STEPS, a list of (command, arg) pairs
where `command` is any ui.py command:

    STEPS = [
        ("tap",        "New game"),
        ("wait",       "TABLE"),
        ("tap",        "Trouble Brewing"),
        ("screenshot", None),
        ("audit",      None),
        ("swipe",      "up"),
        ("type",       "Alice"),
        ("sleep",      1.5),            # extra: pause, seconds
        ("back",       None),
    ]

`arg` is a string, None, or a list/tuple for multi-argument commands
(e.g. ("wait", ["TABLE", "30"]) or ("tapxy", [540, 1245])).

Every step is screenshotted afterwards into
$EMU_OUT/<scenario>/NN-<step>.png. On the first failure the run stops, prints
the current tree, and exits 1 — the screenshot for the failed step is still
written, so you always have a picture of what went wrong.
"""

import os
import runpy
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))


def load_steps(spec):
    path = spec
    if not os.path.exists(path):
        cand = os.path.join(HERE, "scenarios", spec if spec.endswith(".py") else spec + ".py")
        if os.path.exists(cand):
            path = cand
        else:
            ui.fail("scenario not found: %s (looked for %s)" % (spec, cand), 2)
    ns = runpy.run_path(path)
    steps = ns.get("STEPS")
    if not isinstance(steps, (list, tuple)):
        ui.fail("%s does not define a STEPS list" % path, 2)
    return path, list(steps)


def slug(command, arg):
    base = command if arg in (None, "") else "%s-%s" % (command, arg)
    keep = "".join(c if c.isalnum() or c in "-_" else "-" for c in str(base))
    while "--" in keep:
        keep = keep.replace("--", "-")
    return keep.strip("-").lower()[:48] or command


def as_args(arg):
    if arg is None:
        return []
    if isinstance(arg, (list, tuple)):
        return [str(a) for a in arg]
    return [str(arg)]


# `audit` exits non-zero when it finds something. Inside a scenario that is a
# finding to record, not a reason to abandon the playthrough.
NONFATAL = {"audit"}


def main(argv):
    if len(argv) < 3:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    serial, spec = argv[1], argv[2]
    path, steps = load_steps(spec)
    name = os.path.splitext(os.path.basename(path))[0]
    # Namespace by serial so a fleet running the same scenario on several
    # emulators does not overwrite each other's screenshots.
    outdir = os.path.join(ui.EMU_OUT, name, serial)
    os.makedirs(outdir, exist_ok=True)

    print("scenario %s -> %s (%d steps on %s)" % (name, outdir, len(steps), serial))
    findings = []

    for i, step in enumerate(steps, 1):
        if not isinstance(step, (list, tuple)) or not step:
            ui.fail("step %d is not a (command, arg) pair: %r" % (i, step), 2)
        command = step[0]
        arg = step[1] if len(step) > 1 else None
        label = "%02d-%s" % (i, slug(command, arg))
        print("\n--- step %d/%d: %s %s" % (i, len(steps), command, "" if arg is None else repr(arg)))

        failed = None
        if command == "sleep":
            import time
            time.sleep(float(arg))
        elif command not in ui.COMMANDS:
            ui.fail("step %d: unknown command %r" % (i, command), 2)
        else:
            try:
                ui.COMMANDS[command](serial, as_args(arg))
            except SystemExit as e:
                if e.code:
                    failed = e.code
            except Exception as e:  # keep the screenshot even on a crash
                failed = "%s: %s" % (type(e).__name__, e)

        shot = os.path.join(outdir, "%s.png" % label)
        try:
            ui.cmd_screenshot(serial, [shot])
        except SystemExit:
            print("(screenshot failed)", file=sys.stderr)

        if failed is not None and command in NONFATAL:
            print("(finding recorded, continuing)")
            findings.append((i, command, arg, shot))
            failed = None

        if failed is not None:
            print("\nSCENARIO FAILED at step %d (%s %r): %s"
                  % (i, command, arg, failed), file=sys.stderr)
            print("screenshot: %s" % shot, file=sys.stderr)
            print("\ncurrent tree:", file=sys.stderr)
            try:
                ui.cmd_dump(serial, [])
            except SystemExit:
                pass
            sys.exit(1)

    print("\nscenario %s: all %d steps passed -> %s" % (name, len(steps), outdir))
    if findings:
        print("%d audit finding(s) to triage:" % len(findings))
        for i, command, arg, shot in findings:
            print("  step %d (%s %r) -> %s" % (i, command, arg, shot))


if __name__ == "__main__":
    main(sys.argv)
