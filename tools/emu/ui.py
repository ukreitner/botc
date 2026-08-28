#!/usr/bin/env python3
"""ui.py — drive a Clocktower Grimoire emulator from the shell.

    ui.py <serial> dump                  compact semantics tree (raw XML written beside it)
    ui.py <serial> find <regex>          matching nodes + centre coords
    ui.py <serial> tap <regex>           tap centre of first match (refuses off-screen / inset taps)
    ui.py <serial> tapxy <x> <y>
    ui.py <serial> hold <regex> [ms]     press-and-hold (default 800 ms)
    ui.py <serial> swipe up|down|left|right [amount]
    ui.py <serial> type <text...>
    ui.py <serial> back
    ui.py <serial> screenshot [file.png]
    ui.py <serial> wait <regex> [timeout] poll until a node matches (default 15 s)
    ui.py <serial> insets                status bar / nav bar / gesture / cutout insets
    ui.py <serial> audit                 clickable nodes outside the safe area or overlapping

Matching is a case-insensitive regex tested against each node's text and
content-desc. Exit status is 0 on success, 1 on failure (no match, OFFSCREEN,
timeout), 2 on usage error.

Env: ANDROID_SDK (default /opt/homebrew/share/android-commandlinetools)
     EMU_OUT     (default <repo>/tools/emu/out) — screenshots and raw dumps land here
"""

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ANDROID_SDK = os.environ.get("ANDROID_SDK", "/opt/homebrew/share/android-commandlinetools")
EMU_OUT = os.environ.get("EMU_OUT", os.path.join(REPO_ROOT, "tools", "emu", "out"))
DEVICE_DUMP = "/sdcard/window_dump.xml"

_adb_candidates = [os.path.join(ANDROID_SDK, "platform-tools", "adb"), "/opt/homebrew/bin/adb", "adb"]
ADB = next((p for p in _adb_candidates if os.path.isabs(p) and os.access(p, os.X_OK)), "adb")


def fail(msg, code=1):
    print("ui.py: " + msg, file=sys.stderr)
    sys.exit(code)


# --------------------------------------------------------------------- adb

