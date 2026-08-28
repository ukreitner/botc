# Example scenario: home -> new game -> Trouble Brewing -> grimoire, auditing on the way.
# Run with:
#   ./emu.sh launch emulator-5554 --fresh
#   ./scenario.py emulator-5554 new_game
#
# Starts from the Home screen, so it needs --fresh: a plain launch resumes
# whatever game is already saved and step 1 will not find "New game".

STEPS = [
    ("wait",       "New game"),
    ("audit",      None),
    ("tap",        "New game"),
    ("wait",       "TABLE"),
    ("audit",      None),
    ("tap",        "Trouble Brewing"),
    ("tap",        "Start empty"),
    ("wait",       "Before the first night"),
    ("audit",      None),
    ("tap",        "^Close$"),
    ("wait",       "Grimoire"),
    ("audit",      None),
]
