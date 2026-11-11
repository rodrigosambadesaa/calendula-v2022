# Android emulator smoke test manifests

Place each new instrumentation test class in a separate `*.txt` file in this directory (one fully qualified Java/Kotlin class name per non-empty line). The test runner loads files in sorted filename order, validates names, refuses duplicates, and runs **every registered class** on the disposable Android API 23, 33, 36 and 37 emulator matrix.

Feature pull requests can add a new manifest file without editing `scripts/android-smoke-tests.sh`, reducing merge conflicts and preventing regressions from being compiled without execution. Avoid putting real patient data or network credentials in instrumentation tests.

To inspect all selected tests **without launching Gradle or an emulator**, run:

```bash
CALENDULA_SMOKE_LIST_ONLY=1 bash scripts/android-smoke-tests.sh
```

If a new class does not appear, it is not part of the smoke suite. The runner fails on malformed or repeated entries.

## Safety guard for instrumentation

The smoke suite **creates, changes and removes disposable database records**. Do not invoke it on a phone or tablet containing real patient information. After class-list-only mode, `scripts/android-smoke-tests.sh` calls `scripts/ensure_disposable_android_emulator.py` and refuses to start Gradle unless `adb devices` reports **exactly one** ready `emulator-NNNN` target whose Android system properties confirm emulator hardware. The runner pins `ANDROID_SERIAL` and subsequent `adb` calls to that verified serial. Physical/unauthorized/offline targets, multiple devices, a contradictory `ANDROID_SERIAL`, unverified properties or SDK versions below 23 cause a nonzero exit **before running instrumentation**.

The preflight is not a replacement for maintaining clean emulator images. Prefer fresh CI AVDs and synthetic fixtures. Inspect a target before any manual developer invocation.

To check the guard without needing Android SDK or a device:

```bash
python3 scripts/ensure_disposable_android_emulator.py --self-test
```

To inspect the connected target without launching Gradle, run `python3 scripts/ensure_disposable_android_emulator.py`; it prints the serial only if its checks pass.
