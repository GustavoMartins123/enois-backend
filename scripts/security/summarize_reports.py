#!/usr/bin/env python3
"""Summarize Surefire XML without copying environment properties, HTTP bodies or logs.

Example: python3 scripts/security/summarize_reports.py target/surefire-reports --audit-only
"""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def summarize(directory, audit_only=False):
    classes = []
    for path in sorted(Path(directory).glob("TEST-*.xml")):
        root = ET.parse(path).getroot()
        name = root.attrib["name"]
        audit = ".securityaudit." in name or name.endswith(".S3UploadConstraintAuditTests")
        if audit_only and not audit:
            continue
        entry = {"class": name, **{key: int(root.attrib.get(key, 0))
                                  for key in ("tests", "failures", "errors", "skipped")}}
        entry["failed_cases"] = []
        for case in root.findall("testcase"):
            issue = case.find("failure")
            if issue is None:
                issue = case.find("error")
            if issue is not None:
                entry["failed_cases"].append({"test": case.attrib["name"], "type": issue.attrib.get("type")})
        entry["passed"] = entry["tests"] - entry["failures"] - entry["errors"] - entry["skipped"]
        classes.append(entry)
    totals = {key: sum(entry[key] for entry in classes)
              for key in ("tests", "passed", "failures", "errors", "skipped")}
    return {"totals": totals, "classes": classes}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--audit-only", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = json.dumps(summarize(args.directory, args.audit_only), indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result)
    else:
        print(result, end="")
