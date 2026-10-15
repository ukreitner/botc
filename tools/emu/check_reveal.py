#!/usr/bin/env python3
"""Gesture regression: open a player's covered handout card, then run this.

    python3 tools/emu/check_reveal.py emulator-5554

Checks a short tap stays private, a held card stays above the finger, and
release hides it. Saves XML and screenshots to the ignored emulator output.
All gesture coordinates come from the live UI tree.
"""
import pathlib
import subprocess
import sys
import time

import ui


def main(serial):
    out = pathlib.Path(ui.EMU_OUT) / "reveal-regression" / serial
    out.mkdir(parents=True, exist_ok=True)

    def snapshot(name):
        xml = ui.dump_xml(serial)
        (out / f"{name}.xml").write_text(xml)
        return ui.parse_nodes(xml)

    def target(nodes):
        return next(n for n in nodes if n.desc == "Hold to reveal card")

    covered = snapshot("covered")
    hold = target(covered)
    x, y = hold.centre
    ui.adb(serial, "shell", "input", "tap", str(x), str(y))
    tapped = snapshot("short-tap")
    assert any(n.text == "HOLD to reveal" for n in tapped), "A short tap must stay covered"
    assert not any(n.text.startswith("YOU ARE") for n in tapped), "Short tap leaked a card"

    gesture = subprocess.Popen([
        ui.ADB, "-s", serial, "shell", "input", "swipe",
        str(x), str(y), str(x), str(y), "10000",
    ])
    try:
        time.sleep(1.2)
        held = snapshot("held")
        (out / "held.png").write_bytes(ui.adb(serial, "exec-out", "screencap", "-p", binary=True))
        assert any(n.text.startswith("YOU ARE") for n in held), "Sustained hold did not reveal"
        assert target(held).bounds == hold.bounds, "Hold target moved while the finger was down"
        card_text = [n for n in held if n.text and not n.text.startswith("seat ")
                     and n.text not in ("Release to hide", "I'll do this later")]
        assert card_text and all(n.bounds[3] <= hold.bounds[1] for n in card_text), \
            "The character card overlaps the held button"
    finally:
        gesture.wait(timeout=15)

    released = snapshot("released")
    assert not any(n.text.startswith("YOU ARE") for n in released), "Release did not hide the card"
    assert any(n.text in ("HAND OUT TOKENS", "HOLD to reveal") for n in released), \
        "Release must return to the roster or the next covered card"
    print(f"PASS: tap privacy, hold reveal, fixed target, card clearance, release privacy. Evidence: {out}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
