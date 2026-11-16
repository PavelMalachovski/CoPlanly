#!/usr/bin/env python3
"""Whether every crash the iPhone tour met is the one known Compose Multiplatform race.

Usage: known-tour-crash.py <shots-dir> <crashed-visit-count>

The tour's result bundle exports each crash report (`*.ips`) into <shots-dir>. The known crash is
Compose Multiplatform 1.9.3's: XCUITest's accessibility snapshot reads the value of an
`AccessibilityElement` whose semantics node a recomposition has just disposed
(docs/DEVICE-CHECKLIST.md §3.40). Its faulting thread holds both

  - `kfun:androidx.compose.ui.platform.AccessibilityElement.<get-cachedProperties>#internal`
    among its top three frames (the faulting site; the first is often a mis-symbolicated
    `_OBJC_PROTOCOL_$_...`), and
  - a frame of `-[XCTAutomationSession ...]` (the snapshot that asked).

Exits 0 only when there is at least one report per crashed visit and every report matches; prints
one line per report saying why. Anything else (another crash, a report missing, one that cannot be
read) exits 1, so the job fails as it always did.
"""

import glob
import json
import os
import sys

CACHED_PROPERTIES = "kfun:androidx.compose.ui.platform.AccessibilityElement.<get-cachedProperties>#internal"
AUTOMATION_SESSION = "[XCTAutomationSession "
TOP_FRAMES = 3


def faulting_symbols(path):
    """The symbols of the faulting thread's frames, top first, of the crash report at path."""
    with open(path, encoding="utf-8") as handle:
        text = handle.read()
    # An .ips file is a one-line JSON header, then the report's JSON body.
    header, _, body = text.partition("\n")
    report = json.loads(body) if body.strip() else json.loads(header)
    thread = report["threads"][report["faultingThread"]]
    return [frame.get("symbol", "") for frame in thread.get("frames", [])]


def is_known(symbols):
    return CACHED_PROPERTIES in symbols[:TOP_FRAMES] and any(AUTOMATION_SESSION in s for s in symbols)


def main(shots, crashed):
    reports = sorted(glob.glob(os.path.join(shots, "*.ips")))
    if len(reports) < crashed:
        print(f"{len(reports)} crash report(s) for {crashed} crashed visit(s): not every crash can be told")
        return 1
    known = True
    for path in reports:
        try:
            symbols = faulting_symbols(path)
        except (OSError, ValueError, KeyError, IndexError, TypeError) as error:
            print(f"{os.path.basename(path)}: unreadable ({error})")
            known = False
            continue
        if is_known(symbols):
            print(f"{os.path.basename(path)}: the known Compose Multiplatform accessibility race")
        else:
            print(f"{os.path.basename(path)}: another crash; top frames: {symbols[:5]}")
            known = False
    return 0 if known else 1


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1], int(sys.argv[2])))
