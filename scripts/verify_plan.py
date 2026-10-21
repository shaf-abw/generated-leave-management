#!/usr/bin/env python3
"""Validate the structure of docs/implementation-plan.md."""
import re
import sys
from pathlib import Path

PLAN = Path(sys.argv[1] if len(sys.argv) > 1 else "docs/implementation-plan.md")

REQUIRED_TOPICS = {
    "authentication and roles": r"authentication",
    "employee CRUD": r"employee crud",
    "leave requests": r"leave request",
    "manager decisions": r"manager decision",
    "leave history": r"leave history",
    "dashboard": r"dashboard",
    "tests": r"test",
    "Docker Compose": r"docker compose",
}

TASK_HEADING = re.compile(r"^### (T\d{2}): (.+)$", re.MULTILINE)
DEPENDS = re.compile(r"^\*\*Depends on:\*\* (.+)$", re.MULTILINE)
CRITERIA = re.compile(r"^\*\*Acceptance criteria:\*\*\s*$", re.MULTILINE)
CRITERION_ITEM = re.compile(r"^- \[ \] \S", re.MULTILINE)


def main() -> int:
    if not PLAN.is_file():
        print(f"ERROR: {PLAN} not found")
        return 1

    text = PLAN.read_text(encoding="utf-8")
    headings = list(TASK_HEADING.finditer(text))
    errors = []

    if not headings:
        errors.append("no tasks found (expected '### TNN: Title' headings)")

    seen = []
    titles = []
    for index, match in enumerate(headings):
        task_id, title = match.group(1), match.group(2)
        end = headings[index + 1].start() if index + 1 < len(headings) else len(text)
        body = text[match.end():end]
        titles.append(title)

        expected_id = f"T{index + 1:02d}"
        if task_id != expected_id:
            errors.append(f"{task_id}: expected sequential id {expected_id}")

        depends = DEPENDS.findall(body)
        if len(depends) != 1:
            errors.append(f"{task_id}: expected exactly one '**Depends on:**' line")
        else:
            value = depends[0].strip()
            if value != "none":
                for dep in (d.strip() for d in value.split(",")):
                    if not re.fullmatch(r"T\d{2}", dep):
                        errors.append(f"{task_id}: invalid dependency '{dep}'")
                    elif dep not in seen:
                        errors.append(f"{task_id}: dependency {dep} must be an earlier task")

        criteria = CRITERIA.search(body)
        if not criteria:
            errors.append(f"{task_id}: missing '**Acceptance criteria:**' section")
        elif not CRITERION_ITEM.search(body[criteria.end():]):
            errors.append(f"{task_id}: acceptance criteria list is empty")

        seen.append(task_id)

    joined_titles = "\n".join(titles).lower()
    for topic, pattern in REQUIRED_TOPICS.items():
        if not re.search(pattern, joined_titles):
            errors.append(f"no task title covers required topic: {topic}")

    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1

    print(f"OK: {len(headings)} tasks, all with dependencies and acceptance criteria")
    return 0


if __name__ == "__main__":
    sys.exit(main())
