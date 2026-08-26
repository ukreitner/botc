#!/usr/bin/env python3
"""Regenerate the app's bundled character data from the official source.

Source of truth (lead decision D31): The Pandemonium Institute's own
machine-readable data files, the same ones the official app and Script Tool
ship:

    https://github.com/ThePandemoniumInstitute/botc-release
        resources/data/roles.json       181 characters
        resources/data/jinxes.json      131 jinxes (grouped by owner)
        resources/data/nightsheet.json  firstNight 80 / otherNight 99

A pinned copy of those three files is vendored under ``tools/data/`` (see
``tools/data/SOURCE.json`` for the upstream commit), so a build never needs
the network.  ``--fetch`` re-downloads them; nothing else in the pipeline
touches the network.

The official data does not carry everything the app needs, so it is merged
with an *overlay* (``tools/app-overlay.json``) holding the app's own extra
fields:

  * ``firstNightReminder`` / ``otherNightReminder`` — the app deliberately
    ships bra1n/townsquare's verbose storyteller prose instead of the
    official terse strings ("Give a finger signal."); data-accuracy P3 #18
    rules that the app's copy is better and must be kept.
  * ``spentLabel`` — the exact reminder label that marks a once-per-game
    ability as used (lead D49, ARCHITECTURE §2.14).
  * ``edition`` — the app's edition ids (``exp`` for ``carousel``, ``sv``
    for ``snv``) and the app's convention that every Fabled/Loric lives in
    the ``fabled`` / ``loric`` edition group.

Outputs (both pretty-printed with one-space indent, grouped by edition then
team then id, matching the existing files so diffs stay reviewable):

    engine/src/main/resources/botc/data/characters.json
    engine/src/main/resources/botc/data/night_and_jinxes.json

Usage:
    python3 tools/regen-data.py                 # regenerate from vendored data
    python3 tools/regen-data.py --fetch         # re-vendor from upstream first
    python3 tools/regen-data.py --check         # verify, write nothing
    python3 tools/regen-data.py --bootstrap-overlay
                                                # re-derive app-overlay.json
                                                # from the current characters.json
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.request
from collections import Counter
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
VENDOR = os.path.join(HERE, "data")
OVERLAY_PATH = os.path.join(HERE, "app-overlay.json")
DATA_DIR = os.path.join(ROOT, "engine", "src", "main", "resources", "botc", "data")
CHARACTER_KT = os.path.join(
    ROOT, "engine", "src", "main", "kotlin", "com", "clocktower", "engine", "Character.kt"
)

RAW_BASE = "https://raw.githubusercontent.com/ThePandemoniumInstitute/botc-release/main/resources/data/"
SOURCE_FILES = ("roles.json", "jinxes.json", "nightsheet.json")

# ---------------------------------------------------------------------------
# Mappings between the official vocabulary and the app's
# ---------------------------------------------------------------------------

# The app's edition ids. Official `carousel` is the Experimental set; official
# `snv` is Sects & Violets. Fabled and Loric are grouped by team, not edition
# (official files `deusexfiasco` and `ferryman` under `carousel`; the app has
# always grouped every Fabled together, and GameData.travellersFor keys off
# edition, so keeping them out of `exp` matters).
EDITION_MAP = {"carousel": "exp", "snv": "sv", "tb": "tb", "bmr": "bmr", "fabled": "fabled"}

# `Team` uses kotlinx @SerialName("traveler") — one L, US spelling.
TEAM_MAP = {"traveller": "traveler"}

# The app's night-order markers vs the official nightsheet's.
MARKER_MAP = {
    "dusk": "DUSK",
    "minioninfo": "MINION_INFO",
    "demoninfo": "DEMON_INFO",
    "dawn": "DAWN",
}

# Output ordering — the shape the existing characters.json already has.
EDITION_ORDER = ["tb", "bmr", "sv", "exp", "fabled", "loric"]
TEAM_ORDER = ["townsfolk", "outsider", "minion", "demon", "traveler", "fabled", "loric"]

FIELD_ORDER = [
    "id",
    "name",
    "edition",
    "team",
    "ability",
    "setup",
    "firstNightReminder",
    "otherNightReminder",
    "reminders",
    "remindersGlobal",
    "spentLabel",
]


# ---------------------------------------------------------------------------
# Vendoring
# ---------------------------------------------------------------------------


def fetch_official() -> None:
    """Re-download the three official files into tools/data/ and pin the commit."""
    os.makedirs(VENDOR, exist_ok=True)
    for name in SOURCE_FILES:
        url = RAW_BASE + name
        with urllib.request.urlopen(url, timeout=60) as response:
            body = response.read()
        with open(os.path.join(VENDOR, name), "wb") as handle:
            handle.write(body)
        print(f"fetched {name} ({len(body)} bytes)")
    sha = date = ""
    try:
        api = (
            "https://api.github.com/repos/ThePandemoniumInstitute/botc-release/"
            "commits?path=resources/data&per_page=1"
        )
        with urllib.request.urlopen(api, timeout=60) as response:
            commits = json.load(response)
        sha = commits[0]["sha"]
        date = commits[0]["commit"]["committer"]["date"]
    except Exception as exc:  # noqa: BLE001 - provenance is best-effort
        print(f"warning: could not read the upstream commit ({exc})", file=sys.stderr)
    write_json(
        os.path.join(VENDOR, "SOURCE.json"),
        {
            "repo": "https://github.com/ThePandemoniumInstitute/botc-release",
            "path": "resources/data",
            "commit": sha,
            "commitDate": date,
            "files": list(SOURCE_FILES),
            "vendoredAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        },
    )


def load_official():
    roles = load_json(os.path.join(VENDOR, "roles.json"))
    jinx_groups = load_json(os.path.join(VENDOR, "jinxes.json"))
    nightsheet = load_json(os.path.join(VENDOR, "nightsheet.json"))
    return roles, jinx_groups, nightsheet


# ---------------------------------------------------------------------------
# The Team enum question
# ---------------------------------------------------------------------------


def loric_team_value(force: str | None) -> str:
    """What to write in `team` for the 11 official Loric characters.

    ARCHITECTURE §2.14 gives `Team` a `@SerialName("loric") LORIC` value, but
    that is WP0's file, not this package's.  kotlinx.serialization throws on an
    unknown enum value (`ignoreUnknownKeys` only forgives unknown *keys*), so
    writing "loric" before WP0 lands would make the whole dataset fail to load
    and every engine test fail.  Detect what the engine can actually parse and
    emit that; once WP0 adds LORIC, re-running this script flips the 11 entries
    with no other change.
    """
    if force:
        return force
    try:
        with open(CHARACTER_KT, encoding="utf-8") as handle:
            source = handle.read()
    except OSError:
        return "fabled"
    return "loric" if '@SerialName("loric")' in source else "fabled"


# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------


def normalise_official_prose(text: str) -> str:
    """Clean an official night string for use as app prose.

    The official strings carry two bits of markup for TPI's own renderer:
    ``:reminder:`` (draw the reminder-token icon here) and ``*TOKEN NAME*``
    (an info token to show).  The asterisk form is meaningful to a
    storyteller and is kept; ``:reminder:`` is a rendering hint with no
    meaning in prose and is dropped.  Runs of whitespace are collapsed.
    """
    text = text.replace(":reminder:", " ")
    return " ".join(text.split())


def build_characters(roles, overlay, loric_value):
    manual = overlay["manualOverrides"]
    per_id = overlay["characters"]
    out = []
    for role in roles:
        cid = role["id"]
        team = TEAM_MAP.get(role["team"], role["team"])
        official_team = team
        if team == "loric":
            team = loric_value
        extra = per_id.get(cid, {})

        # Edition: official, mapped, unless the character is a Fabled/Loric —
        # those are grouped by team in this app.
        if official_team == "loric":
            edition = "loric"
        elif official_team == "fabled":
            edition = "fabled"
        else:
            edition = EDITION_MAP[role["edition"]]

        entry = {
            "id": cid,
            "name": role["name"],
            "edition": edition,
            "team": team,
            "ability": role.get("ability", ""),
            "setup": bool(role.get("setup", False)),
            # The app's verbose storyteller prose wins over the official terse
            # strings (data-accuracy P3 #18) except where `manualOverrides`
            # says otherwise; a character the app has never carried falls back
            # to the official string.
            "firstNightReminder": extra.get(
                "firstNightReminder",
                normalise_official_prose(role.get("firstNightReminder", "")),
            ),
            "otherNightReminder": extra.get(
                "otherNightReminder",
                normalise_official_prose(role.get("otherNightReminder", "")),
            ),
            "reminders": list(role.get("reminders", [])),
            "remindersGlobal": list(role.get("remindersGlobal", [])),
        }
        for field, value in manual.get(cid, {}).items():
            entry[field] = value
        spent = extra.get("spentLabel", "")
        if spent:
            entry["spentLabel"] = spent
        out.append({k: entry[k] for k in FIELD_ORDER if k in entry})

    def sort_key(c):
        return (
            EDITION_ORDER.index(c["edition"]) if c["edition"] in EDITION_ORDER else 99,
            TEAM_ORDER.index(c["team"]) if c["team"] in TEAM_ORDER else 99,
            c["id"],
        )

    out.sort(key=sort_key)
    return out


def build_jinxes(jinx_groups):
    """Flatten the official grouped form into the app's {id1, id2, reason}.

    `id1` is the official group owner; `GameData.activeJinxes` already matches
    order-insensitively, but 21 pairs are stored in the opposite order to the
    old bundle, so anything sorting on `id1` will reorder (data-accuracy §4).
    """
    flat = []
    for group in jinx_groups:
        for jinx in group["jinx"]:
            flat.append(
                {
                    "id1": group["id"],
                    "id2": jinx["id"],
                    # Two official entries carry a trailing space.
                    "reason": jinx["reason"].strip(),
                }
            )
    return flat


def build_night_orders(nightsheet):
    def convert(ids):
        return [MARKER_MAP.get(i, i) for i in ids]

    return convert(nightsheet["firstNight"]), convert(nightsheet["otherNight"])


# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------


def validate(characters, jinxes, first_night, other_night, loric_value):
    problems = []
    by_id = {c["id"]: c for c in characters}

    if len(characters) != 181:
        problems.append(f"expected 181 characters, got {len(characters)}")
    if len(jinxes) != 131:
        problems.append(f"expected 131 jinxes, got {len(jinxes)}")
    if len(first_night) != 80:
        problems.append(f"expected 80 firstNight entries, got {len(first_night)}")
    if len(other_night) != 99:
        problems.append(f"expected 99 otherNight entries, got {len(other_night)}")

    ids = [c["id"] for c in characters]
    if len(ids) != len(set(ids)):
        problems.append("duplicate character ids")
    for cid in ids:
        if cid != "".join(ch for ch in cid.lower() if ch.isalnum()):
            problems.append(f"id is not in the app's normalized form: {cid}")

    markers = set(MARKER_MAP.values())
    for label, order in (("firstNight", first_night), ("otherNight", other_night)):
        for entry in order:
            if entry in markers:
                continue
            if entry not in by_id:
                problems.append(f"{label} references an unknown character: {entry}")

    # GameDataTest: a non-empty night reminder implies membership of that list.
    for c in characters:
        if c.get("firstNightReminder") and c["id"] not in first_night:
            problems.append(f"{c['id']} has a firstNightReminder but is not in firstNight")
        if c.get("otherNightReminder") and c["id"] not in other_night:
            problems.append(f"{c['id']} has an otherNightReminder but is not in otherNight")
        # ...and the converse, which the audit's DataIntegrityTest #3 adds.
        if c["id"] in first_night and not c.get("firstNightReminder"):
            problems.append(f"{c['id']} is in firstNight but has no firstNightReminder")
        if c["id"] in other_night and not c.get("otherNightReminder"):
            problems.append(f"{c['id']} is in otherNight but has no otherNightReminder")

    for j in jinxes:
        for side in ("id1", "id2"):
            if j[side] not in by_id:
                problems.append(f"jinx references an unknown character: {j[side]}")

    # Every spentLabel must be a label this character can actually place.
    for c in characters:
        spent = c.get("spentLabel", "")
        if spent and spent not in c["reminders"] + c["remindersGlobal"]:
            problems.append(f"{c['id']} spentLabel {spent!r} is not one of its reminders")

    # Labels must not differ only by case (data-accuracy test 2).
    seen = {}
    for c in characters:
        for label in c["reminders"] + c["remindersGlobal"]:
            other = seen.setdefault(label.lower(), label)
            if other != label:
                problems.append(f"labels differ only by case: {other!r} vs {label!r}")

    # Guide coverage (D23 / ARCHITECTURE §2.14) — advisory: the guide is a
    # hand-written file, not a generated one.
    guide_path = os.path.join(DATA_DIR, "night_guide.json")
    if os.path.exists(guide_path):
        guide = load_json(guide_path)
        for c in characters:
            entry = guide.get(c["id"])
            if not entry or not any(
                entry.get(k) for k in ("first", "other", "setup", "day", "reference")
            ):
                problems.append(f"night_guide.json has no channel for {c['id']}")
                continue
            if (c["id"] in first_night) != bool(entry.get("first")):
                problems.append(f"night_guide.json first channel mismatch for {c['id']}")
            if (c["id"] in other_night) != bool(entry.get("other")):
                problems.append(f"night_guide.json other channel mismatch for {c['id']}")
        for marker in markers:
            if marker not in guide:
                problems.append(f"night_guide.json is missing the {marker} marker entry")

    return problems


def summarise(characters, jinxes, first_night, other_night, loric_value):
    teams = Counter(c["team"] for c in characters)
    print(f"characters       : {len(characters)}")
    for team in TEAM_ORDER:
        if teams.get(team):
            print(f"  {team:<12s}: {teams[team]}")
    if loric_value != "loric":
        print(f"  (the 11 Loric are written as team={loric_value!r}; see tools/DATA.md)")
    print(f"jinxes           : {len(jinxes)}")
    print(f"firstNight order : {len(first_night)}")
    print(f"otherNight order : {len(other_night)}")
    print(f"spentLabel set on: {sum(1 for c in characters if c.get('spentLabel'))}")


# ---------------------------------------------------------------------------
# I/O
# ---------------------------------------------------------------------------


def load_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path, value):
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(value, handle, indent=1, ensure_ascii=False)
        handle.write("\n")


def bootstrap_overlay():
    """Re-derive tools/app-overlay.json from the current characters.json.

    Only used to seed the overlay (or to re-capture prose edits made directly
    in characters.json).  The manual-override block is preserved verbatim.
    """
    current = load_json(os.path.join(DATA_DIR, "characters.json"))
    roles = {r["id"]: r for r in load_json(os.path.join(VENDOR, "roles.json"))}
    previous = load_json(OVERLAY_PATH) if os.path.exists(OVERLAY_PATH) else {}
    per_id = {}
    for c in sorted(current, key=lambda x: x["id"]):
        official = roles.get(c["id"], {})
        keep = {}
        for field in ("firstNightReminder", "otherNightReminder"):
            value = c.get(field, "")
            if value != official.get(field, ""):
                keep[field] = value
        spent = c.get("spentLabel") or previous.get("characters", {}).get(c["id"], {}).get(
            "spentLabel", ""
        )
        if spent:
            keep["spentLabel"] = spent
        if keep:
            per_id[c["id"]] = keep
    write_json(
        OVERLAY_PATH,
        {
            "_comment": previous.get("_comment", ""),
            "manualOverrides": previous.get("manualOverrides", {}),
            "characters": per_id,
        },
    )
    print(f"wrote {OVERLAY_PATH} ({len(per_id)} entries)")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fetch", action="store_true", help="re-download the official files")
    parser.add_argument("--check", action="store_true", help="validate only, write nothing")
    parser.add_argument(
        "--bootstrap-overlay",
        action="store_true",
        help="re-derive tools/app-overlay.json from the current characters.json",
    )
    parser.add_argument(
        "--loric-team",
        choices=("loric", "fabled"),
        help="override the team string written for the 11 Loric characters",
    )
    args = parser.parse_args()

    if args.fetch:
        fetch_official()
    if args.bootstrap_overlay:
        bootstrap_overlay()
        return 0

    roles, jinx_groups, nightsheet = load_official()
    overlay = load_json(OVERLAY_PATH)
    loric_value = loric_team_value(args.loric_team)

    characters = build_characters(roles, overlay, loric_value)
    jinxes = build_jinxes(jinx_groups)
    first_night, other_night = build_night_orders(nightsheet)

    if not args.check:
        write_json(os.path.join(DATA_DIR, "characters.json"), characters)
        write_json(
            os.path.join(DATA_DIR, "night_and_jinxes.json"),
            {"jinxes": jinxes, "firstNight": first_night, "otherNight": other_night},
        )

    summarise(characters, jinxes, first_night, other_night, loric_value)
    problems = validate(characters, jinxes, first_night, other_night, loric_value)
    if problems:
        print(f"\n{len(problems)} problem(s):", file=sys.stderr)
        for p in problems:
            print(f"  - {p}", file=sys.stderr)
        return 1
    print("\nall checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
