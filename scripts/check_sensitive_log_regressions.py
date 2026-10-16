#!/usr/bin/env python3
"""Small, dependency-free CI guard for previously observed clinical Logcat leaks.

This intentionally checks only known sensitive logging sites. It avoids
printing any matching source line, because that could repeat secret content.
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
RULES = {
    "Calendula/src/main/java/es/usc/citius/servando/calendula/settings/privacy/PrivacyPrefsPresenter.kt": (
        (r"data=\$data\b", "raw PIN settings Intent interpolated into diagnostics"),
        (r"LogUtil\.\w+\s*\([^\n]*EXTRA_NEW_PIN", "PIN extra directly logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/LeftDrawerMgr.java": (
        (r"LogUtil\.\w+\s*\([^\n]*p\.getName\s*\(", "patient name directly logged"),
        (r"Adding patient to drawer:\s*", "legacy patient-name log reintroduced"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/ConfirmActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*eventInstance\.toString\s*\(", "confirmation intake event serialized to log"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/schedules/ScheduleBuildActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*tmpSchedule\.toString\s*\(", "medication schedule serialized to log"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/DailyAgendaRecyclerAdapter.java": (
        (r"LogUtil\.\w+\s*\([^\n]*item\.toString\s*\(", "agenda row serialized to log"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/fragments/ActiveMedicationListFragment.java": (
        (r"LogUtil\.\w+\s*\([^\n]*event\.toString\s*\(", "provider medication update event serialized to log"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/CalendarActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*msg\.toString\s*\(", "patient medication calendar rendered text logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/util/stock/StockDisplayUtils.kt": (
        (r"updateStockText: estimated end date is", "stock depletion date logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/util/DispensationInfoStore.java": (
        (r"LogUtil\.\w+\s*\([^\n]*day\.toString\s*\(", "dispensation dates logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/scheduling/ScheduleDisplayUtils.java": (
        (r"displayDose: invalid decimalPart value! value =", "raw medication dose logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/schedules/PickRoutineStepFragment.java": (
        (r"ValueChange:[^\n]*selected:", "selected medication routine hour logged"),
    ),
    "Calendula/src/main/java/es/usc/citius/servando/calendula/activities/PdfViewActivity.java": (
        (r"LogUtil\.\w+\s*\([^\n]*\+\s*(?:url|previewUrl)\b", "provider document URL logged"),
        (r"Finished loading URL:", "legacy clinical document URI log reintroduced"),
        (r"LogUtil\.e\s*\(\s*TAG\s*,[^\n]*,\s*e\s*\)", "sensitive provider document exception trace logged"),
    ),
}


def violations(relative_path: str, source: str) -> list[str]:
    """Do not include source text (which may itself be sensitive) in errors."""
    return [
        description
        for pattern, description in RULES[relative_path]
        if re.search(pattern, source, flags=re.MULTILINE)
    ]


def self_test() -> None:
    pin, drawer, pdf = tuple(RULES)
    assert violations(pin, 'LogUtil.d(TAG, "data=$data")')
    assert violations(pin, 'LogUtil.d(TAG, EXTRA_NEW_PIN)')
    assert violations(drawer, 'LogUtil.d(TAG, "Name: " + p.getName())')
    assert violations(pdf, 'LogUtil.d(TAG, "Document: " + previewUrl)')
    assert violations(pdf, 'LogUtil.d(TAG, "Document: " + url)')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/activities/ConfirmActivity.java", 'LogUtil.d(TAG, eventInstance.toString())')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/activities/schedules/ScheduleBuildActivity.java", 'LogUtil.d(TAG, tmpSchedule.toString())')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/DailyAgendaRecyclerAdapter.java", 'LogUtil.d(TAG, item.toString())')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/fragments/ActiveMedicationListFragment.java", 'LogUtil.d(TAG, event.toString())')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/activities/CalendarActivity.java", 'LogUtil.d(TAG, msg.toString())')
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/util/stock/StockDisplayUtils.kt", "LogUtil.d(TAG, \"updateStockText: estimated end date is \" + privateDate)")
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/util/DispensationInfoStore.java", "LogUtil.d(TAG, day.toString(\"dd/MM/YYYY\"))")
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/scheduling/ScheduleDisplayUtils.java", "LogUtil.d(TAG, \"displayDose: invalid decimalPart value! value = \" + decimalPart)")
    assert violations("Calendula/src/main/java/es/usc/citius/servando/calendula/activities/schedules/PickRoutineStepFragment.java", "LogUtil.d(TAG, \"ValueChange: \" + current + \", selected: \" + hours.get(current - 1))")
    for name in RULES:
        assert not violations(name, 'LogUtil.d(TAG, "Operation completed")')
    print("Sensitive log regression guard self-test passed")


def main() -> int:
    if len(sys.argv) > 1:
        if sys.argv[1:] == ["--self-test"]:
            self_test()
            return 0
        print("Usage: check_sensitive_log_regressions.py [--self-test]", file=sys.stderr)
        return 2
    failed = False
    for path in RULES:
        source_path = ROOT / path
        if not source_path.is_file():
            print(f"Required sensitive logging guard target is missing: {path}", file=sys.stderr)
            failed = True
            continue
        for reason in violations(path, source_path.read_text(encoding="utf-8")):
            print(f"Sensitive Logcat regression: {path}: {reason}", file=sys.stderr)
            failed = True
    if not failed:
        print("Known PIN, patient, medication and clinical document Logcat sites are sanitized")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
