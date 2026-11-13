#!/usr/bin/env python3
"""Refuse to run destructive Android integration fixtures on a physical device.

The Android smoke suite creates, modifies and deletes synthetic clinical rows.
A test runner must only target one verified local Android *emulator* device;
it must never fall back to an arbitrarily attached phone or tablet.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
from collections.abc import Callable

Run = Callable[[list[str]], subprocess.CompletedProcess[str]]
SERIAL = re.compile(r"emulator-[0-9]+\Z")
EMULATOR_HARDWARE = {"ranchu", "goldfish"}


class UnsafeDevice(RuntimeError):
    """The currently attached Android device is not an isolated test emulator."""


def command(args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, capture_output=True, text=True, check=False, timeout=15)


def read(run: Run, args: list[str]) -> str:
    try:
        result = run(["adb", *args])
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise UnsafeDevice("adb could not safely inspect Android devices") from exc
    if result.returncode != 0:
        raise UnsafeDevice("adb device inspection failed")
    return result.stdout.strip()


def selected_emulator(run: Run = command, active_serial: str | None = None) -> str:
    lines = read(run, ["devices"]).splitlines()
    if not lines or lines[0].strip() != "List of devices attached":
        raise UnsafeDevice("Unrecognized adb device inventory")
    devices = [line.split() for line in lines[1:] if line.strip()]
    if len(devices) != 1 or len(devices[0]) < 2:
        raise UnsafeDevice("Expected exactly one disposable Android emulator (no other devices)")
    serial, state = devices[0][:2]
    if not SERIAL.fullmatch(serial) or state != "device":
        raise UnsafeDevice("Selected Android target is not an authorized emulator serial")
    if active_serial and active_serial != serial:
        raise UnsafeDevice("ANDROID_SERIAL selects a different device")

    def prop(key: str) -> str:
        return read(run, ["-s", serial, "shell", "getprop", key]).strip().lower()

    qemu = prop("ro.kernel.qemu") == "1" or prop("ro.boot.qemu") == "1"
    hardware = prop("ro.hardware")
    if not qemu and hardware not in EMULATOR_HARDWARE:
        raise UnsafeDevice("Connected target has no verified Android emulator hardware marker")
    sdk = prop("ro.build.version.sdk")
    if not sdk.isdecimal() or int(sdk) < 23:
        raise UnsafeDevice("Connected emulator runs an unverified Android SDK version")
    return serial


def self_test() -> None:
    def fixture(devices: str = "emulator-5554\tdevice\n", *,
                hardware: str = "ranchu", qemu: str = "1", boot: str = "",
                sdk: str = "36") -> Run:
        output = {
            ("adb", "devices"): "List of devices attached\n" + devices,
            ("adb", "-s", "emulator-5554", "shell", "getprop", "ro.kernel.qemu"): qemu,
            ("adb", "-s", "emulator-5554", "shell", "getprop", "ro.boot.qemu"): boot,
            ("adb", "-s", "emulator-5554", "shell", "getprop", "ro.hardware"): hardware,
            ("adb", "-s", "emulator-5554", "shell", "getprop", "ro.build.version.sdk"): sdk,
        }

        def run(args: list[str]) -> subprocess.CompletedProcess[str]:
            key = tuple(args)
            if key not in output:
                return subprocess.CompletedProcess(args, 1, "", "fixture command unknown")
            return subprocess.CompletedProcess(args, 0, output[key] + "\n", "")
        return run

    assert selected_emulator(fixture()) == "emulator-5554"
    assert selected_emulator(fixture(hardware="unknown", qemu="1")) == "emulator-5554"
    assert selected_emulator(fixture(qemu="0", hardware="goldfish")) == "emulator-5554"
    for unsafe, selected in [
        (fixture(devices="R58Y000ABCD\tdevice\n"), None),
        (fixture(devices="emulator-5554\tdevice\nR58Y000ABCD\tdevice\n"), None),
        (fixture(devices="emulator-5554\toffline\n"), None),
        (fixture(devices=""), None),
        (fixture(hardware="qcom", qemu="0"), None),
        (fixture(hardware="qcom", qemu="0", boot="0"), None),
        (fixture(sdk="not-an-sdk"), None),
        (fixture(), "R58Y000ABCD"),
    ]:
        try:
            selected_emulator(unsafe, selected)
        except UnsafeDevice:
            continue
        raise AssertionError("Unsafe physical, ambiguous or malformed device was accepted")
    print("Disposable Android emulator guard self-tests passed")


def main() -> int:
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return 0
    if len(sys.argv) != 1:
        print("Usage: ensure_disposable_android_emulator.py [--self-test]", file=sys.stderr)
        return 2
    try:
        print(selected_emulator(active_serial=os.environ.get("ANDROID_SERIAL")))
    except UnsafeDevice as exc:
        print(f"::error::Android smoke refused: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
