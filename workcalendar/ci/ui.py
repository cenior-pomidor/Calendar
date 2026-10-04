#!/usr/bin/env python3
"""Tiny UI automation helper for the emulator smoke test (uiautomator + adb)."""
import base64
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

OUT = os.environ.get("SMOKE_OUT", "smoke")
PKG = "io.github.ceniorpomidor.workcalendar"


def adb(*args, check=False):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check)


def dump_xml():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        res = adb("shell", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in res.stdout:
            return ET.fromstring(res.stdout[res.stdout.index("<?xml") if "<?xml" in res.stdout else res.stdout.index("<hierarchy"):])
        time.sleep(1)
    return None


def nodes(root):
    for n in root.iter("node"):
        yield n


def center(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(root, text, exact=False):
    best = None
    for n in nodes(root):
        for attr in ("text", "content-desc"):
            value = n.get(attr) or ""
            if (value == text) if exact else (text in value):
                if best is None or len(value) < len(best[1]):
                    best = (n, value)
    return best[0] if best else None


def texts(root):
    out = []
    for n in nodes(root):
        t = (n.get("text") or "").strip()
        d = (n.get("content-desc") or "").strip()
        if t or d:
            c = center(n) or (0, 0)
            out.append((c[1], c[0], t or f"[{d}]"))
    out.sort()
    return [t for _, _, t in out]


def crashed():
    log = adb("logcat", "-d", "-b", "crash").stdout
    if "FATAL EXCEPTION" in log or ("AndroidRuntime" in log and PKG in log):
        print("=====CRASH=====")
        print(log[-6000:])
        return True
    return False


def screenshot(name, inline):
    os.makedirs(OUT, exist_ok=True)
    png = os.path.join(OUT, f"{name}.png")
    with open(png, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    if inline:
        jpg = os.path.join(OUT, f"{name}.jpg")
        r = subprocess.run(["convert", png, "-resize", "432x", "-quality", "60", jpg], capture_output=True)
        if r.returncode == 0 and os.path.exists(jpg):
            data = base64.b64encode(open(jpg, "rb").read()).decode()
            print(f"=====SCREENSHOT {name} {len(data)}=====")
            for i in range(0, len(data), 4000):
                print(data[i:i + 4000])
            print(f"=====END {name}=====")


def cmd_dump(name, inline="0"):
    time.sleep(1.5)
    root = dump_xml()
    print(f"===== SCREEN {name} =====")
    if root is None:
        print("(no ui dump)")
    else:
        for t in texts(root):
            print("  " + t.replace("\n", " / "))
    screenshot(name, inline == "1")
    if crashed():
        sys.exit(1)


def cmd_tap(text, exact="0"):
    root = dump_xml()
    node = find(root, text, exact == "1") if root is not None else None
    if node is None:
        print(f"!! element not found: {text}")
        return
    x, y = center(node)
    adb("shell", "input", "tap", str(x), str(y))
    print(f">> tap '{text}' at {x},{y}")
    time.sleep(1.2)


def cmd_type(text):
    adb("shell", "input", "text", text.replace(" ", "%s"))
    time.sleep(0.5)


def cmd_route(route):
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity", "-a", "android.intent.action.VIEW", "--es", "route", route)
    print(f">> route {route}")
    time.sleep(2)


def cmd_back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)


if __name__ == "__main__":
    command, *args = sys.argv[1:]
    {"dump": cmd_dump, "tap": cmd_tap, "type": cmd_type, "route": cmd_route, "back": cmd_back}[command](*args)
