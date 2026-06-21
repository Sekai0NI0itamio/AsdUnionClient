#!/usr/bin/env python3
"""Extract Java code from Wayback Machine HTML snapshots of GitHub blob pages.

GitHub renders code lines as JSON-encoded strings inside a 'react-code-lines'
container. Each line is an entry of a JSON array. Special characters are
escaped (e.g. \\u003c for '<', \\u003e for '>', \\u0026 for '&').
"""
import json
import os
import re
import sys
import urllib.request

BASE = "https://web.archive.org/web/{ts}/https://github.com/Kopamed/Raven-bPLUS/blob/main/{path}"
TIMESTAMP = "20240823231313"

# (relative path, output file)
FILES = [
    ("src/main/java/keystrokesmod/client/module/modules/combat/AimAssist.java", "AimAssist.java"),
    ("src/main/java/keystrokesmod/client/module/modules/combat/AutoBlock.java", "AutoBlock.java"),
    ("src/main/java/keystrokesmod/client/module/modules/combat/AutoWeapon.java", "AutoWeapon.java"),
    ("src/main/java/keystrokesmod/client/module/modules/combat/BurstClicker.java", "BurstClicker.java"),
    ("src/main/java/keystrokesmod/client/module/modules/combat/ClickAssist.java", "ClickAssist.java"),
    ("src/main/java/keystrokesmod/client/module/modules/combat/HitBox.java", "HitBox.java"),
    ("src/main/java/keystrokesmod/client/module/modules/combat/LeftClicker.java", "LeftClicker.java"),
    ("src/main/java/keystrokesmod/client/module/modules/combat/Velocity.java", "Velocity.java"),
    ("src/main/java/keystrokesmod/client/module/modules/combat/WTap.java", "WTap.java"),
    ("src/main/java/keystrokesmod/client/module/Module.java", "Module.java"),
    ("src/main/java/keystrokesmod/client/module/ModuleManager.java", "ModuleManager.java"),
    ("src/main/java/keystrokesmod/client/clickgui/ClickGui.java", "ClickGui.java"),
    ("src/main/java/keystrokesmod/client/utils/Utils.java", "Utils.java"),
    ("src/main/java/keystrokesmod/client/utils/RotationUtils.java", "RotationUtils.java"),
    ("src/main/java/keystrokesmod/Raven.java", "Raven.java"),
    ("src/main/java/keystrokesmod/client/main/RavenTweaker.java", "RavenTweaker.java"),
]

DEST = sys.argv[1] if len(sys.argv) > 1 else "."


def extract_code(html: str) -> str | None:
    # The Wayback snapshot of a GitHub blob page contains the source as a
    # JSON array of lines embedded as text inside the page. The lines are
    # enclosed in [ ... ] with each line as a JSON string. Special characters
    # are JSON-escaped (e.g. \u003c for '<').
    needle = 'public class'  # reliable anchor: every Java source has it
    idx = html.find(needle)
    if idx < 0:
        return None
    bracket_start = html.rfind('[', 0, idx)
    if bracket_start < 0:
        return None
    # Find matching closing ] by scanning forward and tracking depth, but
    # only outside JSON strings. The lines are joined into a single text
    # blob; newlines may be embedded as \n in the string content. So we
    # walk char-by-char past the [ until we find the matching ].
    depth = 0
    in_string = False
    escape = False
    end_bracket = -1
    for i in range(bracket_start, len(html)):
        c = html[i]
        if escape:
            escape = False
            continue
        if c == '\\':
            escape = True
            continue
        if c == '"':
            in_string = not in_string
            continue
        if in_string:
            continue
        if c == '[':
            depth += 1
        elif c == ']':
            depth -= 1
            if depth == 0:
                end_bracket = i
                break
    if end_bracket < 0:
        return None
    raw = html[bracket_start:end_bracket + 1]
    try:
        lines = json.loads(raw)
    except json.JSONDecodeError:
        return None
    if not isinstance(lines, list) or not lines:
        return None
    out = []
    for line in lines:
        if not isinstance(line, str):
            return None
        out.append(line)
    return "\n".join(out) + "\n"


def fetch(path: str) -> str | None:
    url = BASE.format(ts=TIMESTAMP, path=path)
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = resp.read()
            encoding = resp.headers.get_content_charset() or "utf-8"
            return data.decode(encoding, errors="replace")
    except Exception as exc:
        print(f"  ! fetch error for {path}: {exc}", file=sys.stderr)
        return None


def main() -> None:
    os.makedirs(DEST, exist_ok=True)
    for path, out_name in FILES:
        out_path = os.path.join(DEST, out_name)
        if os.path.exists(out_path) and os.path.getsize(out_path) > 200:
            print(f"[skip] {out_name} already present ({os.path.getsize(out_path)} bytes)")
            continue
        print(f"[fetch] {path}")
        html = fetch(path)
        if html is None:
            print("  -> no response")
            continue
        code = extract_code(html)
        if code is None:
            print("  -> could not extract code")
            with open(out_path + ".raw.html", "w", encoding="utf-8") as f:
                f.write(html)
            continue
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(code)
        print(f"  -> {out_name} ({len(code)} bytes, {code.count(chr(10))} lines)")


if __name__ == "__main__":
    main()