def adb(serial, *args, binary=False, timeout=60, check=True):
    cmd = [ADB, "-s", serial] + list(args)
    try:
        p = subprocess.run(cmd, capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        fail("adb timed out: %s" % " ".join(args))
    if check and p.returncode != 0:
        fail("adb failed (%s): %s" % (" ".join(args), p.stderr.decode("utf-8", "replace").strip()))
    return p.stdout if binary else p.stdout.decode("utf-8", "replace")


def shell(serial, cmdline, **kw):
    return adb(serial, "shell", cmdline, **kw)


# ------------------------------------------------------------------- nodes

BOUNDS_RE = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


class Node:
    __slots__ = ("i", "depth", "text", "desc", "cls", "rid", "bounds", "attrs")

    def __init__(self, i, depth, el):
        a = el.attrib
        self.i = i
        self.depth = depth
        self.text = a.get("text", "")
        self.desc = a.get("content-desc", "")
        self.cls = a.get("class", "").rsplit(".", 1)[-1]
        self.rid = a.get("resource-id", "")
        self.attrs = a
        m = BOUNDS_RE.match(a.get("bounds", ""))
        self.bounds = tuple(int(g) for g in m.groups()) if m else (0, 0, 0, 0)

    def flag(self, name):
        return self.attrs.get(name) == "true"

    @property
    def label(self):
        return self.text or self.desc

    @property
    def centre(self):
        x1, y1, x2, y2 = self.bounds
        return ((x1 + x2) // 2, (y1 + y2) // 2)

    @property
    def area(self):
        x1, y1, x2, y2 = self.bounds
        return max(0, x2 - x1) * max(0, y2 - y1)

    def flags_str(self):
        out = []
        for attr, tag in (("clickable", "click"), ("long-clickable", "long"),
                          ("scrollable", "scroll"), ("checked", "CHECKED"),
                          ("selected", "sel"), ("focused", "focus")):
            if self.flag(attr):
                out.append(tag)
        if not self.flag("enabled"):
            out.append("DISABLED")
        return ",".join(out)

    def describe(self):
        x1, y1, x2, y2 = self.bounds
        lab = self.label or ("<%s>" % self.cls)
        f = self.flags_str()
        return "#%d %s [%d,%d][%d,%d] @(%d,%d)%s" % (
            self.i, _q(lab), x1, y1, x2, y2, self.centre[0], self.centre[1],
            "  " + f if f else "")


def _q(s):
    s = s.replace("\n", "\\n")
    return "'%s'" % s if len(s) <= 60 else "'%s…'" % s[:59]


def dump_xml(serial, retries=3):
    """uiautomator dump -> raw XML text."""
    last = ""
    for attempt in range(retries):
        shell(serial, "rm -f %s" % DEVICE_DUMP, check=False)
        out = shell(serial, "uiautomator dump %s" % DEVICE_DUMP, check=False)
        raw = adb(serial, "exec-out", "cat", DEVICE_DUMP, binary=True, check=False)
        text = raw.decode("utf-8", "replace")
        if text.lstrip().startswith("<?xml") and "</hierarchy>" in text:
            return text
        last = (out + " " + text).strip()
        time.sleep(1.0)
    fail("uiautomator dump produced no hierarchy after %d tries: %s" % (retries, last[:300]))


def parse_nodes(xml_text):
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as e:
        fail("could not parse hierarchy XML: %s" % e)
    nodes, counter = [], [0]

    def walk(el, depth):
        for child in el:
            if child.tag != "node":
                continue
            n = Node(counter[0], depth, child)
            counter[0] += 1
            nodes.append(n)
            walk(child, depth + 1)

    walk(root, 0)
    return nodes


def get_nodes(serial, save_raw=True, tag="dump"):
    xml_text = dump_xml(serial)
    if save_raw:
        os.makedirs(EMU_OUT, exist_ok=True)
        path = os.path.join(EMU_OUT, "%s-%s.xml" % (serial, tag))
        with open(path, "w") as fh:
            fh.write(xml_text)
        return parse_nodes(xml_text), path
    return parse_nodes(xml_text), None


def match_nodes(nodes, pattern):
    try:
        rx = re.compile(pattern, re.IGNORECASE)
    except re.error as e:
        fail("bad regex %r: %s" % (pattern, e), 2)
    return [n for n in nodes if (n.text and rx.search(n.text)) or (n.desc and rx.search(n.desc))]


# ------------------------------------------------------------------ insets

class Insets:
    """Screen geometry + system insets, in pixels."""

    def __init__(self, w, h, top, bottom, gesture_bottom, left, right, cutout_top, source):
        self.w, self.h = w, h
        self.top = top                      # status bar
        self.bottom = bottom                # navigation bar
        self.gesture_bottom = gesture_bottom  # mandatory system gestures (home indicator)
        self.left, self.right = left, right
        self.cutout_top = cutout_top
        self.source = source

    @property
    def safe_top(self):
        return max(self.top, self.cutout_top)

    @property
    def safe_bottom(self):
        """First y that is NOT safe to tap: the home indicator strip counts as unsafe."""
        return self.h - max(self.bottom, self.gesture_bottom)

    def contains(self, x, y):
        return self.left <= x < self.w - self.right and self.safe_top <= y < self.safe_bottom

    def why_unsafe(self, x, y):
        if not (0 <= x < self.w and 0 <= y < self.h):
            return "outside the %dx%d display" % (self.w, self.h)
        if y < self.safe_top:
            return "inside the top status bar / cutout inset (y < %d)" % self.safe_top
        if y >= self.safe_bottom:
            return "under the bottom navigation / gesture inset (y >= %d)" % self.safe_bottom
        if x < self.left:
            return "inside the left inset (x < %d)" % self.left
        if x >= self.w - self.right:
            return "inside the right inset (x >= %d)" % (self.w - self.right)
        return None

    def render(self):
        return "\n".join([
            "display        %dx%d  (source: %s)" % (self.w, self.h, self.source),
            "status bar     top=%d" % self.top,
            "display cutout top=%d" % self.cutout_top,
            "navigation bar bottom=%d" % self.bottom,
            "gesture inset  bottom=%d  (mandatory system gestures / home indicator)" % self.gesture_bottom,
            "side insets    left=%d right=%d" % (self.left, self.right),
            "SAFE AREA      x %d..%d   y %d..%d" % (
                self.left, self.w - self.right, self.safe_top, self.safe_bottom),
        ])


def _wm_size(serial):
    out = shell(serial, "wm size", check=False)
    m = re.search(r"Override size:\s*(\d+)x(\d+)", out) or re.search(r"Physical size:\s*(\d+)x(\d+)", out)
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def _density(serial):
    out = shell(serial, "wm density", check=False)
    m = re.search(r"Override density:\s*(\d+)", out) or re.search(r"Physical density:\s*(\d+)", out)
    return int(m.group(1)) if m else 420


def get_insets(serial):
    w, h = _wm_size(serial)
    # `dumpsys window displays` carries the display's decor insets, cutout and
    # gesture sources in a third of the output; fall back to the full dump.
    dump = shell(serial, "dumpsys window displays", check=False, timeout=90)
    source = "dumpsys window displays"
    if "overrideNonDecorInsets" not in dump:
        dump = shell(serial, "dumpsys window", check=False, timeout=90)
        source = "dumpsys window"
    top = bottom = left = right = None

    # Authoritative and stable: the display's decor insets for the current rotation.
    m = re.search(r"ROTATION_0=\{[^}]*?overrideNonDecorInsets=\[(\d+),(\d+)\]\[(\d+),(\d+)\]", dump)
    if m:
        left, top, right, bottom = (int(g) for g in m.groups())

    # Gesture inset: the bottom mandatorySystemGestures source (home indicator strip).
    gesture_bottom = 0
    for gm in re.finditer(
            r"type=mandatorySystemGestures frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\][^\n]*sideHint=BOTTOM", dump):
        gesture_bottom = max(gesture_bottom, h - int(gm.group(2)))

    # Fall back to per-source frames, then to density-derived defaults.
    if top is None:
        sm = re.search(r"type=statusBars frame=\[\d+,\d+\]\[\d+,(\d+)\]", dump)
        nm = re.search(r"type=navigationBars frame=\[\d+,(\d+)\]\[\d+,\d+\]", dump)
        if sm or nm:
            source = "dumpsys window (InsetsSource)"
            top = int(sm.group(1)) if sm else 0
            bottom = h - int(nm.group(1)) if nm else 0
            left = right = 0
        else:
            d = _density(serial) / 160.0
            source = "density fallback (%.2fx)" % d
            top, bottom, left, right = int(24 * d), int(24 * d), 0, 0

    cutout_top = 0
    cm = re.search(r"mDisplayCutout=DisplayCutout\{insets=Rect\((\d+), (\d+) - (\d+), (\d+)\)", dump)
    if cm:
        cutout_top = int(cm.group(2))

    if not gesture_bottom:
        gesture_bottom = bottom
    return Insets(w, h, top, bottom, gesture_bottom, left, right, cutout_top, source)


# ---------------------------------------------------------------- commands

def _interesting(n):
    """Drop pure layout containers that carry no label and no interaction."""
    return bool(n.label) or n.flag("clickable") or n.flag("scrollable") or n.flag("long-clickable")


def cmd_dump(serial, args):
    nodes, path = get_nodes(serial)
    shown = [n for n in nodes if _interesting(n)]
    base = min((n.depth for n in shown), default=0)
    for n in shown:
        print("  " * min(n.depth - base, 12) + n.describe())
    print("\n%d interesting of %d nodes; raw XML: %s" % (len(shown), len(nodes), path))


def cmd_find(serial, args):
    if not args:
        fail("usage: find <text-regex>", 2)
    nodes, _ = get_nodes(serial)
    hits = match_nodes(nodes, args[0])
    if not hits:
        print("no match for %r" % args[0])
        sys.exit(1)
    ins = get_insets(serial)
    for n in hits:
        x, y = n.centre
        why = ins.why_unsafe(x, y)
        print(n.describe() + ("  << OFFSCREEN: " + why if why else ""))


def _resolve_tap_target(serial, pattern):
    nodes, path = get_nodes(serial)
    hits = match_nodes(nodes, pattern)
    if not hits:
        fail("no node matches %r (see %s)" % (pattern, path))
    # Prefer an interactive node, then the tightest box (labels nest inside buttons).
    hits.sort(key=lambda n: (0 if n.flag("clickable") or n.flag("long-clickable") else 1, n.area))
    n = hits[0]
    x, y = n.centre
    ins = get_insets(serial)
    why = ins.why_unsafe(x, y)
    if why:
        print("OFFSCREEN %s centre=(%d,%d) bounds=[%d,%d][%d,%d]" % (
            _q(n.label), x, y, *n.bounds), file=sys.stderr)
        print("          %s" % why, file=sys.stderr)
        print("          %s" % ins.render().replace("\n", "\n          "), file=sys.stderr)
        sys.exit(1)
    return n, x, y


def cmd_tap(serial, args):
    if not args:
        fail("usage: tap <text-regex>", 2)
    n, x, y = _resolve_tap_target(serial, args[0])
    shell(serial, "input tap %d %d" % (x, y))
    print("tapped %s at (%d,%d)" % (_q(n.label), x, y))


def cmd_tapxy(serial, args):
    if len(args) < 2:
        fail("usage: tapxy <x> <y>", 2)
    x, y = int(args[0]), int(args[1])
    shell(serial, "input tap %d %d" % (x, y))
    print("tapped (%d,%d)" % (x, y))


def cmd_hold(serial, args):
    if not args:
        fail("usage: hold <text-regex> [ms]", 2)
    ms = int(args[1]) if len(args) > 1 else 800
    n, x, y = _resolve_tap_target(serial, args[0])
    shell(serial, "input swipe %d %d %d %d %d" % (x, y, x, y, ms), timeout=max(60, ms // 1000 + 30))
    print("held %s at (%d,%d) for %dms" % (_q(n.label), x, y, ms))


def cmd_swipe(serial, args):
    if not args:
        fail("usage: swipe up|down|left|right [amount]", 2)
    d = args[0].lower()
    ins = get_insets(serial)
    cx, cy = ins.w // 2, (ins.safe_top + ins.safe_bottom) // 2
    span = int(args[1]) if len(args) > 1 else None
    vspan = span if span is not None else (ins.safe_bottom - ins.safe_top) // 3
    hspan = span if span is not None else ins.w // 3
    # "swipe up" = scroll content up = finger moves up.
    moves = {
        "up":    (cx, cy + vspan // 2, cx, cy - vspan // 2),
        "down":  (cx, cy - vspan // 2, cx, cy + vspan // 2),
        "left":  (cx + hspan // 2, cy, cx - hspan // 2, cy),
        "right": (cx - hspan // 2, cy, cx + hspan // 2, cy),
    }
    if d not in moves:
        fail("swipe direction must be up|down|left|right", 2)
    x1, y1, x2, y2 = moves[d]
    shell(serial, "input swipe %d %d %d %d 300" % (x1, y1, x2, y2))
    print("swiped %s (%d,%d)->(%d,%d)" % (d, x1, y1, x2, y2))


def cmd_type(serial, args):
    if not args:
        fail("usage: type <text>", 2)
    text = " ".join(args)
    # `input text` cannot take a bare space; %s is its escape for one. A literal
    # '%' passes through untouched, so do NOT double it — only a literal "%s" in
    # the text is ambiguous, and that is vanishingly rare.
    escaped = text.replace(" ", "%s")
    shell(serial, "input text %s" % _sh_quote(escaped))
    print("typed %r" % text)


def _sh_quote(s):
    return "'" + s.replace("'", "'\\''") + "'"


def cmd_back(serial, args):
    shell(serial, "input keyevent KEYCODE_BACK")
    print("back")


def _next_screenshot_path(serial):
    os.makedirs(EMU_OUT, exist_ok=True)
    rx = re.compile(r"^%s-(\d+)\.png$" % re.escape(serial))
    used = [int(m.group(1)) for m in (rx.match(f) for f in os.listdir(EMU_OUT)) if m]
    return os.path.join(EMU_OUT, "%s-%d.png" % (serial, max(used) + 1 if used else 1))


def cmd_screenshot(serial, args):
    path = args[0] if args else _next_screenshot_path(serial)
    d = os.path.dirname(os.path.abspath(path))
    if d:
        os.makedirs(d, exist_ok=True)
    png = adb(serial, "exec-out", "screencap", "-p", binary=True)
    if not png.startswith(b"\x89PNG"):
        fail("screencap did not return a PNG (%d bytes)" % len(png))
    with open(path, "wb") as fh:
        fh.write(png)
    print(os.path.abspath(path))


def cmd_wait(serial, args):
    if not args:
        fail("usage: wait <text-regex> [timeout]", 2)
    pattern = args[0]
    timeout = float(args[1]) if len(args) > 1 else 15.0
    deadline = time.time() + timeout
    while True:
        nodes, path = get_nodes(serial, save_raw=False)
        hits = match_nodes(nodes, pattern)
        if hits:
            print("found " + hits[0].describe())
            return
        if time.time() >= deadline:
            break
        time.sleep(0.7)
    print("ui.py: %r did not appear within %.0fs; tree was:" % (pattern, timeout), file=sys.stderr)
    cmd_dump(serial, [])
    sys.exit(1)


def cmd_insets(serial, args):
    print(get_insets(serial).render())


def _overlap(a, b):
    ax1, ay1, ax2, ay2 = a
    bx1, by1, bx2, by2 = b
    ox = min(ax2, bx2) - max(ax1, bx1)
    oy = min(ay2, by2) - max(ay1, by1)
    return ox * oy if ox > 0 and oy > 0 else 0


def cmd_audit(serial, args):
    if args and args[0] == "--xml":
        if len(args) < 2:
            fail("usage: audit [--xml <saved-dump.xml>]", 2)
        path = args[1]
        with open(path) as fh:
            nodes = parse_nodes(fh.read())
    else:
        nodes, path = get_nodes(serial, tag="audit")
    ins = get_insets(serial)
    print(ins.render())
    print("\nraw XML: %s" % path)

    clickable = [n for n in nodes if n.flag("clickable") and n.area > 0]
    screen_area = float(ins.w * ins.h)
    # Scrims, sheet backdrops and full-bleed containers legitimately run edge to edge.
    # For those only an untappable centre is a real defect; edge clipping matters for controls.
    def is_backdrop(n):
        return n.area >= 0.40 * screen_area
    controls = [n for n in clickable if not is_backdrop(n)]
    print("\n%d clickable node(s) — %d control(s), %d full-bleed backdrop(s) ignored for clipping"
          % (len(clickable), len(controls), len(clickable) - len(controls)))

    unsafe = []
    for n in clickable:
        x1, y1, x2, y2 = n.bounds
        cx, cy = n.centre
        problems = []
        if x2 <= 0 or y2 <= 0 or x1 >= ins.w or y1 >= ins.h:
            problems.append("entirely off the %dx%d display" % (ins.w, ins.h))
        elif not is_backdrop(n):
            if y1 < ins.safe_top:
                problems.append("top %dpx under the status bar/cutout" % (ins.safe_top - y1))
            if y2 > ins.safe_bottom:
                problems.append("bottom %dpx under the navigation/gesture inset (home indicator)"
                                % (y2 - ins.safe_bottom))
            if x1 < ins.left:
                problems.append("left %dpx off the safe area" % (ins.left - x1))
            if x2 > ins.w - ins.right:
                problems.append("right %dpx off the safe area" % (x2 - (ins.w - ins.right)))
        why_centre = ins.why_unsafe(cx, cy)
        if why_centre:
            problems.append("CENTRE UNTAPPABLE: " + why_centre)
        if problems:
            unsafe.append((n, problems))

    if unsafe:
        print("\n=== SAFE-AREA VIOLATIONS (%d) ===" % len(unsafe))
        for n, problems in unsafe:
            print("  " + n.describe())
            for p in problems:
                print("      - " + p)
    else:
        print("\nsafe area: OK — every clickable node is fully inside x %d..%d, y %d..%d"
              % (ins.left, ins.w - ins.right, ins.safe_top, ins.safe_bottom))

    overlaps = []
    for i in range(len(controls)):
        for j in range(i + 1, len(controls)):
            a, b = controls[i], controls[j]
            ov = _overlap(a.bounds, b.bounds)
            if not ov:
                continue
            # Nested clickables (a row containing a button) are a normal Compose pattern;
            # only flag partial overlaps, where neither box contains the other.
            if ov >= min(a.area, b.area) * 0.99:
                continue
            frac = ov / float(min(a.area, b.area))
            if frac >= 0.05:
                overlaps.append((a, b, frac))

    if overlaps:
        print("\n=== OVERLAPPING CLICKABLES (%d) ===" % len(overlaps))
        for a, b, frac in sorted(overlaps, key=lambda t: -t[2]):
            print("  %.0f%% overlap:" % (frac * 100))
            print("      " + a.describe())
            print("      " + b.describe())
    else:
        print("\noverlap: OK — no two clickable nodes partially overlap")

    if unsafe or overlaps:
        sys.exit(1)


COMMANDS = {
    "dump": cmd_dump, "find": cmd_find, "tap": cmd_tap, "tapxy": cmd_tapxy,
    "hold": cmd_hold, "swipe": cmd_swipe, "type": cmd_type, "back": cmd_back,
    "screenshot": cmd_screenshot, "wait": cmd_wait, "insets": cmd_insets,
    "audit": cmd_audit,
}


def main(argv):
    if len(argv) < 3:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    serial, command, args = argv[1], argv[2], argv[3:]
    if command not in COMMANDS:
        fail("unknown command %r; one of: %s" % (command, ", ".join(sorted(COMMANDS))), 2)
    COMMANDS[command](serial, args)


if __name__ == "__main__":
    main(sys.argv)
