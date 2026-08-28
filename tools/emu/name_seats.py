#!/usr/bin/env python3
"""Rename the seat name fields on the New game screen. Usage: name_seats.py <serial> Ana Ben ..."""
import re
import subprocess
import sys
import time

ADB = "/opt/homebrew/share/android-commandlinetools/platform-tools/adb"


def sh(serial, cmd):
    return subprocess.run([ADB, "-s", serial, "shell"] + cmd.split(),
                          capture_output=True, text=True).stdout


def dump(serial):
    sh(serial, "uiautomator dump /sdcard/d.xml")
    return subprocess.run([ADB, "-s", serial, "shell", "cat", "/sdcard/d.xml"],
                          capture_output=True, text=True).stdout


def find_field(serial, label):
    xml = dump(serial)
    for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        if m.group(1) == label:
            x1, y1, x2, y2 = (int(m.group(i)) for i in range(2, 6))
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def main():
    serial = sys.argv[1]
    names = sys.argv[2:]
    for i, name in enumerate(names, start=1):
        pos = find_field(serial, "Player %d" % i) or find_field(serial, name)
        if pos is None:
            print("seat %d: no field found" % i)
            continue
        sh(serial, "input tap %d %d" % pos)
        time.sleep(0.4)
        sh(serial, "input keyevent KEYCODE_MOVE_END")
        for _ in range(20):
            sh(serial, "input keyevent KEYCODE_DEL")
        sh(serial, "input text %s" % name)
        time.sleep(0.3)
        sh(serial, "input keyevent KEYCODE_BACK")
        time.sleep(0.5)
        print("seat %d -> %s" % (i, name))


main()
