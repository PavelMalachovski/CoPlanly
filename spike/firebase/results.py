"""Prints one line per test case from the JUnit XML files under the given directory."""
import pathlib
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
files = sorted(root.rglob("*.xml"))
if not files:
    print(f"No JUnit XML under {root}")
    sys.exit(0)
for path in files:
    for case in ET.parse(path).getroot().iter("testcase"):
        name = case.get("name")
        failure = case.find("failure")
        if failure is None:
            failure = case.find("error")
        if failure is not None:
            message = (failure.get("message") or failure.text or "").strip().splitlines()
            print(f"FAIL  {name}  -- {message[0][:300] if message else ''}")
        elif case.find("skipped") is not None:
            print(f"SKIP  {name}")
        else:
            print(f"PASS  {name}  ({case.get('time')} s)")
